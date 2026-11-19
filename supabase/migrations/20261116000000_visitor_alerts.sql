-- Notiz: visitor alerts that don't get missed.
--
-- • Visitor alerts carry Allow / Deny right on the notification (the app builds it on Android;
--   iOS uses the GATE_VISITOR category). Labels are added here in the person's language.
-- • When someone in the flat answers, or the guard lets the visitor in or sends them away, a
--   silent push stops the alert on every other phone in the flat.
-- • Residents can turn off chat and notice/poll notifications; visitor, dues, help-desk and
--   membership notifications always come.
-- • send_test_visitor_alert() sends yourself a test alert (Settings → Visitor alerts).
--
-- Run after 20261115000000_gate_photos.sql.

-- ─────────────────────────────────────────────────────────────
-- Notification preferences
-- ─────────────────────────────────────────────────────────────

alter table public.profiles add column push_chat boolean not null default true;
alter table public.profiles add column push_updates boolean not null default true;

-- Which preference (if any) can silence a push of this type
create or replace function public.push_pref_for(p_type text)
returns text language sql immutable as $$
    select case p_type
        when 'chat' then 'chat'
        when 'notice' then 'updates'
        when 'poll' then 'updates'
        else null
    end;
$$;

-- push_l: skips people who turned this kind off, and adds Allow / Deny labels to visitor alerts
create or replace function public.push_l(
    p_user_ids uuid[], p_title_en text, p_body_en text, p_title_ta text, p_body_ta text, p_data jsonb
) returns void language plpgsql security definer set search_path = public as $$
declare
    en uuid[];
    ta uuid[];
    pref text := push_pref_for(p_data->>'type');
    labels_en jsonb := '{}'::jsonb;
    labels_ta jsonb := '{}'::jsonb;
begin
    select array_agg(u) filter (where coalesce(p.language, 'en') <> 'ta'),
           array_agg(u) filter (where p.language = 'ta')
      into en, ta
      from (select distinct x as u from unnest(p_user_ids) x where x is not null) ids
      left join profiles p on p.id = ids.u
     where pref is null
        or (pref = 'chat' and coalesce(p.push_chat, true))
        or (pref = 'updates' and coalesce(p.push_updates, true));

    if p_data->>'type' in ('gate', 'gate_test') then
        labels_en := jsonb_build_object('l_allow', 'Allow', 'l_deny', 'Deny', 'l_allowed', 'Allowed. The gate has been told.',
            'l_denied', 'Denied. The gate has been told.', 'l_failed', 'Couldn''t send your answer. Open Notiz to try again.');
        labels_ta := jsonb_build_object('l_allow', 'அனுமதி', 'l_deny', 'மறு', 'l_allowed', 'அனுமதிக்கப்பட்டது. வாயிலுக்குத் தெரிவிக்கப்பட்டது.',
            'l_denied', 'மறுக்கப்பட்டது. வாயிலுக்குத் தெரிவிக்கப்பட்டது.', 'l_failed', 'பதிலை அனுப்ப முடியவில்லை. Notiz-ஐத் திறந்து மீண்டும் முயலவும்.');
    end if;

    if en is not null then perform enqueue_push(en, p_title_en, p_body_en, p_data || labels_en); end if;
    if ta is not null then
        perform enqueue_push(ta, coalesce(p_title_ta, p_title_en), coalesce(p_body_ta, p_body_en), p_data || labels_ta);
    end if;
end;
$$;

-- A push with no visible notification (the app acts on it quietly)
create or replace function public.push_silent(p_user_ids uuid[], p_data jsonb)
returns void language plpgsql security definer set search_path = public as $$
declare
    ids uuid[];
begin
    select array_agg(distinct u) into ids from unnest(p_user_ids) u where u is not null;
    if ids is null then return; end if;
    insert into notification_outbox (user_ids, title, body, data)
    values (ids, '', '', coalesce(p_data, '{}'::jsonb) || jsonb_build_object('silent', '1'));
end;
$$;

revoke execute on function public.push_l(uuid[], text, text, text, text, jsonb) from public, anon, authenticated;
revoke execute on function public.push_silent(uuid[], jsonb) from public, anon, authenticated;

-- ─────────────────────────────────────────────────────────────
-- Gate functions (same as before, plus the alert data and the silent stops)
-- ─────────────────────────────────────────────────────────────

