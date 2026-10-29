-- Notiz Phase 2: receipts
--
-- Every verified payment gets a receipt number, sequential per society (R-00001, R-00002, …),
-- assigned once and never reused. Also records who reported the payment (shown on the receipt)
-- and lets admins mark cash / cheque payments as paid directly.

alter table public.societies
    add column next_receipt_no int not null default 1;

alter table public.dues
    add column receipt_no   int,
    add column submitted_by uuid references public.profiles (id) on delete set null;

create unique index dues_society_receipt_no on public.dues (society_id, receipt_no)
    where receipt_no is not null;

-- Number payments already marked paid, oldest first, and move each society's counter past them.
with numbered as (
    select id, row_number() over (partition by society_id order by paid_at nulls first, created_at) as rn
    from public.dues
    where status = 'paid'
)
update public.dues d set receipt_no = n.rn from numbered n where d.id = n.id;

update public.societies s
set next_receipt_no = coalesce((select max(receipt_no) from public.dues where society_id = s.id), 0) + 1;

-- Resident reports a UPI payment; remember who did it.
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
    update dues set status = 'submitted', utr = trim(p_utr), submitted_by = auth.uid() where id = p_due_id;
end;
$$;

-- Admin confirms money received (from a reported UPI payment, or directly for cash / cheque),
-- or sends a reported payment back to unpaid. Approving assigns the next receipt number.
create or replace function public.verify_payment(p_due_id uuid, p_approve boolean)
returns void language plpgsql security definer set search_path = public as $$
declare
    d dues;
    rno int;
begin
    select * into d from dues where id = p_due_id for update;
    if d.id is null or not is_admin(d.society_id) then raise exception 'Admins only'; end if;

    if p_approve then
        if d.status = 'paid' then return; end if;   -- already has a receipt; don't number twice
        update societies set next_receipt_no = next_receipt_no + 1
        where id = d.society_id
        returning next_receipt_no - 1 into rno;
        update dues set status = 'paid', paid_at = now(), receipt_no = rno where id = p_due_id;
    else
        if d.status <> 'submitted' then return; end if;
        update dues set status = 'pending', utr = null, submitted_by = null where id = p_due_id;
    end if;
end;
$$;

revoke execute on function public.submit_payment(uuid, text) from public, anon;
revoke execute on function public.verify_payment(uuid, boolean) from public, anon;
grant execute on function public.submit_payment(uuid, text) to authenticated;
grant execute on function public.verify_payment(uuid, boolean) to authenticated;
