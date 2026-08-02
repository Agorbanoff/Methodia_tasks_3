create unique index if not exists uk_invoices_customer_period
    on invoices(customer_id, billing_year, billing_month);
