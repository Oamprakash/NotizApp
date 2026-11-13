-- Notiz: gate log.
--
-- Security staff log every visitor at the gate. The flat gets a push and taps Allow or Deny;
-- the guard sees the answer and marks the visitor in and out. Residents can also pre-approve a
-- guest: they get a 6-digit code to share, and the guard enters it to let the guest straight in.
--
-- New permission 'gate' (run the gate desk), given to security staff by default. Residents always
-- see and answer the visitors for their own flat.
--
-- Run after 20261113000000_roles.sql.

-- ─────────────────────────────────────────────────────────────
-- Permission
-- ─────────────────────────────────────────────────────────────

create or replace function public.all_permissions()
returns text[] language sql immutable as $$
    select array['notices', 'polls', 'bills', 'payments', 'payment_settings', 'members', 'helpdesk', 'moderation', 'society', 'gate'];
$$;

create or replace function public.default_permissions(p_role text)
returns text[] language sql immutable as $$
    select case p_role
        when 'secretary' then array['notices', 'polls', 'members', 'helpdesk', 'moderation', 'society']
        when 'treasurer' then array['bills', 'payments', 'payment_settings', 'notices']
        when 'security' then array['gate']
        else array[]::text[]
    end;
$$;

insert into public.role_permissions (society_id, role, permission)
select id, 'security', 'gate' from public.societies
on conflict do nothing;

-- Today in India (all societies are in India; the server runs in UTC)
create or replace function public.gate_today()
returns date language sql stable as $$
    select (now() at time zone 'Asia/Kolkata')::date;
$$;

-- An approved, non-security member of this flat
create or replace function public.is_my_flat(sid uuid, p_flat_no text)
returns boolean language sql stable security definer set search_path = public as $$
    select exists (
        select 1 from memberships
        where society_id = sid and user_id = auth.uid() and status = 'approved'
          and role <> 'security' and flat_no = p_flat_no
    );
$$;

grant execute on function public.gate_today(), public.is_my_flat(uuid, text) to authenticated;

-- ─────────────────────────────────────────────────────────────
-- Tables
-- ─────────────────────────────────────────────────────────────

create table public.gate_passes (
    id            uuid primary key default gen_random_uuid(),
    society_id    uuid not null references public.societies (id) on delete cascade,
    flat_no       text not null check (char_length(flat_no) between 1 and 20),
    created_by    uuid references auth.users (id) on delete set null,
    created_by_name text not null default '',
    guest_name    text not null check (char_length(guest_name) between 1 and 60),
    category      text not null default 'guest' check (category in ('guest', 'delivery', 'cab', 'service', 'other')),
    code          text not null check (code ~ '^[0-9]{6}$'),
    valid_on      date not null,
    used_at       timestamptz,
    cancelled_at  timestamptz,
    created_at    timestamptz not null default now()
);
-- A code is unique among the passes that can still be used
create unique index gate_passes_open_code on public.gate_passes (society_id, code)
    where used_at is null and cancelled_at is null;
create index gate_passes_flat on public.gate_passes (society_id, flat_no, valid_on desc);

create table public.gate_entries (
    id              uuid primary key default gen_random_uuid(),
    society_id      uuid not null references public.societies (id) on delete cascade,
    flat_no         text not null check (char_length(flat_no) between 1 and 20),
    category        text not null check (category in ('guest', 'delivery', 'cab', 'service', 'other')),
    visitor_name    text not null default '' check (char_length(visitor_name) <= 60),
    visitor_phone   text not null default '' check (visitor_phone ~ '^[0-9]{0,15}$'),
    company         text not null default '' check (char_length(company) <= 40),
    vehicle_no      text not null default '' check (char_length(vehicle_no) <= 20),
    status          text not null default 'waiting'
                    check (status in ('waiting', 'allowed', 'denied', 'inside', 'left', 'cancelled')),
    pass_id         uuid references public.gate_passes (id) on delete set null,
    logged_by       uuid references auth.users (id) on delete set null,
    decided_by      uuid references auth.users (id) on delete set null,
    decided_by_name text not null default '',
    decided_at      timestamptz,
    entered_at      timestamptz,
    exited_at       timestamptz,
    created_at      timestamptz not null default now(),
    check (visitor_name <> '' or company <> '')
);
create index gate_entries_society on public.gate_entries (society_id, created_at desc);
create index gate_entries_flat on public.gate_entries (society_id, flat_no, created_at desc);

alter table public.gate_entries enable row level security;
alter table public.gate_passes enable row level security;

-- Read: the gate desk sees everything, a flat sees its own. All writes go through the functions below.
create policy "gate desk or own flat reads entries" on public.gate_entries for select to authenticated
    using (public.has_perm(society_id, 'gate') or public.is_my_flat(society_id, flat_no));
