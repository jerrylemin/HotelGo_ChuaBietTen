create table if not exists public.notifications (
    id text primary key,
    user_id text,
    user_email text,
    title text not null default '',
    message text,
    body text,
    type text not null default 'general',
    target_role text not null default 'all',
    is_read boolean not null default false,
    "read" boolean not null default false,
    created_at timestamptz not null default now(),
    read_at timestamptz,
    related_id text,
    metadata jsonb not null default '{}'::jsonb
);

alter table public.notifications
    add column if not exists user_id text,
    add column if not exists user_email text,
    add column if not exists title text not null default '',
    add column if not exists message text,
    add column if not exists body text,
    add column if not exists type text not null default 'general',
    add column if not exists target_role text not null default 'all',
    add column if not exists is_read boolean not null default false,
    add column if not exists "read" boolean not null default false,
    add column if not exists created_at timestamptz not null default now(),
    add column if not exists read_at timestamptz,
    add column if not exists related_id text,
    add column if not exists metadata jsonb not null default '{}'::jsonb;

update public.notifications
set
    message = nullif(coalesce(nullif(message, ''), nullif(body, '')), ''),
    body = nullif(coalesce(nullif(body, ''), nullif(message, '')), ''),
    is_read = coalesce(is_read, "read", false),
    "read" = coalesce("read", is_read, false),
    target_role = coalesce(nullif(target_role, ''), 'all'),
    type = coalesce(nullif(type, ''), 'general'),
    metadata = coalesce(metadata, '{}'::jsonb)
where true;

alter table public.notifications enable row level security;

do $$
begin
    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'notifications'
          and policyname = 'users read scoped notifications'
    ) then
        create policy "users read scoped notifications"
        on public.notifications
        for select
        to authenticated
        using (
            target_role = 'all'
            or user_id is null
            or auth.uid()::text = user_id
            or exists (select 1 from public.users u where u.id = auth.uid()::text and u.role = 'admin')
        );
    end if;

    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'notifications'
          and policyname = 'authenticated create notifications'
    ) then
        create policy "authenticated create notifications"
        on public.notifications
        for insert
        to authenticated
        with check (true);
    end if;

    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'notifications'
          and policyname = 'users update scoped notifications'
    ) then
        create policy "users update scoped notifications"
        on public.notifications
        for update
        to authenticated
        using (
            target_role = 'all'
            or user_id is null
            or auth.uid()::text = user_id
            or exists (select 1 from public.users u where u.id = auth.uid()::text and u.role = 'admin')
        )
        with check (
            target_role = 'all'
            or user_id is null
            or auth.uid()::text = user_id
            or exists (select 1 from public.users u where u.id = auth.uid()::text and u.role = 'admin')
        );
    end if;
end $$;

notify pgrst, 'reload schema';
