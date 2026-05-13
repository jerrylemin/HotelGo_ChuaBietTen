create table if not exists public.reviews (
    id text primary key,
    room_id text not null,
    hotel_id text,
    booking_id text,
    user_id text not null,
    rating integer not null check (rating between 1 and 5),
    comment text not null default '',
    created_at timestamptz not null default now()
);

alter table if exists public.reviews
    add column if not exists room_id text,
    add column if not exists hotel_id text,
    add column if not exists booking_id text,
    add column if not exists user_id text,
    add column if not exists rating integer,
    add column if not exists comment text default '',
    add column if not exists created_at timestamptz default now();

update public.reviews
set created_at = now()
where created_at is null;

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

create unique index if not exists reviews_user_room_legacy_unique
    on public.reviews (user_id, room_id)
    where booking_id is null;

create index if not exists reviews_room_id_created_at_idx on public.reviews (room_id, created_at desc);
create index if not exists reviews_user_id_idx on public.reviews (user_id);
create index if not exists reviews_booking_id_idx on public.reviews (booking_id);
create index if not exists reviews_hotel_id_idx on public.reviews (hotel_id);

alter table public.reviews enable row level security;

drop policy if exists "Reviews are readable by everyone" on public.reviews;
create policy "Reviews are readable by everyone"
on public.reviews for select
using (true);

drop policy if exists "Users can create their own reviews" on public.reviews;
create policy "Users can create their own reviews"
on public.reviews for insert
with check (auth.uid()::text = user_id);

drop policy if exists "Users can update their own reviews" on public.reviews;
create policy "Users can update their own reviews"
on public.reviews for update
using (auth.uid()::text = user_id)
with check (auth.uid()::text = user_id);

insert into public.reviews (id, room_id, hotel_id, booking_id, user_id, rating, comment, created_at)
select
    'review-seed-' || b.id,
    b.room_id,
    null,
    b.id,
    b.user_id,
    5,
    'Phong sach se, dich vu tot.',
    now()
from public.bookings b
where b.status in ('completed', 'checked_out', 'paid', 'confirmed')
  and not exists (
      select 1
      from public.reviews r
      where r.user_id = b.user_id
        and (r.booking_id = b.id or (r.booking_id is null and r.room_id = b.room_id))
  )
limit 3
on conflict (id) do nothing;