create policy "gate desk or own flat reads passes" on public.gate_passes for select to authenticated
    using (public.has_perm(society_id, 'gate') or public.is_my_flat(society_id, flat_no));

-- ─────────────────────────────────────────────────────────────
-- Notification helpers
-- ─────────────────────────────────────────────────────────────

-- Everyone living in the flat
create or replace function public.push_flat(p_society_id uuid, p_flat_no text)
returns uuid[] language sql stable security definer set search_path = public as $$
    select coalesce(array_agg(user_id), '{}')
    from memberships
    where society_id = p_society_id and status = 'approved' and role <> 'security' and flat_no = p_flat_no;
$$;

-- The people at the gate: roles with the gate permission (not the president, who would get every visitor)
create or replace function public.push_gate_desk(p_society_id uuid)
returns uuid[] language sql stable security definer set search_path = public as $$
    select coalesce(array_agg(m.user_id), '{}')
    from memberships m
    where m.society_id = p_society_id and m.status = 'approved' and m.role <> 'president'
      and exists (select 1 from role_permissions rp
                  where rp.society_id = m.society_id and rp.role = m.role and rp.permission = 'gate');
$$;

-- "Ravi (Swiggy delivery)", "Uber cab", "Kumar (guest)"
create or replace function public.gate_visitor_label(p_category text, p_name text, p_company text, p_lang text)
returns text language sql immutable as $$
    with c as (
        select case when p_lang = 'ta' then
                   case p_category when 'guest' then 'விருந்தினர்' when 'delivery' then 'டெலிவரி' when 'cab' then 'கேப்'
                                   when 'service' then 'சேவை' else 'பார்வையாளர்' end
               else
                   case p_category when 'guest' then 'guest' when 'delivery' then 'delivery' when 'cab' then 'cab'
                                   when 'service' then 'service' else 'visitor' end
               end as cat
    ), b as (
        select trim(case when coalesce(p_company, '') <> '' then p_company || ' ' || cat else cat end) as base from c
    )
    select case when coalesce(p_name, '') <> '' then p_name || ' (' || base || ')'
                else upper(left(base, 1)) || substr(base, 2) end
    from b;
$$;

revoke execute on function public.push_flat(uuid, text), public.push_gate_desk(uuid) from public, anon, authenticated;

-- ─────────────────────────────────────────────────────────────
-- Gate desk
-- ─────────────────────────────────────────────────────────────

-- A visitor arrives. Asks the flat (status 'waiting'), or lets them straight in when the guard
-- already knows (p_let_in, e.g. the resident phoned the gate).
create or replace function public.log_visitor(
    p_society_id uuid, p_flat_no text, p_category text, p_name text,
    p_phone text default '', p_company text default '', p_vehicle_no text default '', p_let_in boolean default false
) returns public.gate_entries language plpgsql security definer set search_path = public as $$
declare
    e gate_entries;
    flat text := upper(trim(coalesce(p_flat_no, '')));
    who_en text;
    who_ta text;
begin
    if not has_perm(p_society_id, 'gate') then raise exception 'Not allowed for your role'; end if;
    if flat = '' then raise exception 'Enter the flat number'; end if;
    if not exists (select 1 from memberships where society_id = p_society_id and status = 'approved'
                   and role <> 'security' and flat_no = flat) then
        raise exception 'No one lives in flat % on Notiz', flat;
    end if;
    if trim(coalesce(p_name, '')) = '' and trim(coalesce(p_company, '')) = '' then
        raise exception 'Enter the visitor''s name';
    end if;

    insert into gate_entries (society_id, flat_no, category, visitor_name, visitor_phone, company, vehicle_no,
                              status, logged_by, entered_at)
    values (p_society_id, flat, p_category, left(trim(coalesce(p_name, '')), 60),
            left(regexp_replace(coalesce(p_phone, ''), '[^0-9]', '', 'g'), 15),
            left(trim(coalesce(p_company, '')), 40), left(upper(trim(coalesce(p_vehicle_no, ''))), 20),
            case when p_let_in then 'inside' else 'waiting' end, auth.uid(),
            case when p_let_in then now() end)
    returning * into e;

    who_en := gate_visitor_label(e.category, e.visitor_name, e.company, 'en');
    who_ta := gate_visitor_label(e.category, e.visitor_name, e.company, 'ta');
    if p_let_in then
        perform push_l(push_flat(p_society_id, flat),
            'Visitor came in', who_en || ' has come in.',
            'பார்வையாளர் உள்ளே வந்தார்', who_ta || ' உள்ளே வந்துள்ளார்.',
            jsonb_build_object('type', 'gate', 'id', e.id::text, 'society_id', p_society_id::text));
    else
        perform push_l(push_flat(p_society_id, flat),
            'Visitor at the gate 🚪', who_en || ' is waiting. Tap to allow or deny.',
            'வாயிலில் பார்வையாளர் 🚪', who_ta || ' காத்திருக்கிறார். அனுமதிக்க அல்லது மறுக்க தொடவும்.',
            jsonb_build_object('type', 'gate', 'id', e.id::text, 'society_id', p_society_id::text));
    end if;
    return e;
