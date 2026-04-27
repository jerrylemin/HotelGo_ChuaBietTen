alter table if exists public.vouchers
    add column if not exists title text,
    add column if not exists description text,
    add column if not exists discount_type text,
    add column if not exists discount_value numeric,
    add column if not exists min_order_amount numeric default 0,
    add column if not exists max_discount_amount numeric default 0,
    add column if not exists start_date date,
    add column if not exists end_date date,
    add column if not exists is_active boolean default true,
    add column if not exists used_count integer default 0;

update public.vouchers
set
    title = coalesce(title, code),
    discount_type = coalesce(discount_type, case when type = 'percent' then 'percentage' else type end, 'percentage'),
    discount_value = coalesce(discount_value, value, 0),
    min_order_amount = coalesce(min_order_amount, min_spend, 0),
    start_date = coalesce(start_date, nullif(start_at, '')::date),
    end_date = coalesce(end_date, nullif(end_at, '')::date),
    is_active = coalesce(is_active, active, true),
    used_count = coalesce(used_count, 0);

alter table if exists public.bookings
    add column if not exists voucher_id text,
    add column if not exists voucher_code text,
    add column if not exists discount_amount numeric default 0,
    add column if not exists original_total numeric default 0,
    add column if not exists addons_total numeric default 0,
    add column if not exists final_total numeric default 0;

alter table if exists public.payments
    add column if not exists voucher_id text,
    add column if not exists voucher_code text,
    add column if not exists discount_amount numeric default 0,
    add column if not exists original_total numeric default 0,
    add column if not exists addons_total numeric default 0,
    add column if not exists final_total numeric default 0;

create table if not exists public.voucher_usage (
    id text primary key,
    voucher_id text references public.vouchers(id) on delete set null,
    voucher_code text not null,
    user_id text not null,
    booking_id text references public.bookings(id) on delete cascade,
    payment_id text references public.payments(id) on delete set null,
    discount_amount numeric not null default 0,
    used_at timestamptz not null default now()
);

create unique index if not exists voucher_usage_user_booking_unique
    on public.voucher_usage (user_id, booking_id)
    where booking_id is not null;

insert into public.vouchers (
    id, code, title, description, discount_type, discount_value,
    min_order_amount, max_discount_amount, start_date, end_date,
    is_active, usage_limit, used_count,
    type, value, min_spend, start_at, end_at, active
)
values
    ('voucher-welcome10', 'WELCOME10', 'Welcome 10%', 'Giam 10% cho booking tu 500000 VND', 'percentage', 10, 500000, 300000, '2026-01-01', '2026-12-31', true, 500, 0, 'percentage', 10, 500000, '2026-01-01', '2026-12-31', true),
    ('voucher-breakfast50', 'BREAKFAST50', 'Giam 50000 VND', 'Giam truc tiep cho booking co dich vu them', 'fixed_amount', 50000, 300000, 0, '2026-01-01', '2026-12-31', true, 300, 0, 'fixed_amount', 50000, 300000, '2026-01-01', '2026-12-31', true),
    ('voucher-summer15', 'SUMMER15', 'Summer 15%', 'Giam 15% toi da 500000 VND', 'percentage', 15, 1000000, 500000, '2026-04-01', '2026-09-30', true, 200, 0, 'percentage', 15, 1000000, '2026-04-01', '2026-09-30', true)
on conflict (id) do update set
    code = excluded.code,
    title = excluded.title,
    description = excluded.description,
    discount_type = excluded.discount_type,
    discount_value = excluded.discount_value,
    min_order_amount = excluded.min_order_amount,
    max_discount_amount = excluded.max_discount_amount,
    start_date = excluded.start_date,
    end_date = excluded.end_date,
    is_active = excluded.is_active,
    usage_limit = excluded.usage_limit,
    type = excluded.type,
    value = excluded.value,
    min_spend = excluded.min_spend,
    start_at = excluded.start_at,
    end_at = excluded.end_at,
    active = excluded.active;
