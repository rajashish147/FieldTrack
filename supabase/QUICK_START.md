# FieldTrack Supabase Quick Start

## TL;DR

Run these 4 SQL files in order in Supabase SQL Editor:

```
1. setup/01_initial_setup.sql       ← Core database
2. Create buckets in Dashboard      ← Storage setup
3. setup/02_storage_setup.sql       ← Storage policies
4. setup/03_attendance_system.sql   ← Attendance tracking
5. setup/04_admin_features.sql      ← Admin features
```

Done! Your database is ready.

## Storage Buckets (Step 2)

Before running `02_storage_setup.sql`, create these buckets:

**Dashboard → Storage → New Bucket**

| Bucket Name | Public | Size Limit | MIME Types |
|-------------|--------|------------|------------|
| expenses | OFF | 10 MB | image/jpeg, image/png, image/webp |
| reports | OFF | 10 MB | image/jpeg, image/png, image/webp |
| profile-images | OFF | 5 MB | image/jpeg, image/png, image/webp |

## Bootstrap First Admin

After setup, make yourself admin:

```sql
UPDATE users 
SET role = 'admin', is_active = true 
WHERE email = 'your-email@example.com';
```

## Test It Works

```sql
-- Test check-in (as employee)
SELECT * FROM check_in();

-- Test check-out (as employee)
SELECT * FROM check_out();

-- Test admin view (as admin)
SELECT * FROM admin_list_open_sessions();
```

## Need Help?

See `setup/README.md` for detailed instructions and troubleshooting.
