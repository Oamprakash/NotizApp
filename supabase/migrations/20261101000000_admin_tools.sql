-- Notiz Sprint 1: admin tools and account deletion
--   1. Cancel or edit a bill (unpaid dues only)
--   2. Edit society settings, new invite code
--   3. Remove a member, change a member's flat, leave a society
--   4. Delete my account (Play Store requirement) — done by the delete-account Edge Function

-- ─────────────────────────────────────────────────────────────
-- 1. Bills: cancel / edit
-- ─────────────────────────────────────────────────────────────

-- 'cancelled' dues stay in the table (history, and an already-open Razorpay link can still be paid
-- and recorded) but the app hides them.
alter table public.dues drop constraint dues_status_check;
alter table public.dues add constraint dues_status_check
    check (status in ('pending', 'submitted', 'paid', 'cancelled'));

-- Cancel one flat's unpaid due.
create or replace function public.cancel_due(p_due_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
    d dues;
begin
    select * into d from dues where id = p_due_id;
    if d.id is null or not is_admin(d.society_id) then raise exception 'Admins only'; end if;
    if d.status <> 'pending' then raise exception 'Only unpaid dues can be cancelled'; end if;
    update dues set status = 'cancelled' where id = p_due_id;
end;
$$;

-- Cancel a whole bill: every still-unpaid due with this title and due date.
-- Paid and reported (to-verify) dues are left alone. Returns how many were cancelled.
create or replace function public.cancel_bill(p_society_id uuid, p_title text, p_due_date date)
returns int language plpgsql security definer set search_path = public as $$
declare
    n int;
begin
    if not is_admin(p_society_id) then raise exception 'Admins only'; end if;
    update dues set status = 'cancelled'
    where society_id = p_society_id and title = p_title and due_date = p_due_date and status = 'pending';
    get diagnostics n = row_count;
    return n;
end;
$$;

-- Fix a bill's title, amount or due date on every still-unpaid due. An open Razorpay link is
-- dropped so the next "Pay online" uses the new amount. Returns how many dues changed.
create or replace function public.edit_bill(
    p_society_id uuid, p_title text, p_due_date date,
    p_new_title text, p_new_amount_paise bigint, p_new_due_date date
) returns int language plpgsql security definer set search_path = public as $$
declare
    n int;
begin
    if not is_admin(p_society_id) then raise exception 'Admins only'; end if;
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
$$;

-- A cancelled due can't be reported or confirmed by an admin; an online payment that still
-- arrives on an old link is recorded (the money was received).
create or replace function public.verify_payment(p_due_id uuid, p_approve boolean)
returns void language plpgsql security definer set search_path = public as $$
declare
    d dues;
begin
    select * into d from dues where id = p_due_id;
    if d.id is null or not is_admin(d.society_id) then raise exception 'Admins only'; end if;
    if d.status = 'cancelled' then raise exception 'This due was cancelled'; end if;

    if p_approve then
        perform mark_due_paid(p_due_id, case when d.status = 'submitted' then 'upi' else 'cash' end, null);
    elsif d.status = 'submitted' then
        update dues set status = 'pending', utr = null, submitted_by = null where id = p_due_id;
    end if;
end;
$$;

-- Tell the flat when its bill is cancelled (added to the existing dues push trigger).
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
        elsif new.status = 'cancelled' then
            perform enqueue_push(
                push_flat(new.society_id, new.flat_no),
                'Bill cancelled: ' || new.title,
                'The committee cancelled this bill. Nothing to pay.',
                payload);
        end if;
    end if;
    return new;
end;
$$;
revoke execute on function public.push_on_due() from public, anon, authenticated;

-- ─────────────────────────────────────────────────────────────
-- 2. Society settings
-- ─────────────────────────────────────────────────────────────

-- Admins change settings only through these functions (so receipt numbers, invite code and
-- the online-payments switch can't be edited directly).
drop policy if exists "admins update society" on public.societies;

create or replace function public.update_society_settings(
    p_society_id uuid, p_name text, p_city text, p_upi_vpa text, p_upi_payee_name text
) returns public.societies language plpgsql security definer set search_path = public as $$
declare
    s societies;
begin
    if not is_admin(p_society_id) then raise exception 'Admins only'; end if;
    if char_length(trim(coalesce(p_name, ''))) < 2 then raise exception 'Enter the society name'; end if;
    if coalesce(trim(p_upi_vpa), '') <> '' and trim(p_upi_vpa) !~ '^[A-Za-z0-9.\-_]{2,255}@[A-Za-z]{2,64}$' then
        raise exception 'UPI ID should look like society@okaxis';
    end if;
    update societies
    set name = trim(p_name),
        city = trim(coalesce(p_city, '')),
        upi_vpa = trim(coalesce(p_upi_vpa, '')),
        upi_payee_name = trim(coalesce(p_upi_payee_name, ''))
    where id = p_society_id
    returning * into s;
    return s;
end;
$$;

-- New invite code (e.g. if the old one was shared too widely). Existing members are unaffected.
create or replace function public.regenerate_invite_code(p_society_id uuid)
returns text language plpgsql security definer set search_path = public as $$
declare
    code text;
begin
    if not is_admin(p_society_id) then raise exception 'Admins only'; end if;
    loop
        code := upper(substr(replace(gen_random_uuid()::text, '-', ''), 1, 6));
        exit when not exists (select 1 from societies where invite_code = code);
    end loop;
    update societies set invite_code = code where id = p_society_id;
    return code;
end;
$$;

-- ─────────────────────────────────────────────────────────────
-- 3. Members
-- ─────────────────────────────────────────────────────────────

-- True if removing user p_user's admin role would leave approved members without any admin.
create or replace function public.is_last_admin(p_society_id uuid, p_user uuid)
returns boolean language sql stable security definer set search_path = public as $$
    select exists (
        select 1 from memberships
        where society_id = p_society_id and user_id = p_user and role = 'admin' and status = 'approved'
    )
    and not exists (
        select 1 from memberships
        where society_id = p_society_id and user_id <> p_user and role = 'admin' and status = 'approved'
    )
    and exists (
        select 1 from memberships
        where society_id = p_society_id and user_id <> p_user and status = 'approved'
    );
$$;

-- Admin removes someone (e.g. a tenant who moved out). Their flat's dues stay with the flat.
create or replace function public.remove_member(p_membership_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
    m memberships;
begin
    select * into m from memberships where id = p_membership_id;
    if m.id is null or not is_admin(m.society_id) then raise exception 'Admins only'; end if;
    if m.user_id = auth.uid() then raise exception 'Use Leave society to remove yourself'; end if;
    delete from memberships where id = p_membership_id;
end;
$$;

-- Admin corrects a member's flat number.
create or replace function public.set_member_flat(p_membership_id uuid, p_flat_no text)
returns void language plpgsql security definer set search_path = public as $$
declare
    m memberships;
begin
    select * into m from memberships where id = p_membership_id;
    if m.id is null or not is_admin(m.society_id) then raise exception 'Admins only'; end if;
    if char_length(trim(coalesce(p_flat_no, ''))) = 0 then raise exception 'Enter the flat number'; end if;
    update memberships set flat_no = upper(trim(p_flat_no)) where id = p_membership_id;
end;
$$;

-- Admins can't demote the last admin through the direct update either.
create or replace function public.guard_last_admin()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    if old.role = 'admin' and new.role <> 'admin' and is_last_admin(old.society_id, old.user_id) then
        raise exception 'Make another member admin first';
    end if;
    return new;
end;
$$;
create trigger memberships_guard_last_admin before update of role on public.memberships
    for each row execute function public.guard_last_admin();

-- Member leaves the society (moving out). The last admin must hand over first.
create or replace function public.leave_society(p_society_id uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
    if auth.uid() is null then raise exception 'Not signed in'; end if;
    if is_last_admin(p_society_id, auth.uid()) then raise exception 'Make another member admin first'; end if;
    delete from memberships where society_id = p_society_id and user_id = auth.uid();
end;
$$;

-- ─────────────────────────────────────────────────────────────
-- 4. Account deletion
-- ─────────────────────────────────────────────────────────────

-- Deleting a user cascades auth.users → profiles → memberships, device tokens, read receipts.
-- Content they created stays for the society, without the link to them.
alter table public.notices    alter column author_id  drop not null;
alter table public.complaints alter column raised_by  drop not null;
alter table public.polls      alter column created_by drop not null;
alter table public.poll_votes alter column voter_id   drop not null;
alter table public.messages   alter column sender_id  drop not null;
alter table public.societies  alter column created_by drop not null;

alter table public.notices    drop constraint notices_author_id_fkey,
    add constraint notices_author_id_fkey foreign key (author_id) references public.profiles (id) on delete set null;
alter table public.complaints drop constraint complaints_raised_by_fkey,
    add constraint complaints_raised_by_fkey foreign key (raised_by) references public.profiles (id) on delete set null;
alter table public.polls      drop constraint polls_created_by_fkey,
    add constraint polls_created_by_fkey foreign key (created_by) references public.profiles (id) on delete set null;
alter table public.poll_votes drop constraint poll_votes_voter_id_fkey,
    add constraint poll_votes_voter_id_fkey foreign key (voter_id) references public.profiles (id) on delete set null;
alter table public.messages   drop constraint messages_sender_id_fkey,
    add constraint messages_sender_id_fkey foreign key (sender_id) references public.profiles (id) on delete set null;
alter table public.societies  drop constraint societies_created_by_fkey,
    add constraint societies_created_by_fkey foreign key (created_by) references public.profiles (id) on delete set null;

-- Name of a society that would be left without an admin if this user were deleted, or null.
-- Called by the delete-account Edge Function (service role) before deleting.
create or replace function public.account_deletion_blocker(p_user uuid)
returns text language sql stable security definer set search_path = public as $$
    select s.name
    from memberships m
    join societies s on s.id = m.society_id
    where m.user_id = p_user and is_last_admin(m.society_id, p_user)
    limit 1;
$$;

-- ─────────────────────────────────────────────────────────────
-- Permissions
-- ─────────────────────────────────────────────────────────────

revoke execute on function public.is_last_admin(uuid, uuid) from public, anon, authenticated;
revoke execute on function public.guard_last_admin() from public, anon, authenticated;
revoke execute on function public.account_deletion_blocker(uuid) from public, anon, authenticated;
grant execute on function public.account_deletion_blocker(uuid) to service_role;

revoke execute on function public.cancel_due(uuid) from public, anon;
revoke execute on function public.cancel_bill(uuid, text, date) from public, anon;
revoke execute on function public.edit_bill(uuid, text, date, text, bigint, date) from public, anon;
revoke execute on function public.verify_payment(uuid, boolean) from public, anon;
revoke execute on function public.update_society_settings(uuid, text, text, text, text) from public, anon;
revoke execute on function public.regenerate_invite_code(uuid) from public, anon;
revoke execute on function public.remove_member(uuid) from public, anon;
revoke execute on function public.set_member_flat(uuid, text) from public, anon;
revoke execute on function public.leave_society(uuid) from public, anon;
grant execute on function public.cancel_due(uuid) to authenticated;
grant execute on function public.cancel_bill(uuid, text, date) to authenticated;
grant execute on function public.edit_bill(uuid, text, date, text, bigint, date) to authenticated;
grant execute on function public.verify_payment(uuid, boolean) to authenticated;
grant execute on function public.update_society_settings(uuid, text, text, text, text) to authenticated;
grant execute on function public.regenerate_invite_code(uuid) to authenticated;
grant execute on function public.remove_member(uuid) to authenticated;
grant execute on function public.set_member_flat(uuid, text) to authenticated;
grant execute on function public.leave_society(uuid) to authenticated;
