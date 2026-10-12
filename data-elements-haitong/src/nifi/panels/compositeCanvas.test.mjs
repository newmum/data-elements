import test from 'node:test';
import assert from 'node:assert/strict';
import { buildCompositeCanvas, taskChainPaths } from '../utils/compositeCanvas.ts';
import { transportFailureMessage, globalRequestFailureMessage } from '../utils/requestFailure.ts';
import { AxiosError } from 'axios';
import { selectTaskDesign, selectTaskNode } from '../utils/taskSelection.ts';

const dsl = () => ({ version: 1, nodes: [
  { id: 'source', category: 'source', label: '来源', manifestKey: 'source.mysql', x: 0, y: 120, config: { table: 'event' } },
  { id: 'target', category: 'sink', label: '目标', manifestKey: 'sink.hive', x: 500, y: 120, config: { table: 'ods_event' } },
], edges: [{ id: 'edge', source: 'source', target: 'target' }] });
const chain = count => ({ items: Array.from({ length: count }, (_, i) => ({ taskId: `task${i}`, taskName: `第${i}跳`, pipeline: { id: `p${i}`, dsl: dsl() } })),
  links: Array.from({ length: count - 1 }, (_, i) => ({ fromTaskId: `task${i}`, toTaskId: `task${i+1}`, tableId: `table${i}` })) });

test('two task designs are visible together but only selected design retains executable node IDs', () => {
  const input = dsl(), context = chain(2), before = structuredClone(context);
  const result = buildCompositeCanvas(context, 'p0', input);
  assert.equal(Object.keys(result.nodes).length, 4);
  assert.equal(result.nodes.source.readonly, false);
  assert.equal(result.nodes['chain:p1:source'].readonly, true);
  assert.equal(result.edges['chain-link:task0:task1'].relationship, true);
  assert.deepEqual(context, before);
  assert.deepEqual(input, dsl());
  assert.deepEqual(Object.keys(result.edges).filter(key => !result.edges[key].readonly), ['edge']);
});

test('moving selected display nodes maps back to the original coordinates and contextual drafts are preserved', () => {
  const context = chain(2), draft = dsl(); draft.nodes[0].config.table = 'edited';
  const result = buildCompositeCanvas(context, 'p1', draft, { p0: draft });
  const current = result.nodes.source;
  assert.equal(current.node.x - current.offsetX, draft.nodes[0].x);
  assert.equal(current.node.y - current.offsetY, draft.nodes[0].y);
  assert.equal(result.nodes['chain:p0:source'].node.config.table, 'edited');
});

test('historical one-pipeline multi-target remains a single independent editable graph', () => {
  const input = dsl(); input.nodes.push({ ...input.nodes[1], id: 'target2' }); input.edges.push({ id: 'edge2', source: 'source', target: 'target2' });
  const context = chain(1); const result = buildCompositeCanvas(context, 'p0', input);
  assert.equal(Object.keys(result.nodes).length, 3);
  assert.equal(Object.values(result.nodes).some(node => node.readonly), false);
  assert.equal(Object.values(result.edges).some(edge => edge.relationship), false);
});

test('an authorized downstream branch attaches to the matching materialized sink, never a different target', () => {
  const context = chain(3);
  const root = context.items[0].pipeline.dsl;
  root.nodes[1].config.targetTableId = 'a';
  root.nodes.push({ ...root.nodes[1], id: 'target2', y: 440, config: { table: 'ods_second', targetTableId: 'b' } });
  root.edges.push({ id: 'edge2', source: 'source', target: 'target2' });
  context.items[1].pipeline.dsl.nodes[0].config.sourceTableId = 'a';
  context.items[2].pipeline.dsl.nodes[0].config.sourceTableId = 'b';
  context.links = [{ fromTaskId: 'task0', toTaskId: 'task1', tableId: 'a' }, { fromTaskId: 'task0', toTaskId: 'task2', tableId: 'b' }];
  const result = buildCompositeCanvas(context, 'p0', root);
  assert.equal(result.edges['chain-link:task0:task1'].source, 'target');
  assert.equal(result.edges['chain-link:task0:task2'].source, 'target2');
  assert.equal(Object.values(result.nodes).filter(item => !item.readonly).length, 3);
});

test('explicitly mismatched endpoint IDs do not acquire a context connector from their names', () => {
  for (const endpoint of ['source', 'target']) {
    const context = chain(2);
    if (endpoint === 'target') context.items[0].pipeline.dsl.nodes[1].config.targetTableId = 'other-table';
    else context.items[1].pipeline.dsl.nodes[0].config.sourceTableId = 'other-table';
    const result = buildCompositeCanvas(context, 'p0', context.items[0].pipeline.dsl);
    assert.equal(Object.values(result.edges).some(edge => edge.relationship), false);
  }
});

