import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';
import { createRequire } from 'node:module';

const root = new URL('../../', import.meta.url);
const source = await fs.readFile(new URL('lowcode/09.系统管理/02.用户与权限/用户管理(user-manage).vue', root), 'utf8');
const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
const ast = babel.Babel.transform(script, { ast: true, code: false, parserOpts: { sourceType: 'module', plugins: ['typescript'] } }).ast;
const names = ['pinyinCache', 'nameToPinyin', 'accountRegex', 'formRules', 'handleSave'];
const selected = names.map(name => {
  const declaration = ast.program.body.find(n => n.type === 'VariableDeclaration' && n.declarations.some(d => d.id.name === name));
  assert.ok(declaration, name);
  return script.slice(declaration.start, declaration.end);
});
const code = babel.Babel.transform(selected.join('\n'), { filename: 'user-create.ts', presets: ['typescript'] }).code;
const require = createRequire(new URL('data-elements-chengtian/package.json', root));
const form = {};
const calls = [];
const messages = [];
const context = vm.createContext({
  pinyin: require('js-pinyin'), FormUtils: { fixJson: value => value },
  drawerType: { value: 'add' }, generatedAccount: { value: '' },
  currentUserId: { value: '' }, passwordValue: { value: '' },
  saveLoading: { value: false }, drawerVisible: { value: true }, uploadedAvatarId: { value: '' },
  idCardRegex: /^\d{17}[\dXx]$/, customUpload() {}, h() {}, console,
  jsonFormRef: { value: { getFormData: async () => ({ ...form }) } },
  refreshTableData() {},
  ElMessage: Object.fromEntries(['error', 'warning', 'success'].map(key => [key, value => messages.push([key, value])])),
  $common: { regexps: { phone: /^1[3-9]\d{9}$/ }, post: async (route, payload) => calls.push({ route, payload }) },
});
vm.runInContext(code + '\nglobalThis.actual = {' + names.join(',') + '};', context);
const { nameToPinyin, accountRegex, formRules, handleSave } = context.actual;
for (const name of ['叶华', '张三', '吕', '李四', '123测试', 'A'.repeat(70)]) {
  assert.ok(accountRegex.test(nameToPinyin(name)), `Generated invalid account for ${name}`);
}
assert.equal(nameToPinyin('叶华'), 'yehua');
for (const value of ['yh', '1test', 'test user', '', 'a'.repeat(51)]) assert.equal(accountRegex.test(value), false);
const change = formRules.find(r => r.field === 'realName').on.change;
const inject = { api: { getValue: field => form[field], setValue: (field, value) => { form[field] = value; } } };
change(inject, '叶华');assert.equal(form.userName, 'yehua');
change(inject, '张三');assert.equal(form.userName, 'zhangsan');
form.userName = 'manual_account';change(inject, '李四');assert.equal(form.userName, 'manual_account');
context.drawerType.value = 'detail';change(inject, '王五');assert.equal(form.userName, 'manual_account');
const validate = formRules.find(r => r.field === 'userName').validate[0].validator;
let invalid;validate({}, 'yh', error => { invalid = error; });assert.ok(invalid);
let valid;validate({}, ' yehua ', error => { valid = error; });assert.equal(valid, undefined);
Object.assign(form, { realName: ' 测试姓名 ', userName: ' test_account ', phone: ' 13800000000 ', orgId: 'org-1', status: 0, password: 'test-only', confirmPassword: 'test-only' });
await handleSave();assert.equal(calls.length, 1);assert.equal(calls[0].payload.status, 0);
assert.equal(calls[0].payload.realName, '测试姓名');assert.equal(calls[0].payload.userName, 'test_account');
assert.equal(calls[0].payload.phone, '13800000000');assert.equal(calls[0].payload.confirmPassword, undefined);
console.log('PASS: two-character names, minimum length, manual/edit account preservation, field validation, and one normalized save request');
