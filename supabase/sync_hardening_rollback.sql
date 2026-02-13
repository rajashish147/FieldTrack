-- ============================================
-- ROLLBACK: Server-Side Idempotency & Deduplication
-- ============================================
-- Run this if you need to revert Phase 2 changes
-- WARNING: This removes duplicate prevention!

-- ============================================
-- STEP 1: Remove unique constraint
-- ============================================
ALTER TABLE locations 
DROP CONSTRAINT IF EXISTS unique_location_point;

-- ============================================
-- STEP 2: Remove deduplication index
-- ============================================
DROP INDEX IF EXISTS idx_locations_dedup;

-- ============================================
-- STEP 3: Remove validation constraints
-- ============================================
ALTER TABLE locations 
DROP CONSTRAINT IF EXISTS valid_latitude;

ALTER TABLE locations 
DROP CONSTRAINT IF EXISTS valid_longitude;

ALTER TABLE locations 
DROP CONSTRAINT IF EXISTS valid_accuracy;

-- ============================================
-- STEP 4: Revert trigger (optional)
-- ============================================
-- If you want to revert to the old trigger behavior
-- (This is optional - the new trigger is better)

/*
DROP TRIGGER IF EXISTS trigger_update_last_sync ON locations;

CREATE OR REPLACE FUNCTION update_user_last_sync()
RETURNS TRIGGER AS $$
BEGIN
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
*/

-- ============================================
-- ROLLBACK COMPLETE
-- ============================================
-- Note: This does NOT remove duplicates that were prevented
-- It only removes the constraints that prevent future duplicates
