-- Notiz Phase 2: online payments with Razorpay (money goes to each society's own account)
--
-- How it works:
--   1. An admin saves the society's Razorpay API keys (set_razorpay_keys). The key secret is
--      stored in payment_gateways, which no app user can read — only Edge Functions (service role).
--   2. The admin adds a Razorpay webhook (URL + secret shown in the app), then turns online
--      payments on (set_online_payments).
--   3. A resident taps "Pay online": the create-payment-link Edge Function creates a Razorpay
--      Payment Link for that due and the app opens it.
--   4. Razorpay calls the razorpay-webhook Edge Function, which verifies the signature and calls
--      record_online_payment: the due becomes paid with mode 'online' and gets a receipt number.
--      If a webhook is missed, the app's "check" call does the same from the link status.

alter table public.societies
    add column online_payments_enabled boolean not null default false;

create table public.payment_gateways (
    society_id      uuid primary key references public.societies (id) on delete cascade,
    provider        text not null default 'razorpay' check (provider = 'razorpay'),
    key_id          text not null check (key_id ~ '^rzp_(test|live)_[A-Za-z0-9]+$'),
    key_secret      text not null,
    webhook_secret  text not null,
    updated_by      uuid references public.profiles (id) on delete set null,
    updated_at      timestamptz not null default now()
);
-- No policies: app users can never read or write this table directly.
alter table public.payment_gateways enable row level security;
revoke all on public.payment_gateways from anon, authenticated;

-- Webhook events already handled (Razorpay may deliver the same event more than once)
create table public.gateway_events (
    event_id     text primary key,
    society_id   uuid references public.societies (id) on delete cascade,
    received_at  timestamptz not null default now()
);
alter table public.gateway_events enable row level security;
revoke all on public.gateway_events from anon, authenticated;

alter table public.dues
    add column payment_mode        text check (payment_mode in ('upi', 'cash', 'online')),
    add column payment_link_id     text,
    add column payment_link_url    text,
    add column gateway_payment_id  text unique;

update public.dues
set payment_mode = case when utr is not null then 'upi' else 'cash' end
where status = 'paid' and payment_mode is null;

-- ─────────────────────────────────────────────────────────────
-- One place that marks a due paid and issues its receipt number
-- ─────────────────────────────────────────────────────────────

create or replace function public.mark_due_paid(p_due_id uuid, p_mode text, p_reference text)
returns int language plpgsql security definer set search_path = public as $$
declare
    d dues;
    rno int;
begin
    select * into d from dues where id = p_due_id for update;
    if d.id is null then raise exception 'Due not found'; end if;
    if d.status = 'paid' then return d.receipt_no; end if;   -- idempotent: never number twice

    update societies set next_receipt_no = next_receipt_no + 1
    where id = d.society_id
    returning next_receipt_no - 1 into rno;

    update dues
    set status = 'paid',
        paid_at = now(),
        receipt_no = rno,
        payment_mode = p_mode,
        utr = coalesce(nullif(trim(p_reference), ''), utr)
    where id = p_due_id;
    return rno;
end;
$$;

-- Admin confirms money received: a reported UPI payment, or cash/cheque straight from unpaid.
create or replace function public.verify_payment(p_due_id uuid, p_approve boolean)
returns void language plpgsql security definer set search_path = public as $$
declare
    d dues;
begin
    select * into d from dues where id = p_due_id;
    if d.id is null or not is_admin(d.society_id) then raise exception 'Admins only'; end if;

    if p_approve then
        perform mark_due_paid(p_due_id, case when d.status = 'submitted' then 'upi' else 'cash' end, null);
    elsif d.status = 'submitted' then
        update dues set status = 'pending', utr = null, submitted_by = null where id = p_due_id;
    end if;
end;
$$;

-- ─────────────────────────────────────────────────────────────
-- Admin: gateway settings
-- ─────────────────────────────────────────────────────────────

-- Saves the Razorpay keys. Pass null/'' for p_key_secret to keep the saved one (e.g. when only
-- the key id changes). A webhook secret is generated on first save and kept afterwards.
create or replace function public.set_razorpay_keys(p_society_id uuid, p_key_id text, p_key_secret text)
returns void language plpgsql security definer set search_path = public as $$
declare
    existing payment_gateways;
begin
    if not is_admin(p_society_id) then raise exception 'Admins only'; end if;
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
$$;

-- What admins see: never the key secret. The webhook secret is shown so it can be pasted into
-- Razorpay's webhook settings.
create or replace function public.payment_gateway_status(p_society_id uuid)
returns table (configured boolean, key_id text, webhook_secret text, online_enabled boolean, test_mode boolean)
language plpgsql stable security definer set search_path = public as $$
#variable_conflict use_column
begin
    if not is_admin(p_society_id) then raise exception 'Admins only'; end if;
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
$$;

create or replace function public.set_online_payments(p_society_id uuid, p_enabled boolean)
returns void language plpgsql security definer set search_path = public as $$
begin
    if not is_admin(p_society_id) then raise exception 'Admins only'; end if;
    if p_enabled and not exists (select 1 from payment_gateways where society_id = p_society_id) then
        raise exception 'Save the Razorpay keys first';
    end if;
    update societies set online_payments_enabled = p_enabled where id = p_society_id;
end;
$$;

-- Turns online payments off and deletes the saved keys.
create or replace function public.remove_razorpay_keys(p_society_id uuid)
returns void language plpgsql security definer set search_path = public as $$
begin
    if not is_admin(p_society_id) then raise exception 'Admins only'; end if;
    update societies set online_payments_enabled = false where id = p_society_id;
    delete from payment_gateways where society_id = p_society_id;
end;
$$;

-- ─────────────────────────────────────────────────────────────
-- Edge Functions only (service role)
-- ─────────────────────────────────────────────────────────────

-- Records a captured Razorpay payment for a due. Safe to call more than once.
-- Returns the receipt number, or raises if the amount or link doesn't match.
create or replace function public.record_online_payment(
    p_due_id uuid, p_link_id text, p_payment_id text, p_amount_paise bigint, p_reference text
) returns int language plpgsql security definer set search_path = public as $$
declare
    d dues;
begin
    select * into d from dues where id = p_due_id;
    if d.id is null then raise exception 'Due not found'; end if;
    if d.payment_link_id is distinct from p_link_id then raise exception 'Payment link does not match this due'; end if;
    if d.status = 'paid' then return d.receipt_no; end if;
    if p_amount_paise < d.amount_paise then raise exception 'Paid amount is less than the due'; end if;

    update dues set gateway_payment_id = p_payment_id where id = p_due_id;
    return mark_due_paid(p_due_id, 'online', p_reference);
end;
$$;

revoke execute on function public.mark_due_paid(uuid, text, text) from public, anon, authenticated;
revoke execute on function public.record_online_payment(uuid, text, text, bigint, text) from public, anon, authenticated;
grant execute on function public.mark_due_paid(uuid, text, text) to service_role;
grant execute on function public.record_online_payment(uuid, text, text, bigint, text) to service_role;

revoke execute on function public.verify_payment(uuid, boolean) from public, anon;
revoke execute on function public.set_razorpay_keys(uuid, text, text) from public, anon;
revoke execute on function public.payment_gateway_status(uuid) from public, anon;
revoke execute on function public.set_online_payments(uuid, boolean) from public, anon;
revoke execute on function public.remove_razorpay_keys(uuid) from public, anon;
grant execute on function public.verify_payment(uuid, boolean) to authenticated;
grant execute on function public.set_razorpay_keys(uuid, text, text) to authenticated;
grant execute on function public.payment_gateway_status(uuid) to authenticated;
grant execute on function public.set_online_payments(uuid, boolean) to authenticated;
grant execute on function public.remove_razorpay_keys(uuid) to authenticated;
