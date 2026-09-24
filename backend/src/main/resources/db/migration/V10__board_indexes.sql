-- Indexes for community board performance and concurrency:
-- 1. Foreign keys to eliminate table locks on cascade / deletes and speed up joins
-- 2. Partial indexes for feed queries and active comments
-- 3. Indexes for retention purge tasks

-- Foreign key indexes (PostgreSQL does not index foreign keys by default)
CREATE INDEX IF NOT EXISTS idx_posts_author_id ON posts (author_id);
CREATE INDEX IF NOT EXISTS idx_posts_dormitory_id ON posts (dormitory_id);
CREATE INDEX IF NOT EXISTS idx_comments_author_id ON comments (author_id);
CREATE INDEX IF NOT EXISTS idx_comments_post_id ON comments (post_id);

-- Feed indexes: active posts sorted by creation date descending
CREATE INDEX IF NOT EXISTS idx_posts_active_feed 
    ON posts (created_at DESC, dormitory_id, scope, category, status) 
    WHERE is_deleted = FALSE AND status <> 'REMOVED_MODERATOR';

-- Active comments per post sorted by creation date ascending (supports findActiveByPostId & countActiveByPostIds)
CREATE INDEX IF NOT EXISTS idx_comments_active_post 
    ON comments (post_id, created_at ASC) 
    WHERE is_deleted = FALSE;

-- Retention purge indexes
CREATE INDEX IF NOT EXISTS idx_posts_retention_deleted 
    ON posts (deleted_at) 
    WHERE is_deleted = TRUE;

CREATE INDEX IF NOT EXISTS idx_posts_retention_resolved 
    ON posts (updated_at) 
    WHERE status = 'RESOLVED';

CREATE INDEX IF NOT EXISTS idx_comments_retention_deleted 
    ON comments (deleted_at) 
    WHERE is_deleted = TRUE;
