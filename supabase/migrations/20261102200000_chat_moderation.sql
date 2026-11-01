-- Notiz: chat moderation.
--
--   Delete a message   the sender, or any admin. The text is wiped; a "message deleted" stub stays.
--   Report a message   any member → admins get a push with the text.
--   Mute a member      admins stop someone sending chat messages (they can still read).
--
-- Run after 20261102100000_photos.sql.

-- ─────────────────────────────────────────────────────────────
-- Deleting messages
-- ─────────────────────────────────────────────────────────────

alter table public.messages add column deleted_at timestamptz;
alter table public.messages add column deleted_by uuid references public.profiles (id) on delete set null;
alter table public.messages drop constraint if exists messages_body_check;
alter table public.messages add constraint messages_body_check
    check (char_length(body) <= 4000 and (deleted_at is not null or char_length(body) >= 1));

create or replace function public.delete_message(p_message_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
    m messages;
begin
    select * into m from messages where id = p_message_id;
    if m.id is null or not (m.sender_id = auth.uid() or is_admin(m.society_id)) then
        raise exception 'Not allowed';
    end if;
    if m.deleted_at is not null then return; end if;
    update messages set body = '', deleted_at = now(), deleted_by = auth.uid() where id = p_message_id;
end;
$$;

-- ─────────────────────────────────────────────────────────────
-- Muting
-- ─────────────────────────────────────────────────────────────

alter table public.memberships add column chat_muted boolean not null default false;

drop policy if exists "members send messages" on public.messages;
create policy "members send messages" on public.messages for insert to authenticated
    with check (
        sender_id = auth.uid()
        and public.is_member(society_id)
        and not exists (
            select 1 from public.memberships
            where society_id = messages.society_id and user_id = auth.uid() and chat_muted
        )
    );

create or replace function public.set_chat_muted(p_society_id uuid, p_user_id uuid, p_muted boolean)
returns void language plpgsql security definer set search_path = public as $$
begin
    if not is_admin(p_society_id) then raise exception 'Admins only'; end if;
    if p_user_id = auth.uid() then raise exception 'You can''t mute yourself'; end if;
    update memberships set chat_muted = p_muted
    where society_id = p_society_id and user_id = p_user_id and status = 'approved';
    if not found then raise exception 'Member not found'; end if;
end;
$$;

-- The direct "admins update memberships" policy must not be a way around set_chat_muted for
-- non-admins; it already requires is_admin, so nothing else to do here.

-- ─────────────────────────────────────────────────────────────
-- Reporting
-- ─────────────────────────────────────────────────────────────

create table public.message_reports (
    message_id   uuid not null references public.messages (id) on delete cascade,
    reporter_id  uuid not null references public.profiles (id) on delete cascade,
    society_id   uuid not null references public.societies (id) on delete cascade,
    created_at   timestamptz not null default now(),
    primary key (message_id, reporter_id)
);
alter table public.message_reports enable row level security;
create policy "admins read reports" on public.message_reports for select to authenticated
    using (public.is_admin(society_id));

-- Returns false if this member had already reported it.
create or replace function public.report_message(p_message_id uuid)
returns boolean language plpgsql security definer set search_path = public as $$
declare
    m messages;
begin
    select * into m from messages where id = p_message_id;
    if m.id is null or not is_member(m.society_id) then raise exception 'Not allowed'; end if;
    if m.sender_id = auth.uid() then raise exception 'You can''t report your own message'; end if;
    insert into message_reports (message_id, reporter_id, society_id)
    values (m.id, auth.uid(), m.society_id)
    on conflict do nothing;
    return found;
end;
$$;

-- ─────────────────────────────────────────────────────────────
-- Notifications (English here; the Tamil migration that follows replaces these)
-- ─────────────────────────────────────────────────────────────

create or replace function public.push_on_message_report()
returns trigger language plpgsql security definer set search_path = public as $$
declare
    m messages;
begin
    select * into m from messages where id = new.message_id;
    perform enqueue_push(
        push_admins(new.society_id),
        '🚩 Message reported · ' || m.sender_name || case when m.flat_no <> '' then ' · ' || m.flat_no else '' end,
        left(m.body, 160),
        jsonb_build_object('type', 'chat', 'id', m.id::text, 'society_id', new.society_id::text));
    return new;
end;
$$;
revoke execute on function public.push_on_message_report() from public, anon, authenticated;
create trigger message_reports_push after insert on public.message_reports
    for each row execute function public.push_on_message_report();

create or replace function public.push_on_chat_muted()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    if new.chat_muted is distinct from old.chat_muted and new.status = 'approved' then
        perform enqueue_push(
            array[new.user_id],
            case when new.chat_muted then 'Muted in chat' else 'You can chat again' end,
            case when new.chat_muted
                 then 'An admin muted you in the society chat. You can still read messages.'
                 else 'An admin unmuted you in the society chat.' end,
            jsonb_build_object('type', 'society', 'society_id', new.society_id::text));
    end if;
    return new;
end;
$$;
revoke execute on function public.push_on_chat_muted() from public, anon, authenticated;
create trigger memberships_chat_muted_push after update of chat_muted on public.memberships
    for each row execute function public.push_on_chat_muted();

grant execute on function public.delete_message(uuid) to authenticated;
grant execute on function public.set_chat_muted(uuid, uuid, boolean) to authenticated;
grant execute on function public.report_message(uuid) to authenticated;
