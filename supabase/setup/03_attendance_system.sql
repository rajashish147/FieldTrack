-- ============================================================================
-- FIELDTRACK ATTENDANCE SYSTEM
-- ============================================================================
-- Description: Complete attendance tracking with check-in/check-out RPCs
-- Run Order: 3 (After 01_initial_setup.sql and 02_storage_setup.sql)
-- Version: v1.3 P1
-- ============================================================================

-- ============================================================================
-- PART 1: CREATE ATTENDANCE_SESSIONS TABLE
-- ============================================================================

CREATE TABLE IF NOT EXISTS attendance_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  check_in_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  check_out_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  
  -- Constraints
  CONSTRAINT no_future_checkin CHECK (check_in_at <= NOW() + INTERVAL '5 seconds'),
  CONSTRAINT no_future_checkout CHECK (check_out_at IS NULL OR check_out_at <= NOW() + INTERVAL '5 seconds'),
  CONSTRAINT checkout_after_checkin CHECK (check_out_at IS NULL OR check_out_at >= check_in_at)
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_attendance_user_id ON attendance_sessions(user_id);
CREATE INDEX IF NOT EXISTS idx_attendance_check_out_at ON attendance_sessions(check_out_at);
CREATE INDEX IF NOT EXISTS idx_attendance_user_checkout ON attendance_sessions(user_id, check_out_at);
CREATE UNIQUE INDEX IF NOT EXISTS idx_one_open_session_per_user ON attendance_sessions(user_id) WHERE check_out_at IS NULL;

-- Additional indexes for admin queries
CREATE INDEX IF NOT EXISTS idx_attendance_sessions_check_out_at ON attendance_sessions(check_out_at);
CREATE INDEX IF NOT EXISTS idx_attendance_sessions_open_user ON attendance_sessions(user_id, check_out_at) WHERE check_out_at IS NULL;

-- Updated_at trigger
CREATE OR REPLACE FUNCTION update_attendance_sessions_updated_at()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$;

DROP TRIGGER IF EXISTS trigger_attendance_sessions_updated_at ON attendance_sessions;
CREATE TRIGGER trigger_attendance_sessions_updated_at
  BEFORE UPDATE ON attendance_sessions
  FOR EACH ROW
  EXECUTE FUNCTION update_attendance_sessions_updated_at();

-- ============================================================================
-- PART 2: ROW LEVEL SECURITY POLICIES
-- ============================================================================

ALTER TABLE attendance_sessions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "attendance_employee_read" ON attendance_sessions;
DROP POLICY IF EXISTS "attendance_admin_read" ON attendance_sessions;

-- Employees can read their own sessions
CREATE POLICY attendance_employee_read ON attendance_sessions
  FOR SELECT
  TO authenticated
  USING (
    user_id = auth.uid() AND
    EXISTS (
      SELECT 1 FROM users
      WHERE users.id = auth.uid()
        AND users.role = 'employee'
        AND users.is_active = true
    )
  );

-- Admins can read all sessions
CREATE POLICY attendance_admin_read ON attendance_sessions
  FOR SELECT
  TO authenticated
  USING (
    EXISTS (
      SELECT 1 FROM users
      WHERE users.id = auth.uid()
        AND users.role = 'admin'
        AND users.is_active = true
    )
  );

-- ============================================================================
-- PART 3: RPC FUNCTION - check_in
-- ============================================================================

