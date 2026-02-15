-- ============================================================================
-- FIELDTRACK ADMIN FEATURES
-- ============================================================================
-- Description: Admin-only features including open sessions view and force checkout
-- Run Order: 4 (After 03_attendance_system.sql)
-- Version: P2.1 + P2.2
-- ============================================================================

-- ============================================================================
-- PART 1: ADMIN AUDIT LOG TABLE
-- ============================================================================

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
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_admin_id ON admin_audit_log(admin_id);
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_target_user ON admin_audit_log(target_user_id);
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_created_at ON admin_audit_log(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_admin_audit_log_action_type ON admin_audit_log(action_type);

-- Add table comment
COMMENT ON TABLE admin_audit_log IS 
'Audit trail for admin actions. INSERT only - no updates or deletes allowed.
Tracks force check-outs, approvals, deactivations, and other admin operations.';

-- ============================================================================
-- PART 2: RLS POLICIES FOR ADMIN AUDIT LOG
-- ============================================================================

ALTER TABLE admin_audit_log ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS "admin_audit_log_select_policy" ON admin_audit_log;
DROP POLICY IF EXISTS "admin_audit_log_insert_policy" ON admin_audit_log;

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

-- ============================================================================
-- PART 3: RPC FUNCTION - admin_list_open_sessions (P2.1)
-- ============================================================================

CREATE OR REPLACE FUNCTION admin_list_open_sessions()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $
DECLARE
    caller_id UUID;
    result JSONB;
BEGIN
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
                HINT = 'Only admin users can view all open sessions';
    END IF;
    
    -- Build JSONB response with all open sessions
    SELECT jsonb_build_object(
        'sessions', COALESCE(jsonb_agg(
            jsonb_build_object(
                'session_id', s.id,
                'user_id', s.user_id,
                'name', u.name,
                'email', u.email,
                'check_in_at', s.check_in_at,
                'created_at', s.created_at
            )
            ORDER BY s.check_in_at DESC
        ), '[]'::jsonb)
    ) INTO result
    FROM attendance_sessions s
    INNER JOIN users u ON s.user_id = u.id
    WHERE s.check_out_at IS NULL;
    
    RETURN result;
END;
$;

GRANT EXECUTE ON FUNCTION admin_list_open_sessions() TO authenticated;

COMMENT ON FUNCTION admin_list_open_sessions() IS 
'Admin-only function to list all currently open attendance sessions.
Returns JSONB with session details joined with user information.
SQLSTATE P0005: ADMIN_REQUIRED if caller is not admin.
SQLSTATE P0004: AUTHENTICATION_REQUIRED if not authenticated.';

-- ============================================================================
-- PART 4: RPC FUNCTION - admin_force_check_out (P2.2)
-- ============================================================================

CREATE OR REPLACE FUNCTION admin_force_check_out(
    target_user_id UUID,
    reason TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $
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
$;

GRANT EXECUTE ON FUNCTION admin_force_check_out(UUID, TEXT) TO authenticated;

COMMENT ON FUNCTION admin_force_check_out(UUID, TEXT) IS 
'Admin-only function to force check-out a user and close their open session.
Atomically closes session, updates user status, and logs the action.
SQLSTATE P0005: ADMIN_REQUIRED if caller is not admin.
SQLSTATE P0004: AUTHENTICATION_REQUIRED if not authenticated.
SQLSTATE P0002: NOT_CHECKED_IN if user has no open session.';

-- ============================================================================
-- ADMIN FEATURES SETUP COMPLETE
-- ============================================================================
-- Features:
-- - Admin audit log table with RLS policies
-- - admin_list_open_sessions() - View all open sessions
-- - admin_force_check_out() - Force close employee sessions with audit trail
--
-- All admin features are secured with SECURITY DEFINER and role validation
-- ============================================================================
