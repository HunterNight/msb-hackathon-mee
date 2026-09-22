-- mi-assistant-service (screens 06–10). Mi owns conversations, proposals, autonomy, plans and the
-- vector index; knowledge documents and agent definitions belong to cms-service and are read
-- through its internal API (design §2).

create extension if not exists vector;

create table conversation (
  id                uuid primary key,
  customer_id       uuid        not null,
  title             text,
  active_agent_code text        not null default 'general',
  status            text        not null default 'OPEN',
  last_message_at   timestamptz,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz,
  created_by        text,
  version           bigint      not null default 0
);
create index idx_conversation_customer on conversation (customer_id, last_message_at desc);

create table message (
  id              uuid primary key,
  conversation_id uuid        not null references conversation (id) on delete cascade,
  customer_id     uuid        not null,
  role            text        not null,
  kind            text        not null,
  text            text,
  payload         jsonb,
  chips           jsonb       not null default '[]'::jsonb,
  citations       jsonb       not null default '[]'::jsonb,
  agent_code      text,
  safety          text,
  seq             int         not null,
  created_at      timestamptz not null default now(),
  updated_at      timestamptz,
  created_by      text,
  version         bigint      not null default 0
);
create unique index uq_message_seq on message (conversation_id, seq);
create index idx_message_conversation on message (conversation_id, seq);

-- Money only ever moves through a row in this table: the model may propose, never execute.
create table proposal (
  id              uuid primary key,
  customer_id     uuid          not null,
  message_id      uuid,
  conversation_id uuid,
  type            text          not null,
  amount          numeric(19, 0) not null default 0,
  params          jsonb         not null default '{}'::jsonb,
  card            jsonb         not null default '{}'::jsonb,
  phase           text          not null,
  ref             text,
  executed_by     text,
  requires_step_up boolean      not null default true,
  step_up_scope   text,
  idempotency_key text          not null unique,
  decided_at      timestamptz,
  expires_at      timestamptz   not null,
  created_at      timestamptz   not null default now(),
  updated_at      timestamptz,
  created_by      text,
  version         bigint        not null default 0
);
create index idx_proposal_customer on proposal (customer_id, created_at desc);
create index idx_proposal_pending on proposal (expires_at) where phase = 'PROPOSE';

create table customer_autonomy (
  customer_id           uuid primary key,
  perm_recurring_bills  boolean       not null default false,
  perm_saved_recipients boolean       not null default false,
  perm_auto_saving      boolean       not null default false,
  perm_home_insights    boolean       not null default true,
  auto_limit            numeric(19, 0) not null default 2000000,
  paused                boolean       not null default false,
  proactive             boolean       not null default true,
  memory_long_term      boolean       not null default false,
  improve_models        boolean       not null default false,
  policy_version        text          not null default '2026-09-01',
  created_at            timestamptz   not null default now(),
  updated_at            timestamptz,
  created_by            text,
  version               bigint        not null default 0
);

-- Localised at read time: the log stores keys and arguments, never rendered sentences.
create table activity_log (
  id            uuid primary key,
  customer_id   uuid        not null,
  kind          text        not null,
  title_key     text        not null,
  title_args    jsonb       not null default '{}'::jsonb,
  subtitle_key  text        not null,
  subtitle_args jsonb       not null default '{}'::jsonb,
  proposal_id   uuid,
  ref           text,
  deep_link     text,
  occurred_at   timestamptz not null default now(),
  created_at    timestamptz not null default now(),
  updated_at    timestamptz,
  created_by    text,
  version       bigint      not null default 0
);
create index idx_activity_customer on activity_log (customer_id, occurred_at desc);

create table budget (
  id            uuid primary key,
  customer_id   uuid          not null,
  category_code text          not null,
  monthly_limit numeric(19, 0) not null,
  alert_pct     int           not null default 80,
  active        boolean       not null default true,
  created_at    timestamptz   not null default now(),
  updated_at    timestamptz,
  created_by    text,
  version       bigint        not null default 0
);
create unique index uq_budget_category on budget (customer_id, category_code) where active;

