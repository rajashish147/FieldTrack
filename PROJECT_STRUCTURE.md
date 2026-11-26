# Project Structure

Source-aligned documentation audit: 2026-11-24. Build caches, IDE directories,
generated files and captured build logs are not application source or docs.

```text
fieldtrack-android/
  app/
    src/main/
      AndroidManifest.xml
      java/com/fieldtrack/app/
        core/
          components/
          crash/
          data/
            local/
          designsystem/
          navigation/
          network/
            dto/
          permissions/
        features/
          admin/
            analytics/
            dashboard/
            employees/
            expenses/
            tracking/
          auth/
          employee/
            expenses/
            home/
            profile/
            sessions/
        service/
```

## Data Layer

`core/data` and `core/network` provide local storage, Room database objects, Retrofit API declarations, DTOs, and dependency injection.

- `core/data/local/AppDatabase.kt` defines the GPS and pending-expense entities
  and DAOs; they are not separate `*Dao.kt` files. Queue entries currently lack
  user/org ownership and stable expense idempotency fields.
- `core/data/SessionManager.kt` stores access token/identity in DataStore
  preferences. It does not store/use the returned refresh token.
- `core/network/dto/FieldOperationsDtos.kt` defines selfie challenge, camera
  attendance, preset sites and earnings response types.
- `core/network/NetworkModule.kt` builds the authenticated Retrofit transport.
  Signed selfie upload uses a distinct unauthenticated client in HomeRepository.

## Domain Layer

ViewModels coordinate business actions such as check-in, check-out, filtering, expense submission, and retry scheduling. The current project keeps this domain logic close to feature modules.

## Presentation Layer

`features/**` screens are Compose UI surfaces backed by ViewModels and state flows.

Employee home additionally contains `CameraAttendanceDialog.kt` (front-camera
CameraX), `SelfieGpsStamp.kt` (orientation, image footer and GPS EXIF),
`FieldWorkCard.kt` (sites/weekly-monthly earnings), and the repository/view model
that coordinates upload, check-in and service start. Admin salary/site
configuration is in the web dashboard, not the Android admin screens.

## Workers And Services

`LocationForegroundService` records active-session location updates. `LocationSyncWorker`, `ExpenseRetryWorker`, and `SessionRecoveryWorker` keep background sync and recovery behavior alive.

The foreground service sends GPS during active shifts. WorkManager retries account-owned points and expenses after a reconnect. Checkout tries to flush the queue, and the API can accept recent pre-checkout fixes.

## Build and test boundaries

Dependencies/versions live in `gradle/libs.versions.toml`; Android build types,
API URL and signing placeholders are in `app/build.gradle.kts`. Local debug URL
override lives in uncommitted `local.properties`. Release uses the production URL.
There are no checked-in unit/instrumentation test sources; Gradle's successful
`testDebugUnitTest` task currently means `NO-SOURCE`.
