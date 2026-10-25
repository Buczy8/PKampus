-- ==============================================================================
-- Migration V13: Presentation / demo catalogue + accounts (password: pkampus)
-- ==============================================================================
-- Idempotent upserts (safe if V2/V4/V5 already seeded the same UUIDs).
-- BCrypt cost 12 for plaintext "pkampus" (NFR-SEC-02):
--   $2a$12$lZOjyGrBCSFfcYz2rOj8DuoYW6oebSzeY.nXUI4wQ5CNDweJgYp4G
--
-- Logins (all password: pkampus):
--   admin@pk.edu.pl                         SUPER_ADMIN
--   kierownik.ds1..ds4@pk.edu.pl            DORM_ADMIN
--   portier.ds1..ds4@pk.edu.pl              RECEPTIONIST
--   student.kowalski|nowak@student.pk.edu.pl     RESIDENT DS-1
--   student.wisniewski|zielinska@student.pk.edu.pl RESIDENT DS-2
-- ==============================================================================

-- 1. Dormitories DS-1..DS-4
INSERT INTO dormitories (
    id, name, code, address, floors_count,
    laundry_opening_time, laundry_closing_time, laundry_slot_duration_minutes
)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'DS-1 Rumcajs', 'DS-1', 'ul. Skarżyńskiego 3, 31-866 Kraków', 4, '07:00:00', '23:00:00', 180),
    ('22222222-2222-2222-2222-222222222222', 'DS-2 Leon',    'DS-2', 'ul. Skarżyńskiego 5, 31-866 Kraków', 4, '07:00:00', '23:00:00', 180),
    ('33333333-3333-3333-3333-333333333333', 'DS-3',         'DS-3', 'ul. Skarżyńskiego 7, 31-866 Kraków', 4, '07:00:00', '23:00:00', 180),
    ('44444444-4444-4444-4444-444444444444', 'DS-4',         'DS-4', 'ul. Skarżyńskiego 9, 31-866 Kraków', 4, '07:00:00', '23:00:00', 180)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    code = EXCLUDED.code,
    address = EXCLUDED.address,
    floors_count = EXCLUDED.floors_count,
    laundry_opening_time = EXCLUDED.laundry_opening_time,
    laundry_closing_time = EXCLUDED.laundry_closing_time,
    laundry_slot_duration_minutes = EXCLUDED.laundry_slot_duration_minutes;

UPDATE dormitories
SET laundry_slot_duration_minutes = 180
WHERE id IN (
    '11111111-1111-1111-1111-111111111111',
    '22222222-2222-2222-2222-222222222222',
    '33333333-3333-3333-3333-333333333333',
    '44444444-4444-4444-4444-444444444444'
);

-- 2. Housing rooms
INSERT INTO rooms (id, dormitory_id, room_number, floor, capacity)
VALUES
    ('aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', '101', 1, 2),
    ('aaaaaaaa-2222-1111-1111-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', '102', 1, 2),
    ('aaaaaaaa-3333-1111-1111-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', '201', 2, 2),
    ('aaaaaaaa-4444-1111-1111-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', '301', 3, 2),
    ('bbbbbbbb-1111-2222-2222-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', '101', 1, 2),
    ('bbbbbbbb-2222-2222-2222-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', '102', 1, 2),
    ('bbbbbbbb-3333-2222-2222-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', '201', 2, 2),
    ('bbbbbbbb-4444-2222-2222-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', '202', 2, 2),
    ('bbbbbbbb-5555-2222-2222-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', '211', 2, 2),
    ('bbbbbbbb-6666-2222-2222-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', '212', 2, 2),
    ('cccccccc-1111-3333-3333-cccccccccccc', '33333333-3333-3333-3333-333333333333', '101', 1, 2),
    ('cccccccc-2222-3333-3333-cccccccccccc', '33333333-3333-3333-3333-333333333333', '102', 1, 2),
    ('cccccccc-3333-3333-3333-cccccccccccc', '33333333-3333-3333-3333-333333333333', '201', 2, 2),
    ('dddddddd-1111-4444-4444-dddddddddddd', '44444444-4444-4444-4444-444444444444', '101', 1, 2),
    ('dddddddd-2222-4444-4444-dddddddddddd', '44444444-4444-4444-4444-444444444444', '102', 1, 2),
    ('dddddddd-3333-4444-4444-dddddddddddd', '44444444-4444-4444-4444-444444444444', '201', 2, 2)
