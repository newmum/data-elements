import type { Database, Domain, Session } from './types';

/** 审计代码和对象 ID 保留在原始记录中，这里只负责面向管理人员的摘要。 */
const actionNames: Record<string, string> = {
    'profile:domain': '切换身份域',
    'profile:password': '修改登录密码',
    'profile:save': '更新个人资料',
    'applications:bind': '核验应用连接',
    'apps:write': '维护应用登记',
    'sync:create': '创建身份下发任务',
    'sync:execute': '执行身份下发',
    'sync:test': '测试下发连接',
    'sync:save-config': '保存下发配置',
    'sync:rotate': '轮换下发密钥',
    'sync:cancel': '取消下发任务',
    'sync:retry': '重试下发任务',
    'protocol:configure': '配置统一认证',
    'protocol:federation': '配置身份联合',
    'protocol:rotate': '轮换认证密钥',
    'platform-roles:assign': '分配平台角色与范围',
    'operators:reset-password': '重置操作账号密码',
    'operators:unlock': '解除操作账号锁定',
    'auth-accounts:bind-entity': '关联法人身份',
    'public-entities:activate-member': '启用法人经办关系',
    'verification:save-provider': '保存实名核验渠道',
    'auth:login': '登录管理控制台',
    'auth:account-login': '登录统一认证账号',
    'auth:public-register': '注册公众认证账号',
};

const objectNames: Record<string, string> = {
    subjects: '人员资料', orgs: '机构', applications: '应用', apps: '应用',
    roles: '应用角色', resources: '应用资源', grants: '授权',
    assignments: '应用分配', groups: '应用分组', 'permission-groups': '权限组',
    operators: '操作账号', 'platform-roles': '平台角色',
    'auth-accounts': '认证账号', clients: '应用客户端',
    'public-entities': '法人资料', dictionaries: '基础字典',
    'field-definitions': '扩展字段', settings: '平台设置',
    verification: '实名核验', protocol: '认证配置', sync: '身份下发',
};
const verbNames: Record<string, string> = {
    save: '保存', remove: '删除', revoke: '撤销', assign: '分配',
    read: '查看', write: '维护', test: '测试', bind: '绑定',
    rotate: '轮换', unlock: '解锁', cancel: '取消', retry: '重试',
};

export function auditActionLabel(action: string): string {
    if (actionNames[action]) return actionNames[action];
    if (action.startsWith('api:')) return '调用平台接口';
    const [object, verb] = action.split(':');
    if (objectNames[object] && verbNames[verb]) return verbNames[verb] + objectNames[object];
    return '其他业务操作';
}

export interface AuditTargetInput {
    action: string;
    target?: string | null;
    domain?: Domain | null;
    appId?: string | null;
}

/** 只使用当前工作区已经授权加载的资料解析名称，不因摘要额外读取目录或租户数据。 */
export function auditTargetLabel(event: AuditTargetInput, db: Database): string {
    if (event.action === 'profile:domain') return event.domain === 'public' ? '公众身份域' : '政企身份域';
    if (event.action.startsWith('profile:')) return '个人操作账号';
    const id = event.target || '';
    const grant = db.grants.find(item => item.id === id);
    if (grant) {
        const app = db.apps.find(item => item.id === grant.appId);
        const subject = [...db.users, ...db.orgs, ...db.groups].find(item => item.id === grant.subjectId);
        return [subject?.name || '授权对象', app?.name || '应用'].join(' · ');
    }
    const named = [
        ...db.users, ...db.orgs, ...db.apps, ...db.roles, ...db.resources,
        ...db.groups, ...db.syncConfigs, ...db.tasks, ...db.catalog, ...db.legalEntities,
    ].find(item => item.id === id);
    if (named?.name && named.name !== id) return named.name;
    const object = event.action.split(':')[0];
    const app = ['applications', 'apps', 'roles', 'resources', 'grants', 'assignments', 'clients', 'sync'].includes(object)
        ? db.apps.find(item => item.id === event.appId) : undefined;
    if (app) return app.name;
    // 旧记录可能已经没有可读取的对象资料，摘要不能把内部编号冒充名称。
    if (id && !/^[A-Za-z0-9_.:/-]+$/.test(id)) return id;
    return objectNames[object] ? `相关${objectNames[object]}` : '相关对象';
}

export function auditActorLabel(
    actor: string | null | undefined,
    db: Database,
    session?: Pick<Session, 'userId' | 'name'> | null,
    resolvedName?: string,
): string {
    if (resolvedName?.trim()) return resolvedName.trim();
    if (!actor) return '未记录操作人';
    if (actor === session?.userId) return session.name || '当前操作账号';
    const user = db.users.find(item => item.id === actor || item.account === actor);
    return user?.name || '操作账号（名称未提供）';
}
