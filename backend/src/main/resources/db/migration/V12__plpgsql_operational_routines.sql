-- ==============================================================================
-- PKampus - V12: PL/pgSQL operational routines (last-line integrity + cleanup)
-- Conforming to ADR-02 (DB as final enforcer) and Model_Bazy_Danych_ERD.md
--
-- Java service validation stays primary (nice ApiResponse errors, timezone grid
-- logic, mail side-effects). These routines are defense-in-depth: they fire on
-- race conditions, admin hour changes, and direct SQL bypassing JPA.
-- H2 integration tests (flyway.enabled: false, create-drop) do not execute
-- this file; verify on real PostgreSQL 16 (docker compose up -d).
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. touch_updated_at() — auto-touch audit column on UPDATE
-- Covers every table carrying updated_at: users, issues, posts.
-- Explicit changes are respected (IF ... IS NOT DISTINCT FROM) so retention
-- backfills and data fixes setting updated_at directly are not clobbered;
-- only caller-untouched rows get now(). Replaces reliance on Hibernate
-- @UpdateTimestamp for raw SQL / psql writers.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION touch_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    IF NEW.updated_at IS NOT DISTINCT FROM OLD.updated_at THEN
        NEW.updated_at := now();
    END IF;
    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION touch_updated_at() IS
    'Auto-touch updated_at on UPDATE when the writer did not set it explicitly (users, issues, posts).';

DROP TRIGGER IF EXISTS trg_users_touch_updated_at ON users;
CREATE TRIGGER trg_users_touch_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

DROP TRIGGER IF EXISTS trg_issues_touch_updated_at ON issues;
CREATE TRIGGER trg_issues_touch_updated_at
    BEFORE UPDATE ON issues
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

DROP TRIGGER IF EXISTS trg_posts_touch_updated_at ON posts;
CREATE TRIGGER trg_posts_touch_updated_at
    BEFORE UPDATE ON posts
    FOR EACH ROW EXECUTE FUNCTION touch_updated_at();

-- ------------------------------------------------------------------------------
-- 2a. check_laundry_booking_window() — enforce dormitory laundry window in DB
-- Mirrors LaundryService / LaundryBookingValidator.validateSlotOnGrid +
-- validateSlotDuration (Europe/Warsaw, single calendar day, exact slot
-- duration, grid alignment). Overlap stays with chk_laundry_no_overlap
-- (EXCLUDE USING gist, ADR-02); this trigger covers the window half.
-- Raises P0001 with prefix 'laundry_booking_window:' so the service layer
-- can translate it to 422 (BusinessRule) instead of 409 (SlotConflict).
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION check_laundry_booking_window()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_opening              TIME;
    v_closing              TIME;
    v_duration_minutes     INT;
    v_start_time           TIME;
    v_end_time             TIME;
    v_start_date           DATE;
    v_end_date             DATE;
    v_minutes_from_opening INT;
BEGIN
    SELECT d.laundry_opening_time, d.laundry_closing_time, d.laundry_slot_duration_minutes
      INTO v_opening, v_closing, v_duration_minutes
      FROM laundry_machines m
      JOIN dormitories d ON d.id = m.dormitory_id
     WHERE m.id = NEW.machine_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'laundry_booking_window: laundry machine % not found', NEW.machine_id;
    END IF;

    IF NEW.end_time IS NULL OR NEW.start_time IS NULL OR NEW.end_time <= NEW.start_time THEN
        RAISE EXCEPTION 'laundry_booking_window: end_time must be after start_time';
    END IF;

    IF (NEW.end_time - NEW.start_time) <> make_interval(mins => v_duration_minutes) THEN
        RAISE EXCEPTION 'laundry_booking_window: duration must equal slot duration (% minutes)', v_duration_minutes;
    END IF;

    v_start_time := (NEW.start_time AT TIME ZONE 'Europe/Warsaw')::time;
    v_end_time   := (NEW.end_time AT TIME ZONE 'Europe/Warsaw')::time;
    v_start_date := (NEW.start_time AT TIME ZONE 'Europe/Warsaw')::date;
    v_end_date   := (NEW.end_time AT TIME ZONE 'Europe/Warsaw')::date;

    IF v_start_date <> v_end_date THEN
        RAISE EXCEPTION 'laundry_booking_window: slots must stay within a single calendar day';
    END IF;

    IF v_start_time < v_opening THEN
        RAISE EXCEPTION 'laundry_booking_window: slot starts before opening hours (%)', v_opening;
    END IF;

    IF v_end_time > v_closing THEN
        RAISE EXCEPTION 'laundry_booking_window: slot ends after closing hours (%)', v_closing;
    END IF;

    v_minutes_from_opening := floor(extract(epoch FROM (v_start_time - v_opening)) / 60)::int;
    IF v_minutes_from_opening < 0 OR (v_minutes_from_opening % v_duration_minutes) <> 0 THEN
        RAISE EXCEPTION 'laundry_booking_window: slot start is not aligned to the slot grid';
    END IF;

    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION check_laundry_booking_window() IS
    'Enforce dormitory laundry opening window, exact slot duration and grid alignment (Europe/Warsaw). Last-line mirror of LaundryBookingValidator.';

