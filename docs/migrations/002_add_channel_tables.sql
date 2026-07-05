-- Migration: Add Channel (Broadcast) tables and constraints

-- 1. Add check constraint to conversation_members role to enforce UPPERCASE roles
-- First, drop if exists to ensure idempotency (or in case we modify it later)
ALTER TABLE conversation_members DROP CONSTRAINT IF EXISTS conversation_members_role_check;

ALTER TABLE conversation_members
  ADD CONSTRAINT conversation_members_role_check
  CHECK (role IN ('OWNER', 'ADMIN', 'MEMBER'));

-- 2. Create message_post_views table for tracking view counts on channel messages
CREATE TABLE IF NOT EXISTS message_post_views (
  message_id  int8 PRIMARY KEY REFERENCES messages(id) ON DELETE CASCADE,
  view_count  int8 NOT NULL DEFAULT 0,
  updated_at  timestamptz DEFAULT now()
);

-- 3. Apply autovacuum tuning for high-update candidate table
ALTER TABLE message_post_views SET (
  autovacuum_vacuum_scale_factor = 0.02,
  autovacuum_vacuum_threshold = 100
);
