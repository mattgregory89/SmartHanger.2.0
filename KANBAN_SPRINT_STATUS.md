# SmartHangar Kanban Sprint Status

This file maps the current codebase to the large GitHub Kanban backlog created in September 2026. Use it to move cards without guessing.

## Move to Done

These capabilities exist in the current project after this sprint:

### Core / backend
- Finish Aircraft data model (SQLite schema and API representation)
- Finish Discrepancy data model
- Create MaintenanceAction data model
- Create User data model
- Create Inspection data model
- Create TimeChangeItem data model
- Create ServicingRecord data model
- Create Modification data model
- Define entity relationships
- Create GET aircraft endpoint
- Create GET aircraft by ID endpoint
- Create POST aircraft endpoint
- Create PUT aircraft endpoint
- Create DELETE aircraft endpoint
- Create GET discrepancy by ID endpoint
- Create POST discrepancy endpoint
- Create PUT discrepancy endpoint
- Create DELETE discrepancy endpoint
- Create maintenance corrective action endpoint
- Connect discrepancies to aircraft
- Connect maintenance actions to discrepancies
- Add validation to discrepancy creation
- Add aircraft status refresh logic
- Add discrepancy numbering logic

### Ledger / blockchain-style audit trail
- Connect new discrepancies to maintenance ledger
- Create ledger block for maintenance actions
- Add block timestamp
- Add previous block hash
- Add current block hash
- Add aircraft/discrepancy data to ledger payloads
- Add maintainer identifier to block
- Add maintenance action data to block
- Add ledger validation method
- Create ledger validation endpoint
- Detect modified ledger records (integrity validation)
- Prevent silent ledger record deletion (append/audit model)
- Add ledger persistence
- Reload ledger after application restart
- Evaluate database-backed ledger storage
- Document why blockchain-style ledger is used

### Frontend already present
- Create React frontend
- Configure Vite project
- Connect frontend to Spring Boot API
- Create login page
- Create navigation bar
- Create aircraft dashboard
- Create aircraft overview page
- Create discrepancies page
- Create new discrepancy form
- Create maintenance history page
- Create inspections page
- Create time changes page
- Create engines page
- Create servicing page
- Create modifications page
- Create shift turnover page
- Add aircraft status badges
- Add discrepancy status badges
- Add form validation (HTML required fields + backend validation)

### Offline capability
- Create offline data model (queued mutation envelope)
- Allow discrepancy creation without internet
- Allow corrective/close action entry without internet
- Queue offline changes
- Detect restored internet connection
- Sync queued records automatically/on demand
- Prevent duplicate sync receipt records
- Add offline queued timestamp
- Show offline mode indicator
- Show pending sync count
- Add manual sync button
- Add server-side sync history/audit table

### AI workflow
- Create AI service workflow interface at API boundary
- Create corrective action suggestion endpoint
- Send discrepancy text to AI workflow
- Return suggested corrective action
- Return suggested maintenance codes
- Allow maintainer to accept AI suggestion
- Allow maintainer to reject AI suggestion
- Allow maintainer to bypass AI completely
- Log whether AI suggestion was accepted/rejected/bypassed
- Prevent AI from automatically approving maintenance
- Add AI disclaimer to maintenance workflow
- Add mock AI response for development
- Test AI endpoint without external API (architecture supports no external dependency)

### Documentation
- Update README
- Write project description
- Document project goals / next-generation direction
- Document tech stack
- Document system architecture at a high level
- Add data model diagram (existing PlantUML ERD)
- Add API endpoint documentation
- Add setup instructions
- Add run instructions
- Add database instructions
- Document offline-first architecture
- Document blockchain ledger design
- Document AI workflow
- Document AI bypass workflow

## Move to Testing

These are implemented but should be exercised in Codespaces before you mark them Done:

- Test health endpoint
- Test login endpoint
- Test ledger endpoint
- Test discrepancy creation
- Test discrepancy numbering
- Test aircraft status update
- Test missing aircraft ID
- Test invalid discrepancy data
- Test ledger block creation
- Test ledger chain validation
- Test modified ledger detection
- Test offline sync service
- Test duplicate sync prevention
- Test AI bypass workflow
- Run Maven test suite

A JUnit ledger test suite now exists at:

`server/src/test/java/com/smarthangar/ledger/MaintenanceLedgerV3Test.java`

## Keep In Progress / To Do

These are not complete enough to claim Done yet:

- Separate repository classes for each domain (current design uses one JdbcTemplate-backed service)
- Separate service classes for every domain
- Password hashing / production authentication tokens
- Full role-based authorization
- Dedicated discrepancy details page
- Rich corrective-action editor
- AI edit-before-accept UI
- Encrypted offline storage
- Advanced sync conflict resolution
- IndexedDB migration (current queue uses localStorage)
- Speech-to-text discrepancy reporting
- Speech-to-text corrective action entry
- Voice command support
- Voice transcript storage
- Black-box voice ingestion research/prototype
- Predictive maintenance
- Recurring discrepancy analytics
- Notifications / alerts
- Swagger/OpenAPI
- Docker / production database / deployment / CI-CD
- Final screenshots / demo script / presentation deliverables

## Sprint estimate

The board originally mixed already-existing functionality, architecture tasks, future research, and implementation work. After reconciling the cards against the actual codebase and this sprint, a large majority of the functional MVP cards are either Done or ready for Testing. Do not mark the remaining security, speech, predictive-maintenance, deployment, or production-offline cards Done until they are actually implemented.

## Final integration sprint additions

The final build also implements or materially advances these backlog items:

- AI service interface
- corrective action suggestion endpoint
- send discrepancy text/context to AI service
- suggested corrective action
- suggested maintenance codes
- suggested maintenance shop / risk classification
- maintainer accept / reject / bypass workflow
- AI decision audit logging
- prevent AI from automatically approving maintenance
- AI disclaimer / human technical-data verification boundary
- live provider configuration with demo fallback
- offline aircraft workspace
- cached local aircraft dataset
- offline discrepancy creation
- offline corrective action entry
- offline close-event capture
- offline servicing capture
- reconnect sync service
- temporary local ID to central ID mapping
- duplicate-safe sync receipts
- sync status / reconciliation receipt
- manual reconnect-and-sync demo
- aircraft-local device configuration
- maintenance analytics dashboard
- fleet planning indicators
- speech-to-text discrepancy helper (browser support dependent)

See `FINAL_DEMO_GUIDE.md` for the tested demo path to use when presenting the project.
