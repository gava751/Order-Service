# Order Service (InnoBoard)

Spring Boot microservice managing orders, their line items and catalog items.

## Stack

- Java 25 (LTS)
- Spring Boot 4.1.1 (Web MVC, Data JPA, Validation, Actuator)
- PostgreSQL
- Liquibase (schema migrations)
- Lombok

## Schema (Task: DB Schema)

Three tables, all created exclusively by Liquibase
(`src/main/resources/db/changelog`), so Hibernate runs with
`ddl-auto: validate`:

| Table         | Columns                                                                  |
|---------------|--------------------------------------------------------------------------|
| `items`       | `id`, `name`, `price`, audit columns                                     |
| `orders`      | `id`, `user_id`, `status`, `total_price`, `deleted`, audit columns       |
| `order_items` | `id`, `order_id` FK, `item_id` FK, `quantity`, audit columns             |

Every entity table carries `created_at` / `updated_at` (`timestamptz`),
filled by JPA Auditing via the shared `Auditable` base class.

Indexes (each tied to a known access scenario):

| Index                     | Table         | Purpose                                     |
|---------------------------|---------------|---------------------------------------------|
| `idx_orders_user_id`      | `orders`      | all orders of a user                        |
| `idx_orders_status`       | `orders`      | orders filtered by status                   |
| `idx_order_items_order_id`| `order_items` | line items of an order                      |
| `idx_order_items_item_id` | `order_items` | FK support for item update/delete           |

`orders.user_id` is a logical reference to user-service data and has no
database-level FK (cross-service boundary). FKs exist only inside this
service: `order_items → orders`, `order_items → items`.

## Configuration

All database and server settings are provided via environment variables.
Local values live in an ignored `.env` (see `.env.example`):

| Variable      | Default (local)                                    | Description              |
|---------------|----------------------------------------------------|--------------------------|
| `DB_URL`      | `jdbc:postgresql://localhost:5432/order_service`   | JDBC URL                 |
| `DB_USERNAME` | — (no default, required)                           | DB user                  |
| `DB_PASSWORD` | — (no default, required)                           | DB password              |
| `SERVER_PORT` | `8083`                                             | HTTP port                |

## Running locally

```bash
docker run -d --name order-service-db \
  -e POSTGRES_DB=order_service -e POSTGRES_USER=order_service \
  -e POSTGRES_PASSWORD=order_service -p 5432:5432 postgres:18-alpine
```

```bash
export JAVA_HOME="C:/Users/gava751/.jdks/temurin-25.0.4.1"
./mvnw spring-boot:run
```

On startup Liquibase applies the changelogs; a restart applies nothing
again (the `databasechangelog` journal makes migrations idempotent).

Health probes for Kubernetes: `GET /actuator/health/liveness`,
`GET /actuator/health/readiness`.
