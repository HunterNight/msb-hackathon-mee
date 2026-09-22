-- memory-hook-service. The only writer of customer memory (design §3).
-- Nothing here holds a real customer id: `pseudo_id` is HMAC(customerId, MEMORY_PSEUDONYM_KEY)
-- and the only way back from a request is `customer_key.customer_id_hmac` (a second HMAC).

create extension if not exists vector;

-- Per-customer data-encryption key, wrapped by the KEK. Dropping this row crypto-shreds the
-- customer's memory, which is what erasure does first.
create table customer_key (
  pseudo_id        text primary key,
  customer_id_hmac text        not null unique,
  dek_wrapped      bytea       not null,
  kek_version      text        not null,
  rotated_at       timestamptz,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz,
  created_by       text,
  version          bigint      not null default 0
);

create table memory_consent (
  pseudo_id      text primary key references customer_key (pseudo_id) on delete cascade,
  long_term      boolean     not null default false,
  snapshot       boolean     not null default true,
  improve_models boolean     not null default false,
  policy_version text        not null,
  source         text        not null,
  created_at     timestamptz not null default now(),
  updated_at     timestamptz,
  created_by     text,
  version        bigint      not null default 0
);

-- `text_enc` is the exact sentence (AES-GCM under the customer DEK); `abstract_text` is the
-- sanitised form that gets embedded and is the only text ever returned to Mi.
create table memory_record (
  id             uuid primary key,
  pseudo_id      text          not null references customer_key (pseudo_id) on delete cascade,
  kind           text          not null,
  text_enc       bytea         not null,
  abstract_text  text          not null,
  embedding      vector(384),
  confidence     numeric(4, 3) not null,
  source         text          not null,
  pinned         boolean       not null default false,
  supersedes     uuid,
  normalised_key text          not null,
  refs_enc       bytea,
  classification text          not null default 'INTERNAL',
  valid_from     timestamptz   not null default now(),
  valid_until    timestamptz,
  created_at     timestamptz   not null default now(),
  updated_at     timestamptz,
  created_by     text,
  version        bigint        not null default 0
);
create index idx_record_pseudo on memory_record (pseudo_id, kind);
-- now() is not immutable, so the freshness test belongs in the query, not the index.
create index idx_record_live on memory_record (pseudo_id, valid_until);
create unique index uq_record_key on memory_record (pseudo_id, kind, normalised_key)
  where supersedes is null;

create table record_source (
  id         uuid primary key,
  record_id  uuid        not null references memory_record (id) on delete cascade,
  event_id   text        not null,
  event_type text        not null,
  event_time timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz,
  created_by text,
  version    bigint      not null default 0
);
create index idx_record_source_record on record_source (record_id);

-- `public_slice` is bucketed and prompt-safe; `snapshot_enc` holds the exact aggregates that only
-- the proactive policy engine ever sees.
create table customer_snapshot (
  pseudo_id     text primary key references customer_key (pseudo_id) on delete cascade,
  snapshot_enc  bytea       not null,
  public_slice  jsonb       not null default '{}'::jsonb,
  snapshot_version int      not null default 1,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz,
  created_by    text,
  version       bigint      not null default 0
);

-- Minimised facts distilled from events: no merchant strings, no account numbers, no notes.
-- This is what the recurring/salary extractors count; raw events are never retained.
create table event_fact (
  id            uuid primary key,
  pseudo_id     text        not null references customer_key (pseudo_id) on delete cascade,
  fact_type     text        not null,
  ref_key       text        not null,
  amount        numeric(19, 0),
  category_code text,
  day_of_month  int,
  pct           int,
  occurred_at   timestamptz not null,
  event_id      text        not null,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz,
  created_by    text,
  version       bigint      not null default 0
);
create index idx_fact_lookup on event_fact (pseudo_id, fact_type, ref_key, occurred_at desc);
create unique index uq_fact_event on event_fact (event_id, fact_type, ref_key);

-- Ids and status only, so a duplicate delivery is a no-op and nothing replayable is kept.
create table ingest_log (
  event_id   text primary key,
  event_type text        not null,
  pseudo_id  text,
  status     text        not null,
  reason     text,
  at         timestamptz not null default now()
);
create index idx_ingest_at on ingest_log (at);

create table access_audit (
  id         uuid primary key,
  pseudo_id  text        not null,
  actor      text        not null,
  scope      text        not null,
  operation  text        not null,
  records    int         not null default 0,
  request_id text,
  at         timestamptz not null default now()
);
create index idx_audit_pseudo on access_audit (pseudo_id, at desc);

create table erasure_request (
  id           uuid primary key,
  pseudo_id    text        not null,
  status       text        not null,
  reason       text        not null,
  requested_by text        not null,
  requested_at timestamptz not null default now(),
  sla_at       timestamptz not null,
  completed_at timestamptz,
  proof        jsonb,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz,
  created_by   text,
  version      bigint      not null default 0
);
create index idx_erasure_pending on erasure_request (status, requested_at);

-- Poison/parse failures. The payload is kept redacted so ops can decide without seeing raw PII.
create table dlq_entry (
  id            uuid primary key,
  event_id      text        not null,
  event_type    text        not null,
  redacted_body text        not null,
  reason        text        not null,
  attempts      int         not null default 1,
  at            timestamptz not null default now(),
  created_at    timestamptz not null default now(),
  updated_at    timestamptz,
  created_by    text,
  version       bigint      not null default 0
);
create index idx_dlq_at on dlq_entry (at desc);

create table outbox (
  id             uuid primary key,
  aggregate_type text        not null,
  aggregate_id   uuid        not null,
  event_type     text        not null,
  payload        jsonb       not null,
  created_at     timestamptz not null default now(),
  published_at   timestamptz,
  attempts       int         not null default 0
);
create index idx_outbox_unpublished on outbox (created_at) where published_at is null;
