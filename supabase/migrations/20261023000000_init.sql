-- Notiz MVP schema
-- Run in Supabase: SQL Editor → paste → Run   (or `supabase db push` with the CLI)
-- Every table has Row Level Security. Multi-step or privileged writes go through
-- SECURITY DEFINER functions that check the caller's role themselves.

-- ─────────────────────────────────────────────────────────────
-- Tables
-- ─────────────────────────────────────────────────────────────

create table public.profiles (
    id          uuid primary key references auth.users (id) on delete cascade,
    full_name   text not null check (char_length(full_name) between 1 and 80),
    created_at  timestamptz not null default now()
);

create table public.societies (
    id              uuid primary key default gen_random_uuid(),
    name            text not null check (char_length(name) between 2 and 120),
    city            text not null default '',
    upi_vpa         text not null default '',
    upi_payee_name  text not null default '',
    invite_code     text not null unique,
    created_by      uuid not null references public.profiles (id),
    created_at      timestamptz not null default now()
);

create table public.memberships (
    id          uuid primary key default gen_random_uuid(),
    society_id  uuid not null references public.societies (id) on delete cascade,
    user_id     uuid not null references public.profiles (id) on delete cascade,
    flat_no     text not null check (char_length(flat_no) between 1 and 20),
    role        text not null default 'resident' check (role in ('admin', 'resident')),
    status      text not null default 'pending' check (status in ('pending', 'approved', 'rejected')),
    created_at  timestamptz not null default now(),
    unique (society_id, user_id)
);
create index on public.memberships (user_id);
create index on public.memberships (society_id, status);

create table public.notices (
    id          uuid primary key default gen_random_uuid(),
    society_id  uuid not null references public.societies (id) on delete cascade,
    author_id   uuid not null references public.profiles (id),
    title       text not null check (char_length(title) between 1 and 200),
    body        text not null default '',
    pinned      boolean not null default false,
    created_at  timestamptz not null default now()
);
create index on public.notices (society_id, created_at desc);

create table public.notice_reads (
    notice_id  uuid not null references public.notices (id) on delete cascade,
    user_id    uuid not null references public.profiles (id) on delete cascade,
    read_at    timestamptz not null default now(),
    primary key (notice_id, user_id)
);

create table public.dues (
    id            uuid primary key default gen_random_uuid(),
    society_id    uuid not null references public.societies (id) on delete cascade,
    flat_no       text not null,
    title         text not null,
    amount_paise  bigint not null check (amount_paise > 0),
    due_date      date not null,
    status        text not null default 'pending' check (status in ('pending', 'submitted', 'paid')),
    utr           text,
    paid_at       timestamptz,
    created_at    timestamptz not null default now()
);
create index on public.dues (society_id, flat_no);

create table public.complaints (
    id           uuid primary key default gen_random_uuid(),
    society_id   uuid not null references public.societies (id) on delete cascade,
    raised_by    uuid not null references public.profiles (id),
    flat_no      text not null default '',
    category     text not null,
    description  text not null check (char_length(description) between 1 and 2000),
    status       text not null default 'open' check (status in ('open', 'in_progress', 'resolved')),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now()
);
create index on public.complaints (society_id, created_at desc);

create table public.polls (
    id          uuid primary key default gen_random_uuid(),
    society_id  uuid not null references public.societies (id) on delete cascade,
    created_by  uuid not null references public.profiles (id),
    question    text not null check (char_length(question) between 1 and 300),
    created_at  timestamptz not null default now()
);

create table public.poll_options (
    id        uuid primary key default gen_random_uuid(),
    poll_id   uuid not null references public.polls (id) on delete cascade,
    label     text not null,
    position  int not null default 0
);

create table public.poll_votes (
    poll_id    uuid not null references public.polls (id) on delete cascade,
    option_id  uuid not null references public.poll_options (id) on delete cascade,
    flat_no    text not null,
    voter_id   uuid not null references public.profiles (id),
    voted_at   timestamptz not null default now(),
    primary key (poll_id, flat_no)          -- one vote per flat
);

create table public.messages (
    id           uuid primary key default gen_random_uuid(),
    society_id   uuid not null references public.societies (id) on delete cascade,
    sender_id    uuid not null references public.profiles (id),
    sender_name  text not null default '',
    flat_no      text not null default '',
    body         text not null check (char_length(body) between 1 and 4000),
    created_at   timestamptz not null default now()
);
create index on public.messages (society_id, created_at desc);

