# LinkPulse

URL shortener with click analytics. Spring Boot, PostgreSQL, Flyway, JUnit, Mockito, Testcontainers.
Planned: Redis, Kafka, Nginx, read replica.

## What it does
Turns a long URL into a short code. Opening the short link redirects (HTTP 302)
to the original. Supports custom aliases and expiry dates.

## Status
| Phase | State |
|---|---|
| Core API (create, redirect, alias, expiry, error handling) | Done |
| Tests (unit + integration) | Done |
| JWT auth | Planned |
| Redis cache + rate limiting | Planned |
| Kafka analytics service | Planned |
| Nginx load balancing + Postgres read replica | Planned |

## API
| Method | Path | Description | Responses |
|---|---|---|---|
| POST | /v1/links | Create a short link (`longUrl`, optional `alias`, `expiresAt`) | 201, 400, 409 |
| GET | /{code} | Redirect to the long URL | 302, 404, 410 |

All errors use one JSON format: `timestamp, status, error, message, path`.

## Run locally
1. Start Postgres:
   `docker run --name linkpulse-shortener-db -e POSTGRES_USER=linkpulse -e POSTGRES_PASSWORD=linkpulse -e POSTGRES_DB=shortener -p 5433:5432 -d postgres:16`
2. `cd shortener-service`
3. `mvnw.cmd spring-boot:run` (Windows) or `./mvnw spring-boot:run`
4. Import `docs/postman/LinkPulse.postman_collection.json` into Postman.

## Tests
`mvnw.cmd clean test` (needs Docker running; integration tests start their own Postgres via Testcontainers).

## Design decisions
- **302, not 301:** browsers cache a 301 and stop calling our server, which would hide clicks from analytics and make links impossible to change or disable.
- **Short code = Base62 of the DB id:** unique by construction, no collision retries.
- **Flyway migrations:** schema is versioned in SQL; Hibernate only validates it.
- **Layered packages** (controller, service, repository, entity, dto, exception, util).
- **Unchecked custom exceptions + one @RestControllerAdvice:** keeps method signatures clean and gives a consistent error format.
- **410 for expired links:** distinguishes "gone" from "never existed" (404).

## Kafka partitioning
- The `click.events` topic has **3 partitions**; the message key is the **short code**, so all clicks for one link go to the same partition and stay in order.
- Consumers share the consumer group **`analytics`**: Kafka splits the partitions among all running analytics-service instances, so each message is handled by exactly one instance.
- When an instance stops or starts, Kafka **rebalances** and reassigns its partitions to the remaining instances.
- The **maximum useful number of consumers is 3** (the partition count); a 4th instance would sit idle.
- Because a code always maps to one partition, only **one consumer at a time writes a given link's daily row**, which keeps per-link counting simple.
- Delivery is at-least-once, so the consumer is idempotent: a repeated `eventId` is ignored (`ON CONFLICT DO NOTHING`) and counted in the `linkpulse.analytics.duplicates` metric.

## Known limitations (so far)
- Single instance, single database.
- No authentication yet (placeholder user id).