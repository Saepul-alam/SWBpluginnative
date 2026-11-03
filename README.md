# swbnotif

Production-grade Capacitor plugin for local and scheduled notifications on Android (API 21–35), iOS (12+), and Web with zero external dependencies.

Built from the ground up for enterprise resilience: Survives app process death, device reboots, Doze battery optimizations, and timezone changes with strict payload validation, structured error codes, and zero dependency on Firebase or external cloud services.

---

## Installation

### Via GitHub Release Tarball (Recommended)

Install directly from the published release asset without requiring an npm registry account:

```bash
npm install https://github.com/Saepul-alam/SWBpluginnative/releases/download/v0.0.1/swbnotif-0.0.1.tgz
npx cap sync
```

*Or using the dedicated `swbnotif-v0.0.1` tag:*
```bash
npm install https://github.com/Saepul-alam/SWBpluginnative/releases/download/swbnotif-v0.0.1/swbnotif-0.0.1.tgz
npx cap sync
```

### Via Git Branch

```bash
npm install https://github.com/Saepul-alam/SWBpluginnative.git#notify
npx cap sync
```

### Via Local Tarball / Directory

```bash
# From tarball
npm install ./path/to/swbnotif-0.0.1.tgz
npx cap sync

# From local directory (monorepo / local development)
npm install /path/to/swbnotif
npx cap sync
```

---

## Platform Support Matrix

| Feature | Android (API 21–35) | iOS (12+) | Web (Modern Browsers) |
| :--- | :---: | :---: | :---: |
| **Instant Local Delivery** | ✅ | ✅ | ✅ (Web Notifications API) |
| **Exact Alarm Scheduling** | ✅ (`AlarmManager` exact) | ✅ (UNCalendarNotification) | ⚠️ Active tab only |
| **Reboot Rescheduling** | ✅ (`BOOT_COMPLETED` receiver) | ✅ (System-managed) | ❌ |
| **Timezone Change Safe** | ✅ (`TIMEZONE_CHANGED` receiver) | ✅ (Timezone-aware components) | ❌ |
| **Repeating Notifications**| ✅ (`minute`, `hour`, `day`, `week`) | ✅ (`UNTimeIntervalTrigger`) | ⚠️ Active tab only |
| **Notification Channels** | ✅ (Importance, Sound, Lights, Vibrate) | ℹ️ No-op (Platform N/A) | ℹ️ No-op (Platform N/A) |
| **Action Buttons** | ✅ (Up to 4 actions) | ✅ (Custom categories) | ❌ |
| **Inline Quick Reply** | ℹ️ Tapped action event | ✅ (`UNTextInputNotificationAction`) | ❌ |
| **Cold-Start Intent Catch**| ✅ (`getLastAction()`) | ✅ (`getLastAction()`) | ✅ (`getLastAction()`) |
| **Dismiss Event Tracking** | ✅ (`notificationDismissed`) | ✅ (`notificationDismissed`) | ✅ (`onclose` event) |
| **App Icon Badge** | ✅ (Launcher setNumber) | ✅ (SpringBoard badge) | ✅ (App Badging API) |

---

## Real-World Examples

### 1. Requesting Permissions (Android 13+ & iOS)

```ts
import { SwbNotif } from 'swbnotif';

// 1. Check and request notification permissions
const permission = await SwbNotif.requestPermission();
if (!permission.granted) {
  console.warn('Notification permission was denied by the user');
}

// 2. Check and request Exact Alarm permission (Android 12+)
const exactAlarm = await SwbNotif.checkExactAlarmPermission();
if (!exactAlarm.granted) {
  // Opens system settings for user to grant SCHEDULE_EXACT_ALARM
  await SwbNotif.requestExactAlarmPermission();
}
```

### 2. Creating Channels (Android 8+)

Channels allow categorizing notifications with dedicated sound, vibration, and importance levels:

```ts
await SwbNotif.createChannel({
  id: 'orders',
  name: 'Order Updates',
  description: 'Real-time updates regarding your ongoing orders',
  importance: 'high',
  sound: 'order_bell', // raw/order_bell.mp3 in Android res/raw
  vibration: true,
  lights: true,
  lightColor: '#00AAFF',
  visibility: 'private',
});
```

### 3. Immediate Local Notification

```ts
const { id } = await SwbNotif.schedule({
  title: 'Pesanan Diterima',
  body: 'Driver sedang menuju lokasi pengambilan paket.',
  channelId: 'orders',
  extra: { orderId: 'TRX-98214', route: '/orders/TRX-98214' },
  badge: 1,
});
```

### 4. Scheduled & Repeating Notifications

```ts
// Schedule for a specific time tomorrow
const tomorrow = new Date(Date.now() + 24 * 60 * 60 * 1000);

await SwbNotif.schedule({
  title: 'Pengingat Absensi',
  body: 'Jangan lupa untuk melakukan absensi pagi.',
  scheduleAt: tomorrow,
  every: 'day', // repeats daily
  count: 5,     // repeats 5 times, omit for indefinite
});

// Inspect pending notifications
const { notifications } = await SwbNotif.getPending();
console.log('Pending alarms:', notifications);

// Cancel a scheduled notification
await SwbNotif.cancel({ id: 101 });
```

### 5. Action Buttons & Quick Reply

Define interactive action groups with destructive buttons and text inputs:

```ts
await SwbNotif.createActionGroup({
  id: 'chat_reply',
  actions: [
    {
      id: 'reply',
      title: 'Balas',
      input: true,
      inputPlaceholder: 'Tulis balasan pesan...',
    },
    {
      id: 'mark_read',
      title: 'Tandai Dibaca',
    },
    {
      id: 'delete',
      title: 'Hapus',
      destructive: true,
    },
  ],
});

await SwbNotif.schedule({
  title: 'Budi Santoso',
  body: 'Halo, apakah paketnya sudah siap dikirim?',
  actionGroupId: 'chat_reply',
});
```

