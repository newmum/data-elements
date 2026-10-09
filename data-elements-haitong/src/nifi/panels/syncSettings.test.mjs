import test from 'node:test';
import assert from 'node:assert/strict';
import { syncSettings, syncSettingsPatch, syncSettingsError, needsCleanupConfirmation, sameSavedContent } from '../utils/syncSettings.ts';

test('legacy mode inference matches backend and does not treat string false as cleanup', () => {
  assert.equal(syncSettings({}).syncMode, 'FULL');
  assert.equal(syncSettings({ incrementalColumn: ' id ' }).syncMode, 'INCREMENTAL');
  assert.equal(syncSettings({ syncMode: 'INCR' }).syncMode, 'INCREMENTAL');
  assert.equal(syncSettings({ syncMode: 'FULL', deleteTargetData: 'false' }).deleteTargetData, false);
  assert.equal(syncSettings({ syncMode: 'INCREMENTAL', deleteTargetData: true }).deleteTargetData, false);
});
test('full then incremental resets the initial strategy without mutating unrelated config', () => {
  const config = { incrementalColumn: 'time', table: 'events', initialStrategy: 'START_AT_CURRENT_MAX' };
  const patch = syncSettingsPatch(config, { syncMode: 'FULL_THEN_INCR', fullSyncStrategy: 'UPSERT', deleteTargetData: true });
  assert.equal(patch.initialStrategy, 'START_AT_BEGINNING');
  assert.equal(config.initialStrategy, 'START_AT_CURRENT_MAX');
  assert.equal(patch.table, undefined);
});
test('every destructive mode requires confirmation and invalid/missing settings are rejected', () => {
  assert.ok(needsCleanupConfirmation(syncSettings({ syncMode: 'PERIODIC_FULL', fullSyncStrategy: 'TRUNCATE_RELOAD' })));
  assert.ok(needsCleanupConfirmation(syncSettings({ syncMode: 'FULL', deleteTargetData: true })));
  assert.equal(needsCleanupConfirmation(syncSettings({ syncMode: 'PERIODIC_FULL' })), false);
  assert.ok(syncSettingsError({}, syncSettings({ syncMode: 'INCREMENTAL' })));
  assert.ok(syncSettingsError({}, syncSettings({ syncMode: 'typo' })));
  assert.equal(syncSettingsError({ incrementalColumn: 'time' }, syncSettings({ syncMode: 'FULL_THEN_INCR' })), undefined);
});
test('object key order does not create unsaved changes but actual edits do', () => {
  assert.ok(sameSavedContent({ a: 1, config: { id: 'x', mode: 'FULL' } }, { config: { mode: 'FULL', id: 'x' }, a: 1 }));
  assert.equal(sameSavedContent({ mode: 'FULL' }, { mode: 'INCREMENTAL' }), false);
});
