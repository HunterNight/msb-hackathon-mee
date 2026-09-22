-- MEE customer-understanding upgrade: epistemic state machine + memory_link
-- BRD 1 §6.2: Signal ≠ Fact. Behaviour-derived records become HYPOTHESIS until confirmed.

alter table memory_record
  add column if not exists state              text        not null default 'HYPOTHESIS',
  add column if not exists explicit           boolean     not null default false,
  add column if not exists customer_confirmed boolean     not null default false,
  add column if not exists last_confirmed_at  timestamptz,
  add column if not exists persistence        text        not null default 'LONG_TERM',
  add column if not exists entity             text,
  add column if not exists reason_enc         bytea,
  add column if not exists evidence_summary   text,
  add column if not exists suppressed_until   timestamptz;

-- BR01 correction: existing CHAT rows the customer stated are CONFIRMED;
-- all EVENTS-derived rows are HYPOTHESIS (never facts without the customer).
update memory_record set state = 'CONFIRMED', explicit = true, customer_confirmed = true
  where source = 'CHAT' and state = 'HYPOTHESIS';

-- NICKNAME records from a confirmed transfer are already customer-confirmed.
update memory_record set state = 'CONFIRMED', customer_confirmed = true
  where kind = 'NICKNAME' and source = 'EVENTS' and state = 'HYPOTHESIS';

-- Set persistence based on kind for existing rows.
update memory_record set persistence = 'MEDIUM_TERM' where kind = 'PLAN';
update memory_record set persistence = 'TEMPORARY'
  where kind in ('EVENT','TRANSACTION_CONTEXT');

create index if not exists idx_record_state on memory_record (pseudo_id, state);
create index if not exists idx_record_entity on memory_record (pseudo_id, entity);
create index if not exists idx_record_suppressed on memory_record (pseudo_id, suppressed_until);

-- Connections between memories (BRD §6.4 "Connect the dots")
create table if not exists memory_link (
  id            uuid primary key,
  pseudo_id     text not null,
  from_record   uuid not null references memory_record (id) on delete cascade,
  to_record     uuid not null references memory_record (id) on delete cascade,
  relation      text not null,
  state         text not null,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz,
  created_by    text,
  version       bigint not null default 0,
  confirmed_at  timestamptz
);

create index idx_link_pseudo on memory_link (pseudo_id, state);
create index idx_link_from on memory_link (from_record);
create index idx_link_to on memory_link (to_record);
