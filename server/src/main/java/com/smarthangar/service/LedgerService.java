package com.smarthangar.service;

import java.security.KeyPair;

import org.springframework.stereotype.Service;

import com.smarthangar.ledger.MaintenanceLedgerV3;

@Service
public class LedgerService {

    private final MaintenanceLedgerV3 ledger;
    private final KeyPair keyPair;

    public LedgerService() {
        this.ledger = new MaintenanceLedgerV3();
        this.keyPair = MaintenanceLedgerV3.generateRsaKeyPair();
    }

    public MaintenanceLedgerV3 getLedger() {
        return ledger;
    }

    public KeyPair getKeyPair() {
        return keyPair;
    }

    public MaintenanceLedgerV3.Block recordMaintenanceEvent(
        String jsonPayload,
        String signerId) {

    return ledger.appendMaintenanceEvent(
            jsonPayload,
            signerId,
            keyPair.getPrivate(),
            keyPair.getPublic()
    );
}
}