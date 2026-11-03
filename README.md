# swbnotif

Capacitor plugin for local, scheduled and push notifications on Android and iOS.

## Install

```bash
npm install swbnotif
npx cap sync
```

## Usage

```ts
import { SwbNotif } from 'swbnotif';

const permission = await SwbNotif.requestPermission();
if (!permission.granted) {
  console.warn('Notifications are not allowed');
}

await SwbNotif.createChannel({
  id: 'reminders',
  name: 'Reminders',
  importance: 'high',
  vibration: true,
});

const { id } = await SwbNotif.schedule({
  title: 'Hello',
  body: 'This one is delivered right away',
  channelId: 'reminders',
  extra: { route: '/inbox' },
  badge: 1,
});

await SwbNotif.schedule({
  title: 'Reminder',
  body: 'Scheduled for tomorrow',
  channelId: 'reminders',
  scheduleAt: new Date(Date.now() + 24 * 60 * 60 * 1000),
});

const pending = await SwbNotif.getPending();
await SwbNotif.cancel({ id });
```

### Notification actions

```ts
await SwbNotif.createActionGroup({
  id: 'message',
  actions: [
    { id: 'reply', title: 'Reply', input: true, inputPlaceholder: 'Type a reply' },
    { id: 'dismiss', title: 'Dismiss', destructive: true },
  ],
});

await SwbNotif.schedule({
  title: 'New message',
  body: 'Ada pesan baru',
  actionGroupId: 'message',
});
```

### Listening to events

```ts
SwbNotif.addListener('notificationReceived', notification => {
  console.log('received', notification);
});

SwbNotif.addListener('notificationActionPerformed', event => {
  console.log('action', event.actionId, event.notification.extra, event.inputValue);
});

// When the app was launched by tapping a notification before the listener
// above was registered, the event is kept until it is consumed.
const lastAction = await SwbNotif.getLastAction();
```

### Push notifications

```ts
SwbNotif.addListener('pushTokenReceived', ({ token }) => {
  if (token) {
    // Send the token to your backend
  }
});

const { token } = await SwbNotif.registerPush();
```

Push notifications require extra setup in the consuming app:

**Android**

1. Add Firebase to the app (`google-services.json` + the `com.google.gms.google-services` Gradle plugin).
2. Add the dependency (the plugin declares it as `compileOnly`):
   ```gradle
   implementation platform('com.google.firebase:firebase-bom:32.7.0')
   implementation 'com.google.firebase:firebase-messaging'
   ```
3. Use data-only messages (`title`, `body`, `channelId`, `sound`, `smallIcon` keys) so that
   `onMessageReceived` is called and the plugin controls the notification. Notification
   payloads are displayed by the Firebase SDK and bypass the plugin.

**iOS**

1. Enable the Push Notifications capability and upload your APNs key in the Apple Developer portal.
2. Forward the device token from `AppDelegate.swift`:

   ```swift
   import UserNotifications

   func application(
     _ application: UIApplication,
     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
   ) {
     let token = deviceToken.map { String(format: "%02x", $0) }.joined()
     UserDefaults.standard.set(token, forKey: "swbnotif.push_token")
     NotificationCenter.default.post(name: Notification.Name("SwbNotifPushToken"), object: token)
   }
   ```

## Platform notes

- **Android 13+** requires the runtime `POST_NOTIFICATIONS` permission. Call
  `requestPermission()` before scheduling. The plugin ships with `compileSdkVersion 33` and
  `targetSdkVersion 33`, so the consuming app must also compile against SDK 33 or newer.
- **Android 12+** may require the user to allow exact alarms. Use
  `checkExactAlarmPermission()` / `requestExactAlarmPermission()`. Without it alarms fall back to
  inexact delivery.
- **Android channels** are immutable once created. Changing the sound or importance of an existing
  channel requires deleting and recreating it (or using a new channel id).
- **Android badge counts** are launcher-specific; they are attached to the notification with
  `setNumber`. `setBadgeCount` stores the value for `getBadgeCount`.
- **Scheduled notifications** are restored after a reboot on Android. iOS schedules them with
  `UNUserNotificationCenter`.