create or replace function public.log_visitor(
    p_society_id uuid, p_flat_no text, p_category text, p_name text,
    p_phone text default '', p_company text default '', p_vehicle_no text default '', p_let_in boolean default false,
    p_photo_path text default ''
) returns public.gate_entries language plpgsql security definer set search_path = public as $$
declare
    e gate_entries;
    flat text := upper(trim(coalesce(p_flat_no, '')));
    who_en text;
    who_ta text;
begin
    if not has_perm(p_society_id, 'gate') then raise exception 'Not allowed for your role'; end if;
    if flat = '' then raise exception 'Enter the flat number'; end if;
    if not exists (select 1 from memberships where society_id = p_society_id and status = 'approved'
                   and role <> 'security' and flat_no = flat) then
        raise exception 'No one lives in flat % on Notiz', flat;
    end if;
    if trim(coalesce(p_name, '')) = '' and trim(coalesce(p_company, '')) = '' then
        raise exception 'Enter the visitor''s name';
    end if;
    if coalesce(p_photo_path, '') <> '' and (attachment_society(p_photo_path) is distinct from p_society_id
                                             or attachment_kind(p_photo_path) <> 'gate') then
        raise exception 'Photo upload failed, please try again';
    end if;

    insert into gate_entries (society_id, flat_no, category, visitor_name, visitor_phone, company, vehicle_no,
                              status, logged_by, entered_at, photo_path)
    values (p_society_id, flat, p_category, left(trim(coalesce(p_name, '')), 60),
            left(regexp_replace(coalesce(p_phone, ''), '[^0-9]', '', 'g'), 15),
            left(trim(coalesce(p_company, '')), 40), left(upper(trim(coalesce(p_vehicle_no, ''))), 20),
            case when p_let_in then 'inside' else 'waiting' end, auth.uid(),
            case when p_let_in then now() end, coalesce(p_photo_path, ''))
    returning * into e;

    who_en := gate_visitor_label(e.category, e.visitor_name, e.company, 'en');
    who_ta := gate_visitor_label(e.category, e.visitor_name, e.company, 'ta');
    if p_let_in then
        perform push_l(push_flat(p_society_id, flat),
            'Visitor came in', who_en || ' has come in.',
            'பார்வையாளர் உள்ளே வந்தார்', who_ta || ' உள்ளே வந்துள்ளார்.',
            jsonb_build_object('type', 'gate', 'id', e.id::text, 'society_id', p_society_id::text));
    else
        perform push_l(push_flat(p_society_id, flat),
            'Visitor at the gate 🚪',
            who_en || ' is waiting.' || case when e.photo_path <> '' then ' Photo attached.' else '' end || ' Tap to allow or deny.',
            'வாயிலில் பார்வையாளர் 🚪',
            who_ta || ' காத்திருக்கிறார்.' || case when e.photo_path <> '' then ' புகைப்படம் உள்ளது.' else '' end
                || ' அனுமதிக்க அல்லது மறுக்க தொடவும்.',
            jsonb_build_object('type', 'gate', 'gate_action', 'ask', 'id', e.id::text, 'society_id', p_society_id::text));
    end if;
    return e;
end;
$$;

create or replace function public.respond_visitor(p_entry_id uuid, p_allow boolean)
returns public.gate_entries language plpgsql security definer set search_path = public as $$
declare
    e gate_entries;
    me text;
begin
    select * into e from gate_entries where id = p_entry_id for update;
    if e.id is null or not is_my_flat(e.society_id, e.flat_no) then raise exception 'Not allowed for your role'; end if;
    if e.status <> 'waiting' then raise exception 'Someone has already answered for this visitor'; end if;
    select coalesce(full_name, '') into me from profiles where id = auth.uid();

    update gate_entries
    set status = case when p_allow then 'allowed' else 'denied' end,
        decided_by = auth.uid(), decided_by_name = coalesce(me, ''), decided_at = now()
    where id = p_entry_id
    returning * into e;

    -- Stop the alert ringing on the flat's other phones
    perform push_silent(push_flat(e.society_id, e.flat_no),
        jsonb_build_object('type', 'gate_cancel', 'id', e.id::text, 'society_id', e.society_id::text));

    perform push_l(array_append(push_gate_desk(e.society_id), e.logged_by),
        case when p_allow then e.flat_no || ' allowed ✅' else e.flat_no || ' said no ❌' end,
        case when p_allow then 'Let ' || gate_visitor_label(e.category, e.visitor_name, e.company, 'en') || ' in.'
             else 'Don''t let ' || gate_visitor_label(e.category, e.visitor_name, e.company, 'en') || ' in.' end,
        case when p_allow then e.flat_no || ' அனுமதித்தது ✅' else e.flat_no || ' மறுத்தது ❌' end,
        case when p_allow then gate_visitor_label(e.category, e.visitor_name, e.company, 'ta') || ' உள்ளே செல்லலாம்.'
             else gate_visitor_label(e.category, e.visitor_name, e.company, 'ta') || ' உள்ளே செல்ல வேண்டாம்.' end,
        jsonb_build_object('type', 'gate', 'id', e.id::text, 'society_id', e.society_id::text));
    return e;
