-- Allow MUST_CHANGE_PASSWORD for staff accounts created with a temporary password (FR-AUTH-05 / FR-AUTH-08).
ALTER TABLE users DROP CONSTRAINT IF EXISTS users_status_check;
ALTER TABLE users ADD CONSTRAINT users_status_check CHECK (
    status IN (
        'PENDING_EMAIL',
        'PENDING_APPROVAL',
        'MUST_CHANGE_PASSWORD',
        'ACTIVE',
        'BLOCKED',
        'CHECKED_OUT'
    )
);
