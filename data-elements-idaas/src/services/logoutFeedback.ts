export type LogoutRealm = 'platform' | 'auth-account';

const keys: Record<LogoutRealm, string> = {
    platform: 'iam.logout.warning.platform',
    'auth-account': 'iam.logout.warning.auth-account',
};
export const logoutWarningText = '本地会话已清除，但身份服务未确认旧会话撤销。请留意其他设备上的登录状态。';
const listeners = new Set<() => void>();
const read = (realm: LogoutRealm) => {
    try { return sessionStorage.getItem(keys[realm]) === '1'; }
    catch { return false; }
};
const visible: Record<LogoutRealm, boolean> = { platform: read('platform'), 'auth-account': read('auth-account') };
const emit = () => listeners.forEach(listener => listener());

export function getLogoutWarning(realm: LogoutRealm) { return visible[realm] ? logoutWarningText : ''; }
export function subscribeLogoutWarning(listener: () => void) { listeners.add(listener); return () => listeners.delete(listener); }
export function showLogoutWarning(realm: LogoutRealm) {
    visible[realm] = true;
    try { sessionStorage.setItem(keys[realm], '1'); } catch { /* Keep the warning in memory when storage is unavailable. */ }
    emit();
}
export function clearLogoutWarning(realm: LogoutRealm) {
    visible[realm] = false;
    try { sessionStorage.removeItem(keys[realm]); } catch { /* In-memory state is already cleared. */ }
    emit();
}