- **Repeating notifications** on Android respect `scheduleAt` and `count`. On iOS they use a
  repeating interval trigger, so `scheduleAt` and `count` are ignored.
- **Web** supports local notifications through the Web Notifications API while the tab is open.
  Scheduled notifications and push are not supported.
- The iOS deployment target is 12.0.

## Platform Matrix

| Platform | Supported OS / Versions | Local Notifications | Scheduled Notifications | Push (FCM / APNs) | Action Buttons & Quick Reply | App Badge |
| -------- | ----------------------- | ------------------- | ----------------------- | ----------------- | ---------------------------- | --------- |
| Android  | Android 5.0+ (API 21–35)| ✅                   | ✅ (Exact & Inexact)    | ✅ (FCM data)     | ✅                           | ✅        |
| iOS      | iOS 12.0+               | ✅                   | ✅ (Calendar/Interval)  | ✅ (APNs)         | ✅ (with text input)         | ✅        |
| Web      | Modern evergreen        | ✅ (Web Notif API)   | ⚠️ Active tab only      | ❌                | ❌                           | ✅ (Badge)|

## Error Codes

All errors thrown or rejected by native methods provide structured error codes:

| Error Code | Description |
| ---------- | ----------- |
| `PERMISSION_DENIED` | Notification permission has not been granted by user or system. |
| `EXACT_ALARM_NOT_ALLOWED` | Android 12+ exact alarm scheduling permission (`SCHEDULE_EXACT_ALARM`) was denied. |
| `CHANNEL_NOT_FOUND` | Specified Android notification channel does not exist. |
| `INVALID_SCHEDULE` | Date or schedule trigger format is invalid or in the unrepeatable past. |
| `INVALID_ARGUMENT` | Title missing/empty, exceeding 256 chars, body exceeding 4096 chars, extra payload exceeding 32KB, or duplicate id. |
| `NOTIFICATION_NOT_FOUND` | Targeted notification id not found for update or removal. |
| `PUSH_UNAVAILABLE` | Remote push notification service not configured or Firebase dependency missing. |
| `STORAGE_LIMIT_EXCEEDED` | Exceeded limit of 100 scheduled notifications. |
| `UNKNOWN` | Unclassified native exception. |

## Migration Guide from 0.0.x

`swbnotif` 0.1.0 replaces the legacy echo template with a complete production notification system:
- Remove calls to `Swb.echo(...)`.
- Update imports to use `SwbNotif` from `'swbnotif'`.
- All methods now return structured promises or emit typed events.
- On Android, replace any custom broadcast receivers with `SwbNotifReceiver`, `SwbNotifBootReceiver`, and `NotificationActionReceiver`.
- Ensure error handlers inspect `error.code` against the `ErrorCode` enum rather than matching raw error strings.

## Security & Privacy
- **Zero Sensitive Logging:** Notification titles, bodies, and extra dictionaries are never logged to production console logs.
- **Data Minimization:** Notifications and tokens stored in local persistence (`SharedPreferences` / `UserDefaults`) are strictly retained up to the configured bounds (max 100 scheduled, max 100 delivered) and can be completely wiped via `removeAllDelivered()` and `clearLastAction()`.
- **Immutable Intents:** All Android `PendingIntent` instances specify `FLAG_IMMUTABLE` and target explicit components.

## Troubleshooting

- **Android: Notifications do not appear on Android 13+**
  Check that you called `requestPermission()` and that the user granted notification permissions. Verify that `compileSdkVersion` and `targetSdkVersion` in your app are set to 33 or higher.
- **Android: Scheduled notifications do not fire exactly on time**
  On Android 12+ (API 31+), exact alarms require permission. Call `checkExactAlarmPermission()`. If false, call `requestExactAlarmPermission()` to guide the user to system settings.
- **Android: Alarms lost after phone reboot**
  Verify `RECEIVE_BOOT_COMPLETED` permission is in your app and that `SwbNotifBootReceiver` is registered in `AndroidManifest.xml`.
- **iOS: Push token is null**
  Verify your app has the Push Notifications entitlement enabled in Xcode and that you called `registerPush()`.

## API

<docgen-index>

