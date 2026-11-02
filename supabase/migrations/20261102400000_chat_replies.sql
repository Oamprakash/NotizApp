-- Notiz: reply to a chat message (swipe a message right in the app).
--
-- A reply stores the message it answers plus a small preview (sender + first 200 characters),
-- filled by the server, so the quote shows even when the original is too old to be loaded.
-- The person being replied to gets "<name> replied to you" instead of the usual chat push.
-- Run after 20261102300000_push_tamil.sql.

alter table public.messages add column reply_to_id uuid references public.messages (id) on delete set null;
alter table public.messages add column reply_sender_name text;
alter table public.messages add column reply_body text;

-- Extends the existing BEFORE INSERT trigger: sender name, flat and time as before, plus the quote.
create or replace function public.fill_message_sender()
returns trigger language plpgsql security definer set search_path = public as $$
declare
    q messages;
begin
    select full_name into new.sender_name from profiles where id = new.sender_id;
    new.flat_no := coalesce(my_flat(new.society_id), '');
    new.created_at := now();
    new.reply_sender_name := null;
    new.reply_body := null;
    if new.reply_to_id is not null then
        select * into q from messages where id = new.reply_to_id;
        if q.id is null or q.society_id <> new.society_id then
            new.reply_to_id := null; -- not a message in this chat
        else
            new.reply_sender_name := q.sender_name;
            new.reply_body := left(q.body, 200); -- '' if it was deleted
        end if;
    end if;
    return new;
end;
$$;

-- Deleting a message also wipes it from the quotes in replies.
create or replace function public.delete_message(p_message_id uuid)
returns void language plpgsql security definer set search_path = public as $$
declare
    m messages;
begin
    select * into m from messages where id = p_message_id;
    if m.id is null or not (m.sender_id = auth.uid() or is_admin(m.society_id)) then
        raise exception 'Not allowed';
    end if;
    if m.deleted_at is not null then return; end if;
    update messages set body = '', deleted_at = now(), deleted_by = auth.uid() where id = p_message_id;
    update messages set reply_body = '' where reply_to_id = p_message_id;
end;
$$;

-- Chat push: everyone except the sender; the person replied to gets a personal one.
create or replace function public.push_on_message()
returns trigger language plpgsql security definer set search_path = public as $$
declare
    who text := new.sender_name || case when new.flat_no <> '' then ' · ' || new.flat_no else '' end;
    payload jsonb := jsonb_build_object('type', 'chat', 'id', new.id::text, 'society_id', new.society_id::text);
    replied_to uuid;
begin
    if new.reply_to_id is not null then
        select sender_id into replied_to from messages where id = new.reply_to_id;
        if replied_to = new.sender_id then replied_to := null; end if;
    end if;

    perform enqueue_push(
        array(select u from unnest(push_members(new.society_id, new.sender_id)) u where u is distinct from replied_to),
        who,
        left(new.body, 200),
        payload);

    if replied_to is not null and replied_to = any(push_members(new.society_id, new.sender_id)) then
        perform push_l(array[replied_to],
            new.sender_name || ' replied to you',
            left(new.body, 200),
            new.sender_name || ' உங்களுக்குப் பதிலளித்தார்',
            left(new.body, 200),
            payload);
    end if;
    return new;
end;
$$;
revoke execute on function public.push_on_message() from public, anon, authenticated;
