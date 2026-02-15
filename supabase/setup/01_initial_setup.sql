-- ============================================================================
-- FIELDTRACK INITIAL DATABASE SETUP
-- ============================================================================
-- Description: Complete initial database setup including schema, RBAC, and core tables
-- Run Order: 1 (Run this first)
-- ============================================================================

-- ============================================================================
-- PART 1: CORE SCHEMA
-- ============================================================================

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- Users table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    email TEXT,
    phone TEXT,
    profile_image_url TEXT,
    role TEXT NOT NULL DEFAULT 'employee' CHECK (role IN ('employee', 'admin')),
    is_checked_in BOOLEAN NOT NULL DEFAULT false,
    is_active BOOLEAN NOT NULL DEFAULT false,
    last_synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Locations table
CREATE TABLE IF NOT EXISTS locations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy REAL NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    
    -- Unique constraint for duplicate prevention (idempotency)
    CONSTRAINT unique_location_point UNIQUE (user_id, timestamp, latitude, longitude),
    
    -- Validation constraints
    CONSTRAINT valid_latitude CHECK (latitude >= -90 AND latitude <= 90),
    CONSTRAINT valid_longitude CHECK (longitude >= -180 AND longitude <= 180),
    CONSTRAINT valid_accuracy CHECK (accuracy > 0 AND accuracy < 10000)
);

-- Reports table
CREATE TABLE IF NOT EXISTS reports (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    date DATE NOT NULL DEFAULT CURRENT_DATE,
    summary TEXT NOT NULL,
    optional_image_url TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Expenses table
CREATE TABLE IF NOT EXISTS expenses (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    amount DECIMAL(10, 2) NOT NULL,
    category TEXT NOT NULL,
    bill_image_url TEXT NOT NULL DEFAULT '',
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
    timestamp TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ============================================================================
-- PART 2: INDEXES FOR PERFORMANCE
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_locations_user_id ON locations(user_id);
CREATE INDEX IF NOT EXISTS idx_locations_timestamp ON locations(timestamp);
CREATE INDEX IF NOT EXISTS idx_locations_user_timestamp ON locations(user_id, timestamp);
CREATE INDEX IF NOT EXISTS idx_locations_dedup ON locations(user_id, timestamp, latitude, longitude);
CREATE INDEX IF NOT EXISTS idx_reports_user_id ON reports(user_id);
CREATE INDEX IF NOT EXISTS idx_reports_date ON reports(date);
CREATE INDEX IF NOT EXISTS idx_expenses_user_id ON expenses(user_id);
CREATE INDEX IF NOT EXISTS idx_expenses_status ON expenses(status);
CREATE INDEX IF NOT EXISTS idx_users_name_search ON users USING gin(to_tsvector('english', name));
CREATE INDEX IF NOT EXISTS idx_users_email_search ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_last_synced ON users(last_synced_at DESC);
CREATE INDEX IF NOT EXISTS idx_users_profile_image ON users(profile_image_url) WHERE profile_image_url IS NOT NULL;

-- ============================================================================
-- PART 3: RBAC - SECURITY DEFINER FUNCTION
-- ============================================================================

CREATE OR REPLACE FUNCTION public.get_my_role()
RETURNS TEXT
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $
  SELECT role FROM public.users WHERE id = auth.uid();
$;

REVOKE ALL ON FUNCTION public.get_my_role() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.get_my_role() TO authenticated;

-- ============================================================================
-- PART 4: ROW LEVEL SECURITY POLICIES
-- ============================================================================

-- Enable RLS on all tables
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE expenses ENABLE ROW LEVEL SECURITY;

-- ────────────────────────────────────────────────────────────
-- USERS TABLE POLICIES
-- ────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "employees_select_own" ON users;
DROP POLICY IF EXISTS "admins_select_all" ON users;
DROP POLICY IF EXISTS "users_insert_own" ON users;
DROP POLICY IF EXISTS "employees_update_own" ON users;
DROP POLICY IF EXISTS "admins_update_all" ON users;

CREATE POLICY "employees_select_own"
  ON users FOR SELECT
  USING (auth.uid() = id);

CREATE POLICY "admins_select_all"
  ON users FOR SELECT
  USING (public.get_my_role() = 'admin');

CREATE POLICY "users_insert_own"
  ON users FOR INSERT
  WITH CHECK (auth.uid() = id);

CREATE POLICY "employees_update_own"
  ON users FOR UPDATE
  USING (auth.uid() = id)
  WITH CHECK (
    auth.uid() = id
    AND role = (SELECT role FROM public.users WHERE id = auth.uid())
    AND is_active = (SELECT is_active FROM public.users WHERE id = auth.uid())
  );

CREATE POLICY "admins_update_all"
  ON users FOR UPDATE
  USING (public.get_my_role() = 'admin');

-- ────────────────────────────────────────────────────────────
-- LOCATIONS TABLE POLICIES
-- ────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "Users can insert own locations" ON locations;
DROP POLICY IF EXISTS "Users can view own locations" ON locations;
DROP POLICY IF EXISTS "Admins can view all locations" ON locations;

CREATE POLICY "Users can insert own locations"
    ON locations FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can view own locations"
    ON locations FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Admins can view all locations"
    ON locations FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM users WHERE id = auth.uid() AND role = 'admin'
        )
    );

