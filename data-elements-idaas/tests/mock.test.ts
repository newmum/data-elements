import test from 'node:test';
import assert from 'node:assert/strict';
class MemoryStorage {
    private values = new Map<string, string>();
    getItem(k: string) { return this.values.get(k) ?? null; }
    setItem(k: string, v: string) { this.values.set(k, String(v)); }
    removeItem(k: string) { this.values.delete(k); }
    clear() { this.values.clear(); }
    get length() { return this.values.size; }
    key(i: number) { return [...this.values.keys()][i] ?? null; }
}
Object.defineProperty(globalThis, 'localStorage', { value: new MemoryStorage(), configurable: true });
Object.defineProperty(globalThis, 'sessionStorage', { value: new MemoryStorage(), configurable: true });
const { api, getDatabase, getSession, resetData, setFailure, setSession, tickTasks } = await import('../src/mock/database.ts');
const admin = { username: 'admin', name: '平台管理员', role: 'admin' as const, domain: 'workforce' as const };
function fresh() { resetData(); setSession(admin); }
test('Mock登录使用固定评审账号并写入登录日志', async () => { fresh(); await api.login('admin', 'Review@2026', 'workforce'); assert.equal(getSession()?.role, 'admin'); assert.equal(getDatabase().logs[0].type, 'login'); });
test('错误评审密码被拒绝', async () => { fresh(); await assert.rejects(api.login('admin', 'wrong', 'workforce'), /账号或密码/); });
test('修改用户同时更新本地存储和操作日志', async () => { fresh(); const user = getDatabase().users.find(u => u.id === 'u1')!; await api.save('users', { ...user, name: '前端验收用户' }); assert.equal(getDatabase().users.find(u => u.id === 'u1')?.name, '前端验收用户'); assert.ok(localStorage.getItem('iam.frontend.mock.v2')?.includes('前端验收用户')); assert.equal(getDatabase().logs[0].module, 'users'); });
test('旧版本编辑返回冲突，不覆盖新内容', async () => { fresh(); const user = getDatabase().users.find(u => u.id === 'u1')!; await api.save('users', { ...user, name: '第一次修改' }); await assert.rejects(api.save('users', { ...user, name: '旧快照修改' }), /其他操作更新/); assert.equal(getDatabase().users.find(u => u.id === 'u1')?.name, '第一次修改'); });
test('故障注入只失败一次，不提交半成品', async () => { fresh(); const user = getDatabase().users.find(u => u.id === 'u1')!; setFailure(); await assert.rejects(api.save('users', { ...user, name: '重试目标' }), /请求暂时无法完成/); assert.notEqual(getDatabase().users.find(u => u.id === 'u1')?.name, '重试目标'); await api.save('users', { ...user, name: '重试目标' }); assert.equal(getDatabase().users.find(u => u.id === 'u1')?.name, '重试目标'); });
test('只读审计身份无法调用Mock修改', async () => { fresh(); setSession({ ...admin, username: 'auditor', role: 'auditor' }); await assert.rejects(api.save('users', { ...getDatabase().users[0], name: '越权修改' }), /修改权限/); });
test('政企管理会话无法修改公众身份', async () => { fresh(); await assert.rejects(api.save('users', { ...getDatabase().users.find(u => u.id === 'p1')!, name: '跨域修改' }), /其他身份域/); });
test('同步指定对象不会加入其他未选对象', async () => { fresh(); const id = await api.startTask('sc1', 'full', ['u1', 'u2']); const t = getDatabase().tasks.find(t => t.id === id)!; assert.deepEqual(t.items.map(i => i.id).sort(), ['u1', 'u2']); });
test('失败任务重试保留成功结果并最终完成', async () => { fresh(); const id = await api.startTask('sc1', 'full', ['u1', 'u2']); tickTasks(); assert.equal(getDatabase().tasks.find(t => t.id === id)?.status, 'partial'); await api.retryTask(id); tickTasks(); assert.equal(getDatabase().tasks.find(t => t.id === id)?.status, 'success'); });
test('中止任务保留明细，后续tick不改变', async () => { fresh(); const id = await api.startTask('sc1'); tickTasks(); await api.cancelTask(id); const snapshot = JSON.stringify(getDatabase().tasks.find(t => t.id === id)); tickTasks(); assert.equal(JSON.stringify(getDatabase().tasks.find(t => t.id === id)), snapshot); });
test('本地公众注册未标记实名认证，也不自动授权', async () => { fresh(); const initial = getDatabase().grants.length; await api.publicRegister({ name: '测试自然人', account: 'mocknewuser', email: 'test@example.com', phone: '13900009999' }); const u = getDatabase().users.find(u => u.account === 'mocknewuser'); assert.equal(u?.verified, false); assert.equal(u?.domain, 'public'); assert.equal(getDatabase().grants.length, initial); });
test('删除权限组仅移除该组授权来源', async () => { fresh(); const direct = getDatabase().grants.filter(g => g.source === 'user').length; await api.remove('groups', 'g1'); assert.equal(getDatabase().grants.filter(g => g.source === 'user').length, direct); assert.equal(getDatabase().grants.some(g => g.source === 'group' && g.subjectId === 'g1'), false); });
test('品牌设置与审计同时保存', async () => { fresh(); await api.settings('workforce', { title: '身份平台评审' }); assert.equal(getDatabase().settings.workforce.title, '身份平台评审'); assert.equal(getDatabase().logs[0].module, '系统设置'); });

