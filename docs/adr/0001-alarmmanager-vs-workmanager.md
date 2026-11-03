# ADR 0001: AlarmManager vs WorkManager for Local Scheduled Notifications

## Context
Capacitor notification plugins require reliable delivery of scheduled notifications at exact specified moments (e.g., medicine reminders, scheduled meetings, daily alarms). On modern Android (API 21 through 35), background work is constrained by battery management optimizations including Doze mode, App Standby buckets, and background start restrictions.

Two primary mechanisms exist on Android for scheduling:
1. `WorkManager` / `JobScheduler`
2. `AlarmManager` with exact/inexact scheduling APIs (`setExactAndAllowWhileIdle`, `setAndAllowWhileIdle`)

## Decision
We chose **`AlarmManager`** (specifically `setExactAndAllowWhileIdle` when permitted, falling back to `setAndAllowWhileIdle`) coupled with explicit BroadcastReceivers (`SwbNotifReceiver` and `SwbNotifBootReceiver`), rather than `WorkManager`.

## Rationale
- **Exact Time Guarantees:** WorkManager explicitly states in its documentation that it is meant for *deferrable* background work with a minimum periodic interval of 15 minutes. It does not support firing at an exact second or millisecond. When a user schedules a notification for 08:00:00, WorkManager may delay execution by tens of minutes depending on device constraints and Doze cycles.
- **Doze Survival:** `AlarmManager.setExactAndAllowWhileIdle()` is allowed by the Android OS to wake the CPU and fire alarms even while the device is in deep Doze mode.
- **Resource Footprint:** WorkManager introduces a dependency tree containing AndroidX Room, SQLite, and multiple worker lifecycle components (~2MB+ dex size and startup overhead). `AlarmManager` is a native Android framework system service with zero additional dependencies.
- **Lifecycle & Process Death:** When the device boots, restarts, or the app updates (`ACTION_BOOT_COMPLETED`, `ACTION_MY_PACKAGE_REPLACED`, `ACTION_TIMEZONE_CHANGED`), `SwbNotifBootReceiver` queries `SwbNotifStore` and reschedules all pending alarms immediately without requiring an active application process.

## Consequences
- Starting on Android 12 (API 31) and Android 13 (API 33), exact alarms require permission (`SCHEDULE_EXACT_ALARM` or `USE_EXACT_ALARM`).
- The plugin exposes `checkExactAlarmPermission()` and `requestExactAlarmPermission()`. If exact alarm permission is revoked or unavailable, the plugin gracefully falls back to inexact alarms (`setAndAllowWhileIdle`), ensuring reliable delivery without crashing.

