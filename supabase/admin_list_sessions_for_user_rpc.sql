-- ============================================================================
-- ADMIN: LIST ALL SESSIONS FOR A SPECIFIC EMPLOYEE
-- ============================================================================
-- Purpose:   Return full session history (open + closed) for one employee
-- Security:  SECURITY DEFINER — admin-only, auth.uid() validated server-side
-- Version:   v1.4
-- Depends:   public.attendance_sessions, public.users
-- Modifies:  NOTHING — read-only function, zero side effects
-- ============================================================================


-- ============================================================================
-- STEP 1: CREATE THE FUNCTION
-- ============================================================================

CREATE OR REPLACE FUNCTION admin_list_sessions_for_user(
    target_user_id UUID
)
RETURNS TABLE (
    id               UUID,
    user_id          UUID,
    check_in_at      TIMESTAMPTZ,
    check_out_at     TIMESTAMPTZ,
    duration_minutes INTEGER,
    status           TEXT
)
LANGUAGE plpgsql
STABLE          -- read-only: no DML; same inputs → same outputs within a transaction.
                -- PostgREST uses volatility to classify functions: STABLE/IMMUTABLE
                -- functions are treated as safe read calls and will never be rejected
                -- due to mutation-related Prefer header semantics (avoids HTTP 400 on
                -- RETURNS TABLE functions when Prefer: return=representation is present).
                -- NOW() and auth.uid() are both stable within a single transaction,
                -- so STABLE is accurate here.
