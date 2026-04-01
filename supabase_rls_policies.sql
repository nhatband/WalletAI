-- Run this in Supabase SQL Editor for development.
-- It keeps RLS enabled but allows this mobile app to read/write all synced tables.
-- It also adds the schema required for credit card sync.

alter table if exists public.expenses
add column if not exists credit_card_id bigint null;

create table if not exists public.credit_cards (
  id bigint primary key,
  name text not null,
  holder_name text not null,
  last4_digits text not null,
  statement_day integer not null,
  image_uri text null,
  created_at bigint not null
);

alter table if exists public.expenses enable row level security;
alter table if exists public.friends enable row level security;
alter table if exists public.expense_friend_cross_ref enable row level security;
alter table if exists public.credit_cards enable row level security;

drop policy if exists "dev_expenses_select" on public.expenses;
drop policy if exists "dev_expenses_insert" on public.expenses;
drop policy if exists "dev_expenses_update" on public.expenses;
drop policy if exists "dev_expenses_delete" on public.expenses;

create policy "dev_expenses_select"
on public.expenses
for select
to authenticated
using (true);

create policy "dev_expenses_insert"
on public.expenses
for insert
to authenticated
with check (true);

create policy "dev_expenses_update"
on public.expenses
for update
to authenticated
using (true)
with check (true);

create policy "dev_expenses_delete"
on public.expenses
for delete
to authenticated
using (true);

drop policy if exists "dev_friends_select" on public.friends;
drop policy if exists "dev_friends_insert" on public.friends;
drop policy if exists "dev_friends_update" on public.friends;
drop policy if exists "dev_friends_delete" on public.friends;

create policy "dev_friends_select"
on public.friends
for select
to authenticated
using (true);

create policy "dev_friends_insert"
on public.friends
for insert
to authenticated
with check (true);

create policy "dev_friends_update"
on public.friends
for update
to authenticated
using (true)
with check (true);

create policy "dev_friends_delete"
on public.friends
for delete
to authenticated
using (true);

drop policy if exists "dev_cross_ref_select" on public.expense_friend_cross_ref;
drop policy if exists "dev_cross_ref_insert" on public.expense_friend_cross_ref;
drop policy if exists "dev_cross_ref_update" on public.expense_friend_cross_ref;
drop policy if exists "dev_cross_ref_delete" on public.expense_friend_cross_ref;

create policy "dev_cross_ref_select"
on public.expense_friend_cross_ref
for select
to authenticated
using (true);

create policy "dev_cross_ref_insert"
on public.expense_friend_cross_ref
for insert
to authenticated
with check (true);

create policy "dev_cross_ref_update"
on public.expense_friend_cross_ref
for update
to authenticated
using (true)
with check (true);

create policy "dev_cross_ref_delete"
on public.expense_friend_cross_ref
for delete
to authenticated
using (true);

drop policy if exists "dev_credit_cards_select" on public.credit_cards;
drop policy if exists "dev_credit_cards_insert" on public.credit_cards;
drop policy if exists "dev_credit_cards_update" on public.credit_cards;
drop policy if exists "dev_credit_cards_delete" on public.credit_cards;

create policy "dev_credit_cards_select"
on public.credit_cards
for select
to authenticated
using (true);

create policy "dev_credit_cards_insert"
on public.credit_cards
for insert
to authenticated
with check (true);

create policy "dev_credit_cards_update"
on public.credit_cards
for update
to authenticated
using (true)
with check (true);

create policy "dev_credit_cards_delete"
on public.credit_cards
for delete
to authenticated
using (true);
