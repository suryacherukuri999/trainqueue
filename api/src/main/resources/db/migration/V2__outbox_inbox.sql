-- Transactional outbox: events written in the job transaction, relayed to Kafka.
create table outbox_events (
    id           uuid primary key,        -- stable eventId, also the Kafka dedup key
    topic        varchar(100) not null,
    msg_key      varchar(200) not null,
    payload      text not null,
    created_at   timestamptz not null,
    published_at timestamptz
);
create index idx_outbox_pending on outbox_events (created_at) where published_at is null;

-- Consumer inbox: idempotent application keyed by (consumer, event_id).
create table inbox_events (
    id           varchar(220) primary key,  -- consumer + ':' + event_id
    consumer     varchar(100) not null,
    event_id     uuid not null,
    processed_at timestamptz not null
);
