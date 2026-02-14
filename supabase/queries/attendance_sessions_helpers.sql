-- Attendance Sessions Helper Queries
-- Version: v1.3 P1
-- Description: Useful queries for monitoring, debugging, and maintenance

-- ============================================================================
-- MONITORING QUERIES
-- ============================================================================
-- NOTE: These queries calculate "hours" for DIAGNOSTIC purposes only.
-- This is NOT attendance calculation, NOT exposed to users, NOT used for payroll.
-- Use only for admin monitoring and debugging.

-- Query 1: View all open sessions
-- Use: Check who is currently checked in
SELECT 
  s.id as session_id,
  u.name as employee_name,
  u.email,
  s.check_in_at,
  EXTRACT(EPOCH FROM (NOW() - s.check_in_at)) / 3600 as hours_checked_in,
  u.is_checked_in as flag_state
FROM attendance_sessions s
JOIN users u ON u.id = s.user_id
WHERE s.check_out_at IS NULL
ORDER BY s.check_in_at DESC;

-- Query 2: View recent sessions (last 24 hours)
-- Use: Monitor recent attendance activity
SELECT 
  s.id as session_id,
  u.name as employee_name,
  s.check_in_at,
  s.check_out_at,
  CASE 
    WHEN s.check_out_at IS NULL THEN 'OPEN'
    ELSE 'CLOSED'
  END as status,
  CASE 
    WHEN s.check_out_at IS NOT NULL 
    THEN EXTRACT(EPOCH FROM (s.check_out_at - s.check_in_at)) / 3600
    ELSE NULL
  END as duration_hours
FROM attendance_sessions s
JOIN users u ON u.id = s.user_id
WHERE s.check_in_at >= NOW() - INTERVAL '24 hours'
ORDER BY s.check_in_at DESC;

-- Query 3: Detect drift between isCheckedIn flag and session state
-- Use: Find inconsistencies that need repair
SELECT 
  u.id,
  u.name,
  u.email,
  u.is_checked_in as flag_state,
  EXISTS(
    SELECT 1 FROM attendance_sessions 
    WHERE user_id = u.id AND check_out_at IS NULL
  ) as has_open_session,
  CASE
    WHEN u.is_checked_in = true AND NOT EXISTS(
      SELECT 1 FROM attendance_sessions 
      WHERE user_id = u.id AND check_out_at IS NULL
    ) THEN 'FLAG_TRUE_NO_SESSION'
    WHEN u.is_checked_in = false AND EXISTS(
      SELECT 1 FROM attendance_sessions 
      WHERE user_id = u.id AND check_out_at IS NULL
    ) THEN 'FLAG_FALSE_HAS_SESSION'
    ELSE 'CONSISTENT'
  END as consistency_status
FROM users u
WHERE u.role = 'employee'
  AND (
    (u.is_checked_in = true AND NOT EXISTS(
      SELECT 1 FROM attendance_sessions 
      WHERE user_id = u.id AND check_out_at IS NULL
    ))
    OR
    (u.is_checked_in = false AND EXISTS(
      SELECT 1 FROM attendance_sessions 
      WHERE user_id = u.id AND check_out_at IS NULL
    ))
  );

-- Query 4: Count sessions by user
-- Use: See attendance history summary
SELECT 
  u.name as employee_name,
  COUNT(*) as total_sessions,
  COUNT(*) FILTER (WHERE s.check_out_at IS NULL) as open_sessions,
  COUNT(*) FILTER (WHERE s.check_out_at IS NOT NULL) as closed_sessions
FROM users u
LEFT JOIN attendance_sessions s ON s.user_id = u.id
WHERE u.role = 'employee'
GROUP BY u.id, u.name
ORDER BY total_sessions DESC;

-- Query 5: Find long open sessions (potential forgotten check-outs)
-- Use: Identify employees who may have forgotten to check out
SELECT 
  s.id as session_id,
  u.name as employee_name,
  u.email,
  s.check_in_at,
  EXTRACT(EPOCH FROM (NOW() - s.check_in_at)) / 3600 as hours_open,
  CASE
    WHEN EXTRACT(EPOCH FROM (NOW() - s.check_in_at)) / 3600 > 24 THEN 'CRITICAL'
    WHEN EXTRACT(EPOCH FROM (NOW() - s.check_in_at)) / 3600 > 12 THEN 'WARNING'
    ELSE 'NORMAL'
  END as alert_level
