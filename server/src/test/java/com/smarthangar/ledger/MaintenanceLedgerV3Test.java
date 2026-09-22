package com.smarthangar.ledger;

import static org.junit.jupiter.api.Assertions.*;

import java.security.KeyPair;

import org.junit.jupiter.api.Test;

class MaintenanceLedgerV3Test {

    @Test
    void newLedgerStartsValid() {
        MaintenanceLedgerV3 ledger = new MaintenanceLedgerV3();
        assertTrue(ledger.isValidChainAndSignatures());
        assertEquals(1, ledger.getChain().size());
    }

    @Test
    void appendMaintenanceEventCreatesSignedValidBlock() {
        MaintenanceLedgerV3 ledger = new MaintenanceLedgerV3();
        KeyPair keys = MaintenanceLedgerV3.generateRsaKeyPair();

        MaintenanceLedgerV3.Block block = ledger.appendMaintenanceEvent(
                "{\"aircraftId\":1,\"eventKind\":\"DISCREPANCY\"}",
                "SSgt Test Maintainer", keys.getPrivate(), keys.getPublic());

        assertEquals(1, block.getIndex());
        assertEquals(2, ledger.getChain().size());
        assertFalse(block.getHash().isBlank());
        assertFalse(block.getPreviousHash().isBlank());
        assertTrue(ledger.isValidChainAndSignatures());
        assertEquals(1, ledger.getActiveEvents().size());
    }

    @Test
    void deletionEventRemovesEventFromActiveViewWithoutErasingHistory() {
        MaintenanceLedgerV3 ledger = new MaintenanceLedgerV3();
        KeyPair keys = MaintenanceLedgerV3.generateRsaKeyPair();
        MaintenanceLedgerV3.Block created = ledger.appendMaintenanceEvent(
                "{\"record\":\"test\"}", "Maintainer", keys.getPrivate(), keys.getPublic());

        ledger.appendDeletionEvent(created.getEvent().getEventId(), "{\"reason\":\"superseded\"}",
                "Supervisor", keys.getPrivate(), keys.getPublic());

        assertTrue(ledger.isDeleted(created.getEvent().getEventId()));
        assertTrue(ledger.getActiveEvents().isEmpty());
        assertEquals(3, ledger.getChain().size());
        assertTrue(ledger.isValidChainAndSignatures());
    }

    @Test
    void invalidJsonIsRejected() {
        MaintenanceLedgerV3 ledger = new MaintenanceLedgerV3();
        KeyPair keys = MaintenanceLedgerV3.generateRsaKeyPair();
        assertThrows(IllegalArgumentException.class,
                () -> ledger.appendMaintenanceEvent("not-json", "Maintainer", keys.getPrivate(), keys.getPublic()));
    }
}
