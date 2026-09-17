-- Migration V4: DS-2 dorm admin + extra rooms so residency activation works in demos

INSERT INTO rooms (id, dormitory_id, room_number, floor, capacity)
VALUES
    ('bbbbbbbb-3333-2222-2222-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', '201', 2, 2),
    ('bbbbbbbb-4444-2222-2222-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', '202', 2, 2),
    ('bbbbbbbb-5555-2222-2222-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', '211', 2, 2),
    ('bbbbbbbb-6666-2222-2222-bbbbbbbbbbbb', '22222222-2222-2222-2222-222222222222', '212', 2, 2)
ON CONFLICT (id) DO NOTHING;

-- password: Password123!
INSERT INTO users (id, email, password_hash, first_name, last_name, phone_number, role, status, dormitory_id, declared_room_number)
VALUES
    ('88888888-8888-8888-8888-888888888882', 'kierownik.ds2@pk.edu.pl', '$2a$10$8Q5o6LLPfW.Epw3QipUhMuGuQ3ixUK2aO9eMvXqrNdwEhw2TtILWO', 'Ewa', 'Kierownik', '+48123456783', 'DORM_ADMIN', 'ACTIVE', '22222222-2222-2222-2222-222222222222', NULL)
ON CONFLICT (id) DO NOTHING;
