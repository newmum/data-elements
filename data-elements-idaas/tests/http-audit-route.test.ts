import test from 'node:test';
import assert from 'node:assert/strict';
import { request } from '../src/services/http.ts';

test('审计路由在无浏览器、正常路由和无效片段下不影响请求或泄露查询参数', async (t) => {
    const previousWindow = Object.getOwnPropertyDescriptor(globalThis, 'window');
    const previousFetch = globalThis.fetch;
    const seen: Record<string, string>[] = [];
    t.after(() => {
        if (previousWindow) Object.defineProperty(globalThis, 'window', previousWindow);
        else Reflect.deleteProperty(globalThis, 'window');
        globalThis.fetch = previousFetch;
    });
    globalThis.fetch = async (_url, options) => {
        seen.push(options?.headers as Record<string, string>);
        return new Response(JSON.stringify({code: 0, data: {accepted: true}}));
    };
    Reflect.deleteProperty(globalThis, 'window');
    assert.deepEqual(await request('/idaas/session/me'), {accepted: true});
    assert.equal(seen.at(-1)?.['X-Audit-Route'], undefined);
    Object.defineProperty(globalThis, 'window', {configurable: true, value: {location: {hash: '#/audit/logs?keyword=validation-private-query'}}});
    await request('/idaas/logs/query');
    assert.equal(seen.at(-1)?.['X-Audit-Route'], '/audit/logs');
    assert.equal(JSON.stringify(seen).includes('validation-private-query'), false);
    for (const hash of ['', '#invalid', '#?keyword=validation-private-query']) {
        Object.defineProperty(globalThis, 'window', {configurable: true, value: {location: {hash}}});
        await request('/idaas/session/me');
        assert.equal(seen.at(-1)?.['X-Audit-Route'], undefined);
    }
});