SECURITY DEFINER
SET search_path = public, auth
AS $$
-- ── VARIABLE CONFLICT RESOLUTION ────────────────────────────────────────────
-- The RETURNS TABLE declaration above creates PL/pgSQL output variables with
-- the same names as the columns we SELECT (id, user_id, check_in_at, etc.).
-- Without this directive PostgreSQL raises SQLSTATE 42702 ("column reference
-- 'id' is ambiguous — it could refer to either a PL/pgSQL variable or a table
-- column") even when the reference is qualified with a table alias (s.id).
-- #variable_conflict use_column tells the compiler to always prefer the table
-- column over the output variable when both are in scope, which is the correct
-- behaviour for a RETURN QUERY SELECT.
#variable_conflict use_column
DECLARE
    caller_id UUID;
BEGIN

    -- ========================================================================
    -- SECURITY: AUTHENTICATION CHECK
    -- ========================================================================

    caller_id := auth.uid();

    IF caller_id IS NULL THEN
        RAISE EXCEPTION 'NOT_AUTHENTICATED'
            USING
                ERRCODE = 'P0004',
                HINT    = 'User must be authenticated to call this function';
    END IF;

    -- ========================================================================
    -- SECURITY: ADMIN ROLE CHECK
    --
    -- Reads from public.users (not auth.users) — consistent with all existing
    -- admin RPCs in this project (admin_list_open_sessions, admin_force_check_out).
    -- The SECURITY DEFINER context means this SELECT runs with the function
    -- owner's privileges, bypassing RLS on the users table.
    -- ========================================================================

    IF NOT EXISTS (
        SELECT 1
        FROM public.users
        WHERE id   = caller_id
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'ADMIN_REQUIRED'
            USING
                ERRCODE = 'P0005',
                HINT    = 'Only admin users can view employee session history';
    END IF;

    -- ========================================================================
    -- INPUT VALIDATION
    -- ========================================================================

    IF target_user_id IS NULL THEN
        RAISE EXCEPTION 'INVALID_INPUT'
            USING
                ERRCODE = 'P0006',
                HINT    = 'target_user_id must not be NULL';
    END IF;

    -- ========================================================================
    -- RETURN SESSION HISTORY (READ-ONLY)
    --
    -- duration_minutes:
    --   OPEN sessions   → elapsed minutes from check_in_at to NOW()
    --   CLOSED sessions → elapsed minutes from check_in_at to check_out_at
    --
    -- Calculation:
    --   EXTRACT(EPOCH FROM interval) returns fractional seconds as FLOAT8.
    --   Dividing by 60 converts to fractional minutes (still FLOAT8).
    --   Casting to INTEGER truncates toward zero — never rounds up.
    --   Conservative by design: a 1m 59s session reports as 1 minute.
    --   A session under 60 seconds reports as 0 minutes (not negative).
    --
    -- COALESCE(check_out_at, NOW()):
    --   For open sessions check_out_at IS NULL, so NOW() is used as the
    --   live end-time. This gives the admin a real-time duration for any
    --   currently active session without a separate code path.
    --
    -- Performance:
    --   WHERE clause is covered by the existing index:
    --     idx_attendance_user_id ON attendance_sessions(user_id)
    --   No new indexes are required.
    --   ORDER BY check_in_at DESC runs on the already-filtered small row
    --   set for a single employee — no separate index needed.
    -- ========================================================================

    RETURN QUERY
    SELECT
        s.id,
        s.user_id,
        s.check_in_at,
        s.check_out_at,
        (
            EXTRACT(
                EPOCH FROM (COALESCE(s.check_out_at, NOW()) - s.check_in_at)
            ) / 60
        )::INTEGER                                           AS duration_minutes,
        CASE
            WHEN s.check_out_at IS NULL THEN 'OPEN'
            ELSE                              'CLOSED'
        END                                                  AS status
    FROM  attendance_sessions s
    WHERE s.user_id = target_user_id
    ORDER BY s.check_in_at DESC;

END;
$$;


-- ============================================================================
-- STEP 2: LOCK DOWN PERMISSIONS
--
-- WHY REVOKE FIRST:
--   PostgreSQL grants EXECUTE to PUBLIC by default when any function is
--   created. This means an unauthenticated anonymous role could attempt to
--   call the function before Supabase's JWT layer rejects the request.
--   Revoking from PUBLIC adds a database-level enforcement layer that is
--   independent of the application or API gateway.
--
--   Existing project RPCs (admin_list_open_sessions, admin_force_check_out)
--   omit this step. Adding it here is a deliberate security hardening
--   improvement. It does not affect those functions and does not change
--   any existing behaviour.
-- ============================================================================

REVOKE EXECUTE ON FUNCTION admin_list_sessions_for_user(UUID) FROM PUBLIC;

-- Grant ONLY to the authenticated role.
-- Supabase maps all valid JWT-bearing requests to this PostgreSQL role.
-- Combined with the in-function admin check, this creates two independent
-- enforcement layers:
--   Layer 1 (network)  — valid JWT is required (Supabase rejects before DB)
--   Layer 2 (database) — caller must have role = 'admin' in public.users
GRANT EXECUTE ON FUNCTION admin_list_sessions_for_user(UUID) TO authenticated;


-- ============================================================================
-- STEP 3: DOCUMENT THE FUNCTION
-- ============================================================================

COMMENT ON FUNCTION admin_list_sessions_for_user(UUID) IS
'Admin-only function to retrieve the full attendance session history
(open AND closed) for a specific employee, ordered newest-first.

READ-ONLY — does not write to, modify, or lock any data.

SECURITY MODEL:
  This function uses SECURITY DEFINER, which means it runs with the
  privileges of the function owner and bypasses RLS on all tables it
  reads. Access control is therefore enforced entirely inside the
  function body through two explicit checks:
    1. auth.uid() must be non-NULL (authenticated user)
    2. caller must have role = ''admin'' in public.users

  REVOKE FROM PUBLIC ensures the function is unreachable at the database
  level for unauthenticated connections, independently of the API layer.

SQLSTATE error codes (consistent with existing project RPCs):
  P0004 NOT_AUTHENTICATED : auth.uid() returned NULL — no valid JWT
  P0005 ADMIN_REQUIRED    : caller is authenticated but is not an admin
  P0006 INVALID_INPUT     : target_user_id argument was passed as NULL

Parameters:
  target_user_id UUID — the employee whose sessions to retrieve

Returns one row per session, ordered by check_in_at DESC (newest first):
  id               UUID        — session primary key
  user_id          UUID        — employee user ID (always = target_user_id)
  check_in_at      TIMESTAMPTZ — when the session was opened (server time)
  check_out_at     TIMESTAMPTZ — when the session was closed; NULL = open
  duration_minutes INTEGER     — elapsed minutes, truncated (not rounded)
                                 open sessions use NOW() as end-time
  status           TEXT        — ''OPEN'' (check_out_at IS NULL)
                                 ''CLOSED'' (check_out_at IS NOT NULL)

Example usage (from Supabase SQL Editor as admin):
  SELECT *
  FROM admin_list_sessions_for_user(
      ''123e4567-e89b-12d3-a456-426614174000''::UUID
  );

Example usage (from Android via PostgREST):
  POST /rest/v1/rpc/admin_list_sessions_for_user
  { "target_user_id": "123e4567-e89b-12d3-a456-426614174000" }';


-- ============================================================================
-- STEP 4: VERIFY FUNCTION CREATION
-- ============================================================================

-- Verify function exists with correct signature and SECURITY DEFINER model
SELECT
    routine_name,
    routine_type,
    security_type
FROM information_schema.routines
WHERE routine_schema = 'public'
  AND routine_name   = 'admin_list_sessions_for_user';

-- Expected:
-- routine_name                    | routine_type | security_type
-- admin_list_sessions_for_user    | FUNCTION     | DEFINER


-- ============================================================================
-- STEP 5: VERIFY PERMISSIONS
-- ============================================================================

-- Verify PUBLIC has been revoked and only authenticated is granted
SELECT
    grantee,
    privilege_type,
    is_grantable
FROM information_schema.routine_privileges
WHERE routine_schema = 'public'
  AND routine_name   = 'admin_list_sessions_for_user'
ORDER BY grantee;

-- Expected:
-- grantee         | privilege_type | is_grantable
-- authenticated   | EXECUTE        | NO
--
-- PUBLIC must NOT appear in this result.
-- If PUBLIC appears, the REVOKE statement did not execute — re-run Step 2.


-- ============================================================================
-- STEP 6: FUNCTIONAL VERIFICATION QUERIES
-- (READ-ONLY — safe to run in production after deployment)
-- ============================================================================

-- Replace <employee_user_id> with a real employee UUID before running.

-- Verification 1: Returns rows for a valid employee (all sessions)
-- Expected: one row per session, newest check_in_at first
--
-- SELECT *
-- FROM admin_list_sessions_for_user('<employee_user_id>'::UUID);

-- Verification 2: Open session has NULL check_out_at and status = 'OPEN'
-- Expected: rows where check_out_at IS NULL and status = 'OPEN'
--
-- SELECT id, check_in_at, check_out_at, duration_minutes, status
-- FROM admin_list_sessions_for_user('<employee_user_id>'::UUID)
-- WHERE status = 'OPEN';

-- Verification 3: Closed sessions have non-NULL check_out_at and status = 'CLOSED'
-- Expected: rows where check_out_at IS NOT NULL and status = 'CLOSED'
--
-- SELECT id, check_in_at, check_out_at, duration_minutes, status
-- FROM admin_list_sessions_for_user('<employee_user_id>'::UUID)
-- WHERE status = 'CLOSED';

-- Verification 4: duration_minutes is always >= 0 (no negative durations)
-- Expected: 0 rows returned
--
-- SELECT *
-- FROM admin_list_sessions_for_user('<employee_user_id>'::UUID)
-- WHERE duration_minutes < 0;

-- Verification 5: Ordering is correct — newest session is row 1
-- Expected: check_in_at values decrease monotonically top-to-bottom
--
-- SELECT id, check_in_at, status
-- FROM admin_list_sessions_for_user('<employee_user_id>'::UUID);

-- Verification 6: Non-existent user returns empty result set, not an error
-- Expected: 0 rows, no exception
--
-- SELECT *
-- FROM admin_list_sessions_for_user('00000000-0000-0000-0000-000000000000'::UUID);

-- Verification 7: Exactly 6 columns are returned — no extra data exposed
-- Expected: columns = id, user_id, check_in_at, check_out_at,
--           duration_minutes, status — nothing else
--
-- SELECT *
-- FROM admin_list_sessions_for_user('<employee_user_id>'::UUID)
-- LIMIT 1;


-- ============================================================================
-- STEP 7: SECURITY REJECTION TESTS
-- (RUN IN STAGING / DEV ONLY — these are expected to raise exceptions)
-- ============================================================================

/*
-- Test 1: Non-admin authenticated user → must raise SQLSTATE P0005
-- Log in as an employee and run:
SELECT *
FROM admin_list_sessions_for_user('<any_valid_user_id>'::UUID);
-- Expected: ERROR: ADMIN_REQUIRED  (SQLSTATE P0005)


-- Test 2: Unauthenticated call (no JWT) → must raise SQLSTATE P0004
-- Make request without Authorization header and run:
SELECT *
FROM admin_list_sessions_for_user('<any_valid_user_id>'::UUID);
-- Expected: ERROR: NOT_AUTHENTICATED  (SQLSTATE P0004)


-- Test 3: NULL argument → must raise SQLSTATE P0006
SELECT *
FROM admin_list_sessions_for_user(NULL::UUID);
-- Expected: ERROR: INVALID_INPUT  (SQLSTATE P0006)


-- Test 4: Admin viewing their own sessions → must succeed
SELECT *
FROM admin_list_sessions_for_user('<admin_user_id>'::UUID);
-- Expected: 0 or more rows, no error


-- Test 5: Verify P1 RPCs are unaffected after deployment
SELECT check_in();          -- Expected: normal result or P0001/P0003
SELECT check_out();         -- Expected: normal result or P0002/P0003
SELECT get_open_session();  -- Expected: 0 or 1 row, no error
*/


-- ============================================================================
-- STEP 8: PERFORMANCE VERIFICATION
-- ============================================================================

-- Confirm the query plan uses the existing idx_attendance_user_id index.
-- No new indexes were added — the existing single-column index on user_id
-- covers the WHERE clause efficiently for the expected data volume
-- (~100 employees, ~200 sessions/day, ~50,000 sessions/year).
--
-- EXPLAIN ANALYZE
-- SELECT *
-- FROM admin_list_sessions_for_user('<employee_user_id>'::UUID);
--
-- Expected plan:
--   Index Scan using idx_attendance_user_id on attendance_sessions
--     (Filter: user_id = <target_user_id>)
--   Sort (check_in_at DESC) on filtered result set


-- ============================================================================
-- P1 / P2 INVARIANTS CONFIRMATION
-- ============================================================================
-- ✅ Existing RPC: check_in()                  — UNTOUCHED
-- ✅ Existing RPC: check_out()                 — UNTOUCHED
-- ✅ Existing RPC: get_open_session()          — UNTOUCHED
-- ✅ Existing RPC: admin_list_open_sessions()  — UNTOUCHED
-- ✅ Existing RPC: admin_force_check_out()     — UNTOUCHED
-- ✅ Table schema: attendance_sessions         — NO CHANGES
-- ✅ Table schema: users                       — NO CHANGES
-- ✅ RLS policies: all existing policies       — UNCHANGED
-- ✅ Session logic: one open session per user  — ENFORCED (unchanged)
-- ✅ Write safety:  this function is READ-ONLY — CONFIRMED, no DML
-- ✅ Volatility:    STABLE added               — PostgREST read-only classification
-- ✅ Error contract: SQLSTATE P0004/P0005/P0006— CONSISTENT with project
-- ✅ Security model: SECURITY DEFINER + uid()  — MAINTAINED
-- ✅ Column exposure: exactly 6 declared cols  — CONFIRMED, no extras
-- ✅ search_path lock: SET search_path used    —
