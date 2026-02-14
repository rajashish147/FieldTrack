# Attendance Sessions System (v1.3 P1)

## Overview

The attendance sessions system provides historical tracking of employee check-in and check-out events with the following guarantees:

- **Auditable**: All sessions are permanently recorded with timestamps
- **Crash-safe**: Atomic transactions ensure consistency
- **Network-failure-safe**: Idempotent-like behavior handles retries
- **RLS-secured**: Row-level security enforces access control

## Architecture

### Data Model

```
attendance_sessions
├── id (UUID, PK)
├── user_id (UUID, FK → users.id)
├── check_in_at (TIMESTAMPTZ, NOT NULL)
├── check_out_at (TIMESTAMPTZ, NULL = open)
├── created_at (TIMESTAMPTZ)
└── updated_at (TIMESTAMPTZ)
```

### Session States

- **Open Session**: `check_out_at IS NULL` (employee is checked in)
- **Closed Session**: `check_out_at IS NOT NULL` (session complete)

### Key Constraints

1. **One open session per user**: Enforced by unique index `idx_one_open_session_per_user`
2. **Valid timestamps**: `check_out_at > check_in_at`
3. **No future timestamps**: `check_in_at <= NOW()`, `check_out_at <= NOW() + 5 seconds`
4. **Immutable closed sessions**: Once closed, sessions cannot be modified

## Database Schema

### Table Creation

```sql
CREATE TABLE attendance_sessions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  check_in_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  check_out_at TIMESTAMPTZ DEFAULT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  
  CONSTRAINT valid_session_times CHECK (
    check_out_at IS NULL OR check_out_at > check_in_at
  ),
  CONSTRAINT no_future_checkin CHECK (
    check_in_at <= NOW()
  ),
  CONSTRAINT no_future_checkout CHECK (
    check_out_at IS NULL OR check_out_at <= (NOW() + INTERVAL '5 seconds')
  )
);
```

### Auto-Update Trigger

The `updated_at` column is automatically maintained by a trigger with `SECURITY DEFINER` to ensure it always runs regardless of RLS policies:

```sql
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER
SECURITY DEFINER
AS $$
BEGIN
  NEW.updated_at = NOW();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_attendance_sessions_updated_at
BEFORE UPDATE ON attendance_sessions
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
```

This trigger is included in the migration file.

### Critical Index

```sql
CREATE UNIQUE INDEX idx_one_open_session_per_user 
ON attendance_sessions(user_id) 
WHERE check_out_at IS NULL;
```

This index **prevents double check-in** at the database level.

## RLS Policies

### Employee Permissions

- **INSERT**: Can create own sessions (requires `is_active = true`)
- **UPDATE**: Can close own open sessions (check-out)
- **SELECT**: Can read own session history
- **DELETE**: Not allowed

### Admin Permissions

- **SELECT**: Can read all sessions (read-only in P1)
- **INSERT/UPDATE/DELETE**: Not allowed in P1

## Transaction Patterns

### Check-In Transaction

```sql
BEGIN;

INSERT INTO attendance_sessions (user_id, check_in_at)
VALUES (auth.uid(), NOW())
RETURNING id, check_in_at;

UPDATE users
SET is_checked_in = true
WHERE id = auth.uid();

COMMIT;
```

**Atomicity**: Both operations succeed or both fail.

### Check-Out Transaction

```sql
BEGIN;

-- Lock the open session row
SELECT id, check_in_at
FROM attendance_sessions
WHERE user_id = auth.uid()
  AND check_out_at IS NULL
FOR UPDATE;

-- Close the session
UPDATE attendance_sessions
SET check_out_at = NOW()
WHERE user_id = auth.uid()
  AND check_out_at IS NULL
RETURNING id, check_in_at, check_out_at;

-- Update flag
UPDATE users
SET is_checked_in = false
WHERE id = auth.uid();

COMMIT;
```

**FOR UPDATE lock** prevents race conditions on concurrent check-outs.

## Error Handling

### Error Codes

| Scenario | HTTP Status | Error Code | Message |
|----------|-------------|------------|---------|
| Double check-in | 409 Conflict | `ALREADY_CHECKED_IN` | "You are already checked in" |
| Double check-out | 409 Conflict | `NOT_CHECKED_IN` | "You are not checked in" |
| Inactive user | 403 Forbidden | `ACCOUNT_INACTIVE` | "Your account is inactive" |
| RLS violation | 403 Forbidden | `UNAUTHORIZED` | "You can only modify your own sessions" |
| Transaction failure | 500 Internal Server Error | `TRANSACTION_FAILED` | "Operation failed. Please try again." |

### Network Retry Handling

If a check-in request succeeds but the response is lost:

1. User retries check-in
2. Server detects open session exists (unique index violation)
3. Server returns `409 Conflict` with `ALREADY_CHECKED_IN`
4. App queries current state and updates UI

