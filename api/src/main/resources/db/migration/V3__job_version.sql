-- Optimistic locking for the job state machine.
alter table jobs add column version bigint not null default 0;