end;
$$;

-- The flat answers
create or replace function public.respond_visitor(p_entry_id uuid, p_allow boolean)
returns public.gate_entries language plpgsql security definer set search_path = public as $$
declare
    e gate_entries;
    me text;
begin
    select * into e from gate_entries where id = p_entry_id for update;
    if e.id is null or not is_my_flat(e.society_id, e.flat_no) then raise exception 'Not allowed for your role'; end if;
    if e.status <> 'waiting' then raise exception 'Someone has already answered for this visitor'; end if;
    select coalesce(full_name, '') into me from profiles where id = auth.uid();

    update gate_entries
    set status = case when p_allow then 'allowed' else 'denied' end,
        decided_by = auth.uid(), decided_by_name = coalesce(me, ''), decided_at = now()
    where id = p_entry_id
    returning * into e;

    perform push_l(array_append(push_gate_desk(e.society_id), e.logged_by),
        case when p_allow then e.flat_no || ' allowed ✅' else e.flat_no || ' said no ❌' end,
        case when p_allow then 'Let ' || gate_visitor_label(e.category, e.visitor_name, e.company, 'en') || ' in.'
             else 'Don''t let ' || gate_visitor_label(e.category, e.visitor_name, e.company, 'en') || ' in.' end,
        case when p_allow then e.flat_no || ' அனுமதித்தது ✅' else e.flat_no || ' மறுத்தது ❌' end,
        case when p_allow then gate_visitor_label(e.category, e.visitor_name, e.company, 'ta') || ' உள்ளே செல்லலாம்.'
             else gate_visitor_label(e.category, e.visitor_name, e.company, 'ta') || ' உள்ளே செல்ல வேண்டாம்.' end,
        jsonb_build_object('type', 'gate', 'id', e.id::text, 'society_id', e.society_id::text));
    return e;
end;
$$;

-- Visitor walks in: after the flat allowed, or the guard decides (they phoned the flat)
create or replace function public.mark_visitor_in(p_entry_id uuid)
returns public.gate_entries language plpgsql security definer set search_path = public as $$
declare
    e gate_entries;
    was text;
begin
    select * into e from gate_entries where id = p_entry_id for update;
    if e.id is null or not has_perm(e.society_id, 'gate') then raise exception 'Not allowed for your role'; end if;
    if e.status not in ('waiting', 'allowed') then raise exception 'This visitor is no longer waiting'; end if;
    was := e.status;
    update gate_entries set status = 'inside', entered_at = now() where id = p_entry_id returning * into e;
    -- The flat didn't answer in the app, so tell them who came in
    if was = 'waiting' then
        perform push_l(push_flat(e.society_id, e.flat_no),
            'Visitor came in', gate_visitor_label(e.category, e.visitor_name, e.company, 'en') || ' has come in.',
            'பார்வையாளர் உள்ளே வந்தார்', gate_visitor_label(e.category, e.visitor_name, e.company, 'ta') || ' உள்ளே வந்துள்ளார்.',
            jsonb_build_object('type', 'gate', 'id', e.id::text, 'society_id', e.society_id::text));
    end if;
    return e;
end;
$$;

create or replace function public.mark_visitor_out(p_entry_id uuid)
returns public.gate_entries language plpgsql security definer set search_path = public as $$
declare
    e gate_entries;
begin
    select * into e from gate_entries where id = p_entry_id for update;
    if e.id is null or not has_perm(e.society_id, 'gate') then raise exception 'Not allowed for your role'; end if;
    if e.status <> 'inside' then raise exception 'This visitor isn''t inside'; end if;
    update gate_entries set status = 'left', exited_at = now() where id = p_entry_id returning * into e;
    return e;
end;
$$;

-- Visitor went away without coming in (or was logged by mistake)
create or replace function public.cancel_visitor(p_entry_id uuid)
returns public.gate_entries language plpgsql security definer set search_path = public as $$
declare
    e gate_entries;
begin
    select * into e from gate_entries where id = p_entry_id for update;
    if e.id is null or not has_perm(e.society_id, 'gate') then raise exception 'Not allowed for your role'; end if;
    if e.status not in ('waiting', 'allowed', 'denied') then raise exception 'This visitor is no longer waiting'; end if;
    update gate_entries set status = 'cancelled' where id = p_entry_id returning * into e;
    return e;
end;
$$;

-- ─────────────────────────────────────────────────────────────
-- Pre-approved guests
-- ─────────────────────────────────────────────────────────────

