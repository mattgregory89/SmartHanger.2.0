# SmartHangar Final Demo Guide

## What this build demonstrates

SmartHangar is an aircraft-centered maintenance workspace with a Java/Spring Boot API, React/Vite client, SQLite persistence, signed maintenance ledger, human-in-the-loop AI drafting, offline aircraft-local maintenance capture, reconnect reconciliation, search, inspections, time-change tracking, servicing, modifications, turnover, history, and planning analytics.

## Start the project

### Terminal 1 — API

```bash
cd server
mvn clean test
mvn spring-boot:run
```

Spring Boot listens on `3001`.

### Terminal 2 — React

```bash
cd client
npm install
npm run dev
```

Open the Vite URL shown in the terminal (normally port `5173`). Vite proxies `/api` to Spring Boot on port `3001`.

## Demo login

- Username: `maintainer`
- Password: `demo123`

A supervisor demo account also exists:

- Username: `supervisor`
- Password: `demo123`

These credentials are intentionally demo-only and should not be treated as production authentication.

## Live AI configuration

The app works without an API key using a deterministic fallback provider. To enable live OpenAI-backed drafts, export an API key before starting Spring Boot:

```bash
export OPENAI_API_KEY="your-key-here"
mvn spring-boot:run
```

Optional model override:

```bash
export OPENAI_MODEL="gpt-5.6-luna"
```

Do not commit API keys to GitHub.

The AI feature is deliberately human-in-the-loop. It can draft a summary, suggested documentation action, maintenance categories/codes, shop routing, risk classification, and verification reminders. It cannot approve return to service, invent technical-order limits, or replace approved maintenance data.

## Recommended end-to-end demo

### 1. Fleet dashboard

Show aircraft status, open discrepancies, inspections, and the fictional offline-demo aircraft `DEMO-017`.

### 2. Aircraft maintenance

Open an aircraft and demonstrate:

- discrepancy creation
- Red X status behavior
- servicing
- inspections and time-change items
- maintenance timeline
- shift turnover

### 3. AI-assisted maintenance draft

Open an existing discrepancy and select **AI Draft**.

When `OPENAI_API_KEY` is configured, the response comes from the live AI provider. Without a key, SmartHangar uses the deterministic demo provider so the workflow remains demonstrable.

Show:

- summary
- suggested action draft
- suggested codes
- suggested shop
- risk classification
- human verification checklist
- Accept / Reject / Bypass AI

Accepting a draft adds the maintenance action only after the maintainer explicitly approves it. The AI decision is also written to the audit trail/ledger.

### 4. Offline aircraft integration

Select **Offline Demo** in the header.

The fictional `DEMO-017` data package represents information cached on a computer attached to the aircraft before connectivity is lost.

1. Click **Simulate Network Loss**.
2. Create a discrepancy locally.
3. Add a maintenance action to the locally created discrepancy.
4. Optionally close it locally.
5. Record servicing while disconnected.
6. Observe the aircraft-local queue and temporary local IDs.
7. Click **Reconnect & Sync**.

During reconciliation SmartHangar:

- sends queued events as one ordered batch
- assigns central database IDs
- maps temporary local discrepancy IDs to central IDs
- resolves dependent actions/closures against the mapped discrepancy
- stores duplicate-safe receipts using event UUIDs
- updates the central database
- adds applicable maintenance events to the signed ledger
- returns a reconciliation receipt to the aircraft device

Refresh the aircraft record afterward to show that the offline work now exists centrally.

### 5. Ledger validation

API endpoint:

```text
GET /api/ledger/validate
```

The ledger stores chained hashes plus digital signatures and can detect modified historical blocks.

### 6. Analytics

Select **Analytics** in the header to show:

- shop workload
- AI decision audit counts
- offline sync activity
- maintenance planning alerts
- ledger status

Planning alerts are summaries of recorded data and are not airworthiness determinations.

### 7. Voice entry

On the Discrepancies tab, use **Dictate discrepancy** in a browser supporting Web Speech Recognition. If unsupported, ordinary typed entry remains available.

## Important project boundary

This is a capstone/demo maintenance-information system. It is not approved technical data, an official aircraft maintenance system of record, or an autonomous airworthiness decision system. All real maintenance requires qualified personnel and applicable approved technical guidance.
