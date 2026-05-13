alter table if exists public.bookings
    add column if not exists guest_name text,
    add column if not exists guest_phone text;

alter table if exists public.issues
    add column if not exists user_name text,
    add column if not exists user_phone text;

alter table if exists public.room_requests
    add column if not exists user_name text,
    add column if not exists user_phone text;

alter table if exists public.posters
    add column if not exists user_name text,
    add column if not exists user_phone text;

update public.bookings b
set
    guest_name = coalesce(nullif(b.guest_name, ''), nullif(u.name, '')),
    guest_phone = coalesce(nullif(b.guest_phone, ''), nullif(u.phone, ''))
from public.users u
where b.user_id = u.id;

update public.issues i
set
    user_name = coalesce(nullif(i.user_name, ''), nullif(u.name, '')),
    user_phone = coalesce(nullif(i.user_phone, ''), nullif(u.phone, ''))
from public.users u
where i.user_id = u.id;

update public.room_requests r
set
    user_name = coalesce(nullif(r.user_name, ''), nullif(u.name, '')),
    user_phone = coalesce(nullif(r.user_phone, ''), nullif(u.phone, ''))
from public.users u
where r.user_id = u.id;

update public.posters p
set
    user_name = coalesce(nullif(p.user_name, ''), nullif(u.name, '')),
    user_phone = coalesce(nullif(p.user_phone, ''), nullif(u.phone, ''))
from public.users u
where coalesce(nullif(p.user_id, ''), nullif(p.created_by, '')) = u.id;

notify pgrst, 'reload schema';