ON CONFLICT (id) DO UPDATE SET
    dormitory_id = EXCLUDED.dormitory_id,
    room_number  = EXCLUDED.room_number,
    floor        = EXCLUDED.floor,
    capacity     = EXCLUDED.capacity;

-- 3. Laundry machines (upsert — no DELETE, preserves existing bookings)
INSERT INTO laundry_machines (id, dormitory_id, machine_identifier, floor_location, status, notes)
VALUES
    ('d1111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Pralka p.1', 'Piętro 1', 'AVAILABLE', 'Demo'),
    ('d1111111-1111-1111-1111-111111111113', '11111111-1111-1111-1111-111111111111', 'Pralka p.3', 'Piętro 3', 'AVAILABLE', 'Demo'),
    ('d2222222-2222-2222-2222-222222222221', '22222222-2222-2222-2222-222222222222', 'Pralka p.1', 'Piętro 1', 'AVAILABLE', 'Demo'),
    ('d2222222-2222-2222-2222-222222222223', '22222222-2222-2222-2222-222222222222', 'Pralka p.3', 'Piętro 3', 'AVAILABLE', 'Demo'),
    ('d3333333-3333-3333-3333-333333333331', '33333333-3333-3333-3333-333333333333', 'Pralka p.1', 'Piętro 1', 'AVAILABLE', 'Demo'),
    ('d3333333-3333-3333-3333-333333333333', '33333333-3333-3333-3333-333333333333', 'Pralka p.3', 'Piętro 3', 'AVAILABLE', 'Demo'),
    ('d4444444-4444-4444-4444-444444444441', '44444444-4444-4444-4444-444444444444', 'Pralka p.1', 'Piętro 1', 'AVAILABLE', 'Demo'),
    ('d4444444-4444-4444-4444-444444444442', '44444444-4444-4444-4444-444444444444', 'Pralka p.2', 'Piętro 2', 'AVAILABLE', 'Demo'),
    ('d4444444-4444-4444-4444-444444444443', '44444444-4444-4444-4444-444444444444', 'Pralka p.3', 'Piętro 3', 'AVAILABLE', 'Demo'),
    ('d4444444-4444-4444-4444-444444444444', '44444444-4444-4444-4444-444444444444', 'Pralka p.4', 'Piętro 4', 'AVAILABLE', 'Demo')
ON CONFLICT (id) DO UPDATE SET
    machine_identifier = EXCLUDED.machine_identifier,
    floor_location     = EXCLUDED.floor_location,
    status             = EXCLUDED.status,
    notes              = EXCLUDED.notes;

-- 4. Thematic rooms (room_type dropped in V6)
INSERT INTO thematic_rooms (
    id, dormitory_id, name, max_capacity,
    opening_time, closing_time, spans_midnight, max_duration_hours, description, status
)
VALUES
    (
        'e1111111-1111-1111-1111-111111111111',
        '11111111-1111-1111-1111-111111111111',
        'Pokoik Cichej Nauki (Kujon)',
        8, '06:00:00', '23:30:00', false, 4,
        'Strefa ciszy, gniazdka 230V, szybkie Wi-Fi',
        'AVAILABLE'
    ),
    (
        'e2222222-1111-1111-1111-111111111111',
        '11111111-1111-1111-1111-111111111111',
        'Salka Bilardowa & Chillout',
        12, '08:00:00', '22:00:00', false, 3,
        'Stół bilardowy, sofa, gry planszowe',
        'AVAILABLE'
    ),
    (
        'e3333333-2222-2222-2222-222222222222',
        '22222222-2222-2222-2222-222222222222',
        'Sala nauki DS-2',
        10, '07:00:00', '23:00:00', false, 4,
        'Salek z projektorami',
        'AVAILABLE'
    )
