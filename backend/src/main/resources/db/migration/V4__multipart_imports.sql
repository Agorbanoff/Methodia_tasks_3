alter table users add column if not exists tariff_code varchar(50);
update users set tariff_code = 'T' || price_list where tariff_code is null;

alter table prices add column if not exists tariff_code varchar(50);
update prices set tariff_code = 'T' || price_list where tariff_code is null;

alter table file_imports add column if not exists status varchar(30);
alter table file_imports add column if not exists imported_records int;
alter table file_imports add column if not exists error_count int;

create index if not exists idx_users_tariff_code on users(tariff_code);
create index if not exists idx_prices_tariff_code_product_dates on prices(tariff_code, product, start_date, end_date);
