alter table customers
    add column if not exists price_list int;

update customers
set price_list = case
    when tariff_code ~ '\d+' then regexp_replace(tariff_code, '\D+', '', 'g')::int
    else 1
end
where price_list is null;

alter table customers
    alter column price_list set not null;

alter table customers
    drop column if exists tariff_code;

alter table users
    drop column if exists tariff_code;

alter table prices
    drop column if exists tariff_code;
