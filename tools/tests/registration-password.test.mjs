import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const source = await fs.readFile(new URL('lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/01.登记数据源(register-db).vue', root), 'utf8');
const behavior = source.split('// PASSWORD_VISIBILITY_START')[1].split('// PASSWORD_VISIBILITY_END')[0];
const makeTarget = () => {
  const handlers = new Map();
  return {
    handlers,
    addEventListener: (name, handler) => handlers.set(name, handler),
    removeEventListener: (name, handler) => { if (handlers.get(name) === handler) handlers.delete(name); },
    fire: (name, event = {}) => handlers.get(name)?.(event),
  };
};
const fixture = () => {
  const window = makeTarget(), document = { ...makeTarget(), hidden: false };
  const currentDb = { value: { isDraft: true, displayTid: '2100000000000000000' } };
  const currentDid = { value: 'draft' }, sourceType = { value: 'mysql' };
  const accessMode = { value: 'explore' }, connectionEnabled = { value: '1' };
  const rightPanelLoading = { value: false }, registerModalContext = { open: { value: true } };
  const mounts = [], unmounts = [], changes = [];
  const scope = { window, document, currentDb, currentDid, sourceType, accessMode, connectionEnabled, rightPanelLoading, registerModalContext,
    computed: fn => ({ get value() { return fn(); } }), reactive: value => value,
    watch: (refs, callback) => changes.push(callback), onMounted: fn => mounts.push(fn), onBeforeUnmount: fn => unmounts.push(fn),
  };
  vm.runInNewContext(behavior + '\nthis.bindings = passwordInputBindings;', scope);
  mounts.forEach(fn => fn());
  return { ...scope, changed: () => changes.forEach(fn => fn()), unmount: () => unmounts.forEach(fn => fn()) };
};
const control = { key: 'Control', ctrlKey: true, repeat: false };
const f = fixture(), field = 'connectorForm.password';
assert.equal(f.bindings(field).showPassword, true, 'display-only draft ID must not count as persisted');
const draftKey = f.bindings(field).key;
f.currentDb.value = { tid: 'saved-source', isDraft: false }; f.changed();
assert.equal(f.bindings(field).showPassword, false);
assert.notEqual(f.bindings(field).key, draftKey, 'first save resets the built-in eye toggle');
f.bindings(field).onMouseenter({ ctrlKey: false });
assert.equal(f.bindings(field).type, 'password');
f.window.fire('keydown', control);
assert.equal(f.bindings(field).type, 'text');
assert.equal(f.bindings('connectorForm.dbMetaPassword').type, 'password', 'only the intended input reveals');
f.window.fire('keyup', { key: 'Control', ctrlKey: false });
assert.equal(f.bindings(field).type, 'password');
f.bindings(field).onMouseenter({ ctrlKey: true });
assert.equal(f.bindings(field).type, 'text', 'Ctrl can be held before entering the input');
f.bindings(field).onFocus(); f.bindings(field).onMouseleave();
assert.equal(f.bindings(field).type, 'password', 'pointer leave masks even when the input remains focused');
f.window.fire('keydown', { ...control, repeat: true });
assert.equal(f.bindings(field).type, 'password', 'key repeats cannot reveal again after pointer leave');
f.window.fire('keydown', control);
assert.equal(f.bindings(field).type, 'text', 'keyboard focus supports the same temporary reveal');
f.bindings(field).onBlur(); assert.equal(f.bindings(field).type, 'password');
for (const stop of [() => f.window.fire('blur'), () => { f.document.hidden = true; f.document.fire('visibilitychange'); f.document.hidden = false; }, () => { f.currentDid.value = 'another-source'; f.changed(); }, () => { f.sourceType.value = 'kingbase8'; f.changed(); }, () => { f.registerModalContext.open.value = false; f.changed(); }]) {
  f.bindings(field).onMouseenter({ ctrlKey: true }); assert.equal(f.bindings(field).type, 'text');
  stop(); assert.equal(f.bindings(field).type, 'password');
}
const firstApi = 'item.authPassword:client-one', secondApi = 'item.authPassword:client-two';
f.bindings(firstApi).onMouseenter({ ctrlKey: true });
assert.equal(f.bindings(firstApi).type, 'text'); assert.equal(f.bindings(secondApi).type, 'password');
f.currentDb.value = { originalTid: 'missing-source', detailMissing: true }; f.changed();
assert.equal(f.bindings(field).showPassword, false, 'failed detail hydration must not re-enable the eye');
f.rightPanelLoading.value = true; f.window.fire('keydown', control);
assert.equal(f.bindings(field).type, 'password');
f.unmount(); assert.equal(f.window.handlers.size, 0); assert.equal(f.document.handlers.size, 0);
console.log('Draft/persisted eye, Ctrl hold/release, pointer leave, keyboard focus, window/tab/source changes, repeated API fields and event cleanup passed.');