CREATE OR REPLACE FUNCTION check_in()
RETURNS TABLE (
  id UUID,
  user_id UUID,
  check_in_at TIMESTAMPTZ,
  check_out_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $
DECLARE
  v_user_id UUID;
  v_session_id UUID;
BEGIN
  v_user_id := auth.uid();
  
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION USING ERRCODE = 'P0004', MESSAGE = 'NOT_AUTHENTICATED';
  END IF;
  
  IF NOT EXISTS (
    SELECT 1 FROM users
    WHERE users.id = v_user_id AND role = 'employee' AND is_active = true
  ) THEN
    RAISE EXCEPTION USING ERRCODE = 'P0003', MESSAGE = 'ACCOUNT_INACTIVE';
  END IF;
  
  INSERT INTO attendance_sessions (user_id)
  VALUES (v_user_id)
  RETURNING attendance_sessions.id INTO v_session_id;
  
  UPDATE users SET is_checked_in = true WHERE users.id = v_user_id;
  
  RETURN QUERY
  SELECT a.id, a.user_id, a.check_in_at, a.check_out_at, a.created_at, a.updated_at
  FROM attendance_sessions a
  WHERE a.id = v_session_id;
  
EXCEPTION
  WHEN unique_violation THEN
    RAISE EXCEPTION USING ERRCODE = 'P0001', MESSAGE = 'ALREADY_CHECKED_IN';
END;
$;

-- ============================================================================
-- PART 4: RPC FUNCTION - check_out
-- ============================================================================

CREATE OR REPLACE FUNCTION check_out()
RETURNS TABLE (
  id UUID,
  user_id UUID,
  check_in_at TIMESTAMPTZ,
  check_out_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $
DECLARE
  v_user_id UUID;
  v_session_id UUID;
BEGIN
  v_user_id := auth.uid();
  
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION USING ERRCODE = 'P0004', MESSAGE = 'NOT_AUTHENTICATED';
  END IF;
  
  IF NOT EXISTS (
    SELECT 1 FROM users
    WHERE users.id = v_user_id AND role = 'employee' AND is_active = true
  ) THEN
    RAISE EXCEPTION USING ERRCODE = 'P0003', MESSAGE = 'ACCOUNT_INACTIVE';
  END IF;
  
  UPDATE attendance_sessions
  SET check_out_at = NOW()
  WHERE attendance_sessions.user_id = v_user_id AND attendance_sessions.check_out_at IS NULL
  RETURNING attendance_sessions.id INTO v_session_id;
  
  IF NOT FOUND THEN
    RAISE EXCEPTION USING ERRCODE = 'P0002', MESSAGE = 'NOT_CHECKED_IN';
  END IF;
  
  UPDATE users SET is_checked_in = false WHERE users.id = v_user_id;
  
  RETURN QUERY
  SELECT a.id, a.user_id, a.check_in_at, a.check_out_at, a.created_at, a.updated_at
  FROM attendance_sessions a
  WHERE a.id = v_session_id;
END;
$;

-- ============================================================================
-- PART 5: RPC FUNCTION - get_open_session
-- ============================================================================

CREATE OR REPLACE FUNCTION get_open_session()
RETURNS TABLE (
  id UUID,
  user_id UUID,
  check_in_at TIMESTAMPTZ,
  check_out_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ,
  updated_at TIMESTAMPTZ
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $
DECLARE
  v_user_id UUID;
BEGIN
  v_user_id := auth.uid();
  
  IF v_user_id IS NULL THEN
    RAISE EXCEPTION USING ERRCODE = 'P0004', MESSAGE = 'NOT_AUTHENTICATED';
  END IF;
  
  RETURN QUERY
  SELECT a.id, a.user_id, a.check_in_at, a.check_out_at, a.created_at, a.updated_at
  FROM attendance_sessions a
  WHERE a.user_id = v_user_id AND a.check_out_at IS NULL;
END;
$;

-- ============================================================================
-- ATTENDANCE SYSTEM SETUP COMPLETE
-- ============================================================================
-- Features:
-- - Attendance sessions table with constraints and indexes
-- - RLS policies for employee and admin access
-- - Three RPC functions: check_in(), check_out(), get_open_session()
-- - Atomic transactions with SQLSTATE error codes
-- - Server time enforcement
-- - One open session per user constraint
--
-- Next step: Run 04_admin_features.sql to enable admin capabilities
-- ============================================================================
