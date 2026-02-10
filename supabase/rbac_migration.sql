-- ============================================================
-- RBAC Migration for FieldTrack
-- Run this in Supabase SQL Editor (Dashboard → SQL Editor → New Query)
-- ============================================================

-- ────────────────────────────────────────────────────────────
-- 1. Add is_active column to users table
-- ────────────────────────────────────────────────────────────

ALTER TABLE public.users
  ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT false;

-- Activate ALL existing users so they are not locked out
UPDATE public.users SET is_active = true WHERE is_active = false;

-- ────────────────────────────────────────────────────────────
-- 2. SECURITY DEFINER function to read current user's role
--    This function bypasses RLS, preventing infinite recursion
--    when RLS policies on `users` need to check the user's role.
-- ────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.get_my_role()
RETURNS TEXT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
  SELECT role FROM public.users WHERE id = auth.uid();
$$;

-- Revoke direct execution from anon, grant only to authenticated
REVOKE ALL ON FUNCTION public.get_my_role() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_my_role() TO authenticated;

-- ────────────────────────────────────────────────────────────
-- 3. Drop existing RLS policies on users table
--    (these used recursive subqueries)
-- ────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "Users can view own profile"     ON public.users;
DROP POLICY IF EXISTS "Admins can view all users"      ON public.users;
DROP POLICY IF EXISTS "Users can insert own profile"   ON public.users;
DROP POLICY IF EXISTS "Users can update own check-in"  ON public.users;

-- ────────────────────────────────────────────────────────────
-- 4. Create new recursion-safe RLS policies
-- ────────────────────────────────────────────────────────────

-- RLS must be enabled (idempotent)
ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;

-- 4a. SELECT: employees see only their own row
CREATE POLICY "employees_select_own"
  ON public.users FOR SELECT
  USING (auth.uid() = id);

-- 4b. SELECT: admins see all rows
--     Uses get_my_role() to avoid recursion
CREATE POLICY "admins_select_all"
  ON public.users FOR SELECT
  USING (public.get_my_role() = 'admin');

-- 4c. INSERT: users can insert their own profile (trigger fallback)
CREATE POLICY "users_insert_own"
  ON public.users FOR INSERT
  WITH CHECK (auth.uid() = id);

-- 4d. UPDATE: employees can update only their own row,
--     but CANNOT change role or is_active
CREATE POLICY "employees_update_own"
  ON public.users FOR UPDATE
  USING (auth.uid() = id)
  WITH CHECK (
    auth.uid() = id
    AND role = (SELECT role FROM public.users WHERE id = auth.uid())
    AND is_active = (SELECT is_active FROM public.users WHERE id = auth.uid())
  );

-- 4e. UPDATE: admins can update any row (including role + is_active)
CREATE POLICY "admins_update_all"
  ON public.users FOR UPDATE
  USING (public.get_my_role() = 'admin');

-- ────────────────────────────────────────────────────────────
-- 5. Update handle_new_user() trigger
--    New signups get role='employee', is_active=false
-- ────────────────────────────────────────────────────────────

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
  INSERT INTO public.users (id, name, role, is_active, is_checked_in, created_at)
  VALUES (
    NEW.id,
    COALESCE(NEW.raw_user_meta_data ->> 'name', 'Employee'),
    'employee',
    false,          -- new users need admin approval
    false,
    NOW()
  );
  RETURN NEW;
END;
$$;

-- ────────────────────────────────────────────────────────────
-- 6. (OPTIONAL) Bootstrap the first admin user
--    Uncomment and replace the email below with your admin email.
-- ────────────────────────────────────────────────────────────

-- UPDATE public.users
-- SET role = 'admin', is_active = true
-- WHERE id = (SELECT id FROM auth.users WHERE email = 'admin@example.com');
