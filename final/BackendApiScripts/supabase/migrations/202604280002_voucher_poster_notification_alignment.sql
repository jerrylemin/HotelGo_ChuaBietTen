alter table if exists public.vouchers
    add column if not exists title text,
    add column if not exists description text,
    add column if not exists discount_type text,
    add column if not exists discount_value numeric not null default 0,
    add column if not exists min_order_amount numeric not null default 0,
    add column if not exists max_discount_amount numeric not null default 0,
    add column if not exists start_date date,
    add column if not exists end_date date,
    add column if not exists usage_limit integer not null default 0,
    add column if not exists used_count integer not null default 0,
    add column if not exists is_active boolean not null default true,
    add column if not exists created_at timestamptz not null default now(),
    add column if not exists updated_at timestamptz not null default now();

update public.vouchers
set
    title = coalesce(title, code),
    discount_type = coalesce(discount_type, case when type = 'percent' then 'percentage' else type end, 'percentage'),
    discount_value = coalesce(discount_value, value, 0),
    min_order_amount = coalesce(min_order_amount, min_spend, 0),
    start_date = coalesce(
        start_date,
        case
            when btrim(coalesce(start_at::text, '')) ~ '^\d{4}-\d{2}-\d{2}$'
                then btrim(start_at::text)::date
            else null
        end
    ),
    end_date = coalesce(
        end_date,
        case
            when btrim(coalesce(end_at::text, '')) ~ '^\d{4}-\d{2}-\d{2}$'
                then btrim(end_at::text)::date
            else null
        end
    ),
    is_active = coalesce(is_active, active, true),
    used_count = coalesce(used_count, 0),
    updated_at = coalesce(updated_at, now())
where true;

alter table if exists public.posters
    add column if not exists description text,
    add column if not exists image_url text,
    add column if not exists room_id text,
    add column if not exists is_active boolean not null default true,
    add column if not exists created_by text,
    add column if not exists admin_reply text,
    add column if not exists status text not null default 'new',
    add column if not exists updated_at timestamptz not null default now();

update public.posters
set
    description = coalesce(description, content),
    is_active = coalesce(is_active, active, true),
    created_by = coalesce(created_by, user_id),
    admin_reply = coalesce(admin_reply, response),
    updated_at = coalesce(updated_at, now())
where true;

create table if not exists public.room_requests (
    id text primary key,
    user_id text not null,
    user_email text,
    request_text text not null,
    budget numeric not null default 0,
    admin_reply text,
    status text not null default 'new' check (status in ('new', 'processing', 'resolved')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists room_requests_user_id_idx on public.room_requests(user_id);
create index if not exists room_requests_status_idx on public.room_requests(status);

create table if not exists public.notification_settings (
    id text primary key,
    user_id text not null unique,
    check_in boolean not null default true,
    promo boolean not null default true,
    room_status boolean not null default true,
    booking boolean not null default true,
    review boolean not null default true,
    issue boolean not null default true,
    payment boolean not null default true,
    updated_at timestamptz not null default now()
);

alter table if exists public.notifications
    add column if not exists user_id text,
    add column if not exists user_email text,
    add column if not exists message text,
    add column if not exists type text not null default 'general',
    add column if not exists is_read boolean not null default false,
    add column if not exists read_at timestamptz,
    add column if not exists related_id text,
    add column if not exists metadata jsonb not null default '{}'::jsonb,
    add column if not exists target_role text not null default 'all',
    add column if not exists created_at timestamptz not null default now();

update public.notifications
set message = coalesce(message, body), metadata = coalesce(metadata, '{}'::jsonb)
where true;

alter table public.vouchers enable row level security;
alter table public.posters enable row level security;
alter table public.room_requests enable row level security;
alter table public.notifications enable row level security;
alter table public.notification_settings enable row level security;

do $$
begin
    if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'vouchers' and policyname = 'vouchers readable by authenticated users') then
        create policy "vouchers readable by authenticated users" on public.vouchers for select to authenticated using (true);
    end if;
    if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'vouchers' and policyname = 'admins manage vouchers') then
        create policy "admins manage vouchers" on public.vouchers for all to authenticated using (true) with check (true);
    end if;
    if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'posters' and policyname = 'active posters readable') then
        create policy "active posters readable" on public.posters for select to authenticated using (
            is_active = true or exists (select 1 from public.users u where u.id = auth.uid()::text and u.role = 'admin')
        );
    end if;
    if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'posters' and policyname = 'authenticated manage posters') then
        create policy "authenticated manage posters" on public.posters for all to authenticated using (true) with check (true);
    end if;
    if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'room_requests' and policyname = 'users read own room requests') then
        create policy "users read own room requests" on public.room_requests for select to authenticated using (
            auth.uid()::text = user_id or exists (select 1 from public.users u where u.id = auth.uid()::text and u.role = 'admin')
        );
    end if;
    if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'room_requests' and policyname = 'users create own room requests') then
        create policy "users create own room requests" on public.room_requests for insert to authenticated with check (auth.uid()::text = user_id);
    end if;
    if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'room_requests' and policyname = 'authenticated update room requests') then
        create policy "authenticated update room requests" on public.room_requests for update to authenticated using (
            exists (select 1 from public.users u where u.id = auth.uid()::text and u.role = 'admin')
        ) with check (
            exists (select 1 from public.users u where u.id = auth.uid()::text and u.role = 'admin')
        );
    end if;
    if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'notifications' and policyname = 'users read scoped notifications') then
        create policy "users read scoped notifications" on public.notifications for select to authenticated using (
            user_id is null or auth.uid()::text = user_id or exists (select 1 from public.users u where u.id = auth.uid()::text and u.role = 'admin')
        );
    end if;
    if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'notifications' and policyname = 'users update own notification read state') then
        create policy "users update own notification read state" on public.notifications for update to authenticated using (
            user_id is null or auth.uid()::text = user_id or exists (select 1 from public.users u where u.id = auth.uid()::text and u.role = 'admin')
        ) with check (true);
    end if;
    if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'notifications' and policyname = 'authenticated create notifications') then
        create policy "authenticated create notifications" on public.notifications for insert to authenticated with check (true);
    end if;
    if not exists (select 1 from pg_policies where schemaname = 'public' and tablename = 'notification_settings' and policyname = 'users manage own notification settings') then
        create policy "users manage own notification settings" on public.notification_settings for all to authenticated using (auth.uid()::text = user_id) with check (auth.uid()::text = user_id);
    end if;
end $$;

notify pgrst, 'reload schema';
