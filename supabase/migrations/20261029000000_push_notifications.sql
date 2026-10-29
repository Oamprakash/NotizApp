-- Notiz Phase 2: push notifications
--
-- How it works:
--   1. Apps register their FCM token with register_device_token().
--   2. Triggers on notices, dues, complaints, polls, messages and memberships decide WHO
--      should hear about a change and write one row to notification_outbox.
--   3. A Database Webhook on notification_outbox (INSERT) calls the `push` Edge Function,
--      which sends the message through Firebase Cloud Messaging to Android and iOS.
--   4. enqueue_due_reminders() (run daily by Supabase Cron) reminds flats about unpaid dues.
--
-- Recipients are decided in the database, so the app can't spoof or spam notifications.

-- ─────────────────────────────────────────────────────────────
-- Tables
-- ─────────────────────────────────────────────────────────────

create table public.device_tokens (
    token       text primary key,
    user_id     uuid not null references public.profiles (id) on delete cascade,
    platform    text not null check (platform in ('android', 'ios')),
    updated_at  timestamptz not null default now()
);
create index on public.device_tokens (user_id);

create table public.notification_outbox (
    id          bigint generated always as identity primary key,
    user_ids    uuid[] not null,
    title       text not null,
    body        text not null,
    data        jsonb not null default '{}'::jsonb,
    created_at  timestamptz not null default now(),
    sent_at     timestamptz,
    sent_count  int,
    error       text
);

-- Only the Edge Function (service role) touches these directly.
alter table public.device_tokens       enable row level security;
alter table public.notification_outbox enable row level security;

create policy "read own device tokens" on public.device_tokens for select to authenticated
    using (user_id = auth.uid());

-- ─────────────────────────────────────────────────────────────
-- Token registration (called by the app)
-- ─────────────────────────────────────────────────────────────

-- A token belongs to one device; if another account signs in on that device, it moves.
create or replace function public.register_device_token(p_token text, p_platform text)
returns void language plpgsql security definer set search_path = public as $$
begin
    if auth.uid() is null then raise exception 'Not signed in'; end if;
    if char_length(coalesce(p_token, '')) < 20 then raise exception 'Invalid token'; end if;
    insert into device_tokens (token, user_id, platform, updated_at)
    values (p_token, auth.uid(), p_platform, now())
    on conflict (token) do update
        set user_id = excluded.user_id, platform = excluded.platform, updated_at = now();
end;
$$;

create or replace function public.unregister_device_token(p_token text)
returns void language sql security definer set search_path = public as $$
    delete from device_tokens where token = p_token and user_id = auth.uid();
$$;

grant execute on function public.register_device_token(text, text) to authenticated;
grant execute on function public.unregister_device_token(text) to authenticated;

-- ─────────────────────────────────────────────────────────────
-- Internal helpers (not callable from the app)
-- ─────────────────────────────────────────────────────────────

create or replace function public.enqueue_push(p_user_ids uuid[], p_title text, p_body text, p_data jsonb)
returns void language plpgsql security definer set search_path = public as $$
declare
    ids uuid[];
begin
    select array_agg(distinct u) into ids from unnest(p_user_ids) u where u is not null;
    if ids is null or array_length(ids, 1) is null then return; end if;
    insert into notification_outbox (user_ids, title, body, data)
    values (ids, left(p_title, 120), left(p_body, 240), coalesce(p_data, '{}'::jsonb));
end;
$$;

-- Approved members of a society, optionally excluding one user (the person who acted).
create or replace function public.push_members(p_society_id uuid, p_exclude uuid default null)
returns uuid[] language sql stable security definer set search_path = public as $$
    select coalesce(array_agg(user_id), '{}')
    from memberships
    where society_id = p_society_id and status = 'approved'
      and user_id is distinct from p_exclude;
$$;

create or replace function public.push_admins(p_society_id uuid)
returns uuid[] language sql stable security definer set search_path = public as $$
    select coalesce(array_agg(user_id), '{}')
    from memberships
    where society_id = p_society_id and status = 'approved' and role = 'admin';