test('dense branch paths have a strict 100-path and 16-hop rendering limit', () => {
  const context = chain(40);
  context.links = [];
  for (let layer = 0; layer < 19; layer++) for (let from = 0; from < 2; from++) for (let to = 0; to < 2; to++) {
    context.links.push({ fromTaskId: `task${layer*2+from}`, toTaskId: `task${(layer+1)*2+to}`, tableId: 'shared' });
  }
  const paths = taskChainPaths(context);
  assert.equal(paths.length, 100);
  assert.equal(Math.max(...paths.map(path => path.length)), 16);
});

test('explicit task switching retains independent local drafts and serializes only the selected job', () => {
  const context = { chain: chain(2), operationBusy: false, drafts: {}, rememberDraft(id, value) { this.drafts[id] = value; } };
  let active = dsl(); active.nodes[0].config.table = 'unsaved_business';
  const canvas = { currentPipelineId: 'p0', toDsl: () => active, loadDsl(value) { active = value; },
    setCurrentPipeline(id) { this.currentPipelineId = id; }, setCanvasMode() {}, setSyncCompletion() {} };
  assert.equal(selectTaskDesign('p1', canvas, context), true);
  active = structuredClone(active); active.nodes[0].config.table = 'unsaved_warehouse';
  assert.equal(selectTaskDesign('p0', canvas, context), true);
  assert.equal(canvas.toDsl().nodes[0].config.table, 'unsaved_business');
  assert.equal(context.drafts.p1.nodes[0].config.table, 'unsaved_warehouse');
  const display = buildCompositeCanvas(context.chain, canvas.currentPipelineId, canvas.toDsl(), context.drafts);
  assert.equal(Object.keys(display.nodes).length, 4);
  assert.equal(canvas.toDsl().nodes.length, 2);
  assert.equal(canvas.toDsl().edges.length, 1);
  assert.equal(canvas.toDsl().edges.some(edge => edge.id.startsWith('chain-link:')), false);
});

test('deploy/start preflight, mutation in progress and unrecognized pipeline IDs cannot switch active task', () => {
  let loads = 0;
  const context = { chain: chain(2), operationBusy: true, drafts: {}, rememberDraft() { throw new Error('should not record'); } };
  const canvas = { currentPipelineId: 'p0', toDsl: dsl, loadDsl() { loads++; }, setCurrentPipeline() {}, setCanvasMode() {}, setSyncCompletion() {} };
  assert.equal(selectTaskDesign('p1', canvas, context), false);
  context.operationBusy = false;
  assert.equal(selectTaskDesign('p1', canvas, context, true), false);
  assert.equal(selectTaskDesign('foreign-pipeline', canvas, context), false);
  assert.equal(loads, 0);
  assert.equal(canvas.currentPipelineId, 'p0');
});

function nodeSelectionFixture(count = 2, currentPipelineId = 'p0') {
  const context = { chain: chain(count), operationBusy: false, drafts: {}, rememberDraft(id, value) { this.drafts[id] = value; } };
  let active = dsl(), loads = 0;
  const canvas = { currentPipelineId, selectedNodeId: null, toDsl: () => active,
    loadDsl(value) { loads++; active = structuredClone(value); },
    setCurrentPipeline(id) { this.currentPipelineId = id; }, setCanvasMode() {}, setSyncCompletion() {},
    selectNode(id) { this.selectedNodeId = id; } };
  return { context, canvas, loads: () => loads };
}

test('clicking either task lane switches ownership and resolves shared node IDs without losing drafts', () => {
  const { context, canvas } = nodeSelectionFixture();
  canvas.toDsl().nodes[0].config.table = 'edited_business';
  let display = buildCompositeCanvas(context.chain, canvas.currentPipelineId, canvas.toDsl(), context.drafts);
  const beforePosition = display.nodes['chain:p1:target'].node.y;
  assert.equal(selectTaskNode(display.nodes['chain:p1:target'], canvas, context), true);
  assert.equal(canvas.currentPipelineId, 'p1');
  assert.equal(canvas.selectedNodeId, 'target');
  canvas.toDsl().nodes[0].config.table = 'edited_warehouse';
  display = buildCompositeCanvas(context.chain, canvas.currentPipelineId, canvas.toDsl(), context.drafts);
  assert.equal(display.nodes.target.node.y, beforePosition);
  assert.equal(selectTaskNode(display.nodes['chain:p0:source'], canvas, context), true);
  assert.equal(canvas.currentPipelineId, 'p0');
  assert.equal(canvas.selectedNodeId, 'source');
  assert.equal(canvas.toDsl().nodes[0].config.table, 'edited_business');
  assert.equal(context.drafts.p1.nodes[0].config.table, 'edited_warehouse');
  assert.deepEqual(canvas.toDsl().nodes.map(node => node.id), ['source', 'target']);
  assert.equal(canvas.toDsl().edges.length, 1);
});

