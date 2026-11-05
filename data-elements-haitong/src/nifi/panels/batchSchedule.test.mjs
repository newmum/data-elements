import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { automaticSchedule, dailyScheduleDefaults, isBatchSource, scheduleFieldVisible } from '../utils/batchSchedule.ts';

test('new batch nodes wait for one persisted server assignment and Kafka keeps its schedule', () => {
  for (const key of ['source.mysql', 'source.ftp', 'source.sftp', 'source.minio', 'source.api']) assert.equal(isBatchSource(key), true);
  assert.equal(isBatchSource('source.kafka'), false);
  assert.equal(isBatchSource('sink.jdbc'), false);
  const original = { table: 'validation', schedulingPeriod: '60 sec' };
  const config = dailyScheduleDefaults(original);
  assert.equal(config.schedulingStrategy, 'CRON_DRIVEN');
  assert.equal(config.schedulingPeriod, '');
  assert.equal(original.schedulingPeriod, '60 sec');
  assert.equal(automaticSchedule(config), true);
  assert.equal(scheduleFieldVisible('schedulingPeriod', config), false);
  assert.equal(scheduleFieldVisible('schedulingStrategy', config), false);
  assert.equal(scheduleFieldVisible('schedulingPeriod', original), true);
});

test('defaults are creation-only, the saved server DSL is accepted and drawer does not insert a midnight cron', () => {
  const store = readFileSync(new URL('../stores/canvasStore.ts', import.meta.url), 'utf8');
  assert.match(store, /isBatchSource\(m.key\) \? dailyScheduleDefaults\(out\)/);
  const drawer = readFileSync(new URL('./ConfigDrawer.tsx', import.meta.url), 'utf8');
  assert.match(drawer, /if \(!scheduleFieldVisible\(field.key, node.config\)\) return;/);
});
