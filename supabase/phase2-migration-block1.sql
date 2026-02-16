-- ============================================================================
-- PHASE 2 BLOCK 1: SCHEMA INTRODUCTION (SAFE, NULLABLE)
-- ============================================================================
-- Feature: v1.4 Session-Based Location Tracking
-- Block: 1 of 7
-- Objective: Introduce session_id column without affecting existing inserts
-- Safety: Zero downtime, fully reversible
-- ============================================================================

-- ============================================================================
-- PRE-EXECUTION CHECKLIST
-- ============================================================================
-- [ ] Database backup completed
-- [ ] Current location insert rate documented (baseline)
-- [ ] Monitoring dashboard ready
-- [ ] Rollback script reviewed and accessible
-- [ ] Low-traffic window scheduled (optional, but recommended)
-- [ ] DBA approval obtained

-- ============================================================================
-- PART 1: ADD NULLABLE session_id COLUMN
-- ============================================================================

-- Add column with foreign key reference
-- This operation is SAFE because:
-- 1. Column is NULLABLE (existing inserts unaffected)
-- 2. No default value (no table rewrite)
-- 3. Foreign key created with NOT VALID (no blocking validation)
-- 4. Validation runs separately without blocking writes

BEGIN;

-- Add the column
ALTER TABLE locations 
  ADD COLUMN session_id UUID;

-- Add foreign key constraint WITHOUT validation
-- NOT VALID = constraint created instantly without scanning table
-- This is CRITICAL for production safety on large tables
-- Note: No ON DELETE clause because sessions are immutable (Block 4)
ALTER TABLE locations
  ADD CONSTRAINT fk_locations_session_id 
  FOREIGN KEY (session_id) 
  REFERENCES attendance_sessions(id)
  NOT VALID;

COMMIT;

-- ============================================================================
-- PART 2: VALIDATE CONSTRAINT (NON-BLOCKING)
-- ============================================================================

-- Validate the constraint separately
-- This scans the table but does NOT block writes
-- Safe for production with active traffic
ALTER TABLE locations 
  VALIDATE CONSTRAINT fk_locations_session_id;

-- Validation completes instantly because all rows have NULL session_id

-- ============================================================================
-- PART 3: VERIFY COLUMN ADDITION
-- ============================================================================

-- Verify column exists and is nullable
SELECT 
  column_name,
  data_type,
  is_nullable,
  column_default
FROM information_schema.columns
WHERE table_name = 'locations' 
  AND column_name = 'session_id';

-- Expected output:
-- column_name | data_type | is_nullable | column_default
-- session_id  | uuid      | YES         | NULL

-- ============================================================================
-- PART 4: VERIFY FOREIGN KEY CONSTRAINT
-- ============================================================================

-- Verify FK constraint exists and is VALIDATED
SELECT
  tc.constraint_name,
  tc.table_name,
  kcu.column_name,
  ccu.table_name AS foreign_table_name,
  ccu.column_name AS foreign_column_name,
  pg_get_constraintdef(pgc.oid) as constraint_definition
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
  ON tc.constraint_name = kcu.constraint_name
  AND tc.table_schema = kcu.table_schema
JOIN information_schema.constraint_column_usage AS ccu
  ON ccu.constraint_name = tc.constraint_name
  AND ccu.table_schema = tc.table_schema
JOIN pg_constraint pgc
  ON pgc.conname = tc.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND tc.table_name = 'locations'
  AND kcu.column_name = 'session_id';

-- Expected output:
-- constraint_name           | table_name | column_name | foreign_table_name    | foreign_column_name | constraint_definition
-- fk_locations_session_id   | locations  | session_id  | attendance_sessions   | id                  | FOREIGN KEY (session_id) REFERENCES attendance_sessions(id)

-- Verify constraint is VALIDATED (not NOT VALID)
SELECT 
  conname as constraint_name,
  convalidated as is_validated
FROM pg_constraint
WHERE conname = 'fk_locations_session_id';

-- Expected: is_validated = true (after VALIDATE CONSTRAINT ran)

-- ============================================================================
-- PART 5: FUNCTIONAL VERIFICATION (READ-ONLY QUERIES)
-- ============================================================================

