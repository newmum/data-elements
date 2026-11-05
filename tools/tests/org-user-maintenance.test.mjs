import assert from 'node:assert/strict';
import fs from 'node:fs/promises';
import vm from 'node:vm';

const root = new URL('../../', import.meta.url);
const babel = {};
vm.runInNewContext(await fs.readFile(new URL('data-elements-chengtian/public/babel.min.js', root), 'utf8'), babel);
async function functions(file, names) {
  const source = await fs.readFile(new URL(file, root), 'utf8');
  const script = source.match(/<script\b[^>]*>([\s\S]*?)<\/script>/)[1];
  const ast = babel.Babel.transform(script, { ast: true, code: false, parserOpts: { sourceType: 'module', plugins: ['typescript'] } }).ast;
  const chunks = names.map(name => {
    const node = ast.program.body.find(n => n.type === 'VariableDeclaration' && n.declarations.some(d => d.id.name === name));
    assert.ok(node, name);
    return script.slice(node.start, node.end);
  });
  return babel.Babel.transform(chunks.join('\n'), { filename: 'maintenance.ts', presets: ['typescript'] }).code;
}
const userFunctions = await functions('lowcode/09.系统管理/02.用户与权限/用户管理(user-manage).vue', ['buildUserParams', 'requestUserPage', 'isUserOnline', 'fetchUserData']);
const orgFunctions = await functions('lowcode/09.系统管理/02.用户与权限/组织机构(org-manage).vue', ['loadOrgUsers']);
const organizationOptionsFunctions = await functions('lowcode/09.系统管理/02.用户与权限/用户管理(user-manage).vue', ['fetchOrgTree', 'handleDetail', 'handleAdd', 'syncOrganizationOptions']);
for (const size of [20, 100]) {
  const rows = Array.from({ length: size }, (_, i) => ({ id: `validation-user-${i}`, userName: `validation-${i}` }));
  const calls = [];
  const post = async (route, payload) => { calls.push({ route, payload }); return { list: rows, total: size }; };
  const user = vm.createContext({ currentNode: { value: { id: 'validation-org' } }, searchKeyword: { value: '' }, filterOnline: { value: '' },
    fetchOnlineUsers: async () => ({ ids: new Set(['validation-user-0']), userNames: new Set() }), $common: { post }, console });
  vm.runInContext(`${userFunctions}\nglobalThis.fetchPage = fetchUserData;`, user);
  const result = await user.fetchPage({ pageNo: 1, pageSize: size });
  assert.equal(calls.length, 1, 'one user page request, no per-row detail fetches');
  assert.equal(calls[0].payload.orgId, 'validation-org');
  assert.equal(result.list.length, size);
  assert.equal(result.list[0].online, true);
  calls.length = 0;
  const org = vm.createContext({ formData: { value: { id: 'validation-org' } }, orgUserRequest: 0, orgUsers: { value: [] }, orgUsersLoading: { value: false },
    orgUserPage: { value: 1 }, orgUserSize: { value: size }, orgUserSort: { value: {} }, orgUserTotal: { value: 0 }, API: { USER_PAGE: '/sym/user/page' }, $common: { post }, console });
  vm.runInContext(`${orgFunctions}\nglobalThis.fetchPage = loadOrgUsers;`, org);
  await org.fetchPage();
  assert.equal(calls.length, 1, 'one organization-users page request for either page size');
  assert.equal(calls[0].payload.pageSize, size);
  assert.equal(org.orgUsers.value.length, size);
  assert.equal(org.orgUserTotal.value, size);
  console.log(`PASS: ${size} users: one user-page request after online snapshot, one organization-users request; no per-row reads`);
  const options = Array.from({ length: size }, (_, i) => ({ id: `validation-org-${i}`, value: `validation-org-${i}`, label: `最新机构${i}` }));
  const updates = [];
  const organizationRule = { props: { props: { checkStrictly: true, emitPath: false } } };
  let treeReads = 0;
  const refs = Object.fromEntries(['drawerType', 'drawerTab', 'currentUserId', 'roleOptions', 'selectedRoleIds', 'drawerVisible', 'uploadedAvatarId', 'uploadedDataUrl', 'passwordValue', 'generatedAccount'].map(name => [name, { value: null }]));
  const form = vm.createContext({ ...refs, treeData: { value: [{ children: [] }] }, $user: { orgRootId: 'validation-root' },
    nextTick: async callback => callback?.(), fetchAvatarUrl: async () => '', console,
    $common: { post: async () => { treeReads++; return options; } },
    jsonFormRef: { value: { api: { getRule: field => { assert.equal(field, 'orgId'); return organizationRule; }, refresh: () => updates.push({ field: 'orgId', options: organizationRule.props.options }) }, clearValue() {}, setValue: value => updates.push({ value }) } } });
  vm.runInContext(`${organizationOptionsFunctions}\nglobalThis.operations = { fetchOrgTree, handleDetail, handleAdd };`, form);
  await form.operations.fetchOrgTree();
  assert.equal(treeReads, 1);
  assert.equal(updates.at(-1).options, options, 'asynchronous tree arrival updates an already open form');
  assert.equal(organizationRule.props.options, options, 'native Cascader reads props.options');
  assert.equal(organizationRule.props.props.emitPath, false);
  assert.equal(organizationRule.props.props.checkStrictly, true);
  updates.length = 0;
  await form.operations.handleDetail({ id: 'validation-member', orgId: options.at(-1).id });
  assert.equal(updates[0].field, 'orgId');
  assert.equal(updates[0].options, options, 'detail uses the current tenant tree, including newly maintained organizations');
  assert.equal(updates[1].value.orgId, options.at(-1).id);
  form.operations.handleAdd();
  assert.equal(updates.at(-2).options, options);
  assert.equal(treeReads, 1, 'opening forms adds no per-organization requests');
  console.log(`PASS: ${size} organizations: one tree request, fresh add/detail options applied before values, async arrival synchronized`);
}
