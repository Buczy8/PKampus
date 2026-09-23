-- Enforce a single active sanction per (user, type): two concurrent ROOM_BAN
-- requests must not both succeed (previously guarded only by an app-level check).
-- Keeps the latest active sanction per (user_id, sanction_type), deactivates older duplicates.

UPDATE sanctions SET is_active = FALSE
WHERE id IN (
    SELECT id FROM (
        SELECT id,
               ROW_NUMBER() OVER (
                   PARTITION BY user_id, sanction_type
                   ORDER BY end_date DESC, id DESC
               ) AS rn
        FROM sanctions
        WHERE is_active = TRUE
    ) ranked
    WHERE rn > 1
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_sanction_per_user
    ON sanctions (user_id, sanction_type) WHERE is_active = TRUE;
