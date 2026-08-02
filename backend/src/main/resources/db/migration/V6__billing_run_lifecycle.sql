alter table billing_runs add column if not exists started_at timestamp with time zone;
alter table billing_runs add column if not exists ended_at timestamp with time zone;
alter table billing_runs add column if not exists started_by_id varchar(32);
alter table billing_runs add column if not exists total_records int not null default 0;
alter table billing_runs add column if not exists processed_records int not null default 0;
alter table billing_runs add column if not exists failed_records int not null default 0;
alter table billing_runs add column if not exists warning_records int not null default 0;
alter table billing_runs add column if not exists frozen_tariff_version varchar(100) not null default 'LEGACY';
alter table billing_runs add column if not exists reference varchar(100) not null default 'all';

update billing_runs
set started_at = coalesce(started_at, created_at),
    started_by_id = coalesce(started_by_id, requested_by_id),
    processed_records = case when processed_records = 0 then processed_count else processed_records end,
    failed_records = case when failed_records = 0 then failed_count else failed_records end
where true;

alter table billing_runs
    add constraint fk_billing_runs_started_by
    foreign key (started_by_id) references users(id);

alter table billing_runs
    alter column period_start type date
    using case
        when length(period_start) = 5 then to_date('20' || period_start || '-01', 'YYYY-MM-DD')
        else period_start::date
    end;

alter table billing_runs
    alter column period_end type date
    using case
        when length(period_end) = 5 then
            (date_trunc('month', to_date('20' || period_end || '-01', 'YYYY-MM-DD')) + interval '1 month - 1 day')::date
        else period_end::date
    end;

create table if not exists billing_run_items (
    id varchar(32) primary key,
    billing_run_id varchar(32) not null references billing_runs(id) on delete cascade,
    customer_id varchar(32) not null references customers(id),
    status varchar(30) not null,
    invoice_id varchar(32) null references invoices(id),
    severity varchar(20),
    error_message varchar(1000),
    processed_at timestamp with time zone,
    tariff_snapshot text not null,
    constraint uk_billing_run_items_run_customer unique (billing_run_id, customer_id)
);

create index if not exists idx_billing_runs_created_at_desc
    on billing_runs(created_at desc);

create index if not exists idx_billing_run_items_run_status
    on billing_run_items(billing_run_id, status);

create index if not exists idx_billing_run_items_customer
    on billing_run_items(customer_id);
