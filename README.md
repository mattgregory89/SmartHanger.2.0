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
