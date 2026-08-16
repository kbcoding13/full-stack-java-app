# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Project

**Inventory & Stock Management System** — a full-stack CRUD application.

- **Backend:** Spring Boot 4.1 (Java 21), Spring Data JPA, PostgreSQL, Flyway
- **Frontend:** React 19 + TypeScript on Vite, TanStack Query, React Router, Tailwind v4
- **Storage:** AWS S3 (product images, CSV import/export, document attachments)
- **Auth:** Spring Security with JWT, roles `ADMIN` / `STAFF`

The repo is a monorepo:

```
/backend      Spring Boot app (Gradle — chosen at scaffold time, stay with it)
/frontend     Vite React app
/infra        docker-compose (Postgres + LocalStack), IaC if added later
/.github      CI workflows
```

## Domain model

Five aggregates. Keep this shape — it is the core design decision of the project.

| Entity | Notes |
| --- | --- |
| `Category` | name, description. Products belong to one category. |
| `Supplier` | name, contact email/phone, address. Products reference a supplier. |
| `Product` | SKU (unique), name, description, unit price, `reorderLevel`, category, supplier, image keys |
| `StockMovement` | **append-only ledger**: product, type (`IN`/`OUT`/`ADJUST`), quantity, reason, reference, occurredAt, createdBy |
| `User` | email, password hash, role |

**On-hand quantity is derived, never stored as a mutable column.** It is the sum over a product's
stock movements (`IN` positive, `OUT` negative, `ADJUST` signed). Read paths use a SQL projection or
a maintained `product_stock` summary table updated in the same transaction as the movement insert —
never a field that an ordinary product `PUT` can overwrite. If a task asks to "just set the quantity,"
push back: the correct action is to record an `ADJUST` movement.

A product is **low stock** when derived quantity `<= reorderLevel`.

Deletes on `Product`, `Category`, `Supplier` are **soft deletes** (`deletedAt`), because stock
movements must keep referencing history. `StockMovement` rows are never updated or deleted.

## API conventions

Base path `/api/v1`. REST, JSON, plural nouns.

```
POST   /auth/register            public (first user becomes ADMIN)
POST   /auth/login               -> { accessToken, refreshToken }
POST   /auth/refresh

GET    /products                 ?page&size&sort&search&categoryId&supplierId&lowStock=true
POST   /products                 ADMIN
GET    /products/{id}
PUT    /products/{id}            ADMIN
DELETE /products/{id}            ADMIN (soft)

GET    /categories, /suppliers   + full CRUD, same role rules

GET    /products/{id}/movements  paged ledger
POST   /stock-movements          STAFF or ADMIN — the only way stock changes

GET    /products/{id}/images     presigned GET URLs
POST   /products/{id}/images/presign  -> { uploadUrl, key } for direct browser PUT
POST   /products/{id}/images/confirm  registers the key after upload succeeds
DELETE /products/{id}/images/{imageId}

POST   /imports/products         multipart CSV -> proxied to S3, then parsed
GET    /exports/inventory        generates CSV to S3, returns presigned GET
POST   /attachments              multipart doc tied to a movement/product
```

Rules:
- All list endpoints are paged (`Page<T>`), never unbounded.
- Requests and responses use **DTOs**; JPA entities never cross the controller boundary.
- Validate with `jakarta.validation` annotations on request DTOs.
- Errors go through a single `@RestControllerAdvice` returning RFC 7807 `ProblemDetail`.
  Use `404` for missing, `409` for constraint conflicts (duplicate SKU), `422` for domain rule
  violations (e.g. an `OUT` movement that would drive stock negative).
- Every mutation is transactional and audited (`createdBy`, `createdAt`, `updatedAt` via JPA auditing).

## S3 strategy

Two upload paths, deliberately:

1. **Product images → presigned URLs.** Backend issues a presigned PUT; the browser uploads straight
   to S3; the client then calls `/confirm` so the key is persisted. Keeps image bytes off the API.
   Requires CORS on the bucket for the frontend origin.
2. **CSV imports and document attachments → proxy upload.** Multipart to Spring Boot, which validates
   content type and size, then streams to S3 with the AWS SDK v2 `S3Client`. These need server-side
   inspection before they are trusted, so they do not get presigned PUTs.

Downloads are always presigned GETs with a short TTL (15 min default). **The bucket is private — never
make objects public and never store raw S3 URLs in the database.** Store the object key only; build
URLs at read time.

Key layout:

```
products/{productId}/images/{uuid}.{ext}
imports/{yyyy}/{MM}/{uuid}.csv
exports/inventory-{timestamp}.csv
attachments/{entityType}/{entityId}/{uuid}-{originalName}
```

Local dev uses **LocalStack**; `S3Client` is built from a `@ConfigurationProperties` bean so the
endpoint override is config, not code. Never hardcode a region, bucket, or endpoint.

## Security

