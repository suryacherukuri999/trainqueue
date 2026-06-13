create table jobs (
    id            uuid primary key,
    name          varchar(200) not null,
    docker_image  varchar(300) not null,
    command       varchar(500),
    epochs        integer not null,
    fail_at_epoch integer,
    priority      integer not null,
    cpu_millis    integer not null,
    mem_mb        integer not null,
    status        varchar(20) not null,
    attempt       integer not null,
    max_retries   integer not null,
    created_at    timestamptz not null,
    started_at    timestamptz,
    finished_at   timestamptz
);

create index idx_jobs_status on jobs (status);
