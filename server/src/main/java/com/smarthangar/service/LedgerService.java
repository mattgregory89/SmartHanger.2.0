package com.smarthangar.service;

import java.security.KeyPair;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smarthangar.ledger.MaintenanceLedgerV3;

@Service
public class LedgerService {

    private final JdbcTemplate jdbc;
    private final MaintenanceLedgerV3 ledger;
    private final KeyPair keyPair;
    private final ObjectMapper mapper = new ObjectMapper();

    public LedgerService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.keyPair = MaintenanceLedgerV3.generateRsaKeyPair();
        this.ledger = loadLedger();
    }

    private MaintenanceLedgerV3 loadLedger() {
        try {
            List<MaintenanceLedgerV3.Block> stored = new ArrayList<>();
            List<java.util.Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT * FROM ledger_blocks ORDER BY block_index");
            for (java.util.Map<String, Object> row : rows) {
                JsonNode payload = mapper.readTree(String.valueOf(row.get("payload_json")));
                MaintenanceLedgerV3.LedgerEvent event = new MaintenanceLedgerV3.LedgerEvent(
                        String.valueOf(row.get("event_id")),
                        MaintenanceLedgerV3.EventType.valueOf(String.valueOf(row.get("event_type"))),
                        row.get("target_event_id") == null ? null : String.valueOf(row.get("target_event_id")),
                        payload,
                        Instant.parse(String.valueOf(row.get("event_timestamp"))));
                stored.add(new MaintenanceLedgerV3.Block(
                        ((Number) row.get("block_index")).intValue(),
                        Instant.parse(String.valueOf(row.get("block_timestamp"))),
                        event,
                        String.valueOf(row.get("previous_hash")),
                        String.valueOf(row.get("hash")),
                        String.valueOf(row.get("signer_id")),
                        String.valueOf(row.get("public_key_base64")),
                        String.valueOf(row.get("signature_base64"))));
            }
            return stored.isEmpty() ? new MaintenanceLedgerV3() : MaintenanceLedgerV3.fromBlocks(stored);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to load maintenance ledger", ex);
        }
    }

    public MaintenanceLedgerV3 getLedger() {
        return ledger;
    }

    public MaintenanceLedgerV3.Block recordMaintenanceEvent(String jsonPayload, String signerId) {
        MaintenanceLedgerV3.Block block = ledger.appendMaintenanceEvent(
                jsonPayload, signerId, keyPair.getPrivate(), keyPair.getPublic());
        persist(block);
        return block;
    }

    public MaintenanceLedgerV3.Block recordDeletionEvent(String targetEventId, String reasonJson, String signerId) {
        MaintenanceLedgerV3.Block block = ledger.appendDeletionEvent(
                targetEventId, reasonJson, signerId, keyPair.getPrivate(), keyPair.getPublic());
        persist(block);
        return block;
    }

    public boolean isValid() {
        return ledger.isValidChainAndSignatures();
    }

    private void persist(MaintenanceLedgerV3.Block block) {
        try {
            jdbc.update("""
                    INSERT OR REPLACE INTO ledger_blocks
                    (block_index, block_timestamp, event_id, event_type, target_event_id, payload_json,
                     event_timestamp, previous_hash, hash, signer_id, public_key_base64, signature_base64)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    block.getIndex(), block.getTimestamp().toString(), block.getEvent().getEventId(),
                    block.getEvent().getEventType().name(), block.getEvent().getTargetEventId(),
                    mapper.writeValueAsString(block.getEvent().getPayload()),
                    block.getEvent().getTimestamp().toString(), block.getPreviousHash(), block.getHash(),
                    block.getSignerId(), block.getPublicKeyBase64(), block.getSignatureBase64());
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to persist ledger block", ex);
        }
    }
}
