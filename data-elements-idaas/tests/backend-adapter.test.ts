import test from 'node:test';
import assert from 'node:assert/strict';
import { request } from '../src/services/http.ts';
import type { Session } from '../src/domain/types.ts';
import { allowedUser, canEdit } from '../src/domain/engine.ts';

class MemoryStorage {
    private values = new Map<string, string>();
    getItem(key: string) { return this.values.get(key) ?? null; }
    setItem(key: string, value: string) { this.values.set(key, value); }
    removeItem(key: string) { this.values.delete(key); }
}
Object.defineProperty(globalThis, 'sessionStorage', { value: new MemoryStorage(), configurable: true });
const workspace = await import('../src/services/workspace.ts');
const session: Session = { userId: 'account-1', tenantId: 'tenant-1', tenantName: '公安', username: 'contract.user', name: '接口契约用户', role: 'admin', domain: 'workforce', capabilities: ['workspace', 'users', 'orgs', 'apps', 'roles', 'resources', 'audit'], editableTables: ['users', 'orgs', 'apps', 'roles', 'resources'], permissions: ['idaas:users:write'], allowedAppIds: [], orgIds: [], scopeMode: 'tenant' };
const response = (data: unknown, code: number | string = 200, status = 200) => new Response(JSON.stringify({ code, data }), { status, headers: { 'content-type': 'application/json' } });

