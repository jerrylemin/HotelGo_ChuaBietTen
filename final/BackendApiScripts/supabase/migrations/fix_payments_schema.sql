-- Migration: fix_payments_schema
-- Ensure all columns used by the application exist in the payments table.

-- Create table if it completely doesn't exist (very rare, but safe)
CREATE TABLE IF NOT EXISTS public.payments (
    id text PRIMARY KEY,
    booking_id text,
    user_id text,
    amount numeric default 0,
    method text default '',
    status text default 'paid',
    card_last4 text default '',
    voucher_id text default '',
    voucher_code text default '',
    discount_amount numeric default 0,
    original_total numeric default 0,
    addons_total numeric default 0,
    final_total numeric default 0,
    created_at timestamptz default now()
);

-- Safely add missing columns to existing table
ALTER TABLE IF EXISTS public.payments
    ADD COLUMN IF NOT EXISTS id text,
    ADD COLUMN IF NOT EXISTS booking_id text,
    ADD COLUMN IF NOT EXISTS user_id text,
    ADD COLUMN IF NOT EXISTS amount numeric default 0,
    ADD COLUMN IF NOT EXISTS method text default '',
    ADD COLUMN IF NOT EXISTS status text default 'paid',
    ADD COLUMN IF NOT EXISTS card_last4 text default '',
    ADD COLUMN IF NOT EXISTS voucher_id text default '',
    ADD COLUMN IF NOT EXISTS voucher_code text default '',
    ADD COLUMN IF NOT EXISTS discount_amount numeric default 0,
    ADD COLUMN IF NOT EXISTS original_total numeric default 0,
    ADD COLUMN IF NOT EXISTS addons_total numeric default 0,
    ADD COLUMN IF NOT EXISTS final_total numeric default 0,
    ADD COLUMN IF NOT EXISTS created_at timestamptz default now();

-- Tell PostgREST to reload the schema cache so the new columns are immediately available
NOTIFY pgrst, 'reload schema';
