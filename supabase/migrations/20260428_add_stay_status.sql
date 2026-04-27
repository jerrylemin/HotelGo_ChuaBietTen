-- Migration: Add stay status fields to bookings table
-- Safe: only adds columns if they do not already exist. No data is dropped.

alter table if exists public.bookings
    add column if not exists stay_status text default 'pending_checkin',
    add column if not exists checked_in_at timestamptz,
    add column if not exists checked_out_at timestamptz,
    add column if not exists updated_at timestamptz default now();

-- Backfill: set stay_status based on existing booking status
-- Confirmed/paid bookings that haven't checked in → pending_checkin
update public.bookings
set stay_status = 'pending_checkin'
where stay_status is null
  and status in ('confirmed', 'paid');

-- Already checked_in
update public.bookings
set stay_status = 'checked_in'
where stay_status is null
  and status = 'checked_in';

-- Already checked_out / completed
update public.bookings
set stay_status = 'checked_out'
where stay_status is null
  and status in ('checked_out', 'completed');

-- Cancelled stays
update public.bookings
set stay_status = 'cancelled'
where stay_status is null
  and status = 'cancelled';

-- Remaining (pending, etc.)
update public.bookings
set stay_status = 'pending_checkin'
where stay_status is null;

-- Index to speed up admin list queries
create index if not exists bookings_stay_status_idx on public.bookings (stay_status);
create index if not exists bookings_updated_at_idx on public.bookings (updated_at);