DROP TRIGGER IF EXISTS trg_laundry_booking_window ON laundry_bookings;
CREATE TRIGGER trg_laundry_booking_window
    BEFORE INSERT OR UPDATE OF machine_id, start_time, end_time ON laundry_bookings
    FOR EACH ROW EXECUTE FUNCTION check_laundry_booking_window();

-- ------------------------------------------------------------------------------
-- 2b. check_room_booking_window() — enforce thematic-room opening window in DB
-- Mirrors RoomBookingService.validateOpeningWindow + validateDuration +
-- validateWholeHours (Europe/Warsaw, spans_midnight sessions, 1h minimum,
-- max_duration_hours cap, whole-hour grid). Overlap stays with
-- chk_room_no_overlap (EXCLUDE USING gist, ADR-02).
-- Raises P0001 with prefix 'room_booking_window:' for 422 translation.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION check_room_booking_window()
RETURNS trigger
LANGUAGE plpgsql
AS $$
DECLARE
    v_opening      TIME;
    v_closing      TIME;
    v_spans        BOOLEAN;
    v_max_hours    INT;
    v_duration     INTERVAL;
    v_start_ts     TIMESTAMP;
    v_end_ts       TIMESTAMP;
    v_start_t      TIME;
    v_end_t        TIME;
    v_start_d      DATE;
    v_end_d        DATE;
    v_session_d    DATE;
    v_window_start TIMESTAMPTZ;
    v_window_end   TIMESTAMPTZ;