end;
$$;

create or replace function public.mark_visitor_in(p_entry_id uuid)
returns public.gate_entries language plpgsql security definer set search_path = public as $$
declare
    e gate_entries;
    was text;
begin
    select * into e from gate_entries where id = p_entry_id for update;
    if e.id is null or not has_perm(e.society_id, 'gate') then raise exception 'Not allowed for your role'; end if;
    if e.status not in ('waiting', 'allowed') then raise exception 'This visitor is no longer waiting'; end if;
    was := e.status;
    update gate_entries set status = 'inside', entered_at = now() where id = p_entry_id returning * into e;
    -- The flat didn't answer in the app, so tell them who came in
    if was = 'waiting' then
        perform push_silent(push_flat(e.society_id, e.flat_no),
            jsonb_build_object('type', 'gate_cancel', 'id', e.id::text, 'society_id', e.society_id::text));
        perform push_l(push_flat(e.society_id, e.flat_no),
            'Visitor came in', gate_visitor_label(e.category, e.visitor_name, e.company, 'en') || ' has come in.',
            'பார்வையாளர் உள்ளே வந்தார்', gate_visitor_label(e.category, e.visitor_name, e.company, 'ta') || ' உள்ளே வந்துள்ளார்.',
            jsonb_build_object('type', 'gate', 'id', e.id::text, 'society_id', e.society_id::text));
    end if;
    return e;
end;
$$;

create or replace function public.cancel_visitor(p_entry_id uuid)
returns public.gate_entries language plpgsql security definer set search_path = public as $$
declare
    e gate_entries;
    was text;
begin
    select * into e from gate_entries where id = p_entry_id for update;
    if e.id is null or not has_perm(e.society_id, 'gate') then raise exception 'Not allowed for your role'; end if;
    if e.status not in ('waiting', 'allowed', 'denied') then raise exception 'This visitor is no longer waiting'; end if;
    was := e.status;
    update gate_entries set status = 'cancelled' where id = p_entry_id returning * into e;
    if was = 'waiting' then
        perform push_silent(push_flat(e.society_id, e.flat_no),
            jsonb_build_object('type', 'gate_cancel', 'id', e.id::text, 'society_id', e.society_id::text));
    end if;
    return e;
end;
$$;

-- ─────────────────────────────────────────────────────────────
-- Test alert
-- ─────────────────────────────────────────────────────────────

create or replace function public.send_test_visitor_alert()
returns void language plpgsql security definer set search_path = public as $$
begin
    if auth.uid() is null then raise exception 'Not signed in'; end if;
    -- At most one a minute
    if exists (select 1 from notification_outbox where data->>'type' = 'gate_test'
               and user_ids = array[auth.uid()] and created_at > now() - interval '1 minute') then
        raise exception 'Wait a minute before sending another test';
    end if;
    perform push_l(array[auth.uid()],
        'Test: visitor at the gate 🚪', 'This is how a visitor alert looks and sounds. Nothing to do.',
        'சோதனை: வாயிலில் பார்வையாளர் 🚪', 'பார்வையாளர் அறிவிப்பு இப்படித்தான் தெரியும், ஒலிக்கும். எதுவும் செய்ய வேண்டாம்.',
        jsonb_build_object('type', 'gate_test', 'id', gen_random_uuid()::text));
end;
$$;
revoke execute on function public.send_test_visitor_alert() from public, anon;
grant execute on function public.send_test_visitor_alert() to authenticated;
