alter table if exists public.reviews
    add column if not exists hotel_id text,
    add column if not exists booking_id text;

update public.reviews r
set booking_id = b.id
from public.bookings b
where r.booking_id is null
  and r.user_id = b.user_id
  and r.room_id = b.room_id
  and b.status in ('completed', 'checked_out', 'paid', 'confirmed');

create unique index if not exists reviews_user_booking_unique
    on public.reviews (user_id, booking_id)
    where booking_id is not null;

create index if not exists reviews_booking_id_idx on public.reviews (booking_id);
create index if not exists reviews_hotel_id_idx on public.reviews (hotel_id);