test('node selection rejects foreign tasks, missing nodes and task switches during operations', () => {
  const { context, canvas, loads } = nodeSelectionFixture();
  assert.equal(selectTaskNode({ pipelineId: 'foreign', originalNodeId: 'source' }, canvas, context), false);
  assert.equal(selectTaskNode({ pipelineId: 'p1', originalNodeId: 'missing' }, canvas, context), false);
  context.operationBusy = true;
  assert.equal(selectTaskNode({ pipelineId: 'p1', originalNodeId: 'source' }, canvas, context), false);
  context.operationBusy = false;
  assert.equal(selectTaskNode({ pipelineId: 'p1', originalNodeId: 'source' }, canvas, context, true), false);
  assert.equal(canvas.currentPipelineId, 'p0');
  assert.equal(canvas.selectedNodeId, null);
  assert.equal(loads(), 0);
});

test('current and unsaved standalone nodes select without reloading their design', () => {
  for (const id of ['p0', null]) {
    const { context, canvas, loads } = nodeSelectionFixture(1, id);
    const display = buildCompositeCanvas(context.chain, id, canvas.toDsl());
    assert.equal(selectTaskNode(display.nodes.source, canvas, context), true);
    assert.equal(canvas.selectedNodeId, 'source');
    assert.equal(canvas.currentPipelineId, id);
    assert.equal(loads(), 0);
  }
});

test('20/100 displayed task selections use only the authorized aggregate and independent local designs', () => {
  for (const count of [20, 100]) {
    const { context, canvas, loads } = nodeSelectionFixture(count);
    for (let i = 0; i < count; i++) {
      assert.equal(selectTaskNode({ pipelineId: `p${i}`, originalNodeId: 'source' }, canvas, context), true);
      assert.equal(canvas.currentPipelineId, `p${i}`);
      assert.equal(canvas.toDsl().nodes.length, 2);
    }
    assert.equal(loads(), count - 1);
    assert.equal(selectTaskNode({ pipelineId: `p${count}`, originalNodeId: 'source' }, canvas, context), false);
  }
});

test('20 and 100 task display assembly requires no requests and preserves server-authorized relationships', () => {
  for (const count of [20, 100]) {
    const result = buildCompositeCanvas(chain(count), 'p0', dsl());
    assert.equal(Object.keys(result.nodes).length, count * 2);
    assert.equal(Object.values(result.edges).filter(edge => edge.relationship).length, count - 1);
  }
  const result = buildCompositeCanvas({ ...chain(2), links: [] }, 'p0', dsl());
  assert.equal(Object.values(result.edges).some(edge => edge.relationship), false);
});

test('cancellation, token handshake and session transitions never produce a backend-network warning', () => {
  for (const error of [new Error('尚未收到登录信息'), new Error('登录会话已切换'),
    { isAxiosError: true, code: 'ERR_CANCELED', request: {} }, { isAxiosError: true, code: 'ERR_NETWORK' }]) {
    assert.equal(transportFailureMessage(error), undefined);
  }
  assert.match(transportFailureMessage({ isAxiosError: true, request: {}, code: 'ERR_NETWORK' }), /网络连接异常/);
  assert.match(transportFailureMessage({ isAxiosError: true, request: {}, code: 'ECONNABORTED' }), /超时/);
});

test('Axios HTTP/network/timeout polling errors stay rejected but suppress automatic global warnings', () => {
  const polling = { url: '/pipelines/p0/status', nifiSilentGlobalErrors: true };
  for (const error of [new AxiosError('Network Error', 'ERR_NETWORK', polling, {}),
    new AxiosError('timeout', 'ECONNABORTED', polling, {}),
    new AxiosError('503', 'ERR_BAD_RESPONSE', polling, {}, { status: 503 })]) {
    assert.equal(globalRequestFailureMessage(error), undefined);
    assert.ok(error instanceof Error);
  }
  assert.match(globalRequestFailureMessage(new AxiosError('network', 'ERR_NETWORK', { url: '/pipelines/task-chain' }, {})), /网络连接异常/);
  assert.match(globalRequestFailureMessage(new AxiosError('503', 'ERR_BAD_RESPONSE', { url: '/pipelines/p0/start' }, {}, { status: 503 })), /服务器异常/);
});
