# FieldTrack Supabase Setup Guide

Complete database setup for the FieldTrack employee tracking application. All SQL queries are organized into 4 consolidated files that must be run in order.

## Quick Start

Run these files in order in the Supabase SQL Editor:

1. `01_initial_setup.sql` - Core schema, tables, RBAC, and RLS policies
2. `02_storage_setup.sql` - Storage bucket RLS policies (create buckets first!)
3. `03_attendance_system.sql` - Attendance tracking with check-in/check-out
4. `04_admin_features.sql` - Admin capabilities (open sessions, force checkout)

## Detailed Setup Instructions

### Prerequisites

- Supabase project created
- Access to Supabase Dashboard
- SQL Editor access

### Step 1: Initial Setup (01_initial_setup.sql)

**What it does:**
- Creates core tables: `users`, `locations`, `reports`, `expenses`
- Sets up RBAC with `get_my_role()` function
- Configures Row Level Security (RLS) policies
- Creates triggers for auto user creation and location sync
- Adds performance indexes

**How to run:**
1. Go to Supabase Dashboard → SQL Editor
2. Click "New query"
3. Copy and paste contents of `01_initial_setup.sql`
4. Click "Run"
5. Verify: Check that tables exist in Table Editor

**Optional: Bootstrap first admin**
Uncomment the last section and replace email:
```sql
UPDATE public.users
SET role = 'admin', is_active = true
WHERE id = (SELECT id FROM auth.users WHERE email = 'your-admin@example.com');
```

### Step 2: Storage Setup (02_storage_setup.sql)

**What it does:**
- Configures RLS policies for storage buckets
- Sets up access control for expenses, reports, and profile images
- Ensures employees can only access their own files
- Allows admins to view all files

**IMPORTANT: Create buckets first!**

Before running the SQL:

1. Go to Supabase Dashboard → Storage
2. Create these buckets (all PRIVATE):

   **Bucket: expenses**
   - Public: OFF
   - File size limit: 10 MB
   - Allowed MIME types: `image/jpeg`, `image/png`, `image/webp`

   **Bucket: reports**
   - Public: OFF
   - File size limit: 10 MB
   - Allowed MIME types: `image/jpeg`, `image/png`, `image/webp`

   **Bucket: profile-images**
   - Public: OFF
   - File size limit: 5 MB
   - Allowed MIME types: `image/jpeg`, `image/png`, `image/webp`

3. After creating buckets, run `02_storage_setup.sql`
4. Verify: Check policies in Storage → Policies tab

### Step 3: Attendance System (03_attendance_system.sql)

**What it does:**
- Creates `attendance_sessions` table
- Implements check-in/check-out logic
- Adds RPC functions: `check_in()`, `check_out()`, `get_open_session()`
- Enforces one open session per user
- Uses server-side timestamps (no client time manipulation)

**How to run:**
1. Copy and paste contents of `03_attendance_system.sql`
2. Click "Run"
3. Verify: Check that `attendance_sessions` table exists

**Test the RPCs:**
```sql
-- As employee: Check in
SELECT * FROM check_in();

-- As employee: Get open session
SELECT * FROM get_open_session();

-- As employee: Check out
SELECT * FROM check_out();
```

### Step 4: Admin Features (04_admin_features.sql)

**What it does:**
- Creates `admin_audit_log` table for tracking admin actions
- Adds `admin_list_open_sessions()` RPC (P2.1)
- Adds `admin_force_check_out()` RPC (P2.2)
- Implements audit trail for force check-outs

**How to run:**
1. Copy and paste contents of `04_admin_features.sql`
2. Click "Run"
3. Verify: Check that `admin_audit_log` table exists

**Test the admin RPCs:**
```sql
-- As admin: View all open sessions
SELECT * FROM admin_list_open_sessions();

-- As admin: Force check-out a user
SELECT * FROM admin_force_check_out(
    '<user_id>'::UUID,
    'Testing force check-out'
);

-- As admin: View audit log
SELECT * FROM admin_audit_log ORDER BY created_at DESC LIMIT 10;
```

## File Organization

### Setup Files (Run in Order)
- `01_initial_setup.sql` - Core database schema
- `02_storage_setup.sql` - Storage bucket policies
- `03_attendance_system.sql` - Attendance tracking
- `04_admin_features.sql` - Admin capabilities

