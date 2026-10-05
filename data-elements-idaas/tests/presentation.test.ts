import test from 'node:test';
import assert from 'node:assert/strict';
import { createSeed } from '../src/domain/seed.ts';
import { normalizeLegacyPresentation } from '../src/domain/presentation.ts';
import { effectiveAccess } from '../src/domain/engine.ts';
import { platformTitle } from '../src/domain/branding.ts';
const now = new Date('2026-09-27T10:00:00.000Z');
function fixture() {
    const db = createSeed(now);
    db.logs[0].action = '本地评审登录';
    db.logs[0].name = `${db.logs[0].action} · ${db.logs[0].target}`;
    db.logs[0].module = '前端评审';
    db.logs[0].location = '本地样例网络';
    db.catalog.find(x => x.id === 'workforce-admin')!.description = '本地评审管理账号';
    db.tasks[0].items[0].reason = '模拟下游请求超时（HTTP 504），可重试';
    db.users[0].history[0].text = '本地模拟注册，未进行实名核验';
    db.settings.workforce.encryptionPolicy = '待后端接入密钥管理服务';
    db.legalEntities[0].code = 'TEST-ORG-0001';
    return db;
}
test('V2.1: built-in labels upgrade without changing row IDs or counts', () => {
    const db = fixture();
    const before = [db.users, db.apps, db.orgs, db.grants, db.logs, db.catalog, db.tasks].map(a => a.map(x => x.id));
    normalizeLegacyPresentation(db);
    assert.deepEqual([db.users, db.apps, db.orgs, db.grants, db.logs, db.catalog, db.tasks].map(a => a.map(x => x.id)), before);
    assert.equal(db.logs[0].action, '管理员登录');
    assert.equal(db.logs[0].module, '账户服务');
    assert.equal(db.logs[0].location, '企业网络');
    assert.equal(db.catalog.find(x => x.id === 'workforce-admin')!.description, '平台管理账号');
    assert.equal(db.legalEntities[0].code, 'ENT-0001');
});
test('V2.1: presentation upgrade preserves effective access, grant timestamps and resources', () => {
    const db = fixture(), grants = structuredClone(db.grants), resources = structuredClone(db.resources);
    const result = db.users.map(u => effectiveAccess(db, u.id, now));
    normalizeLegacyPresentation(db);
    assert.deepEqual(db.grants, grants); assert.deepEqual(db.resources, resources);
    assert.deepEqual(db.users.map(u => effectiveAccess(db, u.id, now)), result);
});
test('V2.1: audit evidence, user names and free-form input are not rewritten', () => {
    const db = fixture();
    db.users[2].name = 'Mock 服务团队';
    db.apps[0].description = '用于演示业务流程的正式应用';
    db.logs[0].before = '本地评审登录'; db.logs[0].after = '真实历史快照';
    db.legalEntities[1].code = 'CUSTOM-ENTITY';
    normalizeLegacyPresentation(db);
    assert.equal(db.users[2].name, 'Mock 服务团队');
    assert.equal(db.apps[0].description, '用于演示业务流程的正式应用');
    assert.equal(db.logs[0].before, '本地评审登录'); assert.equal(db.logs[0].after, '真实历史快照');
    assert.equal(db.legalEntities[1].code, 'CUSTOM-ENTITY');
});
test('V2.1: label upgrade is idempotent', () => {
    const db = normalizeLegacyPresentation(fixture()), before = structuredClone(db);
    normalizeLegacyPresentation(db); assert.deepEqual(db, before);
});
test('V2.1: new fixture text contains no development or demonstration wording', () => {
    const db = createSeed(now);
    assert.doesNotMatch(JSON.stringify(db), /mock|演示|评审|模拟|待后端|TEST-ORG/i);
});
test('V2.1: failed task status and failure evidence survive copy changes', () => {
    const db = fixture(); const before = db.tasks.map(t => ({status:t.status, progress:t.progress,items:t.items.map(i=>i.status)}));
    normalizeLegacyPresentation(db);
    assert.equal(db.tasks[0].items[0].reason, '下游请求超时（HTTP 504），可重试');
    assert.deepEqual(db.tasks.map(t => ({status:t.status,progress:t.progress,items:t.items.map(i=>i.status)})), before);
});

test('卓鉴命名迁移保留身份域数据、授权与审计快照，重复加载结果不变', () => {
    const db = createSeed(now);
    db.settings.workforce.title = '统一身份管理平台';
    db.settings.public.title = '统一公众身份管理平台';
    db.logs[0].before = '统一身份管理平台';
    db.logs[0].after = '统一公众身份管理平台';
    const before = structuredClone(db);
    normalizeLegacyPresentation(db);
    assert.equal(db.settings.workforce.title, platformTitle);
    assert.equal(db.settings.public.title, platformTitle);
    before.settings.workforce.title = platformTitle;
    before.settings.public.title = platformTitle;
    assert.deepEqual(db, before);
    normalizeLegacyPresentation(db);
    assert.deepEqual(db, before);
});

test('卓鉴命名迁移保留用户自定义标题，包括含旧名称的自由文本', () => {
    const db = createSeed(now);
    db.settings.workforce.title = '公司统一身份管理平台';
    db.settings.public.title = '公众服务中心';
    const before = structuredClone(db);
    normalizeLegacyPresentation(db);
    assert.deepEqual(db, before);
});
