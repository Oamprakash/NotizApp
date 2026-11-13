-- Notiz: committee roles and editable permissions.
--
--   Roles        president · secretary · treasurer · resident · security
--   Committee    president, secretary, treasurer — they keep everything a resident has
--                (their own flat's dues, voting, chat) plus their role's permissions.
--   Security     staff without a flat: notices, help desk (their own requests) and a flat
--                directory; no dues, polls or chat.
--   Permissions  notices · polls · bills · payments · payment_settings · members · helpdesk ·
--                moderation · society. Each society starts with the defaults below; the
--                president can change them per role (Settings → Roles & permissions).
--                The president always has every permission and is the only one who can
--                assign roles or change permissions.
--
-- Existing data: the person who created each society becomes its president, other admins
-- become secretaries. Run after 20261102400000_chat_replies.sql.

-- ─────────────────────────────────────────────────────────────
-- Roles on memberships
-- ─────────────────────────────────────────────────────────────

alter table public.memberships drop constraint if exists memberships_role_check;
alter table public.memberships drop constraint if exists memberships_flat_no_check;

-- Triggers that guard role changes are replaced below; switch them off for the data move.
alter table public.memberships disable trigger user;

update public.memberships m set role = 'president'
from public.societies s
where m.society_id = s.id and m.role = 'admin' and m.user_id = s.created_by;
update public.memberships set role = 'secretary' where role = 'admin';
-- A society whose creator is gone: its longest-standing secretary becomes president
update public.memberships m set role = 'president'
where m.id in (
    select distinct on (society_id) id from public.memberships
    where role = 'secretary' and status = 'approved'
      and society_id not in (select society_id from public.memberships where role = 'president' and status = 'approved')
    order by society_id, created_at
);

alter table public.memberships enable trigger user;

alter table public.memberships add constraint memberships_role_check
    check (role in ('president', 'secretary', 'treasurer', 'resident', 'security'));
-- Security staff have no flat; everyone else has one
alter table public.memberships add constraint memberships_flat_no_check
    check ((role = 'security' and flat_no = '') or char_length(flat_no) between 1 and 20);

-- ─────────────────────────────────────────────────────────────
-- Permissions
-- ─────────────────────────────────────────────────────────────

create or replace function public.all_permissions()
returns text[] language sql immutable as $$
    select array['notices', 'polls', 'bills', 'payments', 'payment_settings', 'members', 'helpdesk', 'moderation', 'society'];
$$;

create or replace function public.default_permissions(p_role text)
returns text[] language sql immutable as $$
    select case p_role
        when 'secretary' then array['notices', 'polls', 'members', 'helpdesk', 'moderation', 'society']
        when 'treasurer' then array['bills', 'payments', 'payment_settings', 'notices']
        else array[]::text[]
    end;
$$;

create table public.role_permissions (
    society_id  uuid not null references public.societies (id) on delete cascade,
    role        text not null check (role in ('secretary', 'treasurer', 'resident', 'security')),
    permission  text not null check (permission = any (public.all_permissions())),
    primary key (society_id, role, permission)
);
alter table public.role_permissions enable row level security;
create policy "members read role permissions" on public.role_permissions for select to authenticated
    using (public.is_member(society_id));

create or replace function public.seed_role_permissions(p_society_id uuid)
returns void language sql security definer set search_path = public as $$
    insert into role_permissions (society_id, role, permission)
    select p_society_id, r, unnest(default_permissions(r))
    from unnest(array['secretary', 'treasurer', 'resident', 'security']) r
    on conflict do nothing;
$$;
revoke execute on function public.seed_role_permissions(uuid) from public, anon, authenticated;

create or replace function public.seed_role_permissions_trigger()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    perform seed_role_permissions(new.id);
    return new;
end;
$$;
revoke execute on function public.seed_role_permissions_trigger() from public, anon, authenticated;
create trigger societies_seed_permissions after insert on public.societies
    for each row execute function public.seed_role_permissions_trigger();

do $$ begin perform public.seed_role_permissions(id) from public.societies; end $$;

-- ─────────────────────────────────────────────────────────────
-- Checks used by policies and functions
-- ─────────────────────────────────────────────────────────────

create or replace function public.my_role(sid uuid)
returns text language sql stable security definer set search_path = public as $$
    select role from memberships where society_id = sid and user_id = auth.uid() and status = 'approved' limit 1;
$$;

create or replace function public.is_president(sid uuid)
returns boolean language sql stable security definer set search_path = public as $$
    select coalesce(my_role(sid) = 'president', false);
$$;

create or replace function public.is_committee(sid uuid)
returns boolean language sql stable security definer set search_path = public as $$
    select coalesce(my_role(sid) in ('president', 'secretary', 'treasurer'), false);
$$;

-- Kept for older code paths: "admin" now means any committee role
create or replace function public.is_admin(sid uuid)
returns boolean language sql stable security definer set search_path = public as $$
    select is_committee(sid);
$$;

-- Approved member who isn't security staff (polls, chat)
create or replace function public.is_resident_member(sid uuid)
returns boolean language sql stable security definer set search_path = public as $$
    select coalesce(my_role(sid) <> 'security', false);
$$;

create or replace function public.has_perm(sid uuid, perm text)
returns boolean language sql stable security definer set search_path = public as $$
    select case my_role(sid)
        when 'president' then true
        when null then false
        else exists (
            select 1 from role_permissions rp
            where rp.society_id = sid and rp.role = my_role(sid) and rp.permission = perm
        )
    end;
$$;

-- What the signed-in user may do in a society (the app hides what isn't allowed)
create or replace function public.my_permissions(p_society_id uuid)
returns text[] language sql stable security definer set search_path = public as $$
    select case my_role(p_society_id)
        when 'president' then all_permissions()
        when null then array[]::text[]
        else coalesce((
            select array_agg(permission order by permission) from role_permissions
            where society_id = p_society_id and role = my_role(p_society_id)
        ), array[]::text[])
    end;
$$;

grant execute on function public.my_role(uuid), public.is_president(uuid), public.is_committee(uuid),
    public.is_resident_member(uuid), public.has_perm(uuid, text), public.my_permissions(uuid),
    public.all_permissions(), public.default_permissions(text) to authenticated;

-- ─────────────────────────────────────────────────────────────
-- President: change permissions and roles
-- ─────────────────────────────────────────────────────────────

create or replace function public.set_role_permission(p_society_id uuid, p_role text, p_permission text, p_enabled boolean)
returns void language plpgsql security definer set search_path = public as $$
begin
    if not is_president(p_society_id) then raise exception 'Only the president can change permissions'; end if;
    if p_role not in ('secretary', 'treasurer', 'resident', 'security') then raise exception 'Unknown role'; end if;
    if not (p_permission = any (all_permissions())) then raise exception 'Unknown permission'; end if;
    if p_enabled then
        insert into role_permissions (society_id, role, permission) values (p_society_id, p_role, p_permission)
        on conflict do nothing;
    else
        delete from role_permissions where society_id = p_society_id and role = p_role and permission = p_permission;
    end if;
end;
$$;

-- Back to Notiz's defaults for one role
create or replace function public.reset_role_permissions(p_society_id uuid, p_role text)
returns void language plpgsql security definer set search_path = public as $$
begin
    if not is_president(p_society_id) then raise exception 'Only the president can change permissions'; end if;
    delete from role_permissions where society_id = p_society_id and role = p_role;
    insert into role_permissions (society_id, role, permission)
    select p_society_id, p_role, unnest(default_permissions(p_role));
end;
$$;

-- Give a member a role. Moving someone off security needs their flat number.
create or replace function public.set_member_role(p_membership_id uuid, p_role text, p_flat_no text default null)
returns memberships language plpgsql security definer set search_path = public as $$
declare
    m memberships;
    flat text;
begin
    select * into m from memberships where id = p_membership_id;
    if m.id is null or not is_president(m.society_id) then raise exception 'Only the president can change roles'; end if;
    if m.status <> 'approved' then raise exception 'Approve the member first'; end if;
    if p_role not in ('president', 'secretary', 'treasurer', 'resident', 'security') then raise exception 'Unknown role'; end if;
    flat := case when p_role = 'security' then '' else upper(trim(coalesce(nullif(trim(p_flat_no), ''), m.flat_no))) end;
    if p_role <> 'security' and flat = '' then raise exception 'Enter the flat number'; end if;
    update memberships set role = p_role, flat_no = flat where id = p_membership_id returning * into m;
    return m;
end;
$$;

grant execute on function public.set_role_permission(uuid, text, text, boolean), public.reset_role_permissions(uuid, text),
    public.set_member_role(uuid, text, text) to authenticated;

-- ─────────────────────────────────────────────────────────────
-- Guards on memberships
-- ─────────────────────────────────────────────────────────────

create or replace function public.is_last_admin(p_society_id uuid, p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
    select exists (
        select 1 from memberships
        where society_id = p_society_id and user_id = p_user and role = 'president' and status = 'approved'
    )
    and not exists (
        select 1 from memberships
        where society_id = p_society_id and user_id <> p_user and role = 'president' and status = 'approved'
    )
    and exists (
        select 1 from memberships
        where society_id = p_society_id and user_id <> p_user and status = 'approved'
    );
$$;

-- Role changes: president only, and never leave a society without a president.
-- Muting in chat: only people with the moderation permission.
create or replace function public.guard_last_admin()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    if new.role is distinct from old.role then
        if auth.uid() is not null and not is_president(old.society_id) then
            raise exception 'Only the president can change roles';
        end if;
        if old.role = 'president' and new.role <> 'president' and is_last_admin(old.society_id, old.user_id) then
            raise exception 'Make someone else president first';
        end if;
    end if;
    if new.chat_muted is distinct from old.chat_muted and auth.uid() is not null and not has_perm(old.society_id, 'moderation') then
        raise exception 'Not allowed for your role';
    end if;
    return new;
end;
$$;
drop trigger if exists memberships_guard_last_admin on public.memberships;
drop trigger if exists guard_last_admin on public.memberships;
create trigger memberships_guard_roles before update of role, chat_muted on public.memberships
    for each row execute function public.guard_last_admin();

-- Only the president can remove a committee member (others can still leave themselves)
create or replace function public.guard_committee_removal()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    if old.status = 'approved' and old.role in ('president', 'secretary', 'treasurer')
       and auth.uid() is not null and auth.uid() <> old.user_id and not is_president(old.society_id) then
        raise exception 'Only the president can remove committee members';
    end if;
    return old;
end;
$$;
create trigger memberships_guard_committee_removal before delete on public.memberships
    for each row execute function public.guard_committee_removal();

revoke execute on function public.guard_last_admin(), public.guard_committee_removal() from public, anon, authenticated;

-- ─────────────────────────────────────────────────────────────
-- Joining as security staff
-- ─────────────────────────────────────────────────────────────

drop function if exists public.join_society(text, text);
create or replace function public.join_society(p_invite_code text, p_flat_no text, p_as_staff boolean default false)
returns memberships language plpgsql security definer set search_path = public as $$
declare
    sid uuid;
    m memberships;
    flat text := case when p_as_staff then '' else upper(trim(coalesce(p_flat_no, ''))) end;
    r text := case when p_as_staff then 'security' else 'resident' end;
begin
    if auth.uid() is null then raise exception 'Not signed in'; end if;
    select id into sid from societies where invite_code = upper(trim(p_invite_code));
    if sid is null then raise exception 'Invalid invite code'; end if;
    if not p_as_staff and flat = '' then raise exception 'Enter the flat number'; end if;

    insert into memberships (society_id, user_id, flat_no, role)
    values (sid, auth.uid(), flat, r)
    on conflict (society_id, user_id) do update
        set flat_no = case when memberships.status = 'approved' then memberships.flat_no else excluded.flat_no end,
            role = case when memberships.status = 'approved' then memberships.role else excluded.role end,
            status = case when memberships.status = 'rejected' then 'pending' else memberships.status end
    returning * into m;
    return m;
end;
$$;
grant execute on function public.join_society(text, text, boolean) to authenticated;

-- ─────────────────────────────────────────────────────────────
-- Directory: who lives in which flat (guards call residents from here)
-- ─────────────────────────────────────────────────────────────

create or replace function public.directory(p_society_id uuid)
returns table (membership_id uuid, user_id uuid, full_name text, flat_no text, role text, phone text)
language plpgsql stable security definer set search_path = public, auth as $$
declare
    show_phone boolean := my_role(p_society_id) = 'security' or is_committee(p_society_id);
begin
    if my_role(p_society_id) is null then raise exception 'Not allowed'; end if;
    return query
        select m.id, m.user_id, p.full_name, m.flat_no, m.role,
               case when show_phone then nullif(u.phone, '') else null end
        from memberships m
        join profiles p on p.id = m.user_id
        left join auth.users u on u.id = m.user_id
        where m.society_id = p_society_id and m.status = 'approved'
        order by m.flat_no = '', m.flat_no, p.full_name;
end;
$$;
grant execute on function public.directory(uuid) to authenticated;

-- ─────────────────────────────────────────────────────────────
-- Notification recipients by permission
-- ─────────────────────────────────────────────────────────────

create or replace function public.push_perm(p_society_id uuid, p_perm text)
returns uuid[] language sql stable security definer set search_path = public as $$
    select coalesce(array_agg(m.user_id), '{}')
    from memberships m
    where m.society_id = p_society_id and m.status = 'approved'
      and (m.role = 'president' or exists (
          select 1 from role_permissions rp
          where rp.society_id = m.society_id and rp.role = m.role and rp.permission = p_perm));
$$;

-- Everyone except security staff (chat, polls, dues-related society changes)
create or replace function public.push_residents(p_society_id uuid, p_exclude uuid default null)
returns uuid[] language sql stable security definer set search_path = public as $$
    select coalesce(array_agg(user_id), '{}')
    from memberships
    where society_id = p_society_id and status = 'approved' and role <> 'security'
      and user_id is distinct from p_exclude;
$$;

create or replace function public.push_admins(p_society_id uuid)
returns uuid[] language sql stable security definer set search_path = public as $$
    select coalesce(array_agg(user_id), '{}')
    from memberships
    where society_id = p_society_id and status = 'approved' and role in ('president', 'secretary', 'treasurer');
$$;

create or replace function public.role_label(p_role text, p_lang text)
returns text language sql immutable as $$
    select case when p_lang = 'ta' then
        case p_role when 'president' then 'தலைவர்' when 'secretary' then 'செயலாளர்' when 'treasurer' then 'பொருளாளர்'
                    when 'security' then 'பாதுகாவலர்' else 'குடியிருப்பாளர்' end
    else
        case p_role when 'president' then 'President' when 'secretary' then 'Secretary' when 'treasurer' then 'Treasurer'
                    when 'security' then 'Security staff' else 'Resident' end
    end;
$$;

revoke execute on function public.push_perm(uuid, text), public.push_residents(uuid, uuid) from public, anon, authenticated;

-- ─────────────────────────────────────────────────────────────
-- Row level security, by permission
-- ─────────────────────────────────────────────────────────────

drop policy if exists "admins update complaints" on public.complaints;
create policy "helpdesk updates complaints" on public.complaints for update to authenticated
    using (public.has_perm(society_id, 'helpdesk')) with check (public.has_perm(society_id, 'helpdesk'));
drop policy if exists "own or admin reads complaints" on public.complaints;
create policy "own or helpdesk reads complaints" on public.complaints for select to authenticated
    using (raised_by = auth.uid() or public.has_perm(society_id, 'helpdesk'));

drop policy if exists "flat or admin reads dues" on public.dues;
create policy "flat or treasury reads dues" on public.dues for select to authenticated
    using (public.has_perm(society_id, 'bills') or public.has_perm(society_id, 'payments') or flat_no = public.my_flat(society_id));

drop policy if exists "admins update memberships" on public.memberships;
create policy "members permission updates memberships" on public.memberships for update to authenticated
    using (public.has_perm(society_id, 'members') or public.is_president(society_id))
    with check (public.has_perm(society_id, 'members') or public.is_president(society_id));

drop policy if exists "admins read reports" on public.message_reports;
create policy "moderators read reports" on public.message_reports for select to authenticated
    using (public.has_perm(society_id, 'moderation'));

drop policy if exists "members read messages" on public.messages;
create policy "residents read messages" on public.messages for select to authenticated
    using (public.is_resident_member(society_id));
drop policy if exists "members send messages" on public.messages;
create policy "residents send messages" on public.messages for insert to authenticated
    with check (
        sender_id = auth.uid()
        and public.is_resident_member(society_id)
        and not exists (
            select 1 from public.memberships
            where society_id = messages.society_id and user_id = auth.uid() and chat_muted
        )
    );

drop policy if exists "admins post notices" on public.notices;
create policy "notice writers post notices" on public.notices for insert to authenticated
    with check (public.has_perm(society_id, 'notices') and author_id = auth.uid());
drop policy if exists "admins edit notices" on public.notices;
create policy "notice writers edit notices" on public.notices for update to authenticated
    using (public.has_perm(society_id, 'notices')) with check (public.has_perm(society_id, 'notices'));
drop policy if exists "admins delete notices" on public.notices;
create policy "notice writers delete notices" on public.notices for delete to authenticated
    using (public.has_perm(society_id, 'notices'));

drop policy if exists "members read polls" on public.polls;
create policy "residents read polls" on public.polls for select to authenticated
    using (public.is_resident_member(society_id));
drop policy if exists "members read poll options" on public.poll_options;
create policy "residents read poll options" on public.poll_options for select to authenticated
    using (exists (select 1 from public.polls p where p.id = poll_options.poll_id and public.is_resident_member(p.society_id)));

drop policy if exists "notiz read attachments" on storage.objects;
create policy "notiz read attachments" on storage.objects for select to authenticated
    using (
        bucket_id = 'attachments' and (
            (public.attachment_kind(name) = 'notices' and public.is_member(public.attachment_society(name)))
            or (public.attachment_kind(name) = 'complaints'
                and (owner_id = auth.uid()::text or public.has_perm(public.attachment_society(name), 'helpdesk')))
        )
    );
drop policy if exists "notiz upload attachments" on storage.objects;
create policy "notiz upload attachments" on storage.objects for insert to authenticated
    with check (
        bucket_id = 'attachments' and (
            (public.attachment_kind(name) = 'notices' and public.has_perm(public.attachment_society(name), 'notices'))
            or (public.attachment_kind(name) = 'complaints' and public.is_member(public.attachment_society(name)))
        )
    );
drop policy if exists "notiz delete attachments" on storage.objects;
create policy "notiz delete attachments" on storage.objects for delete to authenticated
    using (
        bucket_id = 'attachments'
        and (owner_id = auth.uid()::text
             or public.has_perm(public.attachment_society(name), 'notices')
             or public.has_perm(public.attachment_society(name), 'helpdesk'))
    );

-- ─────────────────────────────────────────────────────────────
-- Existing functions, now checking the specific permission
-- ─────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION public.notice_read_counts(p_society_id uuid)
 RETURNS TABLE(notice_id uuid, reads bigint, members bigint)
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
#variable_conflict use_column
begin
    if not has_perm(p_society_id, 'notices') then raise exception 'Not allowed for your role'; end if;
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
$function$;

CREATE OR REPLACE FUNCTION public.create_dues_for_all(p_society_id uuid, p_title text, p_amount_paise bigint, p_due_date date)
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    n int;
begin
    if not has_perm(p_society_id, 'bills') then raise exception 'Not allowed for your role'; end if;
    insert into dues (society_id, flat_no, title, amount_paise, due_date)
    select distinct p_society_id, m.flat_no, trim(p_title), p_amount_paise, p_due_date
    from memberships m
    where m.society_id = p_society_id and m.status = 'approved' and m.role <> 'security' and m.flat_no <> '';
    get diagnostics n = row_count;
    return n;
end;
$function$;

CREATE OR REPLACE FUNCTION public.create_poll(p_society_id uuid, p_question text, p_options text[])
 RETURNS uuid
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    pid uuid;
    i int;
begin
    if not has_perm(p_society_id, 'polls') then raise exception 'Not allowed for your role'; end if;
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
$function$;

CREATE OR REPLACE FUNCTION public.cast_vote(p_poll_id uuid, p_option_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    sid uuid;
    flat text;
begin
    select society_id into sid from polls where id = p_poll_id;
    flat := my_flat(sid);
    if sid is null or coalesce(flat, '') = '' then raise exception 'Not allowed'; end if;
    if not exists (select 1 from poll_options where id = p_option_id and poll_id = p_poll_id) then
        raise exception 'Invalid option';
    end if;
    insert into poll_votes (poll_id, option_id, flat_no, voter_id)
    values (p_poll_id, p_option_id, flat, auth.uid())
    on conflict (poll_id, flat_no) do update
        set option_id = excluded.option_id, voter_id = excluded.voter_id, voted_at = now();
end;
$function$;

CREATE OR REPLACE FUNCTION public.push_on_poll()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
    perform push_l(
        push_residents(new.society_id, new.created_by),
        '🗳️ New poll',
        new.question || ' — one vote per flat.',
        '🗳️ புதிய வாக்கெடுப்பு',
        new.question || ' — ஒரு வீட்டுக்கு ஒரு வாக்கு.',
        jsonb_build_object('type', 'poll', 'id', new.id::text, 'society_id', new.society_id::text));
    return new;
end;
$function$;

CREATE OR REPLACE FUNCTION public.push_on_message()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    who text := new.sender_name || case when new.flat_no <> '' then ' · ' || new.flat_no else '' end;
    payload jsonb := jsonb_build_object('type', 'chat', 'id', new.id::text, 'society_id', new.society_id::text);
    replied_to uuid;
begin
    if new.reply_to_id is not null then
        select sender_id into replied_to from messages where id = new.reply_to_id;
        if replied_to = new.sender_id then replied_to := null; end if;
    end if;

    perform enqueue_push(
        array(select u from unnest(push_residents(new.society_id, new.sender_id)) u where u is distinct from replied_to),
        who,
        left(new.body, 200),
        payload);

    if replied_to is not null and replied_to = any(push_residents(new.society_id, new.sender_id)) then
        perform push_l(array[replied_to],
            new.sender_name || ' replied to you',
            left(new.body, 200),
            new.sender_name || ' உங்களுக்குப் பதிலளித்தார்',
            left(new.body, 200),
            payload);
    end if;
    return new;
end;
$function$;

CREATE OR REPLACE FUNCTION public.edit_bill(p_society_id uuid, p_title text, p_due_date date, p_new_title text, p_new_amount_paise bigint, p_new_due_date date)
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    n int;
begin
    if not has_perm(p_society_id, 'bills') then raise exception 'Not allowed for your role'; end if;
    if char_length(trim(coalesce(p_new_title, ''))) = 0 then raise exception 'Enter a title'; end if;
    if p_new_amount_paise is null or p_new_amount_paise <= 0 then raise exception 'Enter a valid amount'; end if;
    update dues
    set title = trim(p_new_title),
        amount_paise = p_new_amount_paise,
        due_date = p_new_due_date,
        payment_link_id = null,
        payment_link_url = null
    where society_id = p_society_id and title = p_title and due_date = p_due_date and status = 'pending';
    get diagnostics n = row_count;
    return n;
end;
$function$;

CREATE OR REPLACE FUNCTION public.push_on_membership()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    member_name text;
    society_name text;
    me jsonb := jsonb_build_object('type', 'society', 'society_id', new.society_id::text);
begin
    select full_name into member_name from profiles where id = new.user_id;
    select name into society_name from societies where id = new.society_id;

    if new.status = 'pending' and (tg_op = 'INSERT' or old.status is distinct from 'pending') then
        perform push_l(push_perm(new.society_id, 'members'),
            'New join request',
            coalesce(member_name, 'A resident') || case when new.role = 'security' then ' (security staff)' else ' (flat ' || new.flat_no || ')' end || ' wants to join. Tap to review.',
            'புதிய சேர்க்கை கோரிக்கை',
            coalesce(member_name, 'ஒரு குடியிருப்பாளர்') || case when new.role = 'security' then ' (பாதுகாவலர்)' else ' (வீடு ' || new.flat_no || ')' end || ' சேர விரும்புகிறார். பார்க்க தட்டவும்.',
            jsonb_build_object('type', 'member', 'society_id', new.society_id::text));
    elsif tg_op = 'UPDATE' and new.status = 'approved' and old.status = 'pending' then
        perform push_l(array[new.user_id],
            'You''re in! 🎉',
            'Your request to join ' || coalesce(society_name, 'the society') || ' was approved.',
            'நீங்கள் சேர்ந்துவிட்டீர்கள்! 🎉',
            coalesce(society_name, 'சங்கம்') || ' — சேர உங்கள் கோரிக்கை ஏற்கப்பட்டது.',
            jsonb_build_object('type', 'approved', 'society_id', new.society_id::text));
    elsif tg_op = 'UPDATE' and new.status = 'rejected' and old.status = 'pending' then
        perform push_l(array[new.user_id],
            'Join request not approved',
            'Your request to join ' || coalesce(society_name, 'the society') ||
                ' wasn''t approved. Check the invite code and flat number with your secretary.',
            'சேர்க்கை கோரிக்கை ஏற்கப்படவில்லை',
            coalesce(society_name, 'சங்கம்') ||
                ' — சேர உங்கள் கோரிக்கை ஏற்கப்படவில்லை. அழைப்புக் குறியீட்டையும் வீட்டு எண்ணையும் செயலாளரிடம் சரிபார்க்கவும்.',
            me);
    elsif tg_op = 'UPDATE' and new.status = 'approved' then
        if new.role is distinct from old.role then
            perform push_l(array[new.user_id],
                'Your role: ' || role_label(new.role, 'en'),
                'You are now ' || role_label(new.role, 'en') || ' in ' || coalesce(society_name, 'the society') || '.',
                'உங்கள் பொறுப்பு: ' || role_label(new.role, 'ta'),
                coalesce(society_name, 'சங்கம்') || ' — நீங்கள் இப்போது ' || role_label(new.role, 'ta') || '.',
                me);
        end if;
        if new.flat_no is distinct from old.flat_no then
            perform push_l(array[new.user_id],
                'Flat number updated',
                'Your flat in ' || coalesce(society_name, 'the society') || ' is now ' || new.flat_no || ' (was ' || old.flat_no || ').',
                'வீட்டு எண் மாற்றப்பட்டது',
                coalesce(society_name, 'சங்கம்') || ' — உங்கள் வீட்டு எண் இப்போது ' || new.flat_no || ' (முன்பு ' || old.flat_no || ').',
                me);
        end if;
    end if;
    return new;
end;
$function$;

CREATE OR REPLACE FUNCTION public.cancel_due(p_due_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    d dues;
begin
    select * into d from dues where id = p_due_id;
    if d.id is null or not has_perm(d.society_id, 'bills') then raise exception 'Not allowed for your role'; end if;
    if d.status <> 'pending' then raise exception 'Only unpaid dues can be cancelled'; end if;
    update dues set status = 'cancelled' where id = p_due_id;
end;
$function$;

CREATE OR REPLACE FUNCTION public.cancel_bill(p_society_id uuid, p_title text, p_due_date date)
 RETURNS integer
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    n int;
begin
    if not has_perm(p_society_id, 'bills') then raise exception 'Not allowed for your role'; end if;
    update dues set status = 'cancelled'
    where society_id = p_society_id and title = p_title and due_date = p_due_date and status = 'pending';
    get diagnostics n = row_count;
    return n;
end;
$function$;

CREATE OR REPLACE FUNCTION public.set_online_payments(p_society_id uuid, p_enabled boolean)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
    if not has_perm(p_society_id, 'payment_settings') then raise exception 'Not allowed for your role'; end if;
    if p_enabled and not exists (select 1 from payment_gateways where society_id = p_society_id) then
        raise exception 'Save the Razorpay keys first';
    end if;
    update societies set online_payments_enabled = p_enabled where id = p_society_id;
end;
$function$;

CREATE OR REPLACE FUNCTION public.payment_gateway_status(p_society_id uuid)
 RETURNS TABLE(configured boolean, key_id text, webhook_secret text, online_enabled boolean, test_mode boolean)
 LANGUAGE plpgsql
 STABLE SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
#variable_conflict use_column
begin
    if not has_perm(p_society_id, 'payment_settings') then raise exception 'Not allowed for your role'; end if;
    return query
        select g.society_id is not null,
               g.key_id,
               g.webhook_secret,
               s.online_payments_enabled,
               coalesce(g.key_id like 'rzp_test_%', false)
        from societies s
        left join payment_gateways g on g.society_id = s.id
        where s.id = p_society_id;
end;
$function$;

CREATE OR REPLACE FUNCTION public.remove_razorpay_keys(p_society_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
    if not has_perm(p_society_id, 'payment_settings') then raise exception 'Not allowed for your role'; end if;
    update societies set online_payments_enabled = false where id = p_society_id;
    delete from payment_gateways where society_id = p_society_id;
end;
$function$;

CREATE OR REPLACE FUNCTION public.verify_payment(p_due_id uuid, p_approve boolean)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    d dues;
begin
    select * into d from dues where id = p_due_id;
    if d.id is null or not has_perm(d.society_id, 'payments') then raise exception 'Not allowed for your role'; end if;
    if d.status = 'cancelled' then raise exception 'This due was cancelled'; end if;

    if p_approve then
        perform mark_due_paid(p_due_id, case when d.status = 'submitted' then 'upi' else 'cash' end, null);
    elsif d.status = 'submitted' then
        update dues set status = 'pending', utr = null, submitted_by = null where id = p_due_id;
    end if;
end;
$function$;

CREATE OR REPLACE FUNCTION public.set_razorpay_keys(p_society_id uuid, p_key_id text, p_key_secret text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    existing payment_gateways;
begin
    if not has_perm(p_society_id, 'payment_settings') then raise exception 'Not allowed for your role'; end if;
    if trim(coalesce(p_key_id, '')) !~ '^rzp_(test|live)_[A-Za-z0-9]+$' then
        raise exception 'Invalid Razorpay key id';
    end if;
    select * into existing from payment_gateways where society_id = p_society_id;
    if existing.society_id is null and coalesce(trim(p_key_secret), '') = '' then
        raise exception 'Enter the Razorpay key secret';
    end if;

    if existing.society_id is not null then
        update payment_gateways
        set key_id = trim(p_key_id),
            key_secret = coalesce(nullif(trim(p_key_secret), ''), key_secret),
            updated_by = auth.uid(),
            updated_at = now()
        where society_id = p_society_id;
    else
        insert into payment_gateways (society_id, key_id, key_secret, webhook_secret, updated_by)
        values (
            p_society_id,
            trim(p_key_id),
            trim(p_key_secret),
            replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', ''),  -- 64 random hex chars
            auth.uid()
        );
    end if;
end;
$function$;

CREATE OR REPLACE FUNCTION public.regenerate_invite_code(p_society_id uuid)
 RETURNS text
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    code text;
begin
    if not has_perm(p_society_id, 'members') then raise exception 'Not allowed for your role'; end if;
    loop
        code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6));
        exit when not exists (select 1 from societies where invite_code = code);
    end loop;
    update societies set invite_code = code where id = p_society_id;
    return code;
end;
$function$;

CREATE OR REPLACE FUNCTION public.remove_member(p_membership_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    m memberships;
begin
    select * into m from memberships where id = p_membership_id;
    if m.id is null or not has_perm(m.society_id, 'members') then raise exception 'Not allowed for your role'; end if;
    if m.user_id = auth.uid() then raise exception 'Use Leave society to remove yourself'; end if;
    delete from memberships where id = p_membership_id;
end;
$function$;

CREATE OR REPLACE FUNCTION public.set_member_flat(p_membership_id uuid, p_flat_no text)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    m memberships;
begin
    select * into m from memberships where id = p_membership_id;
    if m.id is null or not has_perm(m.society_id, 'members') then raise exception 'Not allowed for your role'; end if;
    if char_length(trim(coalesce(p_flat_no, ''))) = 0 then raise exception 'Enter the flat number'; end if;
    update memberships set flat_no = upper(trim(p_flat_no)) where id = p_membership_id;
end;
$function$;

CREATE OR REPLACE FUNCTION public.update_society_settings(p_society_id uuid, p_name text, p_city text, p_upi_vpa text, p_upi_payee_name text)
 RETURNS societies
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    s societies;
    can_details boolean := has_perm(p_society_id, 'society');
    can_upi boolean := has_perm(p_society_id, 'payment_settings');
begin
    if not (can_details or can_upi) then raise exception 'Not allowed for your role'; end if;
    if can_details and char_length(trim(coalesce(p_name, ''))) < 2 then raise exception 'Enter the society name'; end if;
    if can_upi and coalesce(trim(p_upi_vpa), '') <> '' and trim(p_upi_vpa) !~ '^[A-Za-z0-9.\-_]{2,255}@[A-Za-z]{2,64}$' then
        raise exception 'UPI ID should look like society@okaxis';
    end if;
    -- Each role only changes the part it is allowed to: details (society) or UPI (payment settings)
    update societies
    set name = case when can_details then trim(p_name) else name end,
        city = case when can_details then trim(coalesce(p_city, '')) else city end,
        upi_vpa = case when can_upi then trim(coalesce(p_upi_vpa, '')) else upi_vpa end,
        upi_payee_name = case when can_upi then trim(coalesce(p_upi_payee_name, '')) else upi_payee_name end
    where id = p_society_id
    returning * into s;
    return s;
end;
$function$;

CREATE OR REPLACE FUNCTION public.delete_message(p_message_id uuid)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    m messages;
begin
    select * into m from messages where id = p_message_id;
    if m.id is null or not (m.sender_id = auth.uid() or has_perm(m.society_id, 'moderation')) then
        raise exception 'Not allowed';
    end if;
    if m.deleted_at is not null then return; end if;
    update messages set body = '', deleted_at = now(), deleted_by = auth.uid() where id = p_message_id;
    update messages set reply_body = '' where reply_to_id = p_message_id;
end;
$function$;

CREATE OR REPLACE FUNCTION public.push_on_message_report()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    m messages;
    who text;
begin
    select * into m from messages where id = new.message_id;
    who := m.sender_name || case when m.flat_no <> '' then ' · ' || m.flat_no else '' end;
    perform push_l(push_perm(new.society_id, 'moderation'),
        '🚩 Message reported · ' || who,
        left(m.body, 160),
        '🚩 செய்தி மீது புகார் · ' || who,
        left(m.body, 160),
        jsonb_build_object('type', 'chat', 'id', m.id::text, 'society_id', new.society_id::text));
    return new;
end;
$function$;

CREATE OR REPLACE FUNCTION public.set_chat_muted(p_society_id uuid, p_user_id uuid, p_muted boolean)
 RETURNS void
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
begin
    if not has_perm(p_society_id, 'moderation') then raise exception 'Not allowed for your role'; end if;
    if p_user_id = auth.uid() then raise exception 'You can''t mute yourself'; end if;
    update memberships set chat_muted = p_muted
    where society_id = p_society_id and user_id = p_user_id and status = 'approved';
    if not found then raise exception 'Member not found'; end if;
end;
$function$;

CREATE OR REPLACE FUNCTION public.push_on_due()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    payload jsonb := jsonb_build_object('type', 'dues', 'id', new.id::text, 'society_id', new.society_id::text);
    flat uuid[] := push_flat(new.society_id, new.flat_no);
    amount text := push_inr(new.amount_paise);
    en text[] := '{}';
    ta text[] := '{}';
    body_en text;
begin
    if tg_op = 'INSERT' then
        perform push_l(flat,
            'New bill: ' || new.title,
            amount || ' due by ' || push_date_en(new.due_date) || '. Tap to pay via UPI.',
            'புதிய பில்: ' || new.title,
            amount || ' — கடைசி நாள் ' || push_date_ta(new.due_date) || '. செலுத்த தட்டவும்.',
            payload);
    elsif new.status is distinct from old.status then
        if new.status = 'submitted' then
            perform push_l(push_perm(new.society_id, 'payments'),
                'Payment reported · Flat ' || new.flat_no,
                amount || ' for ' || new.title || '. Tap to verify.',
                'பணம் செலுத்தியதாகத் தகவல் · வீடு ' || new.flat_no,
                new.title || ' — ' || amount || '. சரிபார்க்க தட்டவும்.',
                payload);
        elsif new.status = 'paid' then
            perform push_l(flat,
                'Payment confirmed ✅',
                amount || ' for ' || new.title || ' has been received.',
                'பணம் உறுதிசெய்யப்பட்டது ✅',
                new.title || ' — ' || amount || ' பெறப்பட்டது.',
                payload);
        elsif new.status = 'pending' and old.status = 'submitted' then
            perform push_l(flat,
                'Payment not received',
                'The treasurer couldn''t confirm your payment for ' || new.title || '. Please check and try again.',
                'பணம் வரவில்லை',
                new.title || ' — உங்கள் பணத்தைப் பொருளாளரால் உறுதிசெய்ய முடியவில்லை. சரிபார்த்து மீண்டும் முயலவும்.',
                payload);
        elsif new.status = 'cancelled' then
            perform push_l(flat,
                'Bill cancelled: ' || new.title,
                'The committee cancelled this bill. Nothing to pay.',
                'பில் ரத்து: ' || new.title,
                'குழு இந்தப் பில்லை ரத்து செய்தது. செலுத்த வேண்டியதில்லை.',
                payload);
        end if;
    elsif new.status in ('pending', 'submitted') then
        -- Edited by the committee (edit_bill)
        if new.amount_paise is distinct from old.amount_paise then
            en := en || ('amount is now ' || amount || ' (was ' || push_inr(old.amount_paise) || ')');
            ta := ta || ('தொகை இப்போது ' || amount || ' (முன்பு ' || push_inr(old.amount_paise) || ')');
        end if;
        if new.due_date is distinct from old.due_date then
            en := en || ('due date is now ' || push_date_en(new.due_date));
            ta := ta || ('கடைசி நாள் இப்போது ' || push_date_ta(new.due_date));
        end if;
        if new.title is distinct from old.title then
            en := en || ('renamed from "' || old.title || '"');
            ta := ta || ('முன்பு "' || old.title || '" என்ற பெயர்');
        end if;
        if cardinality(en) > 0 then
            body_en := array_to_string(en, ', ');
            perform push_l(flat,
                'Bill updated: ' || new.title,
                upper(left(body_en, 1)) || substr(body_en, 2) || '.',
                'பில் புதுப்பிக்கப்பட்டது: ' || new.title,
                array_to_string(ta, ', ') || '.',
                payload);
        end if;
    end if;
    return new;
end;
$function$;

CREATE OR REPLACE FUNCTION public.push_on_society()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    payload jsonb := jsonb_build_object('type', 'society', 'society_id', new.id::text);
    recipients uuid[] := push_residents(new.id, auth.uid());
begin
    if new.name is distinct from old.name then
        perform push_l(recipients,
            'Society renamed',
            '"' || old.name || '" is now "' || new.name || '" on Notiz.',
            'சங்கத்தின் பெயர் மாற்றம்',
            '"' || old.name || '" இப்போது "' || new.name || '".',
            payload);
    end if;
    if new.upi_vpa is distinct from old.upi_vpa and coalesce(new.upi_vpa, '') <> '' then
        perform push_l(recipients,
            '💳 New UPI ID for dues',
            'Pay ' || new.name || ' dues to ' || new.upi_vpa || ' from now on' ||
                case when coalesce(old.upi_vpa, '') <> '' then ' (not ' || old.upi_vpa || ')' else '' end || '.',
            '💳 கட்டணத்துக்குப் புதிய UPI ID',
            'இனி ' || new.name || ' கட்டணங்களை ' || new.upi_vpa || ' க்குச் செலுத்தவும்' ||
                case when coalesce(old.upi_vpa, '') <> '' then ' (' || old.upi_vpa || ' அல்ல)' else '' end || '.',
            payload);
    end if;
    if new.online_payments_enabled is distinct from old.online_payments_enabled then
        if new.online_payments_enabled then
            perform push_l(recipients,
                'Pay dues online ✅',
                'You can now pay ' || new.name || ' dues online in Notiz — confirmed instantly, no UTR needed.',
                'ஆன்லைனில் கட்டணம் செலுத்தலாம் ✅',
                'இனி ' || new.name || ' கட்டணங்களை Notiz-இல் ஆன்லைனில் செலுத்தலாம் — உடனே உறுதியாகும், UTR தேவையில்லை.',
                payload);
        else
            perform push_l(recipients,
                'Online payments paused',
                'Online payments are paused for now. Pay via UPI and report the UTR in Notiz.',
                'ஆன்லைன் கட்டணம் நிறுத்தம்',
                'ஆன்லைன் கட்டணம் தற்போது நிறுத்தப்பட்டுள்ளது. UPI மூலம் செலுத்தி UTR-ஐ Notiz-இல் பதிவிடவும்.',
                payload);
        end if;
    end if;
    return new;
end;
$function$;

CREATE OR REPLACE FUNCTION public.push_on_complaint()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    payload jsonb := jsonb_build_object('type', 'complaint', 'id', new.id::text, 'society_id', new.society_id::text);
begin
    if tg_op = 'INSERT' then
        perform push_l(push_perm(new.society_id, 'helpdesk'),
            'New complaint · ' || new.category,
            'Flat ' || new.flat_no || ': ' || left(new.description, 160),
            'புதிய புகார் · ' || push_category_ta(new.category),
            'வீடு ' || new.flat_no || ': ' || left(new.description, 160),
            payload);
    elsif new.status is distinct from old.status then
        perform push_l(array[new.raised_by],
            'Complaint update · ' || new.category,
            'Your complaint is now ' ||
                case new.status when 'in_progress' then 'in progress' when 'resolved' then 'resolved ✅' else 'open' end || '.',
            'புகார் நிலை · ' || push_category_ta(new.category),
            case new.status
                when 'in_progress' then 'உங்கள் புகாரில் வேலை நடக்கிறது.'
                when 'resolved' then 'உங்கள் புகார் தீர்க்கப்பட்டது ✅'
                else 'உங்கள் புகார் மீண்டும் திறக்கப்பட்டது.' end,
            payload);
    end if;
    return new;
end;
$function$;

CREATE OR REPLACE FUNCTION public.push_on_membership_removed()
 RETURNS trigger
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
declare
    member_name text;
    society_name text;
begin
    if old.status <> 'approved' then return old; end if;
    select full_name into member_name from profiles where id = old.user_id;
    select name into society_name from societies where id = old.society_id;

    if auth.uid() is not null and auth.uid() <> old.user_id then
        perform push_l(array[old.user_id],
            'Removed from ' || coalesce(society_name, 'your society'),
            'An admin removed you from the society. Ask the committee if this is a mistake.',
            coalesce(society_name, 'சங்கம்') || ' — நீக்கப்பட்டீர்கள்',
            'ஒரு நிர்வாகி உங்களைச் சங்கத்திலிருந்து நீக்கினார். தவறு என்றால் குழுவிடம் கேளுங்கள்.',
            jsonb_build_object('type', 'society', 'society_id', old.society_id::text));
    else
        perform push_l(push_perm(old.society_id, 'members'),
            'Member left · Flat ' || old.flat_no,
            coalesce(member_name, 'A member') || ' (flat ' || old.flat_no || ') left ' || coalesce(society_name, 'the society') || '.',
            'உறுப்பினர் விலகினார் · வீடு ' || old.flat_no,
            coalesce(member_name, 'ஒரு உறுப்பினர்') || ' (வீடு ' || old.flat_no || ') சங்கத்திலிருந்து விலகினார்.',
            jsonb_build_object('type', 'member', 'society_id', old.society_id::text));
    end if;
    return old;
end;
$function$;

CREATE OR REPLACE FUNCTION public.create_society(p_name text, p_city text, p_flat_no text, p_upi_vpa text, p_upi_payee_name text)
 RETURNS societies
 LANGUAGE plpgsql
 SECURITY DEFINER
 SET search_path TO 'public'
AS $function$
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
    values (s.id, auth.uid(), upper(trim(p_flat_no)), 'president', 'approved');

    return s;
end;
$function$;

grant execute on function public.cast_vote(uuid, uuid) to authenticated;

-- Leaving: the last president has to hand over first
create or replace function public.leave_society(p_society_id uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
    if auth.uid() is null then raise exception 'Not signed in'; end if;
    if is_last_admin(p_society_id, auth.uid()) then raise exception 'Make someone else president first'; end if;
    delete from memberships where society_id = p_society_id and user_id = auth.uid();
end;
$$;
