-- Optimistic locking for the community board: detect lost updates between
-- resident actions (resolve, soft-delete) and staff moderation.
-- Matches @Version mapping on posts/comments (ddl-auto: validate never alters schema).

ALTER TABLE posts ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE comments ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;
