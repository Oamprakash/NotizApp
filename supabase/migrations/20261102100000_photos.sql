-- Notiz: photos on notices (admins) and complaints (residents), up to 3 each.
--
-- Files live in the private Storage bucket "attachments" at
--   <society_id>/notices/<uuid>.jpg      readable by every member of the society
--   <society_id>/complaints/<uuid>.jpg   readable by the resident who uploaded it and the admins
-- The app shrinks photos to about 1600 px JPEG before upload (usually 200–500 KB).
-- Run after 20261102000000_change_notifications.sql.

-- ─────────────────────────────────────────────────────────────
-- Bucket
-- ─────────────────────────────────────────────────────────────

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('attachments', 'attachments', false, 3145728, array['image/jpeg'])
on conflict (id) do update
    set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

-- The society a storage path belongs to, or null if the path isn't ours.
create or replace function public.attachment_society(p_name text)
returns uuid language plpgsql immutable as $$
begin
    if p_name !~ '^[0-9a-f-]{36}/(notices|complaints)/[0-9a-f-]{36}\.jpg$' then return null; end if;
    return split_part(p_name, '/', 1)::uuid;
end;
$$;

create or replace function public.attachment_kind(p_name text)
returns text language sql immutable as $$
    select split_part(p_name, '/', 2);
$$;

-- ─────────────────────────────────────────────────────────────
-- Storage policies
-- ─────────────────────────────────────────────────────────────

drop policy if exists "notiz read attachments" on storage.objects;
create policy "notiz read attachments" on storage.objects for select to authenticated
    using (
        bucket_id = 'attachments' and (
            (attachment_kind(name) = 'notices' and public.is_member(attachment_society(name)))
            or (attachment_kind(name) = 'complaints'
                and (owner_id = auth.uid()::text or public.is_admin(attachment_society(name))))
        )
    );

-- Notices: admins upload. Complaints: any member uploads their own.
drop policy if exists "notiz upload attachments" on storage.objects;
create policy "notiz upload attachments" on storage.objects for insert to authenticated
    with check (
        bucket_id = 'attachments' and (
            (attachment_kind(name) = 'notices' and public.is_admin(attachment_society(name)))
            or (attachment_kind(name) = 'complaints' and public.is_member(attachment_society(name)))
        )
    );

-- Uploader can delete (e.g. removed a photo before posting); admins can tidy their society's files.
drop policy if exists "notiz delete attachments" on storage.objects;
create policy "notiz delete attachments" on storage.objects for delete to authenticated
    using (
        bucket_id = 'attachments'
        and (owner_id = auth.uid()::text or public.is_admin(attachment_society(name)))
    );

-- ─────────────────────────────────────────────────────────────
-- Photo paths on notices and complaints
-- ─────────────────────────────────────────────────────────────

alter table public.notices    add column photo_paths text[] not null default '{}';
alter table public.complaints add column photo_paths text[] not null default '{}';

-- At most 3 photos, all inside this society's folder for that kind of post.
create or replace function public.photo_paths_ok(p_paths text[], p_society uuid, p_kind text)
returns boolean language sql immutable as $$
    select cardinality(p_paths) <= 3
       and not exists (
           select 1 from unnest(p_paths) p
           where attachment_society(p) is distinct from p_society or attachment_kind(p) <> p_kind
       );
$$;

alter table public.notices
    add constraint notices_photo_paths_ok check (photo_paths_ok(photo_paths, society_id, 'notices'));
alter table public.complaints
    add constraint complaints_photo_paths_ok check (photo_paths_ok(photo_paths, society_id, 'complaints'));

-- Note: Supabase blocks deleting storage.objects rows from SQL, so the app removes a notice's
-- photos through the Storage API before deleting the notice (admins may delete in their folder).