## Consistency Strategy

### Dual Source of Truth

- **`users.is_checked_in`**: Real-time flag for quick queries
- **`attendance_sessions`**: Historical record (source of truth)

### Synchronization

Both are updated atomically in a transaction. If drift occurs:

**Detection Query:**
```sql
SELECT u.id, u.name, u.is_checked_in, 
       EXISTS(SELECT 1 FROM attendance_sessions 
              WHERE user_id = u.id AND check_out_at IS NULL) as has_open_session
FROM users u
WHERE u.is_checked_in != EXISTS(
  SELECT 1 FROM attendance_sessions 
  WHERE user_id = u.id AND check_out_at IS NULL
);
```

**Repair (Manual):**
```sql
UPDATE users u
SET is_checked_in = EXISTS(
  SELECT 1 FROM attendance_sessions 
  WHERE user_id = u.id AND check_out_at IS NULL
)
WHERE id = $USER_ID;
```

## Migration Instructions

### 1. Run Migration

Execute the migration file in Supabase SQL Editor:

```bash
supabase/migrations/001_create_attendance_sessions.sql
```

### 2. Verify Schema

```sql
-- Check table exists
SELECT * FROM attendance_sessions LIMIT 1;

-- Check indexes
SELECT indexname FROM pg_indexes 
WHERE tablename = 'attendance_sessions';

-- Check RLS is enabled
SELECT tablename, rowsecurity 
FROM pg_tables 
WHERE tablename = 'attendance_sessions';
```

### 3. Test Policies

```sql
-- As employee: Should see only own sessions
SELECT * FROM attendance_sessions;

-- As admin: Should see all sessions
SELECT * FROM attendance_sessions;
```

## Monitoring

**NOTE**: All monitoring queries that calculate "hours" are for DIAGNOSTIC purposes only. These are NOT attendance calculations, NOT exposed to users, and NOT used for payroll. Use only for admin debugging and system health checks.

### View Open Sessions

```sql
SELECT u.name, s.check_in_at, 
       EXTRACT(EPOCH FROM (NOW() - s.check_in_at)) / 3600 as hours_open
FROM attendance_sessions s
JOIN users u ON u.id = s.user_id
WHERE s.check_out_at IS NULL;
```

### Detect Long Open Sessions

```sql
SELECT u.name, s.check_in_at
FROM attendance_sessions s
JOIN users u ON u.id = s.user_id
WHERE s.check_out_at IS NULL
  AND s.check_in_at < NOW() - INTERVAL '12 hours';
```

### Check Consistency

```sql
-- Run drift detection query (see queries/attendance_sessions_helpers.sql)
```

## Out of Scope (v1.3 P1)

The following features are **NOT implemented** in P1:

- ❌ Admin force check-out
- ❌ Auto check-out after X hours
- ❌ Attendance reports
- ❌ Session duration calculations
- ❌ Edit/delete sessions
- ❌ Geofencing
- ❌ Notifications
- ❌ Analytics

These will be considered for P2/P3.

## Troubleshooting

### Issue: Double check-in error

**Symptom**: User gets "Already checked in" error

**Diagnosis**:
```sql
SELECT * FROM attendance_sessions 
WHERE user_id = $USER_ID AND check_out_at IS NULL;
```

**Resolution**: User is already checked in. Update UI to reflect state.

### Issue: Drift detected

**Symptom**: `is_checked_in` doesn't match session state

**Diagnosis**: Run drift detection query

**Resolution**: Run repair query to sync flag with session state

### Issue: Long open session

**Symptom**: Session open for >24 hours

**Diagnosis**:
```sql
SELECT * FROM attendance_sessions 
WHERE check_out_at IS NULL 
  AND check_in_at < NOW() - INTERVAL '24 hours';
```

**Resolution (P1)**: Contact employee to check out manually

**Resolution (P2)**: Admin force check-out feature

## Security Considerations

1. **RLS Enforcement**: All queries go through RLS policies
2. **No Direct Deletes**: Sessions are immutable
3. **Server Time Only**: Never trust device time
4. **Audit Trail**: All sessions logged with timestamps
5. **Cascade Deletes**: Deleting user cascades to sessions

## Performance

### Expected Load

- ~100 employees
- ~200 check-in/check-out operations per day
- ~50,000 sessions per year

### Index Strategy

- `idx_sessions_user_id`: Fast user lookups
- `idx_sessions_check_in_desc`: Recent sessions
- `idx_one_open_session_per_user`: Enforce business rule (partial index, lightweight)

### Query Performance

All queries should execute in <10ms with proper indexes.

## Support

For issues or questions:
1. Check troubleshooting section
2. Review helper queries in `queries/attendance_sessions_helpers.sql`
3. Contact backend team

---

**Version**: v1.3 P1  
**Last Updated**: 2026-02-14  
**Status**: Production Ready