* [`checkPermission()`](#checkpermission)
* [`requestPermission()`](#requestpermission)
* [`checkExactAlarmPermission()`](#checkexactalarmpermission)
* [`requestExactAlarmPermission()`](#requestexactalarmpermission)
* [`createChannel(...)`](#createchannel)
* [`deleteChannel(...)`](#deletechannel)
* [`listChannels()`](#listchannels)
* [`schedule(...)`](#schedule)
* [`scheduleMultiple(...)`](#schedulemultiple)
* [`getPending()`](#getpending)
* [`cancel(...)`](#cancel)
* [`cancelAll()`](#cancelall)
* [`getDelivered()`](#getdelivered)
* [`removeDelivered(...)`](#removedelivered)
* [`removeAllDelivered()`](#removealldelivered)
* [`setBadgeCount(...)`](#setbadgecount)
* [`getBadgeCount()`](#getbadgecount)
* [`createActionGroup(...)`](#createactiongroup)
* [`deleteActionGroup(...)`](#deleteactiongroup)
* [`registerPush()`](#registerpush)
* [`getPushToken()`](#getpushtoken)
* [`unregisterPush()`](#unregisterpush)
* [`getLastAction()`](#getlastaction)
* [`clearLastAction()`](#clearlastaction)
* [`addListener('notificationReceived', ...)`](#addlistenernotificationreceived)
* [`addListener('notificationActionPerformed', ...)`](#addlistenernotificationactionperformed)
* [`addListener('pushTokenReceived', ...)`](#addlistenerpushtokenreceived)
* [`addListener('notificationDismissed', ...)`](#addlistenernotificationdismissed)
* [`removeAllListeners()`](#removealllisteners)
* [Interfaces](#interfaces)
* [Type Aliases](#type-aliases)

</docgen-index>

<docgen-api>
<!--Update the source file JSDoc comments and rerun docgen to update the docs below-->

### checkPermission()

```typescript
checkPermission() => Promise<PermissionStatus>
```

Check whether notifications are allowed without prompting the user.

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### requestPermission()

```typescript
requestPermission() => Promise<PermissionStatus>
```

Prompt the user for notification permission.

**Returns:** <code>Promise&lt;<a href="#permissionstatus">PermissionStatus</a>&gt;</code>

--------------------


### checkExactAlarmPermission()

```typescript
checkExactAlarmPermission() => Promise<{ granted: boolean; }>
```

Check whether exact alarms are allowed (Android 12+).
Always returns `true` on iOS/web.

**Returns:** <code>Promise&lt;{ granted: boolean; }&gt;</code>

--------------------


### requestExactAlarmPermission()

```typescript
requestExactAlarmPermission() => Promise<{ granted: boolean; }>
```

Open the system settings so the user can allow exact alarms (Android 12+).
No-op on iOS/web.

**Returns:** <code>Promise&lt;{ granted: boolean; }&gt;</code>

--------------------


### createChannel(...)

```typescript
createChannel(options: NotificationChannel) => Promise<void>
```

Create or update an Android notification channel. No-op on iOS/web.

| Param         | Type                                                                |
| ------------- | ------------------------------------------------------------------- |
| **`options`** | <code><a href="#notificationchannel">NotificationChannel</a></code> |

--------------------


### deleteChannel(...)

```typescript
deleteChannel(options: { id: string; }) => Promise<void>
```

| Param         | Type                         |
| ------------- | ---------------------------- |
| **`options`** | <code>{ id: string; }</code> |

--------------------


### listChannels()

```typescript
listChannels() => Promise<{ channels: NotificationChannel[]; }>
```

**Returns:** <code>Promise&lt;{ channels: NotificationChannel[]; }&gt;</code>

--------------------


### schedule(...)

```typescript
schedule(options: ScheduleOptions) => Promise<{ id: number; }>
```

Display or schedule a single notification.

| Param         | Type                                                        |
| ------------- | ----------------------------------------------------------- |
| **`options`** | <code><a href="#scheduleoptions">ScheduleOptions</a></code> |

**Returns:** <code>Promise&lt;{ id: number; }&gt;</code>

--------------------


### scheduleMultiple(...)

```typescript
scheduleMultiple(options: { notifications: ScheduleOptions[]; }) => Promise<{ ids: number[]; }>
```

| Param         | Type                                               |
| ------------- | -------------------------------------------------- |
| **`options`** | <code>{ notifications: ScheduleOptions[]; }</code> |

**Returns:** <code>Promise&lt;{ ids: number[]; }&gt;</code>

--------------------


### getPending()

```typescript
getPending() => Promise<{ notifications: PendingNotification[]; }>
```

List notifications that are scheduled but not yet delivered.

**Returns:** <code>Promise&lt;{ notifications: PendingNotification[]; }&gt;</code>

--------------------


### cancel(...)

```typescript
cancel(options: { id: number; }) => Promise<void>
```

Cancel a scheduled or currently displayed notification.

| Param         | Type                         |
| ------------- | ---------------------------- |
| **`options`** | <code>{ id: number; }</code> |

--------------------


### cancelAll()

```typescript
cancelAll() => Promise<void>
```

Cancel every scheduled and currently displayed notification.

--------------------


### getDelivered()

```typescript
getDelivered() => Promise<{ notifications: DeliveredNotification[]; }>
```

List notifications that were delivered by this plugin and not yet removed.

**Returns:** <code>Promise&lt;{ notifications: DeliveredNotification[]; }&gt;</code>

--------------------


### removeDelivered(...)

```typescript
removeDelivered(options: { ids: number[]; }) => Promise<void>
```

| Param         | Type                            |
| ------------- | ------------------------------- |
| **`options`** | <code>{ ids: number[]; }</code> |

--------------------


### removeAllDelivered()

```typescript
removeAllDelivered() => Promise<void>
```

--------------------


### setBadgeCount(...)

```typescript
setBadgeCount(options: { count: number; }) => Promise<void>
```

Set the app icon badge count.

| Param         | Type                            |
| ------------- | ------------------------------- |
| **`options`** | <code>{ count: number; }</code> |

--------------------


### getBadgeCount()

```typescript
getBadgeCount() => Promise<{ count: number; }>
```

**Returns:** <code>Promise&lt;{ count: number; }&gt;</code>

--------------------


### createActionGroup(...)

```typescript
createActionGroup(options: ActionGroup) => Promise<void>
```

Register notification actions for Android/iOS.

| Param         | Type                                                |
| ------------- | --------------------------------------------------- |
| **`options`** | <code><a href="#actiongroup">ActionGroup</a></code> |

--------------------


### deleteActionGroup(...)

```typescript
deleteActionGroup(options: { id: string; }) => Promise<void>
```

| Param         | Type                         |
| ------------- | ---------------------------- |
| **`options`** | <code>{ id: string; }</code> |

--------------------


### registerPush()

```typescript
registerPush() => Promise<PushToken>
```

Register for push notifications and resolve with the device token.
Resolves with `{ token: null }` when the token is not available yet, in
which case listen for `pushTokenReceived`.

**Returns:** <code>Promise&lt;<a href="#pushtoken">PushToken</a>&gt;</code>

--------------------


### getPushToken()

```typescript
getPushToken() => Promise<PushToken>
```

**Returns:** <code>Promise&lt;<a href="#pushtoken">PushToken</a>&gt;</code>

--------------------


### unregisterPush()

```typescript
unregisterPush() => Promise<void>
```

Unregister device push notification token and tear down remote registration.

--------------------


### getLastAction()

```typescript
getLastAction() => Promise<NotificationActionEvent | null>
```

The action that launched the app, if any. Cleared once consumed.

**Returns:** <code>Promise&lt;<a href="#notificationactionevent">NotificationActionEvent</a> | null&gt;</code>

--------------------


### clearLastAction()

```typescript
clearLastAction() => Promise<void>
```

Clear any cached cold-start action without consuming it.

--------------------


### addListener('notificationReceived', ...)

```typescript
addListener(eventName: 'notificationReceived', listenerFunc: (notification: DeliveredNotification) => void) => Promise<PluginListenerHandle> & PluginListenerHandle
```

| Param              | Type                                                                                               |
| ------------------ | -------------------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'notificationReceived'</code>                                                                |
| **`listenerFunc`** | <code>(notification: <a href="#deliverednotification">DeliveredNotification</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt; & <a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

--------------------


### addListener('notificationActionPerformed', ...)

```typescript
addListener(eventName: 'notificationActionPerformed', listenerFunc: (event: NotificationActionEvent) => void) => Promise<PluginListenerHandle> & PluginListenerHandle
```

| Param              | Type                                                                                            |
| ------------------ | ----------------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'notificationActionPerformed'</code>                                                      |
| **`listenerFunc`** | <code>(event: <a href="#notificationactionevent">NotificationActionEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt; & <a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

--------------------


### addListener('pushTokenReceived', ...)

```typescript
addListener(eventName: 'pushTokenReceived', listenerFunc: (event: PushToken) => void) => Promise<PluginListenerHandle> & PluginListenerHandle
```

| Param              | Type                                                                |
| ------------------ | ------------------------------------------------------------------- |
| **`eventName`**    | <code>'pushTokenReceived'</code>                                    |
| **`listenerFunc`** | <code>(event: <a href="#pushtoken">PushToken</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt; & <a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

--------------------


### addListener('notificationDismissed', ...)

```typescript
addListener(eventName: 'notificationDismissed', listenerFunc: (event: NotificationDismissedEvent) => void) => Promise<PluginListenerHandle> & PluginListenerHandle
```

| Param              | Type                                                                                                  |
| ------------------ | ----------------------------------------------------------------------------------------------------- |
| **`eventName`**    | <code>'notificationDismissed'</code>                                                                  |
| **`listenerFunc`** | <code>(event: <a href="#notificationdismissedevent">NotificationDismissedEvent</a>) =&gt; void</code> |

**Returns:** <code>Promise&lt;<a href="#pluginlistenerhandle">PluginListenerHandle</a>&gt; & <a href="#pluginlistenerhandle">PluginListenerHandle</a></code>

--------------------


### removeAllListeners()

```typescript
removeAllListeners() => Promise<void>
```

--------------------


### Interfaces


#### PermissionStatus

| Prop          | Type                                                            | Description                                                           |
| ------------- | --------------------------------------------------------------- | --------------------------------------------------------------------- |
| **`granted`** | <code>boolean</code>                                            | Whether the app is currently allowed to post notifications.           |
| **`display`** | <code><a href="#permissiondisplay">PermissionDisplay</a></code> | Platform specific display state, useful to know if you can still ask. |


#### NotificationChannel

| Prop              | Type                                                                      | Description                                                             |
| ----------------- | ------------------------------------------------------------------------- | ----------------------------------------------------------------------- |
| **`id`**          | <code>string</code>                                                       |                                                                         |
| **`name`**        | <code>string</code>                                                       |                                                                         |
| **`description`** | <code>string</code>                                                       |                                                                         |
| **`importance`**  | <code><a href="#notificationimportance">NotificationImportance</a></code> |                                                                         |
| **`sound`**       | <code>string</code>                                                       | Default sound for the channel (Android only). Raw resource name or URI. |
| **`vibration`**   | <code>boolean</code>                                                      |                                                                         |
| **`lights`**      | <code>boolean</code>                                                      |                                                                         |
| **`lightColor`**  | <code>string</code>                                                       | Hex color, e.g. `#FF0000` (Android only).                               |
| **`visibility`**  | <code><a href="#notificationvisibility">NotificationVisibility</a></code> |                                                                         |


#### ScheduleOptions

| Prop                | Type                                                                  | Description                                                                                                                                                          |
| ------------------- | --------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **`id`**            | <code>number</code>                                                   | Notification id. When omitted, a unique positive id is generated.                                                                                                    |
| **`title`**         | <code>string</code>                                                   |                                                                                                                                                                      |
| **`body`**          | <code>string</code>                                                   |                                                                                                                                                                      |
| **`largeBody`**     | <code>string</code>                                                   | Android only. Expanded body text.                                                                                                                                    |
| **`summaryText`**   | <code>string</code>                                                   | Android only. Text shown in the collapsed summary.                                                                                                                   |
| **`channelId`**     | <code>string</code>                                                   | Channel to post to. Android 8+ requires a valid channel id.                                                                                                          |
| **`sound`**         | <code>string</code>                                                   | Sound to play. Raw resource name (Android) / bundle file name (iOS) or a URI.                                                                                        |
| **`smallIcon`**     | <code>string</code>                                                   | Drawable/mipmap resource name (Android) or bundle image name (iOS).                                                                                                  |
| **`largeIcon`**     | <code>string</code>                                                   |                                                                                                                                                                      |
| **`iconColor`**     | <code>string</code>                                                   | Hex color applied to the notification icon (Android only).                                                                                                           |
| **`badge`**         | <code>number</code>                                                   | Number shown on the app icon (Android) or the icon badge (iOS).                                                                                                      |
| **`extra`**         | <code><a href="#record">Record</a>&lt;string, any&gt;</code>          | Arbitrary data delivered alongside the notification (max 32KB).                                                                                                      |
| **`actionGroupId`** | <code>string</code>                                                   | Reference to an action group created with `createActionGroup`.                                                                                                       |
| **`priority`**      | <code><a href="#notificationpriority">NotificationPriority</a></code> |                                                                                                                                                                      |
| **`group`**         | <code>string</code>                                                   | Group key used to bundle notifications (Android only).                                                                                                               |
| **`autoCancel`**    | <code>boolean</code>                                                  | Default `true`.                                                                                                                                                      |
| **`ongoing`**       | <code>boolean</code>                                                  | Keep the notification visible and non dismissible.                                                                                                                   |
| **`scheduleAt`**    | <code>string \| number \| <a href="#date">Date</a></code>             | When to deliver the notification. Accepts a <a href="#date">`Date`</a>, an ISO string or epoch milliseconds. When omitted the notification is delivered immediately. |
| **`every`**         | <code><a href="#scheduleevery">ScheduleEvery</a></code>               | Repeats the notification on the given interval starting at `scheduleAt`.                                                                                             |
| **`count`**         | <code>number</code>                                                   | How many times to repeat. Omit for an unlimited number of repetitions.                                                                                               |


#### Date

Enables basic storage and retrieval of dates and times.

| Method                 | Signature                                                                                                    | Description                                                                                                                             |
| ---------------------- | ------------------------------------------------------------------------------------------------------------ | --------------------------------------------------------------------------------------------------------------------------------------- |
| **toString**           | () =&gt; string                                                                                              | Returns a string representation of a date. The format of the string depends on the locale.                                              |
| **toDateString**       | () =&gt; string                                                                                              | Returns a date as a string value.                                                                                                       |
| **toTimeString**       | () =&gt; string                                                                                              | Returns a time as a string value.                                                                                                       |
| **toLocaleString**     | () =&gt; string                                                                                              | Returns a value as a string value appropriate to the host environment's current locale.                                                 |
| **toLocaleDateString** | () =&gt; string                                                                                              | Returns a date as a string value appropriate to the host environment's current locale.                                                  |
| **toLocaleTimeString** | () =&gt; string                                                                                              | Returns a time as a string value appropriate to the host environment's current locale.                                                  |
| **valueOf**            | () =&gt; number                                                                                              | Returns the stored time value in milliseconds since midnight, January 1, 1970 UTC.                                                      |
| **getTime**            | () =&gt; number                                                                                              | Gets the time value in milliseconds.                                                                                                    |
| **getFullYear**        | () =&gt; number                                                                                              | Gets the year, using local time.                                                                                                        |
| **getUTCFullYear**     | () =&gt; number                                                                                              | Gets the year using Universal Coordinated Time (UTC).                                                                                   |
| **getMonth**           | () =&gt; number                                                                                              | Gets the month, using local time.                                                                                                       |
| **getUTCMonth**        | () =&gt; number                                                                                              | Gets the month of a <a href="#date">Date</a> object using Universal Coordinated Time (UTC).                                             |
| **getDate**            | () =&gt; number                                                                                              | Gets the day-of-the-month, using local time.                                                                                            |
| **getUTCDate**         | () =&gt; number                                                                                              | Gets the day-of-the-month, using Universal Coordinated Time (UTC).                                                                      |
| **getDay**             | () =&gt; number                                                                                              | Gets the day of the week, using local time.                                                                                             |
| **getUTCDay**          | () =&gt; number                                                                                              | Gets the day of the week using Universal Coordinated Time (UTC).                                                                        |
| **getHours**           | () =&gt; number                                                                                              | Gets the hours in a date, using local time.                                                                                             |
| **getUTCHours**        | () =&gt; number                                                                                              | Gets the hours value in a <a href="#date">Date</a> object using Universal Coordinated Time (UTC).                                       |
| **getMinutes**         | () =&gt; number                                                                                              | Gets the minutes of a <a href="#date">Date</a> object, using local time.                                                                |
| **getUTCMinutes**      | () =&gt; number                                                                                              | Gets the minutes of a <a href="#date">Date</a> object using Universal Coordinated Time (UTC).                                           |
| **getSeconds**         | () =&gt; number                                                                                              | Gets the seconds of a <a href="#date">Date</a> object, using local time.                                                                |
| **getUTCSeconds**      | () =&gt; number                                                                                              | Gets the seconds of a <a href="#date">Date</a> object using Universal Coordinated Time (UTC).                                           |
| **getMilliseconds**    | () =&gt; number                                                                                              | Gets the milliseconds of a <a href="#date">Date</a>, using local time.                                                                  |
| **getUTCMilliseconds** | () =&gt; number                                                                                              | Gets the milliseconds of a <a href="#date">Date</a> object using Universal Coordinated Time (UTC).                                      |
| **getTimezoneOffset**  | () =&gt; number                                                                                              | Gets the difference in minutes between the time on the local computer and Universal Coordinated Time (UTC).                             |
| **setTime**            | (time: number) =&gt; number                                                                                  | Sets the date and time value in the <a href="#date">Date</a> object.                                                                    |
| **setMilliseconds**    | (ms: number) =&gt; number                                                                                    | Sets the milliseconds value in the <a href="#date">Date</a> object using local time.                                                    |
| **setUTCMilliseconds** | (ms: number) =&gt; number                                                                                    | Sets the milliseconds value in the <a href="#date">Date</a> object using Universal Coordinated Time (UTC).                              |
| **setSeconds**         | (sec: number, ms?: number \| undefined) =&gt; number                                                         | Sets the seconds value in the <a href="#date">Date</a> object using local time.                                                         |
| **setUTCSeconds**      | (sec: number, ms?: number \| undefined) =&gt; number                                                         | Sets the seconds value in the <a href="#date">Date</a> object using Universal Coordinated Time (UTC).                                   |
| **setMinutes**         | (min: number, sec?: number \| undefined, ms?: number \| undefined) =&gt; number                              | Sets the minutes value in the <a href="#date">Date</a> object using local time.                                                         |
| **setUTCMinutes**      | (min: number, sec?: number \| undefined, ms?: number \| undefined) =&gt; number                              | Sets the minutes value in the <a href="#date">Date</a> object using Universal Coordinated Time (UTC).                                   |
| **setHours**           | (hours: number, min?: number \| undefined, sec?: number \| undefined, ms?: number \| undefined) =&gt; number | Sets the hour value in the <a href="#date">Date</a> object using local time.                                                            |
| **setUTCHours**        | (hours: number, min?: number \| undefined, sec?: number \| undefined, ms?: number \| undefined) =&gt; number | Sets the hours value in the <a href="#date">Date</a> object using Universal Coordinated Time (UTC).                                     |
| **setDate**            | (date: number) =&gt; number                                                                                  | Sets the numeric day-of-the-month value of the <a href="#date">Date</a> object using local time.                                        |
| **setUTCDate**         | (date: number) =&gt; number                                                                                  | Sets the numeric day of the month in the <a href="#date">Date</a> object using Universal Coordinated Time (UTC).                        |
| **setMonth**           | (month: number, date?: number \| undefined) =&gt; number                                                     | Sets the month value in the <a href="#date">Date</a> object using local time.                                                           |
| **setUTCMonth**        | (month: number, date?: number \| undefined) =&gt; number                                                     | Sets the month value in the <a href="#date">Date</a> object using Universal Coordinated Time (UTC).                                     |
| **setFullYear**        | (year: number, month?: number \| undefined, date?: number \| undefined) =&gt; number                         | Sets the year of the <a href="#date">Date</a> object using local time.                                                                  |
| **setUTCFullYear**     | (year: number, month?: number \| undefined, date?: number \| undefined) =&gt; number                         | Sets the year value in the <a href="#date">Date</a> object using Universal Coordinated Time (UTC).                                      |
| **toUTCString**        | () =&gt; string                                                                                              | Returns a date converted to a string using Universal Coordinated Time (UTC).                                                            |
| **toISOString**        | () =&gt; string                                                                                              | Returns a date as a string value in ISO format.                                                                                         |
| **toJSON**             | (key?: any) =&gt; string                                                                                     | Used by the JSON.stringify method to enable the transformation of an object's data for JavaScript Object Notation (JSON) serialization. |


#### PendingNotification

| Prop             | Type                | Description                                                       |
| ---------------- | ------------------- | ----------------------------------------------------------------- |
| **`id`**         | <code>number</code> | Notification id. When omitted, a unique positive id is generated. |
| **`scheduleAt`** | <code>number</code> | Epoch milliseconds of the next delivery.                          |


#### DeliveredNotification

| Prop             | Type                | Description                                                       |
| ---------------- | ------------------- | ----------------------------------------------------------------- |
| **`id`**         | <code>number</code> | Notification id. When omitted, a unique positive id is generated. |
| **`receivedAt`** | <code>number</code> | Epoch milliseconds of when the notification was delivered.        |


#### ActionGroup

| Prop          | Type                              | Description                                                                              |
| ------------- | --------------------------------- | ---------------------------------------------------------------------------------------- |
| **`id`**      | <code>string</code>               | Identifier referenced by <a href="#scheduleoptions">`ScheduleOptions.actionGroupId`</a>. |
| **`actions`** | <code>NotificationAction[]</code> |                                                                                          |


#### NotificationAction

| Prop                   | Type                 | Description                                                                   |
| ---------------------- | -------------------- | ----------------------------------------------------------------------------- |
| **`id`**               | <code>string</code>  | Unique identifier for the action, sent back on `notificationActionPerformed`. |
| **`title`**            | <code>string</code>  | Label shown on the action button.                                             |
| **`destructive`**      | <code>boolean</code> | iOS only. Renders the action with a destructive style.                        |
| **`input`**            | <code>boolean</code> | iOS only. Shows a text input on the notification (e.g. for quick reply).      |
| **`inputPlaceholder`** | <code>string</code>  | iOS only. Placeholder for the text input.                                     |


#### PushToken

| Prop        | Type                        |
| ----------- | --------------------------- |
| **`token`** | <code>string \| null</code> |


#### NotificationActionEvent

| Prop               | Type                                                                    | Description                                                   |
| ------------------ | ----------------------------------------------------------------------- | ------------------------------------------------------------- |
| **`actionId`**     | <code>string</code>                                                     |                                                               |
| **`notification`** | <code><a href="#deliverednotification">DeliveredNotification</a></code> |                                                               |
| **`inputValue`**   | <code>string</code>                                                     | Text typed by the user, when the action supports input (iOS). |


#### PluginListenerHandle

| Prop         | Type                                      |
| ------------ | ----------------------------------------- |
| **`remove`** | <code>() =&gt; Promise&lt;void&gt;</code> |


#### NotificationDismissedEvent

| Prop     | Type                |
| -------- | ------------------- |
| **`id`** | <code>number</code> |


### Type Aliases


#### PermissionDisplay

<code>'prompt' | 'prompt-with-rationale' | 'granted' | 'denied'</code>


#### NotificationImportance

<code>'none' | 'min' | 'low' | 'default' | 'high' | 'max'</code>


#### NotificationVisibility

<code>'public' | 'private' | 'secret'</code>


#### Record

Construct a type with a set of properties K of type T

<code>{ [P in K]: T; }</code>


#### NotificationPriority

<code>'min' | 'low' | 'default' | 'high' | 'max'</code>


#### ScheduleEvery

<code>'minute' | 'hour' | 'day' | 'week'</code>

</docgen-api>
