import type { Database, Domain, EntityOf, EntityTable, Session, Settings, User, SyncTask } from '../domain/types.ts';
import { DomainError } from '../domain/types.ts';
import { createSeed } from '../domain/seed.ts';
import { normalizeLegacyPresentation } from '../domain/presentation.ts';
import { assertRemove, validateEntity, timestamp, makeBase, safeSnapshot, syncGroupGrants, advanceTask, descendants, canEdit, accessibleAppIds, allowedUser } from '../domain/engine.ts';
const KEY = 'iam.frontend.mock.v2';
const SESSION_KEY = 'iam.frontend.session';
const listeners = new Set<() => void>();
const sessionListeners = new Set<() => void>();
let storageWarning = '';
function load(): Database {
    try {
        const raw = localStorage.getItem(KEY);
        if (!raw)
            return createSeed();
        const db = JSON.parse(raw) as Database;
        if (db.schema !== 1 || !Array.isArray(db.users) || !Array.isArray(db.apps) || !db.settings || !Array.isArray(db.grants) || !Array.isArray(db.catalog))
            throw new Error('schema');
        return normalizeLegacyPresentation(db);
    }
    catch {
        storageWarning = '无法读取已保存的数据，已载入初始配置。请检查浏览器存储设置。';
        return createSeed();
    }
}
let database: Database = load();
let session: Session | null = null;
try {
    const v = JSON.parse(sessionStorage.getItem(SESSION_KEY) || 'null');
    session = v && ['admin', 'auditor', 'appmanager', 'orgadmin'].includes(v.role) && ['workforce', 'public'].includes(v.domain) ? v : null;
}
catch {
    session = null;
}
let failNext = false;
const persist = () => {
    try {
        localStorage.setItem(KEY, JSON.stringify(database));
    }
    catch {
        storageWarning = '浏览器存储空间不足；当前修改仅保留在内存中。';
    }
    listeners.forEach(fn => fn());
};
export function getDatabase() { return database; }
export function getStorageWarning() { return storageWarning; }
export function setSession(value: Session | null) { session = value; value ? sessionStorage.setItem(SESSION_KEY, JSON.stringify(value)) : sessionStorage.removeItem(SESSION_KEY); sessionListeners.forEach(fn => fn()); }
export function setFailure() { failNext = true; }
export function resetData() { database = createSeed(); storageWarning = ''; persist(); }
const pause = async () => {
    await new Promise(r => setTimeout(r, 220));
    if (failNext) {
        failNext = false;
        throw new DomainError('请求暂时无法完成，输入已保留，请重试。', 'MOCK_NETWORK_ERROR');
    }
};
function authorize(table: EntityTable, record?: {
    id?: string;
    domain?: Domain;
    appId?: string;
    orgId?: string;
    parentId?: string | null;
}): void {
    if (!session)
        throw new DomainError('登录已过期', 'UNAUTHENTICATED');
    const section = table === 'catalog' ? 'system' : table;
    if (!canEdit(session, section))
        throw new DomainError('当前账号无此修改权限', 'FORBIDDEN');
    if (record?.domain && record.domain !== session.domain)
        throw new DomainError('不能修改其他身份域数据', 'FORBIDDEN');
    if (session.role === 'appmanager') {
        const appId = table === 'apps' ? record?.id : record?.appId;
        if (appId && !accessibleAppIds(database, session).includes(appId))
            throw new DomainError('应用不在当前管理范围', 'FORBIDDEN');
        if (table === 'apps' && !database.apps.some(a => a.id === record?.id))
            throw new DomainError('当前应用管理员不能新建应用', 'FORBIDDEN');
    }
    if (session.role === 'orgadmin') {
        const scope = descendants(database, 'o1');
        const check = table === 'orgs' ? record?.id : record?.orgId;
        if (check && !scope.has(check) && database[table].some(x => x.id === record?.id))
            throw new DomainError('对象不在当前机构管理范围', 'FORBIDDEN');
        if (table === 'orgs' && record?.parentId && !scope.has(record.parentId))
            throw new DomainError('上级机构超出当前管理范围', 'FORBIDDEN');
        if (table === 'users' && record?.orgId && !scope.has(record.orgId))
            throw new DomainError('主职机构超出管理范围', 'FORBIDDEN');
    }
}
function appendLog(db: Database, domain: Domain, module: string, action: string, target: string, before: unknown, after: unknown, type: 'operation' | 'login' | 'api' = 'operation') {
    db.logs.unshift({ ...makeBase(`${action} · ${target}`, domain, 'log'), type, actor: session?.username || 'system', module, action, target, result: '成功', ip: '192.0.2.10', appId: '', traceId: `trace_${Date.now()}`, before: safeSnapshot(before), after: safeSnapshot(after) });
    db.logs = db.logs.slice(0, 4000);
}
export const api = {
    async save<K extends EntityTable>(table: K, record: EntityOf<K>, action = '保存'): Promise<void> {
        await pause();
        authorize(table, record);
        const next = structuredClone(database);
        const list = next[table] as EntityOf<K>[];
        const index = list.findIndex(v => v.id === record.id);
        const old = index < 0 ? null : list[index];
        if (old && old.version !== record.version)
            throw new DomainError('内容已被其他操作更新，请刷新后重试', 'CONFLICT');
        validateEntity(next, table, record);
        const value = { ...record, name: record.name.trim(), version: (old?.version || 0) + 1, updatedAt: timestamp() } as EntityOf<K>;
        if (index < 0)
            list.unshift(value);
        else
            list[index] = value;
        if (table === 'groups')
            syncGroupGrants(next, value as Database['groups'][number]);
        if (table === 'users' && old) {
            const before = old as User;
            const after = value as User;
            if (before.orgId !== after.orgId || before.post !== after.post)
                after.history.unshift({ time: timestamp(), text: `任职变更：${next.orgs.find(o => o.id === after.orgId)?.name || '—'} / ${after.post}` });
        }
        appendLog(next, record.domain, table, action, record.name, old, value);
        database = next;
        persist();
    },
    async remove<K extends EntityTable>(table: K, id: string): Promise<void> {
        await pause();
        const r = database[table].find(r => r.id === id);
        if (!r)
            throw new DomainError('记录已不存在');
        authorize(table, r);
        assertRemove(database, table, id);
        const next = structuredClone(database);
        (next[table] as Database[K]) = next[table].filter(r => r.id !== id) as Database[K];
        if (table === 'groups')
            next.grants = next.grants.filter(g => !(g.source === 'group' && g.subjectId === id));
        appendLog(next, r.domain, table, '删除', r.name, r, null);
        database = next;
        persist();
    },
    async settings(domain: Domain, values: Partial<Settings>) {
        await pause();
        if (session?.role !== 'admin' || session.domain !== domain)
            throw new DomainError('没有配置修改权限', 'FORBIDDEN');
        const next = structuredClone(database);
        const old = next.settings[domain];
        next.settings[domain] = { ...old, ...values };
        appendLog(next, domain, '系统设置', '保存配置', next.settings[domain].title, old, next.settings[domain]);
        database = next;
        persist();
    },
    async login(username: string, password: string, domain: Domain): Promise<Session> {
        await pause();
        const accounts: Record<string, {
            name: string;
            role: Session['role'];
        }> = { admin: { name: '平台管理员', role: 'admin' }, auditor: { name: '审计查看者', role: 'auditor' }, appmanager: { name: '应用管理员', role: 'appmanager' }, orgadmin: { name: '机构管理员', role: 'orgadmin' } };
        const match = accounts[username];
        if (!match || password !== 'Review@2026')
            throw new DomainError('账号或密码不正确');
        const s = { username, ...match, domain };
        setSession(s);
        const next = structuredClone(database);
        appendLog(next, domain, '统一认证', '管理员登录', username, null, { role: s.role }, 'login');
        database = next;
        persist();
        return s;
    },
    async publicRegister(values: {
        name: string;
        account: string;
        email: string;
        phone: string;
    }, legalName?: string) {
        await pause();
        const next = structuredClone(database);
        const u: User = { ...makeBase(values.name, 'public', 'person'), ...values, orgId: '', post: '', kind: 'citizen', locked: false, appointments: [], verified: false, history: [{ time: timestamp(), text: '注册账户，待完成身份核验' }] };
        validateEntity(next, 'users', u);
        next.users.unshift(u);
        if (legalName)
            next.legalEntities.unshift({ ...makeBase(legalName, 'public', 'legal'), code: `ENT-${Date.now()}`, contact: values.name, contactId: u.id, phone: values.phone, verified: false, subAccountIds: [u.id] });
        appendLog(next, 'public', '公众注册', '账户注册', values.name, null, { account: u.account, verified: false });
        database = next;
        persist();
    },
    async recordEvent(domain: Domain, action: string, target: string, detail: unknown) { await pause(); const next = structuredClone(database); appendLog(next, domain, '账户服务', action, target, null, detail); database = next; persist(); },
    async startTask(configId: string, mode = 'full', selectedIds?: string[]) {
        await pause();
        authorize('tasks');
        const c = database.syncConfigs.find(v => v.id === configId);
        if (!c || c.status !== 'enabled')
            throw new DomainError('同步配置不存在或已停用');
        if (c.domain !== session?.domain)
            throw new DomainError('身份域不匹配');
        const next = structuredClone(database);
        const scope = new Set(c.orgIds.flatMap(id => [...descendants(next, id)]));
        const items: SyncTask['items'] = [...(c.entities.includes('user') ? next.users.filter(u => u.domain === c.domain && u.kind !== 'admin' && (!c.orgIds.length || scope.has(u.orgId))).map(u => ({ id: u.id, name: u.name, type: 'user' as const, status: 'pending' as const, reason: '' })) : []), ...(c.entities.includes('org') ? next.orgs.filter(o => o.domain === c.domain && (!c.orgIds.length || scope.has(o.id))).map(o => ({ id: o.id, name: o.name, type: 'org' as const, status: 'pending' as const, reason: '' })) : [])];
        const task: SyncTask = { ...makeBase(`${c.name} · ${mode === 'full' ? '全量' : '增量'}任务`, c.domain, 'task'), status: 'running', configId, appId: c.appId, mode, progress: 0, items: selectedIds ? items.filter(i => selectedIds.includes(i.id)) : items, attempt: 1 };
        next.tasks.unshift(task);
        appendLog(next, c.domain, '同步管理', '提交同步任务', task.name, null, { count: task.items.length });
        database = next;
        persist();
        return task.id;
    },
    async retryTask(id: string) {
        await pause();
        authorize('tasks');
        const next = structuredClone(database);
        const t = next.tasks.find(t => t.id === id);
        if (!t || t.domain !== session?.domain)
            throw new DomainError('任务不存在');
        if (!['partial', 'failed', 'cancelled'].includes(t.status))
            throw new DomainError('当前任务不可重试');
        t.items = t.items.map(i => i.status === 'success' ? i : { ...i, status: 'pending', reason: '' });
        t.attempt++;
        t.status = 'running';
        const done = t.items.filter(i => i.status === 'success').length;
        t.progress = t.items.length ? Math.round(done / t.items.length * 100) : 100;
        appendLog(next, t.domain, '同步管理', '重试未成功项', t.name, null, { attempt: t.attempt });
        database = next;
        persist();
    },
    async cancelTask(id: string) {
        await pause();
        authorize('tasks');
        const next = structuredClone(database);
        const t = next.tasks.find(t => t.id === id);
        if (!t || t.domain !== session?.domain)
            throw new DomainError('任务不存在');
        if (t.status !== 'running')
            throw new DomainError('只有执行中的任务可以中止');
        t.status = 'cancelled';
        appendLog(next, t.domain, '同步管理', '中止同步任务', t.name, null, { completed: t.items.filter(i => i.status === 'success').length });
        database = next;
        persist();
    }
};
export function tickTasks() {
    if (!database.tasks.some(t => t.status === 'running'))
        return;
    const next = structuredClone(database);
    next.tasks = next.tasks.map(advanceTask);
    database = next;
    persist();
}
export function subscribeDatabase(callback: () => void) { listeners.add(callback); return () => { listeners.delete(callback); }; }
export function subscribeSession(callback: () => void) { sessionListeners.add(callback); return () => { sessionListeners.delete(callback); }; }
export function getSession(): Session | null { return session; }
