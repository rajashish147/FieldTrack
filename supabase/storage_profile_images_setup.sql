-- ============================================
-- Supabase Storage: Profile Images Bucket Setup
-- ============================================
-- Run this in Supabase SQL Editor

-- Note: Buckets are created via Supabase Dashboard (Storage section)
-- Bucket name: "profile-images"
-- Public: NO (private bucket with RLS)

-- After creating the bucket in the dashboard, run these RLS policies:

-- ============================================
-- STORAGE RLS POLICIES
-- ============================================

-- Policy 1: Employees can upload their own profile image
CREATE POLICY "Employees can upload own profile image"
ON storage.objects FOR INSERT
WITH CHECK (
    bucket_id = 'profile-images' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Policy 2: Employees can update their own profile image
CREATE POLICY "Employees can update own profile image"
ON storage.objects FOR UPDATE
USING (
    bucket_id = 'profile-images' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Policy 3: Employees can delete their own profile image
CREATE POLICY "Employees can delete own profile image"
ON storage.objects FOR DELETE
USING (
    bucket_id = 'profile-images' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Policy 4: Employees can view their own profile image
CREATE POLICY "Employees can view own profile image"
ON storage.objects FOR SELECT
USING (
    bucket_id = 'profile-images' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Policy 5: Admins can view all profile images
CREATE POLICY "Admins can view all profile images"
ON storage.objects FOR SELECT
USING (
    bucket_id = 'profile-images' 
    AND EXISTS (
        SELECT 1 FROM users WHERE id = auth.uid() AND role = 'admin'
    )
);

-- ============================================
-- VERIFICATION
-- ============================================
-- Check that policies are created
SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual
FROM pg_policies
WHERE tablename = 'objects' AND policyname LIKE '%profile%';

-- ============================================
-- SETUP INSTRUCTIONS
-- ============================================
-- 1. Go to Supabase Dashboard → Storage
-- 2. Click "New bucket"
-- 3. Name: profile-images
-- 4. Public: OFF (keep it private)
-- 5. File size limit: 5 MB (recommended)
-- 6. Allowed MIME types: image/jpeg, image/png, image/webp
-- 7. Click "Create bucket"
-- 8. Run this SQL script to add RLS policies