$$;

create or replace function public.push_flat(p_society_id uuid, p_flat_no text)
returns uuid[] language sql stable security definer set search_path = public as $$
    select coalesce(array_agg(user_id), '{}')
    from memberships
    where society_id = p_society_id and status = 'approved' and flat_no = p_flat_no;
$$;

-- 250050 → '₹2500.50', 250000 → '₹2500'
create or replace function public.push_inr(p_paise bigint)
returns text language sql immutable as $$
    select '₹' || case when p_paise % 100 = 0
                       then (p_paise / 100)::text
                       else to_char(p_paise / 100.0, 'FM999999999990.00') end;
$$;

-- ─────────────────────────────────────────────────────────────
-- Triggers: who hears about what
-- ─────────────────────────────────────────────────────────────

-- New notice → every member except the author
create or replace function public.push_on_notice()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    perform enqueue_push(
        push_members(new.society_id, new.author_id),
        '📢 ' || new.title,
        coalesce(nullif(left(new.body, 200), ''), 'New notice from your society'),
        jsonb_build_object('type', 'notice', 'id', new.id::text, 'society_id', new.society_id::text)
    );
    return new;
end;
$$;
create trigger notices_push after insert on public.notices
    for each row execute function public.push_on_notice();

-- New bill → that flat. Payment reported → admins. Verified / rejected → that flat.
create or replace function public.push_on_due()
returns trigger language plpgsql security definer set search_path = public as $$
declare
    payload jsonb := jsonb_build_object('type', 'dues', 'id', new.id::text, 'society_id', new.society_id::text);
begin
    if tg_op = 'INSERT' then
        perform enqueue_push(
            push_flat(new.society_id, new.flat_no),
            'New bill: ' || new.title,
            push_inr(new.amount_paise) || ' due by ' || to_char(new.due_date, 'FMDD Mon') || '. Tap to pay via UPI.',
            payload);
    elsif new.status is distinct from old.status then
        if new.status = 'submitted' then
            perform enqueue_push(
                push_admins(new.society_id),
                'Payment reported · Flat ' || new.flat_no,
                push_inr(new.amount_paise) || ' for ' || new.title || '. Tap to verify.',
                payload);
        elsif new.status = 'paid' then
            perform enqueue_push(
                push_flat(new.society_id, new.flat_no),
                'Payment confirmed ✅',
                push_inr(new.amount_paise) || ' for ' || new.title || ' has been received.',
                payload);
        elsif new.status = 'pending' and old.status = 'submitted' then
            perform enqueue_push(
                push_flat(new.society_id, new.flat_no),
                'Payment not received',
                'The treasurer couldn''t confirm your payment for ' || new.title || '. Please check and try again.',
                payload);
        end if;
    end if;
    return new;
end;
$$;
create trigger dues_push after insert or update of status on public.dues
    for each row execute function public.push_on_due();

-- New complaint → admins. Status change → the resident who raised it.
create or replace function public.push_on_complaint()
returns trigger language plpgsql security definer set search_path = public as $$
declare
    payload jsonb := jsonb_build_object('type', 'complaint', 'id', new.id::text, 'society_id', new.society_id::text);
begin
    if tg_op = 'INSERT' then
        perform enqueue_push(
            push_admins(new.society_id),
            'New complaint · ' || new.category,
            'Flat ' || new.flat_no || ': ' || left(new.description, 160),
            payload);
    elsif new.status is distinct from old.status then
        perform enqueue_push(
            array[new.raised_by],
            'Complaint update · ' || new.category,
            'Your complaint is now ' ||
                case new.status when 'in_progress' then 'in progress' when 'resolved' then 'resolved ✅' else 'open' end || '.',
            payload);
    end if;
    return new;
end;
$$;
create trigger complaints_push after insert or update of status on public.complaints
    for each row execute function public.push_on_complaint();

-- New poll → every member except the creator
create or replace function public.push_on_poll()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    perform enqueue_push(
        push_members(new.society_id, new.created_by),
        '🗳️ New poll',
        new.question || ' — one vote per flat.',
        jsonb_build_object('type', 'poll', 'id', new.id::text, 'society_id', new.society_id::text));
    return new;
