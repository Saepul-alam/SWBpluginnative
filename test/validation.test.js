const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');

// Load built CJS bundle
const {
  ErrorCode,
  SWBNOTIF_LIMITS,
  PluginError,
  validateScheduleOptions,
  validateScheduleMultiple,
  validateActionGroup,
  validateChannel,
} = require('../dist/plugin.cjs.js');

test('SWBNOTIF_LIMITS constants are correct', () => {
  assert.equal(SWBNOTIF_LIMITS.MAX_TITLE_LENGTH, 256);
  assert.equal(SWBNOTIF_LIMITS.MAX_BODY_LENGTH, 4096);
  assert.equal(SWBNOTIF_LIMITS.MAX_EXTRA_BYTES, 32768);
  assert.equal(SWBNOTIF_LIMITS.MAX_SCHEDULED, 100);
  assert.equal(SWBNOTIF_LIMITS.MAX_DELIVERED, 100);
  assert.equal(SWBNOTIF_LIMITS.MAX_ACTIONS_PER_GROUP, 4);
});

test('validateScheduleOptions passes on valid options', () => {
  assert.doesNotThrow(() => {
    validateScheduleOptions({
      title: 'Reminder',
      body: 'Time to drink water',
      extra: { taskId: 42, flag: true },
    });
  });
});

test('validateScheduleOptions throws when title is missing or empty', () => {
  assert.throws(
    () => validateScheduleOptions({}),
    err =>
      err instanceof PluginError && err.code === ErrorCode.INVALID_ARGUMENT,
  );
  assert.throws(
    () => validateScheduleOptions({ title: '   ' }),
    err =>
      err instanceof PluginError && err.code === ErrorCode.INVALID_ARGUMENT,
  );
});

test('validateScheduleOptions throws when title exceeds limit', () => {
  const longTitle = 'A'.repeat(SWBNOTIF_LIMITS.MAX_TITLE_LENGTH + 1);
  assert.throws(
    () => validateScheduleOptions({ title: longTitle }),
    err =>
      err instanceof PluginError && err.code === ErrorCode.INVALID_ARGUMENT,
  );
});

test('validateScheduleOptions throws when body exceeds limit', () => {
  const longBody = 'B'.repeat(SWBNOTIF_LIMITS.MAX_BODY_LENGTH + 1);
  assert.throws(
    () => validateScheduleOptions({ title: 'Valid', body: longBody }),
    err =>
      err instanceof PluginError && err.code === ErrorCode.INVALID_ARGUMENT,
  );
});

test('validateScheduleOptions throws when extra exceeds 32KB payload limit', () => {
  const hugePayload = {
    data: 'x'.repeat(SWBNOTIF_LIMITS.MAX_EXTRA_BYTES + 10),
  };
  assert.throws(
    () => validateScheduleOptions({ title: 'Valid', extra: hugePayload }),
    err =>
      err instanceof PluginError && err.code === ErrorCode.INVALID_ARGUMENT,
  );
});

test('validateScheduleOptions throws when count is not positive', () => {
  assert.throws(
    () => validateScheduleOptions({ title: 'Valid', count: 0 }),
    err =>
      err instanceof PluginError && err.code === ErrorCode.INVALID_ARGUMENT,
  );
  assert.throws(
    () => validateScheduleOptions({ title: 'Valid', count: -5 }),
    err =>
      err instanceof PluginError && err.code === ErrorCode.INVALID_ARGUMENT,
  );
});

test('validateScheduleMultiple enforces limits and duplicate ids', () => {
  assert.throws(
    () =>
      validateScheduleMultiple([
        { id: 101, title: 'First' },
        { id: 101, title: 'Duplicate' },
      ]),
    err =>
      err instanceof PluginError && err.code === ErrorCode.INVALID_ARGUMENT,
  );

  const tooMany = [];
  for (let i = 0; i <= SWBNOTIF_LIMITS.MAX_SCHEDULED; i++) {
    tooMany.push({ title: `Item ${i}` });
  }
  assert.throws(
    () => validateScheduleMultiple(tooMany),
    err =>
      err instanceof PluginError &&
      err.code === ErrorCode.STORAGE_LIMIT_EXCEEDED,
  );
});

test('validateActionGroup enforces action limits and required fields', () => {
  assert.doesNotThrow(() => {
    validateActionGroup({
      id: 'chat_actions',
      actions: [
        { id: 'reply', title: 'Reply' },
        { id: 'archive', title: 'Archive' },
      ],
    });
  });

  // More than 4 actions
  assert.throws(
    () =>
      validateActionGroup({
        id: 'overflow',
        actions: [
          { id: '1', title: '1' },
          { id: '2', title: '2' },
          { id: '3', title: '3' },
          { id: '4', title: '4' },
          { id: '5', title: '5' },
        ],
      }),
    err =>
      err instanceof PluginError && err.code === ErrorCode.INVALID_ARGUMENT,
  );

  // Duplicate action ids
  assert.throws(
    () =>
      validateActionGroup({
        id: 'dup_actions',
        actions: [
          { id: 'ok', title: 'OK' },
          { id: 'ok', title: 'Duplicate OK' },
        ],
      }),
    err =>
      err instanceof PluginError && err.code === ErrorCode.INVALID_ARGUMENT,
  );
});

test('validateChannel checks id and name', () => {
  assert.doesNotThrow(() => {
    validateChannel({ id: 'messages', name: 'Messages' });
  });

  assert.throws(
    () => validateChannel({ id: '', name: 'Messages' }),
    err =>
      err instanceof PluginError && err.code === ErrorCode.INVALID_ARGUMENT,
  );

  assert.throws(
    () => validateChannel({ id: 'messages', name: '' }),
    err =>
      err instanceof PluginError && err.code === ErrorCode.INVALID_ARGUMENT,
  );
});
