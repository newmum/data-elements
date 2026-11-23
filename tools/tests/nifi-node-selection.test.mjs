import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';

const source = fs.readFileSync(new URL('../../lowcode/03.数据接入/2.3.1 数据汇聚(data-convergence)/接入申请(access-apply).vue', import.meta.url), 'utf8');
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const babel = {};
vm.runInNewContext(fs.readFileSync(new URL('../../data-elements-chengtian/public/babel.min.js', import.meta.url), 'utf8'), babel);
const ast = babel.Babel.transform(script, { ast: true, code: false, parserOpts: { sourceType: 'module', plugins: ['typescript'] } }).ast;
const names = ['getNifiNodesByNetwork', 'getDefaultNifiNodeId', 'resolveNifiNodeId', 'nodeConfigurationProblem',
  'checkNifiNodeSelection', 'syncNifiNodeSelection', 'normalizeFormPayload', 'loadManagedNifiNodeOptions'];
const selected = names.map(name => {
  const node = ast.program.body.find(item => item.type === 'VariableDeclaration' && item.declarations.some(value => value.id?.name === name));
  assert.ok(node, name);
  return script.slice(node.start, node.end);
});
const executable = babel.Babel.transform(selected.join('\n'), { filename: 'node-selection.ts', presets: ['typescript'] }).code;

function fixture(count = 3) {
  const form = {};
  const options = { value: [] };
  const context = vm.createContext({ ref: value => ({ value }), nifiNodeOptions: options, nifiNetworkOptions: { value: [] },
    jsonFormRef: { value: { setValue: value => Object.assign(form, value) } },
    datasourceMap: { value: {} }, orderTargetDatasourceIds: value => value || [], nodeEnabled: () => true,
    nodeDefault: value => Boolean(value.isDefault), readList: value => value.list,
  });
  vm.runInContext(executable + '\nglobalThis.actual={' + names.join(',') + '};', context);
  const rows = Array.from({ length: count }, (_, index) => ({ nodeId: 'node-' + index, nodeName: '节点' + index,
    networkCode: 'GAW', label: '节点' + index, isDefault: index === 0, configurationValid: index !== 2,
    configurationMessage: index === 2 ? '数据接入节点「节点2」（https://node2.example）：缺少NiFi密码' : '',
    authenticationMode: index === 1 ? 'ANONYMOUS_HTTP' : 'USERNAME_PASSWORD' }));
  context.actual.loadManagedNifiNodeOptions({ networks: [{ value: 'GAW', label: '公安网' }], list: rows });
  return { ui: context.actual, form, options };
}

test('the chosen anonymous node is preserved independently of the credentialed default', () => {
  const { ui, form } = fixture();
  ui.syncNifiNodeSelection(['GAW', 'node-1']);
  assert.equal(form.nifiNodeId, 'node-1');
  assert.equal(ui.nodeConfigurationProblem.value, '');
  const payload = ui.normalizeFormPayload({ nifiNodePath: ['GAW', 'node-1'], nifiNodeId: 'node-0' });
  assert.equal(payload.nifiNodeId, 'node-1');
});

test('invalid, removed or cleared selections never silently become the default node', () => {
  const { ui, form } = fixture();
  assert.equal(ui.resolveNifiNodeId('removed', 'GAW'), 'removed');
  ui.syncNifiNodeSelection(['GAW', 'node-2']);
  assert.equal(form.nifiNodeId, 'node-2');
  assert.match(ui.nodeConfigurationProblem.value, /节点2.*缺少NiFi密码/);
  ui.syncNifiNodeSelection(['GAW', 'removed']);
  assert.equal(form.nifiNodeId, 'removed');
  assert.match(ui.nodeConfigurationProblem.value, /removed.*重新选择/);
  ui.syncNifiNodeSelection([]);
  assert.equal(form.nifiNodeId, '');
  assert.match(ui.nodeConfigurationProblem.value, /请选择/);
  assert.equal(ui.normalizeFormPayload({}).nifiNodeId, '');
});

test('20/100 option rows are processed locally with the same selection and readiness rules', () => {
  for (const count of [20, 100]) {
    const { ui, options } = fixture(count);
    assert.equal(options.value.length, count);
    ui.syncNifiNodeSelection(['GAW', 'node-1']);
    assert.equal(ui.nodeConfigurationProblem.value, '');
    assert.match(ui.checkNifiNodeSelection('node-1', 'OTHER'), /不属于/);
  }
  assert.match(source, /if \(nodeConfigurationProblem\.value\) throw new Error\(nodeConfigurationProblem\.value\)/);
});
