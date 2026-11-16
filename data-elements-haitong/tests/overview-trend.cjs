const assert = require('node:assert/strict');
const path = require('node:path');
const test = require('node:test');

const { PlatformApiError } = require(path.join(process.env.HAITONG_BUILD, 'api', 'platformError.js'));
const { loadOverviewTrend } = require(path.join(process.env.HAITONG_BUILD, 'pages', 'overview', 'overviewTrend.js'));

const now = new Date('2026-11-16T02:30:00Z');
const limit = () => new PlatformApiError('/ods/monitor/trend', '时间范围内快照超过50000条，请缩短查询时间范围', 413);

test('overview starts with six hours and narrows an overflowing window without changing its end time', async () => {
  const queries = [];
  const signal = new AbortController().signal;
  const result = await loadOverviewTrend(async (begin, end, actualSignal) => {
    assert.equal(actualSignal, signal);
    queries.push([begin, end]);
    if (queries.length === 1) throw limit();
    return { points: [{ sampleCount: 8 }] };
  }, signal, now);
  assert.deepEqual(queries, [['2026-11-16 04:30:00', '2026-11-16 10:30:00'], ['2026-11-16 09:30:00', '2026-11-16 10:30:00']]);
  assert.deepEqual(result, { minutes: 60, snapshot: { points: [{ sampleCount: 8 }] } });
});

test('repeated overflow is bounded; a permission failure or cancellation does not retry', async () => {
  let calls = 0;
  const controller = new AbortController();
  assert.deepEqual(await loadOverviewTrend(async () => { calls++; throw limit(); }, controller.signal, now), { minutes: 15, snapshot: null });
  assert.equal(calls, 3);

  const forbidden = new PlatformApiError('/ods/monitor/trend', '没有权限', 403);
  await assert.rejects(loadOverviewTrend(async () => { calls++; throw forbidden; }, controller.signal, now), forbidden);
  assert.equal(calls, 4);

  await assert.rejects(loadOverviewTrend(async () => { calls++; controller.abort(); throw limit(); }, controller.signal, now), { status: 413 });
  assert.equal(calls, 5);
});
