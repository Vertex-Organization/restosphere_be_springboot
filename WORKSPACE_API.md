# Database workspace API

All paths below are under `/api/v1`. IDs are database UUID strings, not frontend fixture IDs. Dates are ISO instants unless explicitly specified. Money is whole VND; fractional quantities (up to three decimal places) are accepted only for `kg` menu items. Commands never accept replacement workspace state.

## Authentication and responses

`GET /workspaces/{admin|manager|kitchen|cashier|staff}` returns the corresponding frontend snapshot. Staff excludes the authenticated `session`; clients retain authentication separately. Snapshots use database records, including empty collections when no records exist.

`POST /workspaces/{workspace}/commands` accepts the frontend command object directly, with `type` at the top level.

- Admin and manager return the updated snapshot directly.
- Kitchen and cashier return `{data, summary, ref?}`.
- Staff returns `{data, state, summary, ref?, reservationId?}`; `data` and `state` are the same snapshot, and `reservationId` is supplied for create.
- Unknown commands and invalid values: 400; missing records: 404; workflow/version conflicts: 409; wrong/disabled current database role: 403; missing JWT: 401.
- Admin can access every workspace. Otherwise each named workspace requires its matching role; staff permits receptionists and waiters, with command-level separation.

## Commands

Manager implements the web `ManagerCommand` union: `reservation.move`, `table.update`, `employee.assign`, `shift.save`, `menu.save`, `menu.delete`, `category.save`, `category.delete`, `approval.decide`, `incident.update`, `notification.read`. For a new shift omit `shift.id` or supply an empty string; server allocates IDs. Menu/category creates use `creating:true` and ignore client IDs.

Kitchen implements `start`, `complete`, `reject`, `report-incident`, `set-menu-status`, `request-menu-status`, `read-notifications`. Start/complete receive `items:[{id,version}]`; reject receives `itemId,version,reason`.

Cashier implements `apply-discount`, `remove-discount`, `request-discount`, `request-adjustment`, `request-void`, `withdraw-request`, `return-payment-request`, `pay-cash`, `create-transfer`, `confirm-transfer`, `cancel-transfer`, `open-shift`, `close-shift`, `read-notifications`, with fields matching web `CashierCommand`. Payment creation requires `billVersion` and `idempotencyKey`; the same payment payload/key returns the existing payment. Cash tender/change is recorded. Transfer confirmation is manual and requires a unique bank reference. Open a shift before collecting payment. Direct discounts are limited to 10%; larger discounts require manager approval. Payment does not release the table.

Reception implements `create`, `edit`, `request-exception`, `confirm`, `reject`, `cancel`, `no-show`, `assign`, `check-in`, `handover`, `read`, matching mobile `ReceptionCommand`.

Waiter implements `accept`, `add-draft`, `edit-draft`, `remove-draft`, `submit`, `serve`, `request-cancel`, `request-priority`, `request-payment`, `withdraw-payment-request`, `guest-left`, `clean`, `read-notification`, matching mobile `WaiterCommand`. Drafts are persisted per waiter. Accept imports reservation preorders into drafts; submit prices from the current menu. Ownership is enforced. Departure requires payment; cleaning requires departure.

Admin commands:

```json
{"type":"user.role","id":"UUID","role":"WAITER"}
{"type":"user.status","id":"UUID","status":"LOCKED"}
{"type":"restaurant.update","restaurant":{"name":"...","description":"...","address":"...","hotline":"...","email":"...","openingHours":"...","facebook":"...","instagram":"...","tiktok":"...","logoUrl":"...","coverUrl":"..."}}
{"type":"settings.update","settings":{"onlineReservations":true,"tableHoldMinutes":15,"noShowMinutes":30,"bufferMinutes":15,"timezone":"Asia/Ho_Chi_Minh","currency":"VND","emailNotifications":false,"billing":{"serviceChargePercent":0,"vatPercent":0,"bankAccount":"...","bankName":"...","bankAccountName":"..."}}}
```

Self access changes and removing the last enabled administrator are rejected. These commands manage existing accounts; account creation/password reset is not implemented here.

## Public reservations

- `GET /reservations/settings`: online flag, hold/buffer/no-show minutes, timezone, database `areas:[{id,name}]`.
- `POST /reservations`: web `ReservationRequest`, **requiring real `areaId`**. Use `startsAt` or local `date` + `time`; optional `durationMinutes` defaults to 120. Returns 201 and web Reservation shape. Preorders use `items:[{dishId,quantity}]`.
- `POST /reservations/lookup`: `{code,phone}`; both must match. Returns Reservation or 404.
- `POST /reservations/cancel`: `{code,phone,reason}`; both must match, only PENDING/CONFIRMED may be cancelled. Returns Reservation (CANCELLED), 404 or 409.

Bookings start PENDING. Confirmation requires a table assignment; overlapping assignments include the configured buffer. Creation checks area capacity minus guests of active bookings overlapping the same time window (sum per area, not per-table fit), but does not guarantee a table until assignment. Lookup requires the random booking code and phone.

## Concurrency and accounting

Workspace reads/writes and reservation operations acquire a PostgreSQL transaction advisory lock; this serializes operations across application instances. Kitchen items and bills additionally expose JPA optimistic versions. Transactions roll back on invalid commands. Existing table resource writes reject active sessions/reservations. Line totals round HALF_UP to whole VND, discount precedes service charge, and VAT applies to discounted subtotal plus service charge. Successful payments count toward outstanding balance; pending transfers block another payment. Voiding a payment is restricted to an open cashier shift before guest departure.

## Known limits / integration follow-up

- No bank callback, card acquiring, email delivery, push/WebSocket delivery, or automated no-show scheduler. Notifications are persisted and available through polling. Email notification settings are stored only.
- Optional `scheduledShiftId` on cashier shift opening is persisted and checked against the cashier assignment and shift time window. Manager approval limits are not configurable; direct cashier discount limit is 10%.
- Public `area` legacy alias is returned as `main`; clients must use the authoritative `areaId` and settings areas. Booking table holds are configured but not automatically expired.
- Snapshots load complete datasets and perform repeated database queries. The global lock favors correctness for a single restaurant over throughput; pagination and narrower locks are needed at higher volume.
- Kitchen/manager incident fields and notification links use the available entity relationships; there is no separate delivery/outbox processor. Historical line names/prices are preserved, but report categories follow current menu category.
- Existing catalog/auth resource endpoints retain their own authorization policies. Workspace account lock/role changes are rechecked immediately on workspace commands; old JWTs on legacy endpoints remain subject to their existing token policy.
- Existing installations: after Hibernate schema update, apply `src/main/resources/migrations/workspace-legacy-constraints.sql` with `psql --single-transaction -v ON_ERROR_STOP=1 -f ...`. Hibernate does not widen legacy enum CHECK constraints. This repeatable migration validates existing rows and preserves data; unsupported legacy values abort the entire transaction. Tests use isolated PostgreSQL Testcontainers, not the demo database.

## Verification

`./mvnw.cmd test` runs existing auth/catalog/config/context checks plus `WorkspaceIntegrationTests`: full reservation-to-cleaning workflow, role/ownership denials, stale kitchen versions, idempotent payment replay/payload mismatch, lookup privacy, public cancellation, time-window capacity, reservation overlap, money/quantity validation, and HTTP authentication boundaries. No parallel contention/load test has been added.
