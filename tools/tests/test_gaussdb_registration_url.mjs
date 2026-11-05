import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const source = await fs.readFile(new URL('../../lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/01.登记数据源(register-db).vue', import.meta.url), 'utf8');
const fn = source.slice(source.indexOf('function gaussdbUrlParameters()'), source.indexOf('function buildHiveJdbcUrl('));
const form = {gaussdbVariant:'gaussdb-jdbc', sslMode:'prefer', schema:''};
const context = vm.createContext({connectorForm: form});
vm.runInContext(fn + '\nglobalThis.parameters = gaussdbUrlParameters;', context);
for (const variant of ['gaussdb', 'gaussdb-jdbc']) {
  form.gaussdbVariant = variant;
  assert.equal(context.parameters(), '?autoBalance=true');
  form.schema = '业务'; form.sslMode = 'verify-full';
  assert.equal(context.parameters(), '?autoBalance=true&currentSchema=%E4%B8%9A%E5%8A%A1&sslmode=verify-full');
  form.schema = ''; form.sslMode = 'prefer';
}
form.gaussdbVariant = 'opengauss';
assert.equal(context.parameters(), '?sslmode=prefer');
form.gaussdbVariant = 'mysql';
assert.equal(context.parameters(), '?sslMode=PREFERRED');
console.log('PASS: registration native autoBalance, explicit TLS/schema, and other Gauss products');
