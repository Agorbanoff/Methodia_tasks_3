create table users (
    id varchar(32) primary key,
    name varchar(255) not null,
    reference varchar(100) not null unique,
    price_list int not null
);

create table file_imports (
    id varchar(32) primary key,
    type varchar(30) not null,
    filename varchar(255) not null,
    uploaded_by_id varchar(32) null references users(id),
    uploaded_at timestamp with time zone not null,
    file_content bytea null
);

create table readings (
    id varchar(32) primary key,
    user_id varchar(32) not null references users(id),
    product varchar(20) not null,
    date_time timestamp with time zone not null,
    last_reading numeric(19, 3) not null,
    invoiced boolean not null default false,
    self_reported boolean not null default false,
    file_import_id varchar(32) null references file_imports(id),
    unique(user_id, product, date_time)
);

create table prices (
    id varchar(32) primary key,
    product varchar(20) not null,
    start_date date not null,
    end_date date not null,
    price numeric(19, 4) not null,
    price_list int not null,
    file_import_id varchar(32) null references file_imports(id),
    unique(price_list, product, start_date, end_date)
);

create table invoices (
    id varchar(32) primary key,
    date_time timestamp with time zone not null,
    number varchar(50) not null unique,
    user_id varchar(32) not null references users(id),
    total_amount numeric(19, 2) not null,
    paid boolean not null default false,
    billing_year int not null,
    billing_month int not null,
    unique(user_id, billing_year, billing_month)
);

create table invoice_lines (
    id varchar(32) primary key,
    line_id int not null,
    invoice_id varchar(32) not null references invoices(id) on delete cascade,
    quantity numeric(19, 2) not null,
    start_date_time timestamp with time zone not null,
    end_date_time timestamp with time zone not null,
    product varchar(20) not null,
    price numeric(19, 2) not null,
    price_list int not null,
    amount numeric(19, 2) not null
);

create index idx_readings_user_product_date_time
    on readings(user_id, product, date_time);

create index idx_prices_price_list_product_start_date_end_date
    on prices(price_list, product, start_date, end_date);

create index idx_invoices_number
    on invoices(number);

create index idx_invoices_user_billing_year_billing_month
    on invoices(user_id, billing_year, billing_month);

create index idx_invoice_lines_invoice_id
    on invoice_lines(invoice_id);
