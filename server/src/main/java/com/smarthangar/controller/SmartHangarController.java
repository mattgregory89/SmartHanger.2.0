package com.smarthangar.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.smarthangar.ledger.MaintenanceLedgerV3;
import com.smarthangar.service.LedgerService;
import com.smarthangar.service.SmartHangarService;

@RestController
@RequestMapping("/api")
public class SmartHangarController {

    private final SmartHangarService service;
    private final LedgerService ledgerService;

    public SmartHangarController(
            SmartHangarService service,
            LedgerService ledgerService) {

        this.service = service;
        this.ledgerService = ledgerService;
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

    @GetMapping("/aircraft/{id}")
    public ResponseEntity<?> aircraftDetail(@PathVariable long id) {
        return service.aircraftDetail(id)
                .<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("message", "Aircraft not found")));
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

    private static String text(Map<String, Object> body, String key) {
        Object value = body.get(key);
        return value == null ? "" : value.toString();
    }
}
