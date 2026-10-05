import type { Database, Domain, EntityTable, EntityOf, EffectiveAccess, Grant, User, Base, Session, PermissionGroup, SyncTask } from './types.ts';
import { DomainError } from './types.ts';
export const timestamp = () => new Date().toISOString();
export const uid = (prefix: string) => `${prefix}-${globalThis.crypto?.randomUUID?.() || `${Date.now()}-${Math.random().toString(36).slice(2)}`}`;
export function descendants(db: Database, orgId: string): Set<string> {
    const found = new Set<string>();
    const visit = (id: string) => {
        if (found.has(id))
            return;
        found.add(id);
        db.orgs.filter(o => o.parentId === id).forEach(o => visit(o.id));
    };
    visit(orgId);
    return found;
}
export function belongsToOrg(db: Database, user: User, orgId: string, includeChildren: boolean): boolean { const ids = includeChildren ? descendants(db, orgId) : new Set([orgId]); return user.appointments.some(a => ids.has(a.orgId)) || ids.has(user.orgId); }
export function grantIsCurrent(g: Grant, now = new Date()): boolean { return g.status === 'enabled' && new Date(g.startsAt) <= now && (!g.expiresAt || new Date(g.expiresAt) > now); }
export function effectiveAccess(db: Database, userId: string, now = new Date()): EffectiveAccess[] {
    if (db.authoritativeAccess) return db.authoritativeAccess[userId] || [];
    const user = db.users.find(u => u.id === userId);
    if (!user || user.status !== 'enabled' || user.locked || user.kind === 'admin')
        return [];
    const result = new Map<string, EffectiveAccess>();
    for (const g of db.grants) {
        if (g.domain !== user.domain || !grantIsCurrent(g, now))
            continue;
        const app = db.apps.find(a => a.id === g.appId && a.domain === user.domain && a.status === 'enabled');
        if (!app)
            continue;
        let matches = false, label = '';
        if (g.source === 'user') {
            matches = g.subjectId === userId;
            label = '直接授权';
        }
        if (g.source === 'org') {
            const org = db.orgs.find(o => o.id === g.subjectId && o.status === 'enabled');
            matches = Boolean(org) && belongsToOrg(db, user, g.subjectId, g.includeChildren);
            label = `机构 · ${org?.name || '已失效机构'}`;
        }
        if (g.source === 'group') {
            const group = db.groups.find(v => v.id === g.subjectId && v.status === 'enabled');
            matches = Boolean(group?.userIds.includes(userId));
            label = `权限组 · ${group?.name || '已失效权限组'}`;
        }
        if (!matches)
            continue;
        const access = result.get(app.id) || { appId: app.id, roleIds: [], resourceIds: [], sources: [] };
        const roles = db.roles.filter(r => g.roleIds.includes(r.id) && r.status === 'enabled' && r.appId === app.id);
        access.roleIds = [...new Set([...access.roleIds, ...roles.map(r => r.id)])];
        const enabledResource = (id: string, visited = new Set<string>()): boolean => {
            const r = db.resources.find(x => x.id === id && x.appId === app.id && x.status === 'enabled');
            if (!r || visited.has(id))
                return false;
            visited.add(id);
            return !r.parentId || enabledResource(r.parentId, visited);
        };
        access.resourceIds = [...new Set([...access.resourceIds, ...roles.flatMap(r => r.resourceIds).filter(id => enabledResource(id))])];
        access.sources.push({ grantId: g.id, source: g.source, label });
        result.set(app.id, access);
    }
    return [...result.values()];
}
export function assertGrant(db: Database, g: Grant): void {
    const app = db.apps.find(a => a.id === g.appId && a.domain === g.domain);
    if (!app)
        throw new DomainError('应用不存在或不属于当前身份域');
    if (g.status === 'enabled' && app.status !== 'enabled')
        throw new DomainError('只能为已启用应用新增授权');
    if (g.status === 'enabled' && g.roleIds.some(id => !db.roles.some(r => r.id === id && r.appId === g.appId && r.status === 'enabled')))
        throw new DomainError('所选角色已停用，或不属于当前应用');
    if (g.source === 'user') {
        const u = db.users.find(u => u.id === g.subjectId && u.domain === g.domain);
        if (!u)
            throw new DomainError('用户不存在或身份域不匹配');
        if (u.kind === 'admin')
            throw new DomainError('平台管理账号不能获得业务应用访问权限', 'ADMIN_SEPARATION');
    }
    if (g.source === 'org' && g.status === 'enabled' && !db.orgs.some(o => o.id === g.subjectId && o.domain === g.domain && o.status === 'enabled'))
        throw new DomainError('机构不存在或已停用');
    if (g.source === 'group' && !db.groups.some(v => v.id === g.subjectId && v.domain === g.domain))
        throw new DomainError('权限组不存在');
    if (!g.reason.trim())
        throw new DomainError('请填写授权原因');
    if (!Number.isFinite(Date.parse(g.startsAt)))
        throw new DomainError('生效时间无效');
    if (g.expiresAt && (!Number.isFinite(Date.parse(g.expiresAt)) || new Date(g.expiresAt) <= new Date(g.startsAt)))
        throw new DomainError('到期时间必须晚于生效时间');
}
export function validateEntity<K extends EntityTable>(db: Database, table: K, record: EntityOf<K>): void {
    const r = record as Base;
    if (!r.name.trim())
        throw new DomainError('名称不能为空');
    if (r.name.length > 100)
        throw new DomainError('名称不能超过100个字符');
    if (table === 'users') {
        const u = record as User;
        if (u.kind === 'admin' && (u.status !== 'enabled' || u.locked))
            throw new DomainError('内置平台管理账号不可在此停用或锁定');
        if (!/^[A-Za-z][A-Za-z0-9_.-]{2,31}$/.test(u.account))
            throw new DomainError('账号需以字母开头，3–32位字母、数字或 _ . -');
        if (db.users.some(v => v.id !== u.id && v.domain === u.domain && v.account.toLowerCase() === u.account.toLowerCase()))
            throw new DomainError('当前身份域已存在相同账号', 'DUPLICATE');
        if (u.email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(u.email))
            throw new DomainError('请输入有效邮箱');
        if (u.domain === 'workforce' && !db.orgs.some(o => o.id === u.orgId && o.status === 'enabled'))
            throw new DomainError('请选择有效的主职机构');
        if (u.appointments.some(a => !db.orgs.some(o => o.id === a.orgId && o.domain === u.domain)))
            throw new DomainError('任职机构不存在');
        if (u.domain === 'workforce' && u.appointments.filter(a => a.primary).length !== 1)
            throw new DomainError('必须且只能配置一个主职');
    }
    if (table === 'apps') {
        const a = record as Database['apps'][number];
        if (!/^[a-z][a-z0-9-]{2,47}$/.test(a.code))
            throw new DomainError('应用编码为3–48位小写字母、数字或短横线');
        if (db.apps.some(v => v.id !== a.id && v.domain === a.domain && v.code === a.code))
            throw new DomainError('应用编码已存在', 'DUPLICATE');
        for (const text of [a.homepage, ...a.redirectUris.split('\n').filter(Boolean), a.logoutUri].filter(Boolean)) {
            try {
                const u = new URL(text);
                if (u.protocol !== 'https:' && !(u.protocol === 'http:' && ['localhost', '127.0.0.1'].includes(u.hostname)))
                    throw new Error();
                if (u.username || u.password || u.hash || text.includes('*'))
                    throw new Error();
            }
            catch {
                throw new DomainError('URL须为有效HTTPS地址（回环地址可用HTTP），不允许通配符、凭据或片段');
            }
        }
    }
    if (table === 'orgs') {
        const o = record as Database['orgs'][number];
        if (o.parentId === o.id || (o.parentId && descendants(db, o.id).has(o.parentId)))
            throw new DomainError('不能把机构移动到自身或下级机构');
        if (o.parentId && !db.orgs.some(x => x.id === o.parentId && x.domain === o.domain))
            throw new DomainError('上级机构不存在');
        if (db.orgs.some(x => x.id !== o.id && x.code === o.code))
            throw new DomainError('机构编码已存在');
    }
    if (table === 'roles') {
        const role = record as Database['roles'][number];
        if (!db.apps.some(a => a.id === role.appId && a.domain === role.domain))
            throw new DomainError('所属应用不存在');
        if (db.roles.some(x => x.id !== role.id && x.appId === role.appId && x.code === role.code))
            throw new DomainError('该应用下角色编码已存在');
        if (role.resourceIds.some(id => !db.resources.some(x => x.id === id && x.appId === role.appId)))
            throw new DomainError('资源必须属于当前应用');
    }
    if (table === 'resources') {
        const res = record as Database['resources'][number];
        if (db.resources.some(x => x.id !== res.id && x.appId === res.appId && x.code === res.code))
            throw new DomainError('该应用下资源编码已存在');
        if (res.parentId) {
            const parent = db.resources.find(x => x.id === res.parentId);
            if (!parent || parent.appId !== res.appId || parent.id === res.id)
                throw new DomainError('父资源无效');
            let current = parent;
            const visited = new Set([res.id]);
            while (current) {
                if (visited.has(current.id))
                    throw new DomainError('资源层级不能循环');
                visited.add(current.id);
                current = db.resources.find(x => x.id === current.parentId)!;
            }
        }
    }
    if (table === 'grants')
        assertGrant(db, record as Grant);
    if (table === 'groups') {
        const group = record as PermissionGroup;
        if (group.userIds.some(id => !db.users.some(u => u.id === id && u.domain === group.domain && u.kind !== 'admin')))
            throw new DomainError('成员不存在、跨域或属于平台管理账号');
        for (const item of group.items) {
            if (!db.apps.some(a => a.id === item.appId && a.domain === group.domain))
                throw new DomainError('权限组应用身份域不匹配');
            if (item.roleIds.some(id => !db.roles.some(r => r.id === id && r.appId === item.appId)))
                throw new DomainError('权限组角色不属于对应应用');
        }
    }
    if (table === 'syncConfigs') {
        const config = record as Database['syncConfigs'][number];
        if (!db.apps.some(app => app.id === config.appId && app.domain === config.domain))
            throw new DomainError('请选择当前身份域的有效应用');
        try {
            const url = new URL(config.endpoint);
            if (url.protocol !== 'https:' || url.username || url.password)
                throw new Error();
        }
        catch {
            throw new DomainError('同步接口必须是有效的HTTPS地址，不可包含凭据');
        }
        if (!config.entities.length || config.entities.some(x => !['user', 'org'].includes(x)))
            throw new DomainError('请选择用户或机构同步对象');
        if (config.orgIds.some(id => !db.orgs.some(o => o.id === id && o.domain === config.domain)))
            throw new DomainError('同步机构不属于当前身份域');
        if (!(config.timeout >= 1 && config.timeout <= 120))
            throw new DomainError('接口超时时间应在1–120秒之间');
    }
    if (table === 'legalEntities') {
        const entity = record as Database['legalEntities'][number];
        if (entity.domain !== 'public')
            throw new DomainError('法人记录仅属于公众身份域');
        if (!entity.code.trim())
            throw new DomainError('法人标识不能为空');
        if (db.legalEntities.some(x => x.id !== entity.id && x.code.toLowerCase() === entity.code.toLowerCase()))
            throw new DomainError('法人标识已存在');
        const ids = [entity.contactId, ...entity.subAccountIds].filter(Boolean);
        if (ids.some(id => !db.users.some(u => u.id === id && u.domain === 'public')))
            throw new DomainError('经办人和子账号须属于公众身份域');
    }
    if (table === 'catalog') {
        const item = record as Database['catalog'][number];
        if (item.code && db.catalog.some(x => x.id !== item.id && x.domain === item.domain && x.category === item.category && x.code === item.code && (item.category !== 'api-grant' || (x.subjectType === item.subjectType && x.appId === item.appId && x.userId === item.userId))))
            throw new DomainError('当前类别中编码已存在');
    }
}
export function syncGroupGrants(db: Database, group: PermissionGroup): void {
    db.grants = db.grants.filter(g => !(g.source === 'group' && g.subjectId === group.id));
    for (const item of group.items)
        db.grants.push({ id: uid('grant'), domain: group.domain, name: group.name, status: group.status, createdAt: timestamp(), updatedAt: timestamp(), version: 1, source: 'group', subjectId: group.id, appId: item.appId, roleIds: item.roleIds, includeChildren: false, startsAt: timestamp(), expiresAt: null, reason: '权限组关联' });
}
export function assertRemove(db: Database, table: EntityTable, id: string): void {
    if (table === 'orgs' && (db.orgs.some(o => o.parentId === id) || db.users.some(u => u.orgId === id || u.appointments.some(a => a.orgId === id)) || db.grants.some(g => g.source === 'org' && g.subjectId === id)))
        throw new DomainError('机构仍有关联子机构、人员或授权，请先处理关联关系');
    if (table === 'apps' && (db.roles.some(r => r.appId === id) || db.grants.some(g => g.appId === id)))
        throw new DomainError('应用仍有角色或授权，建议停用；清理关联后才能删除');
    if (table === 'roles' && (db.grants.some(g => g.roleIds.includes(id)) || db.groups.some(g => g.items.some(i => i.roleIds.includes(id)))))
        throw new DomainError('角色仍被授权或权限组使用，请先解除引用');
    if (table === 'resources' && (db.roles.some(r => r.resourceIds.includes(id)) || db.resources.some(r => r.parentId === id)))
        throw new DomainError('资源存在子资源或角色引用，请先解除关联');
    if (table === 'users') {
        const u = db.users.find(u => u.id === id);
        if (u?.kind === 'admin')
            throw new DomainError('平台内置管理账号不可删除');
        if (db.grants.some(g => g.source === 'user' && g.subjectId === id) || db.groups.some(g => g.userIds.includes(id)))
            throw new DomainError('用户仍有授权或权限组引用，请先停用或解除关联');
    }
    if (table === 'syncConfigs' && db.tasks.some(t => t.configId === id && ['running', 'pending'].includes(t.status)))
        throw new DomainError('存在正在执行的任务，不能删除配置');
}
export function safeSnapshot(value: unknown): string { return JSON.stringify(value, (k, v) => /password|secret|token|credential/i.test(k) ? '[已脱敏]' : v, 2) || '—'; }
export function makeBase(name: string, domain: Domain, prefix = 'id'): Base { const time = timestamp(); return { id: uid(prefix), name, domain, status: 'enabled', createdAt: time, updatedAt: time, version: 1 }; }
export function canEdit(session: Session | null, section: string): boolean {
    if (!session)
        return false;
    if (session.editableTables) return session.editableTables.includes(section);
    if (session.role === 'admin')
        return true;
    if (session.role === 'auditor')
        return false;
    if (session.role === 'appmanager')
        return ['apps', 'roles', 'resources'].includes(section);
    return ['users', 'orgs'].includes(section);
}
export function accessibleAppIds(db: Database, session: Session): string[] { return session.allowedAppIds ?? (session.role === 'appmanager' ? (session.domain === 'workforce' ? ['a1'] : ['a10']) : db.apps.filter(a => a.domain === session.domain).map(a => a.id)); }
export function allowedUser(db: Database, session: Session, user: User): boolean { return user.domain === session.domain && (session.scopeMode === 'tenant' ? true : session.orgIds ? session.orgIds.some(id => belongsToOrg(db, user, id, true)) : session.role !== 'orgadmin' || belongsToOrg(db, user, 'o1', true)); }
export function taskCounts(task: SyncTask) { return { total: task.items.length, success: task.items.filter(i => i.status === 'success').length, failed: task.items.filter(i => i.status === 'failed').length, pending: task.items.filter(i => i.status === 'pending').length }; }
export function advanceTask(task: SyncTask): SyncTask {
    if (task.status !== 'running')
        return task;
    const updated = structuredClone(task);
    const remaining = updated.items.filter(i => i.status === 'pending').slice(0, 5);
    for (const item of remaining) {
        const index = updated.items.findIndex(i => i.id === item.id && i.type === item.type);
        const fail = updated.attempt === 1 && index % 7 === 0;
        updated.items[index] = { ...item, status: fail ? 'failed' : 'success', reason: fail ? '下游请求超时（HTTP 504），可重试' : '' };
    }
    const counts = taskCounts(updated);
    updated.progress = counts.total ? Math.round((counts.success + counts.failed) / counts.total * 100) : 100;
    if (!counts.pending)
        updated.status = counts.failed ? 'partial' : 'success';
    updated.updatedAt = timestamp();
    updated.version++;
    return updated;
}
