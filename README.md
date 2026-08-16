# Inventory & Stock Management System

Full-stack CRUD application: Spring Boot API + React SPA + S3 object storage.

- **Backend** — Spring Boot 4.1 (Java 21), Spring Data JPA, PostgreSQL, Flyway, Spring Security + JWT
- **Frontend** — React 19 + TypeScript on Vite, TanStack Query, React Router, Tailwind v4
- **Storage** — AWS S3 (LocalStack for local dev)

## The one design rule

**On-hand quantity is never stored as an editable column.** It is derived from an append-only
`stock_movements` ledger (`IN` / `OUT` / `ADJUST`) and materialised into `product_stock` by a
database trigger inside the same transaction. A `CHECK` constraint there makes negative stock
impossible, and further triggers reject any `UPDATE` or `DELETE` on the ledger.

To change stock, `POST /api/v1/stock-movements`. There is deliberately no other path — a product
`PUT` cannot touch quantity. To correct a mistake, record a compensating `ADJUST` movement.

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 21 | |
| Node | 20+ | |
| Docker | any recent | Postgres + LocalStack, **and** required by the backend integration tests |

## Getting started

```bash
# 1. Infrastructure (Postgres on :5432, LocalStack S3 on :4566)
docker compose -f infra/docker-compose.yml up -d

# 2. Backend on :8080
cd backend && ./gradlew bootRun

# 3. Frontend on :5173
cd frontend && npm install && npm run dev
```

Open http://localhost:5173. **The first account you register becomes `ADMIN`**; everyone after
that is `STAFF`.

### Demo data

To start with a populated catalog instead of an empty one:

```bash
cd backend && ./gradlew bootRun --args='--spring.profiles.active=seed'
```

That creates seven products across three categories and two suppliers, signs in with
`admin@example.com` / `staff@example.com` (password `password123`), and leaves one product below
its reorder level so the low-stock filter has something to show. Opening balances are written as
stock movements, not as a quantity column. It is idempotent per SKU and gated behind the `seed`
profile, so it never runs in a real environment.

The LocalStack container creates the `inventory-local` bucket and its CORS rules on startup via
`infra/localstack/init/01-create-bucket.sh` — presigned browser uploads need that CORS policy.

### If a port is already taken

A PostgreSQL server or another app already running locally will quietly shadow the container,
and the failure looks like bad credentials rather than a port clash:

```bash
POSTGRES_PORT=5433 docker compose -f infra/docker-compose.yml up -d
cd backend && ./gradlew bootRun --args='--spring.datasource.url=jdbc:postgresql://localhost:5433/inventory'

# and if 8080 is taken
cd backend && ./gradlew bootRun --args='--server.port=8081'
cd frontend && VITE_API_TARGET=http://localhost:8081 npm run dev
```

Note that `./gradlew bootRun` forks from the Gradle daemon, which does not inherit environment
variables set in your shell — pass overrides via `--args` rather than exporting them.

### Running without Docker

If you already have PostgreSQL locally, point the backend at it and skip the Postgres container:

```bash
createdb inventory
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/inventory   SPRING_DATASOURCE_USERNAME=you SPRING_DATASOURCE_PASSWORD=secret ./gradlew bootRun
```

S3 features (image upload, CSV import/export) still need LocalStack or real AWS credentials.

## Commands

```bash
# backend (from /backend)
./gradlew bootRun
./gradlew bootRun --args='--spring.profiles.active=seed'   # with demo data
./gradlew test          # unit tests + Testcontainers integration tests (needs Docker)
./gradlew build         # compile + test

# frontend (from /frontend)
npm run dev
npm run build           # type check + production build
npm run test
npm run lint
```

## Configuration

Everything environment-specific is bound through `AppProperties` (`app.*` in
`application.yml`) and overridable by environment variable:

| Variable | Default | Purpose |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | local Postgres | Datasource |
| `JWT_SECRET` | dev-only value | Base64 HMAC key — **must** be overridden outside local dev |
| `S3_BUCKET` | `inventory-local` | Bucket name |
| `S3_ENDPOINT` | `http://localhost:4566` | Blank to use real AWS |
| `AWS_REGION` | `us-east-1` | |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173` | Frontend origin |

Upload limits are config too: `app.storage.max-image-bytes` / `allowed-image-types` for product
images, and `max-attachment-bytes` / `allowed-attachment-types` for documents.

Secrets belong in environment variables or a gitignored `application-local.yml` — never in
`application.yml`.

## API

Base path `/api/v1`. All list endpoints are paged; all errors are RFC 7807 `ProblemDetail`.

```
POST   /auth/register              public — first user becomes ADMIN
POST   /auth/login                 -> { accessToken, refreshToken }
POST   /auth/refresh
GET    /auth/me

GET    /products                   ?page&size&sort&search&categoryId&supplierId&lowStock
POST   /products                   ADMIN
GET    /products/{id}
PUT    /products/{id}              ADMIN
DELETE /products/{id}              ADMIN (soft delete)

GET    /categories  /suppliers     + full CRUD, ADMIN for writes

POST   /stock-movements            STAFF or ADMIN — the only way stock changes
GET    /products/{id}/movements    paged ledger
GET    /products/{id}/stock        derived level

GET    /products/{id}/images       presigned GET URLs
POST   /products/{id}/images/presign   -> { uploadUrl, key } for direct browser PUT
POST   /products/{id}/images/confirm   registers the key after upload
DELETE /products/{id}/images/{imageId}

POST   /imports/products           ADMIN — multipart CSV, upsert by SKU
GET    /exports/inventory          generates CSV to S3, returns a presigned GET

POST   /attachments                STAFF+ — multipart doc tied to a product/supplier/movement
GET    /attachments                ?entityType&entityId — presigned GET URLs
DELETE /attachments/{id}           ADMIN
```

CSV import columns: `sku` and `name` are required; `description`, `category`, `supplier`,
`unitPrice` and `reorderLevel` are optional. Categories and suppliers are created on demand by
name. Rows are validated independently — a bad row is reported and skipped rather than failing
the file. **An import never sets stock**; opening balances must be recorded as movements.

Status codes: `404` missing, `409` conflict (duplicate SKU), `422` domain rule violation
(e.g. an `OUT` that would drive stock negative).

## S3 strategy

Two upload paths, on purpose:

1. **Product images → presigned PUT.** The browser uploads straight to S3, then calls `/confirm`
   so the key is persisted. Image bytes never pass through the API. Needs bucket CORS.
2. **CSV imports and attachments → proxy upload.** Multipart to Spring Boot, which validates
   content type and size before streaming to S3. These need server-side inspection first.

Downloads are always short-lived presigned GETs. **The bucket is private; only object keys are
stored in the database, never URLs.**

## Repository layout

```
backend/    Spring Boot app (Gradle)
frontend/   Vite React app
infra/      docker-compose + LocalStack bootstrap
.github/    CI workflow
```

See [CLAUDE.md](CLAUDE.md) for the conventions this codebase follows.
