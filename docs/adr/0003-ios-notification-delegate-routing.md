# ADR 0003: iOS Notification Handler Delegate Routing

## Context
Capacitor plugins that handle user notifications on iOS must interact with `UNUserNotificationCenterDelegate`. If multiple plugins or native code overwrite `UNUserNotificationCenter.current().delegate` directly, the last plugin to set the delegate silences all preceding handlers, creating race conditions, broken deep links, and lost notification response callbacks.

## Decision
We integrate with Capacitor's centralized `NotificationHandlerProtocol` via `bridge?.notificationRouter`:
```swift
bridge?.notificationRouter.localNotificationHandler = self
bridge?.notificationRouter.pushNotificationHandler = self
```
Combined with iOS NotificationCenter observers for remote token propagation (`SwbNotifPushToken`).

## Rationale
- **No Delegate Overwriting:** By delegating handling through `notificationRouter`, Capacitor's main bridge manages `UNUserNotificationCenterDelegate` lifecycle and routes events cleanly.
- **Support for Multiple Plugins:** If another Capacitor notification plugin is installed alongside `swbnotif`, Capacitor coordinates callback dispatch rather than causing unrecoverable delegate collision.
- **Process Death & Cold Start Safety:** Taps on notifications when the app is suspended or terminated are safely captured in `defaults` (`swbnotif.last_action`) and made available through `getLastAction()` once the JavaScript engine initializes.

## Consequences
- Requires Capacitor 3.0+ bridge architecture.
- Clean cleanup in `deinit` removing `NotificationCenter` observers prevents observer leaks across web view reloads.

