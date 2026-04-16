# FieldTrack — Enterprise Field Employee Tracking System (Android)

⚠️ **Source-available portfolio project. Commercial use requires permission.**

**Version:** 1.0.0
**Platform:** Android (API 24+)
**Architecture:** Offline-first, enterprise-grade
**Status:** Production-ready (portfolio release)

FieldTrack is a secure, production-grade Android application designed for tracking field employee attendance sessions, GPS location history, reports, and expenses with enterprise-level reliability, privacy protection, and offline support.

This project demonstrates real-world architecture patterns used in enterprise workforce tracking systems and serves as the foundation for a future multi-tenant SaaS platform.

---

# Key Highlights

• Offline-first architecture with deterministic sync engine
• Secure session-based GPS tracking
• Enterprise-grade concurrency-safe authentication
• Crash-safe persistent state management
• Supabase backend with strict Row Level Security
• Idempotent sync preventing duplicate or lost records
• Admin dashboard with live employee tracking
• Production-hardened release build (ProGuard, resource shrinking)

---

# Screenshots

(Add screenshots in `/screenshots` folder)

| Employee Home                              | Tracking Map                         | Admin Dashboard                           |
| ------------------------------------------ | ------------------------------------ | ----------------------------------------- |
| ![Employee](screenshots/employee_home.png) | ![Map](screenshots/tracking_map.png) | ![Admin](screenshots/admin_dashboard.png) |

---

# Features

## Employee Features

### Secure Authentication

* Email/password authentication via Supabase GoTrue (JWT)
* Automatic token refresh with thread-safe authenticator
* Session persistence with atomic commit guarantees

### Attendance Tracking

* Explicit Check-in / Check-out workflow
* Server-verified attendance sessions
* Accurate work duration tracking
* Crash-safe session recovery

### GPS Location Tracking

* Foreground location service using Fused Location Provider
* High-accuracy tracking with configurable intervals
* Session-linked location history
* Real-time route visualization via Google Maps
* Mock location detection and abuse prevention

### Offline-First Sync

* Full offline operation using Room database
* Deterministic sync state machine
* Automatic retry using WorkManager
* Zero data loss guarantee

### Reports

* Daily report submission
* Optional image attachments
* Offline creation with background sync

### Expense Tracking

* Expense submission with bill images
* Approval workflow
* Sync-safe idempotent inserts

---

## Admin Features

### Employee Management

* View all employees
* Approve or deactivate accounts

### Live Tracking

* View employees with last sync timestamps
* Drill-down to employee map view
* View last known employee location

### Report Management

* View all employee reports

### Expense Management

* Approve or reject expense submissions

---

# Architecture Overview

FieldTrack follows a strict offline-first MVVM architecture:


UI (Fragments)
    ↓
ViewModels
    ↓
Repositories (Single Source of Truth)
    ↓
Room DB ←→ Retrofit API ←→ Supabase Backend
    ↓
WorkManager Sync Engine
```

This architecture guarantees:

• Crash safety
• Offline reliability
• Data consistency
• Secure access isolation

---

# Tech Stack

| Layer                | Technology                             |
| -------------------- | -------------------------------------- |
| Language             | Kotlin                                 |
| Architecture         | MVVM + Repository Pattern              |
| Dependency Injection | Koin                                   |
| Local Storage        | Room Database                          |
| Networking           | Retrofit + OkHttp                      |
| Backend              | Supabase (PostgreSQL, REST, Auth, RLS) |
| Location             | Fused Location Provider                |
| Maps                 | Google Maps SDK                        |
| Background Sync      | WorkManager                            |
| Authentication       | JWT (Supabase GoTrue)                  |
| Security             | EncryptedSharedPreferences             |

---

# Security Model

FieldTrack implements enterprise-grade security controls:

### Authentication Security

* JWT-based authentication
* Automatic token refresh
* Thread-safe authenticator

### Data Isolation

* Supabase Row Level Security (RLS)
* All queries scoped to authenticated user ID

### Storage Security

* Encrypted token storage
* No sensitive production logging

### Sync Integrity

* UPSERT-based sync prevents duplicates
* Sync verification prevents data loss

---

# Sync State Machine


LOCAL_ONLY → SYNCING → SYNCED
                   ↘ FAILED → RETRY
```

Guarantees reliable sync under all conditions.

---

# Demo / Evaluation Instructions

You can evaluate FieldTrack locally:

## Step 1 — Create Supabase Project

Create a Supabase project and run:


supabase/schema.sql
```

---

## Step 2 — Configure Local Environment

Copy:


local.properties.example → local.properties
```

Add:


SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
```

---

## Step 3 — Build and Run


./gradlew assembleDebug
```

Install on Android device.

---

## Step 4 — Create Admin User

Register a user normally, then promote to admin:

```sql
update users
set role = 'ADMIN'
where email = 'your-email@example.com';
```

---

# Project Structure


app/src/main/java/com/fieldtrack/app/

data/
├── local/
├── model/
├── remote/
└── repository/

di/

service/

ui/
├── admin/
├── auth/
├── employee/
├── expense/
└── report/

util/
```

---

# Release Information

Current version:

```
VersionCode: 100
VersionName: 1.0.0
```

Release build is:

• ProGuard optimized
• Privacy safe
• Production hardened

---

# Production Safety Guarantees

FieldTrack ensures:

• Crash-safe session persistence
• Thread-safe authentication
• Data integrity protection
• Privacy-safe logging
• Offline reliability

---

# Minimum Requirements

Android 7.0 (API 24) or higher

---

# Future Roadmap

Planned evolution into enterprise SaaS platform:

• Multi-tenant architecture
• Web admin dashboard
• Enterprise deployment
• Advanced analytics
• SaaS subscription model

---

# License

FieldTrack is source-available software.

Copyright © 2026 Ashish Raj.

All rights reserved.

This code is publicly visible for portfolio and educational purposes only.
Commercial use, modification, or redistribution requires explicit permission.

---

# Author

Ashish Raj

FieldTrack — Enterprise Workforce Tracking System

GitHub: https://github.com/rajashish147

Contact: [rajashish147@gmail.com](mailto:rajashish147@gmail.com)
