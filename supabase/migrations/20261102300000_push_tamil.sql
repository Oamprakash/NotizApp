-- Notiz: push notifications in each person's language (English or Tamil).
--
-- The app saves the language the user picked to profiles.language. Every trigger now writes
-- both an English and a Tamil text; push_l() splits the recipients by language and queues one
-- outbox row per language. Names, bill titles and messages stay as people typed them.
--
-- This replaces every push trigger function from the earlier migrations (same triggers).
-- Run after 20261102200000_chat_moderation.sql.

alter table public.profiles add column language text not null default 'en' check (language in ('en', 'ta'));

-- ─────────────────────────────────────────────────────────────
-- Helpers
-- ─────────────────────────────────────────────────────────────

-- Queue a notification in English for 'en' users and in Tamil for 'ta' users.
create or replace function public.push_l(
    p_user_ids uuid[], p_title_en text, p_body_en text, p_title_ta text, p_body_ta text, p_data jsonb
) returns void language plpgsql security definer set search_path = public as $$
declare
    en uuid[];
    ta uuid[];
begin
    select array_agg(u) filter (where coalesce(p.language, 'en') <> 'ta'),
           array_agg(u) filter (where p.language = 'ta')
      into en, ta
      from (select distinct x as u from unnest(p_user_ids) x where x is not null) ids
      left join profiles p on p.id = ids.u;
    if en is not null then perform enqueue_push(en, p_title_en, p_body_en, p_data); end if;
    if ta is not null then
        perform enqueue_push(ta, coalesce(p_title_ta, p_title_en), coalesce(p_body_ta, p_body_en), p_data);
    end if;
end;
$$;

-- '2026-11-05' → '5 நவ'
create or replace function public.push_date_ta(p_date date)
returns text language sql immutable as $$
    select extract(day from p_date)::int || ' ' ||
        (array['ஜன', 'பிப்', 'மார்ச்', 'ஏப்', 'மே', 'ஜூன்', 'ஜூலை', 'ஆக', 'செப்', 'அக்', 'நவ', 'டிச'])[extract(month from p_date)::int];
$$;

create or replace function public.push_date_en(p_date date)
returns text language sql immutable as $$
    select to_char(p_date, 'FMDD Mon');
$$;

-- Complaint categories are stored in English (see ComplaintRepository.categories)
create or replace function public.push_category_ta(p_category text)
returns text language sql immutable as $$
    select case p_category
        when 'Plumbing' then 'குழாய்'
        when 'Electrical' then 'மின்சாரம்'
        when 'Lift' then 'லிஃப்ட்'
        when 'Security' then 'பாதுகாப்பு'
        when 'Cleaning' then 'சுத்தம்'
        when 'Parking' then 'வாகன நிறுத்தம்'
        when 'Other' then 'மற்றவை'
        else p_category end;
$$;

-- ─────────────────────────────────────────────────────────────
-- Notices
-- ─────────────────────────────────────────────────────────────

create or replace function public.push_on_notice()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    perform push_l(
        push_members(new.society_id, new.author_id),
        '📢 ' || new.title,
        coalesce(nullif(left(new.body, 200), ''), 'New notice from your society'),
        '📢 ' || new.title,
        coalesce(nullif(left(new.body, 200), ''), 'உங்கள் சங்கத்திலிருந்து புதிய அறிவிப்பு'),
        jsonb_build_object('type', 'notice', 'id', new.id::text, 'society_id', new.society_id::text));
    return new;
end;
$$;

-- ─────────────────────────────────────────────────────────────
-- Dues
-- ─────────────────────────────────────────────────────────────

