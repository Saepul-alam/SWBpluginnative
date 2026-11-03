import { WebPlugin } from '@capacitor/core';

import type {
  ActionGroup,
  DeliveredNotification,
  NotificationActionEvent,
  NotificationChannel,
  NotificationDismissedEvent,
  PendingNotification,
  PermissionStatus,
  PushToken,
  ScheduleEvery,
  ScheduleOptions,
  SwbNotifPlugin,
} from './definitions';
import { ErrorCode, SWBNOTIF_LIMITS } from './definitions';
import {
  PluginError,
  validateActionGroup,
  validateChannel,
  validateScheduleMultiple,
  validateScheduleOptions,
} from './validation';

interface WebScheduled {
  id: number;
  options: ScheduleOptions;
  nextRun: number;
  remaining?: number;
  timer?: ReturnType<typeof setTimeout>;
}

const INTERVAL_MS: Record<ScheduleEvery, number> = {
  minute: 60 * 1000,
  hour: 60 * 60 * 1000,
  day: 24 * 60 * 60 * 1000,
  week: 7 * 24 * 60 * 60 * 1000,
};

function toMillis(
  value: Date | string | number | undefined,
): number | undefined {
  if (value === undefined || value === null) {
    return undefined;
  }
  if (value instanceof Date) {
    return value.getTime();
  }
  if (typeof value === 'number') {
    return value;
  }
  const parsed = Date.parse(value);
  return Number.isNaN(parsed) ? undefined : parsed;
}

export class SwbNotifWeb extends WebPlugin implements SwbNotifPlugin {
  private readonly channels = new Map<string, NotificationChannel>();
  private readonly actionGroups = new Map<string, ActionGroup>();
  private readonly scheduled = new Map<number, WebScheduled>();
  private readonly delivered = new Map<number, DeliveredNotification>();
  private readonly notifications = new Map<number, Notification>();

  private nextId = Date.now();
  private badge = 0;
  private lastAction: NotificationActionEvent | null = null;

  private get api(): typeof Notification | undefined {
    return typeof window !== 'undefined' ? window.Notification : undefined;
  }

  async checkPermission(): Promise<PermissionStatus> {
    const api = this.api;
    if (!api) {
      return { granted: false, display: 'denied' };
    }
    const granted = api.permission === 'granted';
    return {
      granted,
      display: granted
        ? 'granted'
        : api.permission === 'denied'
        ? 'denied'
        : 'prompt',
    };
  }

  async requestPermission(): Promise<PermissionStatus> {
    const api = this.api;
    if (!api) {
      return { granted: false, display: 'denied' };
    }
    const result = await api.requestPermission();
    return {
      granted: result === 'granted',
      display: result === 'granted' ? 'granted' : 'denied',
    };
  }

  async checkExactAlarmPermission(): Promise<{ granted: boolean }> {
    return { granted: true };
  }

  async requestExactAlarmPermission(): Promise<{ granted: boolean }> {
    return { granted: true };
  }

  async createChannel(options: NotificationChannel): Promise<void> {
    validateChannel(options);
    this.channels.set(options.id, options);
  }

  async deleteChannel(options: { id: string }): Promise<void> {
    if (!options || !options.id || typeof options.id !== 'string') {
      throw new PluginError(
        'A channel id is required',
        ErrorCode.INVALID_ARGUMENT,
      );
    }
    this.channels.delete(options.id);
  }

  async listChannels(): Promise<{ channels: NotificationChannel[] }> {
    return { channels: Array.from(this.channels.values()) };
  }

  async schedule(options: ScheduleOptions): Promise<{ id: number }> {
    validateScheduleOptions(options);

    if (this.scheduled.size >= SWBNOTIF_LIMITS.MAX_SCHEDULED) {
      throw new PluginError(
        `Scheduled notification limit of ${SWBNOTIF_LIMITS.MAX_SCHEDULED} exceeded`,
        ErrorCode.STORAGE_LIMIT_EXCEEDED,
      );
    }

    const id = options.id ?? this.nextId++;
    const at = toMillis(options.scheduleAt) ?? Date.now();
    const delay = Math.max(0, at - Date.now());

    const entry: WebScheduled = {
      id,
      options: { ...options, id },
      nextRun: at,
      remaining: options.count,
    };

    if (delay === 0 && !options.every) {
      await this.show(id, entry.options);
    } else {
      this.clearScheduled(id);
      this.scheduled.set(id, entry);
      this.arm(entry);
    }

    return { id };
  }

  async scheduleMultiple(options: {
    notifications: ScheduleOptions[];
  }): Promise<{ ids: number[] }> {
    if (!options || !Array.isArray(options.notifications)) {
      throw new PluginError(
        'notifications must be an array',
        ErrorCode.INVALID_ARGUMENT,
      );
    }
    validateScheduleMultiple(options.notifications);

    const ids: number[] = [];
    for (const notification of options.notifications) {
      const scheduled = await this.schedule(notification);
      ids.push(scheduled.id);
    }
    return { ids };
  }

  async getPending(): Promise<{ notifications: PendingNotification[] }> {
    return {
      notifications: Array.from(this.scheduled.values()).map(entry => ({
        ...entry.options,
        id: entry.id,
        scheduleAt: entry.nextRun,
      })),
    };
  }

