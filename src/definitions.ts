import type { PluginListenerHandle } from '@capacitor/core';

export type NotificationImportance =
  | 'none'
  | 'min'
  | 'low'
  | 'default'
  | 'high'
  | 'max';

export type NotificationPriority = 'min' | 'low' | 'default' | 'high' | 'max';

export type NotificationVisibility = 'public' | 'private' | 'secret';

export type ScheduleEvery = 'minute' | 'hour' | 'day' | 'week';

export type PermissionDisplay =
  | 'prompt'
  | 'prompt-with-rationale'
  | 'granted'
  | 'denied';

/**
 * Structured error codes emitted across JS, Android, iOS, and Web.
 */
export enum ErrorCode {
  PERMISSION_DENIED = 'PERMISSION_DENIED',
  EXACT_ALARM_NOT_ALLOWED = 'EXACT_ALARM_NOT_ALLOWED',
  CHANNEL_NOT_FOUND = 'CHANNEL_NOT_FOUND',
  INVALID_SCHEDULE = 'INVALID_SCHEDULE',
  INVALID_ARGUMENT = 'INVALID_ARGUMENT',
  NOTIFICATION_NOT_FOUND = 'NOTIFICATION_NOT_FOUND',
  STORAGE_LIMIT_EXCEEDED = 'STORAGE_LIMIT_EXCEEDED',
  UNKNOWN = 'UNKNOWN',
}

/**
 * System and payload safety bounds enforced across all platforms.
 */
export const SWBNOTIF_LIMITS = {
  MAX_TITLE_LENGTH: 256,
  MAX_BODY_LENGTH: 4096,
  MAX_EXTRA_BYTES: 32768, // 32 KB
  MAX_SCHEDULED: 100,
  MAX_DELIVERED: 100,
  MAX_ACTIONS_PER_GROUP: 4,
} as const;

export interface PermissionStatus {
  /** Whether the app is currently allowed to post notifications. */
  granted: boolean;
  /** Platform specific display state, useful to know if you can still ask. */
  display: PermissionDisplay;
}

export interface NotificationAction {
  /** Unique identifier for the action, sent back on `notificationActionPerformed`. */
  id: string;
  /** Label shown on the action button. */
  title: string;
  /** iOS only. Renders the action with a destructive style. */
  destructive?: boolean;
  /** iOS only. Shows a text input on the notification (e.g. for quick reply). */
  input?: boolean;
  /** iOS only. Placeholder for the text input. */
  inputPlaceholder?: string;
}

export interface ActionGroup {
  /** Identifier referenced by `ScheduleOptions.actionGroupId`. */
  id: string;
  actions: NotificationAction[];
}

export interface NotificationChannel {
  id: string;
  name: string;
  description?: string;
  importance?: NotificationImportance;
  /** Default sound for the channel (Android only). Raw resource name or URI. */
  sound?: string;
  vibration?: boolean;
  lights?: boolean;
  /** Hex color, e.g. `#FF0000` (Android only). */
  lightColor?: string;
  visibility?: NotificationVisibility;
}

export interface ScheduleOptions {
  /** Notification id. When omitted, a unique positive id is generated. */
  id?: number;
  title: string;
  body?: string;
  /** Android only. Expanded body text. */
  largeBody?: string;
  /** Android only. Text shown in the collapsed summary. */
  summaryText?: string;
  /** Channel to post to. Android 8+ requires a valid channel id. */
  channelId?: string;
  /** Sound to play. Raw resource name (Android) / bundle file name (iOS) or a URI. */
  sound?: string;
  /** Drawable/mipmap resource name (Android) or bundle image name (iOS). */
  smallIcon?: string;
  largeIcon?: string;
  /** Hex color applied to the notification icon (Android only). */
  iconColor?: string;
  /** Number shown on the app icon (Android) or the icon badge (iOS). */
  badge?: number;
  /** Arbitrary data delivered alongside the notification (max 32KB). */
  extra?: Record<string, any>;
  /** Reference to an action group created with `createActionGroup`. */
  actionGroupId?: string;
  priority?: NotificationPriority;
  /** Group key used to bundle notifications (Android only). */
  group?: string;
  /** Default `true`. */
  autoCancel?: boolean;
  /** Keep the notification visible and non dismissible. */
  ongoing?: boolean;
  /**
   * When to deliver the notification. Accepts a `Date`, an ISO string or
   * epoch milliseconds. When omitted the notification is delivered immediately.
   */
  scheduleAt?: Date | string | number;
  /** Repeats the notification on the given interval starting at `scheduleAt`. */
  every?: ScheduleEvery;
  /** How many times to repeat. Omit for an unlimited number of repetitions. */
  count?: number;
}