create or replace function public.push_on_due()
returns trigger language plpgsql security definer set search_path = public as $$
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
            perform push_l(push_admins(new.society_id),
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
$$;

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
        perform push_l(
            push_flat(d.society_id, d.flat_no),
            case
                when d.due_date = today + 2 then 'Due in 2 days: '
                when d.due_date = today then 'Due today: '
                else 'Overdue: '
            end || d.title,
            push_inr(d.amount_paise) || ' for flat ' || d.flat_no || '. Tap to pay via UPI.',
            case
                when d.due_date = today + 2 then 'இன்னும் 2 நாளில்: '
                when d.due_date = today then 'இன்று கடைசி நாள்: '
                else 'தாமதம்: '
            end || d.title,
            push_inr(d.amount_paise) || ' — வீடு ' || d.flat_no || '. செலுத்த தட்டவும்.',
            jsonb_build_object('type', 'dues', 'id', d.id::text, 'society_id', d.society_id::text));
        n := n + 1;
    end loop;
    return n;
end;
$$;

-- ─────────────────────────────────────────────────────────────
-- Complaints and polls
-- ─────────────────────────────────────────────────────────────

create or replace function public.push_on_complaint()
returns trigger language plpgsql security definer set search_path = public as $$
declare
    payload jsonb := jsonb_build_object('type', 'complaint', 'id', new.id::text, 'society_id', new.society_id::text);
begin
    if tg_op = 'INSERT' then
        perform push_l(push_admins(new.society_id),
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
$$;

create or replace function public.push_on_poll()
returns trigger language plpgsql security definer set search_path = public as $$
begin
    perform push_l(
        push_members(new.society_id, new.created_by),
        '🗳️ New poll',
        new.question || ' — one vote per flat.',
        '🗳️ புதிய வாக்கெடுப்பு',
        new.question || ' — ஒரு வீட்டுக்கு ஒரு வாக்கு.',
        jsonb_build_object('type', 'poll', 'id', new.id::text, 'society_id', new.society_id::text));
    return new;
end;
$$;

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

    if new.status = 'pending' and (tg_op = 'INSERT' or old.status is distinct from 'pending') then
        perform push_l(push_admins(new.society_id),
            'New join request',
            coalesce(member_name, 'A resident') || ' (flat ' || new.flat_no || ') wants to join. Tap to review.',
            'புதிய சேர்க்கை கோரிக்கை',
            coalesce(member_name, 'ஒரு குடியிருப்பாளர்') || ' (வீடு ' || new.flat_no || ') சேர விரும்புகிறார். பார்க்க தட்டவும்.',
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
            if new.role = 'admin' then
                perform push_l(array[new.user_id],
                    'You''re now an admin',
                    'You can now post notices, raise bills and approve members in ' || coalesce(society_name, 'the society') || '.',
                    'நீங்கள் இப்போது நிர்வாகி',
                    coalesce(society_name, 'சங்கம்') || ' — இனி அறிவிப்புகள் இடலாம், பில் எழுப்பலாம், உறுப்பினர்களை ஏற்கலாம்.',
                    me);
            else
                perform push_l(array[new.user_id],
                    'You''re no longer an admin',
                    'Your admin access in ' || coalesce(society_name, 'the society') || ' was removed.',
                    'நிர்வாகி அனுமதி நீக்கப்பட்டது',
                    coalesce(society_name, 'சங்கம்') || ' — உங்கள் நிர்வாகி அனுமதி நீக்கப்பட்டது.',
                    me);
            end if;
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
$$;

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
        perform push_l(array[old.user_id],
            'Removed from ' || coalesce(society_name, 'your society'),
            'An admin removed you from the society. Ask the committee if this is a mistake.',
            coalesce(society_name, 'சங்கம்') || ' — நீக்கப்பட்டீர்கள்',
            'ஒரு நிர்வாகி உங்களைச் சங்கத்திலிருந்து நீக்கினார். தவறு என்றால் குழுவிடம் கேளுங்கள்.',
            jsonb_build_object('type', 'society', 'society_id', old.society_id::text));
    else
        perform push_l(push_admins(old.society_id),
            'Member left · Flat ' || old.flat_no,
            coalesce(member_name, 'A member') || ' (flat ' || old.flat_no || ') left ' || coalesce(society_name, 'the society') || '.',
            'உறுப்பினர் விலகினார் · வீடு ' || old.flat_no,
            coalesce(member_name, 'ஒரு உறுப்பினர்') || ' (வீடு ' || old.flat_no || ') சங்கத்திலிருந்து விலகினார்.',
            jsonb_build_object('type', 'member', 'society_id', old.society_id::text));
    end if;
    return old;
end;
$$;

-- ─────────────────────────────────────────────────────────────
-- Society
-- ─────────────────────────────────────────────────────────────

create or replace function public.push_on_society()
returns trigger language plpgsql security definer set search_path = public as $$
declare
    payload jsonb := jsonb_build_object('type', 'society', 'society_id', new.id::text);
    recipients uuid[] := push_members(new.id, auth.uid());
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
$$;

-- ─────────────────────────────────────────────────────────────
-- Chat moderation
-- ─────────────────────────────────────────────────────────────

create or replace function public.push_on_message_report()
returns trigger language plpgsql security definer set search_path = public as $$
declare
    m messages;
    who text;
begin
    select * into m from messages where id = new.message_id;
    who := m.sender_name || case when m.flat_no <> '' then ' · ' || m.flat_no else '' end;
    perform push_l(push_admins(new.society_id),
        '🚩 Message reported · ' || who,
        left(m.body, 160),
        '🚩 செய்தி மீது புகார் · ' || who,
        left(m.body, 160),
        jsonb_build_object('type', 'chat', 'id', m.id::text, 'society_id', new.society_id::text));
    return new;
end;
$$;

create or replace function public.push_on_chat_muted()
returns trigger language plpgsql security definer set search_path = public as $$
declare
    payload jsonb := jsonb_build_object('type', 'society', 'society_id', new.society_id::text);
begin
    if new.chat_muted is distinct from old.chat_muted and new.status = 'approved' then
        if new.chat_muted then
            perform push_l(array[new.user_id],
                'Muted in chat',
                'An admin muted you in the society chat. You can still read messages.',
                'அரட்டையில் முடக்கப்பட்டீர்கள்',
                'ஒரு நிர்வாகி உங்களைச் சங்க அரட்டையில் முடக்கினார். செய்திகளைப் படிக்கலாம்.',
                payload);
        else
            perform push_l(array[new.user_id],
                'You can chat again',
                'An admin unmuted you in the society chat.',
                'மீண்டும் அரட்டை செய்யலாம்',
                'ஒரு நிர்வாகி உங்களை அரட்டையில் மீண்டும் அனுமதித்தார்.',
                payload);
        end if;
    end if;
    return new;
end;
$$;

-- Chat messages (push_on_message) are people's own words: no translation needed.

revoke execute on function public.push_l(uuid[], text, text, text, text, jsonb) from public, anon, authenticated;
revoke execute on function public.push_on_notice() from public, anon, authenticated;
revoke execute on function public.push_on_due() from public, anon, authenticated;
revoke execute on function public.enqueue_due_reminders() from public, anon, authenticated;
revoke execute on function public.push_on_complaint() from public, anon, authenticated;
revoke execute on function public.push_on_poll() from public, anon, authenticated;
revoke execute on function public.push_on_membership() from public, anon, authenticated;
revoke execute on function public.push_on_membership_removed() from public, anon, authenticated;
revoke execute on function public.push_on_society() from public, anon, authenticated;
revoke execute on function public.push_on_message_report() from public, anon, authenticated;
revoke execute on function public.push_on_chat_muted() from public, anon, authenticated;
