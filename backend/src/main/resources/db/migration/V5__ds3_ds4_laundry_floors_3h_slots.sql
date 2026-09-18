-- ==============================================================================
-- Migration V5: DS-3 / DS-4 + floor-labelled laundry machines + 3h slots
-- ==============================================================================
-- Rules:
--   * laundry_slot_duration_minutes = 180 for all dormitories
--   * machine_identifier / floor_location signed by floor number (e.g. "Pralka p.1")
--   * DS-1, DS-2, DS-3: machines on odd floors only
--   * DS-4: machines on every floor
-- ==============================================================================

-- 1. 3-hour laundry slots campus-wide (existing DS-1 / DS-2)
UPDATE dormitories
SET laundry_slot_duration_minutes = 180;

-- 2. New dormitories DS-3 and DS-4
INSERT INTO dormitories (
    id, name, code, address, floors_count,
    laundry_opening_time, laundry_closing_time, laundry_slot_duration_minutes
)
VALUES
    (
        '33333333-3333-3333-3333-333333333333',
        'DS-3',
        'DS-3',
        'ul. Skarżyńskiego 7, 31-866 Kraków',
        4,
        '07:00:00',
        '23:00:00',
        180
    ),
    (
        '44444444-4444-4444-4444-444444444444',
        'DS-4',
        'DS-4',
        'ul. Skarżyńskiego 9, 31-866 Kraków',
        4,
        '07:00:00',
        '23:00:00',
        180
    )
ON CONFLICT (id) DO UPDATE
SET
    name = EXCLUDED.name,
    code = EXCLUDED.code,
    address = EXCLUDED.address,
    floors_count = EXCLUDED.floors_count,
    laundry_opening_time = EXCLUDED.laundry_opening_time,
    laundry_closing_time = EXCLUDED.laundry_closing_time,
    laundry_slot_duration_minutes = EXCLUDED.laundry_slot_duration_minutes;

-- 3. Replace seed laundry machines (CASCADE clears any demo bookings on those machines)
DELETE FROM laundry_machines
WHERE dormitory_id IN (
    '11111111-1111-1111-1111-111111111111', -- DS-1
    '22222222-2222-2222-2222-222222222222', -- DS-2
    '33333333-3333-3333-3333-333333333333', -- DS-3
    '44444444-4444-4444-4444-444444444444'  -- DS-4
);

-- DS-1 Rumcajs — odd floors (1, 3) for floors_count = 4
INSERT INTO laundry_machines (id, dormitory_id, machine_identifier, floor_location, status, notes)
VALUES
    (
        'd1111111-1111-1111-1111-111111111111',
        '11111111-1111-1111-1111-111111111111',
        'Pralka p.1',
        'Piętro 1',
        'AVAILABLE',
        'Pralka na piętrze nieparzystym'
    ),
    (
        'd1111111-1111-1111-1111-111111111113',
        '11111111-1111-1111-1111-111111111111',
        'Pralka p.3',
        'Piętro 3',
        'AVAILABLE',
        'Pralka na piętrze nieparzystym'
    );

-- DS-2 Leon — odd floors (1, 3)
INSERT INTO laundry_machines (id, dormitory_id, machine_identifier, floor_location, status, notes)
VALUES
    (
        'd2222222-2222-2222-2222-222222222221',
        '22222222-2222-2222-2222-222222222222',
        'Pralka p.1',
        'Piętro 1',
        'AVAILABLE',
        'Pralka na piętrze nieparzystym'
    ),
    (
        'd2222222-2222-2222-2222-222222222223',
        '22222222-2222-2222-2222-222222222222',
        'Pralka p.3',
        'Piętro 3',
        'AVAILABLE',
        'Pralka na piętrze nieparzystym'
    );

-- DS-3 — odd floors (1, 3)
INSERT INTO laundry_machines (id, dormitory_id, machine_identifier, floor_location, status, notes)
VALUES
    (
        'd3333333-3333-3333-3333-333333333331',
        '33333333-3333-3333-3333-333333333333',
        'Pralka p.1',
        'Piętro 1',
        'AVAILABLE',
        'Pralka na piętrze nieparzystym'
    ),
    (
        'd3333333-3333-3333-3333-333333333333',
        '33333333-3333-3333-3333-333333333333',
        'Pralka p.3',
        'Piętro 3',
        'AVAILABLE',
        'Pralka na piętrze nieparzystym'
    );

-- DS-4 — every floor (1..4)
INSERT INTO laundry_machines (id, dormitory_id, machine_identifier, floor_location, status, notes)
VALUES
    (
        'd4444444-4444-4444-4444-444444444441',
        '44444444-4444-4444-4444-444444444444',
        'Pralka p.1',
        'Piętro 1',
        'AVAILABLE',
        'Pralka na każdym piętrze'
    ),
    (
        'd4444444-4444-4444-4444-444444444442',
        '44444444-4444-4444-4444-444444444444',
        'Pralka p.2',
        'Piętro 2',
        'AVAILABLE',
        'Pralka na każdym piętrze'
    ),
    (
        'd4444444-4444-4444-4444-444444444443',
        '44444444-4444-4444-4444-444444444444',
        'Pralka p.3',
        'Piętro 3',
        'AVAILABLE',
        'Pralka na każdym piętrze'
    ),
    (
        'd4444444-4444-4444-4444-444444444444',
        '44444444-4444-4444-4444-444444444444',
        'Pralka p.4',
        'Piętro 4',
        'AVAILABLE',
        'Pralka na każdym piętrze'
    );
