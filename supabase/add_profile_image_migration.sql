-- ============================================
-- Add profile_image_url column to users table
-- ============================================
-- Run this in Supabase SQL Editor

-- Add profile_image_url column
ALTER TABLE users 
ADD COLUMN IF NOT EXISTS profile_image_url TEXT;

-- Create index for faster queries
CREATE INDEX IF NOT EXISTS idx_users_profile_image 
ON users(profile_image_url) 
WHERE profile_image_url IS NOT NULL;

-- Verify the column was added
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'users' AND column_name = 'profile_image_url';