-- ─────────────────────────────────────────────────────────────
-- Helper functions (SECURITY DEFINER so policies don't recurse)
-- ─────────────────────────────────────────────────────────────

create or replace function public.is_member(sid uuid)
returns boolean language sql stable security definer set search_path = public as $$
    select exists (
        select 1 from memberships
        where society_id = sid and user_id = auth.uid() and status = 'approved'
    );
$$;

create or replace function public.is_admin(sid uuid)
returns boolean language sql stable security definer set search_path = public as $$
    select exists (
        select 1 from memberships
        where society_id = sid and user_id = auth.uid() and status = 'approved' and role = 'admin'
    );
$$;

create or replace function public.has_any_membership(sid uuid)
returns boolean language sql stable security definer set search_path = public as $$
    select exists (select 1 from memberships where society_id = sid and user_id = auth.uid());
$$;

create or replace function public.my_flat(sid uuid)
returns text language sql stable security definer set search_path = public as $$
    select flat_no from memberships
    where society_id = sid and user_id = auth.uid() and status = 'approved'
    limit 1;
$$;

-- Users who share an approved society with the caller (to see each other's names)
create or replace function public.shares_society(other uuid)
returns boolean language sql stable security definer set search_path = public as $$
    select exists (
        select 1
        from memberships mine
        join memberships theirs on theirs.society_id = mine.society_id
        where mine.user_id = auth.uid() and mine.status = 'approved'
          and theirs.user_id = other
    );
$$;

-- ─────────────────────────────────────────────────────────────
-- Row Level Security
-- ─────────────────────────────────────────────────────────────

alter table public.profiles     enable row level security;
alter table public.societies    enable row level security;
alter table public.memberships  enable row level security;
alter table public.notices      enable row level security;
alter table public.notice_reads enable row level security;
alter table public.dues         enable row level security;
alter table public.complaints   enable row level security;
alter table public.polls        enable row level security;
alter table public.poll_options enable row level security;
alter table public.poll_votes   enable row level security;
alter table public.messages     enable row level security;

-- profiles
create policy "read own or co-member profile" on public.profiles for select to authenticated
    using (id = auth.uid() or public.shares_society(id));
create policy "insert own profile" on public.profiles for insert to authenticated
    with check (id = auth.uid());
create policy "update own profile" on public.profiles for update to authenticated
    using (id = auth.uid()) with check (id = auth.uid());

-- societies (writes only via RPC)
create policy "members and applicants read society" on public.societies for select to authenticated
    using (public.has_any_membership(id));
create policy "admins update society" on public.societies for update to authenticated
    using (public.is_admin(id)) with check (public.is_admin(id));

-- memberships (insert only via RPC)
create policy "read own or co-members" on public.memberships for select to authenticated
    using (user_id = auth.uid() or public.is_member(society_id));
create policy "admins update memberships" on public.memberships for update to authenticated
    using (public.is_admin(society_id)) with check (public.is_admin(society_id));

-- notices
create policy "members read notices" on public.notices for select to authenticated
    using (public.is_member(society_id));
create policy "admins post notices" on public.notices for insert to authenticated
    with check (public.is_admin(society_id) and author_id = auth.uid());
create policy "admins edit notices" on public.notices for update to authenticated
    using (public.is_admin(society_id)) with check (public.is_admin(society_id));
create policy "admins delete notices" on public.notices for delete to authenticated
    using (public.is_admin(society_id));

-- notice_reads (writes via RPC)
create policy "read own reads" on public.notice_reads for select to authenticated
    using (user_id = auth.uid());

-- dues (writes via RPC)
create policy "flat or admin reads dues" on public.dues for select to authenticated
    using (public.is_admin(society_id) or flat_no = public.my_flat(society_id));

-- complaints
create policy "own or admin reads complaints" on public.complaints for select to authenticated
    using (raised_by = auth.uid() or public.is_admin(society_id));
create policy "members raise complaints" on public.complaints for insert to authenticated
    with check (raised_by = auth.uid() and public.is_member(society_id));
create policy "admins update complaints" on public.complaints for update to authenticated
    using (public.is_admin(society_id)) with check (public.is_admin(society_id));

-- polls (writes via RPC)
create policy "members read polls" on public.polls for select to authenticated
    using (public.is_member(society_id));
create policy "members read poll options" on public.poll_options for select to authenticated
    using (exists (select 1 from public.polls p where p.id = poll_id and public.is_member(p.society_id)));
create policy "read own flat vote" on public.poll_votes for select to authenticated
    using (exists (
        select 1 from public.polls p
        where p.id = poll_id and flat_no = public.my_flat(p.society_id)
    ));

-- messages
create policy "members read messages" on public.messages for select to authenticated
    using (public.is_member(society_id));
create policy "members send messages" on public.messages for insert to authenticated
    with check (sender_id = auth.uid() and public.is_member(society_id));

-- ─────────────────────────────────────────────────────────────
-- Triggers: fill denormalised fields server-side so clients can't spoof them
-- ─────────────────────────────────────────────────────────────

create or replace function public.fill_message_sender()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    select full_name into new.sender_name from profiles where id = new.sender_id;
    new.flat_no := coalesce(my_flat(new.society_id), '');
    new.created_at := now();
    return new;
end;
$$;
create trigger messages_fill_sender before insert on public.messages
    for each row execute function public.fill_message_sender();

create or replace function public.fill_complaint_flat()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    if tg_op = 'INSERT' then
        new.flat_no := coalesce(my_flat(new.society_id), '');
        new.status := 'open';
    end if;
    new.updated_at := now();
    return new;
end;
$$;
create trigger complaints_fill before insert or update on public.complaints
    for each row execute function public.fill_complaint_flat();

-- ─────────────────────────────────────────────────────────────
-- RPC functions
-- ─────────────────────────────────────────────────────────────

-- Register a society; caller becomes approved admin.
create or replace function public.create_society(
    p_name text, p_city text, p_flat_no text, p_upi_vpa text, p_upi_payee_name text
) returns public.societies
language plpgsql security definer set search_path = public as $$
declare
    s societies;
    code text;
begin
    if auth.uid() is null then raise exception 'Not signed in'; end if;
    loop
        code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6));
        exit when not exists (select 1 from societies where invite_code = code);
    end loop;

    insert into societies (name, city, upi_vpa, upi_payee_name, invite_code, created_by)
    values (trim(p_name), trim(p_city), trim(p_upi_vpa), trim(p_upi_payee_name), code, auth.uid())
    returning * into s;

    insert into memberships (society_id, user_id, flat_no, role, status)
    values (s.id, auth.uid(), upper(trim(p_flat_no)), 'admin', 'approved');

    return s;
