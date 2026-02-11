# FieldTrack - Field Employee Tracking App

Android MVP for tracking field employee GPS locations during work hours.

## Tech Stack

- **Language:** Kotlin
- **Architecture:** MVVM
- **UI:** XML + ViewBinding
- **DI:** Koin
- **Local DB:** Room
- **Network:** Retrofit + OkHttp
- **Backend:** Supabase (PostgreSQL + REST API + JWT Auth)
- **Maps:** Google Maps SDK
- **Location:** Fused Location Provider (Foreground Service)
- **Background Sync:** WorkManager (periodic + batch sync)

## Setup

### 1. Supabase Project
Create a Supabase project and run the SQL in `supabase/schema.sql` to set up the database tables.

### 2. Local Configuration
Copy `local.properties.example` to `local.properties` and fill in:
```
SUPABASE_URL=https://your-project.supabase.co/
SUPABASE_ANON_KEY=your-anon-key
```

### 3. Build
Open in Android Studio and sync Gradle. Build and run on a device with API 24+.

## Project Structure

```
app/src/main/java/com/fieldtrack/app/
├── data/
│   ├── local/          # Room DB, DAOs, Entities
│   ├── model/          # Domain models
│   ├── remote/         # Retrofit APIs, DTOs, Session
│   └── repository/     # Repository pattern
├── di/                 # Koin DI modules
├── service/            # Foreground location service, sync worker
├── ui/
│   ├── admin/          # Admin dashboard, live tracking, employee map
│   ├── auth/           # Login/SignUp
│   ├── dashboard/      # Legacy employee dashboard
│   ├── employee/       # Employee home, my location map
│   ├── expense/        # Expense management
│   └── report/         # Daily reports
└── util/               # Constants, extensions, location utils
```

## Features

### Employee
- **Authentication:** Email/password via Supabase GoTrue
- **Check-in/Check-out:** Explicit consent-based work tracking with duration timer
- **GPS Tracking:** Foreground service with configurable intervals, accuracy filtering, and batch sync
- **Abuse Detection:** Mock location detection, velocity checks, GPS status monitoring
- **My Location Map:** Real-time polyline route visualization of today's tracked path with Google Maps
- **Offline Support:** Room local cache with automatic background sync via WorkManager
- **Reports:** Daily report submission with optional image attachment
- **Expenses:** Expense tracking with category, bill image, and approval status workflow

### Admin
- **Employee Management:** View all employees, approve pending registrations, deactivate accounts
- **Live Tracking:** List of all employees with last sync timestamps — tap to drill down into a single-employee map showing their last known location
- **Reports Dashboard:** View and manage all employee reports
- **Expenses Dashboard:** View, approve, or reject employee expense submissions

## Data Models

| Model    | Fields |
|----------|--------|
| User     | id, name, role (employee/admin), isCheckedIn |
| Location | id, userId, latitude, longitude, accuracy, timestamp, isSynced |
| Report   | id, userId, date, summary, optionalImageUrl, isSynced |
| Expense  | id, userId, amount, category, billImageUrl, status, timestamp, isSynced |

## Architecture & Data Flow

```
┌─────────────────┐    ┌──────────────┐    ┌──────────────┐
│  UI (Fragments)  │◄──│  ViewModels   │◄──│ Repositories  │
└─────────────────┘    └──────────────┘    └──────┬───────┘
                                                  │
                                    ┌─────────────┼─────────────┐
                                    │             │             │
                               ┌────▼───┐   ┌────▼───┐   ┌────▼────┐
                               │ Room DB │   │Retrofit│   │ Session │
                               │ (Local) │   │(Remote)│   │ Manager │
                               └────────┘   └────────┘   └─────────┘
```

- **All DAO queries** are scoped to the current `userId` to prevent cross-user data leakage
- **Row Level Security (RLS)** is enforced server-side on Supabase
- **Background sync** uses WorkManager with exponential backoff and network constraints
- **Location cleanup** automatically removes synced data older than the configured retention period

## Security

- All sync/deletion queries are scoped to the authenticated user's ID
- Supabase RLS ensures server-side data isolation between users
- JWT tokens are refreshed automatically via `TokenAuthenticator`
- Logout clears only the current user's local data — other users' cached data is preserved

## Min SDK
API 24 (Android 7.0)
