-- ============================================
-- FieldTrack Supabase Migration
-- File: 00000000000000_init.sql
-- ============================================

-- ============================================
-- EXTENSIONS
-- ============================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================
-- ENUM: user_role
-- ============================================

DO $$
BEGIN
IF NOT EXISTS (
SELECT 1 FROM pg_type WHERE typname = 'user_role'
) THEN
CREATE TYPE public.user_role AS ENUM (
'ADMIN',
'EMPLOYEE'
);
END IF;
END$$;

-- ============================================
-- TABLE: users
-- ============================================

CREATE TABLE IF NOT EXISTS public.users (

```
id UUID PRIMARY KEY
REFERENCES auth.users(id)
ON DELETE CASCADE,

name TEXT NOT NULL,

email TEXT,

phone TEXT,

role public.user_role
NOT NULL
DEFAULT 'EMPLOYEE',

profile_image_url TEXT,

is_active BOOLEAN
NOT NULL
DEFAULT TRUE,

last_synced_at TIMESTAMPTZ,

created_at TIMESTAMPTZ
NOT NULL
DEFAULT now()
```

);

-- INDEXES

CREATE INDEX IF NOT EXISTS idx_users_email
ON public.users(email);

CREATE INDEX IF NOT EXISTS idx_users_role
ON public.users(role);

CREATE INDEX IF NOT EXISTS idx_users_last_synced
ON public.users(last_synced_at DESC);

-- ============================================
-- TABLE: attendance_sessions
-- ============================================

CREATE TABLE IF NOT EXISTS public.attendance_sessions (

```
id UUID PRIMARY KEY
DEFAULT gen_random_uuid(),

user_id UUID NOT NULL
REFERENCES public.users(id)
ON DELETE CASCADE,

check_in_at TIMESTAMPTZ NOT NULL,

check_out_at TIMESTAMPTZ,

created_at TIMESTAMPTZ
NOT NULL
DEFAULT now(),

updated_at TIMESTAMPTZ
NOT NULL
DEFAULT now(),

CONSTRAINT valid_session_times
CHECK (
    check_out_at IS NULL
    OR check_out_at > check_in_at
),

CONSTRAINT no_future_checkin
CHECK (
    check_in_at <= now()
),

CONSTRAINT no_future_checkout
CHECK (
    check_out_at IS NULL
    OR check_out_at <= now()
)
```

);

CREATE INDEX IF NOT EXISTS idx_sessions_user
ON public.attendance_sessions(user_id);

CREATE UNIQUE INDEX IF NOT EXISTS idx_one_open_session_per_user
ON public.attendance_sessions(user_id)
WHERE check_out_at IS NULL;

-- ============================================
-- TABLE: locations
-- ============================================

CREATE TABLE IF NOT EXISTS public.locations (

```
id UUID PRIMARY KEY
DEFAULT gen_random_uuid(),

user_id UUID NOT NULL
REFERENCES public.users(id)
ON DELETE CASCADE,

session_id UUID
REFERENCES public.attendance_sessions(id)
ON DELETE SET NULL,

latitude DOUBLE PRECISION NOT NULL,

longitude DOUBLE PRECISION NOT NULL,

accuracy REAL NOT NULL,

timestamp TIMESTAMPTZ NOT NULL,

CONSTRAINT valid_latitude
CHECK (
    latitude BETWEEN -90 AND 90
),

CONSTRAINT valid_longitude
CHECK (
    longitude BETWEEN -180 AND 180
),

CONSTRAINT valid_accuracy
CHECK (
    accuracy >= 0
)
```

);

CREATE INDEX IF NOT EXISTS idx_locations_user
ON public.locations(user_id);

CREATE INDEX IF NOT EXISTS idx_locations_session
ON public.locations(session_id);

CREATE INDEX IF NOT EXISTS idx_locations_timestamp
ON public.locations(timestamp);

CREATE UNIQUE INDEX IF NOT EXISTS unique_location_point
ON public.locations(
user_id,
timestamp,
latitude,
longitude
);

-- ============================================
-- TABLE: expenses
-- ============================================

CREATE TABLE IF NOT EXISTS public.expenses (

```
id UUID PRIMARY KEY
DEFAULT gen_random_uuid(),

user_id UUID NOT NULL
REFERENCES public.users(id)
ON DELETE CASCADE,

amount NUMERIC NOT NULL,

category TEXT NOT NULL,

bill_image_url TEXT NOT NULL,

status TEXT NOT NULL DEFAULT 'PENDING',

timestamp TIMESTAMPTZ NOT NULL DEFAULT now()
```

);

CREATE INDEX IF NOT EXISTS idx_expenses_user
ON public.expenses(user_id);

CREATE INDEX IF NOT EXISTS idx_expenses_status
ON public.expenses(status);

-- ============================================
-- TABLE: reports
-- ============================================

CREATE TABLE IF NOT EXISTS public.reports (

```
id UUID PRIMARY KEY
DEFAULT gen_random_uuid(),

user_id UUID NOT NULL
REFERENCES public.users(id)
ON DELETE CASCADE,

date DATE NOT NULL,

summary TEXT NOT NULL,

optional_image_url TEXT,

created_at TIMESTAMPTZ NOT NULL DEFAULT now()
```

);

CREATE INDEX IF NOT EXISTS idx_reports_user
ON public.reports(user_id);

CREATE INDEX IF NOT EXISTS idx_reports_date
ON public.reports(date);

-- ============================================
-- TABLE: admin_audit_log
-- ============================================

CREATE TABLE IF NOT EXISTS public.admin_audit_log (

```
id BIGSERIAL PRIMARY KEY,

admin_id UUID NOT NULL
REFERENCES public.users(id),

target_user_id UUID
REFERENCES public.users(id),

target_session_id UUID
REFERENCES public.attendance_sessions(id),

action_type TEXT NOT NULL,

reason TEXT,

metadata JSONB,

created_at TIMESTAMPTZ NOT NULL DEFAULT now()
```

);

CREATE INDEX IF NOT EXISTS idx_admin_audit_admin
ON public.admin_audit_log(admin_id);

CREATE INDEX IF NOT EXISTS idx_admin_audit_created
ON public.admin_audit_log(created_at DESC);

-- ============================================
-- ENABLE ROW LEVEL SECURITY
-- ============================================

ALTER TABLE public.users ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.attendance_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.locations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.expenses ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.reports ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.admin_audit_log ENABLE ROW LEVEL SECURITY;

-- ============================================
-- BASIC POLICIES
-- ============================================

CREATE POLICY users_select_own
ON public.users
FOR SELECT
TO authenticated
USING (
id = auth.uid()
);

CREATE POLICY locations_insert_own
ON public.locations
FOR INSERT
TO authenticated
WITH CHECK (
user_id = auth.uid()
);

CREATE POLICY locations_select_own
ON public.locations
FOR SELECT
TO authenticated
USING (
user_id = auth.uid()
);

-- ============================================
-- STORAGE BUCKETS
-- ============================================

INSERT INTO storage.buckets (
id,
name,
public
)
VALUES
('profile-images', 'profile-images', false),
('bill-images', 'bill-images', false),
('report-images', 'report-images', false)
ON CONFLICT DO NOTHING;

-- ============================================
-- END OF MIGRATION
-- ============================================
