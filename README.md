# Mini Billing

Full-stack project for generating JSON invoices from CSV input files.

## Structure

- `backend/` - Java 21 Spring Boot backend.
- `frontend/` - React application built with Vite.
- `data/input/` - CSV input files.
- `data/output/` - generated JSON invoice files.

## Backend

Configuration is in `backend/src/main/resources/application.yml`:

```yaml
billing:
  input-directory: ../data/input
  output-directory: ../data/output
```

Run the backend:

```bash
cd backend
mvn spring-boot:run
```

Run backend tests:

```bash
cd backend
mvn test
```

Main endpoints:

```text
GET http://localhost:8080/api/billing/health
POST http://localhost:8080/api/invoices/generate
GET http://localhost:8080/api/invoices
GET http://localhost:8080/api/invoices/{documentNumber}
GET http://localhost:8080/api/invoices/{documentNumber}/download
```

Generate invoices example:

```bash
curl -X POST http://localhost:8080/api/invoices/generate \
  -H "Content-Type: application/json" \
  -d "{\"year\":2024,\"month\":3}"
```

## Frontend

Install dependencies:

```bash
cd frontend
npm install
```

Create local environment config if the backend is not on the default URL:

```bash
cp .env.example .env
```

Default frontend API base URL:

```text
VITE_API_BASE_URL=http://localhost:8080
```

Run the development server:

```bash
npm run dev
```

Build the frontend:

```bash
npm run build
```

## Notes

The project intentionally does not include database configuration. CSV files are the input source, and JSON files under `data/output/` are the invoice repository.
