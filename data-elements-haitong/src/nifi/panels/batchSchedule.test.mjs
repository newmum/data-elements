import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { DAILY_IMMEDIATE, automaticSchedule, dailyScheduleDefaults, staggeredScheduleDefaults, isBatchSource, scheduleFieldVisible } from '../utils/batchSchedule.ts';

test('new batch nodes default to immediate execution with a one-day timer and Kafka is excluded', () => {
  for (const key of ['source.mysql', 'source.ftp', 'source.sftp', 'source.minio', 'source.api']) assert.equal(isBatchSource(key), true);
  assert.equal(isBatchSource('source.kafka'), false);
  assert.equal(isBatchSource('sink.jdbc'), false);
  const original = { table: 'validation', schedulingPeriod: '60 sec' };
  const config = dailyScheduleDefaults(original);
  assert.equal(config.batchSchedulePolicy, DAILY_IMMEDIATE);
  assert.equal(config.schedulingStrategy, 'TIMER_DRIVEN');
  assert.equal(config.schedulingPeriod, '1 day');
  assert.equal(original.schedulingPeriod, '60 sec');
  assert.equal(automaticSchedule(config), false);
  assert.equal(scheduleFieldVisible('schedulingPeriod', config), false);
  assert.equal(scheduleFieldVisible('schedulingStrategy', config), false);
  assert.equal(scheduleFieldVisible('schedulingPeriod', original), true);
});

test('staggering remains available and switching to immediate clears the saved clock', () => {
  const staggered = staggeredScheduleDefaults({ table: 'validation' });
  assert.equal(automaticSchedule(staggered), true);
  assert.equal(staggered.schedulingStrategy, 'CRON_DRIVEN');
  assert.equal(staggered.schedulingPeriod, '');
  const immediate = dailyScheduleDefaults({ ...staggered, batchScheduleTime: '01:58:39' });
  assert.equal(immediate.batchScheduleTime, undefined);
  assert.equal(immediate.batchScheduleZone, undefined);
  assert.equal(immediate.schedulingPeriod, '1 day');
  assert.equal(scheduleFieldVisible('schedulingPeriod', staggered), false);
});

test('defaults are creation-only, the saved server DSL is accepted and drawer does not insert a midnight cron', () => {
  const store = readFileSync(new URL('../stores/canvasStore.ts', import.meta.url), 'utf8');
  assert.match(store, /isBatchSource\(m.key\) \? dailyScheduleDefaults\(out\)/);
  const drawer = readFileSync(new URL('./ConfigDrawer.tsx', import.meta.url), 'utf8');
  assert.match(drawer, /if \(!scheduleFieldVisible\(field.key, node.config\)\) return;/);
});