  async cancel(options: { id: number }): Promise<void> {
    if (!options || typeof options.id !== 'number') {
      throw new PluginError(
        'A notification id is required',
        ErrorCode.INVALID_ARGUMENT,
      );
    }
    this.clearScheduled(options.id);
    this.notifications.get(options.id)?.close();
    this.notifications.delete(options.id);
    this.delivered.delete(options.id);
  }

  async cancelAll(): Promise<void> {
    for (const id of Array.from(this.scheduled.keys())) {
      this.clearScheduled(id);
    }
    for (const notification of this.notifications.values()) {
      notification.close();
    }
    this.notifications.clear();
    this.delivered.clear();
  }

  async getDelivered(): Promise<{ notifications: DeliveredNotification[] }> {
    return { notifications: Array.from(this.delivered.values()) };
  }

  async removeDelivered(options: { ids: number[] }): Promise<void> {
    if (!options || !Array.isArray(options.ids)) {
      throw new PluginError('ids must be an array', ErrorCode.INVALID_ARGUMENT);
    }
    for (const id of options.ids) {
      this.notifications.get(id)?.close();
      this.notifications.delete(id);
      this.delivered.delete(id);
    }
  }

  async removeAllDelivered(): Promise<void> {
    for (const notification of this.notifications.values()) {
      notification.close();
    }
    this.notifications.clear();
    this.delivered.clear();
  }

  async setBadgeCount(options: { count: number }): Promise<void> {
    if (!options || typeof options.count !== 'number' || options.count < 0) {
      throw new PluginError(
        'count must be a non-negative number',
        ErrorCode.INVALID_ARGUMENT,
      );
    }
    this.badge = options.count;
    const navigatorWithBadge = (typeof navigator !== 'undefined'
      ? navigator
      : {}) as Navigator & {
      setAppBadge?: (count?: number) => Promise<void>;
    };
    await navigatorWithBadge.setAppBadge?.(options.count);
  }

  async getBadgeCount(): Promise<{ count: number }> {
    return { count: this.badge };
  }

  async createActionGroup(options: ActionGroup): Promise<void> {
    validateActionGroup(options);
    this.actionGroups.set(options.id, options);
  }

  async deleteActionGroup(options: { id: string }): Promise<void> {
    if (!options || !options.id || typeof options.id !== 'string') {
      throw new PluginError(
        'An action group id is required',
        ErrorCode.INVALID_ARGUMENT,
      );
    }
    this.actionGroups.delete(options.id);
  }

  async registerPush(): Promise<PushToken> {
    return { token: null };
  }

  async getPushToken(): Promise<PushToken> {
    return { token: null };
  }

  async unregisterPush(): Promise<void> {
    return;
  }

  async getLastAction(): Promise<NotificationActionEvent | null> {
    const action = this.lastAction;
    this.lastAction = null;
    return action;
  }

  async clearLastAction(): Promise<void> {
    this.lastAction = null;
  }

  private arm(entry: WebScheduled): void {
    const delay = Math.max(0, entry.nextRun - Date.now());
    entry.timer = setTimeout(() => {
      void this.fire(entry);
    }, delay);
  }

  private async fire(entry: WebScheduled): Promise<void> {
    const delivered = await this.show(entry.id, entry.options);

    if (entry.options.every) {
      if (entry.remaining !== undefined) {
        entry.remaining -= 1;
        if (entry.remaining <= 0) {
          this.scheduled.delete(entry.id);
          return;
        }
      }
      entry.nextRun = Date.now() + INTERVAL_MS[entry.options.every];
      this.arm(entry);
      return;
    }

    this.scheduled.delete(entry.id);
    void delivered;
  }

  private async show(
    id: number,
    options: ScheduleOptions,
  ): Promise<DeliveredNotification> {
    const api = this.api;
    const delivered: DeliveredNotification = {
      ...options,
      id,
      receivedAt: Date.now(),
    };

    if (api && api.permission === 'granted') {
      const notification = new api(options.title, {
        body: options.body,
        tag: String(id),
        silent: options.sound === 'none',
      });
      notification.onclick = () => {
        const event: NotificationActionEvent = {
          actionId: 'tap',
          notification: delivered,
        };
        this.lastAction = event;
        this.notifyListeners('notificationActionPerformed', event);
      };
      notification.onclose = () => {
        const event: NotificationDismissedEvent = { id };
        this.notifyListeners('notificationDismissed', event);
      };
      this.notifications.set(id, notification);
    }

    if (this.delivered.size >= SWBNOTIF_LIMITS.MAX_DELIVERED) {
      const oldestKey = this.delivered.keys().next().value;
      if (oldestKey !== undefined) {
        this.delivered.delete(oldestKey);
      }
    }
    this.delivered.set(id, delivered);
    this.notifyListeners('notificationReceived', delivered);
    return delivered;
  }

  private clearScheduled(id: number): void {
    const entry = this.scheduled.get(id);
    if (entry?.timer) {
      clearTimeout(entry.timer);
    }
    this.scheduled.delete(id);
  }
}
