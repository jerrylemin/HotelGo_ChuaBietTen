alter table if exists public.issues
    add column if not exists booking_id text;

create index if not exists issues_booking_id_idx on public.issues (booking_id);

alter table public.issues enable row level security;

drop policy if exists "Issues are readable by owners and admins" on public.issues;
create policy "Issues are readable by owners and admins"
on public.issues for select
using (
    auth.uid()::text = user_id
    or exists (
        select 1
        from public.users u
        where u.id = auth.uid()::text
          and u.role = 'admin'
    )
);

drop policy if exists "Users can create their own issues" on public.issues;
create policy "Users can create their own issues"
on public.issues for insert
with check (auth.uid()::text = user_id);

drop policy if exists "Admins can update issues" on public.issues;
create policy "Admins can update issues"
on public.issues for update
using (
    exists (
        select 1
        from public.users u
        where u.id = auth.uid()::text
          and u.role = 'admin'
    )
);