-- PRODUCTION NOTE: DO NOT run procedural test blocks in production
-- These verification queries are READ-ONLY and safe for production

-- Verification 1: Check if any locations have session_id populated
-- Expected: 0 rows (all NULL immediately after Block 1)
SELECT COUNT(*) as locations_with_session_id
FROM locations
WHERE session_id IS NOT NULL;

-- Verification 2: Check if FK constraint allows NULL
-- Expected: This query succeeds (proves NULL is allowed)
SELECT 
  l.id,
  l.user_id,
  l.session_id,
  l.timestamp
FROM locations l
WHERE l.session_id IS NULL
LIMIT 5;

-- Verification 3: Check if FK constraint validates non-NULL values
-- Expected: 0 rows (no invalid session_id references exist)
SELECT COUNT(*) as invalid_session_references
FROM locations l
WHERE l.session_id IS NOT NULL
  AND NOT EXISTS (
    SELECT 1 FROM attendance_sessions s
    WHERE s.id = l.session_id
  );

-- ============================================================================
-- STAGING/DEV ONLY: FUNCTIONAL TESTS WITH INSERTS
-- ============================================================================
-- WARNING: DO NOT RUN THESE IN PRODUCTION
-- Run these tests in staging/development environment only

/*
-- Test 1: Insert location with NULL session_id (should succeed)
DO $
DECLARE
  test_user_id UUID;
  test_location_id UUID;
BEGIN
  SELECT id INTO test_user_id 
  FROM users 
  WHERE role = 'employee' AND is_active = true 
  LIMIT 1;
  
  IF test_user_id IS NULL THEN
    RAISE EXCEPTION 'No active employee found for testing';
  END IF;
  
  INSERT INTO locations (id, user_id, session_id, latitude, longitude, accuracy, timestamp)
  VALUES (
    gen_random_uuid(),
    test_user_id,
    NULL,
    40.7128,
    -74.0060,
    10.0,
    NOW()
  )
  RETURNING id INTO test_location_id;
  
  RAISE NOTICE 'Test 1 PASSED: Insert with NULL session_id succeeded (id: %)', test_location_id;
  
  DELETE FROM locations WHERE id = test_location_id;
END;
$;

-- Test 2: Insert location with valid session_id (should succeed)
DO $
DECLARE
  test_user_id UUID;
  test_session_id UUID;
  test_location_id UUID;
BEGIN
  SELECT u.id, s.id INTO test_user_id, test_session_id
  FROM users u
  JOIN attendance_sessions s ON s.user_id = u.id
  WHERE u.role = 'employee' 
    AND u.is_active = true
    AND s.check_out_at IS NULL
  LIMIT 1;
  
  IF test_session_id IS NULL THEN
    RAISE NOTICE 'Test 2 SKIPPED: No open session found';
  ELSE
    INSERT INTO locations (id, user_id, session_id, latitude, longitude, accuracy, timestamp)
    VALUES (
      gen_random_uuid(),
      test_user_id,
      test_session_id,
      40.7128,
      -74.0060,
      10.0,
      NOW()
    )
    RETURNING id INTO test_location_id;
    
    RAISE NOTICE 'Test 2 PASSED: Insert with valid session_id succeeded (id: %)', test_location_id;
    
    DELETE FROM locations WHERE id = test_location_id;
  END IF;
END;
$;

-- Test 3: Insert location with invalid session_id (should fail)
DO $
DECLARE
  test_user_id UUID;
  fake_session_id UUID := gen_random_uuid();
BEGIN
  SELECT id INTO test_user_id 
  FROM users 
  WHERE role = 'employee' AND is_active = true 
  LIMIT 1;
  
  IF test_user_id IS NULL THEN
    RAISE EXCEPTION 'No active employee found for testing';
  END IF;
  
  BEGIN
    INSERT INTO locations (id, user_id, session_id, latitude, longitude, accuracy, timestamp)
    VALUES (
      gen_random_uuid(),
      test_user_id,
      fake_session_id,
      40.7128,
      -74.0060,
      10.0,
      NOW()
    );
    
    RAISE EXCEPTION 'Test 3 FAILED: Insert with invalid session_id should have failed';
  EXCEPTION
    WHEN foreign_key_violation THEN
      RAISE NOTICE 'Test 3 PASSED: Insert with invalid session_id correctly rejected';
  END;
END;
$;
*/

