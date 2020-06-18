# PickupReady

Portfolio reference implementation of a 2019 project, built to show a buy-online-pickup-in-store (BOPIS) workflow for store associates.

**Stack:** Spring Boot 3 (Java 21), MySQL 8 + Flyway, Redis (pub/sub, board cache, SMS outbox), React 18 + Vite.

## What it does
- **Order lifecycle** as an explicit state machine (`PickupStatus`):
  `PLACED → ACCEPTED → PICKING ⇄ AWAITING_SUBSTITUTION → READY → CUSTOMER_ARRIVED → PICKED_UP`, plus `EXPIRED` (no-show) and `CANCELLED`. Every transition is written to `pickup_events` as an audit trail.
- **Picking with substitutions:** associates record found quantities line by line. A short line can get a proposed substitute; the order waits on the customer's approve or decline before it can be staged.
- **Staging and hold window:** staging sets a shelf or cooler location and a 72-hour hold. A scheduled sweep expires no-shows and queues a refund message.
- **Curbside check-in:** the customer taps "I'm here" with a parking spot, which jumps the order to the top of the board.
- **Secure handoff:** a 6-digit pickup code is returned once when the order is placed and stored only as a salted SHA-256 hash. Codes are compared in constant time. Five wrong codes lock the order until a manager checks ID.
- **Live board:** every change publishes to `store:<id>:orders` in Redis. Each API node subscribes and fans updates out to tablets over SSE, so the board stays in sync across instances.
- **Optimistic locking** (`@Version`) stops two associates from staging the same order at once.

## Layout
```
backend/   Spring Boot API (domain, service, web, notify)
frontend/  React associate board (columns by status, pick sheet, handoff)
```

## Run
```bash
docker compose up -d mysql redis
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev   # http://localhost:5173/?store=S042
```

## Test
```bash
cd backend && mvn test      # H2 (MySQL mode), fake clock + recording notifier
cd frontend && npm test     # board grouping / urgency / live-merge logic
```
