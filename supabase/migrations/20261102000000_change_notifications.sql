-- Notiz: notify people about important changes, not only new things.
--
--   Dues        bill edited (amount, title or due date) → that flat
--   Members     join request rejected, made / no longer admin, flat number changed,
--               removed by an admin → that person; a member left → admins
--   Society     name, UPI ID or online payments changed → every member (except the admin who did it)
--
-- Pushes of type 'society' tell the app to reload who/where the user is (flat, society, removed).
-- Run after 20261101000000_admin_tools.sql.

-- ─────────────────────────────────────────────────────────────
-- Dues: add "Bill updated" to the existing trigger
-- ─────────────────────────────────────────────────────────────

create or replace function public.push_on_due()
returns trigger language plpgsql security definer set search_path = public as $$
declare
    payload jsonb := jsonb_build_object('type', 'dues', 'id', new.id::text, 'society_id', new.society_id::text);
    changes text[] := '{}';
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
        elsif new.status = 'cancelled' then
            perform enqueue_push(
                push_flat(new.society_id, new.flat_no),
                'Bill cancelled: ' || new.title,
                'The committee cancelled this bill. Nothing to pay.',
                payload);
        end if;
    elsif new.status in ('pending', 'submitted') then
        -- Edited by the committee (edit_bill)
        if new.amount_paise is distinct from old.amount_paise then
            changes := changes || ('amount is now ' || push_inr(new.amount_paise) || ' (was ' || push_inr(old.amount_paise) || ')');
        end if;
        if new.due_date is distinct from old.due_date then
            changes := changes || ('due date is now ' || to_char(new.due_date, 'FMDD Mon'));
        end if;
        if new.title is distinct from old.title then
            changes := changes || ('renamed from "' || old.title || '"');
        end if;
        if cardinality(changes) > 0 then
            perform enqueue_push(
                push_flat(new.society_id, new.flat_no),
                'Bill updated: ' || new.title,
                upper(left(array_to_string(changes, ', '), 1)) || substr(array_to_string(changes, ', '), 2) || '.',
                payload);
        end if;
    end if;
    return new;
end;
$$;
revoke execute on function public.push_on_due() from public, anon, authenticated;

drop trigger if exists dues_push on public.dues;
create trigger dues_push after insert or update of status, amount_paise, title, due_date on public.dues
    for each row execute function public.push_on_due();

-- ─────────────────────────────────────────────────────────────
-- Memberships
-- ─────────────────────────────────────────────────────────────

create or replace function public.push_on_membership()
returns trigger language plpgsql security definer set search_path = public as $$
declare
    member_name text;
    society_name text;
    me jsonb := jsonb_build_object('type', 'society', 'society_id', new.society_id::text);
begin
    select full_name into member_name from profiles where id = new.user_id;
    select name into society_name from societies where id = new.society_id;
    society_name := coalesce(society_name, 'the society');

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
            'Your request to join ' || society_name || ' was approved.',
            jsonb_build_object('type', 'approved', 'society_id', new.society_id::text));
    elsif tg_op = 'UPDATE' and new.status = 'rejected' and old.status = 'pending' then
        perform enqueue_push(
            array[new.user_id],
            'Join request not approved',
            'Your request to join ' || society_name || ' wasn''t approved. Check the invite code and flat number with your secretary.',
            me);
    elsif tg_op = 'UPDATE' and new.status = 'approved' then
        if new.role is distinct from old.role then
            perform enqueue_push(
                array[new.user_id],
                case when new.role = 'admin' then 'You''re now an admin' else 'You''re no longer an admin' end,
                case when new.role = 'admin'
                     then 'You can now post notices, raise bills and approve members in ' || society_name || '.'
                     else 'Your admin access in ' || society_name || ' was removed.' end,
                me);
        end if;
        if new.flat_no is distinct from old.flat_no then
            perform enqueue_push(
                array[new.user_id],
                'Flat number updated',
                'Your flat in ' || society_name || ' is now ' || new.flat_no || ' (was ' || old.flat_no || ').',
                me);
        end if;
    end if;
    return new;
end;
$$;
revoke execute on function public.push_on_membership() from public, anon, authenticated;

drop trigger if exists memberships_push on public.memberships;
create trigger memberships_push after insert or update of status, role, flat_no on public.memberships
    for each row execute function public.push_on_membership();

-- Removed by an admin → tell them. Left by themselves (or deleted their account) → tell the admins.
create or replace function public.push_on_membership_removed()
returns trigger language plpgsql security definer set search_path = public as $$
declare
    member_name text;
    society_name text;
begin
    if old.status <> 'approved' then return old; end if;
    select full_name into member_name from profiles where id = old.user_id;
    select name into society_name from societies where id = old.society_id;

    if auth.uid() is not null and auth.uid() <> old.user_id then
        perform enqueue_push(
            array[old.user_id],
            'Removed from ' || coalesce(society_name, 'your society'),
            'An admin removed you from the society. Ask the committee if this is a mistake.',
            jsonb_build_object('type', 'society', 'society_id', old.society_id::text));
    else
        perform enqueue_push(
            push_admins(old.society_id),
            'Member left · Flat ' || old.flat_no,
            coalesce(member_name, 'A member') || ' (flat ' || old.flat_no || ') left ' || coalesce(society_name, 'the society') || '.',
            jsonb_build_object('type', 'member', 'society_id', old.society_id::text));
    end if;
    return old;
end;
$$;
revoke execute on function public.push_on_membership_removed() from public, anon, authenticated;

create trigger memberships_removed_push after delete on public.memberships
    for each row execute function public.push_on_membership_removed();

-- ─────────────────────────────────────────────────────────────
-- Society details everyone relies on
-- ─────────────────────────────────────────────────────────────

create or replace function public.push_on_society()
returns trigger language plpgsql security definer set search_path = public as $$
declare
    payload jsonb := jsonb_build_object('type', 'society', 'society_id', new.id::text);
    recipients uuid[] := push_members(new.id, auth.uid());
begin
    if new.name is distinct from old.name then
        perform enqueue_push(
            recipients,
            'Society renamed',
            '"' || old.name || '" is now "' || new.name || '" on Notiz.',
            payload);
    end if;
    if new.upi_vpa is distinct from old.upi_vpa and coalesce(new.upi_vpa, '') <> '' then
        perform enqueue_push(
            recipients,
            '💳 New UPI ID for dues',
            'Pay ' || new.name || ' dues to ' || new.upi_vpa || ' from now on' ||
                case when coalesce(old.upi_vpa, '') <> '' then ' (not ' || old.upi_vpa || ')' else '' end || '.',
            payload);
    end if;
    if new.online_payments_enabled is distinct from old.online_payments_enabled then
        perform enqueue_push(
            recipients,
            case when new.online_payments_enabled then 'Pay dues online ✅' else 'Online payments paused' end,
            case when new.online_payments_enabled
                 then 'You can now pay ' || new.name || ' dues online in Notiz — confirmed instantly, no UTR needed.'
                 else 'Online payments are paused for now. Pay via UPI and report the UTR in Notiz.' end,
            payload);
    end if;
    return new;
end;
$$;
revoke execute on function public.push_on_society() from public, anon, authenticated;

create trigger societies_push after update of name, upi_vpa, online_payments_enabled on public.societies
    for each row execute function public.push_on_society();