BEGIN
    SELECT opening_time, closing_time, spans_midnight, max_duration_hours
      INTO v_opening, v_closing, v_spans, v_max_hours
      FROM thematic_rooms
     WHERE id = NEW.room_id;

    IF NOT FOUND THEN
        RAISE EXCEPTION 'room_booking_window: thematic room % not found', NEW.room_id;
    END IF;

    IF NEW.end_time IS NULL OR NEW.start_time IS NULL OR NEW.end_time <= NEW.start_time THEN
        RAISE EXCEPTION 'room_booking_window: end_time must be after start_time';
    END IF;

    v_duration := NEW.end_time - NEW.start_time;
    IF v_duration < INTERVAL '1 hour' THEN
        RAISE EXCEPTION 'room_booking_window: reservation must be at least 1 hour';
    END IF;
    IF v_duration > make_interval(hours => v_max_hours) THEN
        RAISE EXCEPTION 'room_booking_window: duration exceeds max % hours for this room', v_max_hours;
    END IF;

    v_start_ts := NEW.start_time AT TIME ZONE 'Europe/Warsaw';
    v_end_ts   := NEW.end_time AT TIME ZONE 'Europe/Warsaw';
    v_start_t  := v_start_ts::time;
    v_end_t    := v_end_ts::time;
    v_start_d  := v_start_ts::date;
    v_end_d    := v_end_ts::date;

    IF extract(minute FROM v_start_ts) <> 0 OR extract(second FROM v_start_ts) <> 0
       OR extract(minute FROM v_end_ts) <> 0 OR extract(second FROM v_end_ts) <> 0 THEN
        RAISE EXCEPTION 'room_booking_window: reservations must start and end on the hour (HH:00)';
    END IF;

    IF NOT v_spans THEN
        IF v_start_d <> v_end_d THEN
            RAISE EXCEPTION 'room_booking_window: reservation must stay within a single calendar day';
        END IF;
        IF v_start_t < v_opening THEN
            RAISE EXCEPTION 'room_booking_window: reservation starts before opening hours (%)', v_opening;
        END IF;
        IF v_end_t > v_closing THEN
            RAISE EXCEPTION 'room_booking_window: reservation ends after closing hours (%)', v_closing;
        END IF;
        RETURN NEW;
    END IF;

    -- spans_midnight: window = [opening on sessionDay, closing on sessionDay+1]
    IF v_start_t >= v_opening THEN
        v_session_d := v_start_d;
    ELSIF v_start_t < v_closing THEN
        v_session_d := v_start_d - 1;
    ELSE
        RAISE EXCEPTION 'room_booking_window: reservation starts outside opening hours';
    END IF;

    v_window_start := (v_session_d + v_opening) AT TIME ZONE 'Europe/Warsaw';
    v_window_end   := ((v_session_d + 1) + v_closing) AT TIME ZONE 'Europe/Warsaw';

    IF NEW.start_time < v_window_start OR NEW.end_time > v_window_end THEN
        RAISE EXCEPTION 'room_booking_window: reservation must fit within opening hours (% - % next day)', v_opening, v_closing;
    END IF;

    RETURN NEW;
END;
$$;

COMMENT ON FUNCTION check_room_booking_window() IS
    'Enforce thematic-room opening window, whole-hour grid and max duration (Europe/Warsaw, spans_midnight aware). Last-line mirror of RoomBookingService.';

DROP TRIGGER IF EXISTS trg_room_booking_window ON room_bookings;
CREATE TRIGGER trg_room_booking_window
    BEFORE INSERT OR UPDATE OF room_id, start_time, end_time ON room_bookings
    FOR EACH ROW EXECUTE FUNCTION check_room_booking_window();

-- ------------------------------------------------------------------------------
-- 3. purge_expired_tokens() — atomic bulk cleanup of auth token tables
-- DB-level equivalent of RefreshTokenCleanupJob + PasswordResetTokenRepository:
--   refresh_tokens: (revoked AND created_at < p_refresh_revoked_before)
--                   OR (expires_at < p_refresh_expired_before)
--   password_reset_tokens: used_at IS NOT NULL OR expires_at < p_reset_cutoff
-- Cutoffs stay caller-supplied so @Value TTLs in Java remain authoritative.
-- App scheduler remains the caller of record (keeps logs/metrics); this
-- function gives a single-round-trip atomic path and a pg_cron entry point
-- for environments where the app scheduler is down.
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION purge_expired_tokens(
    p_refresh_revoked_before TIMESTAMPTZ,
    p_refresh_expired_before TIMESTAMPTZ,
    p_reset_cutoff TIMESTAMPTZ
)
RETURNS TABLE (refresh_deleted INTEGER, reset_deleted INTEGER)
LANGUAGE plpgsql
AS $$
DECLARE
    v_refresh INT := 0;
    v_reset   INT := 0;
BEGIN
    DELETE FROM refresh_tokens
     WHERE (revoked AND created_at < p_refresh_revoked_before)
        OR (expires_at < p_refresh_expired_before);
    GET DIAGNOSTICS v_refresh = ROW_COUNT;

    DELETE FROM password_reset_tokens
     WHERE used_at IS NOT NULL
        OR expires_at < p_reset_cutoff;
    GET DIAGNOSTICS v_reset = ROW_COUNT;

    RETURN QUERY SELECT v_refresh, v_reset;
END;
$$;

COMMENT ON FUNCTION purge_expired_tokens(TIMESTAMPTZ, TIMESTAMPTZ, TIMESTAMPTZ) IS
    'Atomic bulk delete of stale refresh_tokens and password_reset_tokens. Cutoffs supplied by caller (RefreshTokenCleanupJob).';
