-- ============================================
-- Clear All Expenses
-- ============================================
-- WARNING: This will permanently delete all expense records
-- Run this in Supabase SQL Editor

-- Delete all expenses from the database
DELETE FROM expenses;

-- Verify deletion
SELECT COUNT(*) as remaining_expenses FROM expenses;

-- ============================================
-- Optional: Clear expense images from storage
-- ============================================
-- Note: This SQL only clears database records
-- To delete images from storage bucket:
-- 1. Go to Supabase Dashboard → Storage → expenses bucket
-- 2. Select all files
-- 3. Click Delete
-- OR use the Supabase Storage API/SDK to delete programmatically

-- ============================================
-- Reset auto-increment (if using serial IDs)
-- ============================================
-- Not needed for UUID-based IDs, but included for reference
-- ALTER SEQUENCE expenses_id_seq RESTART WITH 1;