FROM attendance_sessions s
JOIN users u ON u.id = s.user_id
WHERE s.check_out_at IS NULL
  AND s.check_in_at < NOW() - INTERVAL '12 hours'
ORDER BY s.check_in_at ASC;

-- ============================================================================
-- REPAIR QUERIES (Use with caution)
-- ============================================================================

-- Repair 1: Fix drift by trusting session state (RECOMMENDED)
-- Use: Repair isCheckedIn flag based on session state
-- WARNING: Run this manually, not automatically
/*
UPDATE users u
SET is_checked_in = EXISTS(
  SELECT 1 FROM attendance_sessions 
  WHERE user_id = u.id AND check_out_at IS NULL
)
WHERE u.role = 'employee'
  AND u.is_checked_in != EXISTS(
    SELECT 1 FROM attendance_sessions 
    WHERE user_id = u.id AND check_out_at IS NULL
  );
*/

-- Repair 2: Fix specific user drift
-- Use: Repair single user's state
-- Replace $USER_ID with actual user ID
/*
UPDATE users
SET is_checked_in = EXISTS(
  SELECT 1 FROM attendance_sessions 
  WHERE user_id = $USER_ID AND check_out_at IS NULL
)
WHERE id = $USER_ID;
*/

-- ============================================================================
-- TESTING QUERIES
-- ============================================================================

-- Test 1: Verify unique index works (should fail on second insert)
-- Use: Test that double check-in is prevented
/*
-- First insert (should succeed)
INSERT INTO attendance_sessions (user_id, check_in_at)
VALUES ('test-user-id', NOW());

-- Second insert (should fail with unique violation)
INSERT INTO attendance_sessions (user_id, check_in_at)
VALUES ('test-user-id', NOW());
*/

-- Test 2: Verify CHECK constraints work
-- Use: Test that invalid timestamps are rejected
/*
-- Should fail: check_out_at before check_in_at
INSERT INTO attendance_sessions (user_id, check_in_at, check_out_at)
VALUES ('test-user-id', NOW(), NOW() - INTERVAL '1 hour');

-- Should fail: future check_in_at
INSERT INTO attendance_sessions (user_id, check_in_at)
VALUES ('test-user-id', NOW() + INTERVAL '1 hour');
*/

-- Test 3: Verify RLS policies work
-- Use: Test that employees can only access their own sessions
/*
-- As employee A: Should see only own sessions
SELECT * FROM attendance_sessions;

-- As admin: Should see all sessions
SELECT * FROM attendance_sessions;
*/

-- ============================================================================
-- STATISTICS QUERIES
-- ============================================================================

-- Stats 1: Table size and row count
SELECT 
  pg_size_pretty(pg_total_relation_size('attendance_sessions')) as total_size,
  COUNT(*) as total_rows,
  COUNT(*) FILTER (WHERE check_out_at IS NULL) as open_sessions,
  COUNT(*) FILTER (WHERE check_out_at IS NOT NULL) as closed_sessions
FROM attendance_sessions;

-- Stats 2: Index usage
SELECT 
  schemaname,
  relname as tablename,
  indexrelname as indexname,
  idx_scan as index_scans,
  idx_tup_read as tuples_read,
  idx_tup_fetch as tuples_fetched
FROM pg_stat_user_indexes
WHERE relname = 'attendance_sessions'
ORDER BY idx_scan DESC;

-- ============================================================================
-- CLEANUP QUERIES (Future use - NOT for P1)
-- ============================================================================

-- Cleanup 1: Archive old closed sessions (NOT IMPLEMENTED IN P1)
-- Use: Move old sessions to archive table (future feature)
/*
-- Create archive table first
CREATE TABLE attendance_sessions_archive (LIKE attendance_sessions INCLUDING ALL);

-- Move sessions older than 1 year
INSERT INTO attendance_sessions_archive
SELECT * FROM attendance_sessions
WHERE check_out_at IS NOT NULL
  AND check_out_at < NOW() - INTERVAL '1 year';

-- Delete archived sessions
DELETE FROM attendance_sessions
WHERE check_out_at IS NOT NULL
  AND check_out_at < NOW() - INTERVAL '1 year';
*/
