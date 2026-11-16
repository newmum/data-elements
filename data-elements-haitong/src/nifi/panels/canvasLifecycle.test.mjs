import assert from 'node:assert/strict';
import test from 'node:test';
import { canvasLifecycle, runtimeReflectsStopReceipt } from '../runtime/canvasLifecycle.ts';

test('a deployed and stopped group with invalid processors leaves canvas monitoring mode', () => {
  const invalid = { status: 'RUN_ERROR', deployed: true, runtimeState: 'INVALID', runtimeFresh: true };
  assert.deepEqual(canvasLifecycle(invalid), { status: 'STOPPED', stoppedWithInvalidProcessors: true });
  assert.equal(canvasLifecycle({ ...invalid, runtimeState: 'RUN_ERROR' }).status, 'RUN_ERROR');
  assert.equal(canvasLifecycle({ ...invalid, runtimeFresh: false }).status, 'RUN_ERROR');
  assert.equal(canvasLifecycle({ ...invalid, deployed: false, status: 'DEPLOY_FAILED' }).status, 'DEPLOY_FAILED');
});

test('a cached running or error snapshot cannot override a successful stop receipt', () => {
  assert.equal(runtimeReflectsStopReceipt({status:'RUNNING',runtimeFresh:true,runtimeObservedAt:1_000}, 1_100), false);
  assert.equal(runtimeReflectsStopReceipt({status:'RUN_ERROR',runtimeFresh:true,runtimeObservedAt:1_000}, 1_100), false);
  assert.equal(runtimeReflectsStopReceipt({status:'RUNNING',runtimeFresh:false,runtimeObservedAt:1_200}, 1_100), false);
  assert.equal(runtimeReflectsStopReceipt({status:'STOPPED',runtimeFresh:true,runtimeObservedAt:1_200}, 1_100), true);
  assert.equal(runtimeReflectsStopReceipt({status:'RUNNING',runtimeFresh:true,runtimeObservedAt:1_500}, 1_100), true);
});
