-- Notiz: demo society for Google Play reviewers and closed-test screenshots.
--
-- Fills a society called "Green Park Residency (Demo)" with neighbours, notices, a bill,
-- payments, help-desk requests, a poll, chat and a visitor waiting at the gate, and makes the
-- reviewer account its President so every screen has something on it.
--
-- How to use (see docs/CLOSED_TESTING.md, step 3):
--   1. Supabase → Authentication → Providers → Phone → "Test phone numbers": add
--      919000000001=123456  (no SMS is sent; the code is always 123456).
--   2. In the app, log in with 9000000001 / 123456 and enter the name "Play Reviewer".
--      Stop at the "Join or register a society" screen.
--   3. Run this whole file in the SQL editor. Run it again any time to reset the demo.
--   4. Reopen the app: you're the President of the demo society.
--
-- The demo neighbours are login-less users (no phone, no password); nobody can sign in as them.

do $$
declare
    reviewer_phone constant text := '919000000001';
    reviewer uuid;
    sid uuid;
    priya   constant uuid := 'd0000000-0000-4000-8000-000000000001';
    karthik constant uuid := 'd0000000-0000-4000-8000-000000000002';
    lakshmi constant uuid := 'd0000000-0000-4000-8000-000000000003';
    murugan constant uuid := 'd0000000-0000-4000-8000-000000000004';
    arun    constant uuid := 'd0000000-0000-4000-8000-000000000005';
    month_end date := (date_trunc('month', now() at time zone 'Asia/Kolkata') + interval '1 month - 1 day')::date;
    month_name text := to_char(now() at time zone 'Asia/Kolkata', 'FMMonth');
    poll_id uuid;
    opt_yes uuid;
    opt_no uuid;
    m1 uuid;
