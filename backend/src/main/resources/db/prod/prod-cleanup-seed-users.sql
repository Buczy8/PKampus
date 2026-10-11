# ==============================================================================
# PKampus - one-off PROD cleanup: remove dev/test seed users (Supabase)
# ==============================================================================
# Run ONCE on the Supabase prod database AFTER `flyway migrate` (V1..V12),
# BEFORE opening registration. Dev databases (docker-compose) must NOT run this
# — demo logins (admin@pk.edu.pl / Password123! etc.) live there on purpose.
#
# Why a manual script and not Flyway V13: a V13 DELETE would also wipe dev/demo
# data on every developer machine (V2/V4 use fixed UUIDs + ON CONFLICT DO
# NOTHING, so deleted rows would never come back without a DB wipe). Schema
# history stays identical (V1..V12) on dev and prod; this removes DATA only.
#
# Usage (Supabase SQL editor or psql on the DIRECT 5432 connection):
#   \i backend/src/main/resources/db/prod/prod-cleanup-seed-users.sql
#
# What stays: dormitories DS-1..DS-4, rooms, laundry machines, thematic rooms
# (real catalogue data from V2/V4/V5). What goes: known seed users + their
# room assignments (CASCADE) listed below.
# ==============================================================================

BEGIN;

-- Room assignments of seed users (explicit first for clarity; users CASCADE too)
DELETE FROM room_assignments WHERE user_id IN (
    '99999999-9999-9999-9999-999999999999', -- SUPER_ADMIN admin@pk.edu.pl (V2)
    '88888888-8888-8888-8888-888888888888', -- DORM_ADMIN kierownik.ds1 (V2)
    '88888888-8888-8888-8888-888888888882', -- DORM_ADMIN kierownik.ds2 (V4)
    '77777777-7777-7777-7777-777777777777', -- RECEPTIONIST portier.ds1 (V2)
    '55555555-5555-5555-5555-555555555555', -- RESIDENT student.kowalski (V2)
    '66666666-6666-6666-6666-666666666666'  -- RESIDENT student.nowak (V2)
);

-- Seed users (known dev password hash Password123! — must not exist on prod)
DELETE FROM users WHERE id IN (
    '99999999-9999-9999-9999-999999999999',
    '88888888-8888-8888-8888-888888888888',
    '88888888-8888-8888-8888-888888888882',
    '77777777-7777-7777-7777-777777777777',
    '55555555-5555-5555-5555-555555555555',
    '66666666-6666-6666-6666-666666666666'
);

-- Safety net: any leftover user with the public dev password hash
-- ($2a$10$8Q5o6LLPfW.Epw3QipUhMuGuQ3ixUK2aO9eMvXqrNdwEhw2TtILWO = Password123!)
-- Remove this block if prod already has real users sharing the hash by accident
-- (should never happen — real passwords are always freshly hashed).
DELETE FROM users
WHERE password_hash = '$2a$10$8Q5o6LLPfW.Epw3QipUhMuGuQ3ixUK2aO9eMvXqrNdwEhw2TtILWO';

COMMIT;

-- Verify: expect 0 rows on prod
-- SELECT count(*) AS remaining_seed_users FROM users WHERE email LIKE '%@pk.edu.pl' OR email LIKE '%@student.pk.edu.pl';
