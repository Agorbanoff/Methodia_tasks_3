# Mini Billing

Full-stack project for importing CSV billing data into PostgreSQL and generating invoices.

## Structure

- `backend/` - Java 21 Spring Boot backend.
- `frontend/` - React application built with Vite.
- `data/input/` - source CSV input files.
- `data/output/` - generated JSON invoice files.
- `docker-compose.yml` - local PostgreSQL database.

## Local Run Flow

Start PostgreSQL first:

```bash
docker compose up -d
```

Start the backend on `http://localhost:6969`:

```bash
cd backend
mvn spring-boot:run
```

Start the frontend:

```bash
cd frontend
npm run dev
```

Before generating invoices, import the CSV files into PostgreSQL:

```bash
curl -X POST http://localhost:6969/api/import
```

You can also use the `Import CSV files` button in the frontend.

After the import succeeds, generate invoices:

```bash
curl -X POST http://localhost:6969/api/invoices/generate \
  -H "Content-Type: application/json" \
  -d "{\"year\":2024,\"month\":3}"
```

You can also use the `Generate invoices` button in the frontend.

Generate requires imported PostgreSQL data. If the CSV files have not been imported yet, the backend returns:

```text
No imported data found. Please import CSV files first.
```

The frontend shows that message in the Generate card.

## Backend

Configuration is in `backend/src/main/resources/application.yml`:

```yaml
server:
  port: 6969

billing:
  input-directory: ../data/input
  output-directory: ../data/output
```

Run backend tests:

```bash
cd backend
mvn test
```

Main endpoints:

```text
GET http://localhost:6969/api/billing/health
POST http://localhost:6969/api/import
POST http://localhost:6969/api/invoices/generate
GET http://localhost:6969/api/invoices
GET http://localhost:6969/api/invoices/{documentNumber}
GET http://localhost:6969/api/invoices/{documentNumber}/download
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
VITE_API_BASE_URL=http://localhost:6969
```

Build the frontend:

```bash
npm run build
```
