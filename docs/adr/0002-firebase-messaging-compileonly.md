# ADR 0002: Firebase Messaging as compileOnly Dependency

## Context
Many mobile applications utilize Capacitor for local and scheduled notifications without requiring Firebase Cloud Messaging (FCM) or push notifications (e.g., enterprise offline tools, local utilities, privacy-focused apps). Forcing Firebase dependencies on every consuming app increases APK size, requires Google Play Services integration, and mandates the presence of `google-services.json` at build time.

## Decision
We configured Firebase Cloud Messaging as a **`compileOnly`** dependency in Android `build.gradle`:
```groovy
compileOnly "com.google.firebase:firebase-messaging:$firebaseMessagingVersion"
```
All Firebase invocation logic is isolated inside `SwbNotifPush.java`.

## Rationale
- **Flexibility & App Autonomy:** Applications that do not use remote push notifications do not have to install Firebase dependencies, include Google services plugins, or configure Firebase console credentials.
- **Fail Safe at Runtime:** If a consumer application calls `registerPush()` or `unregisterPush()` without including Firebase Messaging in their application build, `SwbNotifPlugin` catches `NoClassDefFoundError` / `ClassNotFoundException` and rejects with a structured `PUSH_UNAVAILABLE` error code, explaining exactly what library and configuration are required.
- **Zero Binary Bloat:** Applications that use only local notifications avoid adding Firebase client libraries to their release APK.

## Consequences
- Consuming apps that want remote push notifications must add `com.google.firebase:firebase-messaging` to their own app-level `build.gradle` and configure `google-services.json`.
- The plugin documentation clearly outlines the setup steps for push-enabled apps.

