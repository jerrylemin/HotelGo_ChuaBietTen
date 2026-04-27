create table if not exists public.booking_addons (
    id text primary key,
    booking_id text not null references public.bookings(id) on delete cascade,
    addon_item_id text not null references public.add_ons(id) on delete restrict,
    quantity integer not null check (quantity > 0),
    unit_price numeric not null default 0 check (unit_price >= 0),
    total_price numeric not null default 0 check (total_price >= 0),
    created_at timestamptz not null default now()
);

create index if not exists booking_addons_booking_id_idx on public.booking_addons (booking_id);
create index if not exists booking_addons_addon_item_id_idx on public.booking_addons (addon_item_id);

insert into public.add_ons (id, name, description, price, category, active)
values
    ('addon-water', 'Nuoc suoi', 'Nuoc suoi dong chai trong phong', 20000, 'drink', true),
    ('addon-breakfast', 'Bua sang', 'Buffet sang tai nha hang khach san', 180000, 'food', true),
    ('addon-airport-transfer', 'Dua don san bay', 'Xe dua don mot chieu tu hoac den san bay', 450000, 'transport', true),
    ('addon-room-decoration', 'Trang tri phong', 'Trang tri phong cho sinh nhat hoac ky niem', 350000, 'service', true),
    ('addon-laundry', 'Giat ui', 'Giat ui co ban trong ngay', 120000, 'service', true)
on conflict (id) do update set
    name = excluded.name,
    description = excluded.description,
    price = excluded.price,
    category = excluded.category,
    active = excluded.active;
