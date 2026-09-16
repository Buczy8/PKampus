-- ==============================================================================
-- PKampus - Schemat Bazy Danych PostgreSQL 16 (01_init.sql / Flyway V1 Baseline)
-- Zgodny z dokumentacją Model_Bazy_Danych_ERD.md
-- ==============================================================================

-- Włączenie rozszerzenia do generowania UUID oraz indeksowania przedziałów czasowych GiST
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "btree_gist";

-- ------------------------------------------------------------------------------
-- 1. TABELA DOMÓW STUDENCKICH (Dormitories)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dormitories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    code VARCHAR(10) NOT NULL UNIQUE,
    address VARCHAR(255) NOT NULL,
    floors_count INT NOT NULL CHECK (floors_count > 0),
    laundry_opening_time TIME NOT NULL DEFAULT '07:00:00',
    laundry_closing_time TIME NOT NULL DEFAULT '23:00:00',
    laundry_slot_duration_minutes INT NOT NULL DEFAULT 90 CHECK (laundry_slot_duration_minutes > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------------------------
-- 2. TABELA POKOI (Rooms)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dormitory_id UUID NOT NULL REFERENCES dormitories(id) ON DELETE CASCADE,
    room_number VARCHAR(10) NOT NULL,
    floor INT NOT NULL,
    capacity INT NOT NULL DEFAULT 2 CHECK (capacity > 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_dormitory_room UNIQUE (dormitory_id, room_number)
);

-- ------------------------------------------------------------------------------
-- 3. TABELA UŻYTKOWNIKÓW (Users)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(150) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(50) NOT NULL,
    last_name VARCHAR(80) NOT NULL,
    phone_number VARCHAR(20) NOT NULL,
    avatar_url VARCHAR(500),
    role VARCHAR(30) NOT NULL CHECK (role IN ('RESIDENT', 'RECEPTIONIST', 'DORM_ADMIN', 'SUPER_ADMIN')),
    status VARCHAR(30) NOT NULL CHECK (status IN ('PENDING_EMAIL', 'PENDING_APPROVAL', 'ACTIVE', 'BLOCKED', 'CHECKED_OUT')),
    dormitory_id UUID REFERENCES dormitories(id) ON DELETE SET NULL,
    declared_room_number VARCHAR(10),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_user_dormitory_scope CHECK (
        (role = 'SUPER_ADMIN') OR (dormitory_id IS NOT NULL)
    )
);

-- ------------------------------------------------------------------------------
-- 4. TABELA HISTORII KWATERUNKU (Room Assignments)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS room_assignments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    room_id UUID NOT NULL REFERENCES rooms(id) ON DELETE RESTRICT,
    academic_year VARCHAR(9) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    check_in_date DATE NOT NULL,
    check_out_date DATE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Tylko jeden aktywny pokój dla studenta naraz
CREATE UNIQUE INDEX IF NOT EXISTS idx_active_user_assignment ON room_assignments (user_id) WHERE is_active = TRUE;

-- ------------------------------------------------------------------------------
-- 5. TABELA PRALEK (Laundry Machines)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS laundry_machines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dormitory_id UUID NOT NULL REFERENCES dormitories(id) ON DELETE CASCADE,
    machine_identifier VARCHAR(30) NOT NULL,
    floor_location VARCHAR(50) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'OUT_OF_ORDER')),
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------------------------
-- 6. TABELA REZERWACJI PRALNI (Laundry Bookings)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS laundry_bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    machine_id UUID NOT NULL REFERENCES laundry_machines(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED', 'KEY_ISSUED', 'COMPLETED', 'CANCELLED_USER', 'AUTO_CANCELLED_15MIN', 'CANCELLED_MACHINE_OUT_OF_ORDER')),
    key_issued_at TIMESTAMPTZ,
    key_returned_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_laundry_time CHECK (end_time > start_time)
);

-- Ochrona przed nakładaniem przedziałów czasowych w pralni (PostgreSQL btree_gist)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_laundry_no_overlap'
    ) THEN
        ALTER TABLE laundry_bookings 
            ADD CONSTRAINT chk_laundry_no_overlap 
            EXCLUDE USING gist (
                machine_id WITH =, 
                tstzrange(start_time, end_time) WITH &&
            ) WHERE (status IN ('CONFIRMED', 'KEY_ISSUED'));
    END IF;
END $$;

-- ------------------------------------------------------------------------------
-- 7. TABELA SALEK TEMATYCZNYCH (Thematic Rooms)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS thematic_rooms (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dormitory_id UUID NOT NULL REFERENCES dormitories(id) ON DELETE CASCADE,
    name VARCHAR(100) NOT NULL,
    room_type VARCHAR(30) NOT NULL CHECK (room_type IN ('STANDARD', 'QUIET_STUDY_KUJON', 'CHILLOUT')),
    max_capacity INT NOT NULL CHECK (max_capacity > 0),
    opening_time TIME NOT NULL DEFAULT '06:00:00',
    closing_time TIME NOT NULL DEFAULT '23:30:00',
    spans_midnight BOOLEAN NOT NULL DEFAULT FALSE,
    max_duration_hours INT NOT NULL DEFAULT 4 CHECK (max_duration_hours > 0),
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' CHECK (status IN ('AVAILABLE', 'MAINTENANCE')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_thematic_hours CHECK (
        (spans_midnight = FALSE AND closing_time > opening_time) OR
        (spans_midnight = TRUE AND closing_time < opening_time)
    )
);

