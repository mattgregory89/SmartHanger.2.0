package com.smarthangar.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class OfflineSyncService {

    private final JdbcTemplate jdbc;
    private final SmartHangarService smartHangar;
    private final ObjectMapper mapper = new ObjectMapper();

    public OfflineSyncService(JdbcTemplate jdbc, SmartHangarService smartHangar) {
        this.jdbc = jdbc;
        this.smartHangar = smartHangar;
    }

    public Map<String, Object> demoPackage(long aircraftId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("device", smartHangar.one("SELECT * FROM device_config WHERE aircraft_id=? ORDER BY id LIMIT 1", aircraftId).orElse(Map.of()));
        response.put("snapshot", smartHangar.aircraftDetail(aircraftId)
                .orElseThrow(() -> new IllegalArgumentException("Demo aircraft not found")));
        response.put("instructions", "This package represents the aircraft-local maintenance workspace cached before network loss.");
        return response;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> syncBatch(Map<String, Object> body) {
        String deviceName = text(body.get("deviceName"), "UNKNOWN-OFFLINE-DEVICE");
        Object rawEvents = body.get("events");
        if (!(rawEvents instanceof List<?> events)) throw new IllegalArgumentException("events must be an array");

        List<Map<String, Object>> results = new ArrayList<>();
        int applied = 0;
        int duplicates = 0;
        int failed = 0;

        for (Object raw : events) {
            if (!(raw instanceof Map<?, ?> rawMap)) continue;
            Map<String, Object> event = new LinkedHashMap<>();
            rawMap.forEach((k, v) -> event.put(String.valueOf(k), v));
            String clientEventId = text(event.get("clientEventId"), "");
            String eventType = text(event.get("eventType"), "").toUpperCase();
            String localEntityId = text(event.get("localEntityId"), "");
            Map<String, Object> payload = event.get("payload") instanceof Map<?, ?> p ? normalizeMap(p) : new LinkedHashMap<>();

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("clientEventId", clientEventId);
            result.put("eventType", eventType);

            if (clientEventId.isBlank() || eventType.isBlank()) {
                result.put("status", "FAILED");
                result.put("message", "clientEventId and eventType are required");
                results.add(result);
                failed++;
                continue;
            }

            Optional<Map<String, Object>> existing = smartHangar.one(
                    "SELECT * FROM offline_sync_receipts WHERE client_event_id=?", clientEventId);
            if (existing.isPresent()) {
                result.put("status", "DUPLICATE");
                result.put("remoteEntityId", existing.get().get("remote_entity_id"));
                result.put("remoteEntityType", existing.get().get("remote_entity_type"));
                results.add(result);
                duplicates++;
                continue;
            }

            try {
                AppliedEntity appliedEntity = apply(eventType, localEntityId, payload, deviceName);
                String resultJson = mapper.writeValueAsString(appliedEntity.record());
                jdbc.update("""
                        INSERT INTO offline_sync_receipts
                        (client_event_id, event_type, local_entity_id, remote_entity_type, remote_entity_id, result_json, device_name)
                        VALUES (?, ?, ?, ?, ?, ?, ?)
                        """, clientEventId, eventType, localEntityId, appliedEntity.type(), appliedEntity.id(), resultJson, deviceName);

                smartHangar.syncEvent(clientEventId, "OFFLINE " + eventType, payload, deviceName);
                result.put("status", "APPLIED");
                result.put("remoteEntityType", appliedEntity.type());
                result.put("remoteEntityId", appliedEntity.id());
                result.put("record", appliedEntity.record());
                applied++;
            } catch (Exception ex) {
                result.put("status", "FAILED");
                result.put("message", ex.getMessage());
                failed++;
            }
            results.add(result);
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("deviceName", deviceName);
        summary.put("applied", applied);
        summary.put("duplicates", duplicates);
        summary.put("failed", failed);
        summary.put("results", results);
        return summary;
    }

    private AppliedEntity apply(String eventType, String localEntityId, Map<String, Object> payload, String deviceName) {
        return switch (eventType) {
            case "CREATE_DISCREPANCY" -> {
                long aircraftId = longValue(payload.get("aircraftId"));
                Map<String, Object> record = smartHangar.addDiscrepancy(
                        aircraftId,
                        text(payload.get("description"), "Offline discrepancy"),
                        text(payload.get("symbol"), "RED DASH"),
                        text(payload.get("assignedShop"), "CREW CHIEF"),
                        text(payload.get("reportedBy"), deviceName));
                yield new AppliedEntity("DISCREPANCY", ((Number) record.get("id")).longValue(), record);
            }
            case "CREATE_SERVICING" -> {
                long aircraftId = longValue(payload.get("aircraftId"));
                Map<String, Object> record = smartHangar.addServicing(
                        aircraftId,
                        text(payload.get("type"), "FUEL"),
                        doubleValue(payload.get("quantity")),
                        text(payload.get("unit"), "LBS"),
                        text(payload.get("servicedBy"), deviceName),
                        text(payload.get("notes"), "Synced from offline device"));
                yield new AppliedEntity("SERVICING", ((Number) record.get("id")).longValue(), record);
            }
            case "CREATE_ACTION" -> {
                long discrepancyId = resolveDiscrepancyId(payload);
                Map<String, Object> record = smartHangar.addAction(
                        discrepancyId,
                        text(payload.get("actionText"), "Offline maintenance action"),
                        text(payload.get("performedBy"), deviceName));
                yield new AppliedEntity("MAINTENANCE_ACTION", ((Number) record.get("id")).longValue(), record);
            }
            case "CLOSE_DISCREPANCY" -> {
                long discrepancyId = resolveDiscrepancyId(payload);
                Map<String, Object> record = smartHangar.closeDiscrepancy(
                                discrepancyId,
                                text(payload.get("correctiveAction"), "Corrective action synchronized from offline device"),
                                text(payload.get("closedBy"), deviceName))
                        .orElseThrow(() -> new IllegalArgumentException("Discrepancy not found"));
                yield new AppliedEntity("DISCREPANCY", discrepancyId, record);
            }
            default -> throw new IllegalArgumentException("Unsupported offline event type: " + eventType);
        };
    }

    private long resolveDiscrepancyId(Map<String, Object> payload) {
        Object remote = payload.get("discrepancyId");
        if (remote != null && !String.valueOf(remote).isBlank()) return longValue(remote);

        String local = text(payload.get("localDiscrepancyId"), "");
        if (local.isBlank()) throw new IllegalArgumentException("discrepancyId or localDiscrepancyId is required");
        return smartHangar.one("""
                SELECT remote_entity_id FROM offline_sync_receipts
                WHERE local_entity_id=? AND remote_entity_type='DISCREPANCY'
                ORDER BY synced_at DESC LIMIT 1
                """, local)
                .map(row -> ((Number) row.get("remote_entity_id")).longValue())
                .orElseThrow(() -> new IllegalArgumentException("No synchronized discrepancy mapping for " + local));
    }

    private Map<String, Object> normalizeMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((k, v) -> result.put(String.valueOf(k), v));
        return result;
    }

    private String text(Object value, String fallback) {
        String s = value == null ? "" : String.valueOf(value);
        return s.isBlank() ? fallback : s;
    }

    private long longValue(Object value) {
        if (value instanceof Number n) return n.longValue();
        return Long.parseLong(String.valueOf(value));
    }

    private double doubleValue(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }

    private record AppliedEntity(String type, long id, Map<String, Object> record) {}
}
