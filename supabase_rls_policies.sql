begin;

alter table if exists public.expenses
add column if not exists credit_card_id bigint null;

alter table if exists public.expenses
add column if not exists user_id uuid;

alter table if exists public.friends
add column if not exists user_id uuid;

create table if not exists public.credit_cards (
  id bigint primary key,
  user_id uuid not null,
  name text not null,
  holder_name text not null,
  last4_digits text not null,
  statement_day integer not null,
  image_uri text null,
  created_at bigint not null
);

alter table if exists public.credit_cards
add column if not exists user_id uuid;

alter table if exists public.expenses enable row level security;
alter table if exists public.friends enable row level security;
alter table if exists public.expense_friend_cross_ref enable row level security;
alter table if exists public.credit_cards enable row level security;

create index if not exists expenses_user_id_idx on public.expenses(user_id);
create index if not exists friends_user_id_idx on public.friends(user_id);
create index if not exists credit_cards_user_id_idx on public.credit_cards(user_id);

drop policy if exists "dev_expenses_select" on public.expenses;
drop policy if exists "dev_expenses_insert" on public.expenses;
drop policy if exists "dev_expenses_update" on public.expenses;
drop policy if exists "dev_expenses_delete" on public.expenses;
drop policy if exists "dev_friends_select" on public.friends;
drop policy if exists "dev_friends_insert" on public.friends;
drop policy if exists "dev_friends_update" on public.friends;
drop policy if exists "dev_friends_delete" on public.friends;
drop policy if exists "dev_cross_ref_select" on public.expense_friend_cross_ref;
drop policy if exists "dev_cross_ref_insert" on public.expense_friend_cross_ref;
drop policy if exists "dev_cross_ref_update" on public.expense_friend_cross_ref;
drop policy if exists "dev_cross_ref_delete" on public.expense_friend_cross_ref;
drop policy if exists "dev_credit_cards_select" on public.credit_cards;
drop policy if exists "dev_credit_cards_insert" on public.credit_cards;
drop policy if exists "dev_credit_cards_update" on public.credit_cards;
drop policy if exists "dev_credit_cards_delete" on public.credit_cards;
drop policy if exists "expenses_select_own" on public.expenses;
drop policy if exists "expenses_insert_own" on public.expenses;
drop policy if exists "expenses_update_own" on public.expenses;
drop policy if exists "expenses_delete_own" on public.expenses;
drop policy if exists "friends_select_own" on public.friends;
drop policy if exists "friends_insert_own" on public.friends;
drop policy if exists "friends_update_own" on public.friends;
drop policy if exists "friends_delete_own" on public.friends;
drop policy if exists "credit_cards_select_own" on public.credit_cards;
drop policy if exists "credit_cards_insert_own" on public.credit_cards;
drop policy if exists "credit_cards_update_own" on public.credit_cards;
drop policy if exists "credit_cards_delete_own" on public.credit_cards;
drop policy if exists "cross_ref_select_own" on public.expense_friend_cross_ref;
drop policy if exists "cross_ref_insert_own" on public.expense_friend_cross_ref;
drop policy if exists "cross_ref_update_own" on public.expense_friend_cross_ref;
drop policy if exists "cross_ref_delete_own" on public.expense_friend_cross_ref;

create policy "expenses_select_own"
on public.expenses
for select
to authenticated
using (auth.uid() = user_id);

create policy "expenses_insert_own"
on public.expenses
for insert
to authenticated
with check (auth.uid() = user_id);

create policy "expenses_update_own"
on public.expenses
for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

create policy "expenses_delete_own"
on public.expenses
for delete
to authenticated
using (auth.uid() = user_id);

create policy "friends_select_own"
on public.friends
for select
to authenticated
using (auth.uid() = user_id);

create policy "friends_insert_own"
on public.friends
for insert
to authenticated
with check (auth.uid() = user_id);

create policy "friends_update_own"
on public.friends
for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

create policy "friends_delete_own"
on public.friends
for delete
to authenticated
using (auth.uid() = user_id);

create policy "credit_cards_select_own"
on public.credit_cards
for select
to authenticated
using (auth.uid() = user_id);

create policy "credit_cards_insert_own"
on public.credit_cards
for insert
to authenticated
with check (auth.uid() = user_id);

create policy "credit_cards_update_own"
on public.credit_cards
for update
to authenticated
using (auth.uid() = user_id)
with check (auth.uid() = user_id);

create policy "credit_cards_delete_own"
on public.credit_cards
for delete
to authenticated
using (auth.uid() = user_id);

create policy "cross_ref_select_own"
on public.expense_friend_cross_ref
for select
to authenticated
using (
  exists (
    select 1
    from public.expenses e
    where e.id = expense_id
      and e.user_id = auth.uid()
  )
);

create policy "cross_ref_insert_own"
on public.expense_friend_cross_ref
for insert
to authenticated
with check (
  exists (
    select 1
    from public.expenses e
    where e.id = expense_id
      and e.user_id = auth.uid()
  )
);

create policy "cross_ref_update_own"
on public.expense_friend_cross_ref
for update
to authenticated
using (
  exists (
    select 1
    from public.expenses e
    where e.id = expense_id
      and e.user_id = auth.uid()
  )
)
with check (
  exists (
    select 1
    from public.expenses e
    where e.id = expense_id
      and e.user_id = auth.uid()
  )
);

create policy "cross_ref_delete_own"
on public.expense_friend_cross_ref
for delete
to authenticated
using (
  exists (
    select 1
    from public.expenses e
    where e.id = expense_id
      and e.user_id = auth.uid()
  )
);

commit;