### 6. Event Listeners, Deep Linking & Cold Start

Handle user taps and dismiss actions across warm, background, and cold start states:

```ts
// Notification received in foreground
SwbNotif.addListener('notificationReceived', notification => {
  console.log('Delivered:', notification.title, notification.extra);
});

// User clicked notification or action button
SwbNotif.addListener('notificationActionPerformed', event => {
  const { actionId, notification, inputValue } = event;
  console.log('Action performed:', actionId);
  
  if (inputValue) {
    console.log('User typed quick reply:', inputValue);
  }

  // Deep Link Navigation
  if (notification.extra?.route) {
    router.push(notification.extra.route);
  }
});

// Notification dismissed by user (swiped away)
SwbNotif.addListener('notificationDismissed', ({ id }) => {
  console.log('User dismissed notification:', id);
});

// Cold Start: App launched by tapping notification when app was closed
const coldStartAction = await SwbNotif.getLastAction();
if (coldStartAction) {
  console.log('App opened via notification tap:', coldStartAction);
  if (coldStartAction.notification.extra?.route) {
    router.push(coldStartAction.notification.extra.route);
  }
}
```

---

## System Bounds & Payload Limits

To protect system stability and avoid out-of-memory errors or Intent payload limits, safety thresholds are strictly enforced on all platforms:

| Constraint | Limit | Description |
| :--- | :---: | :--- |
| `MAX_TITLE_LENGTH` | **256** chars | Title cannot be empty and cannot exceed 256 characters. |
| `MAX_BODY_LENGTH` | **4096** chars | Body text maximum length. |
| `MAX_EXTRA_BYTES` | **32768** bytes (32 KB) | Serialized JSON `extra` dictionary payload size limit. |
| `MAX_SCHEDULED` | **100** items | Maximum pending scheduled alarms per app. |
| `MAX_DELIVERED` | **100** items | Retention cap for delivered notifications store (FIFO eviction). |
| `MAX_ACTIONS_PER_GROUP`| **4** actions | Maximum number of interactive action buttons per group. |

Violations of these bounds reject with structured error code `INVALID_ARGUMENT` or `STORAGE_LIMIT_EXCEEDED`.

---

## Error Codes

Every native error rejection returns a structured machine-readable error code:

| Error Code | Meaning / Cause |
| :--- | :--- |
| `PERMISSION_DENIED` | Notification permission was denied or revoked by the user or OS. |
| `EXACT_ALARM_NOT_ALLOWED` | Android 12+ exact alarm scheduling permission (`SCHEDULE_EXACT_ALARM`) is not allowed. |
| `CHANNEL_NOT_FOUND` | Notification channel requested does not exist. |
| `INVALID_SCHEDULE` | Date or schedule interval is invalid or points to the past without repetition. |
| `INVALID_ARGUMENT` | Payload validation failed (title missing, string length exceeded, extra payload >32KB, duplicate ID). |
| `NOTIFICATION_NOT_FOUND` | Targeted notification ID not found for update or removal. |
| `STORAGE_LIMIT_EXCEEDED` | Attempted to schedule more than 100 notifications concurrently. |
| `UNKNOWN` | Unclassified native exception. |

---

## Troubleshooting Guide

### 1. Notifications do not appear on Android 13+
- **Cause:** Android 13 (API 33) introduced the runtime `POST_NOTIFICATIONS` permission.
- **Fix:** Ensure you call `await SwbNotif.requestPermission()` before attempting to schedule or post notifications.

### 2. Scheduled alarms are delayed on Android 12+
- **Cause:** Android 12+ restricts exact alarms to conserve battery.
- **Fix:** Check `await SwbNotif.checkExactAlarmPermission()`. If false, invoke `await SwbNotif.requestExactAlarmPermission()` to guide the user to System Settings. If exact alarms are not granted, the plugin automatically uses inexact scheduling (`setAndAllowWhileIdle`).

### 3. Notification sound does not change on Android
- **Cause:** Android notification channels are immutable once created by the operating system.
- **Fix:** Changing sound, vibration, or importance requires deleting the channel (`deleteChannel({ id })`) and recreating it, or using a new channel ID.

---

## Security & Privacy

- **Data Minimization:** No sensitive user data (PII) is captured or transmitted. Local store items are automatically pruned to 100 entries.
- **No Console Leaks:** Notification titles, bodies, and payload extras are never output to standard logcat or console logs in production builds.
- **Intent Boundary Protection:** All Android `PendingIntent` instances use `FLAG_IMMUTABLE` and specify explicit package/class destinations to prevent intent hijacking.
- **Data Erasure:** Calling `removeAllDelivered()`, `cancelAll()`, and `clearLastAction()` completely purges locally cached notification records and payload states.

---

## Deprecation & SemVer Policy

- This plugin strictly follows **Semantic Versioning 2.0.0**.
- Breaking changes to method signatures, return types, or behavior will only occur in major releases (`X.0.0`) with a minimum 6-month deprecation notice in `CHANGELOG.md`.
- Legacy 0.0.x APIs (`Swb.echo`) are deprecated and replaced by `SwbNotif`.

---

## API Reference

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
* [`getLastAction()`](#getlastaction)
* [`clearLastAction()`](#clearlastaction)
* [`addListener('notificationReceived', ...)`](#addlistenernotificationreceived)
* [`addListener('notificationActionPerformed', ...)`](#addlistenernotificationactionperformed)
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