create or replace function public.create_gate_pass(
    p_society_id uuid, p_guest_name text, p_valid_on date, p_category text default 'guest'
) returns public.gate_passes language plpgsql security definer set search_path = public as $$
declare
    p gate_passes;
    flat text;
    me text;
    new_code text;
    tries int := 0;
begin
    select m.flat_no into flat from memberships m
    where m.society_id = p_society_id and m.user_id = auth.uid() and m.status = 'approved' and m.role <> 'security'
    limit 1;
    if flat is null then raise exception 'Not allowed for your role'; end if;
    if trim(coalesce(p_guest_name, '')) = '' then raise exception 'Enter the guest''s name'; end if;
    if p_valid_on is null or p_valid_on < gate_today() or p_valid_on > gate_today() + 30 then
        raise exception 'Pick a day in the next 30 days';
    end if;
    if (select count(*) from gate_passes where society_id = p_society_id and flat_no = flat
            and used_at is null and cancelled_at is null and valid_on >= gate_today()) >= 20 then
        raise exception 'Too many open guest passes. Cancel some first.';
    end if;
    select coalesce(full_name, '') into me from profiles where id = auth.uid();

    loop
        new_code := lpad((floor(random() * 1000000))::int::text, 6, '0');
        exit when not exists (select 1 from gate_passes where society_id = p_society_id and code = new_code
                              and used_at is null and cancelled_at is null);
        tries := tries + 1;
        if tries > 20 then raise exception 'Please try again'; end if;
    end loop;

    insert into gate_passes (society_id, flat_no, created_by, created_by_name, guest_name, category, code, valid_on)
    values (p_society_id, flat, auth.uid(), coalesce(me, ''), left(trim(p_guest_name), 60),
            coalesce(p_category, 'guest'), new_code, p_valid_on)
    returning * into p;
    return p;
end;
$$;

create or replace function public.cancel_gate_pass(p_pass_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
    p gate_passes;
begin
    select * into p from gate_passes where id = p_pass_id for update;
    if p.id is null or not is_my_flat(p.society_id, p.flat_no) then raise exception 'Not allowed for your role'; end if;
    if p.used_at is not null then raise exception 'This guest has already come in'; end if;
    update gate_passes set cancelled_at = now() where id = p_pass_id and cancelled_at is null;
end;
$$;

-- The guard types the guest's code: one use, on the day it was made for
create or replace function public.use_gate_pass(p_society_id uuid, p_code text)
returns public.gate_entries language plpgsql security definer set search_path = public as $$
declare
    p gate_passes;
    e gate_entries;
begin
    if not has_perm(p_society_id, 'gate') then raise exception 'Not allowed for your role'; end if;
    select * into p from gate_passes
    where society_id = p_society_id and code = trim(coalesce(p_code, ''))
      and used_at is null and cancelled_at is null
    for update;
    if p.id is null then raise exception 'Code not found or already used'; end if;
    if p.valid_on <> gate_today() then
        raise exception 'This code is for %', to_char(p.valid_on, 'FMDD Mon');
    end if;

    insert into gate_entries (society_id, flat_no, category, visitor_name, status, pass_id, logged_by,
                              decided_by, decided_by_name, decided_at, entered_at)
    values (p.society_id, p.flat_no, p.category, p.guest_name, 'inside', p.id, auth.uid(),
            p.created_by, p.created_by_name, p.created_at, now())
    returning * into e;
    update gate_passes set used_at = now() where id = p.id;

    perform push_l(push_flat(p.society_id, p.flat_no),
        'Your guest has arrived', p.guest_name || ' came in with your gate pass.',
        'உங்கள் விருந்தினர் வந்துவிட்டார்', p.guest_name || ' உங்கள் அனுமதிக் குறியீட்டுடன் உள்ளே வந்தார்.',
        jsonb_build_object('type', 'gate', 'id', e.id::text, 'society_id', p.society_id::text));
    return e;
end;
$$;

revoke execute on function
    public.log_visitor(uuid, text, text, text, text, text, text, boolean),
    public.respond_visitor(uuid, boolean), public.mark_visitor_in(uuid), public.mark_visitor_out(uuid),
    public.cancel_visitor(uuid), public.create_gate_pass(uuid, text, date, text), public.cancel_gate_pass(uuid),
    public.use_gate_pass(uuid, text)
    from public, anon;
grant execute on function
    public.log_visitor(uuid, text, text, text, text, text, text, boolean),
    public.respond_visitor(uuid, boolean), public.mark_visitor_in(uuid), public.mark_visitor_out(uuid),
    public.cancel_visitor(uuid), public.create_gate_pass(uuid, text, date, text), public.cancel_gate_pass(uuid),
    public.use_gate_pass(uuid, text)
    to authenticated;
