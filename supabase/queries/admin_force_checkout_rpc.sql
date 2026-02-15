-- ============================================================================
-- P2.2: Admin Force Check-Out with Audit Logging
-- ============================================================================
-- Purpose: Allow admin users to force check-out employees with audit trail
-- Security: SECURITY DEFINER with explicit admin validation
-- P1 Invariants: PRESERVED - No modifications to existing RPCs or logic
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Step 1: Create Admin Audit Log Table
-- ----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS admin_audit_log (
    id BIGSERIAL PRIMARY KEY,
    admin_id UUID NOT NULL REFERENCES auth.users(id),
    action_type TEXT NOT NULL,
    target_user_id UUID REFERENCES auth.users(id),
    target_session_id UUID,
    reason TEXT,
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Add indexes for common queries
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_admin_id 
ON admin_audit_log(admin_id);

CREATE INDEX IF NOT EXISTS idx_admin_audit_log_target_user 
ON admin_audit_log(target_user_id);

CREATE INDEX IF NOT EXISTS idx_admin_audit_log_created_at 
ON admin_audit_log(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_admin_audit_log_action_type 
ON admin_audit_log(action_type);

-- Add table comment
COMMENT ON TABLE admin_audit_log IS 
'Audit trail for admin actions. INSERT only - no updates or deletes allowed.
Tracks force check-outs, approvals, deactivations, and other admin operations.';

-- ----------------------------------------------------------------------------
-- Step 2: RLS Policies for Admin Audit Log
-- ----------------------------------------------------------------------------
-- Enable RLS
ALTER TABLE admin_audit_log ENABLE ROW LEVEL SECURITY;

-- Policy: Admins can view all audit logs
CREATE POLICY admin_audit_log_select_policy ON admin_audit_log
    FOR SELECT
    TO authenticated
    USING (
        EXISTS (
            SELECT 1 FROM public.users
            WHERE id = auth.uid()
              AND role = 'admin'
        )
    );

-- Policy: Admins can insert audit logs (via RPC only in practice)
CREATE POLICY admin_audit_log_insert_policy ON admin_audit_log
    FOR INSERT
    TO authenticated
    WITH CHECK (
        EXISTS (
            SELECT 1 FROM public.users
            WHERE id = auth.uid()
              AND role = 'admin'
        )
    );

-- No UPDATE or DELETE policies - audit logs are immutable

-- ----------------------------------------------------------------------------
-- Step 3: Admin Force Check-Out RPC
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION admin_force_check_out(
    target_user_id UUID,
    reason TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    caller_id UUID;
    open_session_record RECORD;
    result JSONB;
BEGIN
    -- ========================================================================
    -- SECURITY VALIDATION
    -- ========================================================================
    
    -- Get authenticated user ID
    caller_id := auth.uid();
    
    -- Check authentication
    IF caller_id IS NULL THEN
        RAISE EXCEPTION 'AUTHENTICATION_REQUIRED'
            USING 
                ERRCODE = 'P0004',
                HINT = 'User must be authenticated';
    END IF;
    
    -- Validate admin role
    IF NOT EXISTS (
        SELECT 1
        FROM public.users
        WHERE id = caller_id
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'ADMIN_REQUIRED'
            USING 
                ERRCODE = 'P0005',
                HINT = 'Only admin users can force check-out';
    END IF;
    
    -- ========================================================================
    -- FETCH OPEN SESSION
    -- ========================================================================
    
    -- Get the open session for target user
    SELECT 
        s.id,
        s.user_id,
        s.check_in_at,
        s.created_at,
        u.name,
        u.email
    INTO open_session_record
    FROM attendance_sessions s
    INNER JOIN users u ON s.user_id = u.id
    WHERE s.user_id = target_user_id
      AND s.check_out_at IS NULL
    LIMIT 1;
    
    -- Check if session exists
    IF NOT FOUND THEN
        RAISE EXCEPTION 'NOT_CHECKED_IN'
            USING 
                ERRCODE = 'P0002',
                HINT = 'User does not have an open session';
    END IF;
    
    -- ========================================================================
    -- ATOMIC TRANSACTION: Close Session + Update User + Audit Log
    -- ========================================================================
    
    -- 1. Close the session
    UPDATE attendance_sessions
    SET 
        check_out_at = NOW(),
        updated_at = NOW()
    WHERE id = open_session_record.id;
    
    -- 2. Update user's checked-in status
    UPDATE users
    SET is_checked_in = false
    WHERE id = target_user_id;
    
    -- 3. Insert audit log
    INSERT INTO admin_audit_log (
        admin_id,
        action_type,
        target_user_id,
        target_session_id,
        reason,
        metadata
    ) VALUES (
        caller_id,
        'FORCE_CHECK_OUT',
        target_user_id,
        open_session_record.id,
        reason,
        jsonb_build_object(
            'target_user_name', open_session_record.name,
            'target_user_email', open_session_record.email,
            'check_in_at', open_session_record.check_in_at,
            'check_out_at', NOW(),
            'forced_by_admin', true
        )
    );
    
    -- ========================================================================
    -- BUILD RESPONSE
    -- ========================================================================
    
    result := jsonb_build_object(
        'session_id', open_session_record.id,
        'closed_at', NOW(),
        'target_user_id', target_user_id,
        'target_user_name', open_session_record.name,
        'check_in_at', open_session_record.check_in_at
    );
    
    RETURN result;
END;
$$;

-- ----------------------------------------------------------------------------
-- Step 4: Grant Permissions
-- ----------------------------------------------------------------------------
GRANT EXECUTE ON FUNCTION admin_force_check_out(UUID, TEXT) TO authenticated;

-- ----------------------------------------------------------------------------
-- Step 5: Add Function Comment
-- ----------------------------------------------------------------------------
COMMENT ON FUNCTION admin_force_check_out(UUID, TEXT) IS 
'Admin-only function to force check-out a user and close their open session.
Atomically closes session, updates user status, and logs the action.
SQLSTATE P0005: ADMIN_REQUIRED if caller is not admin.
SQLSTATE P0004: AUTHENTICATION_REQUIRED if not authenticated.
SQLSTATE P0002: NOT_CHECKED_IN if user has no open session.

Parameters:
- target_user_id: UUID of the user to force check-out
- reason: Optional text explaining why force check-out was performed

Returns JSONB:
{
  "session_id": "uuid",
  "closed_at": "timestamp",
  "target_user_id": "uuid",
  "target_user_name": "name",
  "check_in_at": "timestamp"
}

Example:
SELECT admin_force_check_out(
    ''123e4567-e89b-12d3-a456-426614174000''::UUID,
    ''Employee unresponsive, emergency check-out''
);';

-- ============================================================================
-- VERIFICATION QUERIES
-- ============================================================================

-- Test 1: Check table structure
-- SELECT column_name, data_type, is_nullable
-- FROM information_schema.columns
-- WHERE table_name = 'admin_audit_log'
-- ORDER BY ordinal_position;

-- Test 2: Check RLS policies
-- SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual
-- FROM pg_policies
-- WHERE tablename = 'admin_audit_log';

-- Test 3: Check function exists
-- SELECT routine_name, routine_type, security_type
-- FROM information_schema.routines
-- WHERE routine_name = 'admin_force_check_out';

-- Test 4: View audit logs (as admin)
-- SELECT 
--     id,
--     admin_id,
--     action_type,
--     target_user_id,
--     reason,
--     metadata,
--     created_at
-- FROM admin_audit_log
-- ORDER BY created_at DESC
-- LIMIT 10;

-- ============================================================================
-- P1 INVARIANTS CONFIRMATION
-- ============================================================================
-- ✅ Existing RPCs: check_in(), check_out(), get_open_session() - UNTOUCHED
-- ✅ Table Schema: attendance_sessions, users - NO CHANGES
-- ✅ RLS Policies: Existing policies - UNCHANGED
-- ✅ Session Logic: One open session per user - ENFORCED
-- ✅ Atomic Operations: All updates in single transaction - MAINTAINED
-- ✅ Server Truth: Session state managed server-side - PRESERVED
-- ✅ Error Contract: SQLSTATE codes - CONSISTENT
-- ✅ Security Model: SECURITY DEFINER with validation - MAINTAINED
-- ============================================================================

-- ============================================================================
-- TESTING INSTRUCTIONS
-- ============================================================================
-- 
-- Prerequisites:
-- 1. Have an admin user account
-- 2. Have an employee user with an open session
-- 
-- Test Case 1: Successful Force Check-Out
-- ----------------------------------------
-- As admin user:
-- SELECT admin_force_check_out(
--     '<employee_user_id>'::UUID,
--     'Testing force check-out functionality'
-- );
-- 
-- Expected: Returns JSONB with session details
-- Verify:
-- - Session has check_out_at set
-- - User's is_checked_in = false
-- - Audit log entry created
-- 
-- Test Case 2: Non-Admin User
-- ----------------------------
-- As employee user:
-- SELECT admin_force_check_out(
--     '<another_user_id>'::UUID,
--     'Should fail'
-- );
-- 
-- Expected: SQLSTATE P0005 - ADMIN_REQUIRED
-- 
-- Test Case 3: User Not Checked In
-- ---------------------------------
-- As admin user:
-- SELECT admin_force_check_out(
--     '<user_without_session>'::UUID,
--     'Should fail'
-- );
-- 
-- Expected: SQLSTATE P0002 - NOT_CHECKED_IN
-- 
-- Test Case 4: Unauthenticated Request
-- -------------------------------------
-- Without auth token:
-- SELECT admin_force_check_out(
--     '<any_user_id>'::UUID,
--     'Should fail'
-- );
-- 
-- Expected: SQLSTATE P0004 - AUTHENTICATION_REQUIRED
-- 
-- Test Case 5: Verify Audit Log
-- ------------------------------
-- As admin user:
-- SELECT * FROM admin_audit_log
-- WHERE action_type = 'FORCE_CHECK_OUT'
-- ORDER BY created_at DESC
-- LIMIT 5;
-- 
-- Expected: See all force check-out actions with metadata
-- 
-- Test Case 6: Verify P1 Invariants
-- ----------------------------------
-- 1. Regular check-in still works:
--    SELECT check_in();
-- 
-- 2. Regular check-out still works:
--    SELECT check_out();
-- 
-- 3. Get open session still works:
--    SELECT get_open_session();
-- 
-- Expected: All P1 RPCs function normally
-- 
-- ============================================================================
