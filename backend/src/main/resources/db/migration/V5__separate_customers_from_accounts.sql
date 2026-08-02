create table if not exists customers (
    id varchar(32) primary key,
    reference varchar(100) not null unique,
    name varchar(255) not null,
    tariff_code varchar(50) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

insert into customers (id, reference, name, tariff_code, created_at, updated_at)
select id, reference, name, coalesce(tariff_code, 'T' || price_list), now(), now()
from users
where upper(role) <> 'ADMIN'
on conflict (reference) do update
set name = excluded.name,
    tariff_code = excluded.tariff_code,
    updated_at = now();

alter table users add column if not exists customer_id varchar(32);
alter table users add column if not exists enabled boolean not null default true;
alter table users add column if not exists created_at timestamp with time zone;

update users
set customer_id = customers.id
from customers
where users.customer_id is null
  and upper(users.role) = 'USER'
  and users.reference = customers.reference;

update users set created_at = now() where created_at is null;
alter table users alter column created_at set not null;

alter table users
    add constraint fk_users_customer
    foreign key (customer_id) references customers(id);

create unique index if not exists idx_users_customer_id
    on users(customer_id)
    where customer_id is not null;

alter table readings add column if not exists customer_id varchar(32);

update readings
set customer_id = customers.id
from users
join customers on customers.reference = users.reference
where readings.customer_id is null
  and readings.user_id = users.id;

alter table readings alter column user_id drop not null;
alter table readings alter column customer_id set not null;

alter table readings
    add constraint fk_readings_customer
    foreign key (customer_id) references customers(id);

create unique index if not exists idx_readings_customer_product_date_time
    on readings(customer_id, product, date_time);

create index if not exists idx_readings_customer_id
    on readings(customer_id);

alter table invoices add column if not exists customer_id varchar(32);

update invoices
set customer_id = customers.id
from users
join customers on customers.reference = users.reference
where invoices.customer_id is null
  and invoices.user_id = users.id;

alter table invoices alter column user_id drop not null;
alter table invoices alter column customer_id set not null;

alter table invoices
    add constraint fk_invoices_customer
    foreign key (customer_id) references customers(id);

create unique index if not exists idx_invoices_customer_billing_year_billing_month
    on invoices(customer_id, billing_year, billing_month);

create index if not exists idx_invoices_customer_id
    on invoices(customer_id);
