import test from 'node:test';
import assert from 'node:assert/strict';
import { requireListResponse, normalizeProbeResponse, unwrapSuccessfulEnvelope } from '../api/response.ts';

test('HTTP 200 failure envelopes produce a query error instead of reaching array consumers', () => {
  assert.throws(() => requireListResponse({ code: 500, success: false, data: null, msg: '账号校验暂不可用' }, '流程错误列表'), /账号校验暂不可用.*500/);
});

test('list queries accept direct and wrapped empty lists but reject non-list success data', () => {
  assert.deepEqual(requireListResponse([], '流程错误列表'), []);
  assert.deepEqual(requireListResponse({ code: 0, data: { success: true, data: [{ id: 'e1' }] } }, '流程错误列表'), [{ id: 'e1' }]);
  assert.throws(() => requireListResponse({ code: 0, data: {} }, '流程错误列表'), /无效列表/);
});

test('metadata probes retain controlled failure details and a safe empty result list', () => {
  const result = normalizeProbeResponse({ code: 500, success: false, data: null, msg: '无法连接数据源' }, '表探查', 'tables');
  assert.equal(result.success, false);
  assert.deepEqual(result.tables, []);
  assert.match(result.error, /无法连接数据源/);
});

test('mutations cannot report success for a platform failure, while controlled probes remain readable', () => {
  assert.throws(() => unwrapSuccessfulEnvelope({ code: 0, data: { code: 500, success: false, msg: '启动失败' } }), /启动失败/);
  const probe = { success: false, error: '目标不可达', tables: [] };
  assert.deepEqual(unwrapSuccessfulEnvelope({ code: 0, data: probe }), probe);
  assert.deepEqual(unwrapSuccessfulEnvelope({ code: 0, data: { state: 'RUNNING' } }), { state: 'RUNNING' });
});
