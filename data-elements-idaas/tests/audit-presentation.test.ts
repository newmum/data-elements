import test from 'node:test';
import assert from 'node:assert/strict';
import { auditActionLabel, auditActorLabel, auditTargetLabel } from '../src/domain/audit-presentation.ts';
import { createSeed } from '../src/domain/seed.ts';

test('审计动作以中文业务名称展示，未知编码不直接暴露在摘要中', () => {
    assert.equal(auditActionLabel('applications:bind'), '核验应用连接');
    assert.equal(auditActionLabel('subjects:save'), '保存人员资料');
    assert.equal(auditActionLabel('profile:domain'), '切换身份域');
    assert.equal(auditActionLabel('unknown:opaque-code'), '其他业务操作');
});

test('审计摘要使用当前目录名称，无法解析的对象不直接显示长 ID', () => {
    const db = createSeed();
    const app = db.apps[0];
    assert.equal(auditTargetLabel({ action: 'applications:bind', target: app.id }, db), app.name);
    assert.equal(auditTargetLabel({ action: 'settings:save', target: '2084109831682699264' }, db), '相关平台设置');
    assert.equal(auditTargetLabel({ action: 'profile:domain', target: '2084109831682699264', domain: 'public' }, db), '公众身份域');
    assert.equal(auditTargetLabel({ action: 'applications:save', target: '业务应用名称' }, db), '业务应用名称');
});

test('操作人优先使用审计接口解析的名称，其次是当前会话与已加载目录', () => {
    const db = createSeed();
    assert.equal(auditActorLabel('opaque-operator-id', db, null, '张管理员'), '张管理员');
    assert.equal(auditActorLabel('current-id', db, { userId: 'current-id', name: '当前管理员' }), '当前管理员');
    assert.equal(auditActorLabel('2084109831682699264', db), '操作账号（名称未提供）');
});
