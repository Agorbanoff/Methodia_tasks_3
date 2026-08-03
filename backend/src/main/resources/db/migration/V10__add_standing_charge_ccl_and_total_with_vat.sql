alter table invoices
    add column if not exists total_amount_with_vat numeric(19, 2);

update invoices
set total_amount_with_vat = total_amount
where total_amount_with_vat is null;

alter table invoices
    alter column total_amount_with_vat set not null;

alter table invoice_lines
    add column if not exists source_line_id varchar(32);

alter table invoice_lines
    add constraint fk_invoice_lines_source_line
    foreign key (source_line_id) references invoice_lines(id);

create index if not exists idx_invoice_lines_source_line_id
    on invoice_lines(source_line_id);
