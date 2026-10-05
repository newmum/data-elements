import type { Domain } from '../domain/types.ts';
/** Business navigation only. Keep design guidance in the repository, not the console. */
export interface NavPage {
    key: string;
    label: string;
    description: string;
    group: string;
}
export const pages: NavPage[] = [
    { key: 'overview', label: '总览', description: '集中掌握身份、应用与访问状态', group: '总览' },
    { key: 'apps', label: '应用列表', description: '统一维护应用接入信息、角色与访问权限', group: '应用管理' },
    { key: 'app-groups', label: '应用分组', description: '按业务归属组织应用，保持分类清晰', group: '应用管理' },
    { key: 'organization', label: '组织与用户', description: '管理组织架构、人员账户与任职关系', group: '组织与用户' },
    { key: 'delegates', label: '分级管理员', description: '按机构范围分配管理职责', group: '组织与用户' },
    { key: 'org-settings/lines', label: '条线管理', description: '维护业务条线与机构归属', group: '组织与用户' },
    { key: 'org-settings/dictionaries', label: '职务岗位', description: '维护职务、岗位、职级及职称字典', group: '组织与用户' },
    { key: 'org-settings/fields', label: '扩展字段', description: '配置用户、机构、应用与资源的扩展属性', group: '组织与用户' },
    { key: 'grants/apps', label: '应用授权', description: '以应用为中心查看和配置访问资格', group: '授权管理' },
    { key: 'grants/users', label: '用户授权', description: '查看用户的全部授权来源与当前有效访问', group: '授权管理' },
    { key: 'grants/orgs', label: '机构授权', description: '配置机构与下级人员的应用访问资格', group: '授权管理' },
    { key: 'permission-groups', label: '权限组', description: '组合多个应用角色，统一授予一组用户', group: '授权管理' },
    { key: 'api-grants/apps', label: 'API 应用授权', description: '限定应用机器身份可调用的接口范围', group: '授权管理' },
    { key: 'api-grants/users', label: 'API 用户授权', description: '限定用户身份可调用的接口范围', group: '授权管理' },
    { key: 'sync/configs', label: '同步配置', description: '配置下游应用的身份数据同步方式', group: '同步管理' },
    { key: 'sync/entities', label: '同步对象', description: '查看待同步的用户、机构与关联状态', group: '同步管理' },
    { key: 'sync/tasks', label: '同步任务', description: '跟踪任务进度、失败原因与重试结果', group: '同步管理' },
    { key: 'audit/operations', label: '操作日志', description: '追溯管理动作与重要配置变更', group: '审计日志' },
    { key: 'audit/logins', label: '登录日志', description: '检查身份验证与应用访问结果', group: '审计日志' },
    { key: 'audit/api', label: 'API 日志', description: '查询接口调用与身份数据访问记录', group: '审计日志' },
    { key: 'audit/statistics', label: '统计分析', description: '比较访问趋势与应用使用情况', group: '审计日志' },
    { key: 'system/access/admins', label: '管理员管理', description: '管理平台成员及其系统角色', group: '系统管理' },
    { key: 'system/access/roles', label: '系统角色', description: '配置平台功能权限与管理范围', group: '系统管理' },
    { key: 'system/settings/security', label: '账号安全', description: '配置账号密码、锁定与多因子策略', group: '系统管理' },
    { key: 'system/settings/sso', label: '单点登录设置', description: '统一维护会话、令牌与退出策略', group: '系统管理' },
    { key: 'system/settings/encryption', label: '字段加密', description: '管理敏感字段保护与展示策略', group: '系统管理' },
    { key: 'system/settings/api', label: 'API 访问控制', description: '配置接口访问限制与请求限额', group: '系统管理' },
    { key: 'system/settings/branding', label: '系统品牌', description: '统一设置平台名称与登录品牌', group: '系统管理' },
    { key: 'locked-accounts', label: '账号解锁', description: '查看被锁定账号并按权限解除锁定', group: '系统管理' },
    { key: 'auth-accounts', label: '认证账号', description: '管理独立统一认证凭据和账号状态', group: '组织与用户' },
    { key: 'verifications', label: '身份核验', description: '配置实名核验渠道和查看实际申请结果', group: '公众身份' },
    { key: 'persons', label: '自然人管理', description: '统一查看公众身份、实名状态与关联关系', group: '公众身份' },
    { key: 'entities', label: '法人管理', description: '管理法人信息及关联自然人和子账号', group: '公众身份' },
    { key: 'profile', label: '个人中心', description: '查看个人资料与账户安全选项', group: '个人中心' },
];
export const publicPageKeys = new Set(['auth-accounts', 'verifications', 'overview', 'apps', 'app-groups', 'persons', 'entities', 'audit/operations', 'audit/logins', 'audit/api', 'audit/statistics', 'system/access/admins', 'system/access/roles', 'system/settings/security', 'system/settings/branding']);
export const authPaths = ['/auth/identity/login', '/login', '/auth/public/login', '/auth/public/register/person', '/auth/public/register/company', '/auth/workforce/forgot', '/auth/public/forgot', '/auth/workforce/mfa', '/auth/public/mfa'];
export function domainPages(domain: Domain): NavPage[] {
    return pages.filter(p => domain === 'public' ? publicPageKeys.has(p.key) || p.key === 'profile' : !['persons', 'entities', 'verifications'].includes(p.key));
}
