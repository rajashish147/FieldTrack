-- ============================================
-- Supabase Storage: Expense Bill Images Bucket Setup
-- ============================================
-- Run this in Supabase SQL Editor

-- Note: Buckets are created via Supabase Dashboard (Storage section)
-- Bucket name: "expenses"
-- Public: NO (private bucket with RLS)

-- After creating the bucket in the dashboard, run these RLS policies:

-- ============================================
-- STORAGE RLS POLICIES FOR EXPENSES
-- ============================================

-- Policy 1: Employees can upload their own bill images
CREATE POLICY "Employees can upload own bill images"
ON storage.objects FOR INSERT
WITH CHECK (
    bucket_id = 'expenses' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Policy 2: Employees can update their own bill images
CREATE POLICY "Employees can update own bill images"
ON storage.objects FOR UPDATE
USING (
    bucket_id = 'expenses' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Policy 3: Employees can delete their own bill images
CREATE POLICY "Employees can delete own bill images"
ON storage.objects FOR DELETE
USING (
    bucket_id = 'expenses' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Policy 4: Employees can view their own bill images
CREATE POLICY "Employees can view own bill images"
ON storage.objects FOR SELECT
USING (
    bucket_id = 'expenses' 
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- Policy 5: Admins can view all bill images (for approval)
CREATE POLICY "Admins can view all bill images"
ON storage.objects FOR SELECT
USING (
    bucket_id = 'expenses' 
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
WHERE tablename = 'objects' AND policyname LIKE '%bill%';

-- ============================================
-- SETUP INSTRUCTIONS
-- ============================================
-- 1. Go to Supabase Dashboard → Storage
-- 2. Check if "expenses" bucket exists
--    - If it exists and is PUBLIC, you need to:
--      a. Download all existing images (backup)
--      b. Delete the public bucket
--      c. Create new private bucket
--      d. Re-upload images
--    - If it doesn't exist, create it:
--      * Click "New bucket"
--      * Name: expenses
--      * Public: OFF (keep it private)
--      * File size limit: 10 MB (recommended)
--      * Allowed MIME types: image/jpeg, image/png, image/webp
--      * Click "Create bucket"
-- 3. Run this SQL script to add RLS policies
-- 4. Test: Employee should be able to upload and view their own bills
-- 5. Test: Admin should be able to view all bills for approval

-- ============================================
-- MIGRATION FROM PUBLIC TO PRIVATE BUCKET
-- ============================================
-- If you already have a public "expenses" bucket with images:

-- Step 1: Check existing images
-- SELECT name, bucket_id, owner, created_at 
-- FROM storage.objects 
-- WHERE bucket_id = 'expenses' 
-- ORDER BY created_at DESC;

-- Step 2: Backup strategy
-- Download all images from Supabase Dashboard before proceeding

-- Step 3: After backup, delete public bucket and create private one
-- Then re-upload images with correct folder structure: {userId}/{timestamp}.jpg

-- Step 4: Update database URLs if needed
-- Old format: /storage/v1/object/public/expenses/expenses/{userId}/{timestamp}.jpg
-- New format: /storage/v1/object/expenses/{userId}/{timestamp}.jpg

-- UPDATE expenses 
-- SET bill_image_url = REPLACE(
--     bill_image_url, 
--     '/storage/v1/object/public/expenses/expenses/', 
--     '/storage/v1/object/expenses/'
-- )
-- WHERE bill_image_url LIKE '%/public/expenses/expenses/%';

-- ============================================
-- SECURITY NOTES
-- ============================================
-- 1. Bucket is PRIVATE - no public access
-- 2. Employees can only access their own bill images
-- 3. Admins can access all bill images for approval
-- 4. Images are organized by userId: expenses/{userId}/{timestamp}.jpg
-- 5. Auth headers required for all image requests
-- 6. RLS policies enforce access control at storage level
