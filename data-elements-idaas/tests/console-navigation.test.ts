import test from 'node:test';
import assert from 'node:assert/strict';
import { canViewDomain, consolePages, consoleHome } from '../src/app/console-navigation.ts';
import type { Session } from '../src/domain/types.ts';

const session: Session = { userId: 'operator', realm: 'platform', username: 'operator', name: '操作员', role: 'admin', domain: 'workforce', permissions: ['users:read'], globalPermissions: ['users:read'], navigationPermissions: { workforce: { permissions: ['users:read'], globalPermissions: ['users:read'] }, public: { permissions: ['apps:read'], globalPermissions: [] } } };
test('跨域菜单发现按目标域配对权限展示，局部应用权限不扩大为全局配置权限', () => {
    assert.equal(canViewDomain(session, 'public', 'apps'), true);
    assert.equal(canViewDomain(session, 'public', 'persons'), false);
    assert.equal(canViewDomain(session, 'public', 'app-groups'), false);
    const entries = consolePages(session);
    assert.ok(entries.some(page => page.id === 'public:apps' && page.group === '公众身份'));
    assert.ok(entries.some(page => page.id === 'workforce:organization'));
    assert.equal(entries.length, new Set(entries.map(page => page.id)).size);
    assert.deepEqual(session.permissions, ['users:read']);
});
test('角色名称及旧响应没有授权另一身份域，强制改密只允许进入个人安全流程', () => {
    const old = { ...session, navigationPermissions: undefined };
    assert.equal(canViewDomain(old, 'public', 'entities'), false);
    assert.ok(consolePages(old).every(page => page.domain === 'workforce'));
    assert.ok(consolePages({ ...session, mustChangePassword: true }).every(page => page.key === 'overview'));
});
test('只有公众管理范围的操作员可由统一登录入口进入公众总览', () => {
    const publicOnly = { ...session, permissions: [], globalPermissions: [], navigationPermissions: { public: { permissions: ['public:read'], globalPermissions: ['public:read'] } } };
    assert.equal(consoleHome(publicOnly), 'public');
    assert.ok(consolePages({ ...publicOnly, domain: 'public', permissions: ['public:read'], globalPermissions: ['public:read'] }).some(page => page.id === 'public:entities'));
    const publicSettings = { ...publicOnly, domain: 'public' as const, permissions: ['public:read', 'settings:read'], globalPermissions: ['public:read', 'settings:read'], navigationPermissions: { public: { permissions: ['public:read', 'settings:read'], globalPermissions: ['public:read', 'settings:read'] } } };
    assert.ok(!consolePages(publicSettings).some(page => page.key.startsWith('system/')));
});
