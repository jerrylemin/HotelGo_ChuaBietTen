-- Migration: add_payment_summary_fields
-- Add missing columns to bookings and payments to support payment flow

-- Bookings table
ALTER TABLE IF EXISTS public.bookings
    ADD COLUMN IF NOT EXISTS original_total numeric default 0,
    ADD COLUMN IF NOT EXISTS addons_total numeric default 0,
    ADD COLUMN IF NOT EXISTS discount_amount numeric default 0,
    ADD COLUMN IF NOT EXISTS final_total numeric default 0,
    ADD COLUMN IF NOT EXISTS voucher_id text,
    ADD COLUMN IF NOT EXISTS voucher_code text;

-- Payments table
ALTER TABLE IF EXISTS public.payments
    ADD COLUMN IF NOT EXISTS original_total numeric default 0,
    ADD COLUMN IF NOT EXISTS addons_total numeric default 0,
    ADD COLUMN IF NOT EXISTS discount_amount numeric default 0,
    ADD COLUMN IF NOT EXISTS final_total numeric default 0,
    ADD COLUMN IF NOT EXISTS voucher_id text,
    ADD COLUMN IF NOT EXISTS voucher_code text,
    ADD COLUMN IF NOT EXISTS amount numeric default 0,
    ADD COLUMN IF NOT EXISTS method text,
    ADD COLUMN IF NOT EXISTS status text,
    ADD COLUMN IF NOT EXISTS booking_id text,
    ADD COLUMN IF NOT EXISTS user_id text;

-- Voucher usage table (create if not exists)
CREATE TABLE IF NOT EXISTS public.voucher_usage (
    id text PRIMARY KEY,
    user_id text NOT NULL,
    voucher_id text NOT NULL,
    booking_id text,
    used_at timestamp with time zone default now()
);

-- Ensure booking_addons table exists
CREATE TABLE IF NOT EXISTS public.booking_addons (
    id text PRIMARY KEY,
    booking_id text NOT NULL,
    add_on_item_id text NOT NULL,
    name text,
    description text,
    quantity integer default 1,
    unit_price numeric default 0,
    total_price numeric default 0,
    created_at timestamp with time zone default now()
);