end;
$$;

-- Join with an invite code; membership starts as pending.
create or replace function public.join_society(p_invite_code text, p_flat_no text)
returns public.memberships
language plpgsql security definer set search_path = public as $$
declare
    sid uuid;
    m memberships;
begin
    if auth.uid() is null then raise exception 'Not signed in'; end if;
    select id into sid from societies where invite_code = upper(trim(p_invite_code));
    if sid is null then raise exception 'Invalid invite code'; end if;

    insert into memberships (society_id, user_id, flat_no)
    values (sid, auth.uid(), upper(trim(p_flat_no)))
    on conflict (society_id, user_id) do update
        set flat_no = excluded.flat_no,
            status = case when memberships.status = 'rejected' then 'pending' else memberships.status end
    returning * into m;
    return m;
end;
$$;

-- Record that the caller opened a notice.
create or replace function public.mark_notice_read(p_notice_id uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
    if not exists (select 1 from notices n where n.id = p_notice_id and is_member(n.society_id)) then
        raise exception 'Not allowed';
    end if;
    insert into notice_reads (notice_id, user_id) values (p_notice_id, auth.uid())
    on conflict do nothing;
end;
$$;

-- Read counts per notice + approved member count (admins only).
create or replace function public.notice_read_counts(p_society_id uuid)
returns table (notice_id uuid, reads bigint, members bigint)
language plpgsql stable security definer set search_path = public as $$
#variable_conflict use_column
begin
    if not is_admin(p_society_id) then raise exception 'Admins only'; end if;
    return query
        select n.id,
               (select count(*) from notice_reads r
                  join memberships m on m.user_id = r.user_id
                       and m.society_id = p_society_id and m.status = 'approved'
                 where r.notice_id = n.id),
               (select count(*) from memberships m
                 where m.society_id = p_society_id and m.status = 'approved')
        from notices n
        where n.society_id = p_society_id;
end;
$$;

-- Raise one dues row per approved flat. Returns number of flats billed.
create or replace function public.create_dues_for_all(
    p_society_id uuid, p_title text, p_amount_paise bigint, p_due_date date
) returns int
language plpgsql security definer set search_path = public as $$
declare
    n int;
begin
    if not is_admin(p_society_id) then raise exception 'Admins only'; end if;
    insert into dues (society_id, flat_no, title, amount_paise, due_date)
    select distinct p_society_id, m.flat_no, trim(p_title), p_amount_paise, p_due_date
    from memberships m
    where m.society_id = p_society_id and m.status = 'approved';
    get diagnostics n = row_count;
    return n;
end;
$$;

-- Resident reports a UPI payment with its UTR / reference number.
create or replace function public.submit_payment(p_due_id uuid, p_utr text)
returns void language plpgsql security definer set search_path = public as $$
declare
    d dues;
begin
    select * into d from dues where id = p_due_id;
    if d.id is null or d.flat_no is distinct from my_flat(d.society_id) then
        raise exception 'Not allowed';
    end if;
    if d.status <> 'pending' then raise exception 'Already submitted'; end if;
    if char_length(trim(p_utr)) < 6 then raise exception 'Enter a valid UTR / reference number'; end if;
    update dues set status = 'submitted', utr = trim(p_utr) where id = p_due_id;
end;
$$;

-- Admin confirms money received (or rejects back to pending).
create or replace function public.verify_payment(p_due_id uuid, p_approve boolean)
returns void language plpgsql security definer set search_path = public as $$
declare
    d dues;
begin
    select * into d from dues where id = p_due_id;
    if d.id is null or not is_admin(d.society_id) then raise exception 'Admins only'; end if;
    if p_approve then
        update dues set status = 'paid', paid_at = now() where id = p_due_id;
    else
        update dues set status = 'pending', utr = null where id = p_due_id;
    end if;
end;
$$;

-- Create a poll with its options atomically.
create or replace function public.create_poll(p_society_id uuid, p_question text, p_options text[])
returns uuid language plpgsql security definer set search_path = public as $$
declare
    pid uuid;
    i int;
begin
    if not is_admin(p_society_id) then raise exception 'Admins only'; end if;
    if coalesce(array_length(p_options, 1), 0) < 2 then raise exception 'Add at least two options'; end if;
    insert into polls (society_id, created_by, question)
    values (p_society_id, auth.uid(), trim(p_question)) returning id into pid;
    for i in 1 .. array_length(p_options, 1) loop
        if char_length(trim(p_options[i])) > 0 then
            insert into poll_options (poll_id, label, position) values (pid, trim(p_options[i]), i);
        end if;
    end loop;
    return pid;
end;
$$;

-- One vote per flat; voting again changes the flat's vote.
create or replace function public.cast_vote(p_poll_id uuid, p_option_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
    sid uuid;
    flat text;
begin
    select society_id into sid from polls where id = p_poll_id;
    flat := my_flat(sid);
    if sid is null or flat is null then raise exception 'Not allowed'; end if;
    if not exists (select 1 from poll_options where id = p_option_id and poll_id = p_poll_id) then
        raise exception 'Invalid option';
    end if;
    insert into poll_votes (poll_id, option_id, flat_no, voter_id)
    values (p_poll_id, p_option_id, flat, auth.uid())
    on conflict (poll_id, flat_no) do update
        set option_id = excluded.option_id, voter_id = excluded.voter_id, voted_at = now();
end;
$$;

-- Vote counts per option for every poll in a society.
create or replace function public.poll_results(p_society_id uuid)
returns table (option_id uuid, votes bigint)
language plpgsql stable security definer set search_path = public as $$
#variable_conflict use_column
begin
    if not is_member(p_society_id) then raise exception 'Not allowed'; end if;
    return query
        select o.id, count(v.flat_no)
        from poll_options o
        join polls p on p.id = o.poll_id
        left join poll_votes v on v.option_id = o.id
        where p.society_id = p_society_id
        group by o.id;
end;
$$;

-- Lock down: functions callable by signed-in users only.
revoke execute on all functions in schema public from anon, public;
grant execute on all functions in schema public to authenticated;

-- Realtime for chat (RLS still applies to subscribers)
alter publication supabase_realtime add table public.messages;
