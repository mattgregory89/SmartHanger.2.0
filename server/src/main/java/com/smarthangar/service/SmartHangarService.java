package com.smarthangar.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SmartHangarService {

    private final JdbcTemplate jdbc;
    private static final DateTimeFormatter SQL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public SmartHangarService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
        if (key == null) throw new IllegalStateException("Insert did not return an id");
        return key.longValue();
    }

    public void refreshAircraftStatus(long aircraftId) {
        Integer redXCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM discrepancies WHERE aircraft_id=? AND status='OPEN' AND symbol='RED X'",
                Integer.class,
                aircraftId
        );

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
        return one("SELECT id, username, full_name, role FROM users WHERE username=? AND password=?", username, password);
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
        List<Map<String, Object>> dueInspections = many("""
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
        if (aircraftOpt.isEmpty()) return Optional.empty();

        Map<String, Object> aircraft = aircraftOpt.get();
        double totalHours = ((Number) aircraft.get("total_hours")).doubleValue();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("aircraft", aircraft);
        response.put("discrepancies", many("SELECT * FROM discrepancies WHERE aircraft_id=? ORDER BY reported_date DESC", id));
        response.put("inspections", many("""
                SELECT *, CASE WHEN due_hours IS NULL THEN NULL ELSE ROUND(due_hours - ?, 1) END AS hours_remaining
                FROM inspections WHERE aircraft_id=? ORDER BY due_hours, due_date
                """, totalHours, id));
        response.put("timeChanges", many("""
                SELECT *, CASE WHEN due_hours IS NULL THEN NULL ELSE ROUND(due_hours - ?, 1) END AS hours_remaining
                FROM time_change_items WHERE aircraft_id=? ORDER BY due_hours, due_date
                """, totalHours, id));
        response.put("engines", many("SELECT * FROM engines WHERE aircraft_id=? ORDER BY position", id));
        response.put("servicing", many("SELECT * FROM servicing_records WHERE aircraft_id=? ORDER BY service_date DESC", id));
        response.put("modifications", many("SELECT * FROM modifications WHERE aircraft_id=? ORDER BY id DESC", id));
        return Optional.of(response);
    }

    public Optional<Map<String, Object>> setAircraftStatus(long id, String status) {
        int changed = jdbc.update("UPDATE aircraft SET status=?, last_updated=? WHERE id=?", status, nowSql(), id);
        if (changed == 0) return Optional.empty();
        return one("SELECT * FROM aircraft WHERE id=?", id);
    }

    public Map<String, Object> addDiscrepancy(long aircraftId, String description, String symbol,
                                               String assignedShop, String reportedBy) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM discrepancies WHERE aircraft_id=?", Integer.class, aircraftId);
        int next = aircraftId > 0 ? (int) (aircraftId * 100 + (count == null ? 0 : count) + 1) : 1;
        String number = "MX-%03d".formatted(next);

        long id = insertAndReturnId("""
                INSERT INTO discrepancies
                (aircraft_id, discrepancy_number, description, symbol, status, reported_by, reported_date, assigned_shop)
                VALUES (?, ?, ?, ?, 'OPEN', ?, ?, ?)
                """, aircraftId, number, description, symbol,
                blankToDefault(reportedBy, "Demo Maintainer"), nowSql(), blankToDefault(assignedShop, "CREW CHIEF"));

        refreshAircraftStatus(aircraftId);
        return one("SELECT * FROM discrepancies WHERE id=?", id).orElseThrow();
    }

    public Map<String, Object> addAction(long discrepancyId, String actionText, String performedBy) {
        long id = insertAndReturnId("""
                INSERT INTO maintenance_actions (discrepancy_id, action_text, performed_by, action_date)
                VALUES (?, ?, ?, ?)
                """, discrepancyId, actionText, blankToDefault(performedBy, "Demo Maintainer"), nowSql());
        return one("SELECT * FROM maintenance_actions WHERE id=?", id).orElseThrow();
    }

    public Optional<Map<String, Object>> closeDiscrepancy(long discrepancyId, String correctiveAction, String closedBy) {
        Optional<Map<String, Object>> discrepancy = one("SELECT * FROM discrepancies WHERE id=?", discrepancyId);
        if (discrepancy.isEmpty()) return Optional.empty();

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
        long id = insertAndReturnId("""
                INSERT INTO servicing_records (aircraft_id, type, quantity, unit, serviced_by, service_date, notes)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, aircraftId, type.toUpperCase(), quantity, unit.toUpperCase(),
                blankToDefault(servicedBy, "Demo Maintainer"), nowSql(), notes == null ? "" : notes);
        return one("SELECT * FROM servicing_records WHERE id=?", id).orElseThrow();
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
        if (aircraft.isEmpty()) return Optional.empty();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("aircraft", aircraft.get());
        response.put("openDiscrepancies", many(
                "SELECT * FROM discrepancies WHERE aircraft_id=? AND status='OPEN' ORDER BY reported_date", aircraftId));
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

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