-- ============================================================================
-- PART 6: PERFORMANCE VERIFICATION
-- ============================================================================

-- Check table size before/after (should be minimal increase)
SELECT 
  pg_size_pretty(pg_total_relation_size('locations')) as total_size,
  pg_size_pretty(pg_relation_size('locations')) as table_size,
  pg_size_pretty(pg_indexes_size('locations')) as indexes_size;

-- Verify no performance regression on existing queries
EXPLAIN ANALYZE
SELECT * FROM locations 
WHERE user_id = (SELECT id FROM users LIMIT 1)
ORDER BY timestamp DESC 
LIMIT 100;

-- Expected: Query plan unchanged from baseline

-- ============================================================================
-- PART 7: MONITORING QUERIES
-- ============================================================================

-- Query 1: Count locations by session_id status
SELECT 
  COUNT(*) FILTER (WHERE session_id IS NOT NULL) as with_session_id,
  COUNT(*) FILTER (WHERE session_id IS NULL) as without_session_id,
  COUNT(*) as total,
  ROUND(
    (COUNT(*) FILTER (WHERE session_id IS NULL)::FLOAT / NULLIF(COUNT(*), 0)) * 100, 
    2
  ) as null_percentage
FROM locations;

-- Expected immediately after Block 1: 100% NULL

-- Query 2: Recent inserts (last 1 hour)
SELECT 
  COUNT(*) FILTER (WHERE session_id IS NOT NULL) as with_session_id,
  COUNT(*) FILTER (WHERE session_id IS NULL) as without_session_id,
  COUNT(*) as total
FROM locations
WHERE timestamp >= NOW() - INTERVAL '1 hour';

-- Query 3: Verify FK constraint is validated and active
SELECT 
  conname as constraint_name,
  contype as constraint_type,
  convalidated as is_validated,
  CASE 
    WHEN convalidated THEN 'Constraint is VALIDATED and enforcing'
    ELSE 'Constraint is NOT VALID (still enforcing for new rows)'
  END as status
FROM pg_constraint
WHERE conname = 'fk_locations_session_id';

-- Expected: is_validated = true (after VALIDATE CONSTRAINT completed)

-- ============================================================================
-- BLOCK 1 COMPLETION CHECKLIST
-- ============================================================================
-- [ ] Column session_id exists and is nullable
-- [ ] Foreign key constraint fk_locations_session_id created with NOT VALID
-- [ ] Constraint validated successfully (is_validated = true)
-- [ ] Verification queries show 0 locations with session_id
-- [ ] Verification queries show 0 invalid FK references
-- [ ] No performance regression observed
-- [ ] Monitoring queries return expected results (100% NULL)
-- [ ] Existing mobile clients continue inserting locations successfully
-- [ ] Staging tests passed (if applicable)

-- ============================================================================
-- ROLLBACK SCRIPT (IF NEEDED)
-- ============================================================================
-- ONLY execute if Block 1 must be reverted
-- WARNING: This will drop the session_id column and all data in it
-- SAFE: No locks required, column is nullable and unused

/*
-- Rollback is simple and safe
BEGIN;

-- Drop foreign key constraint first
ALTER TABLE locations 
  DROP CONSTRAINT IF EXISTS fk_locations_session_id;

-- Drop the column
ALTER TABLE locations 
  DROP COLUMN IF EXISTS session_id;

COMMIT;

-- Verify rollback
SELECT column_name 
FROM information_schema.columns 
WHERE table_name = 'locations' AND column_name = 'session_id';
-- Expected: 0 rows (column does not exist)

-- Verify constraint removed
SELECT conname 
FROM pg_constraint 
WHERE conname = 'fk_locations_session_id';
-- Expected: 0 rows (constraint does not exist)
*/

-- ============================================================================
-- NEXT STEPS
-- ============================================================================
-- After Block 1 verification complete:
-- 1. Monitor production for 24-48 hours
-- 2. Verify no insert failures
-- 3. Confirm no performance degradation
-- 4. Proceed to Block 2: Performance Hardening (Index Creation)
-- ============================================================================
