import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const source = await fs.readFile(new URL('lowcode/05.数据治理/2.5.1 数据中心(data-warehouse).vue', root), 'utf8');
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const ast = babel.Babel.transform(script, {
  ast: true, code: false, configFile: false, babelrc: false,
  parserOpts: { sourceType: 'module', plugins: ['typescript'] },
}).ast;
const names = ['layers', 'layersLoading', 'layersError', 'loadLayers'];
const selected = names.map(name => {
  const declaration = ast.program.body.find(node => node.type === 'VariableDeclaration'
    && node.declarations.some(item => item.id?.name === name));
  assert.ok(declaration, `Missing production declaration ${name}`);
  return script.slice(declaration.start, declaration.end);
});
const executable = babel.Babel.transform(selected.join('\n'), {
  filename: 'resource-center.ts', presets: ['typescript'], configFile: false, babelrc: false,
}).code + '\nglobalThis.actual = {' + names.join(',') + '};';

function fixture() {
  let resolve, reject;
  const requests = [];
  const context = vm.createContext({
    ref: value => ({ value }),
    $common: { get: path => {
      requests.push(path);
      return new Promise((success, failure) => { resolve = success; reject = failure; });
    } },
  });
  vm.runInContext(executable, context);
  return { actual: context.actual, requests, resolve: value => resolve(value), reject: error => reject(error) };
}
for (const size of [20, 100]) {
  const f = fixture();
  const request = f.actual.loadLayers();
  assert.equal(f.actual.layersLoading.value, true);
  await f.actual.loadLayers();
  assert.deepEqual(f.requests, ['/dwm/center/layer-list'], 'Concurrent loads must reuse the in-flight work');
  const domains = Array.from({ length: size }, (_, index) => ({ id: `domain-${index}`, tableCount: 1 }));
  f.resolve([{ name: 'DWD', domains: [{ name: '标准库', items: domains }] }]);
  await request;
  assert.equal(f.actual.layersLoading.value, false);
  assert.equal(f.actual.layersError.value, '');
  assert.equal(f.actual.layers.value[0].domains[0].items.length, size);
  assert.equal(f.requests.length, 1, `${size} items must require exactly one overview request`);
}
const failure = fixture();
let pending = failure.actual.loadLayers();
failure.reject(new Error('request timed out'));
await pending;
assert.equal(failure.actual.layersLoading.value, false);
assert.match(failure.actual.layersError.value, /加载失败/);
assert.equal(failure.actual.layers.value.length, 0);
pending = failure.actual.loadLayers();
assert.equal(failure.actual.layersError.value, '');
failure.resolve([{ name: 'DST', domains: [] }]);
await pending;
assert.equal(failure.actual.layers.value[0].name, 'DST');
assert.equal(failure.requests.length, 2, 'Retry is one explicit request');
pending = failure.actual.loadLayers();
failure.resolve({ unexpected: true });
await pending;
assert.match(failure.actual.layersError.value, /加载失败/);
assert.equal(failure.actual.layers.value[0].name, 'DST', 'Failed refresh preserves the last successful overview');
assert.ok(source.includes('!layersLoading && !layersError && !layers.length'), 'Only a successful empty response may display the empty state');
console.log('Resource center: 20/100 items each use one request; duplicate loads, failure, retry and invalid responses verified.');
