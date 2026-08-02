alter table users add column if not exists username varchar(100);
alter table users add column if not exists password_hash varchar(255);
alter table users add column if not exists role varchar(20) not null default 'USER';
update users set username = reference where username is null;
create unique index if not exists idx_users_username on users(username);

create table if not exists billing_runs (
    id varchar(32) primary key,
    period_start varchar(5) not null,
    period_end varchar(5) not null,
    status varchar(30) not null,
    processed_count int not null default 0,
    failed_count int not null default 0,
    requested_by_id varchar(32) null references users(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists audit_logs (
    id varchar(32) primary key,
    action varchar(80) not null,
    actor_reference varchar(100),
    description varchar(1000),
    created_at timestamp with time zone not null
);

create table if not exists billing_error_logs (
    id varchar(32) primary key,
    type varchar(120) not null,
    description varchar(1000) not null,
    customer_id varchar(100),
    module varchar(120) not null,
    severity varchar(20) not null,
    status varchar(20) not null,
    created_at timestamp with time zone not null
);

create index if not exists idx_billing_runs_created_at on billing_runs(created_at);
create index if not exists idx_audit_logs_created_at on audit_logs(created_at);
create index if not exists idx_billing_error_logs_created_at on billing_error_logs(created_at);
