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
- **Maps:** OSMDroid (OpenStreetMap)
- **Location:** Fused Location Provider (Foreground Service)

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
│   ├── model/          # Domain models (locked)
│   ├── remote/         # Retrofit APIs, DTOs, Session
│   └── repository/     # Repository pattern
├── di/                 # Koin DI modules
├── service/            # Foreground location service, sync worker
├── ui/
│   ├── admin/          # Admin dashboard
│   ├── auth/           # Login/SignUp
│   ├── dashboard/      # Employee dashboard + home
│   ├── expense/        # Expense management
│   ├── map/            # OSMDroid map view
│   └── report/         # Daily reports
└── util/               # Constants, extensions, location utils
```

## Features

- **Authentication:** Email/password via Supabase GoTrue
- **Check-in/Check-out:** Explicit consent-based work tracking
- **GPS Tracking:** Foreground service, 2-5 min intervals, balanced power
- **Abuse Detection:** Mock location detection, GPS status checks
- **Offline Support:** Room local cache with network sync
- **Reports:** Daily report submission
- **Expenses:** Expense tracking with status workflow
- **Admin Panel:** View employees, reports, expenses; approve/reject expenses
- **Maps:** OSMDroid route visualization

## Data Models

| Model    | Fields |
|----------|--------|
| User     | id, name, role (employee/admin), isCheckedIn |
| Location | id, userId, latitude, longitude, accuracy, timestamp |
| Report   | id, userId, date, summary, optionalImageUrl |
| Expense  | id, userId, amount, category, billImageUrl, status, timestamp |

## Min SDK
API 24 (Android 7.0)
