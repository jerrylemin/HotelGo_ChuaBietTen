create table if not exists public.posters (
    id text primary key,
    type text not null default 'recommend',
    title text not null,
    description text,
    image_url text,
    room_id text,
    room_name text,
    hotel_name text,
    price numeric not null default 0,
    is_active boolean not null default true,
    created_by text,
    admin_reply text,
    status text not null default 'new',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

alter table public.posters
    add column if not exists type text not null default 'recommend',
    add column if not exists title text not null default '',
    add column if not exists description text,
    add column if not exists content text,
    add column if not exists image_url text,
    add column if not exists room_id text,
    add column if not exists room_name text,
    add column if not exists hotel_name text,
    add column if not exists price numeric not null default 0,
    add column if not exists is_active boolean not null default true,
    add column if not exists active boolean,
    add column if not exists created_by text,
    add column if not exists user_id text,
    add column if not exists admin_reply text,
    add column if not exists response text,
    add column if not exists role text,
    add column if not exists status text not null default 'new',
    add column if not exists created_at timestamptz not null default now(),
    add column if not exists updated_at timestamptz not null default now();

update public.posters
set
    description = nullif(coalesce(nullif(description, 'null'), nullif(content, 'null')), ''),
    content = nullif(coalesce(nullif(content, 'null'), nullif(description, 'null')), ''),
    is_active = coalesce(is_active, active, true),
    active = coalesce(active, is_active, true),
    created_by = coalesce(created_by, user_id),
    user_id = coalesce(user_id, created_by),
    admin_reply = nullif(coalesce(nullif(admin_reply, 'null'), nullif(response, 'null')), ''),
    response = nullif(coalesce(nullif(response, 'null'), nullif(admin_reply, 'null')), ''),
    updated_at = coalesce(updated_at, now())
where true;

alter table public.posters enable row level security;

do $$
begin
    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'posters'
          and policyname = 'posters readable by authenticated users'
    ) then
        create policy "posters readable by authenticated users"
        on public.posters
        for select
        to authenticated
        using (true);
    end if;

    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'posters'
          and policyname = 'authenticated users manage posters'
    ) then
        create policy "authenticated users manage posters"
        on public.posters
        for all
        to authenticated
        using (true)
        with check (true);
    end if;
end $$;

notify pgrst, 'reload schema';
