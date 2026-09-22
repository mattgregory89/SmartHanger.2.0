package com.smarthangar.service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class SmartHangarService {

    private final JdbcTemplate jdbc;
    private final LedgerService ledgerService;
    private final AiService aiService;
    private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SmartHangarService(
            JdbcTemplate jdbc,
            LedgerService ledgerService,
            AiService aiService) {

        this.jdbc = jdbc;
        this.ledgerService = ledgerService;
        this.aiService = aiService;
    }

    private String nowSql() {
        return LocalDateTime.now().format(SQL_TIME);
    }

    public Optional<Map<String, Object>> one(String sql, Object... args) {
        List<Map<String, Object>> rows = jdbc.queryForList(sql, args);
        return rows.stream().findFirst();
    }

    public List<Map<String, Object>> many(String sql, Object... args) {
        return jdbc.queryForList(sql, args);
    }

    private long insertAndReturnId(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null)
            throw new IllegalStateException("Insert did not return an id");
        return key.longValue();
    }

    public void refreshAircraftStatus(long aircraftId) {
        Integer redXCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM discrepancies WHERE aircraft_id=? AND status='OPEN' AND symbol='RED X'",
                Integer.class,
                aircraftId);

        if (redXCount != null && redXCount > 0) {
            jdbc.update("UPDATE aircraft SET status='NMC', last_updated=? WHERE id=?", nowSql(), aircraftId);
            return;
        }

        Optional<Map<String, Object>> aircraft = one("SELECT status FROM aircraft WHERE id=?", aircraftId);
        if (aircraft.isPresent() && "NMC".equals(aircraft.get().get("status"))) {
            jdbc.update("UPDATE aircraft SET status='FMC', last_updated=? WHERE id=?", nowSql(), aircraftId);
        }
    }

    public Optional<Map<String, Object>> login(String username, String password) {
        return one("SELECT id, username, full_name, role FROM users WHERE username=? AND password=?", username,
                password);
    }

    public Map<String, Object> dashboard() {
        List<Map<String, Object>> aircraft = many("SELECT * FROM aircraft ORDER BY tail_number");
        List<Map<String, Object>> openDiscrepancies = many("""
                SELECT d.*, a.tail_number
                FROM discrepancies d
                JOIN aircraft a ON a.id=d.aircraft_id
                WHERE d.status='OPEN'
                ORDER BY CASE d.symbol WHEN 'RED X' THEN 1 WHEN 'RED DASH' THEN 2 ELSE 3 END,
                         d.reported_date DESC
                """);
        List<Map<String, Object>> dueInspections = many(
                """
                        SELECT i.*, a.tail_number, a.total_hours,
                               CASE WHEN i.due_hours IS NULL THEN NULL ELSE ROUND(i.due_hours - a.total_hours, 1) END AS hours_remaining
                        FROM inspections i
                        JOIN aircraft a ON a.id=i.aircraft_id
                        WHERE i.status != 'COMPLETE'
                        ORDER BY COALESCE(i.due_hours - a.total_hours, 999999), i.due_date
                        """);

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("FMC", 0);
        counts.put("PMC", 0);
        counts.put("NMC", 0);
        for (Map<String, Object> row : aircraft) {
            String status = String.valueOf(row.get("status"));
            counts.put(status, counts.getOrDefault(status, 0) + 1);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("aircraft", aircraft);
        response.put("openDiscrepancies", openDiscrepancies);
        response.put("dueInspections", dueInspections);
        response.put("counts", counts);
        return response;
    }

    public List<Map<String, Object>> aircraft() {
        return many("SELECT * FROM aircraft ORDER BY tail_number");
    }

    public Optional<Map<String, Object>> aircraftDetail(long id) {
        Optional<Map<String, Object>> aircraftOpt = one("SELECT * FROM aircraft WHERE id=?", id);
        if (aircraftOpt.isEmpty())
            return Optional.empty();

        Map<String, Object> aircraft = aircraftOpt.get();
        double totalHours = ((Number) aircraft.get("total_hours")).doubleValue();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("aircraft", aircraft);
        response.put("discrepancies",
                many("SELECT * FROM discrepancies WHERE aircraft_id=? ORDER BY reported_date DESC", id));
        response.put("inspections", many("""
                SELECT *, CASE WHEN due_hours IS NULL THEN NULL ELSE ROUND(due_hours - ?, 1) END AS hours_remaining
                FROM inspections WHERE aircraft_id=? ORDER BY due_hours, due_date
                """, totalHours, id));
        response.put("timeChanges", many("""
                SELECT *, CASE WHEN due_hours IS NULL THEN NULL ELSE ROUND(due_hours - ?, 1) END AS hours_remaining
                FROM time_change_items WHERE aircraft_id=? ORDER BY due_hours, due_date
                """, totalHours, id));
        response.put("engines", many("SELECT * FROM engines WHERE aircraft_id=? ORDER BY position", id));
        response.put("servicing",
                many("SELECT * FROM servicing_records WHERE aircraft_id=? ORDER BY service_date DESC", id));
        response.put("modifications", many("SELECT * FROM modifications WHERE aircraft_id=? ORDER BY id DESC", id));
        return Optional.of(response);
    }

    public Map<String, Object> createAircraft(Map<String, Object> body) {
        long id = insertAndReturnId(
                """
                        INSERT INTO aircraft (tail_number, model, status, location, total_hours, total_cycles, assigned_crew_chief, last_updated)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                body.get("tailNumber"), body.get("model"),
                blankToDefault(string(body.get("status")), "FMC"), body.get("location"),
                body.getOrDefault("totalHours", 0), body.getOrDefault("totalCycles", 0),
                body.get("assignedCrewChief"), nowSql());
        return one("SELECT * FROM aircraft WHERE id=?", id).orElseThrow();
    }

    public Optional<Map<String, Object>> updateAircraft(long id, Map<String, Object> body) {
        Optional<Map<String, Object>> current = one("SELECT * FROM aircraft WHERE id=?", id);
        if (current.isEmpty())
            return Optional.empty();
        Map<String, Object> c = current.get();
        jdbc.update("""
                UPDATE aircraft SET tail_number=?, model=?, status=?, location=?, total_hours=?, total_cycles=?,
                    assigned_crew_chief=?, last_updated=? WHERE id=?
                """, valueOr(body, "tailNumber", c.get("tail_number")), valueOr(body, "model", c.get("model")),
                valueOr(body, "status", c.get("status")), valueOr(body, "location", c.get("location")),
                valueOr(body, "totalHours", c.get("total_hours")), valueOr(body, "totalCycles", c.get("total_cycles")),
                valueOr(body, "assignedCrewChief", c.get("assigned_crew_chief")), nowSql(), id);
        return one("SELECT * FROM aircraft WHERE id=?", id);
    }

    public boolean deleteAircraft(long id) {
        return jdbc.update("DELETE FROM aircraft WHERE id=?", id) > 0;
    }

    public Optional<Map<String, Object>> setAircraftStatus(long id, String status) {
        int changed = jdbc.update("UPDATE aircraft SET status=?, last_updated=? WHERE id=?", status, nowSql(), id);
        if (changed == 0)
            return Optional.empty();
        return one("SELECT * FROM aircraft WHERE id=?", id);
    }

    public Map<String, Object> addDiscrepancy(
            long aircraftId,
            String description,
            String symbol,
            String assignedShop,
            String reportedBy) {

        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM discrepancies WHERE aircraft_id=?",
                Integer.class,
                aircraftId);

        int next = aircraftId > 0
                ? (int) (aircraftId * 100 + (count == null ? 0 : count) + 1)
                : 1;

        String number = "MX-%03d".formatted(next);

        String maintainer = blankToDefault(
                reportedBy,
                "Demo Maintainer");

        long id = insertAndReturnId(
                """
                        INSERT INTO discrepancies
                        (aircraft_id, discrepancy_number, description, symbol, status, reported_by, reported_date, assigned_shop)
                        VALUES (?, ?, ?, ?, 'OPEN', ?, ?, ?)
                        """,
                aircraftId,
                number,
                description,
                symbol,
                maintainer,
                nowSql(),
                blankToDefault(assignedShop, "CREW CHIEF"));

        refreshAircraftStatus(aircraftId);

        Map<String, Object> savedDiscrepancy = one(
                "SELECT * FROM discrepancies WHERE id=?",
                id).orElseThrow();

        try {
            String jsonPayload = objectMapper.writeValueAsString(savedDiscrepancy);

            ledgerService.recordMaintenanceEvent(
                    jsonPayload,
                    maintainer);

        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to record discrepancy in maintenance ledger",
                    e);
        }

        return savedDiscrepancy;
    }

    public Optional<Map<String, Object>> discrepancy(long id) {
        return one("SELECT * FROM discrepancies WHERE id=?", id);
    }

    public Optional<Map<String, Object>> updateDiscrepancy(long id, Map<String, Object> body) {
        Optional<Map<String, Object>> current = one("SELECT * FROM discrepancies WHERE id=?", id);
        if (current.isEmpty())
            return Optional.empty();
        Map<String, Object> c = current.get();
        jdbc.update("""
                UPDATE discrepancies SET description=?, symbol=?, assigned_shop=?, status=? WHERE id=?
                """, valueOr(body, "description", c.get("description")), valueOr(body, "symbol", c.get("symbol")),
                valueOr(body, "assignedShop", c.get("assigned_shop")), valueOr(body, "status", c.get("status")), id);
        long aircraftId = ((Number) c.get("aircraft_id")).longValue();
        refreshAircraftStatus(aircraftId);
        Map<String, Object> saved = one("SELECT * FROM discrepancies WHERE id=?", id).orElseThrow();
        recordLedger("DISCREPANCY_UPDATED", saved, "Demo Maintainer");
        return Optional.of(saved);
    }

    public boolean deleteDiscrepancy(long id, String deletedBy) {
        Optional<Map<String, Object>> current = one("SELECT * FROM discrepancies WHERE id=?", id);
        if (current.isEmpty())
            return false;
        Map<String, Object> record = current.get();
        recordLedger("DISCREPANCY_DELETE_AUDIT", record, blankToDefault(deletedBy, "Demo Maintainer"));
        int changed = jdbc.update("DELETE FROM discrepancies WHERE id=?", id);
        if (changed > 0)
            refreshAircraftStatus(((Number) record.get("aircraft_id")).longValue());
        return changed > 0;
    }

    public Map<String, Object> addAction(long discrepancyId, String actionText, String performedBy) {
        String maintainer = blankToDefault(performedBy, "Demo Maintainer");
        long id = insertAndReturnId("""
                INSERT INTO maintenance_actions (discrepancy_id, action_text, performed_by, action_date)
                VALUES (?, ?, ?, ?)
                """, discrepancyId, actionText, maintainer, nowSql());
        Map<String, Object> saved = one("SELECT * FROM maintenance_actions WHERE id=?", id).orElseThrow();
        recordLedger("MAINTENANCE_ACTION", saved, maintainer);
        return saved;
    }

    public Optional<Map<String, Object>> closeDiscrepancy(long discrepancyId, String correctiveAction,
            String closedBy) {
        Optional<Map<String, Object>> discrepancy = one("SELECT * FROM discrepancies WHERE id=?", discrepancyId);
        if (discrepancy.isEmpty())
            return Optional.empty();

        String closer = blankToDefault(closedBy, "Demo Maintainer");
        if (correctiveAction != null && !correctiveAction.isBlank()) {
            jdbc.update("""
                    INSERT INTO maintenance_actions (discrepancy_id, action_text, performed_by, action_date)
                    VALUES (?, ?, ?, ?)
                    """, discrepancyId, correctiveAction, closer, nowSql());
        }

        jdbc.update("UPDATE discrepancies SET status='CLOSED', closed_by=?, closed_date=? WHERE id=?",
                closer, nowSql(), discrepancyId);
        long aircraftId = ((Number) discrepancy.get().get("aircraft_id")).longValue();
        refreshAircraftStatus(aircraftId);
        return one("SELECT * FROM discrepancies WHERE id=?", discrepancyId);
    }

    public List<Map<String, Object>> discrepancyActions(long id) {
        return many("SELECT * FROM maintenance_actions WHERE discrepancy_id=? ORDER BY action_date DESC", id);
    }

    public Map<String, Object> addServicing(long aircraftId, String type, double quantity, String unit,
            String servicedBy, String notes) {
        String maintainer = blankToDefault(servicedBy, "Demo Maintainer");
        long id = insertAndReturnId("""
                INSERT INTO servicing_records (aircraft_id, type, quantity, unit, serviced_by, service_date, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, aircraftId, type.toUpperCase(), quantity, unit.toUpperCase(),
                maintainer, nowSql(), notes == null ? "" : notes);
        Map<String, Object> saved = one("SELECT * FROM servicing_records WHERE id=?", id).orElseThrow();
        recordLedger("SERVICING", saved, maintainer);
        return saved;
    }

    public List<Map<String, Object>> timeline(long aircraftId) {
        return many("""
                SELECT reported_date AS event_date, 'DISCREPANCY OPENED' AS event_type,
                       discrepancy_number || ': ' || description AS description
                FROM discrepancies WHERE aircraft_id=?
                UNION ALL
                SELECT d.closed_date AS event_date, 'DISCREPANCY CLOSED' AS event_type,
                       d.discrepancy_number || ': ' || d.description AS description
                FROM discrepancies d WHERE d.aircraft_id=? AND d.closed_date IS NOT NULL
                UNION ALL
                SELECT m.action_date AS event_date, 'MAINTENANCE ACTION' AS event_type,
                       d.discrepancy_number || ': ' || m.action_text AS description
                FROM maintenance_actions m
                JOIN discrepancies d ON d.id=m.discrepancy_id
                WHERE d.aircraft_id=?
                UNION ALL
                SELECT service_date AS event_date, 'SERVICING' AS event_type,
                       type || ': ' || quantity || ' ' || unit AS description
                FROM servicing_records WHERE aircraft_id=?
                UNION ALL
                SELECT completed_date AS event_date, 'MODIFICATION COMPLETE' AS event_type,
                       mod_number || ': ' || title AS description
                FROM modifications WHERE aircraft_id=? AND completed_date IS NOT NULL
                ORDER BY event_date DESC
                """, aircraftId, aircraftId, aircraftId, aircraftId, aircraftId);
    }

    public Optional<Map<String, Object>> turnover(long aircraftId) {
        Optional<Map<String, Object>> aircraft = one("SELECT * FROM aircraft WHERE id=?", aircraftId);
        if (aircraft.isEmpty())
            return Optional.empty();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("aircraft", aircraft.get());
        response.put("openDiscrepancies", many(
                "SELECT * FROM discrepancies WHERE aircraft_id=? AND status='OPEN' ORDER BY reported_date",
                aircraftId));
        response.put("latestServicing", many("""
                SELECT s1.* FROM servicing_records s1
                WHERE s1.aircraft_id=? AND s1.service_date=(
                    SELECT MAX(s2.service_date) FROM servicing_records s2
                    WHERE s2.aircraft_id=s1.aircraft_id AND s2.type=s1.type
                ) ORDER BY s1.type
                """, aircraftId));
        return Optional.of(response);
    }

    public Map<String, Object> search(String rawQuery) {
        if (rawQuery == null || rawQuery.trim().isEmpty()) {
            return Map.of("aircraft", List.of(), "discrepancies", List.of(), "modifications", List.of());
        }
        String q = "%" + rawQuery.trim() + "%";
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("aircraft", many(
                "SELECT * FROM aircraft WHERE tail_number LIKE ? OR model LIKE ? OR status LIKE ? OR location LIKE ?",
                q, q, q, q));
        response.put("discrepancies", many("""
                SELECT d.*, a.tail_number FROM discrepancies d JOIN aircraft a ON a.id=d.aircraft_id
                WHERE d.discrepancy_number LIKE ? OR d.description LIKE ? OR d.symbol LIKE ? OR d.assigned_shop LIKE ?
                """, q, q, q, q));
        response.put("modifications", many("""
                SELECT m.*, a.tail_number FROM modifications m JOIN aircraft a ON a.id=m.aircraft_id
                WHERE m.mod_number LIKE ? OR m.title LIKE ? OR m.description LIKE ?
                """, q, q, q));
        return response;
    }

    public List<Map<String, Object>> inspections(long aircraftId) {
        return many("SELECT * FROM inspections WHERE aircraft_id=? ORDER BY due_hours, due_date", aircraftId);
    }

    public Map<String, Object> addInspection(long aircraftId, Map<String, Object> body) {
        long id = insertAndReturnId("""
                INSERT INTO inspections (aircraft_id, name, due_hours, due_date, last_completed_date, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, aircraftId, body.get("name"), body.get("dueHours"), body.get("dueDate"),
                body.get("lastCompletedDate"), blankToDefault(string(body.get("status")), "DUE"));
        return one("SELECT * FROM inspections WHERE id=?", id).orElseThrow();
    }

    public Optional<Map<String, Object>> completeInspection(long id, String completedBy) {
        int changed = jdbc.update("UPDATE inspections SET status='COMPLETE', last_completed_date=? WHERE id=?",
                java.time.LocalDate.now().toString(), id);
        if (changed == 0)
            return Optional.empty();
        Map<String, Object> saved = one("SELECT * FROM inspections WHERE id=?", id).orElseThrow();
        recordLedger("INSPECTION_COMPLETE", saved, blankToDefault(completedBy, "Demo Maintainer"));
        return Optional.of(saved);
    }

    public List<Map<String, Object>> timeChanges(long aircraftId) {
        return many("SELECT * FROM time_change_items WHERE aircraft_id=? ORDER BY due_hours, due_date", aircraftId);
    }

    public Map<String, Object> addTimeChange(long aircraftId, Map<String, Object> body) {
        long id = insertAndReturnId(
                """
                        INSERT INTO time_change_items
                        (aircraft_id, name, part_number, serial_number, installed_date, installed_hours, due_date, due_hours, warning_hours)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                        """,
                aircraftId, body.get("name"), body.get("partNumber"), body.get("serialNumber"),
                body.get("installedDate"), body.get("installedHours"), body.get("dueDate"),
                body.get("dueHours"), body.getOrDefault("warningHours", 50));
        return one("SELECT * FROM time_change_items WHERE id=?", id).orElseThrow();
    }

    public List<Map<String, Object>> modifications(long aircraftId) {
        return many("SELECT * FROM modifications WHERE aircraft_id=? ORDER BY id DESC", aircraftId);
    }

    public Map<String, Object> addModification(long aircraftId, Map<String, Object> body) {
        long id = insertAndReturnId("""
                INSERT INTO modifications (aircraft_id, mod_number, title, description, status, notes)
                VALUES (?, ?, ?, ?, ?, ?)
                """, aircraftId, body.get("modNumber"), body.get("title"), body.get("description"),
                blankToDefault(string(body.get("status")), "NOT STARTED"), body.get("notes"));
        return one("SELECT * FROM modifications WHERE id=?", id).orElseThrow();
    }

    public Optional<Map<String, Object>> updateModification(long id, Map<String, Object> body) {
        Optional<Map<String, Object>> current = one("SELECT * FROM modifications WHERE id=?", id);
        if (current.isEmpty())
            return Optional.empty();
        Map<String, Object> c = current.get();
        String status = blankToDefault(string(body.get("status")), string(c.get("status")));
        String completed = "COMPLETE".equalsIgnoreCase(status)
                ? blankToDefault(string(body.get("completedDate")), java.time.LocalDate.now().toString())
                : string(c.get("completed_date"));
        jdbc.update("UPDATE modifications SET title=?, description=?, status=?, completed_date=?, notes=? WHERE id=?",
                valueOr(body, "title", c.get("title")), valueOr(body, "description", c.get("description")),
                status, completed, valueOr(body, "notes", c.get("notes")), id);
        Map<String, Object> saved = one("SELECT * FROM modifications WHERE id=?", id).orElseThrow();
        if ("COMPLETE".equalsIgnoreCase(status))
            recordLedger("MODIFICATION_COMPLETE", saved, "Demo Maintainer");
        return Optional.of(saved);
    }

    public List<Map<String, Object>> users() {
        return many("SELECT id, username, full_name, role FROM users ORDER BY full_name");
    }

    public Map<String, Object> createUser(Map<String, Object> body) {
        long id = insertAndReturnId("INSERT INTO users (username, password, full_name, role) VALUES (?, ?, ?, ?)",
                body.get("username"), body.get("password"), body.get("fullName"),
                blankToDefault(string(body.get("role")), "MAINTAINER"));
        return one("SELECT id, username, full_name, role FROM users WHERE id=?", id).orElseThrow();
    }

    public Map<String, Object> aiSuggestion(long discrepancyId, String prompt) {
        Map<String, Object> discrepancy = one("SELECT * FROM discrepancies WHERE id=?", discrepancyId)
                .orElseThrow(() -> new IllegalArgumentException("Discrepancy not found"));
        long aircraftId = ((Number) discrepancy.get("aircraft_id")).longValue();
        Map<String, Object> aircraft = one("SELECT * FROM aircraft WHERE id=?", aircraftId).orElse(Map.of());

        Map<String, Object> draft = aiService.generateMaintenanceDraft(discrepancy, aircraft, prompt);
        String suggestion = String.valueOf(draft.getOrDefault("suggestedAction", ""));
        Object codesValue = draft.getOrDefault("suggestedCodes", List.of());
        String codes;
        if (codesValue instanceof List<?> list) {
            codes = list.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        } else {
            codes = String.valueOf(codesValue);
        }
        String promptText = blankToDefault(prompt, string(discrepancy.get("description")));
        long id = insertAndReturnId(
                """
                        INSERT INTO ai_audit (discrepancy_id, prompt_text, suggestion_text, suggested_codes, decision, created_at)
                        VALUES (?, ?, ?, ?, 'PENDING', ?)
                        """,
                discrepancyId, promptText, suggestion, codes, nowSql());

        Map<String, Object> response = new LinkedHashMap<>(draft);
        response.put("auditId", id);
        response.put("discrepancyId", discrepancyId);
        response.put("suggestion", suggestion);
        response.put("suggestedCodes", codes.isBlank() ? List.of() : List.of(codes.split(",")));
        response.put("requiresHumanApproval", true);
        response.put("disclaimer",
                "AI output is a draft only. A qualified maintainer must verify the aircraft condition and current approved technical data before performing, signing, or closing maintenance.");
        return response;
    }

    public Map<String, Object> aiStatus() {
        return aiService.status();
    }

    public Optional<Map<String, Object>> decideAiSuggestion(long auditId, String decision, String decidedBy) {
        String normalized = blankToDefault(decision, "REJECTED").toUpperCase();
        if (!List.of("ACCEPTED", "EDITED", "REJECTED", "BYPASSED").contains(normalized))
            throw new IllegalArgumentException("Invalid AI decision");
        String reviewer = blankToDefault(decidedBy, "Demo Maintainer");
        int changed = jdbc.update("UPDATE ai_audit SET decision=?, decided_by=?, decided_at=? WHERE id=?",
                normalized, reviewer, nowSql(), auditId);
        if (changed == 0)
            return Optional.empty();
        Map<String, Object> saved = one("SELECT * FROM ai_audit WHERE id=?", auditId).orElseThrow();
        recordLedger("AI_DECISION", saved, reviewer);
        return Optional.of(saved);
    }

    public Map<String, Object> syncEvent(String clientEventId, String eventType, Object payload, String deviceName) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            jdbc.update(
                    """
                            INSERT OR IGNORE INTO sync_events (client_event_id, event_type, payload_json, device_name, status, received_at)
                            VALUES (?, ?, ?, ?, 'SYNCED', ?)
                            """,
                    clientEventId, eventType, json, blankToDefault(deviceName, "UNKNOWN"), nowSql());
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("clientEventId", clientEventId);
            response.put("status", "SYNCED");
            response.put("duplicateSafe", true);
            response.put("receivedAt", nowSql());
            return response;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to store sync event", ex);
        }
    }

    public List<Map<String, Object>> syncHistory() {
        return many("SELECT * FROM sync_events ORDER BY received_at DESC LIMIT 100");
    }

    public Map<String, Object> analytics() {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("statusCounts",
                many("SELECT status, COUNT(*) AS count FROM aircraft GROUP BY status ORDER BY status"));
        response.put("shopWorkload", many("""
                SELECT assigned_shop AS shop, COUNT(*) AS open_count
                FROM discrepancies WHERE status='OPEN'
                GROUP BY assigned_shop ORDER BY open_count DESC
                """));
        response.put("repeatPatterns", many("""
                SELECT UPPER(SUBSTR(description, 1, 36)) AS pattern, COUNT(*) AS occurrences
                FROM discrepancies
                GROUP BY UPPER(SUBSTR(description, 1, 36))
                HAVING COUNT(*) > 1
                ORDER BY occurrences DESC LIMIT 10
                """));
        response.put("aiDecisions",
                many("SELECT decision, COUNT(*) AS count FROM ai_audit GROUP BY decision ORDER BY count DESC"));
        response.put("offlineSync", many("SELECT status, COUNT(*) AS count FROM sync_events GROUP BY status"));
        response.put("ledgerValid", ledgerService.isValid());
        response.put("ledgerBlocks", ledgerService.getLedger().getChain().size());
        response.put("planningAlerts", many("""
                SELECT a.id AS aircraft_id, a.tail_number, a.status,
                       SUM(CASE WHEN d.status='OPEN' THEN 1 ELSE 0 END) AS open_discrepancies,
                       SUM(CASE WHEN d.status='OPEN' AND d.symbol='RED X' THEN 1 ELSE 0 END) AS red_x_count
                FROM aircraft a LEFT JOIN discrepancies d ON d.aircraft_id=a.id
                GROUP BY a.id, a.tail_number, a.status
                ORDER BY red_x_count DESC, open_discrepancies DESC
                """));
        response.put("note",
                "Planning indicators summarize recorded maintenance data only and do not determine airworthiness or return-to-service approval.");
        return response;
    }

    private void recordLedger(String kind, Map<String, Object> record, String signer) {
        try {
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("eventKind", kind);
            envelope.put("record", record);
            ledgerService.recordMaintenanceEvent(objectMapper.writeValueAsString(envelope), signer);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to record event in maintenance ledger", ex);
        }
    }

    private String suggestCodes(String text) {
        String t = text == null ? "" : text.toLowerCase();
        if (t.contains("hydraulic"))
            return "HYD,OPS-CHECK";
        if (t.contains("elect") || t.contains("light"))
            return "ELEC,OPS-CHECK";
        if (t.contains("engine") || t.contains("oil"))
            return "ENG,OPS-CHECK";
        if (t.contains("avion") || t.contains("radio"))
            return "AVIONICS,OPS-CHECK";
        return "GENERAL,OPS-CHECK";
    }

    private Object valueOr(Map<String, Object> body, String key, Object fallback) {
        return body.containsKey(key) ? body.get(key) : fallback;
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public Map<String, Object> getDiscrepancyById(int discrepancyId) {
        String sql = """
                SELECT *
                FROM discrepancies
                WHERE id = ?
                """;

        return jdbc.queryForMap(sql, discrepancyId);
    }

    public Map<String, Object> getAircraftById(int aircraftId) {
        String sql = """
                SELECT *
                FROM aircraft
                WHERE id = ?
                """;

        return jdbc.queryForMap(sql, aircraftId);
    }

    public Map<String, Object> generateAiCloseout(
            int discrepancyId,
            String workPerformed) {

        Map<String, Object> discrepancy = getDiscrepancyById(discrepancyId);

        int aircraftId = ((Number) discrepancy.get("aircraft_id")).intValue();

        Map<String, Object> aircraft = getAircraftById(aircraftId);

        return aiService.generateCloseoutDraft(
                discrepancy,
                aircraft,
                workPerformed);
    }
}