-- ────────────────────────────────────────────────────────────
-- REPORTS TABLE POLICIES
-- ────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "Users can insert own reports" ON reports;
DROP POLICY IF EXISTS "Users can view own reports" ON reports;
DROP POLICY IF EXISTS "Admins can view all reports" ON reports;

CREATE POLICY "Users can insert own reports"
    ON reports FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can view own reports"
    ON reports FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Admins can view all reports"
    ON reports FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM users WHERE id = auth.uid() AND role = 'admin'
        )
    );

-- ────────────────────────────────────────────────────────────
-- EXPENSES TABLE POLICIES
-- ────────────────────────────────────────────────────────────

DROP POLICY IF EXISTS "Users can insert own expenses" ON expenses;
DROP POLICY IF EXISTS "Users can view own expenses" ON expenses;
DROP POLICY IF EXISTS "Admins can view all expenses" ON expenses;
DROP POLICY IF EXISTS "Admins can update expense status" ON expenses;

CREATE POLICY "Users can insert own expenses"
    ON expenses FOR INSERT
    WITH CHECK (auth.uid() = user_id);

CREATE POLICY "Users can view own expenses"
    ON expenses FOR SELECT
    USING (auth.uid() = user_id);

CREATE POLICY "Admins can view all expenses"
    ON expenses FOR SELECT
    USING (
        EXISTS (
            SELECT 1 FROM users WHERE id = auth.uid() AND role = 'admin'
        )
    );

CREATE POLICY "Admins can update expense status"
    ON expenses FOR UPDATE
    USING (
        EXISTS (
            SELECT 1 FROM users WHERE id = auth.uid() AND role = 'admin'
        )
    );

-- ============================================================================
-- PART 5: TRIGGERS AND FUNCTIONS
-- ============================================================================

-- Function to auto-create user profile on signup
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $
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
$;

DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION public.handle_new_user();

-- Function to auto-update user's last_synced_at when locations are inserted
CREATE OR REPLACE FUNCTION update_user_last_sync()
RETURNS TRIGGER AS $
BEGIN
  UPDATE users 
  SET last_synced_at = NEW.timestamp 
  WHERE id = NEW.user_id 
    AND (last_synced_at IS NULL OR NEW.timestamp > last_synced_at);
  RETURN NEW;
END;
$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trigger_update_last_sync ON locations;
CREATE TRIGGER trigger_update_last_sync
AFTER INSERT ON locations
FOR EACH ROW
EXECUTE FUNCTION update_user_last_sync();

-- ============================================================================
-- PART 6: BOOTSTRAP FIRST ADMIN (OPTIONAL)
-- ============================================================================
-- Uncomment and replace the email below with your admin email

-- UPDATE public.users
-- SET role = 'admin', is_active = true
-- WHERE id = (SELECT id FROM auth.users WHERE email = 'admin@example.com');

-- ============================================================================
-- SETUP COMPLETE
-- ============================================================================
-- Next steps:
-- 1. Run 02_storage_setup.sql to configure storage buckets
-- 2. Run 03_attendance_system.sql to set up attendance tracking
-- 3. Run 04_admin_features.sql to enable admin capabilities
-- ============================================================================