### Legacy Files (Archived)
The following files are now consolidated into the setup files above:
- `schema.sql` → Merged into `01_initial_setup.sql`
- `rbac_migration.sql` → Merged into `01_initial_setup.sql`
- `add_profile_image_migration.sql` → Merged into `01_initial_setup.sql`
- `storage_expenses_setup.sql` → Merged into `02_storage_setup.sql`
- `storage_reports_setup.sql` → Merged into `02_storage_setup.sql`
- `storage_profile_images_setup.sql` → Merged into `02_storage_setup.sql`
- `complete_attendance_system.sql` → Merged into `03_attendance_system.sql`
- `admin_open_sessions_rpc.sql` → Merged into `04_admin_features.sql`
- `admin_force_checkout_rpc.sql` → Merged into `04_admin_features.sql`

### Utility Files (Keep Separate)
- `sync_hardening_migration.sql` - Idempotency and deduplication (optional)
- `sync_hardening_rollback.sql` - Rollback sync hardening
- `clear_expenses.sql` - Delete all expenses (development only)
- `attendance_sessions_helpers.sql` - Monitoring and debugging queries

## Features by File

### 01_initial_setup.sql
- ✅ Users table with RBAC
- ✅ Locations table with deduplication
- ✅ Reports table
- ✅ Expenses table
- ✅ RLS policies for all tables
- ✅ Auto user creation trigger
- ✅ Location sync trigger
- ✅ Performance indexes

### 02_storage_setup.sql
- ✅ Expenses bucket policies
- ✅ Reports bucket policies
- ✅ Profile images bucket policies
- ✅ Employee file isolation
- ✅ Admin view-all access

### 03_attendance_system.sql
- ✅ Attendance sessions table
- ✅ check_in() RPC
- ✅ check_out() RPC
- ✅ get_open_session() RPC
- ✅ One open session per user constraint
- ✅ Server-side timestamp enforcement
- ✅ SQLSTATE error codes

### 04_admin_features.sql
- ✅ Admin audit log table
- ✅ admin_list_open_sessions() RPC
- ✅ admin_force_check_out() RPC
- ✅ Audit trail for admin actions
- ✅ Security validation

## Error Codes

All RPCs use SQLSTATE error codes for consistent error handling:

- `P0001` - ALREADY_CHECKED_IN
- `P0002` - NOT_CHECKED_IN
- `P0003` - ACCOUNT_INACTIVE
- `P0004` - NOT_AUTHENTICATED / AUTHENTICATION_REQUIRED
- `P0005` - ADMIN_REQUIRED

## Security Features

- All tables have Row Level Security (RLS) enabled
- Employees can only access their own data
- Admins can view all data
- SECURITY DEFINER functions with explicit role validation
- Storage buckets are private with RLS policies
- Audit trail for admin actions
- No client-side timestamp manipulation

## Verification Queries

After setup, verify everything works:

```sql
-- Check all tables exist
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
  AND table_type = 'BASE TABLE'
ORDER BY table_name;

-- Check all RLS policies
SELECT schemaname, tablename, policyname 
FROM pg_policies 
WHERE schemaname = 'public'
ORDER BY tablename, policyname;

-- Check all functions
SELECT routine_name, routine_type, security_type
FROM information_schema.routines
WHERE routine_schema = 'public'
  AND routine_type = 'FUNCTION'
ORDER BY routine_name;

-- Check storage policies
SELECT schemaname, tablename, policyname
FROM pg_policies
WHERE tablename = 'objects'
ORDER BY policyname;
```

## Troubleshooting

### Issue: Policies not working
**Solution:** Ensure RLS is enabled on all tables:
```sql
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE attendance_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE admin_audit_log ENABLE ROW LEVEL SECURITY;
```

### Issue: Storage policies not applying
**Solution:** Verify buckets are created and set to PRIVATE (not public)

### Issue: Admin functions return P0005 error
**Solution:** Ensure user has `role = 'admin'` in users table:
```sql
UPDATE users SET role = 'admin', is_active = true WHERE email = 'your-email@example.com';
```

### Issue: Duplicate location inserts
**Solution:** Run `sync_hardening_migration.sql` to add unique constraint

## Migration from Old Setup

If you previously ran individual SQL files:

1. **Don't re-run setup files** - They use `IF NOT EXISTS` and `DROP POLICY IF EXISTS`
2. **Safe to run** - All setup files are idempotent
3. **Verify** - Check that all features work after consolidation

## Next Steps

After completing setup:

1. Configure Android app `local.properties` with Supabase credentials
2. Test employee check-in/check-out flow
3. Test admin features (open sessions, force checkout)
4. Monitor `admin_audit_log` for admin actions
5. Use `attendance_sessions_helpers.sql` for monitoring

## Support

For issues or questions:
- Check Supabase logs in Dashboard → Logs
- Review RLS policies in Dashboard → Authentication → Policies
- Test RPCs in SQL Editor
- Check Android app logs for API errors
