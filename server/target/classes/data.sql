INSERT OR IGNORE INTO users (username, password, full_name, role) VALUES
('maintainer', 'demo123', 'SSgt Demo Maintainer', 'MAINTAINER'),
('supervisor', 'demo123', 'MSgt Demo Supervisor', 'SUPERVISOR');

INSERT OR IGNORE INTO aircraft (id, tail_number, model, status, location, total_hours, total_cycles, assigned_crew_chief) VALUES
(1, '07-7182', 'C-17A', 'FMC', 'Dover AFB', 8421.6, 2835, 'SSgt J. Smith'),
(2, '08-8192', 'C-17A', 'PMC', 'Dover AFB', 7950.3, 2651, 'TSgt A. Lee'),
(3, '06-6167', 'C-17A', 'NMC', 'Dover AFB', 9120.8, 3102, 'SSgt R. Jones'),
(4, '09-9211', 'C-17A', 'FMC', 'Dover AFB', 7442.0, 2444, 'SrA K. Davis');

INSERT OR IGNORE INTO discrepancies (id, aircraft_id, discrepancy_number, description, symbol, status, reported_by, assigned_shop) VALUES
(1, 2, 'MX-208', 'Landing light intermittent during ops check.', 'RED DASH', 'OPEN', 'SSgt Demo Maintainer', 'ELECTRICAL'),
(2, 3, 'MX-310', 'Hydraulic quantity decreasing after operation.', 'RED X', 'OPEN', 'SSgt R. Jones', 'HYDRAULICS'),
(3, 1, 'MX-116', 'Crew entry door seal shows minor wear.', 'INFORMATIONAL', 'CLOSED', 'SrA K. Davis', 'CREW CHIEF');

INSERT OR IGNORE INTO maintenance_actions (id, discrepancy_id, action_text, performed_by, action_date) VALUES
(1, 3, 'Inspected seal. No leak noted. Condition documented for monitoring.', 'SSgt J. Smith', '2026-08-07 16:33:00');

UPDATE discrepancies SET closed_by='SSgt J. Smith', closed_date='2026-08-07 16:33:00' WHERE id=3;

INSERT OR IGNORE INTO inspections (id, aircraft_id, name, due_hours, due_date, last_completed_date, status) VALUES
(1, 1, 'Home Station Check', 8462.8, NULL, '2026-07-20', 'DUE'),
(2, 1, '30 Day Inspection', NULL, '2026-08-22', '2026-07-23', 'DUE'),
(3, 2, 'Home Station Check', 7968.0, NULL, '2026-07-15', 'DUE');

INSERT OR IGNORE INTO time_change_items (id, aircraft_id, name, part_number, serial_number, installed_date, installed_hours, due_date, due_hours, warning_hours) VALUES
(1, 1, 'Main Battery', 'BAT-1001', 'BATT-7782', '2025-11-01', 7900.0, NULL, 8500.0, 75.0),
(2, 1, 'Hydraulic Filter', 'HF-220', 'HF-22118', '2026-05-04', 8200.0, NULL, 8450.0, 50.0),
(3, 1, 'Emergency Bottle', 'BOT-18', 'EB-9912', '2025-09-15', NULL, '2026-09-15', NULL, 30.0);

INSERT OR IGNORE INTO engines (id, aircraft_id, position, serial_number, total_hours, total_cycles, installed_date) VALUES
(1, 1, 1, 'ENG-7182-1', 7832.4, 2590, '2024-02-10'),
(2, 1, 2, 'ENG-7182-2', 8001.9, 2661, '2023-11-06'),
(3, 1, 3, 'ENG-7182-3', 7922.1, 2618, '2024-01-19'),
(4, 1, 4, 'ENG-7182-4', 8110.7, 2704, '2023-08-23'),
(5, 2, 1, 'ENG-8192-1', 7012.0, 2311, '2024-06-15'),
(6, 2, 2, 'ENG-8192-2', 7334.2, 2410, '2024-03-20');

INSERT OR IGNORE INTO servicing_records (id, aircraft_id, type, quantity, unit, serviced_by, service_date, notes) VALUES
(1, 1, 'FUEL', 87000, 'LBS', 'SSgt J. Smith', '2026-08-09 10:15:00', 'Post-flight fuel level'),
(2, 1, 'LOX', 72, 'PERCENT', 'SrA K. Davis', '2026-08-08 18:11:00', 'Servicing check'),
(3, 1, 'ENGINE OIL #3', 2, 'QUARTS', 'SrA K. Davis', '2026-08-08 18:11:00', 'Added during post-flight'),
(4, 2, 'FUEL', 62000, 'LBS', 'TSgt A. Lee', '2026-08-09 09:00:00', 'Current fuel load');

INSERT OR IGNORE INTO modifications (id, aircraft_id, mod_number, title, description, status, completed_date, notes) VALUES
(1, 1, 'MOD-2026-014', 'Updated Communications Package', 'Demonstration communications-system modification.', 'IN PROGRESS', NULL, 'Kit received; awaiting scheduled downtime'),
(2, 1, 'MOD-2025-008', 'Cabin Lighting Update', 'Demonstration lighting configuration update.', 'COMPLETE', '2025-12-11', 'Completed and inspected');
