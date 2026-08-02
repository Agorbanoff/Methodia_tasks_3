alter table readings add column if not exists source varchar(30);

update readings
set source = case
    when self_reported then 'SELF_REPORTED'
    else 'IMPORTED'
end
where source is null;

alter table readings alter column source set default 'IMPORTED';
alter table readings alter column source set not null;

create table if not exists self_reports (
    id varchar(32) primary key,
    customer_id varchar(32) not null references customers(id),
    service varchar(20) not null,
    reading_date date not null,
    amount numeric(19, 3) not null,
    status varchar(30) not null,
    requested_at timestamp with time zone not null,
    reviewed_at timestamp with time zone,
    reviewed_by_id varchar(32) references users(id)
);

create unique index if not exists idx_self_reports_pending_unique
    on self_reports(customer_id, service, reading_date)
    where status = 'PENDING';

create index if not exists idx_self_reports_customer_status
    on self_reports(customer_id, status);

create index if not exists idx_self_reports_requested_at
    on self_reports(requested_at desc);

create index if not exists idx_readings_customer_service_reading_date
    on readings(customer_id, product, date_time);

create index if not exists idx_readings_source
    on readings(source);
