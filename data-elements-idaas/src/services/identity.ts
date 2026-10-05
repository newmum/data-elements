import { request } from './http.ts';
import type { Domain } from '../domain/types.ts';
import { DomainError } from '../domain/types.ts';

export interface IdentityProfile { id: string; subjectId: string; username: string; name: string; domain: Domain; realm: 'auth-account'; mustChangePassword: boolean; verificationStatus: string; legalEntityId?: string; version: number; }
export interface LoginChallenge { challenge: string; realm: 'platform' | 'auth-account'; domain: Domain; returnTo?: string; expires: number; }
const identityKey = 'iam.auth.account.token';
const challengeKey = 'iam.auth.pending.challenge';
function read(key: string) { try { return sessionStorage.getItem(key) || ''; } catch { return ''; } }
let identityToken = read(identityKey);
export function retainIdentityToken(value: string) { identityToken = value; try { value ? sessionStorage.setItem(identityKey, value) : sessionStorage.removeItem(identityKey); } catch { /* In-memory authentication remains scoped to this tab. */ } }
export function rememberChallenge(value: Omit<LoginChallenge, 'expires'>) { try { sessionStorage.setItem(challengeKey, JSON.stringify({ ...value, expires: Date.now() + 300000 })); } catch { throw new Error('无法保存认证流程，请允许当前标签页使用会话存储。'); } }
export function pendingChallenge(): LoginChallenge | null { try { const value = JSON.parse(read(challengeKey)) as LoginChallenge; return value?.expires > Date.now() && value.challenge ? value : null; } catch { return null; } }
export function clearChallenge() { try { sessionStorage.removeItem(challengeKey); } catch { /* Expired server challenge never authenticates. */ } }
export function hasIdentitySession() { return Boolean(identityToken); }
export async function identityRequest<T>(path: string, body?: unknown): Promise<T> {
    const currentToken = identityToken;
    try { return await request<T>(path, { token: currentToken, method: body === undefined ? 'GET' : 'POST', body }); }
    catch (error) { if (error instanceof DomainError && error.code === 'UNAUTHENTICATED' && identityToken === currentToken) retainIdentityToken(''); throw error; }
}
export const identityApi = {
    requestRecovery: (username: string, realm: 'platform' | 'auth-account', domain: Domain) =>
        request<{ accepted: boolean; message: string }>('/idaas/password-recovery/request', {
            method: 'POST', body: { username, realm, domain },
        }),
    async login(username: string, password: string, domain: Domain) {
        const value = await request<{ token?: string; profile?: IdentityProfile; mfaRequired?: boolean; challenge?: string }>('/idaas/auth-account/login', { method: 'POST', body: { username, password, domain } });
        if (value.token) retainIdentityToken(value.token);
        return value;
    },
    register: (values: { username: string; name: string; password: string; phone?: string; email?: string; consent: boolean; requestId: string }) => request<{ id: string; subjectId: string; verificationStatus: string }>('/idaas/auth-account/register', { method: 'POST', body: { ...values, domain: 'public' } }),
    me: () => identityRequest<IdentityProfile>('/idaas/auth-account/me'),
    async logout() {
        const currentToken = identityToken;
        const revocation = currentToken ? request('/idaas/auth-account/logout', { token: currentToken, method: 'POST', body: {} }) : Promise.resolve();
        retainIdentityToken('');
        try { await revocation; }
        catch (error) {
            if (error instanceof DomainError && error.code === 'UNAUTHENTICATED') return;
            throw error;
        }
    },
    async password(oldPassword: string, password: string) { await identityRequest('/idaas/auth-account/password', { oldPassword, password }); retainIdentityToken(''); },
    async verify(challenge: string, code: string) {
        const result = await request<{ token: string; realm?: string; session?: import('../domain/types').Session }>('/idaas/mfa/verify-login', { method: 'POST', body: { challenge, code } });
        return result;
    },
};
/** Only resume this server's authorization endpoint; query input never selects a redirect host. */
export async function resumeAuthorization(returnTo?: string) {
    if (!returnTo || !returnTo.startsWith('/idaas/oauth2/authorize?') || returnTo.includes('\\') || /[\r\n]/.test(returnTo)) return false;
    const response = await fetch('/api/.well-known/openid-configuration');
    if (!response.ok) throw new Error('无法读取认证服务配置，请稍后重试。');
    const settings = await response.json() as { issuer: string };
    location.assign(new URL(returnTo, settings.issuer).href);
    return true;
}
