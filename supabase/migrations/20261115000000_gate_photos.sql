-- Notiz: visitor photos at the gate.
--
-- The guard can take one photo of a visitor. It is stored at
--   <society_id>/gate/<uuid>.jpg   in the private "attachments" bucket,
-- readable by the gate desk and by the flat the visitor came for (nobody else).
--
-- Run after 20261114000000_gate_log.sql.

-- Paths can now also be in the gate folder
create or replace function public.attachment_society(p_name text)
returns uuid language plpgsql immutable as $$
begin
    if p_name !~ '^[0-9a-f-]{36}/(notices|complaints|gate)/[0-9a-f-]{36}\.jpg$' then return null; end if;
    return split_part(p_name, '/', 1)::uuid;
end;
$$;

alter table public.gate_entries add column photo_path text not null default '';
alter table public.gate_entries add constraint gate_entries_photo_path_ok check (
    photo_path = ''
    or coalesce(attachment_society(photo_path) = society_id and attachment_kind(photo_path) = 'gate', false)
);

-- The flat sees the photo of its own visitor
create or replace function public.can_see_gate_photo(p_name text)
returns boolean language sql stable security definer set search_path = public as $$
    select has_perm(attachment_society(p_name), 'gate')
        or exists (select 1 from gate_entries e
                   where e.photo_path = p_name and is_my_flat(e.society_id, e.flat_no));
$$;
grant execute on function public.can_see_gate_photo(text) to authenticated;

drop policy if exists "notiz read attachments" on storage.objects;
create policy "notiz read attachments" on storage.objects for select to authenticated
    using (
        bucket_id = 'attachments' and (
            (public.attachment_kind(name) = 'notices' and public.is_member(public.attachment_society(name)))
            or (public.attachment_kind(name) = 'complaints'
                and (owner_id = auth.uid()::text or public.has_perm(public.attachment_society(name), 'helpdesk')))
            or (public.attachment_kind(name) = 'gate' and public.can_see_gate_photo(name))
        )
    );
drop policy if exists "notiz upload attachments" on storage.objects;
create policy "notiz upload attachments" on storage.objects for insert to authenticated
    with check (
        bucket_id = 'attachments' and (
            (public.attachment_kind(name) = 'notices' and public.has_perm(public.attachment_society(name), 'notices'))
            or (public.attachment_kind(name) = 'complaints' and public.is_member(public.attachment_society(name)))
            or (public.attachment_kind(name) = 'gate' and public.has_perm(public.attachment_society(name), 'gate'))
        )
    );
-- Each permission only removes photos of its own kind
drop policy if exists "notiz delete attachments" on storage.objects;
create policy "notiz delete attachments" on storage.objects for delete to authenticated
    using (
        bucket_id = 'attachments' and (
            owner_id = auth.uid()::text
            or (public.attachment_kind(name) = 'notices' and public.has_perm(public.attachment_society(name), 'notices'))
            or (public.attachment_kind(name) = 'complaints' and public.has_perm(public.attachment_society(name), 'helpdesk'))
            or (public.attachment_kind(name) = 'gate' and public.has_perm(public.attachment_society(name), 'gate'))
        )
    );

-- log_visitor gets the photo (uploaded by the app just before)
drop function public.log_visitor(uuid, text, text, text, text, text, text, boolean);

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
            jsonb_build_object('type', 'gate', 'id', e.id::text, 'society_id', p_society_id::text));
    end if;
    return e;
end;
$$;

revoke execute on function public.log_visitor(uuid, text, text, text, text, text, text, boolean, text) from public, anon;
grant execute on function public.log_visitor(uuid, text, text, text, text, text, text, boolean, text) to authenticated;
