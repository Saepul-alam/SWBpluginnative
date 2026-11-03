# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.0.1] - 2026-11-03

### Added
- **Full Production Notification Suite:** Replaced initial echo template with production-grade local, scheduled, and push notification architecture for Android, iOS, and Web.
- **Structured Error Handling:** Introduced `ErrorCode` enum (`PERMISSION_DENIED`, `EXACT_ALARM_NOT_ALLOWED`, `CHANNEL_NOT_FOUND`, `INVALID_SCHEDULE`, `INVALID_ARGUMENT`, `NOTIFICATION_NOT_FOUND`, `PUSH_UNAVAILABLE`, `STORAGE_LIMIT_EXCEEDED`, `UNKNOWN`).
- **Explicit Bounds Enforced:** Limits for title length (256 chars), body length (4096 chars), extra payload (32KB), maximum scheduled (100), maximum delivered retention (100), and actions per group (4).
- **Cold-Start Handling:** `getLastAction()` and `clearLastAction()` for reliable app launch resolution on notification tap.
- **Dismiss Tracking:** Added `notificationDismissed` event across Android, iOS, and Web.
- **Push Deregistration:** Added `unregisterPush()` to cleanly tear down remote registrations.
- **Android Resilience:**
  - `AlarmManager` with exact/inexact fallback surviving Doze and process death.
  - Automatic reschedule receiver on `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `TIME_SET`, and `TIMEZONE_CHANGED`.
  - Immutable explicit PendingIntents (`FLAG_IMMUTABLE`).
  - SharedPreferences store with automated schema migration.
  - `compileOnly` Firebase Messaging dependency ensuring zero APK bloat for local-only apps.
- **iOS Resilience:**
  - Conforming to Capacitor `NotificationHandlerProtocol` to avoid delegate collision with other plugins.
  - Dynamic category generation for notification actions and text input quick replies.
- **Web Compatibility:**
  - Web Notifications API and app badge integration with graceful fallback.
- **Testing & Quality:**
  - Pure logic Node.js unit test runner.
  - Android JUnit unit tests (`SwbNotifHelperTest`).
  - iOS XCTest suite on iPhone simulator.
  - Automated CI workflow with multi-node matrix, linter, formatting, test, and package size budget checks.
  - Architecture Decision Records (ADRs) in `docs/adr/`.

