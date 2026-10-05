import type { Domain, Session } from '../domain/types.ts';
import { pages, publicPageKeys, type NavPage } from './navigation-data.ts';

export interface ConsolePage extends NavPage { id: string; domain: Domain; }

/** A role label never substitutes for the platform's paired permission/scope checks. */
export function canView(session: Session, path: string): boolean {
    if (path === 'overview' || path === 'profile') return true;
    if (session.mustChangePassword) return false;
    if (session.realm === 'platform') {
        const permission = path === 'apps' || path.startsWith('apps/') || path === 'app-groups' ? 'apps:read'
            : path === 'auth-accounts' ? 'auth-accounts:read' : path === 'verifications' || path === 'entities' ? 'public:read'
            : path === 'persons' || path === 'organization' ? 'users:read' : path.startsWith('org-settings/') ? 'directory:read'
            : path === 'delegates' || path === 'system/access/admins' || path === 'locked-accounts' ? 'operators:read'
            : path.startsWith('audit/') ? 'audit:read' : path === 'system/access/roles' ? 'platform-roles:read'
            : path.startsWith('grants/') || path === 'permission-groups' ? 'grants:read'
            : path.startsWith('sync/') ? 'sync:read' : path.startsWith('system/settings/') ? 'settings:read' : '';
        const permissions = path === 'apps' || path.startsWith('apps/') || path === 'organization' || path === 'persons' || path.startsWith('grants/')
            ? session.permissions : session.globalPermissions;
        return permission !== '' && Boolean(permissions?.includes(permission));
    }
    if (session.role === 'admin') return true;
    if (session.role === 'auditor') return path.startsWith('audit/');
    if (session.role === 'appmanager') return path === 'apps' || path.startsWith('apps/');
    if (session.role === 'orgadmin') return path === 'organization' || path === 'locked-accounts';
    return false;
}

export function canViewDomain(session: Session, domain: Domain, path: string): boolean {
    if (domain === session.domain) return canView(session, path);
    const discovery = session.navigationPermissions?.[domain];
    if (!discovery || session.mustChangePassword) return false;
    return canView({ ...session, domain, ...discovery }, path);
}

const publicEntries = [
    ['overview', '公众总览'], ['persons', '自然人管理'], ['entities', '法人管理'],
    ['auth-accounts', '公众认证账号'], ['verifications', '身份核验'],
    ['apps', '公众应用'], ['app-groups', '公众应用分组'], ['audit/statistics', '公众统计'],
] as const;

/** One stable console menu. Public business pages select their own data context by route. */
export function consolePages(session: Session): ConsolePage[] {
    const available = (domain: Domain) => session.navigationPermissions ? Boolean(session.navigationPermissions[domain]) : domain === session.domain;
    // Existing global administration guards use workforce scope; public-scoped grants do not imply that access.
    const common = (available('workforce') ? pages : []).filter(page => page.key !== 'profile' && !['persons', 'entities', 'verifications'].includes(page.key)).flatMap(page => {
        const domain: Domain = 'workforce';
        if (!canViewDomain(session, domain, page.key)) return [];
        return [{ ...page, domain, id: `${domain}:${page.key}` }];
    });
    const publicPages = available('public') ? publicEntries.flatMap(([key, label]) => {
        const page = pages.find(candidate => candidate.key === key)!;
        if (!canViewDomain(session, 'public', key)) return [];
        return [{ ...page, label, group: '公众身份', domain: 'public' as const, id: `public:${key}` }];
    }) : [];
    // Keep platform settings at the end while public identities remain a business feature.
    return [...common.filter(page => !['审计日志', '系统管理'].includes(page.group)), ...publicPages, ...common.filter(page => ['审计日志', '系统管理'].includes(page.group))];
}

export function consoleHome(session: Session): Domain {
    return session.navigationPermissions && !session.navigationPermissions.workforce && session.navigationPermissions.public ? 'public' : session.domain;
}

export function isPublicRoute(path: string): boolean { return publicPageKeys.has(path) || path.startsWith('apps/') || path === 'profile'; }