ON CONFLICT (id) DO UPDATE SET
    name               = EXCLUDED.name,
    max_capacity       = EXCLUDED.max_capacity,
    opening_time       = EXCLUDED.opening_time,
    closing_time       = EXCLUDED.closing_time,
    spans_midnight     = EXCLUDED.spans_midnight,
    max_duration_hours = EXCLUDED.max_duration_hours,
    description        = EXCLUDED.description,
    status             = EXCLUDED.status;

-- 5. Users — password: pkampus
INSERT INTO users (
    id, email, password_hash, first_name, last_name, phone_number,
    role, status, dormitory_id, declared_room_number
)
VALUES
    (
        '99999999-9999-9999-9999-999999999999',
        'admin@pk.edu.pl',
        '$2a$12$lZOjyGrBCSFfcYz2rOj8DuoYW6oebSzeY.nXUI4wQ5CNDweJgYp4G',
        'Główny', 'Administrator', '+48123456780',
        'SUPER_ADMIN', 'ACTIVE', NULL, NULL
    ),
    (
        '88888888-8888-8888-8888-888888888888',
        'kierownik.ds1@pk.edu.pl',
        '$2a$12$lZOjyGrBCSFfcYz2rOj8DuoYW6oebSzeY.nXUI4wQ5CNDweJgYp4G',
        'Marian', 'Kierownik', '+48123456781',
        'DORM_ADMIN', 'ACTIVE', '11111111-1111-1111-1111-111111111111', NULL
    ),
    (
        '88888888-8888-8888-8888-888888888882',
        'kierownik.ds2@pk.edu.pl',
        '$2a$12$lZOjyGrBCSFfcYz2rOj8DuoYW6oebSzeY.nXUI4wQ5CNDweJgYp4G',
        'Ewa', 'Kierownik', '+48123456783',
        'DORM_ADMIN', 'ACTIVE', '22222222-2222-2222-2222-222222222222', NULL
    ),
    (
        '88888888-8888-8888-8888-888888888883',
        'kierownik.ds3@pk.edu.pl',
        '$2a$12$lZOjyGrBCSFfcYz2rOj8DuoYW6oebSzeY.nXUI4wQ5CNDweJgYp4G',
        'Piotr', 'Kierownik', '+48123456784',
        'DORM_ADMIN', 'ACTIVE', '33333333-3333-3333-3333-333333333333', NULL
    ),
    (
        '88888888-8888-8888-8888-888888888884',
        'kierownik.ds4@pk.edu.pl',
        '$2a$12$lZOjyGrBCSFfcYz2rOj8DuoYW6oebSzeY.nXUI4wQ5CNDweJgYp4G',
        'Anna', 'Kierownik', '+48123456785',
        'DORM_ADMIN', 'ACTIVE', '44444444-4444-4444-4444-444444444444', NULL
    ),
    (
        '77777777-7777-7777-7777-777777777777',
        'portier.ds1@pk.edu.pl',
        '$2a$12$lZOjyGrBCSFfcYz2rOj8DuoYW6oebSzeY.nXUI4wQ5CNDweJgYp4G',
        'Stanisław', 'Portier', '+48123456782',
        'RECEPTIONIST', 'ACTIVE', '11111111-1111-1111-1111-111111111111', NULL
    ),
    (
        '77777777-7777-7777-7777-777777777772',
        'portier.ds2@pk.edu.pl',
        '$2a$12$lZOjyGrBCSFfcYz2rOj8DuoYW6oebSzeY.nXUI4wQ5CNDweJgYp4G',
        'Tomasz', 'Portier', '+48123456786',
        'RECEPTIONIST', 'ACTIVE', '22222222-2222-2222-2222-222222222222', NULL
    ),
    (
        '77777777-7777-7777-7777-777777777773',
        'portier.ds3@pk.edu.pl',
        '$2a$12$lZOjyGrBCSFfcYz2rOj8DuoYW6oebSzeY.nXUI4wQ5CNDweJgYp4G',
        'Krzysztof', 'Portier', '+48123456787',
        'RECEPTIONIST', 'ACTIVE', '33333333-3333-3333-3333-333333333333', NULL
    ),
    (
        '77777777-7777-7777-7777-777777777774',
        'portier.ds4@pk.edu.pl',
        '$2a$12$lZOjyGrBCSFfcYz2rOj8DuoYW6oebSzeY.nXUI4wQ5CNDweJgYp4G',
        'Magdalena', 'Portier', '+48123456788',
        'RECEPTIONIST', 'ACTIVE', '44444444-4444-4444-4444-444444444444', NULL
    ),
    (
        '55555555-5555-5555-5555-555555555555',
        'student.kowalski@student.pk.edu.pl',
        '$2a$12$lZOjyGrBCSFfcYz2rOj8DuoYW6oebSzeY.nXUI4wQ5CNDweJgYp4G',
        'Jan', 'Kowalski', '+48600100200',
        'RESIDENT', 'ACTIVE', '11111111-1111-1111-1111-111111111111', '101'
    ),
    (
        '66666666-6666-6666-6666-666666666666',
        'student.nowak@student.pk.edu.pl',
        '$2a$12$lZOjyGrBCSFfcYz2rOj8DuoYW6oebSzeY.nXUI4wQ5CNDweJgYp4G',
        'Anna', 'Nowak', '+48600300400',
        'RESIDENT', 'ACTIVE', '11111111-1111-1111-1111-111111111111', '102'
    ),
    (
        '55555555-5555-5555-5555-555555555552',
        'student.wisniewski@student.pk.edu.pl',
        '$2a$12$lZOjyGrBCSFfcYz2rOj8DuoYW6oebSzeY.nXUI4wQ5CNDweJgYp4G',
        'Marek', 'Wiśniewski', '+48600500600',
        'RESIDENT', 'ACTIVE', '22222222-2222-2222-2222-222222222222', '101'
    ),
    (
        '55555555-5555-5555-5555-555555555553',
        'student.zielinska@student.pk.edu.pl',
        '$2a$12$lZOjyGrBCSFfcYz2rOj8DuoYW6oebSzeY.nXUI4wQ5CNDweJgYp4G',
        'Karolina', 'Zielińska', '+48600700800',
        'RESIDENT', 'ACTIVE', '22222222-2222-2222-2222-222222222222', '201'
    )
