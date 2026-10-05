import test from 'node:test';
import assert from 'node:assert/strict';
import { createSeed } from '../src/domain/seed.ts';
import { assertGrant, assertRemove, belongsToOrg, canEdit, descendants, effectiveAccess, grantIsCurrent, syncGroupGrants, taskCounts, advanceTask, validateEntity, safeSnapshot, accessibleAppIds, allowedUser } from '../src/domain/engine.ts';
import { encodeCsv, parseCsv, maskEmail, maskPhone } from '../src/domain/csv.ts';
import type { Database, Grant, Session, SyncTask } from '../src/domain/types.ts';
const now = new Date('2026-09-26T12:00:00.000Z');
function setup() { const db = createSeed(now); db.grants = []; db.users.find(u => u.id === 'u1')!.status = 'enabled'; db.users.find(u => u.id === 'u1')!.locked = false; return db; }
function grant(overrides: Partial<Grant> = {}): Grant { return { id: 'test-grant', domain: 'workforce', name: '测试授权', status: 'enabled', createdAt: now.toISOString(), updatedAt: now.toISOString(), version: 1, source: 'user', subjectId: 'u1', appId: 'a1', roleIds: ['a1-r1'], includeChildren: true, startsAt: '2026-09-25T00:00:00.000Z', expiresAt: null, reason: '前端规则测试', ...overrides }; }
const admin: Session = { username: 'admin', name: '平台管理员', role: 'admin', domain: 'workforce' };
test('Mock数据包含两个隔离身份域和可引用应用', () => {
    const db = createSeed(now);
    assert.equal(db.apps.length, 12);
    assert.equal(db.orgs.length, 12);
    assert.equal(db.legalEntities.length, 8);
    assert.equal(db.logs.length, 1800);
    assert.equal(db.users.length, 241);
    for (const g of db.grants)
        assert.ok(db.apps.some(a => a.id === g.appId && a.domain === g.domain));
});
test('种子数据中所有用户通过字段校验', () => {
    const db = createSeed(now);
    for (const u of db.users)
        validateEntity(db, 'users', u);
});
test('种子数据中应用、角色、资源通过字段校验', () => {
    const db = createSeed(now);
    for (const a of db.apps)
        validateEntity(db, 'apps', a);
    for (const a of db.roles)
        validateEntity(db, 'roles', a);
    for (const a of db.resources)
        validateEntity(db, 'resources', a);
});
test('直接授权产生应用访问和业务角色', () => { const db = setup(); db.grants.push(grant()); const result = effectiveAccess(db, 'u1', now); assert.equal(result.length, 1); assert.ok(result[0].roleIds.includes('a1-r1')); });
test('同一用户多来源授权合并且保留来源', () => { const db = setup(); db.grants.push(grant(), grant({ id: 'second' })); const result = effectiveAccess(db, 'u1', now); assert.equal(result.length, 1); assert.equal(result[0].sources.length, 2); assert.equal(result[0].roleIds.length, 1); });
test('撤销一种来源保留其他有效来源', () => { const db = setup(); db.grants.push(grant({ status: 'revoked' }), grant({ id: 'second' })); assert.equal(effectiveAccess(db, 'u1', now)[0].sources.length, 1); });
test('到期边界不再生效', () => { assert.equal(grantIsCurrent(grant({ expiresAt: now.toISOString() }), now), false); });
test('未来授权不会提前生效', () => { assert.equal(grantIsCurrent(grant({ startsAt: '2027-01-01T00:00:00Z' }), now), false); });
test('停用用户不具有有效访问', () => { const db = setup(); db.grants.push(grant()); db.users.find(u => u.id === 'u1')!.status = 'disabled'; assert.deepEqual(effectiveAccess(db, 'u1', now), []); });
test('锁定用户不具有有效访问', () => { const db = setup(); db.grants.push(grant()); db.users.find(u => u.id === 'u1')!.locked = true; assert.deepEqual(effectiveAccess(db, 'u1', now), []); });
test('平台管理账号不可通过直接授权访问业务应用', () => { const db = setup(); const g = grant({ subjectId: 'u-admin' }); assert.throws(() => assertGrant(db, g), /管理账号/); db.grants.push(g); assert.deepEqual(effectiveAccess(db, 'u-admin', now), []); });
test('平台管理账号不可经机构授权继承业务应用', () => { const db = setup(); db.grants.push(grant({ source: 'org', subjectId: 'o0' })); assert.deepEqual(effectiveAccess(db, 'u-admin', now), []); });
test('跨域用户授权被拒绝', () => { assert.throws(() => assertGrant(setup(), grant({ subjectId: 'p1' })), /身份域/); });
test('不同应用角色不可混用', () => { assert.throws(() => assertGrant(setup(), grant({ roleIds: ['a2-r1'] })), /角色/); });
test('已停用应用不能新增有效授权', () => { const db = setup(); db.apps.find(a => a.id === 'a1')!.status = 'disabled'; assert.throws(() => assertGrant(db, grant()), /已启用应用/); });
test('应用停用后仍可撤销历史授权', () => { const db = setup(); db.apps.find(a => a.id === 'a1')!.status = 'disabled'; assert.doesNotThrow(() => assertGrant(db, grant({ status: 'revoked' }))); });
test('无效授权期限被拒绝', () => { assert.throws(() => assertGrant(setup(), grant({ expiresAt: '2020-01-01T00:00:00Z' })), /到期时间/); });
test('机构范围包含下级且任职关系可参与计算', () => { const db = setup(); const user = db.users.find(u => u.id === 'u1')!; user.orgId = 'o2'; user.appointments = [{ orgId: 'o2', post: '工程师', primary: true }]; assert.equal(belongsToOrg(db, user, 'o1', true), true); assert.equal(belongsToOrg(db, user, 'o1', false), false); });
test('机构层级修改不能形成循环', () => { const db = setup(); const org = db.orgs.find(o => o.id === 'o1')!; assert.throws(() => validateEntity(db, 'orgs', { ...org, parentId: 'o2' }), /下级机构/); });
test('机构遍历对畸形循环不会无限递归', () => { const db = setup(); db.orgs.find(o => o.id === 'o1')!.parentId = 'o2'; assert.ok(descendants(db, 'o1').size < 20); });
test('同域账号唯一性不区分大小写', () => { const db = setup(); const user = db.users.find(u => u.id === 'u1')!; assert.throws(() => validateEntity(db, 'users', { ...user, id: 'new', account: user.account.toUpperCase() }), /相同账号/); });
test('每个人员恰好一个主职', () => { const db = setup(); const u = db.users.find(u => u.id === 'u1')!; assert.throws(() => validateEntity(db, 'users', { ...u, appointments: [] }), /一个主职/); });
test('内置管理账号不能在用户页被停用', () => { const db = setup(); const u = db.users.find(u => u.id === 'u-admin')!; assert.throws(() => validateEntity(db, 'users', { ...u, status: 'disabled' }), /内置平台管理账号/); });
test('回调地址禁止通配符', () => { const db = setup(); assert.throws(() => validateEntity(db, 'apps', { ...db.apps[0], redirectUris: 'https://app.example.com/*' }), /URL/); });
test('生产回调地址禁止普通HTTP', () => { const db = setup(); assert.throws(() => validateEntity(db, 'apps', { ...db.apps[0], redirectUris: 'http://app.example.com/callback' }), /HTTPS/); });
test('本地调试回调可使用localhostHTTP', () => { const db = setup(); assert.doesNotThrow(() => validateEntity(db, 'apps', { ...db.apps[0], redirectUris: 'http://localhost:5173/callback' })); });
test('资源父项停用时排除其子资源', () => { const db = setup(); const resources = db.resources.filter(r => r.appId === 'a1'); resources[1].parentId = resources[0].id; resources[0].status = 'disabled'; db.roles.find(r => r.id === 'a1-r1')!.resourceIds = [resources[1].id]; db.grants.push(grant()); assert.equal(effectiveAccess(db, 'u1', now)[0].resourceIds.length, 0); });
test('权限组重算只替换自身授权来源', () => { const db = setup(); db.grants.push(grant()); const group = db.groups[0]; syncGroupGrants(db, group); const count = db.grants.length; syncGroupGrants(db, group); assert.equal(db.grants.length, count); assert.ok(db.grants.some(g => g.id === 'test-grant')); });
test('被引用的机构不可直接删除', () => { assert.throws(() => assertRemove(setup(), 'orgs', 'o1'), /关联/); });
test('被引用的资源不可直接删除', () => { const db = setup(); assert.throws(() => assertRemove(db, 'resources', db.resources[0].id), /关联/); });
test('审计角色只读', () => { assert.equal(canEdit({ ...admin, role: 'auditor' }, 'users'), false); assert.equal(canEdit(admin, 'users'), true); });
test('应用管理员只有指定应用范围', () => { assert.deepEqual(accessibleAppIds(setup(), { ...admin, role: 'appmanager' }), ['a1']); });
test('机构管理员范围不包含外部机构用户', () => { const db = setup(); const u = { ...db.users[0], orgId: 'o7', appointments: [{ orgId: 'o7', post: '职员', primary: true }] }; assert.equal(allowedUser(db, { ...admin, role: 'orgadmin' }, u), false); });
test('同步任务计数一致且首轮失败可重试', () => {
    const db = setup();
    let task: SyncTask = { ...db.tasks[0], status: 'running', progress: 0, attempt: 1, items: db.tasks[0].items.map(i => ({ ...i, status: 'pending', reason: '' })) };
    let ticks = 0;
    while (task.status === 'running' && ticks++ < 20)
        task = advanceTask(task);
    const counts = taskCounts(task);
    assert.equal(counts.success + counts.failed + counts.pending, counts.total);
    assert.equal(task.status, 'partial');
    assert.equal(task.progress, 100);
    task = { ...task, status: 'running', attempt: 2, items: task.items.map(i => i.status === 'failed' ? { ...i, status: 'pending', reason: '' } : i) };
    ticks = 0;
    while (task.status === 'running' && ticks++ < 20)
        task = advanceTask(task);
    assert.equal(task.status, 'success');
    assert.equal(taskCounts(task).failed, 0);
});
test('已中止任务不会继续改变结果', () => { const task = { ...setup().tasks[0], status: 'cancelled' }; assert.deepEqual(advanceTask(task), task); });
test('同步配置检查HTTPS和当前身份域', () => { const db = setup(); assert.throws(() => validateEntity(db, 'syncConfigs', { ...db.syncConfigs[0], endpoint: 'http://bad.example.com' }), /HTTPS/); });
test('法人标识不可重复，关联账号不可跨域', () => { const db = setup(); assert.throws(() => validateEntity(db, 'legalEntities', { ...db.legalEntities[0], id: 'copy' }), /已存在/); assert.throws(() => validateEntity(db, 'legalEntities', { ...db.legalEntities[0], contactId: 'u1' }), /公众身份域/); });
test('CSV支持BOM、逗号、引号和字段内换行', () => { const expected = [['姓名', '说明'], ['张某', '逗号,引号"和\n换行']]; assert.deepEqual(parseCsv(encodeCsv(expected)), expected); });
test('CSV未闭合引号返回可读错误', () => { assert.throws(() => parseCsv('姓名,说明\n"未结束'), /未闭合/); });
test('CSV导出中和潜在公式', () => {
    for (const value of ['=HYPERLINK("x")', '+1', '-1', '@test'])
        assert.ok(parseCsv(encodeCsv([[value]]))[0][0].startsWith("'"));
});
test('导出字段默认脱敏', () => { assert.equal(maskPhone('13900001234'), '139****1234'); assert.equal(maskEmail('sample@example.com'), 'sa***@example.com'); });
test('日志快照不明文输出敏感键', () => { const text = safeSnapshot({ password: 'topsecret', nested: { accessToken: 'abcdef' }, name: '可见' }); assert.ok(!text.includes('topsecret')); assert.ok(!text.includes('abcdef')); assert.ok(text.includes('可见')); });