-- ------------------------------------------------------------------------------
-- 8. TABELA REZERWACJI SALEK TEMATYCZNYCH (Room Bookings)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS room_bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    room_id UUID NOT NULL REFERENCES thematic_rooms(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    start_time TIMESTAMPTZ NOT NULL,
    end_time TIMESTAMPTZ NOT NULL,
    participants_count INT NOT NULL CHECK (participants_count > 0),
    purpose VARCHAR(255) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'CONFIRMED' CHECK (status IN ('CONFIRMED', 'KEY_ISSUED', 'COMPLETED', 'CANCELLED_USER', 'AUTO_CANCELLED_15MIN', 'CANCELLED_ROOM_MAINTENANCE')),
    terms_accepted BOOLEAN NOT NULL CHECK (terms_accepted = TRUE),
    key_issued_at TIMESTAMPTZ,
    key_returned_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_room_time CHECK (end_time > start_time)
);

-- Ochrona przed nakładaniem przedziałów czasowych w salkach (PostgreSQL btree_gist)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_room_no_overlap'
    ) THEN
        ALTER TABLE room_bookings 
            ADD CONSTRAINT chk_room_no_overlap 
            EXCLUDE USING gist (
                room_id WITH =, 
                tstzrange(start_time, end_time) WITH &&
            ) WHERE (status IN ('CONFIRMED', 'KEY_ISSUED'));
    END IF;
END $$;

-- ------------------------------------------------------------------------------
-- 9. TABELA USTEREK (Issues)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS issues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    dormitory_id UUID NOT NULL REFERENCES dormitories(id) ON DELETE CASCADE,
    room_id UUID REFERENCES rooms(id) ON DELETE RESTRICT,
    common_area_name VARCHAR(100),
    category VARCHAR(30) NOT NULL CHECK (category IN ('PLUMBING', 'ELECTRICAL', 'FURNITURE', 'LOCKSMITH', 'OTHER')),
    urgency VARCHAR(20) NOT NULL DEFAULT 'NORMAL' CHECK (urgency IN ('NORMAL', 'URGENT')),
    description TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'NEW' CHECK (status IN ('NEW', 'ASSIGNED_TO_MAINTENANCE', 'IN_PROGRESS', 'RESOLVED', 'REJECTED', 'PARTS_REQUIRED')),
    staff_notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_issue_location CHECK (
        (room_id IS NOT NULL AND common_area_name IS NULL) OR
        (room_id IS NULL AND common_area_name IS NOT NULL)
    )
);

-- ------------------------------------------------------------------------------
-- 10. TABELA ZDJĘĆ USTEREK (Issue Photos)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS issue_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    issue_id UUID NOT NULL REFERENCES issues(id) ON DELETE CASCADE,
    photo_url VARCHAR(500) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_size_bytes INT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------------------------
-- 11. TABELA POSTÓW SĄSIEDZKICH (Posts)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS posts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    dormitory_id UUID REFERENCES dormitories(id) ON DELETE SET NULL,
    title VARCHAR(150) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(30) NOT NULL CHECK (category IN ('BORROW_HELP', 'BUY_SELL', 'LOST_FOUND', 'GENERAL')),
    scope VARCHAR(20) NOT NULL DEFAULT 'DORMITORY' CHECK (scope IN ('DORMITORY', 'CAMPUS')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'RESOLVED', 'REMOVED_MODERATOR')),
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------------------------
-- 12. TABELA KOMENTARZY (Comments)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    content TEXT NOT NULL,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------------------------
-- 13. TABELA WYDARZEŃ I OFICJALNYCH KOMUNIKATÓW (Dorm Events)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS dorm_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    author_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    dormitory_id UUID REFERENCES dormitories(id) ON DELETE CASCADE,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(30) NOT NULL CHECK (category IN ('BED_LINEN', 'TECHNICAL_OUTAGE', 'ADMIN_NOTICE', 'STUDENT_EVENT')),
    priority VARCHAR(20) NOT NULL DEFAULT 'INFO' CHECK (priority IN ('INFO', 'WARNING', 'CRITICAL')),
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    event_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ------------------------------------------------------------------------------
-- 14. TABELA SANKCJI REGULAMINOWYCH (Sanctions)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sanctions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    issued_by_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    dormitory_id UUID NOT NULL REFERENCES dormitories(id) ON DELETE CASCADE,
    sanction_type VARCHAR(30) NOT NULL DEFAULT 'ROOM_BAN' CHECK (sanction_type IN ('ROOM_BAN')),
    reason TEXT NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sanction_dates CHECK (end_date >= start_date)
);

-- Indeks przyspieszający sprawdzanie aktywnych sankcji przy rezerwacji
CREATE INDEX IF NOT EXISTS idx_active_sanctions ON sanctions (user_id, end_date) WHERE is_active = TRUE;

-- ------------------------------------------------------------------------------
-- 15. TABELA TOKENÓW RESETOWANIA HASŁA (Password Reset Tokens)
-- ------------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indeks wyszukiwania aktywnego tokenu
CREATE INDEX IF NOT EXISTS idx_pwd_reset_token ON password_reset_tokens (token_hash) WHERE used_at IS NULL;
