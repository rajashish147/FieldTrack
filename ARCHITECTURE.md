# Android Architecture

FieldTrack Android is a Kotlin + Jetpack Compose app using Hilt, Retrofit, Room, WorkManager, and foreground services.

## Modules

- `core/components`: reusable Compose UI.
- `core/data`: local persistence and session management.
- `core/network`: Retrofit API service, auth interceptor, DTOs, network module.
- `core/navigation`: route definitions and navigation graph.
- `features/auth`: login and session bootstrap.
- `features/employee`: home, sessions, expenses, profile.
- `features/admin`: dashboard, employees, tracking, expenses, analytics.
- `service`: location tracking, expense retry, location sync, session recovery.

## Tracking system

The employee starts a shift with CameraX and a current precise GPS fix. The app stamps and embeds the fix in the JPEG before uploading it on a separate client. The API verifies the selfie and opens the shift. Session recovery looks up active attendance through the attendance endpoint and follows Android location-service startup rules.

The foreground service records shift GPS in Room and uploads pending fixes when connected. WorkManager handles offline retries and recoverable session state. Queues are scoped to the authenticated account. Checkout attempts a bounded flush. The server accepts recent fixes from before checkout and identifies discarded timestamps.
## API Integration

Retrofit methods mirror the backend contract:

- Sessions use `status=all|active|recent|inactive`.
- Expenses use `status=all|PENDING|APPROVED|REJECTED|processed`.
- Paginated responses read metadata from the API `pagination` object.

## Auth flow

The session manager stores refresh credentials using Android Keystore-backed encryption. The network layer coordinates refresh and retry. On sign-out or confirmed expiry, the app clears the session and stops foreground tracking. WorkManager can resume account-owned GPS and expense retries after login. Expenses carry a stable submission key.

## Company field operations

Home displays active preset sites and daily, weekly, and monthly attendance and earnings. Reports use the company timezone and currency. The API verifies warehouse arrival and applies visit-rate history and monthly basic pay.

## Verification

The emulator passed camera upload, connected GPS sync, warehouse arrival, and checkout with a local API fixture. Physical-device camera, battery, and Android lifecycle coverage remains pending.
