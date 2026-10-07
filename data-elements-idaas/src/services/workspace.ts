import { normalizeSystemDescription, normalizeSystemTitle, platformTitle } from '../domain/branding.ts';
import type { Database, Domain, EntityOf, EntityTable, Session, Settings } from '../domain/types.ts';
import { DomainError } from '../domain/types.ts';
import { request } from './http.ts';
import { rememberChallenge } from './identity.ts';

const TOKEN_KEY = 'iam.frontend.backend.token';
const listeners = new Set<() => void>();
const sessionListeners = new Set<() => void>();
const stateListeners = new Set<() => void>();
const userOperationListeners = new Set<() => void>();
const tablePaths: Partial<Record<EntityTable, string>> = { users: 'users', orgs: 'orgs', apps: 'applications', roles: 'roles', resources: 'resources', grants: 'grants', groups: 'permission-groups', legalEntities: 'public-entities' };
const mutationIds = new Map<string, string>();
async function requestIdFor(value: unknown): Promise<{ key: string; requestId: string }> {
    const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(value)));
    const key = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('');
    const requestId = mutationIds.get(key) || crypto.randomUUID();
    mutationIds.set(key, requestId);
    return { key, requestId };
}
const defaultSettings = (): Settings => ({ title: platformTitle, subtitle: '统一身份 · 安全访问 · 高效协同', logoUrl: '', minLength: 0, lockThreshold: 0, lockMinutes: 0, forceChange: false, mfaRequired: false, passwordDays: 0, passwordBlacklist: '', accessTtl: 0, refreshTtl: 0, idleMinutes: 0, singleLogout: false, allowConcurrent: false, encryptionEnabled: false, encryptionPolicy: '', maskedFields: ['phone', 'email'], apiWhitelist: '', ipBlacklist: '', apiRateLimit: 0 });
export function emptyDatabase(): Database { return { schema: 1, users: [], orgs: [], apps: [], roles: [], resources: [], groups: [], grants: [], syncConfigs: [], tasks: [], logs: [], catalog: [], legalEntities: [], settings: { workforce: defaultSettings(), public: defaultSettings() } }; }
let database = emptyDatabase();
let session: Session | null = null;
let settingVersions: Record<string, number> = {};
let token = '';
try { token = sessionStorage.getItem(TOKEN_KEY) || ''; } catch { /* An unavailable browser store never grants a session. */ }
export interface WorkspaceState { status: 'idle' | 'loading' | 'ready' | 'error'; error: string; }
let state: WorkspaceState = { status: token ? 'loading' : 'idle', error: '' };
let pending: Promise<void> | undefined;
let revision = 0;
let domainQueue: Promise<unknown> = Promise.resolve();
const domainRequests = new Map<Domain, Promise<Session>>();
function emit() { listeners.forEach(fn => fn()); sessionListeners.forEach(fn => fn()); stateListeners.forEach(fn => fn()); }
function setState(status: WorkspaceState['status'], error = '') { state = { status, error }; stateListeners.forEach(fn => fn()); }
function retainToken(value: string) { token = value; try { value ? sessionStorage.setItem(TOKEN_KEY, value) : sessionStorage.removeItem(TOKEN_KEY); } catch { /* This tab keeps the live session in memory only. */ } }
function clearSession() { revision++; domainRequests.clear(); retainToken(''); session = null; mutationIds.clear(); database = emptyDatabase(); state = { status: 'idle', error: '' }; emit(); }
function validateSession(value: Session): Session {
    if (!value?.userId || !['workforce', 'public'].includes(value.domain) || !['admin', 'auditor', 'appmanager', 'orgadmin'].includes(value.role) || (value.realm !== 'platform' && !value.tenantId)) throw new DomainError('当前会话资料不完整，请重新登录。', 'INVALID_SESSION');
    return value;
}
export interface Bootstrap { session: Session; database: Database; capabilities: string[]; settingVersions?: Record<string, number>; }
function acceptBootstrap(value: Bootstrap) {
    const nextSession = validateSession(value?.session);
    const incoming = value?.database;
    const keys = ['users', 'orgs', 'apps', 'roles', 'resources', 'groups', 'grants', 'syncConfigs', 'tasks', 'logs', 'catalog', 'legalEntities'] as const;
    if (incoming?.schema !== 1 || keys.some(key => !Array.isArray(incoming[key]))) throw new DomainError('工作区数据不完整，请刷新重试。', 'INVALID_RESPONSE');
    const empty = emptyDatabase();
    database = { ...incoming, settings: { workforce: { ...empty.settings.workforce, ...incoming.settings?.workforce, title: normalizeSystemTitle(incoming.settings?.workforce?.title), subtitle: normalizeSystemDescription(incoming.settings?.workforce?.subtitle) }, public: { ...empty.settings.public, ...incoming.settings?.public, title: normalizeSystemTitle(incoming.settings?.public?.title), subtitle: normalizeSystemDescription(incoming.settings?.public?.subtitle) } } };
    session = { ...nextSession, capabilities: value.capabilities || nextSession.capabilities || [] };
    settingVersions = value.settingVersions || {};
    state = { status: 'ready', error: '' };
    emit();
}
async function authenticated<T>(path: string, body?: unknown): Promise<T> {
    const currentToken = token;
    if (!currentToken) throw new DomainError('请先登录。', 'UNAUTHENTICATED');
    try { return await request<T>(path, { token: currentToken, method: body === undefined ? 'GET' : 'POST', body }); }
    catch (error) { if (error instanceof DomainError && error.code === 'UNAUTHENTICATED' && token === currentToken) clearSession(); throw error; }
}
export async function refreshWorkspace(keepMounted = false): Promise<void> {
    const current = revision;
    // A write may refresh in place; login and realm changes must still hide old data.
    if (!keepMounted || state.status !== 'ready') setState('loading');
    try { const value = await authenticated<Bootstrap>('/idaas/workspace/bootstrap'); if (current === revision) acceptBootstrap(value); }
    catch (error) { if (current === revision) setState('error', error instanceof Error ? error.message : '工作区加载失败'); throw error; }
}
export function initializeWorkspace(): Promise<void> {
    if (!token || state.status === 'ready') return Promise.resolve();
    if (pending) return pending;
    const current = revision;
    pending = (async () => {
        setState('loading');
        try { validateSession(await authenticated<Session>('/idaas/session/me')); if (current === revision) await refreshWorkspace(); }
        catch (error) { if (current === revision) setState('error', error instanceof Error ? error.message : '无法恢复会话'); }
    })().finally(() => { pending = undefined; });
    return pending;
}
export interface TenantOption { tenantId: string; tenantName: string; tenantCode?: string; }
export interface UserOperation { requestId: string; userId: string; operationType: string; status: string; errorCode?: string | null; createdTime: string; }
export async function loginTenants(): Promise<TenantOption[]> {
    const rows = await request<Array<TenantOption & { tid?: string; name?: string; id?: string }>>('/sym/tenant/login-options');
    if (!Array.isArray(rows)) throw new DomainError('无法读取登录租户，请稍后重试。', 'INVALID_RESPONSE');
    return rows.map(row => ({ tenantId: String(row.tenantId || row.tid || row.id || ''), tenantName: row.tenantName || row.name || '', tenantCode: row.tenantCode })).filter(row => row.tenantId && row.tenantName);
}
export async function loginBrand(): Promise<Pick<Settings, 'title' | 'subtitle' | 'logoUrl'>> {
    const value = await request<Pick<Settings, 'title' | 'subtitle' | 'logoUrl'>>('/idaas/auth/public-settings');
    return { ...value, title: normalizeSystemTitle(value.title), subtitle: normalizeSystemDescription(value.subtitle) };
}
function unavailable(): never { throw new DomainError('当前服务尚未开通此功能。', 'CAPABILITY_UNAVAILABLE'); }
export function hasCapability(name: string): boolean { return Boolean(session?.capabilities?.includes(name)); }
export const api = {
    async login(username: string, password: string, domain: Domain, tenantId?: string): Promise<Session> {
        const result = await request<{ token: string; session: Session; mfaRequired?: boolean; challenge?: string }>('/idaas/auth/login', { method: 'POST', body: { username, password, domain: 'workforce', tenantId } });
        if (result.mfaRequired && result.challenge) { rememberChallenge({ challenge: result.challenge, realm: 'platform', domain }); throw new DomainError('请完成动态口令验证。', 'MFA_REQUIRED'); }
        validateSession(result.session);
        if (!result.token) throw new DomainError('登录响应缺少会话凭据。', 'INVALID_SESSION');
        revision++; domainRequests.clear(); retainToken(result.token);
        session = result.session;
        await refreshWorkspace();
        if (domain !== session!.domain) await api.changeDomain(domain);
        return session!;
    },
    changeDomain(domain: Domain): Promise<Session> {
        const existing = domainRequests.get(domain);
        if (existing) return existing;
        const currentToken = token;
        const queued = domainQueue.catch(() => {}).then(async () => {
            if (!session) throw new DomainError('请先登录。', 'UNAUTHENTICATED');
            if (token !== currentToken) throw new DomainError('会话已更新，请重新进入此功能。', 'SESSION_CHANGED');
            if (session.domain === domain) return session;
            const current = ++revision;
            setState('loading');
            try {
                const value = await authenticated<Bootstrap>('/idaas/session/domain', { domain });
                if (current !== revision) throw new DomainError('会话已更新，请重新进入此功能。', 'SESSION_CHANGED');
                acceptBootstrap(value);
                return session!;
            } catch (error) {
                if (current === revision && session) setState('ready');
                throw error;
            }
        });
        domainQueue = queued;
        domainRequests.set(domain, queued);
        const complete = () => { if (domainRequests.get(domain) === queued) domainRequests.delete(domain); };
        void queued.then(complete, complete);
        return queued;
    },
    async logout() {
        const currentToken = token;
        const revocation = currentToken ? request('/idaas/auth/logout', { token: currentToken, method: 'POST', body: {} }) : Promise.resolve();
        clearSession();
        try { await revocation; }
        catch (error) {
            // An already expired credential cannot keep this tab signed in.
            if (error instanceof DomainError && error.code === 'UNAUTHENTICATED') return;
            throw error;
        }
    },
    async userOperations(): Promise<UserOperation[]> {
        if (!session?.editableTables?.includes('users')) return unavailable();
        const rows = await authenticated<UserOperation[]>('/idaas/users/operations');
        if (!Array.isArray(rows) || rows.some(row => !row || typeof row.requestId !== 'string')) throw new DomainError('待恢复的人员操作暂时无法读取，请重试。', 'INVALID_RESPONSE');
        return rows;
    },
    async retryUserOperation(requestId: string): Promise<void> {
        if (!session?.editableTables?.includes('users')) return unavailable();
        if (!requestId.trim()) throw new DomainError('请选择需要恢复的人员操作。');
        await authenticated('/idaas/users/retry', { requestId });
        await refreshWorkspace();
    },
    async save<K extends EntityTable>(table: K, record: EntityOf<K>, action = '保存'): Promise<string> {
        const catalog = table === 'catalog' ? record as Database['catalog'][number] : null;
        const directoryCatalog = catalog && ['line', 'dictionary', 'extension'].includes(catalog.category);
        const path = catalog?.category === 'app-group' ? 'application-groups' : directoryCatalog ? catalog.category === 'extension' ? 'field-definitions' : 'dictionaries' : tablePaths[table];
        const editableKey = catalog?.category === 'app-group' ? 'app-groups' : directoryCatalog ? 'directory' : table;
        if (!path || !session?.editableTables?.includes(editableKey)) return unavailable();
        const creating = !database[table].some(row => row.id === record.id);
        if (creating && session.realm === 'platform' && (table === 'users' || table === 'orgs') && record.id.length > 32) {
            const digest = await crypto.subtle.digest('SHA-256', new TextEncoder().encode(record.id));
            const id = Array.from(new Uint8Array(digest), byte => byte.toString(16).padStart(2, '0')).join('').slice(0, 32);
            record = { ...record, id };
        }
        const mutation = await requestIdFor({ tenantId: session.tenantId, table, record, action, creating });
        let saved: { id?: string } | undefined;
        try { saved = await authenticated<{ id?: string }>(`/idaas/${path}/save`, { record, action, creating, requestId: mutation.requestId }); }
        catch (error) { if (table === 'users') userOperationListeners.forEach(fn => fn()); throw error; }
        mutationIds.delete(mutation.key);
        await refreshWorkspace(true);
        return saved?.id || record.id;
    },
    async batchDisableUsers(entries: { id: string; version: number }[], domain: Domain): Promise<number> {
        if (!session?.editableTables?.includes('users')) return unavailable();
        if (!entries.length || entries.length > 100 || new Set(entries.map(row => row.id)).size !== entries.length)
            throw new DomainError('每次请选择 1 至 100 个不同的用户。');
        const result = await authenticated<{ count: number }>('/idaas/users/batch-disable', {
            domain, entries, requestId: crypto.randomUUID(),
        });
        await refreshWorkspace();
        return result.count;
    },
    async importUsers(rows: { name: string; account: string; email: string; phone: string; orgCode: string; post: string }[], domain: Domain): Promise<number> {
        if (!session?.editableTables?.includes('users')) return unavailable();
        if (!rows.length || rows.length > 500) throw new DomainError('每次可导入 1 至 500 人。');
        const result = await authenticated<{ count: number }>('/idaas/users/import', {
            domain, rows, requestId: crypto.randomUUID(),
        });
        await refreshWorkspace();
        return result.count;
    },
    async listUsers(query: { domain: Domain; page: number; size: number; q?: string; status?: string; orgId?: string; includeChildren?: boolean }): Promise<{ list: Database['users']; total: number; page: number; size: number }> {
        if (!session?.capabilities?.includes('users')) return unavailable();
        const params = new URLSearchParams({ domain: query.domain, page: String(query.page), size: String(query.size),
            q: query.q || '', status: query.status || 'all', orgId: query.orgId || '', includeChildren: String(query.includeChildren === true) });
        const result = await authenticated<{ list: Database['users']; total: number; page: number; size: number }>(`/idaas/users/list?${params}`);
        if (!result || !Array.isArray(result.list) || !Number.isFinite(result.total)) throw new DomainError('人员列表响应不完整，请重试。', 'INVALID_RESPONSE');
        return result;
    },
    async reorderApplications(ids: string[]): Promise<void> {
        if (!session?.globalPermissions?.includes('apps:write')) return unavailable();
        if (!ids.length || new Set(ids).size !== ids.length) throw new DomainError('请选择有效的应用顺序。');
        await authenticated('/idaas/applications/reorder', { ids, domain: session.domain, requestId: crypto.randomUUID() });
        await refreshWorkspace();
    },
    async remove<K extends EntityTable>(table: K, id: string): Promise<void> {
        const catalog = table === 'catalog' ? database.catalog.find(row => row.id === id) : null;
        const directoryCatalog = catalog && ['line', 'dictionary', 'extension'].includes(catalog.category);
        const path = catalog?.category === 'app-group' ? 'application-groups' : directoryCatalog ? catalog.category === 'extension' ? 'field-definitions' : 'dictionaries' : tablePaths[table];
        const editableKey = catalog?.category === 'app-group' ? 'app-groups' : directoryCatalog ? 'directory' : table;
        if (!path || !session?.editableTables?.includes(editableKey)) return unavailable();
        const value = database[table].find(row => row.id === id);
        if (!value) throw new DomainError('记录已不存在，请刷新后重试。', 'NOT_FOUND');
        const mutation = await requestIdFor({ tenantId: session.tenantId, table, id, version: value.version });
        try { await authenticated(`/idaas/${path}/remove`, { id, version: value.version, requestId: mutation.requestId }); }
        catch (error) { if (table === 'users') userOperationListeners.forEach(fn => fn()); throw error; }
        mutationIds.delete(mutation.key);
        await refreshWorkspace();
    },
    async settings(domain: Domain, values: Partial<Settings>, kind = 'security'): Promise<void> {
        if (domain !== 'workforce' || !['security', 'branding', 'api', 'sso'].includes(kind)) return unavailable();
        const category = kind === 'branding' ? 'BRANDING' : kind === 'api' ? 'API_ACCESS' : kind === 'sso' ? 'SSO' : 'SECURITY';
        const keys = kind === 'branding' ? ['title', 'subtitle', 'logoUrl'] : kind === 'api' ? ['apiWhitelist','ipBlacklist','apiRateLimit'] : kind === 'sso' ? ['accessTtl','refreshTtl','idleMinutes','allowConcurrent'] : ['minLength', 'lockThreshold', 'lockMinutes', 'forceChange', 'passwordBlacklist','mfaRequired'];
        const selected = Object.fromEntries(keys.map(key => [key, values[key as keyof Settings]]));
        const body = { category, configKey: kind === 'branding' ? 'brand' : kind === 'api' ? 'access' : kind === 'sso' ? 'session' : 'password', values: selected, version: settingVersions[category] };
        const mutation = await requestIdFor(body);
        await authenticated('/idaas/settings/save', { ...body, requestId: mutation.requestId });
        mutationIds.delete(mutation.key); await refreshWorkspace();
    },
    async profile(name: string): Promise<void> {
        const body = { name, version: session?.operatorVersion, subjectVersion: session?.subjectVersion };
        const mutation = await requestIdFor(body);
        await authenticated('/idaas/profile/save', { ...body, requestId: mutation.requestId });
        mutationIds.delete(mutation.key); await refreshWorkspace();
    },
    async password(oldPassword: string, password: string): Promise<void> {
        const body = { oldPassword, password, version: session?.operatorVersion };
        const mutation = await requestIdFor(body);
        await authenticated('/idaas/profile/password', { ...body, requestId: mutation.requestId });
        clearSession();
    },
    async publicRegister(_values: { name: string; account: string; email: string; phone: string }, _legalName?: string): Promise<void> { unavailable(); },
    async recordEvent(_domain: Domain, _action: string, _target: string, _detail: unknown): Promise<void> { unavailable(); },
    async startTask(_configId: string, _mode = 'full', _selectedIds?: string[]): Promise<string> { return unavailable(); },
    async retryTask(_id: string): Promise<void> { unavailable(); },
    async cancelTask(_id: string): Promise<void> { unavailable(); },
};
export function getDatabase() { return database; }
export function getSession() { return session; }
export function getWorkspaceState() { return state; }
export function getStorageWarning() { return ''; }
/** Platform administration uses the separately authenticated IAM routes. */
export const foundationApi = {
    list: <T>(path: string) => authenticated<T[]>(path),
    read: <T>(path: string) => authenticated<T>(path),
    async write<T>(path: string, body: Record<string, unknown>): Promise<T> {
        const mutation = await requestIdFor({ path, body });
        const result = await authenticated<T>(path, { ...body, requestId: mutation.requestId });
        mutationIds.delete(mutation.key);
        return result;
    },
};
export async function acceptPlatformLogin(value: { token: string; session: Session }, domain: Domain) { validateSession(value.session); revision++; domainRequests.clear(); retainToken(value.token); session = value.session; await refreshWorkspace(); if (domain !== session!.domain) await api.changeDomain(domain); return session!; }
export interface ReceiverConfig { id: string; name: string; appId: string; targetTenantId: string; receiverInstanceId: string; endpoint: string; keyId: string; enabled: boolean; timeout: number; version: number; hasSecret: boolean; lastTestResult?: string; mappingMode?: string; }
export interface ProvisionItem { eventId: string; objectId: string; type: 'user' | 'org' | 'role' | 'resource'; version: number; status: string; attempt: number; result: { message?: string; result?: string; localId?: string } | null; }
export interface ProvisionTask { id: string; version: number; configId: string; targetTenantId: string; status: string; createdAt: string; name: string; items: ProvisionItem[]; }
export interface ProvisionObject { id: string; name: string; version: number; status: string; account?: string; code?: string; }
export const provisionApi = {
    options: () => authenticated<TenantOption[]>('/idaas/provision/options'),
    configs: () => authenticated<ReceiverConfig[]>('/idaas/provision/configs'),
    tasks: () => authenticated<ProvisionTask[]>('/idaas/provision/tasks'),
    objects: (query: { configId: string; kind: string; page: number; size: number; q?: string }) => authenticated<{ list: ProvisionObject[]; total: number; page: number; size: number }>(
        `/idaas/provision/objects?${new URLSearchParams({ configId: query.configId, kind: query.kind, page: String(query.page), size: String(query.size), q: query.q || '' })}`),
    save: (values: Partial<ReceiverConfig> & { secret?: string }) => foundationApi.write('/idaas/provision/save-config', values),
    async test(configId: string) { const result = await foundationApi.write<{ verified: boolean; message?: string }>('/idaas/provision/test', { configId }); if (!result.verified) throw new DomainError(result.message || '接收方的应用、实例或签名核验失败，请检查配置。'); return result; },
    create: (configId: string, requestId: string, kind?: string, selectedIds?: string[]) => authenticated<{ id: string }>('/idaas/provision/create', { configId, requestId, kind, selectedIds }),
    execute: (id: string) => foundationApi.write('/idaas/provision/execute', { id }),
    retry: (task: ProvisionTask) => foundationApi.write('/idaas/provision/retry', { id: task.id, version: task.version }),
    cancel: (task: ProvisionTask) => foundationApi.write('/idaas/provision/cancel', { id: task.id, version: task.version }),
};
/** Legacy callers cannot manufacture a server session or change its tenant/domain. */
export function setSession(value: Session | null) { if (value === null) clearSession(); else throw new DomainError('请通过身份服务更新会话。', 'INVALID_SESSION'); }
export function subscribeDatabase(fn: () => void) { listeners.add(fn); return () => { listeners.delete(fn); }; }
export function subscribeSession(fn: () => void) { sessionListeners.add(fn); return () => { sessionListeners.delete(fn); }; }
export function subscribeWorkspace(fn: () => void) { stateListeners.add(fn); return () => { stateListeners.delete(fn); }; }
export function subscribeUserOperations(fn: () => void) { userOperationListeners.add(fn); return () => { userOperationListeners.delete(fn); }; }
