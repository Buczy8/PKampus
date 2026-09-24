-- Fix feed index column order: equality columns first, range/sort column last.
-- V10 placed created_at (range + ORDER BY) before equality filters
-- (dormitory_id, scope, category, status), limiting index usability.
DROP INDEX IF EXISTS idx_posts_active_feed;
CREATE INDEX IF NOT EXISTS idx_posts_active_feed
    ON posts (dormitory_id, scope, category, status, created_at DESC)
    WHERE is_deleted = FALSE AND status <> 'REMOVED_MODERATOR';
