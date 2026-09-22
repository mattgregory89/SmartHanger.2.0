# SmartHangar – Java Conversion

This version keeps the original SmartHangar React/Vite UI and replaces the Node/Express backend with Java + Spring Boot.

## Technology

- Frontend: React + Vite
- Backend: Java 21 + Spring Boot 3.3.4
- Database access: Spring JDBC / JdbcTemplate
- Database: SQLite
- Build: Maven

## Why this conversion was structured this way

The React UI already calls a simple REST API. Rather than rewrite working UI code, the Java backend implements the same `/api/...` routes and returns the same JSON field names as the Node version. This lets SmartHangar move to Java without redesigning the application at the same time.

## Run the Java API

From `server/`:

```bash
mvn spring-boot:run
```

The API runs at:

```text
http://localhost:3001
```

## Run the React client

In a second terminal, from `client/`:

```bash
npm install
npm run dev
```

Vite normally runs at:

```text
http://localhost:5173
```

The existing Vite proxy already sends `/api` requests to port 3001.

## Demo login

- Maintainer: `maintainer` / `demo123`
- Supervisor: `supervisor` / `demo123`

## Java structure

```text
server/
├── pom.xml
├── smarthangar.db
└── src/main/
    ├── java/com/smarthangar/
    │   ├── SmartHangarApplication.java
    │   ├── config/WebConfig.java
    │   ├── controller/SmartHangarController.java
    │   └── service/SmartHangarService.java
    └── resources/
        ├── application.properties
        ├── schema.sql
        └── data.sql
```

## API functionality preserved

- Login
- Dashboard
- Fleet/aircraft retrieval
- Aircraft detail
- Aircraft status updates
- Create discrepancy
- Add maintenance action
- Close discrepancy
- Discrepancy action history
- Add servicing
- Maintenance timeline
- Shift turnover
- Global search
- Automatic NMC/FMC status logic for Red X discrepancies

## Next SmartHangar iteration

The UI has intentionally not been redesigned yet. This gives us a stable Java baseline before adding the fictional next-generation features such as:

- Voice discrepancy capture
- Voice corrective-action capture
- Structured AI-assisted draft fields
- Human review / electronic approval
- Offline local queue + sync state
- Aircraft health/fault-data ingestion
- Pilot-report + aircraft-data correlation

## September 2026 Sprint Expansion

SmartHangar now includes a larger second-stage feature set intended to support the passion-project roadmap.

### Expanded backend API

Aircraft now supports create, read, update, delete, and status changes. Discrepancies support create, read, update, close, action history, and audited deletion. The API also includes maintenance-domain routes for inspections, time-change items, modifications, users, servicing, search, turnover, timeline, ledger validation, AI assistance, and offline synchronization receipts.

### Persistent signed maintenance ledger

Maintenance events are written to a SHA-256 hash-linked ledger. Non-genesis blocks are digitally signed using RSA. Ledger blocks are persisted to SQLite in `ledger_blocks` and reconstructed when the application starts. The API exposes both the chain and an integrity-validation endpoint.

Important design choice: the ledger is append-oriented. A maintenance record can be superseded/audited without silently rewriting prior ledger history.

### Human-in-the-loop AI assistance

`POST /api/discrepancies/{id}/ai-suggestion` creates a draft corrective-action suggestion and basic code suggestions. The response explicitly requires human approval. The maintainer can accept, edit, reject, or bypass AI; that decision is recorded in `ai_audit`.

This is currently a deterministic mock AI service so the workflow can be demonstrated without an external AI account or internet connection. It is deliberately not authorized to approve maintenance or return an aircraft to service.

### Offline-first foundation

Mutating client requests such as discrepancy creation, discrepancy closure, servicing, inspections, time-change changes, modifications, and aircraft status changes can be queued in browser local storage when the device is offline. The header displays online/offline state and pending-sync count. On reconnection, queued mutations can be replayed and the server records a duplicate-safe sync receipt using a client-generated event ID.

This is a foundation for the aircraft-mounted/offline SmartHangar concept. A production version would replace browser local storage with encrypted local persistence and stronger conflict-resolution rules.

### New/expanded routes

```text
GET    /api/ledger
GET    /api/ledger/validate

POST   /api/aircraft
GET    /api/aircraft
GET    /api/aircraft/{id}
PUT    /api/aircraft/{id}
DELETE /api/aircraft/{id}
PATCH  /api/aircraft/{id}/status

POST   /api/aircraft/{id}/discrepancies
GET    /api/discrepancies/{id}
PUT    /api/discrepancies/{id}
DELETE /api/discrepancies/{id}
PATCH  /api/discrepancies/{id}/close
POST   /api/discrepancies/{id}/actions
GET    /api/discrepancies/{id}/actions

GET    /api/aircraft/{id}/inspections
POST   /api/aircraft/{id}/inspections
PATCH  /api/inspections/{id}/complete

GET    /api/aircraft/{id}/time-changes
POST   /api/aircraft/{id}/time-changes

GET    /api/aircraft/{id}/modifications
POST   /api/aircraft/{id}/modifications
PATCH  /api/modifications/{id}

GET    /api/users
POST   /api/users

POST   /api/discrepancies/{id}/ai-suggestion
PATCH  /api/ai-suggestions/{id}/decision

POST   /api/sync/events
GET    /api/sync/events
```

### Verification

Run the backend tests from `server/`:

```bash
mvn clean test
```

Run the backend:

```bash
mvn spring-boot:run
```

Then verify ledger integrity:

```bash
curl http://localhost:3001/api/ledger/validate
```

A healthy response should report `"valid": true`.

Run the frontend from `client/`:

```bash
npm install
npm run dev
```

For a clean production build:

```bash
npm run build
```

## Final integrated demo features

The final sprint adds:

- live OpenAI Responses API integration with a deterministic no-key fallback
- structured AI maintenance drafts with explicit maintainer accept/reject/bypass workflow
- AI decision audit records written into the maintenance ledger
- fictional aircraft-local dataset (`DEMO-017`) for disconnected operations
- offline discrepancy, maintenance-action, closure, and servicing capture
- ordered reconnect reconciliation with local-to-central ID mapping
- idempotent/duplicate-safe sync receipts
- offline device configuration and cached aircraft package
- maintenance analytics and planning indicators
- browser speech-to-text helper for discrepancy entry

See `FINAL_DEMO_GUIDE.md` for the complete demo sequence and setup instructions.