end;
$$;
create trigger polls_push after insert on public.polls
    for each row execute function public.push_on_poll();

-- Chat message → every member except the sender (sender_name/flat_no are filled by a BEFORE trigger)
create or replace function public.push_on_message()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    perform enqueue_push(
        push_members(new.society_id, new.sender_id),
        new.sender_name || case when new.flat_no <> '' then ' · ' || new.flat_no else '' end,
        left(new.body, 200),
        jsonb_build_object('type', 'chat', 'id', new.id::text, 'society_id', new.society_id::text));
    return new;
end;
$$;
create trigger messages_push after insert on public.messages
    for each row execute function public.push_on_message();

-- Join request → admins. Approved → the new member.
create or replace function public.push_on_membership()
returns trigger language plpgsql security definer set search_path = public as $$
declare
    member_name text;
    society_name text;
begin
    select full_name into member_name from profiles where id = new.user_id;
    select name into society_name from societies where id = new.society_id;
    if new.status = 'pending' and (tg_op = 'INSERT' or old.status is distinct from 'pending') then
        perform enqueue_push(
            push_admins(new.society_id),
            'New join request',
            coalesce(member_name, 'A resident') || ' (flat ' || new.flat_no || ') wants to join. Tap to review.',
            jsonb_build_object('type', 'member', 'society_id', new.society_id::text));
    elsif tg_op = 'UPDATE' and new.status = 'approved' and old.status = 'pending' then
        perform enqueue_push(
            array[new.user_id],
            'You''re in! 🎉',
            'Your request to join ' || coalesce(society_name, 'the society') || ' was approved.',
            jsonb_build_object('type', 'approved', 'society_id', new.society_id::text));
    end if;
    return new;
end;
$$;
create trigger memberships_push after insert or update of status on public.memberships
    for each row execute function public.push_on_membership();

-- ─────────────────────────────────────────────────────────────
-- Daily dues reminders (schedule with Supabase Cron, see README)
-- ─────────────────────────────────────────────────────────────

-- Reminds each flat about bills due in 2 days, due today, and 3 days overdue.
-- Returns the number of reminders queued.
create or replace function public.enqueue_due_reminders()
returns int language plpgsql security definer set search_path = public as $$
declare
    today date := (now() at time zone 'Asia/Kolkata')::date;
    d record;
    n int := 0;
begin
    for d in
        select * from dues
        where status = 'pending' and due_date in (today + 2, today, today - 3)
    loop
        perform enqueue_push(
            push_flat(d.society_id, d.flat_no),
            case
                when d.due_date = today + 2 then 'Due in 2 days: ' || d.title
                when d.due_date = today then 'Due today: ' || d.title
                else 'Overdue: ' || d.title
            end,
            push_inr(d.amount_paise) || ' for flat ' || d.flat_no || '. Tap to pay via UPI.',
            jsonb_build_object('type', 'dues', 'id', d.id::text, 'society_id', d.society_id::text));
        n := n + 1;
    end loop;
    return n;
end;
$$;

-- Internal functions: not callable through the API by app users.
revoke execute on function public.enqueue_push(uuid[], text, text, jsonb) from public, anon, authenticated;
revoke execute on function public.push_members(uuid, uuid) from public, anon, authenticated;
revoke execute on function public.push_admins(uuid) from public, anon, authenticated;
revoke execute on function public.push_flat(uuid, text) from public, anon, authenticated;
revoke execute on function public.enqueue_due_reminders() from public, anon, authenticated;
revoke execute on function public.push_on_notice() from public, anon, authenticated;
revoke execute on function public.push_on_due() from public, anon, authenticated;
revoke execute on function public.push_on_complaint() from public, anon, authenticated;
revoke execute on function public.push_on_poll() from public, anon, authenticated;
revoke execute on function public.push_on_message() from public, anon, authenticated;
revoke execute on function public.push_on_membership() from public, anon, authenticated;
