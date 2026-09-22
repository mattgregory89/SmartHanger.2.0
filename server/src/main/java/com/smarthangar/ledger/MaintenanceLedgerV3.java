package com.smarthangar.ledger;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class MaintenanceLedgerV3 {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String SIG_ALGO = "SHA256withRSA";
    private static final String HASH_ALGO = "SHA-256";

    public enum EventType { CREATE, DELETE }

    public static final class LedgerEvent {
        private final String eventId;
        private final EventType eventType;
        private final String targetEventId;
        private final JsonNode payload;
        private final Instant timestamp;

        public LedgerEvent(String eventId, EventType eventType, String targetEventId, JsonNode payload, Instant timestamp) {
            this.eventId = eventId;
            this.eventType = eventType;
            this.targetEventId = targetEventId;
            this.payload = payload;
            this.timestamp = timestamp;
        }

        public String getEventId() { return eventId; }
        public EventType getEventType() { return eventType; }
        public String getTargetEventId() { return targetEventId; }
        public JsonNode getPayload() { return payload; }
        public Instant getTimestamp() { return timestamp; }
    }

    public static final class Block {
        private final int index;
        private final Instant timestamp;
        private final LedgerEvent event;
        private final String previousHash;
        private final String hash;
        private final String signerId;
        private final String publicKeyBase64;
        private final String signatureBase64;

        public Block(int index, Instant timestamp, LedgerEvent event, String previousHash, String hash,
                     String signerId, String publicKeyBase64, String signatureBase64) {
            this.index = index;
            this.timestamp = timestamp;
            this.event = event;
            this.previousHash = previousHash;
            this.hash = hash;
            this.signerId = signerId;
            this.publicKeyBase64 = publicKeyBase64;
            this.signatureBase64 = signatureBase64;
        }

        public int getIndex() { return index; }
        public Instant getTimestamp() { return timestamp; }
        public LedgerEvent getEvent() { return event; }
        public String getPreviousHash() { return previousHash; }
        public String getHash() { return hash; }
        public String getSignerId() { return signerId; }
        public String getPublicKeyBase64() { return publicKeyBase64; }
        public String getSignatureBase64() { return signatureBase64; }
    }

    private final List<Block> chain = new ArrayList<>();

    public MaintenanceLedgerV3() {
        chain.add(createGenesisBlock());
    }

    private MaintenanceLedgerV3(List<Block> blocks) {
        chain.addAll(blocks);
    }

    public static MaintenanceLedgerV3 fromBlocks(List<Block> blocks) {
        if (blocks == null || blocks.isEmpty()) return new MaintenanceLedgerV3();
        List<Block> restored = new ArrayList<>();
        if (blocks.get(0).getIndex() != 0) {
            restored.add(new MaintenanceLedgerV3().getChain().get(0));
        }
        restored.addAll(blocks);
        MaintenanceLedgerV3 ledger = new MaintenanceLedgerV3(restored);
        if (!ledger.isValidChainAndSignatures()) {
            throw new IllegalArgumentException("Stored ledger failed integrity validation");
        }
        return ledger;
    }

    private Block createGenesisBlock() {
        LedgerEvent genesisEvent = new LedgerEvent(
                "GENESIS", EventType.CREATE, null,
                MAPPER.createObjectNode().put("message", "genesis"), Instant.EPOCH
        );
        String hash = computeHash(0, Instant.EPOCH, genesisEvent, "0");
        return new Block(0, Instant.EPOCH, genesisEvent, "0", hash, "SYSTEM", "", "");
    }

    public Block appendMaintenanceEvent(String jsonPayload, String signerId, PrivateKey privateKey, PublicKey publicKey) {
        JsonNode payload = parseJson(jsonPayload, "Invalid maintenance JSON payload");
        LedgerEvent event = new LedgerEvent(UUID.randomUUID().toString(), EventType.CREATE, null, payload, Instant.now());
        return appendSignedEvent(event, signerId, privateKey, publicKey);
    }

    public Block appendDeletionEvent(String targetEventId, String reasonJson, String signerId, PrivateKey privateKey, PublicKey publicKey) {
        if (targetEventId == null || targetEventId.isBlank()) throw new IllegalArgumentException("targetEventId must not be blank");
        if (isDeleted(targetEventId)) throw new IllegalStateException("Event already deleted: " + targetEventId);
        LedgerEvent target = getEventById(targetEventId).orElseThrow(() -> new IllegalArgumentException("Target event not found"));
        if (target.getEventType() != EventType.CREATE) throw new IllegalArgumentException("Can only delete CREATE events");

        JsonNode payload = parseJson(reasonJson, "Invalid delete-reason JSON payload");
        LedgerEvent event = new LedgerEvent(UUID.randomUUID().toString(), EventType.DELETE, targetEventId, payload, Instant.now());
        return appendSignedEvent(event, signerId, privateKey, publicKey);
    }

    private Block appendSignedEvent(LedgerEvent event, String signerId, PrivateKey privateKey, PublicKey publicKey) {
        if (signerId == null || signerId.isBlank()) throw new IllegalArgumentException("signerId required");
        Objects.requireNonNull(privateKey, "privateKey required");
        Objects.requireNonNull(publicKey, "publicKey required");

        Block last = chain.get(chain.size() - 1);
        int index = last.getIndex() + 1;
        Instant blockTs = Instant.now();
        String previousHash = last.getHash();
        String hash = computeHash(index, blockTs, event, previousHash);

        String signingPayload = signingPayload(index, blockTs, event, previousHash, hash, signerId);
        String signatureBase64 = sign(signingPayload, privateKey);
        String publicKeyBase64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());

        Block b = new Block(index, blockTs, event, previousHash, hash, signerId, publicKeyBase64, signatureBase64);
        chain.add(b);
        return b;
    }

    public Optional<LedgerEvent> getEventById(String eventId) {
        return chain.stream().map(Block::getEvent).filter(e -> e.getEventId().equals(eventId)).findFirst();
    }

    public boolean isDeleted(String eventId) {
        return chain.stream().map(Block::getEvent)
                .anyMatch(e -> e.getEventType() == EventType.DELETE && eventId.equals(e.getTargetEventId()));
    }

    public List<LedgerEvent> getActiveEvents() {
        Set<String> deleted = chain.stream().map(Block::getEvent)
                .filter(e -> e.getEventType() == EventType.DELETE)
                .map(LedgerEvent::getTargetEventId)
                .collect(Collectors.toSet());

        return chain.stream().map(Block::getEvent)
                .filter(e -> e.getEventType() == EventType.CREATE)
                .filter(e -> !"GENESIS".equals(e.getEventId()))
                .filter(e -> !deleted.contains(e.getEventId()))
                .toList();
    }

    public List<Block> getChain() {
        return Collections.unmodifiableList(chain);
    }

    public boolean isValidChainAndSignatures() {
        if (chain.isEmpty()) return false;

        for (int i = 0; i < chain.size(); i++) {
            Block curr = chain.get(i);

            String recalculated = computeHash(curr.index, curr.timestamp, curr.event, curr.previousHash);
            if (!recalculated.equals(curr.hash)) return false;

            if (i == 0) {
                if (curr.index != 0 || !"0".equals(curr.previousHash)) return false;
            } else {
                Block prev = chain.get(i - 1);
                if (curr.index != prev.index + 1) return false;
                if (!curr.previousHash.equals(prev.hash)) return false;

                String payload = signingPayload(curr.index, curr.timestamp, curr.event, curr.previousHash, curr.hash, curr.signerId);
                if (!verify(payload, curr.signatureBase64, curr.publicKeyBase64)) return false;
            }
        }
        return true;
    }

    private String computeHash(int index, Instant timestamp, LedgerEvent event, String previousHash) {
        try {
            String raw = index + "|" + timestamp + "|" + event.eventId + "|" + event.eventType + "|" +
                    (event.targetEventId == null ? "" : event.targetEventId) + "|" +
                    MAPPER.writeValueAsString(event.payload) + "|" + event.timestamp + "|" + previousHash;
            MessageDigest md = MessageDigest.getInstance(HASH_ALGO);
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private String signingPayload(int index, Instant blockTimestamp, LedgerEvent event, String previousHash, String hash, String signerId) {
        try {
            ObjectNode n = MAPPER.createObjectNode();
            n.put("index", index);
            n.put("blockTimestamp", blockTimestamp.toString());
            n.put("previousHash", previousHash);
            n.put("hash", hash);
            n.put("signerId", signerId);

            ObjectNode e = n.putObject("event");
            e.put("eventId", event.eventId);
            e.put("eventType", event.eventType.name());
            if (event.targetEventId == null) e.putNull("targetEventId"); else e.put("targetEventId", event.targetEventId);
            e.set("payload", event.payload);
            e.put("eventTimestamp", event.timestamp.toString());

            return MAPPER.writeValueAsString(n);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private String sign(String payload, PrivateKey privateKey) {
        try {
            Signature sig = Signature.getInstance(SIG_ALGO);
            sig.initSign(privateKey);
            sig.update(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(sig.sign());
        } catch (Exception e) {
            throw new RuntimeException("Signing failed", e);
        }
    }

    private boolean verify(String payload, String signatureBase64, String publicKeyBase64) {
        try {
            byte[] pubBytes = Base64.getDecoder().decode(publicKeyBase64);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            PublicKey pub = kf.generatePublic(new X509EncodedKeySpec(pubBytes));

            Signature sig = Signature.getInstance(SIG_ALGO);
            sig.initVerify(pub);
            sig.update(payload.getBytes(StandardCharsets.UTF_8));
            return sig.verify(Base64.getDecoder().decode(signatureBase64));
        } catch (Exception e) {
            return false;
        }
    }

    private JsonNode parseJson(String json, String msg) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(msg, e);
        }
    }

    public static KeyPair generateRsaKeyPair() {
        try {
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
            kpg.initialize(2048);
            return kpg.generateKeyPair();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}