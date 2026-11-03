# ADR 0002: Zero External Dependencies Architecture

## Context
Capacitor notification plugins often introduce complex third-party SDK dependencies (such as Google Firebase Cloud Messaging / Play Services). For enterprise apps with internal backends or applications running on diverse Android hardware (including non-GMS devices, Zebra scanners, industrial handhelds, and kiosk tablets), requiring Google Play Services or Firebase creates runtime friction, credential dependencies (`google-services.json`), and APK bloat.

## Decision
We eliminated all Firebase and Google Play Services dependencies from `swbnotif`.
The plugin is built with **zero external native dependencies**:
- Android uses pure Android OS Framework APIs (`NotificationManager`, `NotificationCompat`, `AlarmManager`, `SharedPreferences`).
- iOS uses Apple framework APIs (`UserNotifications`, `UIKit`).
- Web uses browser standard Web Notifications and App Badging APIs.

## Rationale
- **100% Autonomous & Offline-First:** Operates on any Android device regardless of whether Google Play Services is installed, enabled, or blocked by firewall.
- **Minimal Footprint:** No extra dex files, no background Google services daemons, and zero impact on app startup performance.
- **Clean Separation of Concerns:** If an enterprise application needs server-driven events, the application can manage its own socket/API communication directly, triggering `SwbNotif.schedule(...)` natively without third-party push intermediaries.

## Consequences
- No `google-services.json` or Firebase SDK configuration is required.
- The build process is 100% reproducible and fast across Android and iOS.

