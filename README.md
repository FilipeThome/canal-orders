# Canals Orders API

Backend service for the Canals e-commerce assessment. Implements order creation (`POST /orders`) and listing (`GET /orders`) with single-warehouse fulfilment, distance-based warehouse selection, mocked geocoding and payment integrations, idempotent request handling, and end-to-end transactional safety.

Built with **Kotlin 1.9 + JDK 21 + Spring Boot 3.2 + PostgreSQL 15 + Flyway**.

---

## Table of contents

1. [Architecture overview](#architecture-overview)
2. [Domain model](#domain-model)
3. [Order creation pipeline](#order-creation-pipeline)
4. [Concurrency & race-condition handling](#concurrency--race-condition-handling)
5. [Idempotency](#idempotency)
6. [REST API reference](#rest-api-reference)
7. [JSON API contract](#json-api-contract)
8. [Running the service](#running-the-service)
   - [Option A — Everything in Docker](#option-a--everything-in-docker)
   - [Option B — Postgres in Docker, app in IntelliJ](#option-b--postgres-in-docker-app-in-intellij)
9. [Using the API (Postman / Insomnia)](#using-the-api-postman--insomnia)
10. [Manual smoke test with curl](#manual-smoke-test-with-curl)
11. [Project layout](#project-layout)
12. [Production-readiness notes](#production-readiness-notes)

---

## Architecture overview

The service follows a strict layered architecture. Each layer only talks to the layer directly below it; services never reach across domain boundaries into another domain's repository.

```
HTTP Requests
        |
        v
+-----------------------------------------------+
| Controllers  (controller/)                     |
|  OrderController    GET+POST+PUT+DELETE /orders|
|  CustomerController full CRUD /customers       |
|  ProductController  full CRUD /products        |
|  WarehouseController full CRUD /warehouses     |
+-----------------------------------------------+
        | calls
        v
+-----------------------------------------------+
| Services  (service/)                           |
|  IdempotencyService          — key claim + cache lookup    |
|  IdempotencyOrderCreatorService — transactional order create |
|  OrderService        — order transaction       |
|  CustomerService     — customer lookup         |
|  ProductService      — product lookup          |
|  WarehouseStockService — lock → sealed StockReservation + decrement |
|  WarehouseSelectionService — Haversine ranking |
|  WarehouseService    — warehouse listing       |
+-----------------------------------------------+
        | calls (own domain repo only)
        v
+-----------------------------------------------+
| Repositories  (repository/)                    |
|  OrderRepository / CustomerRepository          |
|  ProductRepository / WarehouseRepository       |
|  WarehouseStockRepository / IdempotencyKey...  |
+-----------------------------------------------+
        |
        v
+-----------------------------------------------+
| PostgreSQL 15 + Flyway migrations              |
+-----------------------------------------------+

External integrations (external/):
  GeocodingService  — address → (lat, lng)  [mocked]
  PaymentService    — charge card            [mocked, card ending 0002 declines]

Response DTOs (dto/response/):
  OrderResponse / CustomerResponse / ProductResponse / WarehouseResponse
  toResponse() extension functions — mapping lives alongside the DTOs, not in controllers
```

The service is production-shaped: real database, real migrations, explicit JSON schema, strict JSON deserialisation, RFC 7807 error envelope, structured logging with request-id correlation, graceful shutdown, multi-stage Docker image, idempotency key claiming before payment, and pessimistic locking against race conditions on stock.

---

## Code quality highlights

The codebase follows **SOLID + Clean Code** principles with idiomatic Kotlin throughout.

| Area | What was done |
|------|--------------|
| **Rich domain** | `Order` owns `markPaid()`, `markFailed()`, `transitionTo()`. `WarehouseStock` owns `decrement(qty)`. State mutations no longer scattered across the service layer. |
| **Sealed result types** | `WarehouseStockService.tryLock()` returns `sealed StockReservation { Acquired | Unavailable }` — exhaustive `when` at every call site, no nullable `Map?`. |
| **O(1) warehouse query** | `WarehouseSelectionService` replaced N+1 per-product queries with a single native SQL batch query using Postgres `UNNEST` + `GROUP BY / HAVING COUNT = P`. |
| **Zero DTO duplication** | `UpdateCustomerRequest`, `UpdateProductRequest`, `UpdateWarehouseRequest` are `typealias` for their Create counterparts — identical contracts, one source of truth. |
| **Decomposed service** | `OrderService.create()` split from 128 lines into `validateNoDuplicateProducts()`, `fulfillWithFirstAvailableWarehouse()`, `buildOrder()`, `chargePayment()` — each with single responsibility. |
| **Exception handler DSL** | `GlobalExceptionHandler` uses a private `problem(status, detail, type, configure)` with trailing lambda — all handlers reduced to single-line expressions. |
| **Idiomatic Kotlin** | `let {}`, `apply {}`, `buildMap {}`, `sumOf {}`, `kotlin.random.Random`, `orElseThrow {}`, `associateBy {}` throughout; no unnecessary `var`, no `Math.random()`. |
| **Query ownership** | `Warehouse.@NamedQuery` moved to `WarehouseRepository.@Query` — entities own data, repositories own queries. |

---

## Domain model

| Table              | Purpose                                                                                                                           |
| ------------------ | --------------------------------------------------------------------------------------------------------------------------------- |
| `customers`        | Reference data — used to validate the customer on order creation.                                                                 |
| `products`         | Catalogue — SKU, name, current unit price.                                                                                        |
| `warehouses`       | Reference data — code, name, **lat/lng** of the warehouse, address.                                                               |
| `warehouse_stock`  | On-hand quantity per `(warehouse, product)`. Composite PK. Source of truth for fulfilment decisions.                              |
| `orders`           | Aggregate root. Contains a snapshot of the shipping address (denormalised on purpose — orders are immutable historical records).  |
| `order_items`      | Line items. `unit_price` is snapshotted at order time so future catalogue price changes don't retro-affect past orders.           |
| `idempotency_keys` | Stripe-style cache: `key` PK + SHA-256 of the canonical request + cached response payload. Serialises racing identical requests.  |

The status of an order is a Postgres native ENUM (`order_status`) with values `PENDING_PAYMENT`, `PAID`, `PAYMENT_FAILED`, `CANCELLED`. Money is stored as `NUMERIC(12,2)` — never floats. Geo coordinates are stored as `NUMERIC(9,6)` / `NUMERIC(10,6)` (precision sufficient for ~10 cm). PostGIS would be ideal in production but adds image weight that isn't justified for this scope; the Haversine distance is computed in Kotlin inside `WarehouseSelectionService`.

---

## Order creation pipeline

`OrderService.create` runs inside a single `@Transactional` boundary:

1. **Validate** — customer exists, products exist, no duplicate product IDs in the request.
2. **Geocode** — convert the shipping address to (lat, lng) via `GeocodingService` (mocked: deterministic hash, so the same address always yields the same coordinates).
3. **Find candidate warehouses** — `WarehouseSelectionService`:
   - Runs a **single native SQL batch query** (`findWarehousesWithSufficientStock`) using Postgres `UNNEST` to expand the product-id / min-quantity pairs into an inline relation, joined to `warehouse_stock`, and grouped by warehouse. A `HAVING COUNT(DISTINCT product_id) = P` predicate ensures every warehouse in the result carries **all** P requested products at the required quantities — in one round-trip regardless of how many products are in the order.
   - Sorts the result by Haversine distance ascending in application code.
4. **Lock and verify stock** — `WarehouseStockService.tryLock()` issues `SELECT ... FOR UPDATE` ordered by `product_id` (deterministic lock order prevents deadlocks), then re-checks quantities under the lock. Returns a `sealed StockReservation` (`Acquired` or `Unavailable`). Between step 3 and now another transaction may have shipped the last unit — if `Unavailable`, fall back to the next candidate.
5. **Decrement stock** — `WarehouseStockService.decrement(reservation, productToQty)` delegates to `WarehouseStock.decrement(qty)` on each locked domain entity, keeping the mutation inside the aggregate.
6. **Persist the order** in `PENDING_PAYMENT`, snapshot prices into `order_items`.
7. **Charge the card** via `PaymentService` (mocked):
   - On approval → mark `PAID`, store `payment_id`, `card_last4`, `paid_at`. Commit.
   - On decline → throw `PaymentFailedException`, which rolls back the entire transaction (order row, stock decrements, everything). The compensation is automatic.

We intentionally **reserve stock before charging** (rather than charging first then decrementing). A successful charge with no remaining stock is the worse failure mode — inventory contention is far more common than payment-gateway failures, and rolling back a non-charged Tx is free.

---

## Concurrency & race-condition handling

Two real concerns were addressed:

**1. Two clients buying the last unit at the same time.**
Solved with `PESSIMISTIC_WRITE` on `warehouse_stock` rows for the chosen warehouse, ordered by `product_id`. The deterministic lock order eliminates the classic A→B / B→A deadlock pattern. The lock is held for the duration of the transaction, including the payment call — this is acceptable because the payment mock is fast; in production you'd switch to a "reserve → release after timeout" pattern (saga) if the gateway is slow.

**2. Two retries with the same `Idempotency-Key` arriving simultaneously.**
`IdempotencyService` inserts and flushes the `idempotency_keys` row before creating the order. The unique PK on `idempotency_keys.key` serialises racing requests before stock is decremented or payment is charged; a loser of the race rolls back and re-reads the winner's cached response. See [Idempotency](#idempotency).

---

## Idempotency

The `Idempotency-Key` header (max 120 chars, opaque string) is **required** on every `POST /orders` request. Behaviour mirrors Stripe and AWS:

| Scenario                                         | Result                                                           |
| ------------------------------------------------ | ---------------------------------------------------------------- |
| First call with a key                            | Claim the key, process normally, cache the response under that key. |
| Repeat call, same key, **same body**             | Return the cached response. No new order, no new charge.         |
| Repeat call, same key, **different body**        | HTTP 422 `idempotency-conflict`.                                 |
| Missing header                                   | HTTP 400 `missing-header`.                                       |

The "same body" check is order-insensitive: we canonicalise the request (sort items by `productId`, normalise the address line, normalise card numbers to digits only, strip ordering of JSON fields) and SHA-256 the canonical form. This means a UI that re-orders items in its array between retries is still treated as identical.

---

## REST API reference

All endpoints return JSON. Error responses follow [RFC 7807](https://datatracker.ietf.org/doc/html/rfc7807).

### Customers

| Method | Path | Status | Notes |
| ------ | ---- | ------ | ----- |
| `GET` | `/customers` | 200 | List all customers. |
| `GET` | `/customers/{id}` | 200 / 404 | Single customer by UUID. |
| `POST` | `/customers` | 201 | Create customer. Body: `email`, `fullName`. |
| `PUT` | `/customers/{id}` | 200 / 404 | Replace all fields. Body: `email`, `fullName`. |
| `DELETE` | `/customers/{id}` | 204 / 404 | Delete customer. |

### Products

| Method | Path | Status | Notes |
| ------ | ---- | ------ | ----- |
| `GET` | `/products` | 200 | List all products. |
| `GET` | `/products/{id}` | 200 / 404 | Single product by UUID. |
| `POST` | `/products` | 201 | Create product. Body: `sku`, `name`, `unitPrice`. |
| `PUT` | `/products/{id}` | 200 / 404 | Replace all fields. Body: `sku`, `name`, `unitPrice`. |
| `DELETE` | `/products/{id}` | 204 / 404 | Delete product. |

### Warehouses

| Method | Path | Status | Notes |
| ------ | ---- | ------ | ----- |
| `GET` | `/warehouses` | 200 | List all warehouses. |
| `GET` | `/warehouses/{id}` | 200 / 404 | Single warehouse by UUID. |
| `POST` | `/warehouses` | 201 | Create warehouse. Body: `code`, `name`, `latitude`, `longitude`, `address`. |
| `PUT` | `/warehouses/{id}` | 200 / 404 | Replace all fields. Same body shape as POST. |
| `DELETE` | `/warehouses/{id}` | 204 / 404 | Delete warehouse. |

### Orders

| Method | Path | Status | Notes |
| ------ | ---- | ------ | ----- |
| `GET` | `/orders` | 200 | List all orders. |
| `GET` | `/orders/{id}` | 200 / 404 | Single order by UUID. |
| `POST` | `/orders` | 201 | Create order. Requires `Idempotency-Key` header. See [JSON API contract](#json-api-contract). |
| `PUT` | `/orders/{id}` | 200 / 404 | Update order status. Body: `{ "status": "PENDING_PAYMENT" \| "PAID" \| "PAYMENT_FAILED" \| "CANCELLED" }`. |
| `DELETE` | `/orders/{id}` | 204 / 404 | Delete order. |

---

## JSON API contract

The canonical JSON contract is documented in [`docs/orders-api.schema.json`](./docs/orders-api.schema.json). Runtime validation intentionally matches that contract:

- Unknown JSON properties are rejected (`spring.jackson.deserialization.fail-on-unknown-properties=true`).
- Malformed JSON, invalid UUIDs, missing non-null fields, or type mismatches return HTTP 400 with `type=https://canals.example/errors/invalid-json`.
- Bean Validation failures return HTTP 400 with `type=https://canals.example/errors/validation-error` and an `errors` map.
- `payment.cardNumber` accepts 13-19 digits, with optional spaces or dashes, and is normalised to digits before idempotency hashing and payment.

Create order request shape:

```json
{
  "customerId": "00000000-0000-0000-0000-000000000000",
  "shippingAddress": {
    "addressLine": "123 Market St, San Francisco, CA 94103"
  },
  "items": [
    {
      "productId": "00000000-0000-0000-0000-000000000000",
      "quantity": 2
    }
  ],
  "payment": {
    "cardNumber": "4111 1111 1111 1111"
  }
}
```

Successful order responses use the same `shippingAddress` object shape:

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "customerId": "00000000-0000-0000-0000-000000000000",
  "warehouseId": "00000000-0000-0000-0000-000000000000",
  "status": "PAID",
  "shippingAddress": {
    "addressLine": "123 Market St, San Francisco, CA 94103"
  },
  "items": [
    {
      "productId": "00000000-0000-0000-0000-000000000000",
      "quantity": 2,
      "unitPrice": 29.99
    }
  ],
  "totalAmount": 59.98,
  "currency": "USD",
  "cardLast4": "1111",
  "paymentId": "pay_...",
  "createdAt": "2026-05-05T12:00:00Z",
  "paidAt": "2026-05-05T12:00:00Z",
  "failureReason": null
}
```

---

## Running the service

### Prerequisites

- **Docker / Docker Compose** for both options.
- For Option B only: **JDK 21** and IntelliJ (or `./gradlew bootRun`).

### Option A — Everything in Docker

This requires only Docker. No JDK, no Kotlin, no Gradle on your host.

```bash
# From the project root
docker compose up -d --build

# Tail the app logs
docker compose logs -f app
```

What this brings up:

- `canals-db` — Postgres 15 Alpine on `localhost:5432`, credentials `canals/canals`, database `canals`. Volume `canals_pgdata` persists data across restarts.
- `canals-app` — the Spring Boot app on `localhost:8080`. Built from the multi-stage `Dockerfile` (final image ~110 MB, runs as non-root user).

The app waits for Postgres to be healthy (`pg_isready`) before booting, so the first `up` may take ~30 seconds for the DB to initialise and Flyway to run.

Verify it's up:

```bash
curl http://localhost:8080/actuator/health
# {"status":"UP"}
```

To shut down:

```bash
docker compose down              # stops containers, keeps the data volume
docker compose down -v           # also wipes the data volume (full reset)
```

### Option B — Postgres in Docker, app in IntelliJ

Use this when you want hot-reload, debugger, etc. We bring up only Postgres in a container and run the JVM on the host.

```bash
# From the project root — start ONLY the database
docker compose -f docker-compose.db.yml up -d
```

Now open the project in IntelliJ:

1. **File → Open** → select the project root.
2. Wait for the Gradle import to finish.
3. Open `src/main/kotlin/com/canals/orders/Application.kt`.
4. Click the green ▶ next to `fun main(...)` → **Run 'Application'**.

That's it — `application.yml` defaults already point at `localhost:5432` with username/password `canals/canals`, which is exactly what `docker-compose.db.yml` provisions.

**Alternatively** without IntelliJ:

```bash
./gradlew bootRun
```

To stop just the database:

```bash
docker compose -f docker-compose.db.yml down
```

---

## Using the API (Postman / Insomnia)

A ready-to-use collection lives in [`postman/`](./postman/):

- `canals-orders.postman_collection.json` — the requests (Postman v2.1 format, also imports cleanly into Insomnia).
- `canals-local.postman_environment.json` — variables for a local environment.
- `../docs/orders-api.schema.json` — explicit JSON Schema for the order request/response contract.

### Importing into Postman

1. **File → Import** → drop both files.
2. In the top-right environment selector, choose **Canals - Local**.
3. (One-time) populate the IDs: see [Bootstrapping the IDs](#bootstrapping-the-ids) below.
4. Hit any request in the collection.

### Importing into Insomnia

1. **Application menu → Import** → **From File** → pick `canals-orders.postman_collection.json`. Insomnia handles the Postman v2.1 schema natively.
2. The collection variables (`baseUrl`, `customerId`, `firstProductId`, `secondProductId`, `warehouseId`, `orderId`) are imported too. Edit them under the request group's "Manage Environments" panel.
3. Hit any request.

### Bootstrapping the IDs

The collection uses `{{customerId}}`, `{{firstProductId}}`, `{{secondProductId}}`, `{{warehouseId}}`, and `{{orderId}}` placeholders. Fill them with UUIDs from the seed data using the reference endpoints:

```bash
# List all customers — find Alice's id
curl http://localhost:8080/customers

# List all products — find SKU-1001 (Mouse) and SKU-1002 (Keyboard) ids
curl http://localhost:8080/products

# List all warehouses (useful for understanding fulfilment results)
curl http://localhost:8080/warehouses
```

Copy the `id` values into the Postman/Insomnia environment variables (`customerId`, `firstProductId`, `secondProductId`, `warehouseId`). Set `orderId` after creating your first order.

### What's in the collection

The collection is organised into resource folders plus a top-level health check.

**Health check**

| Request | What it demonstrates |
| ------- | -------------------- |
| `Health check` | Actuator probe — confirms DB connection and Flyway state. |

**Customers / Products / Warehouses** (each folder contains the same set)

| Request | What it demonstrates |
| ------- | -------------------- |
| `List <resource>` | `GET /<resource>` — returns all records. Use to copy UUIDs into environment variables. |
| `Get <resource> by id` | `GET /<resource>/{id}` — single record, 404 on missing. |
| `Create <resource>` | `POST /<resource>` → 201 with the created resource. |
| `Update <resource>` | `PUT /<resource>/{id}` — full replacement, 404 on missing. |
| `Delete <resource>` | `DELETE /<resource>/{id}` → 204, 404 on missing. |

**Orders**

| Request | What it demonstrates |
| ------- | -------------------- |
| `List orders` | `GET /orders` — inspect all persisted orders. |
| `Get order by id` | `GET /orders/{id}` — single order, 404 on missing. |
| `Create order — happy path` | The standard 201 flow. |
| `Create order — idempotency replay` | Send it twice with the same key; second call returns cached body. |
| `Create order — idempotency conflict` | Same key, different body → 422. |
| `Create order — payment declined` | Card ending in `0002` triggers a forced decline → 402. |
| `Create order — no eligible warehouse` | Quantity beyond any single warehouse's stock → 422. |
| `Create order — validation error` | Empty/invalid fields → 400 with per-field error map. |
| `Create order — invalid JSON` | Malformed JSON → 400 `invalid-json`. |
| `Create order — unknown field` | Extra JSON properties → 400 `invalid-json`. |
| `Create order — missing Idempotency-Key` | No header → 400 `missing-header`. |
| `Update order status` | `PUT /orders/{id}` — change status to `CANCELLED` etc. |
| `Delete order` | `DELETE /orders/{id}` → 204. |

---

## Manual smoke test with curl

```bash
# Look up real IDs from the reference endpoints (requires jq)
CUSTOMER_ID=$(curl -s http://localhost:8080/customers | jq -r '.[] | select(.email=="alice@example.com") | .id')
PRODUCT_ID=$(curl -s http://localhost:8080/products | jq -r '.[] | select(.sku=="SKU-1001") | .id')

curl -i -X POST http://localhost:8080/orders \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: smoke-$(date +%s)" \
  -d "{
    \"customerId\": \"$CUSTOMER_ID\",
    \"shippingAddress\": { \"addressLine\": \"123 Market St, San Francisco, CA 94103\" },
    \"items\": [{ \"productId\": \"$PRODUCT_ID\", \"quantity\": 2 }],
    \"payment\": { \"cardNumber\": \"4111 1111 1111 1111\" }
  }"
```

Expected response: HTTP 201 with the order JSON, including `warehouseId`, `shippingAddress.addressLine`, `cardLast4: "1111"`, `status: "PAID"`.

---

## Project layout

```
canals-orders/
├── build.gradle.kts                 — Gradle build (Kotlin DSL)
├── settings.gradle.kts
├── Dockerfile                       — Multi-stage build (builder + JRE Alpine)
├── docker-compose.yml               — Full stack (db + app)
├── docker-compose.db.yml            — Postgres only (for IntelliJ runs)
├── .dockerignore  /  .gitignore
├── README.md
├── docs/
│   └── orders-api.schema.json          — JSON Schema for POST /orders
├── postman/
│   ├── canals-orders.postman_collection.json
│   └── canals-local.postman_environment.json
└── src/
    └── main/
        ├── kotlin/com/canals/orders/
        │   ├── Application.kt
        │   ├── config/
        │   ├── controller/
        │   │   ├── OrderController.kt          — GET /orders, POST /orders
        │   │   ├── CustomerController.kt       — GET /customers
        │   │   ├── ProductController.kt        — GET /products
        │   │   └── WarehouseController.kt      — GET /warehouses
        │   ├── domain/                         — JPA entities
        │   │   ├── Customer.kt
        │   │   ├── Product.kt
        │   │   ├── Warehouse.kt                — location entity (query moved to WarehouseRepository)
        │   │   ├── WarehouseStock.kt           — decrement(qty) domain method
        │   │   ├── Order.kt                    — aggregate root; markPaid(), markFailed(), transitionTo()
        │   │   ├── OrderItem.kt
        │   │   ├── OrderStatus.kt
        │   │   └── IdempotencyKey.kt
        │   ├── dto/
        │   │   ├── CreateOrderRequest.kt       — Bean Validation + card normalisation
        │   │   └── response/                  — Response DTOs + toResponse() extensions
        │   │       ├── OrderResponse.kt        — includes ShippingAddressResponse, OrderItemResponse
        │   │       ├── CustomerResponse.kt
        │   │       ├── ProductResponse.kt
        │   │       └── WarehouseResponse.kt
        │   ├── exception/
        │   │   ├── DomainException.kt
        │   │   └── GlobalExceptionHandler.kt   — RFC 7807 + invalid JSON handling
        │   ├── external/                       — Mocked third parties
        │   │   ├── GeocodingService.kt
        │   │   └── PaymentService.kt
        │   ├── repository/
        │   │   ├── CustomerRepository.kt
        │   │   ├── ProductRepository.kt
        │   │   ├── WarehouseRepository.kt      — single-batch stock eligibility query (UNNEST + GROUP BY HAVING)
        │   │   ├── WarehouseStockRepository.kt — PESSIMISTIC_WRITE lock query
        │   │   ├── OrderRepository.kt
        │   │   └── IdempotencyKeyRepository.kt
        │   └── service/
        │       ├── CustomerService.kt          — Customer listing + lookup by id
        │       ├── ProductService.kt           — Product listing + bulk lookup by ids
        │       ├── WarehouseService.kt         — Warehouse listing
        │       ├── WarehouseStockService.kt    — PESSIMISTIC_WRITE lock → sealed StockReservation; decrement delegates to domain
        │       ├── WarehouseSelectionService.kt — O(1) batch query + Haversine sort
        │       ├── OrderService.kt             — Order listing + pipeline (validate → geocode → select → lock → persist → charge)
        │       ├── IdempotencyService.kt            — Key claim + cache lookup (required header)
        │       └── IdempotencyOrderCreatorService.kt — @Transactional order creation boundary
        └── resources/
            ├── application.yml                 — DB config + strict unknown-field JSON handling
            └── db/migration/
                ├── V1__init_schema.sql         — Schema with constraints/indexes
                └── V2__seed_data.sql           — Seed data (customers, products, warehouses, stock)
```

---

## Production-readiness notes

What's there:

- **Real DB, no in-memory shortcuts.** Schema owned by Flyway; JPA in `validate` mode.
- **Money as `NUMERIC`**, never `Float`/`Double`.
- **Idempotency** with a stable canonical request hash; safe under concurrent retries.
- **Pessimistic locking** for stock decrements with deterministic order to prevent deadlocks.
- **No PAN storage.** Only the last 4 digits and the gateway's `payment_id`.
- **Graceful shutdown**, **HikariCP tuning**, **non-root container user**, multi-stage Docker for ~110 MB final image.
- **Structured logs**, RFC 7807 error envelopes.
- **Postgres native enum** for `order_status` — type-safe at the DB level.

What was deliberately scoped out (and would be the obvious next steps):

- **PostGIS** for spatial indexing — nice-to-have but the warehouse table will not realistically reach a size where the Haversine computation in `WarehouseSelectionService` becomes a bottleneck.
- **Outbox pattern** for the `Payment` call — production systems decouple "order persisted" from "card charged" via an outbox table + worker, so a payment-gateway timeout can't leave inconsistent state. The current implementation is acceptable because we charge inside the same Tx and roll back on decline; a network timeout AFTER the gateway charged would still leave a problematic gap. Mitigation: idempotent retries from the client + payment-side reconciliation job.
- **Authentication / authorization** — explicitly excluded by the spec.
- **Contract / load / mutation testing** — unit and integration tests are in place with JaCoCo coverage enforcement (≥ 80% line, ≥ 70% branch); property-based and contract tests would be the obvious next layer.
- **Inventory release on payment failure across separate transactions** — the current "rollback the whole Tx" approach works for the synchronous gateway but a fully async saga would track stock reservations as a first-class entity with a TTL.
- **Rate limiting / circuit breaker** on the payment client (Resilience4j) — would be wired in if the gateway were real.
