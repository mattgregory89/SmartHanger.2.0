package com.smarthangar.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smarthangar.ledger.MaintenanceLedgerV3;
import com.smarthangar.service.LedgerService;
import com.smarthangar.service.OfflineSyncService;
import com.smarthangar.service.SmartHangarService;

@RestController
@RequestMapping("/api")
public class SmartHangarController {

    private final SmartHangarService service;
    private final LedgerService ledgerService;
    private final OfflineSyncService offlineSyncService;

    public SmartHangarController(
            SmartHangarService service,
            LedgerService ledgerService,
            OfflineSyncService offlineSyncService) {

        this.service = service;
        this.ledgerService = ledgerService;
        this.offlineSyncService = offlineSyncService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "SmartHangar API running");
    }

    @GetMapping("/ledger")
    public List<MaintenanceLedgerV3.Block> getLedger() {
        return ledgerService.getLedger().getChain();
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> body) {
        String username = text(body, "username");
        String password = text(body, "password");
        return service.login(username, password)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("message", "Invalid username or password")));
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        return service.dashboard();
    }

    @GetMapping("/aircraft")
    public List<Map<String, Object>> aircraft() {
        return service.aircraft();
    }

    @PostMapping("/aircraft")
    public ResponseEntity<?> createAircraft(@RequestBody Map<String, Object> body) {
        if (text(body, "tailNumber").isBlank() || text(body, "model").isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Tail number and model are required"));
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createAircraft(body));
    }

    @GetMapping("/aircraft/{id}")
    public ResponseEntity<?> aircraftDetail(@PathVariable long id) {
        return service.aircraftDetail(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Aircraft not found")));
    }

    @PutMapping("/aircraft/{id}")
    public ResponseEntity<?> updateAircraft(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return service.updateAircraft(id, body)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Aircraft not found")));
    }

    @DeleteMapping("/aircraft/{id}")
    public ResponseEntity<?> deleteAircraft(@PathVariable long id) {
        return service.deleteAircraft(id) ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Aircraft not found"));
    }

    @PatchMapping("/aircraft/{id}/status")
    public ResponseEntity<?> setStatus(@PathVariable long id, @RequestBody Map<String, Object> body) {
        String status = text(body, "status");
        if (!List.of("FMC", "PMC", "NMC").contains(status)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Invalid status"));
        }
        return service.setAircraftStatus(id, status)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Aircraft not found")));
    }

    @PostMapping("/aircraft/{id}/discrepancies")
    public ResponseEntity<?> addDiscrepancy(@PathVariable long id, @RequestBody Map<String, Object> body) {
        String description = text(body, "description");
        String symbol = text(body, "symbol");
        if (description.isBlank() || symbol.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Description and symbol are required"));
        }
        Map<String, Object> created = service.addDiscrepancy(
                id, description, symbol, text(body, "assignedShop"), text(body, "reportedBy"));
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/discrepancies/{id}")
    public ResponseEntity<?> discrepancy(@PathVariable long id) {
        return service.discrepancy(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Discrepancy not found")));
    }

    @PutMapping("/discrepancies/{id}")
    public ResponseEntity<?> updateDiscrepancy(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return service.updateDiscrepancy(id, body)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Discrepancy not found")));
    }

    @DeleteMapping("/discrepancies/{id}")
    public ResponseEntity<?> deleteDiscrepancy(@PathVariable long id,
            @RequestParam(defaultValue = "Demo Maintainer") String deletedBy) {
        return service.deleteDiscrepancy(id, deletedBy) ? ResponseEntity.noContent().build()
                : ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "Discrepancy not found"));
    }

    @PostMapping("/discrepancies/{id}/actions")
    public ResponseEntity<?> addAction(@PathVariable long id, @RequestBody Map<String, Object> body) {
        String actionText = text(body, "actionText");
        if (actionText.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Action text is required"));
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.addAction(id, actionText, text(body, "performedBy")));
    }

    @PatchMapping("/discrepancies/{id}/close")
    public ResponseEntity<?> closeDiscrepancy(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return service.closeDiscrepancy(id, text(body, "correctiveAction"), text(body, "closedBy"))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Discrepancy not found")));
    }

    @GetMapping("/discrepancies/{id}/actions")
    public List<Map<String, Object>> discrepancyActions(@PathVariable long id) {
        return service.discrepancyActions(id);
    }

    @PostMapping("/aircraft/{id}/servicing")
    public ResponseEntity<?> addServicing(@PathVariable long id, @RequestBody Map<String, Object> body) {
        String type = text(body, "type");
        String unit = text(body, "unit");
        Object quantityValue = body.get("quantity");
        if (type.isBlank() || unit.isBlank() || quantityValue == null) {
            return ResponseEntity.badRequest().body(Map.of("message", "Type, quantity, and unit are required"));
        }
        try {
            double quantity = quantityValue instanceof Number n
                    ? n.doubleValue()
                    : Double.parseDouble(quantityValue.toString());
            return ResponseEntity.status(HttpStatus.CREATED).body(service.addServicing(
                    id, type, quantity, unit, text(body, "servicedBy"), text(body, "notes")));
        } catch (NumberFormatException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Quantity must be numeric"));
        }
    }

    @GetMapping("/aircraft/{id}/timeline")
    public List<Map<String, Object>> timeline(@PathVariable long id) {
        return service.timeline(id);
    }

    @GetMapping("/aircraft/{id}/turnover")
    public ResponseEntity<?> turnover(@PathVariable long id) {
        return service.turnover(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Aircraft not found")));
    }

    @GetMapping("/search")
    public Map<String, Object> search(@RequestParam(defaultValue = "") String q) {
        return service.search(q);
    }

    @GetMapping("/ledger/validate")
    public Map<String, Object> validateLedger() {
        return Map.of(
                "valid", ledgerService.isValid(),
                "blocks", ledgerService.getLedger().getChain().size(),
                "activeEvents", ledgerService.getLedger().getActiveEvents().size());
    }

    @GetMapping("/aircraft/{id}/inspections")
    public List<Map<String, Object>> inspections(@PathVariable long id) {
        return service.inspections(id);
    }

    @PostMapping("/aircraft/{id}/inspections")
    public ResponseEntity<?> addInspection(@PathVariable long id, @RequestBody Map<String, Object> body) {
        if (text(body, "name").isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Inspection name is required"));
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addInspection(id, body));
    }

    @PatchMapping("/inspections/{id}/complete")
    public ResponseEntity<?> completeInspection(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return service.completeInspection(id, text(body, "completedBy"))
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Inspection not found")));
    }

    @GetMapping("/aircraft/{id}/time-changes")
    public List<Map<String, Object>> timeChanges(@PathVariable long id) {
        return service.timeChanges(id);
    }

    @PostMapping("/aircraft/{id}/time-changes")
    public ResponseEntity<?> addTimeChange(@PathVariable long id, @RequestBody Map<String, Object> body) {
        if (text(body, "name").isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Time-change item name is required"));
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addTimeChange(id, body));
    }

    @GetMapping("/aircraft/{id}/modifications")
    public List<Map<String, Object>> modifications(@PathVariable long id) {
        return service.modifications(id);
    }

    @PostMapping("/aircraft/{id}/modifications")
    public ResponseEntity<?> addModification(@PathVariable long id, @RequestBody Map<String, Object> body) {
        if (text(body, "modNumber").isBlank() || text(body, "title").isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "Modification number and title are required"));
        return ResponseEntity.status(HttpStatus.CREATED).body(service.addModification(id, body));
    }

    @PatchMapping("/modifications/{id}")
    public ResponseEntity<?> updateModification(@PathVariable long id, @RequestBody Map<String, Object> body) {
        return service.updateModification(id, body)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Modification not found")));
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        return service.users();
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody Map<String, Object> body) {
        if (text(body, "username").isBlank() || text(body, "password").isBlank() || text(body, "fullName").isBlank())
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Username, password, and full name are required"));
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createUser(body));
    }

    @PostMapping("/discrepancies/{id}/ai-suggestion")
    public ResponseEntity<?> aiSuggestion(@PathVariable long id,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            return ResponseEntity.ok(service.aiSuggestion(id, body == null ? "" : text(body, "prompt")));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
        }
    }

    @PatchMapping("/ai-suggestions/{id}/decision")
    public ResponseEntity<?> decideAi(@PathVariable long id, @RequestBody Map<String, Object> body) {
        try {
            return service.decideAiSuggestion(id, text(body, "decision"), text(body, "decidedBy"))
                    .<ResponseEntity<?>>map(ResponseEntity::ok)
                    .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("message", "AI suggestion not found")));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @GetMapping("/ai/status")
    public Map<String, Object> aiStatus() {
        return service.aiStatus();
    }

    @GetMapping("/offline-demo/package")
    public ResponseEntity<?> offlineDemoPackage(@RequestParam(defaultValue = "5") long aircraftId) {
        try {
            return ResponseEntity.ok(offlineSyncService.demoPackage(aircraftId));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/sync/batch")
    public ResponseEntity<?> syncBatch(@RequestBody Map<String, Object> body) {
        try {
            return ResponseEntity.ok(offlineSyncService.syncBatch(body));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", ex.getMessage()));
        }
    }

    @PostMapping("/sync/events")
    public ResponseEntity<?> syncEvent(@RequestBody Map<String, Object> body) {
        String clientEventId = text(body, "clientEventId");
        String eventType = text(body, "eventType");
        if (clientEventId.isBlank() || eventType.isBlank())
            return ResponseEntity.badRequest().body(Map.of("message", "clientEventId and eventType are required"));
        return ResponseEntity
                .ok(service.syncEvent(clientEventId, eventType, body.get("payload"), text(body, "deviceName")));
    }

    @GetMapping("/sync/events")
    public List<Map<String, Object>> syncHistory() {
        return service.syncHistory();
    }

    @GetMapping("/analytics")
    public Map<String, Object> analytics() {
        return service.analytics();
    }

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : value.toString();
    }

    @PostMapping("/discrepancies/{id}/ai-closeout")
    public ResponseEntity<?> generateAiCloseout(
            @PathVariable int id,
            @RequestBody Map<String, Object> body) {

        try {
            String workPerformed = String.valueOf(body.getOrDefault("workPerformed", ""));

            if (workPerformed.isBlank()) {
                return ResponseEntity.badRequest().body(
                        Map.of(
                                "message",
                                "Actual maintenance performed is required before generating a closeout draft."));
            }

            Map<String, Object> result = service.generateAiCloseout(id, workPerformed);

            return ResponseEntity.ok(result);

        } catch (Exception ex) {
            return ResponseEntity.badRequest().body(
                    Map.of("message", ex.getMessage()));
        }
    }
}
