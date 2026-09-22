PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS users (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    username TEXT UNIQUE NOT NULL,
    password TEXT NOT NULL,
    full_name TEXT NOT NULL,
    role TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS aircraft (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    tail_number TEXT UNIQUE NOT NULL,
    model TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'FMC',
    location TEXT,
    total_hours REAL NOT NULL DEFAULT 0,
    total_cycles INTEGER NOT NULL DEFAULT 0,
    assigned_crew_chief TEXT,
    last_updated TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS discrepancies (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    aircraft_id INTEGER NOT NULL,
    discrepancy_number TEXT NOT NULL,
    description TEXT NOT NULL,
    symbol TEXT NOT NULL,
    status TEXT NOT NULL DEFAULT 'OPEN',
    reported_by TEXT,
    reported_date TEXT DEFAULT CURRENT_TIMESTAMP,
    assigned_shop TEXT,
    closed_by TEXT,
    closed_date TEXT,
    FOREIGN KEY (aircraft_id) REFERENCES aircraft(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS maintenance_actions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    discrepancy_id INTEGER NOT NULL,
    action_text TEXT NOT NULL,
    performed_by TEXT,
    action_date TEXT DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (discrepancy_id) REFERENCES discrepancies(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS inspections (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    aircraft_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    due_hours REAL,
    due_date TEXT,
    last_completed_date TEXT,
    status TEXT NOT NULL DEFAULT 'DUE',
    FOREIGN KEY (aircraft_id) REFERENCES aircraft(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS time_change_items (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    aircraft_id INTEGER NOT NULL,
    name TEXT NOT NULL,
    part_number TEXT,
    serial_number TEXT,
    installed_date TEXT,
    installed_hours REAL,
    due_date TEXT,
    due_hours REAL,
    warning_hours REAL DEFAULT 50,
    FOREIGN KEY (aircraft_id) REFERENCES aircraft(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS engines (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    aircraft_id INTEGER NOT NULL,
    position INTEGER NOT NULL,
    serial_number TEXT NOT NULL,
    total_hours REAL NOT NULL DEFAULT 0,
    total_cycles INTEGER NOT NULL DEFAULT 0,
    installed_date TEXT,
    FOREIGN KEY (aircraft_id) REFERENCES aircraft(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS servicing_records (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    aircraft_id INTEGER NOT NULL,
    type TEXT NOT NULL,
    quantity REAL NOT NULL,
    unit TEXT NOT NULL,
    serviced_by TEXT,
    service_date TEXT DEFAULT CURRENT_TIMESTAMP,
    notes TEXT,
    FOREIGN KEY (aircraft_id) REFERENCES aircraft(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS modifications (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    aircraft_id INTEGER NOT NULL,
    mod_number TEXT NOT NULL,
    title TEXT NOT NULL,
    description TEXT,
    status TEXT NOT NULL DEFAULT 'NOT STARTED',
    completed_date TEXT,
    notes TEXT,
    FOREIGN KEY (aircraft_id) REFERENCES aircraft(id) ON DELETE CASCADE
);
CREATE TABLE IF NOT EXISTS device_config (
    id INTEGER PRIMARY KEY,
    device_name TEXT NOT NULL,
    aircraft_id INTEGER,
    mode TEXT NOT NULL DEFAULT 'AIRCRAFT'
);

CREATE TABLE IF NOT EXISTS ledger_blocks (
    block_index INTEGER PRIMARY KEY,
    block_timestamp TEXT NOT NULL,
    event_id TEXT UNIQUE NOT NULL,
    event_type TEXT NOT NULL,
    target_event_id TEXT,
    payload_json TEXT NOT NULL,
    event_timestamp TEXT NOT NULL,
    previous_hash TEXT NOT NULL,
    hash TEXT NOT NULL,
    signer_id TEXT NOT NULL,
    public_key_base64 TEXT NOT NULL,
    signature_base64 TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS ai_audit (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    discrepancy_id INTEGER,
    prompt_text TEXT NOT NULL,
    suggestion_text TEXT NOT NULL,
    suggested_codes TEXT,
    decision TEXT NOT NULL DEFAULT 'PENDING',
    decided_by TEXT,
    created_at TEXT DEFAULT CURRENT_TIMESTAMP,
    decided_at TEXT,
    FOREIGN KEY (discrepancy_id) REFERENCES discrepancies(id) ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS sync_events (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_event_id TEXT UNIQUE NOT NULL,
    event_type TEXT NOT NULL,
    payload_json TEXT NOT NULL,
    device_name TEXT,
    status TEXT NOT NULL DEFAULT 'SYNCED',
    received_at TEXT DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS offline_sync_receipts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    client_event_id TEXT UNIQUE NOT NULL,
    event_type TEXT NOT NULL,
    local_entity_id TEXT,
    remote_entity_type TEXT NOT NULL,
    remote_entity_id INTEGER NOT NULL,
    result_json TEXT,
    device_name TEXT,
    synced_at TEXT DEFAULT CURRENT_TIMESTAMP
);
