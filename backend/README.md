# Mini Billing Backend

Spring Boot backend for Mini Billing. The frontend folders in this repository are obsolete for the current contract.

## Run

Start PostgreSQL from the repository root:

```powershell
docker compose up -d postgres
```

Run tests or package from `backend`:

```powershell
D:\apache-maven-3.9.14\bin\mvn.cmd clean test
D:\apache-maven-3.9.14\bin\mvn.cmd clean package
```

## Import Contract

`POST /api/file/import` is ADMIN-only and accepts multipart form fields:

- `uploadedBy`: optional compatibility field, ignored for trusted attribution
- `files[n].type`: `USERS`, `READINGS`, or `PRICES`
- `files[n].file`: CSV or XLSX upload

Expected filenames:

- `customer_data.csv` or `customer_data.xlsx`
- `tariff_plans.csv` or `tariff_plans.xlsx`
- `usage_data.csv` or `usage_data.xlsx`

Only the first XLSX worksheet is read. CSV and XLSX rows go through the same validation and transactional import logic.

## Public DTO Notes

- Page responses use `content`, `totalElements`, `number`, `size`, `totalPages`, `first`, and `last`.
- Reading responses use `dateTime`, `product`, `lastReading`, `selfReported`, and `invoiced`.
- Invoice lines use `product`, `price`, `lineStart`, `lineEnd`, and `priceList`.
- Rejected self reports are returned as `DENIED`; the database may still store internal `DECLINED`.
- Backend PDF generation is not implemented; the frontend generates PDFs locally.
