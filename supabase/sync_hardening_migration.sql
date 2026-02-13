-- ============================================
-- PHASE 2: Server-Side Idempotency & Deduplication
-- ============================================
-- Run this in Supabase SQL Editor
-- This migration adds duplicate prevention and data validation

-- ============================================
-- STEP 1: Check for existing duplicates
-- ============================================
-- Run this first to see if you have duplicates
-- If this returns rows, you need to clean them up before adding the constraint

SELECT user_id, timestamp, latitude, longitude, COUNT(*) as count
FROM locations
GROUP BY user_id, timestamp, latitude, longitude
HAVING COUNT(*) > 1;

-- ============================================
-- STEP 2: Clean up duplicates (if any found)
-- ============================================
-- Only run this if Step 1 found duplicates
-- This keeps the first occurrence and deletes the rest

/*
DELETE FROM locations 
WHERE id IN (
    SELECT id FROM (
        SELECT id, 
               ROW_NUMBER() OVER (
                   PARTITION BY user_id, timestamp, latitude, longitude 
                   ORDER BY id
               ) as rn
        FROM locations
    ) t 
    WHERE rn > 1
);
*/

-- ============================================
-- STEP 3: Add unique constraint (duplicate prevention)
-- ============================================
-- This is the core idempotency mechanism
-- Same location at same time = duplicate (rejected by DB)
-- Client can safely retry, server rejects duplicates

ALTER TABLE locations 
ADD CONSTRAINT unique_location_point 
UNIQUE (user_id, timestamp, latitude, longitude);

-- ============================================
-- STEP 4: Add deduplication index
-- ============================================
-- Speeds up duplicate checks during inserts

CREATE INDEX IF NOT EXISTS idx_locations_dedup 
ON locations(user_id, timestamp, latitude, longitude);

-- ============================================
-- STEP 5: Add validation constraints
-- ============================================
-- Prevent invalid GPS coordinates and accuracy values

ALTER TABLE locations 
ADD CONSTRAINT valid_latitude 
CHECK (latitude >= -90 AND latitude <= 90);

ALTER TABLE locations 
ADD CONSTRAINT valid_longitude 
CHECK (longitude >= -180 AND longitude <= 180);

ALTER TABLE locations 
ADD CONSTRAINT valid_accuracy 
CHECK (accuracy > 0 AND accuracy < 10000);

-- ============================================
-- STEP 6: Update last_synced_at trigger
-- ============================================
-- Only update on successful inserts (not duplicates)
-- This ensures admin sees accurate "last new location" time

DROP TRIGGER IF EXISTS trigger_update_last_sync ON locations;

CREATE OR REPLACE FUNCTION update_user_last_sync()
RETURNS TRIGGER AS $$
BEGIN
  -- Only update if this is a NEW location (not a duplicate)
  -- Trigger only fires on INSERT, so NEW is always the inserted row
  UPDATE users 
  SET last_synced_at = NEW.timestamp 
  WHERE id = NEW.user_id 
    AND (last_synced_at IS NULL OR NEW.timestamp > last_synced_at);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_last_sync
AFTER INSERT ON locations
FOR EACH ROW
EXECUTE FUNCTION update_user_last_sync();

-- ============================================
-- STEP 7: Verify constraints
-- ============================================
-- Check that all constraints are in place

SELECT 
    conname AS constraint_name,
    contype AS constraint_type,
    pg_get_constraintdef(oid) AS definition
FROM pg_constraint
WHERE conrelid = 'locations'::regclass
ORDER BY conname;

-- ============================================
-- STEP 8: Test duplicate prevention
-- ============================================
-- Try inserting a duplicate (should fail with unique constraint violation)

/*
-- First insert (should succeed)
INSERT INTO locations (id, user_id, latitude, longitude, accuracy, timestamp)
VALUES (
    gen_random_uuid(),
    (SELECT id FROM users WHERE role = 'employee' LIMIT 1),
    28.6139,
    77.2090,
    10.0,
    '2026-02-12T10:00:00Z'
);

-- Duplicate insert (should fail)
INSERT INTO locations (id, user_id, latitude, longitude, accuracy, timestamp)
VALUES (
    gen_random_uuid(),
    (SELECT id FROM users WHERE role = 'employee' LIMIT 1),
    28.6139,
    77.2090,
    10.0,
    '2026-02-12T10:00:00Z'
);
-- Expected error: duplicate key value violates unique constraint "unique_location_point"
*/

-- ============================================
-- MIGRATION COMPLETE
-- ============================================
-- Summary of changes:
-- ✅ Unique constraint prevents duplicates
-- ✅ Validation constraints ensure data integrity
-- ✅ Deduplication index speeds up inserts
-- ✅ Trigger only updates last_synced_at on real inserts
-- ✅ Idempotent: client can safely retry uploads
