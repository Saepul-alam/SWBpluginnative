import type {
  ActionGroup,
  NotificationChannel,
  ScheduleOptions,
} from './definitions';
import { ErrorCode, SWBNOTIF_LIMITS } from './definitions';

export class PluginError extends Error {
  code: ErrorCode;

  constructor(message: string, code: ErrorCode = ErrorCode.INVALID_ARGUMENT) {
    super(message);
    this.name = 'PluginError';
    this.code = code;
    Object.setPrototypeOf(this, PluginError.prototype);
  }
}

export function validateScheduleOptions(options: ScheduleOptions): void {
  if (!options || typeof options !== 'object') {
    throw new PluginError(
      'Schedule options must be an object',
      ErrorCode.INVALID_ARGUMENT,
    );
  }

  if (typeof options.title !== 'string' || options.title.trim().length === 0) {
    throw new PluginError(
      'title is required and cannot be empty',
      ErrorCode.INVALID_ARGUMENT,
    );
  }

  if (options.title.length > SWBNOTIF_LIMITS.MAX_TITLE_LENGTH) {
    throw new PluginError(
      `title exceeds maximum length of ${SWBNOTIF_LIMITS.MAX_TITLE_LENGTH} characters`,
      ErrorCode.INVALID_ARGUMENT,
    );
  }

  if (
    options.body !== undefined &&
    options.body !== null &&
    typeof options.body === 'string' &&
    options.body.length > SWBNOTIF_LIMITS.MAX_BODY_LENGTH
  ) {
    throw new PluginError(
      `body exceeds maximum length of ${SWBNOTIF_LIMITS.MAX_BODY_LENGTH} characters`,
      ErrorCode.INVALID_ARGUMENT,
    );
  }

  if (options.extra !== undefined && options.extra !== null) {
    if (typeof options.extra !== 'object') {
      throw new PluginError(
        'extra must be an object/record',
        ErrorCode.INVALID_ARGUMENT,
      );
    }
    try {
      const serialized = JSON.stringify(options.extra);
      const byteLength = new TextEncoder().encode(serialized).length;
      if (byteLength > SWBNOTIF_LIMITS.MAX_EXTRA_BYTES) {
        throw new PluginError(
          `extra payload exceeds maximum size of ${SWBNOTIF_LIMITS.MAX_EXTRA_BYTES} bytes`,
          ErrorCode.INVALID_ARGUMENT,
        );
      }
    } catch (e: any) {
      if (e instanceof PluginError) {
        throw e;
      }
      throw new PluginError(
        'extra payload cannot be serialized to JSON',
        ErrorCode.INVALID_ARGUMENT,
      );
    }
  }

  if (options.count !== undefined && options.count !== null) {
    if (typeof options.count !== 'number' || options.count < 1) {
      throw new PluginError(
        'count must be a positive integer',
        ErrorCode.INVALID_ARGUMENT,
      );
    }
  }

  if (options.id !== undefined && options.id !== null) {
    if (typeof options.id !== 'number' || !Number.isFinite(options.id)) {
      throw new PluginError(
        'id must be a finite number',
        ErrorCode.INVALID_ARGUMENT,
      );
    }
  }
}

export function validateScheduleMultiple(
  notifications: ScheduleOptions[],
): void {
  if (!Array.isArray(notifications)) {
    throw new PluginError(
      'notifications must be an array',
      ErrorCode.INVALID_ARGUMENT,
    );
  }

  if (notifications.length > SWBNOTIF_LIMITS.MAX_SCHEDULED) {
    throw new PluginError(
      `Cannot schedule more than ${SWBNOTIF_LIMITS.MAX_SCHEDULED} notifications at once`,
      ErrorCode.STORAGE_LIMIT_EXCEEDED,
    );
  }

  const seenIds = new Set<number>();
  for (const item of notifications) {
    validateScheduleOptions(item);
    if (item.id !== undefined && item.id !== null) {
      if (seenIds.has(item.id)) {
        throw new PluginError(
          `Duplicate notification id ${item.id} in schedule list`,
          ErrorCode.INVALID_ARGUMENT,
        );
      }
      seenIds.add(item.id);
    }
  }
}

export function validateActionGroup(options: ActionGroup): void {
  if (!options || typeof options !== 'object') {
    throw new PluginError(
      'ActionGroup must be an object',
      ErrorCode.INVALID_ARGUMENT,
    );
  }

  if (typeof options.id !== 'string' || options.id.trim().length === 0) {
    throw new PluginError(
      'ActionGroup id is required',
      ErrorCode.INVALID_ARGUMENT,
    );
  }

  if (!Array.isArray(options.actions)) {
    throw new PluginError(
      'actions must be an array',
      ErrorCode.INVALID_ARGUMENT,
    );
  }

  if (options.actions.length > SWBNOTIF_LIMITS.MAX_ACTIONS_PER_GROUP) {
    throw new PluginError(
      `An action group cannot have more than ${SWBNOTIF_LIMITS.MAX_ACTIONS_PER_GROUP} actions`,
      ErrorCode.INVALID_ARGUMENT,
    );
  }

  const seenActionIds = new Set<string>();
  for (const action of options.actions) {
    if (
      !action ||
      typeof action.id !== 'string' ||
      action.id.trim().length === 0
    ) {
      throw new PluginError(
        'Each action must have a non-empty id',
        ErrorCode.INVALID_ARGUMENT,
      );
    }
    if (typeof action.title !== 'string' || action.title.trim().length === 0) {
      throw new PluginError(
        'Each action must have a non-empty title',
        ErrorCode.INVALID_ARGUMENT,
      );
    }
    if (seenActionIds.has(action.id)) {
      throw new PluginError(
        `Duplicate action id ${action.id} in group`,
        ErrorCode.INVALID_ARGUMENT,
      );
    }
    seenActionIds.add(action.id);
  }
}

export function validateChannel(options: NotificationChannel): void {
  if (!options || typeof options !== 'object') {
    throw new PluginError(
      'Channel options must be an object',
      ErrorCode.INVALID_ARGUMENT,
    );
  }

  if (typeof options.id !== 'string' || options.id.trim().length === 0) {
    throw new PluginError('Channel id is required', ErrorCode.INVALID_ARGUMENT);
  }

  if (typeof options.name !== 'string' || options.name.trim().length === 0) {
    throw new PluginError(
      'Channel name is required',
      ErrorCode.INVALID_ARGUMENT,
    );
  }
}
