import test from 'node:test';
import assert from 'node:assert/strict';
import type { Session } from '../src/domain/types.ts';

class MemoryStorage {
    private values = new Map<string, string>();
    getItem(key: string) { return this.values.get(key) ?? null; }
    setItem(key: string, value: string) { this.values.set(key, value); }
    removeItem(key: string) { this.values.delete(key); }
}

const storage = new MemoryStorage();
Object.defineProperty(globalThis, 'sessionStorage', { value: storage, configurable: true });
const workspace = await import('../src/services/workspace.ts');
const identity = await import('../src/services/identity.ts');
const platformSession: Session = {
    userId: 'operator-1', tenantId: 'tenant-1', tenantName: '租户', username: 'operator', name: '操作员', role: 'admin',
    domain: 'workforce', capabilities: ['workspace'], editableTables: [], permissions: [], allowedAppIds: [], orgIds: [], scopeMode: 'tenant',
};
const response = (data: unknown, code = 200, status = 200) => new Response(JSON.stringify({ code, data }), { status, headers: { 'content-type': 'application/json' } });

test('管理端退出立即清除本地会话，并用原令牌发送撤销请求', async () => {
    let finishLogout: (value: Response) => void = () => {};
    let logoutToken = '';
    globalThis.fetch = async (input, options) => {
        const path = String(input);
        if (path.endsWith('/auth/login')) return response({ token: 'platform-token-1', session: platformSession });
        if (path.endsWith('/workspace/bootstrap')) return response({ session: platformSession, database: workspace.emptyDatabase(), capabilities: platformSession.capabilities });
        if (path.endsWith('/auth/logout')) {
            logoutToken = (options?.headers as Record<string, string>).token;
            return new Promise<Response>(resolve => { finishLogout = resolve; });
        }
        throw new Error(`Unexpected request: ${path}`);
    };
    await workspace.api.login('operator', 'password', 'workforce');
    assert.equal(storage.getItem('iam.frontend.backend.token'), 'platform-token-1');
    const logout = workspace.api.logout();
    assert.equal(logoutToken, 'platform-token-1');
    assert.equal(storage.getItem('iam.frontend.backend.token'), null);
    assert.equal(workspace.getSession(), null);
    assert.equal(workspace.getDatabase().users.length, 0);
    finishLogout(response(true));
    await logout;
});

test('管理端撤销请求失败也退出，并保留后续新会话', async () => {
    let loginCount = 0;
    let finishLogout: (value: Response) => void = () => {};
    globalThis.fetch = async input => {
        const path = String(input);
        if (path.endsWith('/auth/login')) return response({ token: `platform-token-${++loginCount}`, session: platformSession });
        if (path.endsWith('/workspace/bootstrap')) return response({ session: platformSession, database: workspace.emptyDatabase(), capabilities: platformSession.capabilities });
        if (path.endsWith('/auth/logout')) return new Promise<Response>(resolve => { finishLogout = resolve; });
        throw new Error(`Unexpected request: ${path}`);
    };
    await workspace.api.login('operator', 'password', 'workforce');
    const logout = workspace.api.logout();
    assert.equal(workspace.getSession(), null);
    await workspace.api.login('operator', 'password', 'workforce');
    finishLogout(response(null, 503, 503));
    await assert.rejects(logout, /服务器无响应，请稍后再试/);
    assert.equal(storage.getItem('iam.frontend.backend.token'), 'platform-token-2');
    assert.equal(workspace.getSession()?.userId, 'operator-1');
    workspace.setSession(null);
});

test('管理端旧请求的过期响应不能清除重新登录的会话', async () => {
    let loginCount = 0;
    let finishOldRead: (value: Response) => void = () => {};
    globalThis.fetch = async input => {
        const path = String(input);
        if (path.endsWith('/auth/login')) return response({ token: `platform-token-${++loginCount}`, session: platformSession });
        if (path.endsWith('/workspace/bootstrap')) return response({ session: platformSession, database: workspace.emptyDatabase(), capabilities: platformSession.capabilities });
        if (path.endsWith('/auth/logout')) return response(true);
        if (path.endsWith('/idaas/profile/read')) return new Promise<Response>(resolve => { finishOldRead = resolve; });
        throw new Error(`Unexpected request: ${path}`);
    };
    await workspace.api.login('operator', 'password', 'workforce');
    const oldRead = workspace.foundationApi.read('/idaas/profile/read');
    await workspace.api.logout();
    await workspace.api.login('operator', 'password', 'workforce');
    finishOldRead(response(null, 401, 401));
    await assert.rejects(oldRead, /登录已过期/);
    assert.equal(storage.getItem('iam.frontend.backend.token'), 'platform-token-2');
    assert.equal(workspace.getSession()?.userId, 'operator-1');
    workspace.setSession(null);
});

test('统一认证退出网络失败也立即清令牌，旧请求不会清除后续新登录', async () => {
    let loginCount = 0;
    let failLogout: (error: Error) => void = () => {};
    let finishOldRead: (value: Response) => void = () => {};
    let logoutToken = '';
    globalThis.fetch = async (input, options) => {
        const path = String(input);
        if (path.endsWith('/auth-account/login')) return response({ token: `identity-token-${++loginCount}` });
        if (path.endsWith('/auth-account/logout')) {
            logoutToken = (options?.headers as Record<string, string>).token;
            return new Promise<Response>((_, reject) => { failLogout = reject; });
        }
        if (path.endsWith('/auth-account/me')) return new Promise<Response>(resolve => { finishOldRead = resolve; });
        throw new Error(`Unexpected request: ${path}`);
    };
    await identity.identityApi.login('user', 'password', 'public');
    const oldRead = identity.identityApi.me();
    const logout = identity.identityApi.logout();
    assert.equal(logoutToken, 'identity-token-1');
    assert.equal(identity.hasIdentitySession(), false);
    assert.equal(storage.getItem('iam.auth.account.token'), null);
    await identity.identityApi.login('user', 'password', 'public');
    finishOldRead(response(null, 401, 401));
    await assert.rejects(oldRead, /登录已过期/);
    failLogout(new TypeError('offline'));
    await assert.rejects(logout, /服务器无响应，请稍后再试/);
    assert.equal(identity.hasIdentitySession(), true);
    assert.equal(storage.getItem('iam.auth.account.token'), 'identity-token-2');
    identity.retainIdentityToken('');
});

test('已失效的退出令牌视为本地退出完成', async () => {
    globalThis.fetch = async input => String(input).endsWith('/auth-account/logout')
        ? response(null, 401, 401) : response({ token: 'expired-identity-token' });
    await identity.identityApi.login('user', 'password', 'public');
    await identity.identityApi.logout();
    assert.equal(identity.hasIdentitySession(), false);
});