- JWT access token (15 min) + refresh token (7 days). Secret from env, never committed.
- Passwords hashed with BCrypt.
- Method-level `@PreAuthorize` on service methods, plus route rules in `SecurityFilterChain`.
  Read endpoints: any authenticated user. Writes to catalog data: `ADMIN`. Stock movements: `STAFF`+.
- CORS is configured for the frontend origin only, from config.
- No secrets in `application.yml` — use env vars / `application-local.yml` which is gitignored.

## Commands

```bash
# infra
docker compose -f infra/docker-compose.yml up -d      # Postgres + LocalStack

# backend (from /backend)
./gradlew bootRun
./gradlew test                    # integration tests need a Docker daemon
./gradlew build

# frontend (from /frontend)
npm run dev
npm run build
npm run test
npm run lint
```

Backend runs on `:8080`, frontend on `:5173` with a Vite proxy to `/api`.

## Testing

- **Backend:** JUnit 5. Integration tests use **Testcontainers** for Postgres and LocalStack — no
  H2, no mocked `S3Client` in integration tests. `@SpringBootTest` + `MockMvc` for controller slices.
  Every endpoint gets at least a happy path and an auth-denied test. Stock math gets unit tests for
  the ledger sum, including negative-stock rejection.
- **Frontend:** Vitest + React Testing Library. Test user-visible behavior, not implementation.
  MSW for API mocking. Data-fetching hooks are tested through the components that use them.
- **CI:** GitHub Actions on every PR — backend tests, frontend tests, lint, build. Green CI is the
  bar for "done."

## Conventions

**Backend**
- Package by feature, not by layer: `com.example.inventory.product`, `.stock`, `.supplier`, `.storage`, `.auth`.
- Layering inside a feature: `Controller → Service → Repository`. Controllers hold no business logic.
- Constructor injection only. No field `@Autowired`.
- Use records for DTOs. Prefer immutability.
- Flyway migrations are **append-only** — never edit an applied migration; add a new one.
  Naming: `V{n}__snake_case_description.sql`.

**Frontend**
- All server state through TanStack Query. No manual `useEffect` fetching, no server data in Redux/context.
- API client in `src/api/` with typed request/response models mirroring the backend DTOs.
- Feature folders under `src/features/{products,stock,suppliers,categories,auth}`.
- Forms use React Hook Form + Zod, and the Zod schema is the single source of validation truth on the client.
- Tailwind utilities in JSX; extract a component before extracting a CSS class.

**Both**
- Small, focused commits. Conventional commit messages (`feat:`, `fix:`, `chore:`).
- Do not add a dependency without a clear reason; prefer what's already here.

## Working agreements for Claude

- Read neighboring code before writing; match its style over these general rules if they conflict.
- When adding an endpoint, do the full slice: migration (if needed) → entity → repository → service →
  DTO → controller → test → frontend API client → UI. A half-slice is not done.
- Run the relevant tests before reporting completion, and paste real output — never claim green
  without having run it.
- If a change would let stock quantity be mutated outside the movement ledger, stop and flag it.
- Ask before: adding a new AWS service, changing the auth model, or introducing a new state-management
  library.

## Pinned versions and platform gotchas

Scaffolded from start.spring.io on 2026-08-16. **Spring Boot 4.1.0 / Java 21 / Gradle 9.5.1.**
Boot 4 renamed or moved several things away from what Boot 3 muscle memory expects — these are
verified against the resolved dependency tree, not guessed:

| Boot 3 habit | What Boot 4.1 actually uses |
| --- | --- |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| `spring-boot-starter-test` | per-starter `-test` artifacts (`…-webmvc-test`, `…-data-jpa-test`, …) |
| Jackson 2 `com.fasterxml.jackson.databind` | **Jackson 3** `tools.jackson.databind` |
| Jackson bundled with the web starter | needs an explicit `spring-boot-starter-json` |
| `HttpStatus.UNPROCESSABLE_ENTITY` | `HttpStatus.UNPROCESSABLE_CONTENT` (422 renamed in RFC 9110) |
| `org.springframework.lang.NonNull` | deprecated; omit it or use JSpecify |
| Testcontainers `org.testcontainers:postgresql` | `org.testcontainers:testcontainers-postgresql` (2.x) |
| `PostgreSQLContainer<>` self-typed generic | Testcontainers 2.x containers are **not** generic |
| `DefaultCredentialsProvider.create()` | deprecated; use `.builder().build()` |

Frontend is on **Vite 8 / TypeScript 6 / Tailwind v4 / Zod 4 / React Router 7 / Vitest 4**.
TS 6 deprecates `baseUrl` — declare `paths` alone, relative to the tsconfig.
Tailwind v4 has no `tailwind.config.js`: theme tokens live in `@theme` inside `src/index.css`.

Before changing a version here, re-check the resolved tree rather than assuming.

## Open items

- Deployment target not chosen yet (candidates: ECS/Fargate + RDS + CloudFront-hosted frontend).
- Multi-warehouse locations and purchase orders are explicitly **out of scope** for v1.