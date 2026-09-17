-- ==============================================================================
-- Migration V2: Seed test data for development & demonstration
-- ==============================================================================

-- 1. Dormitories
INSERT INTO dormitories (id, name, code, address, floors_count, laundry_opening_time, laundry_closing_time, laundry_slot_duration_minutes)
VALUES
    ('11111111-1111-1111-1111-111111111111', 'DS-1 Rumcajs', 'DS-1', 'ul. Skarżyńskiego 3, 31-866 Kraków', 4, '07:00:00', '23:00:00', 90),
    ('22222222-2222-2222-2222-222222222222', 'DS-2 Leon', 'DS-2', 'ul. Skarżyńskiego 5, 31-866 Kraków', 4, '07:00:00', '23:00:00', 90)
ON CONFLICT (id) DO NOTHING;

-- 2. Rooms
INSERT INTO rooms (id, dormitory_id, room_number, floor, capacity)
VALUES
    ('aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', '101', 1, 2),
    ('aaaaaaaa-2222-1111-1111-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', '102', 1, 2),
    ('aaaaaaaa-3333-1111-1111-aaaaaaaaaaaa', '11111111-1111-1111-1111-111111111111', '201', 2, 2),
    ('bbbbbbbb-1111-2222-2222-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', '101', 1, 2),
    ('bbbbbbbb-2222-2222-2222-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', '102', 1, 2)
ON CONFLICT (id) DO NOTHING;

-- 3. Users (password: Password123!)
INSERT INTO users (id, email, password_hash, first_name, last_name, phone_number, role, status, dormitory_id, declared_room_number)
VALUES
    ('99999999-9999-9999-9999-999999999999', 'admin@pk.edu.pl', '$2a$10$8Q5o6LLPfW.Epw3QipUhMuGuQ3ixUK2aO9eMvXqrNdwEhw2TtILWO', 'Główny', 'Administrator', '+48123456780', 'SUPER_ADMIN', 'ACTIVE', NULL, NULL),
    ('88888888-8888-8888-8888-888888888888', 'kierownik.ds1@pk.edu.pl', '$2a$10$8Q5o6LLPfW.Epw3QipUhMuGuQ3ixUK2aO9eMvXqrNdwEhw2TtILWO', 'Marian', 'Kierownik', '+48123456781', 'DORM_ADMIN', 'ACTIVE', '11111111-1111-1111-1111-111111111111', NULL),
    ('77777777-7777-7777-7777-777777777777', 'portier.ds1@pk.edu.pl', '$2a$10$8Q5o6LLPfW.Epw3QipUhMuGuQ3ixUK2aO9eMvXqrNdwEhw2TtILWO', 'Stanisław', 'Portier', '+48123456782', 'RECEPTIONIST', 'ACTIVE', '11111111-1111-1111-1111-111111111111', NULL),
    ('55555555-5555-5555-5555-555555555555', 'student.kowalski@student.pk.edu.pl', '$2a$10$8Q5o6LLPfW.Epw3QipUhMuGuQ3ixUK2aO9eMvXqrNdwEhw2TtILWO', 'Jan', 'Kowalski', '+48600100200', 'RESIDENT', 'ACTIVE', '11111111-1111-1111-1111-111111111111', '101'),
    ('66666666-6666-6666-6666-666666666666', 'student.nowak@student.pk.edu.pl', '$2a$10$8Q5o6LLPfW.Epw3QipUhMuGuQ3ixUK2aO9eMvXqrNdwEhw2TtILWO', 'Anna', 'Nowak', '+48600300400', 'RESIDENT', 'ACTIVE', '11111111-1111-1111-1111-111111111111', '102')
ON CONFLICT (id) DO NOTHING;

-- 4. Room Assignments
INSERT INTO room_assignments (id, user_id, room_id, academic_year, is_active, check_in_date)
VALUES
    ('c1111111-1111-1111-1111-111111111111', '55555555-5555-5555-5555-555555555555', 'aaaaaaaa-1111-1111-1111-aaaaaaaaaaaa', '2025/2026', true, '2025-10-01'),
    ('c2222222-2222-2222-2222-222222222222', '66666666-6666-6666-6666-666666666666', 'aaaaaaaa-2222-1111-1111-aaaaaaaaaaaa', '2025/2026', true, '2025-10-01')
ON CONFLICT (id) DO NOTHING;

-- 5. Laundry Machines
INSERT INTO laundry_machines (id, dormitory_id, machine_identifier, floor_location, status, notes)
VALUES
    ('d1111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Pralka 1', 'Parter (Pralnia główna)', 'AVAILABLE', 'Wsyp proszku po lewej stronie'),
    ('d2222222-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Pralka 2', 'Parter (Pralnia główna)', 'AVAILABLE', 'Pojemność 7kg'),
    ('d3333333-2222-2222-2222-222222222222', '22222222-2222-2222-2222-222222222222', 'Pralka 1', 'Piwnica (Pralnia B)', 'AVAILABLE', 'Nowy bęben')
ON CONFLICT (id) DO NOTHING;

-- 6. Thematic Rooms
INSERT INTO thematic_rooms (id, dormitory_id, name, room_type, max_capacity, opening_time, closing_time, spans_midnight, max_duration_hours, description, status)
VALUES
    ('e1111111-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Pokoik Cichej Nauki (Kujon)', 'QUIET_STUDY_KUJON', 8, '06:00:00', '23:30:00', false, 4, 'Strefa ciszy, gniazdka 230V przy każdym biurku, szybkie Wi-Fi', 'AVAILABLE'),
    ('e2222222-1111-1111-1111-111111111111', '11111111-1111-1111-1111-111111111111', 'Salka Bilardowa & Chillout', 'CHILLOUT', 12, '08:00:00', '22:00:00', false, 3, 'Stół bilardowy, sofa, gry planszowe', 'AVAILABLE')
ON CONFLICT (id) DO NOTHING;
