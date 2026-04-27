alter table if exists public.bookings
    add column if not exists voucher_id text,
    add column if not exists voucher_code text,
    add column if not exists discount_amount numeric default 0,
    add column if not exists original_total numeric default 0,
    add column if not exists addons_total numeric default 0,
    add column if not exists final_total numeric default 0,
    add column if not exists actual_check_in_at timestamptz,
    add column if not exists actual_check_out_at timestamptz;

create table if not exists public.booking_addons (
    id text primary key,
    booking_id text not null references public.bookings(id) on delete cascade,
    addon_item_id text not null,
    quantity integer not null check (quantity > 0),
    unit_price numeric not null default 0 check (unit_price >= 0),
    total_price numeric not null default 0 check (total_price >= 0),
    created_at timestamptz not null default now()
);

create index if not exists booking_addons_booking_id_idx on public.booking_addons (booking_id);
create index if not exists booking_addons_addon_item_id_idx on public.booking_addons (addon_item_id);

alter table public.booking_addons enable row level security;

drop policy if exists "Booking addons are readable by everyone" on public.booking_addons;
create policy "Booking addons are readable by everyone"
on public.booking_addons for select
using (true);

drop policy if exists "Logged in users can create booking addons" on public.booking_addons;
create policy "Logged in users can create booking addons"
on public.booking_addons for insert
with check (auth.uid() is not null);
