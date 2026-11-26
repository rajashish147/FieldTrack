# FieldTrack Android

Native Android client for FieldTrack employee and admin workflows.

The debug build and lint checks ran locally. An emulator with simulated camera and GPS completed check-in, GPS sync, warehouse arrival, and checkout against a local API fixture.

## Features

- Employee check-in with an in-app front-camera selfie and fresh accurate GPS; check-out.
- Saved JPEG footer with coordinates, accuracy and photo/GPS times in IST (UTC+05:30), plus GPS EXIF.
- Preset work sites, GPS-verified arrival, and daily, weekly, and monthly earnings on employee home.
- Foreground GPS tracking during active attendance sessions.
- Local Room queues and bounded location/expense retry workers; see limitations below.
- Employee session history with backend status filters.
- Expense submission and status-filtered expense history.
- Admin dashboard, employees, live tracking, and expense review.

## Architecture

The app uses MVVM with a clean separation between:

- `core/network`: Retrofit API contract and DTOs.
- `core/data`: session storage, Room database, and dependency wiring.
- `features`: presentation and ViewModel logic for auth, employee, and admin flows.
- `service`: foreground location tracking and WorkManager retry/recovery workers.

## Setup

1. Install Android Studio and a compatible JDK (JVM target 17; this audit used JDK 21).
2. Open this folder as the project root.
3. Let Gradle sync dependencies.
4. For local API testing, create `local.properties` with:

```properties
API_BASE_URL=http://10.0.2.2:3000/
```

Production builds default to `https://api.getfieldtrack.app/`.

## Run

Use Android Studio Run, or:

```powershell
.\gradlew.bat :app:assembleDebug
```

For the local compile/test-task checks used in this audit:

```powershell
java -jar .\gradle\wrapper\gradle-wrapper.jar :app:compileDebugKotlin :app:testDebugUnitTest --console=plain
```

API defaults to port 3000; use a separate API port when also running the web app.
Release signing is not configured in the checked-in Gradle file; configure the
signing/distribution process before company rollout. Apply the API's required
field-operations schema and deploy matching clients; no live migration was run.

## Permissions

- Internet and network state.
- Fine and coarse location.
- Camera (runtime) for the in-app selfie; a usable front camera is required for attendance.
- Foreground service location.
- Wake lock and boot completed for recovery workers.
- Notifications on Android 13+.

## Company workflow

An employee starts a shift with the front camera and a fresh precise GPS fix. The app corrects image orientation, embeds GPS in EXIF, and stamps coordinates, accuracy, and timestamps below the photo. The signed upload uses a separate client. Employees select a configured site and tap **I’ve reached** to submit a current GPS fix. The server checks the arrival radius and applies the visit rate.

Android sends GPS while a shift is active and preserves unsent fixes in Room for retry. The server can accept valid historical fixes within the shift and its 48-hour sync window. Admins can view active staff on the live map. Reopen the app after reboot or force-stop to restore on-duty tracking under Android's foreground-service rules.

The employee home reports attendance, work time, and visit earnings. Monthly basic pay is displayed separately. Admins configure sites, rates and basic pay in the web dashboard.

## Local verification

The debug APK built successfully and Android lint reported zero errors. The unit-test task has no test sources. Verify camera rotation, GPS accuracy, battery management, and tracking recovery on physical devices using the company's staging API.