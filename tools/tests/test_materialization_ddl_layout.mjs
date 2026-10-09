import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const source = await fs.readFile(new URL('lowcode/03.数据接入/2.3.1 数据汇聚(data-convergence)/接入申请(access-apply).vue', root), 'utf8');
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const ast = babel.Babel.transform(script, {
  ast: true, code: false, configFile: false, babelrc: false,
  parserOpts: { sourceType: 'module', plugins: ['typescript'] },
}).ast;
const names = ['ddlEditorHost', 'ddlEditorHeight', 'ddlSizeObserver', 'syncDdlEditorHeight', 'stopDdlEditorSizing'];
const declarations = names.map(name => {
  const node = ast.program.body.find(item => item.type === 'VariableDeclaration'
    && item.declarations.some(declaration => declaration.id?.name === name));
  assert.ok(node, name);
  return script.slice(node.start, node.end);
});
const watcher = ast.program.body.find(node => node.type === 'ExpressionStatement'
  && node.expression.callee?.name === 'watch'
  && node.expression.arguments[0]?.type === 'ArrayExpression'
  && node.expression.arguments[0].elements.some(item => item?.name === 'ddlMode'));
assert.ok(watcher, 'The visible DDL editor needs lifecycle sizing');
const executable = babel.Babel.transform(declarations.join('\n') + '\n' + script.slice(watcher.start, watcher.end), {
  filename: 'ddl-layout.ts', presets: ['typescript'], configFile: false, babelrc: false,
}).code + '\nglobalThis.actual = {ddlEditorHost,ddlEditorHeight,syncDdlEditorHeight,stopDdlEditorSizing};';
const listeners = new Map(), observers = [];
let callback, bodyBottom = 942;
const body = { getBoundingClientRect: () => ({ bottom: bodyBottom }) };
const host = { closest: () => body, getBoundingClientRect: () => ({ top: 160 }) };
const context = vm.createContext({
  ref: value => ({ value }), open: {}, ddlMode: {}, activeDdlKey: {},
  watch: (_refs, handler, options) => { callback = handler; assert.equal(options.flush, 'post'); },
  ResizeObserver: class {
    constructor(handler) { this.handler = handler; this.disconnected = false; observers.push(this); }
    observe(target) { assert.equal(target, body); }
    disconnect() { this.disconnected = true; }
  },
  window: {
    getComputedStyle: () => ({ paddingBottom: '12px' }),
    addEventListener: (event, listener) => listeners.set(event, listener),
    removeEventListener: event => listeners.delete(event),
  },
});
vm.runInContext(executable, context);
context.actual.ddlEditorHost.value = host;
callback([true, true]);
assert.equal(context.actual.ddlEditorHeight.value, '770px', 'Tall drawers should use their available space');
bodyBottom = 602;
listeners.get('resize')();
assert.equal(context.actual.ddlEditorHeight.value, '430px', 'Window resizing must update the explicit editor height');
bodyBottom = 290;
observers[0].handler();
assert.equal(context.actual.ddlEditorHeight.value, '200px', 'Short windows retain a readable editor minimum');
callback([true, true]);
assert.equal(observers[0].disconnected, true, 'Switching targets must release the previous observer');
assert.equal(listeners.size, 1, 'Only one resize listener may remain');
callback([false, false]);
assert.equal(observers[1].disconnected, true);
assert.equal(listeners.size, 0, 'Closing the drawer must release listeners');
context.actual.ddlEditorHost.value = null;
assert.doesNotThrow(context.actual.syncDdlEditorHeight);
assert.equal(source.includes(':has('), false, 'Legacy browser sizing must not depend on :has support');
console.log('DDL layout: tall/short drawers, window resize, target switching and cleanup verified; no :has dependency.');