test('HTTP 适配器遵循 Magic 响应并发送现有 token 请求头', async () => {
    globalThis.fetch = async (input, options) => {
        assert.equal(input, '/api/idaas/session/me');
        assert.equal((options?.headers as Record<string, string>).token, 'opaque-test-token');
        assert.equal((options?.headers as Record<string, string>).satoken, undefined);
        return response({ tenantId: 'tenant-1' });
    };
    assert.deepEqual(await request('/idaas/session/me', { token: 'opaque-test-token' }), { tenantId: 'tenant-1' });
});
test('HTTP 业务失败、无效响应和网络失败都拒绝返回数据', async () => {
    globalThis.fetch = async () => new Response(JSON.stringify({ code: 403, message: '无此操作权限', data: { users: ['not-allowed'] } }));
    await assert.rejects(request('/idaas/users/save'), /无此操作权限/);
    globalThis.fetch = async () => new Response('<html>error</html>');
    await assert.rejects(request('/idaas/workspace/bootstrap'), /无法识别/);
    globalThis.fetch = async () => new Response(JSON.stringify({ code: 200 }));
    await assert.rejects(request('/idaas/workspace/bootstrap'), /不完整/);
    globalThis.fetch = async () => { throw new TypeError('offline'); };
    await assert.rejects(request('/idaas/workspace/bootstrap'), /无法连接/);
});
test('工作区最初为空，登录失败不会载入初始身份或伪造会话', async () => {
    assert.equal(workspace.getDatabase().users.length, 0);
    assert.equal(workspace.getDatabase().apps.length, 0);
    assert.equal(workspace.getSession(), null);
    globalThis.fetch = async () => new Response(JSON.stringify({ code: 400, message: '账号或密码不正确' }));
    await assert.rejects(workspace.api.login('wrong', 'wrong', 'workforce', 'tenant-1'), /账号或密码/);
    assert.equal(workspace.getSession(), null);
    assert.equal(workspace.getDatabase().users.length, 0);
});
test('登录凭据错误保留业务提示，已有会话过期使用认证错误', async () => {
    globalThis.fetch = async () => new Response(JSON.stringify({ code: 401, message: '账号或密码不正确' }));
    await assert.rejects(request('/idaas/auth/login'), /账号或密码不正确/);
    globalThis.fetch = async () => new Response(JSON.stringify({ code: 100120, msg: 'user_no_login' }));
    await assert.rejects(request('/idaas/session/me', { token: 'expired' }), error => (error as { code?: string }).code === 'UNAUTHENTICATED');
});
test('管理角色名称不扩大服务端功能权限或空机构范围', () => {
    const db = workspace.emptyDatabase();
    const user = { id: 'u', domain: 'workforce' as const, name: '机构用户', status: 'enabled', createdAt: '', updatedAt: '', version: 1, account: 'user', email: '', phone: '', orgId: 'org1', post: '', kind: 'person' as const, locked: false, appointments: [], verified: false, history: [] };
    assert.equal(canEdit({ ...session, role: 'admin', editableTables: [] }, 'users'), false);
    assert.equal(allowedUser(db, { ...session, scopeMode: 'org', orgIds: [] }, user), false);
    assert.equal(allowedUser(db, { ...session, scopeMode: 'tenant', orgIds: [] }, user), true);
});
test('实体保存使用明确创建标识、幂等请求号和服务端重新加载结果', async () => {
    const db = workspace.emptyDatabase();
    const calls: Array<{ path: string; body: Record<string, unknown> }> = [];
    globalThis.fetch = async (input, options) => {
        const path = String(input);
        const body = JSON.parse(String(options?.body || '{}'));
        calls.push({ path, body });
        if (path.endsWith('/auth/login')) return response({ token: 'contract-token', session });
        if (path.endsWith('/workspace/bootstrap')) return response({ session, database: db, capabilities: session.capabilities });
        if (path.endsWith('/orgs/save')) {
            assert.equal(body.creating, true);
            assert.match(body.requestId, /^[a-f0-9-]{36}$/);
            db.orgs.push({ ...body.record, id: 'server-generated-id', version: 2 });
            return response({ id: 'server-generated-id', version: 2 });
        }
        throw new Error(`Unexpected path ${path}`);
    };
    await workspace.api.login(session.username, 'valid-password', 'workforce', session.tenantId);
    await workspace.api.save('orgs', { id: 'temporary-client-id', domain: 'workforce', name: '新增机构', status: 'enabled', version: 1, createdAt: '', updatedAt: '', code: 'ORG_NEW', parentId: null, leader: '', line: '' });
    assert.equal(workspace.getDatabase().orgs[0].id, 'server-generated-id');
    assert.equal(calls.filter(call => call.path.endsWith('/workspace/bootstrap')).length, 2);
    assert.equal(workspace.getSession()?.tenantId, 'tenant-1');
    assert.throws(() => workspace.setSession({ ...session, tenantId: 'other-tenant' }), /身份服务/);
});
test('20/100 项批量停用和 CSV 导入各只发送一次写请求与一次工作区刷新', async () => {
    const db = workspace.getDatabase();
    for (const size of [20, 100]) {
        const paths: string[] = [];
        globalThis.fetch = async (input, options) => {
            const path = String(input);
            paths.push(path);
            if (path.endsWith('/users/batch-disable')) {
                const body = JSON.parse(String(options?.body));
                assert.equal(body.entries.length, size);
                assert.equal(body.domain, 'workforce');
                return response({ count: size });
            }
            if (path.endsWith('/users/import')) {
                const body = JSON.parse(String(options?.body));
                assert.equal(body.rows.length, size);
                assert.equal(body.domain, 'workforce');
                return response({ count: size });
            }
            if (path.endsWith('/workspace/bootstrap')) return response({ session, database: db, capabilities: session.capabilities });
            throw new Error(`Unexpected path ${path}`);
        };
        assert.equal(await workspace.api.batchDisableUsers(Array.from({ length: size }, (_, i) => ({ id: `user-${i}`, version: 1 })), 'workforce'), size);
        assert.equal(await workspace.api.importUsers(Array.from({ length: size }, (_, i) => ({ name: `Person ${i}`, account: `user${i}`, email: '', phone: '', orgCode: 'ORG', post: '' })), 'workforce'), size);
        assert.equal(paths.filter(path => path.endsWith('/users/batch-disable')).length, 1);
        assert.equal(paths.filter(path => path.endsWith('/users/import')).length, 1);
        assert.equal(paths.filter(path => path.endsWith('/workspace/bootstrap')).length, 2);
    }
});
test('待恢复人员操作读取失败保留工作区，重试只发送原请求号', async () => {
    const db = workspace.getDatabase();
    globalThis.fetch = async () => new Response(JSON.stringify({ code: 503, message: '恢复记录暂时不可用' }));
    await assert.rejects(workspace.api.userOperations(), /暂时不可用/);
    assert.equal(workspace.getDatabase(), db);
    assert.equal(workspace.getWorkspaceState().status, 'ready');
    const operation = { requestId: 'original-request-id', userId: 'u1', operationType: 'CREATE_USER', status: 'PENDING', errorCode: null, createdTime: '2026-09-27T12:00:00' };
    let retryBody: unknown;
    let bootstrapCount = 0;
    globalThis.fetch = async (input, options) => {
        const path = String(input);
        if (path.endsWith('/users/operations')) return response([operation]);
        if (path.endsWith('/users/retry')) { retryBody = JSON.parse(String(options?.body)); return response({ status: 'COMPLETED' }); }
        if (path.endsWith('/workspace/bootstrap')) { bootstrapCount++; return response({ session, database: db, capabilities: session.capabilities }); }
        throw new Error(`Unexpected path ${path}`);
    };
    assert.deepEqual(await workspace.api.userOperations(), [operation]);
    await workspace.api.retryUserOperation(operation.requestId);
    assert.deepEqual(retryBody, { requestId: 'original-request-id' });
    assert.equal(bootstrapCount, 1);
});
test('只读会话不请求或重试开户恢复接口', async () => {
    const db = workspace.getDatabase();
    globalThis.fetch = async () => response({ session: { ...session, editableTables: [] }, database: db, capabilities: session.capabilities });
    await workspace.refreshWorkspace();
    let called = false;
    globalThis.fetch = async () => { called = true; return response([]); };
    await assert.rejects(workspace.api.userOperations(), /尚未开通/);
    await assert.rejects(workspace.api.retryUserOperation('original-request-id'), /尚未开通/);
    assert.equal(called, false);
});
test('会话过期清空工作区，公众管理登录失败与无权限同步不能返回成功', async () => {
    globalThis.fetch = async () => response(null, 401, 401);
    await assert.rejects(workspace.refreshWorkspace(), /登录已过期/);
    assert.equal(workspace.getSession(), null);
    assert.equal(workspace.getDatabase().orgs.length, 0);
    globalThis.fetch = async () => response(null, 401, 200);
    await assert.rejects(workspace.api.login('user', 'password', 'public'), /请求未完成/);
    await assert.rejects(workspace.api.startTask('id'), /尚未开通/);
});