ON CONFLICT (id) DO UPDATE SET
    email                = EXCLUDED.email,
    password_hash        = EXCLUDED.password_hash,
    first_name           = EXCLUDED.first_name,
    last_name            = EXCLUDED.last_name,
    phone_number         = EXCLUDED.phone_number,
    role                 = EXCLUDED.role,
    status               = 'ACTIVE',
    dormitory_id         = EXCLUDED.dormitory_id,
    declared_room_number = EXCLUDED.declared_room_number,
    updated_at           = CURRENT_TIMESTAMP;

-- 6. Active room assignments (2026/2027)
INSERT INTO room_assignments (id, user_id, room_id, academic_year, is_active, check_in_date)
VALUES
    (
        'c1111111-1111-1111-1111-111111111111',
        '55555555-5555-5555-5555-555555555555',
        'aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa',
        '2026/2027', true, '2026-10-01'
    ),
    (
        'c2222222-2222-2222-2222-222222222222',
        '66666666-6666-6666-6666-666666666666',
        'aaaaaaaa-2222-1111-1111-aaaaaaaaaaaa',
        '2026/2027', true, '2026-10-01'
    ),
    (
        'c3333333-3333-3333-3333-333333333333',
        '55555555-5555-5555-5555-555555555552',
        'bbbbbbbb-1111-2222-2222-bbbbbbbbbbbb',
        '2026/2027', true, '2026-10-01'
    ),
    (
        'c4444444-4444-4444-4444-444444444444',
        '55555555-5555-5555-5555-555555555553',
        'bbbbbbbb-3333-2222-2222-bbbbbbbbbbbb',
        '2026/2027', true, '2026-10-01'
    )
ON CONFLICT (id) DO UPDATE SET
    user_id        = EXCLUDED.user_id,
    room_id        = EXCLUDED.room_id,
    academic_year  = EXCLUDED.academic_year,
    is_active      = TRUE,
    check_in_date  = EXCLUDED.check_in_date,
    check_out_date = NULL;
