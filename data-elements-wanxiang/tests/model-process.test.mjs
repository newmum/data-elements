import assert from 'node:assert/strict';
import test from 'node:test';
import { standardizationModel } from '../src/panshi/pages/modelSelection.ts';
import { modelLogRows } from '../src/panshi/pages/modelLogView.ts';

test('archived model in an old URL is replaced by an active selection', () => {
  const models = [
    { id: 'archived', state: 'ARCHIVED' },
    { id: 'active', state: 'ACTIVE' },
  ];
  assert.equal(standardizationModel(models, 'archived')?.id, 'active');
  assert.equal(standardizationModel(models, 'active')?.id, 'active');
  assert.equal(standardizationModel([models[0]], 'archived'), undefined);
});

test('model log history keeps archived models and links plan and item-set events', () => {
  const state = {
    models: [{ id: 'm1', name: '人员模型', code: 'PERSON', state: 'ARCHIVED' }],
    materializations: [{ id: 'p1', modelId: 'm1' }],
    itemSets: [{ id: 's1', modelId: 'm1' }],
    logs: [
      { id: 'l1', time: '2026-10-03T09:00:00', action: '创建逻辑模型', objectId: 'm1', name: '人员模型' },
      { id: 'l2', time: '2026-10-03T09:01:00', action: '提交标准候选', objectId: 's1', name: '数据项集' },
      { id: 'l3', time: '2026-10-03T09:02:00', action: '物化执行完成', objectId: 'p1', name: '物化方案' },
      { id: 'l4', time: '2026-10-03T09:03:00', action: '登记仓库资源', objectId: 'r1', name: '仓库表' },
    ],
  };
  const rows = modelLogRows(state);
  assert.deepEqual(rows.map(row => row.id), ['l3', 'l2', 'l1']);
  assert.deepEqual(rows.map(row => row.stage), ['物化', '标准化', '设计']);
  assert.ok(rows.every(row => row.modelId === 'm1' && row.modelArchived && row.modelName === '人员模型'));
});
