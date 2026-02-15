-- ============================================================================
-- FIELDTRACK STORAGE BUCKETS SETUP
-- ============================================================================
-- Description: RLS policies for all storage buckets (expenses, reports, profile-images)
-- Run Order: 2 (After 01_initial_setup.sql)
-- Prerequisites: Buckets must be created in Supabase Dashboard first
-- ============================================================================

-- ============================================================================
-- BUCKET CREATION INSTRUCTIONS
-- ============================================================================
-- Before running this script, create these buckets in Supabase Dashboard:
--
-- 1. Go to Supabase Dashboard → Storage
-- 2. Create the following buckets (all PRIVATE):
--
--    Bucket: "expenses"
--    - Public: OFF
--    - File size limit: 10 MB
--    - Allowed MIME types: image/jpeg, image/png, image/webp
--
--    Bucket: "reports"
--    - Public: OFF
--    - File size limit: 10 MB
--    - Allowed MIME types: image/jpeg, image/png, image/webp
--
--    Bucket: "profile-images"
--    - Public: OFF
--    - File size limit: 5 MB
--    - Allowed MIME types: image/jpeg, image/png, image/webp
--
-- 3. After creating buckets, run this SQL script
-- ============================================================================

-- ============================================================================
-- PART 1: EXPENSES BUCKET POLICIES
-- ============================================================================

DROP POLICY IF EXISTS "Employees can upload own bill images" ON storage.objects;
DROP POLICY IF EXISTS "Employees can update own bill images" ON storage.objects;
DROP POLICY IF EXISTS "Employees can delete own bill images" ON storage.objects;
DROP POLICY IF EXISTS "Employees can view own bill images" ON storage.objects;
DROP POLICY IF EXISTS "Admins can view all bill images" ON storage.objects;

CREATE POLICY "Employees can upload own bill images"
ON storage.objects FOR INSERT
WITH CHECK (
    bucket_id = 'expenses' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Employees can update own bill images"
ON storage.objects FOR UPDATE
USING (
    bucket_id = 'expenses' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Employees can delete own bill images"
ON storage.objects FOR DELETE
USING (
    bucket_id = 'expenses' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Employees can view own bill images"
ON storage.objects FOR SELECT
USING (
    bucket_id = 'expenses' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Admins can view all bill images"
ON storage.objects FOR SELECT
USING (
    bucket_id = 'expenses' 
    AND EXISTS (
        SELECT 1 FROM users WHERE id = auth.uid() AND role = 'admin'
    )
);

-- ============================================================================
-- PART 2: REPORTS BUCKET POLICIES
-- ============================================================================

DROP POLICY IF EXISTS "Employees can upload own report images" ON storage.objects;
DROP POLICY IF EXISTS "Employees can update own report images" ON storage.objects;
DROP POLICY IF EXISTS "Employees can delete own report images" ON storage.objects;
DROP POLICY IF EXISTS "Employees can view own report images" ON storage.objects;
DROP POLICY IF EXISTS "Admins can view all report images" ON storage.objects;

CREATE POLICY "Employees can upload own report images"
ON storage.objects FOR INSERT
WITH CHECK (
    bucket_id = 'reports' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Employees can update own report images"
ON storage.objects FOR UPDATE
USING (
    bucket_id = 'reports' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Employees can delete own report images"
ON storage.objects FOR DELETE
USING (
    bucket_id = 'reports' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Employees can view own report images"
ON storage.objects FOR SELECT
USING (
    bucket_id = 'reports' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Admins can view all report images"
ON storage.objects FOR SELECT
USING (
    bucket_id = 'reports' 
    AND EXISTS (
        SELECT 1 FROM users WHERE id = auth.uid() AND role = 'admin'
    )
);

-- ============================================================================
-- PART 3: PROFILE-IMAGES BUCKET POLICIES
-- ============================================================================

DROP POLICY IF EXISTS "Employees can upload own profile image" ON storage.objects;
DROP POLICY IF EXISTS "Employees can update own profile image" ON storage.objects;
DROP POLICY IF EXISTS "Employees can delete own profile image" ON storage.objects;
DROP POLICY IF EXISTS "Employees can view own profile image" ON storage.objects;
DROP POLICY IF EXISTS "Admins can view all profile images" ON storage.objects;

CREATE POLICY "Employees can upload own profile image"
ON storage.objects FOR INSERT
WITH CHECK (
    bucket_id = 'profile-images' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Employees can update own profile image"
ON storage.objects FOR UPDATE
USING (
    bucket_id = 'profile-images' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Employees can delete own profile image"
ON storage.objects FOR DELETE
USING (
    bucket_id = 'profile-images' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Employees can view own profile image"
ON storage.objects FOR SELECT
USING (
    bucket_id = 'profile-images' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

CREATE POLICY "Admins can view all profile images"
ON storage.objects FOR SELECT
USING (
    bucket_id = 'profile-images' 
    AND EXISTS (
        SELECT 1 FROM users WHERE id = auth.uid() AND role = 'admin'
    )
);

-- ============================================================================
-- VERIFICATION
-- ============================================================================

-- Check that all policies are created
SELECT schemaname, tablename, policyname, permissive, roles, cmd
FROM pg_policies
WHERE tablename = 'objects' 
  AND (
    policyname LIKE '%bill%' 
    OR policyname LIKE '%report%' 
    OR policyname LIKE '%profile%'
  )
ORDER BY policyname;

-- ============================================================================
-- STORAGE SETUP COMPLETE
-- ============================================================================
-- Security notes:
-- - All buckets are PRIVATE (no public access)
-- - Employees can only access their own files
-- - Admins can view all files for approval/monitoring
-- - Files organized by userId: {bucket}/{userId}/{filename}
-- - Auth headers required for all requests
-- ============================================================================
