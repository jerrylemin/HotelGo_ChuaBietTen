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

create table if not exists public.posters (
    id text primary key,
    type text not null default 'search',
    title text not null default '',
    created_at timestamptz not null default now()
);

alter table public.posters
    add column if not exists description text,
    add column if not exists content text,
    add column if not exists user_id text,
    add column if not exists created_by text,
    add column if not exists user_email text,
    add column if not exists budget numeric not null default 0,
    add column if not exists admin_reply text,
    add column if not exists response text,
    add column if not exists status text not null default 'new',
    add column if not exists updated_at timestamptz not null default now();

alter table public.room_requests
    add column if not exists user_id text not null default '',
    add column if not exists user_email text,
    add column if not exists request_text text not null default '',
    add column if not exists budget numeric not null default 0,
    add column if not exists admin_reply text,
    add column if not exists status text not null default 'new',
    add column if not exists created_at timestamptz not null default now(),
    add column if not exists updated_at timestamptz not null default now();

insert into public.room_requests (
    id,
    user_id,
    user_email,
    request_text,
    budget,
    admin_reply,
    status,
    created_at,
    updated_at
)
select
    p.id,
    coalesce(nullif(p.user_id, ''), nullif(p.created_by, ''), '') as user_id,
    nullif(p.user_email, '') as user_email,
    coalesce(nullif(p.description, ''), nullif(p.content, ''), nullif(p.title, ''), '') as request_text,
    coalesce(p.budget, 0) as budget,
    nullif(coalesce(nullif(p.admin_reply, ''), nullif(p.response, '')), '') as admin_reply,
    case
        when lower(coalesce(p.status, 'new')) in ('processing', 'in_progress', 'dang_xu_ly') then 'processing'
        when lower(coalesce(p.status, 'new')) in ('resolved', 'done', 'closed', 'da_xu_ly') then 'resolved'
        else 'new'
    end as status,
    coalesce(p.created_at, now()) as created_at,
    coalesce(p.updated_at, p.created_at, now()) as updated_at
from public.posters p
where p.type = 'search'
  and coalesce(nullif(p.user_id, ''), nullif(p.created_by, ''), '') <> ''
on conflict (id) do update
set
    user_id = excluded.user_id,
    user_email = coalesce(excluded.user_email, public.room_requests.user_email),
    request_text = excluded.request_text,
    budget = excluded.budget,
    admin_reply = coalesce(excluded.admin_reply, public.room_requests.admin_reply),
    status = excluded.status,
    updated_at = excluded.updated_at;

alter table public.room_requests enable row level security;

do $$
begin
    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'room_requests'
          and policyname = 'users read own room requests'
    ) then
        create policy "users read own room requests"
        on public.room_requests
        for select
        to authenticated
        using (
            auth.uid()::text = user_id
            or exists (select 1 from public.users u where u.id = auth.uid()::text and u.role = 'admin')
        );
    end if;

    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'room_requests'
          and policyname = 'users create own room requests'
    ) then
        create policy "users create own room requests"
        on public.room_requests
        for insert
        to authenticated
        with check (auth.uid()::text = user_id);
    end if;

    if not exists (
        select 1 from pg_policies
        where schemaname = 'public'
          and tablename = 'room_requests'
          and policyname = 'admins update room requests'
    ) then
        create policy "admins update room requests"
        on public.room_requests
        for update
        to authenticated
        using (exists (select 1 from public.users u where u.id = auth.uid()::text and u.role = 'admin'))
        with check (exists (select 1 from public.users u where u.id = auth.uid()::text and u.role = 'admin'));
    end if;
end $$;

notify pgrst, 'reload schema';