export interface PendingNotification extends ScheduleOptions {
  id: number;
  /** Epoch milliseconds of the next delivery. */
  scheduleAt?: number;
}

export interface DeliveredNotification extends ScheduleOptions {
  id: number;
  /** Epoch milliseconds of when the notification was delivered. */
  receivedAt: number;
}

export interface NotificationActionEvent {
  actionId: string;
  notification: DeliveredNotification;
  /** Text typed by the user, when the action supports input (iOS). */
  inputValue?: string;
}

export interface NotificationDismissedEvent {
  id: number;
}

export interface SwbNotifPlugin {
  /** Check whether notifications are allowed without prompting the user. */
  checkPermission(): Promise<PermissionStatus>;
  /** Prompt the user for notification permission. */
  requestPermission(): Promise<PermissionStatus>;

  /**
   * Check whether exact alarms are allowed (Android 12+).
   * Always returns `true` on iOS/web.
   */
  checkExactAlarmPermission(): Promise<{ granted: boolean }>;
  /**
   * Open the system settings so the user can allow exact alarms (Android 12+).
   * No-op on iOS/web.
   */
  requestExactAlarmPermission(): Promise<{ granted: boolean }>;

  /** Create or update an Android notification channel. No-op on iOS/web. */
  createChannel(options: NotificationChannel): Promise<void>;
  deleteChannel(options: { id: string }): Promise<void>;
  listChannels(): Promise<{ channels: NotificationChannel[] }>;

  /** Display or schedule a single notification. */
  schedule(options: ScheduleOptions): Promise<{ id: number }>;
  scheduleMultiple(options: {
    notifications: ScheduleOptions[];
  }): Promise<{ ids: number[] }>;

  /** List notifications that are scheduled but not yet delivered. */
  getPending(): Promise<{ notifications: PendingNotification[] }>;
  /** Cancel a scheduled or currently displayed notification. */
  cancel(options: { id: number }): Promise<void>;
  /** Cancel every scheduled and currently displayed notification. */
  cancelAll(): Promise<void>;

  /** List notifications that were delivered by this plugin and not yet removed. */
  getDelivered(): Promise<{ notifications: DeliveredNotification[] }>;
  removeDelivered(options: { ids: number[] }): Promise<void>;
  removeAllDelivered(): Promise<void>;

  /** Set the app icon badge count. */
  setBadgeCount(options: { count: number }): Promise<void>;
  getBadgeCount(): Promise<{ count: number }>;

  /** Register notification actions for Android/iOS. */
  createActionGroup(options: ActionGroup): Promise<void>;
  deleteActionGroup(options: { id: string }): Promise<void>;

  /** The action that launched the app, if any. Cleared once consumed. */
  getLastAction(): Promise<NotificationActionEvent | null>;
  /** Clear any cached cold-start action without consuming it. */
  clearLastAction(): Promise<void>;

  addListener(
    eventName: 'notificationReceived',
    listenerFunc: (notification: DeliveredNotification) => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;
  addListener(
    eventName: 'notificationActionPerformed',
    listenerFunc: (event: NotificationActionEvent) => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;
  addListener(
    eventName: 'notificationDismissed',
    listenerFunc: (event: NotificationDismissedEvent) => void,
  ): Promise<PluginListenerHandle> & PluginListenerHandle;

  removeAllListeners(): Promise<void>;
}
