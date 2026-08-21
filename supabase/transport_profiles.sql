alter table public.profiles add column if not exists user_id uuid references auth.users(id) on delete cascade;
alter table public.profiles add column if not exists name text;
alter table public.profiles add column if not exists limits jsonb not null default '{}'::jsonb;
alter table public.profiles add column if not exists is_standard boolean not null default false;

create unique index if not exists profiles_user_id_id_unique on public.profiles (user_id, id);

alter table public.profiles enable row level security;

drop policy if exists "Users can read own transport profiles" on public.profiles;
create policy "Users can read own transport profiles"
on public.profiles for select
using (auth.uid() = user_id);

drop policy if exists "Users can insert own transport profiles" on public.profiles;
create policy "Users can insert own transport profiles"
on public.profiles for insert
with check (auth.uid() = user_id);

drop policy if exists "Users can update own transport profiles" on public.profiles;
create policy "Users can update own transport profiles"
on public.profiles for update
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

drop policy if exists "Users can delete own transport profiles" on public.profiles;
create policy "Users can delete own transport profiles"
on public.profiles for delete
using (auth.uid() = user_id);