begin
    select id into reviewer from auth.users where phone = reviewer_phone;
    if reviewer is null then
        raise exception 'Log in once in the app with % first (see the steps at the top of this file)', reviewer_phone;
    end if;


    -- Login-less neighbours
    insert into auth.users (id, aud, role, created_at, updated_at)
    select u, 'authenticated', 'authenticated', now(), now()
    from unnest(array[priya, karthik, lakshmi, murugan, arun]) u
    on conflict (id) do nothing;
    insert into profiles (id, full_name, language) values
        (priya, 'Priya Raman', 'en'),
        (karthik, 'Karthik S', 'en'),
        (lakshmi, 'Lakshmi Narayanan', 'ta'),
        (murugan, 'Murugan (Security)', 'ta'),
        (arun, 'Arun Kumar', 'en')
    on conflict (id) do update set full_name = excluded.full_name;
    insert into profiles (id, full_name) values (reviewer, 'Play Reviewer') on conflict (id) do nothing;

    -- Demo data shouldn't send pushes or trip the "only the president can…" guards (we run as no one)
    alter table notices disable trigger user;
    alter table dues disable trigger user;
    alter table complaints disable trigger user;
    alter table polls disable trigger user;
    alter table messages disable trigger user;
    alter table memberships disable trigger user;

    -- Start clean: drop the previous demo society and the reviewer's other memberships
    delete from societies where invite_code = 'DEMO42';
    delete from memberships where user_id = reviewer;

    -- (societies triggers stay on: one of them sets up the default role permissions)
    insert into societies (name, city, upi_vpa, upi_payee_name, invite_code, created_by)
    values ('Green Park Residency (Demo)', 'Chennai', 'greenparkrwa@okaxis', 'Green Park RWA', 'DEMO42', reviewer)
    returning id into sid;

    insert into memberships (society_id, user_id, flat_no, role, status, created_at) values
        (sid, reviewer, 'A-101', 'president', 'approved', now() - interval '60 days'),
        (sid, priya,    'B-202', 'secretary', 'approved', now() - interval '58 days'),
        (sid, karthik,  'C-303', 'treasurer', 'approved', now() - interval '58 days'),
        (sid, lakshmi,  'D-404', 'resident',  'approved', now() - interval '40 days'),
        (sid, murugan,  '',      'security',  'approved', now() - interval '30 days'),
        (sid, arun,     'E-505', 'resident',  'pending',  now() - interval '2 hours');

    -- Notices
    insert into notices (society_id, author_id, title, body, pinned, created_at) values
        (sid, priya, 'Water tank cleaning on Sunday',
         'Overhead tanks will be cleaned this Sunday from 9 AM to 1 PM. Please store water on Saturday night.', true,
         now() - interval '1 day'),
        (sid, priya, 'Diwali get-together',
         'Join us on the terrace at 7 PM for sweets and music. Children''s rangoli contest at 6 PM.', false,
         now() - interval '4 days'),
        (sid, karthik, 'Lift maintenance',
         'Block B lift will be under maintenance on Wednesday, 10 AM to 4 PM.', false, now() - interval '9 days');
    insert into notice_reads (notice_id, user_id)
    select n.id, u from notices n, unnest(array[priya, karthik, lakshmi]) u where n.society_id = sid;

    -- This month's maintenance: one paid (with receipt), one to verify, two unpaid
    insert into dues (society_id, flat_no, title, amount_paise, due_date, status, created_at)
    select sid, f, month_name || ' maintenance', 250000, month_end, 'pending', now() - interval '6 days'
    from unnest(array['A-101', 'B-202', 'C-303', 'D-404']) f;
    update dues set status = 'paid', payment_mode = 'upi', utr = '431298765012', paid_at = now() - interval '3 days',
                    receipt_no = 1, submitted_by = priya
    where society_id = sid and flat_no = 'B-202';
    alter table societies disable trigger user;
    update societies set next_receipt_no = 2 where id = sid;
    alter table societies enable trigger user;
    update dues set status = 'submitted', payment_mode = 'upi', utr = '431299001122', submitted_by = karthik
    where society_id = sid and flat_no = 'C-303';
    -- Last month, all paid
    insert into dues (society_id, flat_no, title, amount_paise, due_date, status, payment_mode, paid_at, receipt_no, created_at)
    select sid, f, to_char((now() at time zone 'Asia/Kolkata') - interval '1 month', 'FMMonth') || ' maintenance', 250000,
           (month_end - interval '1 month')::date, 'paid', 'cash', now() - interval '35 days', 100 + n, now() - interval '40 days'
    from unnest(array['A-101', 'B-202', 'C-303', 'D-404']) with ordinality as t(f, n);

    -- Help desk
    insert into complaints (society_id, raised_by, flat_no, category, description, status, created_at, updated_at) values
        (sid, lakshmi, 'D-404', 'Plumbing', 'Kitchen tap is leaking since yesterday.', 'open', now() - interval '5 hours', now() - interval '5 hours'),
        (sid, karthik, 'C-303', 'Electrical', 'Corridor light outside C-303 is not working.', 'in_progress', now() - interval '2 days', now() - interval '1 day'),
        (sid, reviewer, 'A-101', 'Lift', 'Lift door closes too fast.', 'resolved', now() - interval '12 days', now() - interval '10 days');

    -- Poll
    insert into polls (society_id, created_by, question, created_at)
    values (sid, priya, 'Should we install CCTV at the back gate?', now() - interval '2 days')
    returning id into poll_id;
    insert into poll_options (poll_id, label, position) values (poll_id, 'Yes', 0) returning id into opt_yes;
    insert into poll_options (poll_id, label, position) values (poll_id, 'No', 1) returning id into opt_no;
    insert into poll_options (poll_id, label, position) values (poll_id, 'Need more details', 2);
    insert into poll_votes (poll_id, option_id, flat_no, voter_id) values
        (poll_id, opt_yes, 'B-202', priya), (poll_id, opt_yes, 'C-303', karthik), (poll_id, opt_no, 'D-404', lakshmi);

    -- Chat
    insert into messages (society_id, sender_id, sender_name, flat_no, body, created_at) values
        (sid, priya, 'Priya Raman', 'B-202', 'Good morning everyone! Reminder: water tank cleaning this Sunday.', now() - interval '3 hours')
    returning id into m1;
    insert into messages (society_id, sender_id, sender_name, flat_no, body, created_at, reply_to_id, reply_sender_name, reply_body) values
        (sid, lakshmi, 'Lakshmi Narayanan', 'D-404', 'Thanks! Will the motor be off the whole morning?', now() - interval '2 hours 50 minutes',
         m1, 'Priya Raman', 'Good morning everyone! Reminder: water tank cleaning this Sunday.'),
        (sid, priya, 'Priya Raman', 'B-202', 'Only 9 to 1. Should be back by lunch 🙂', now() - interval '2 hours 40 minutes', null, null, null),
        (sid, karthik, 'Karthik S', 'C-303', 'Maintenance for this month is up. UPI ID is in the Dues tab.', now() - interval '1 hour', null, null, null);

    alter table notices enable trigger user;
    alter table dues enable trigger user;
    alter table complaints enable trigger user;
    alter table polls enable trigger user;
    alter table messages enable trigger user;
    alter table memberships enable trigger user;

    -- Gate: a delivery waiting for the reviewer's flat, one visitor inside, earlier visitors
    insert into gate_entries (society_id, flat_no, category, visitor_name, company, status, logged_by, created_at) values
        (sid, 'A-101', 'delivery', 'Ravi', 'Swiggy', 'waiting', murugan, now() - interval '2 minutes');
    insert into gate_entries (society_id, flat_no, category, visitor_name, vehicle_no, status, logged_by,
                              decided_by, decided_by_name, decided_at, entered_at, created_at) values
        (sid, 'D-404', 'guest', 'Meena', 'TN09 AB 1234', 'inside', murugan,
         lakshmi, 'Lakshmi Narayanan', now() - interval '40 minutes', now() - interval '39 minutes', now() - interval '41 minutes');
    insert into gate_entries (society_id, flat_no, category, visitor_name, company, status, logged_by, entered_at, exited_at, created_at) values
        (sid, 'B-202', 'cab', '', 'Uber', 'left', murugan, now() - interval '3 hours', now() - interval '3 hours', now() - interval '3 hours'),
        (sid, 'C-303', 'service', 'Selvi', 'Maid', 'left', murugan, now() - interval '5 hours', now() - interval '4 hours', now() - interval '5 hours');
    insert into gate_passes (society_id, flat_no, created_by, created_by_name, guest_name, category, code, valid_on)
    values (sid, 'A-101', reviewer, 'Play Reviewer', 'Amma', 'guest', '482913', gate_today());

    raise notice 'Demo society ready (invite code DEMO42). Reopen the app.';
end $$;
