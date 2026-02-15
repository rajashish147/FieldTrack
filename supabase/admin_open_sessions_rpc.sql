-- ============================================================================
-- P2: Admin Open Session Visibility (Read-Only)
-- ============================================================================
-- Purpose: Allow admin users to view all currently open attendance sessions
-- Security: SECURITY DEFINER with explicit admin validation
-- P1 Invariants: UNTOUCHED - No modifications to existing session logic
-- Version: v1.3 P2
-- Date: 2024-02-15
-- ============================================================================

-- ----------------------------------------------------------------------------
-- Step 1: Ensure performance index exists
-- ----------------------------------------------------------------------------
-- Index on check_out_at for efficient filtering of open sessions
-- (May already exist from P1, this is idempotent)
CREATE INDEX IF NOT EXISTS idx_attendance_sessions_check_out_at 
ON attendance_sessions(check_out_at);

-- Additional composite index for optimal query performance
CREATE INDEX IF NOT EXISTS idx_attendance_sessions_open_user 
ON attendance_sessions(user_id, check_out_at) 
WHERE check_out_at IS NULL;

-- ----------------------------------------------------------------------------
-- Step 2: Admin Open Sessions RPC (SECURITY DEFINER)
-- ----------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION admin_list_open_sessions()
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, auth
AS $$
DECLARE
    caller_id UUID;
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
    
    -- Validate admin role (single query, no ambiguity)
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
    
    -- ========================================================================
    -- QUERY OPEN SESSIONS
    -- ========================================================================
    
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
$$;

-- ----------------------------------------------------------------------------
-- Step 3: Grant execute permission to authenticated users
-- ----------------------------------------------------------------------------
-- Note: Function is SECURITY DEFINER, so it runs with definer's privileges
-- The function itself validates admin role internally
GRANT EXECUTE ON FUNCTION admin_list_open_sessions() TO authenticated;

-- ----------------------------------------------------------------------------
-- Step 4: Add function comment for documentation
-- ----------------------------------------------------------------------------
COMMENT ON FUNCTION admin_list_open_sessions() IS 
'Admin-only function to list all currently open attendance sessions.
Returns JSONB with session details joined with user information.
Validates caller is admin before executing query.
SQLSTATE P0005: ADMIN_REQUIRED if caller is not admin or user not found.
SQLSTATE P0004: AUTHENTICATION_REQUIRED if not authenticated.

Example Response:
{
  "sessions": [
    {
      "session_id": "550e8400-e29b-41d4-a716-446655440000",
      "user_id": "123e4567-e89b-12d3-a456-426614174000",
      "name": "John Doe",
      "email": "john.doe@example.com",
      "check_in_at": "2024-02-15T08:30:00Z",
      "created_at": "2024-02-15T08:30:00Z"
    }
  ]
}

Empty result when no open sessions:
{
  "sessions": []
}';

-- ============================================================================
-- VERIFICATION QUERIES (Run these to test the function)
-- ============================================================================

-- Test 1: Call as admin user (should succeed)
-- SELECT admin_list_open_sessions();

-- Test 2: Verify indexes exist
-- SELECT indexname, indexdef 
-- FROM pg_indexes 
-- WHERE tablename = 'attendance_sessions' 
--   AND indexname LIKE 'idx_attendance_sessions%';

-- Test 3: Check function permissions
-- SELECT routine_name, routine_type, security_type
-- FROM information_schema.routines
-- WHERE routine_name = 'admin_list_open_sessions';

-- ============================================================================
-- P1 INVARIANTS CONFIRMATION
-- ============================================================================
-- ✅ Existing RPCs: check_in(), check_out(), get_open_session() - UNTOUCHED
-- ✅ Table Schema: attendance_sessions, users - NO CHANGES
-- ✅ RLS Policies: All existing policies - UNCHANGED
-- ✅ Session Logic: One open session per user - ENFORCED (by existing logic)
-- ✅ Error Contract: SQLSTATE codes - PRESERVED
-- ✅ Security Model: No weakening - MAINTAINED
-- ============================================================================