test('身份资料加载去重且串行，不由迟到的公众响应覆盖返回政企的目标', async () => {
    const db = workspace.emptyDatabase();
    let current = { ...session };
    const transitions: string[] = [];
    globalThis.fetch = async (input, options) => {
        const path = String(input);
        if (path.endsWith('/auth/login')) return response({ token: 'navigation-token', session: current });
        if (path.endsWith('/session/domain')) {
            const domain = JSON.parse(String(options?.body)).domain;
            transitions.push(domain);
            if (domain === 'public') await new Promise(resolve => setTimeout(resolve, 15));
            current = { ...current, domain };
        }
        return response({ session: current, database: db, capabilities: current.capabilities });
    };
    await workspace.api.login(session.username, 'valid-password', 'workforce');
    const first = workspace.api.changeDomain('public');
    assert.equal(workspace.api.changeDomain('public'), first);
    const last = workspace.api.changeDomain('workforce');
    await Promise.all([first, last]);
    assert.deepEqual(transitions, ['public', 'workforce']);
    assert.equal(workspace.getSession()?.domain, 'workforce');
});

test('退出期间的旧工作区响应不能恢复会话和业务资料', async () => {
    let release: (response: Response) => void = () => {};
    globalThis.fetch = async input => String(input).endsWith('/session/domain')
        ? new Promise<Response>(resolve => { release = resolve; }) : response(true);
    const loading = workspace.api.changeDomain('public');
    const rejected = assert.rejects(loading, /会话已更新/);
    await new Promise(resolve => setTimeout(resolve, 0));
    await workspace.api.logout();
    release(response({ session: { ...session, domain: 'public' }, database: workspace.emptyDatabase(), capabilities: [] }));
    await rejected;
    assert.equal(workspace.getSession(), null);
    assert.equal(workspace.getWorkspaceState().status, 'idle');
    assert.equal(workspace.getDatabase().users.length, 0);
});
