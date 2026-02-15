-- ============================================
-- Supabase Storage: Report Images Bucket Setup
-- ============================================
-- Run this in Supabase SQL Editor

-- Note: Buckets are created via Supabase Dashboard (Storage section)
-- Bucket name: "reports"
-- Public: NO (private bucket with RLS)

-- After creating the bucket in the dashboard, run these RLS policies:

-- ============================================
-- STORAGE RLS POLICIES FOR REPORTS
-- ============================================

-- Policy 1: Employees can upload their own report images
CREATE POLICY "Employees can upload own report images"
ON storage.objects FOR INSERT
WITH CHECK (
    bucket_id = 'reports' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Policy 2: Employees can update their own report images
CREATE POLICY "Employees can update own report images"
ON storage.objects FOR UPDATE
USING (
    bucket_id = 'reports' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Policy 3: Employees can delete their own report images
CREATE POLICY "Employees can delete own report images"
ON storage.objects FOR DELETE
USING (
    bucket_id = 'reports' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Policy 4: Employees can view their own report images
CREATE POLICY "Employees can view own report images"
ON storage.objects FOR SELECT
USING (
    bucket_id = 'reports' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Policy 5: Admins can view all report images
CREATE POLICY "Admins can view all report images"
ON storage.objects FOR SELECT
USING (
    bucket_id = 'reports' 
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
WHERE tablename = 'objects' AND policyname LIKE '%report%';

-- ============================================
-- SETUP INSTRUCTIONS
-- ============================================
-- 1. Go to Supabase Dashboard → Storage
-- 2. Check if "reports" bucket exists
--    - If it exists and is PUBLIC, you need to:
--      a. Download all existing images (backup)
--      b. Delete the public bucket
--      c. Create new private bucket
--      d. Re-upload images
--    - If it doesn't exist, create it:
--      * Click "New bucket"
--      * Name: reports
--      * Public: OFF (keep it private)
--      * File size limit: 10 MB (recommended)
--      * Allowed MIME types: image/jpeg, image/png, image/webp
--      * Click "Create bucket"
-- 3. Run this SQL script to add RLS policies
-- 4. Test: Employee should be able to upload and view their own reports
-- 5. Test: Admin should be able to view all reports

-- ============================================
-- MIGRATION FROM PUBLIC TO PRIVATE BUCKET
-- ============================================
-- If you already have a public "reports" bucket with images:

-- Step 1: Check existing images
-- SELECT name, bucket_id, owner, created_at 
-- FROM storage.objects 
-- WHERE bucket_id = 'reports' 
-- ORDER BY created_at DESC;

-- Step 2: Backup strategy
-- Download all images from Supabase Dashboard before proceeding

-- Step 3: After backup, delete public bucket and create private one
-- Then re-upload images with correct folder structure: reports/{userId}/{date}_{timestamp}.jpg

-- Step 4: Update database URLs if needed
-- Old format: /storage/v1/object/public/reports/reports/{userId}/{date}_{timestamp}.jpg
-- New format: /storage/v1/object/reports/reports/{userId}/{date}_{timestamp}.jpg

-- UPDATE reports 
-- SET optional_image_url = REPLACE(
--     optional_image_url, 
--     '/storage/v1/object/public/reports/', 
--     '/storage/v1/object/reports/'
-- )
-- WHERE optional_image_url LIKE '%/public/reports/%';

-- ============================================
-- SECURITY NOTES
-- ============================================
-- 1. Bucket is PRIVATE - no public access
-- 2. Employees can only access their own report images
-- 3. Admins can access all report images
-- 4. Images are organized by userId: reports/{userId}/{date}_{timestamp}.jpg
-- 5. Auth headers required for all image requests
-- 6. RLS policies enforce access control at storage level