-- The RAG index. Chunks are derived from cms-service documents and re-embedded on its webhook.
create table document_chunk (
  id         uuid primary key,
  doc_id     uuid  not null,
  collection_code text not null,
  locale     text  not null,
  doc_version int  not null,
  title      text  not null,
  section    text,
  content    text  not null,
  deep_link  text,
  metadata   jsonb not null default '{}'::jsonb,
  embedding  vector(384),
  created_at timestamptz not null default now(),
  updated_at timestamptz,
  created_by text,
  version    bigint not null default 0
);
create index idx_chunk_doc on document_chunk (doc_id);
create index idx_chunk_collection on document_chunk (collection_code, locale);

create table insight (
  id          uuid primary key,
  customer_id uuid        not null,
  placement   text        not null,
  text_key    text        not null,
  args        jsonb       not null default '{}'::jsonb,
  prompt      text,
  deep_link   text,
  action_key  text,
  tone        text        not null default 'DEFAULT',
  trigger_code text,
  valid_until timestamptz,
  created_at  timestamptz not null default now(),
  updated_at  timestamptz,
  created_by  text,
  version     bigint      not null default 0
);
create index idx_insight_lookup on insight (customer_id, placement, valid_until);

-- Proactive items the customer sees in-app, with the explanation the design demands.
create table nudge (
  id           uuid primary key,
  customer_id  uuid        not null,
  trigger_code text        not null,
  placement    text        not null,
  text_key     text        not null,
  args         jsonb       not null default '{}'::jsonb,
  explain_key  text        not null,
  prompt       text,
  deep_link    text,
  dismissed    boolean     not null default false,
  dismissed_at timestamptz,
  expires_at   timestamptz not null,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz,
  created_by   text,
  version      bigint      not null default 0
);
create index idx_nudge_customer on nudge (customer_id, dismissed, expires_at);

create table plan (
  id           uuid primary key,
  customer_id  uuid        not null,
  title        text        not null,
  status       text        not null,
  total_amount numeric(19, 0) not null default 0,
  log          jsonb       not null default '[]'::jsonb,
  expires_at   timestamptz,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz,
  created_by   text,
  version      bigint      not null default 0
);
create index idx_plan_customer on plan (customer_id, status);

create table plan_step (
  id                uuid primary key,
  plan_id           uuid        not null references plan (id) on delete cascade,
  seq               int         not null,
  kind              text        not null,
  title             text        not null,
  status            text        not null,
  amount            numeric(19, 0),
  requires_approval boolean     not null default true,
  proposal_id       uuid,
  waiting_for       text,
  created_at        timestamptz not null default now(),
  updated_at        timestamptz,
  created_by        text,
  version           bigint      not null default 0
);
create unique index uq_plan_step_seq on plan_step (plan_id, seq);

-- Why Mi did something, kept for the /explain endpoint.
create table decision_log (
  id            uuid primary key,
  customer_id   uuid        not null,
  kind          text        not null,
  reason_key    text        not null,
  inputs        jsonb       not null default '[]'::jsonb,
  policy_name   text,
  policy_version text,
  model_profile text,
  model_version text,
  created_at    timestamptz not null default now(),
  updated_at    timestamptz,
  created_by    text,
  version       bigint      not null default 0
);
create index idx_decision_customer on decision_log (customer_id, created_at desc);

-- Repeated illegal turns feed the fraud review threshold (design §12.4).
create table safety_flag (
  id          uuid primary key,
  customer_id uuid        not null,
  label       text        not null,
  excerpt     text,
  at          timestamptz not null default now(),
  created_at  timestamptz not null default now(),
  updated_at  timestamptz,
  created_by  text,
  version     bigint      not null default 0
);
create index idx_safety_customer on safety_flag (customer_id, at desc);

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

create table idempotency_key (
  key          text primary key,
  customer_id  uuid        not null,
  request_hash text        not null,
  response     jsonb,
  status_code  int,
  created_at   timestamptz not null default now()
);
