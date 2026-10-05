import { useEffect, useState, useSyncExternalStore } from 'react';
import { Alert, App, Avatar, Button, Card, Checkbox, ConfigProvider, Form, Input, Result, Segmented, Select, Space, Steps, Tabs, Tag, Typography, theme } from 'antd';
import { ApartmentOutlined, AppstoreOutlined, ArrowLeftOutlined, ArrowRightOutlined, CheckCircleOutlined, KeyOutlined, LockOutlined, SafetyCertificateOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons';
import { Link, useNavigate, useParams, useLocation, useSearchParams } from 'react-router-dom';
import { identityApi, rememberChallenge, pendingChallenge, clearChallenge, retainIdentityToken, resumeAuthorization, identityRequest } from '../services/identity';
import { acceptPlatformLogin } from '../services/workspace';
import { useDatabase, api, loginBrand } from '../mock/store';
import { effectiveAccess } from '../domain/engine';
import type { Domain, User } from '../domain/types';
import { Text, Title } from '../components/common';
import { BrandMark, ShieldArtwork } from '../components/Brand';
import { platformTitle } from '../domain/branding';
import { authTheme } from '../app/theme';
import { consoleHome } from '../app/console-navigation';
import { clearLogoutWarning, getLogoutWarning, subscribeLogoutWarning } from '../services/logoutFeedback';
export function AuthFrame({ children, title = platformTitle, subtitle = '统一身份 · 安全访问 · 高效协同' }: {
    children: React.ReactNode;
    title?: string;
    subtitle?: string;
}) {
    useEffect(() => { document.title = title; }, [title]);
    return <ConfigProvider componentSize="large" theme={authTheme}><div className="auth-layout"><section className="auth-brand"><Link to="/login" className="auth-logo"><BrandMark size={44}/><strong>{title}</strong></Link><div className="auth-story"><h1>一个身份。<br />连接每一份信任。</h1><p>{subtitle}，<br />让人员、应用和访问权限有序协同。</p><div className="auth-shield"><ShieldArtwork /></div><div className="auth-capabilities">{[{ icon: <TeamOutlined />, title: '统一账户', text: '组织与人员，一处维护' }, { icon: <KeyOutlined />, title: '精细授权', text: '访问与操作，边界清晰' }, { icon: <SafetyCertificateOutlined />, title: '全程追溯', text: '重要行为，有迹可循' }].map(v => <div key={v.title}><span className="auth-cap-icon">{v.icon}</span><div><strong>{v.title}</strong><small>{v.text}</small></div></div>)}</div></div><div className="auth-brand-footer"><span className="status-dot"/> 统一身份，守护每一次连接</div></section><main className="auth-main"><div className="auth-content">{children}</div><div className="auth-footer">统一身份 · 安全访问 · 高效协同</div></main></div></ConfigProvider>;
}
export default function Login() {
    const db = useDatabase();
    const domain: Domain = 'workforce';
    const [brand, setBrand] = useState({ title: platformTitle, subtitle: db.settings.workforce.subtitle });
    useEffect(() => { void loginBrand().then(value => { if (value.title) setBrand(value); }).catch(() => {}); }, []);
    const [form] = Form.useForm();
    const [error, setError] = useState('');
    const [busy, setBusy] = useState(false);
    const logoutWarning = useSyncExternalStore(subscribeLogoutWarning, () => getLogoutWarning('platform'));
    const nav = useNavigate();
    const { modal } = App.useApp();
    const login = async (v: {
        username: string;
        password: string;
    }) => {
        setBusy(true);
        setError('');
        try {
            const session = await api.login(v.username.trim(), v.password, domain);
            clearLogoutWarning('platform');
            nav(`/console/${consoleHome(session)}/overview`);
        }
        catch (e) {
            if (e instanceof Error && 'code' in e && e.code === 'MFA_REQUIRED') { nav(`/auth/${domain}/mfa`); return; }
            setError(e instanceof Error ? e.message : '登录失败');
        }
        finally {
            setBusy(false);
        }
    };
    return <AuthFrame title={brand.title} subtitle={brand.subtitle}><div className="login-content"><Tag color="blue">管理控制台</Tag><Title className="auth-page-title" level={2} >欢迎登录</Title><Typography.Paragraph type="secondary">使用平台操作账号，管理应用、身份目录与平台权限。</Typography.Paragraph>{logoutWarning && <Alert title={logoutWarning} type="warning" showIcon closable onClose={() => clearLogoutWarning('platform')} style={{ marginBottom: 12 }}/>} {error && <Alert title={error} type="error" showIcon style={{ marginBottom: 12 }}/>}<Form form={form} layout="vertical" size="large" onFinish={login}><Form.Item label="管理账号" name="username" rules={[{ required: true, message: '请输入管理账号' }]}><Input prefix={<UserOutlined />} autoComplete="username" placeholder="请输入管理账号"/></Form.Item><Form.Item label="登录密码" name="password" rules={[{ required: true, message: '请输入密码' }]}><Input.Password prefix={<LockOutlined />} autoComplete="current-password" placeholder="请输入密码"/></Form.Item><div className="login-options"><Text type="secondary">请妥善保管账号与密码</Text><Link to={`/auth/${domain}/forgot`}>忘记密码</Link></div><Button className="auth-primary-action" type="primary" htmlType="submit" block loading={busy}>登录控制台 <ArrowRightOutlined /></Button></Form><div className="auth-links"><Link to="/auth/public/login">公众认证入口</Link><Link to="/auth/public/register/person">自然人注册</Link><Link to="/auth/public/register/company">法人注册</Link></div></div></AuthFrame>;
}
function UnavailableAuth({ title, description }: { title: string; description: string }) {
    const nav = useNavigate();
    return <AuthFrame><div className="login-content"><Button type="text" icon={<ArrowLeftOutlined />} onClick={() => nav('/login')} style={{ paddingLeft: 0 }}>返回登录</Button><Title className="auth-page-title" level={2}>{title}</Title><Result status="info" title="此服务尚未开通" subTitle={description} extra={<Button className="auth-primary-action" type="primary" onClick={() => nav('/login')}>返回管理端登录</Button>}/></div></AuthFrame>;
}
export function PublicLogin() {
    const [params] = useSearchParams(); const route = useLocation(); const nav = useNavigate(); const [domain, setDomain] = useState<Domain>(route.pathname.startsWith('/auth/public') ? 'public' : 'workforce'); const [busy, setBusy] = useState(false); const [error, setError] = useState('');
    const logoutWarning = useSyncExternalStore(subscribeLogoutWarning, () => getLogoutWarning('auth-account'));
    const login = async (v: { username: string; password: string }) => { setBusy(true); setError(''); try { const result = await identityApi.login(v.username.trim(), v.password, domain); const returnTo = params.get('returnTo') || undefined; if (result.mfaRequired && result.challenge) { rememberChallenge({ challenge: result.challenge, realm: 'auth-account', domain, returnTo }); nav(`/auth/${domain}/mfa`); return; } if (!result.token) throw new Error('认证响应不完整，请重新登录'); clearLogoutWarning('auth-account'); if (result.profile?.mustChangePassword || !await resumeAuthorization(returnTo)) nav('/auth/identity/profile'); } catch (e) { setError(e instanceof Error ? e.message : '认证失败'); } finally { setBusy(false); } };
    return <AuthFrame><div className="login-content"><Tag color="blue">统一认证</Tag><Title className="auth-page-title" level={2}>身份登录</Title><Typography.Paragraph type="secondary">使用统一认证账号访问已接入的应用，或维护自己的身份与账号安全。</Typography.Paragraph>{logoutWarning && <Alert type="warning" title={logoutWarning} showIcon closable onClose={() => clearLogoutWarning('auth-account')} style={{ marginBottom: 12 }} />}<Segmented block className="auth-domain-switch" value={domain} onChange={v => setDomain(v as Domain)} options={[{ value: 'workforce', label: '政企身份' }, { value: 'public', label: '公众身份' }]} />{error && <Alert type="error" title={error} showIcon />}<Form size="large" layout="vertical" onFinish={login}><Form.Item name="username" label="认证账号" rules={[{ required: true }]}><Input prefix={<UserOutlined />} autoComplete="username" /></Form.Item><Form.Item name="password" label="登录密码" rules={[{ required: true }]}><Input.Password prefix={<LockOutlined />} autoComplete="current-password" /></Form.Item><Button className="auth-primary-action" type="primary" block htmlType="submit" loading={busy}>登录</Button></Form><div className="auth-links"><Link to="/auth/public/register/person">自然人注册</Link><Link to="/auth/public/register/company">法人注册</Link><Link to="/login">平台管理入口</Link></div></div></AuthFrame>;
}
export function Register({ company = false }: { company?: boolean }) {
    const nav = useNavigate(); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const [requestId] = useState(() => crypto.randomUUID());
    const submit = async (v: { username: string; name: string; password: string; phone?: string; email?: string; consent: boolean; companyName?: string; registrationCode?: string }) => { setBusy(true); setError(''); try { await identityApi.register({ username: v.username, name: v.name, password: v.password, phone: v.phone, email: v.email, consent: v.consent, requestId }); const login = await identityApi.login(v.username, v.password, 'public'); if (!login.token) throw new Error('注册已完成，请使用新账号登录后继续维护资料'); clearLogoutWarning('auth-account'); if (company) await identityRequest('/idaas/self-entities/register', { name: v.companyName, registrationCode: v.registrationCode, consent: true, requestId }); nav('/auth/identity/profile'); } catch (e) { setError(e instanceof Error ? e.message : '注册未完成'); } finally { setBusy(false); } };
    return <AuthFrame><div className="login-content"><Link to="/auth/public/login">返回身份登录</Link><Title className="auth-page-title" level={2}>{company ? '法人账户注册' : '自然人注册'}</Title><Alert type="info" title="注册资料与实名核验分开处理" description={company ? '先建立经办人的独立认证账号，法人资料进入待核验状态。核验通过并获授权后才能使用法人身份办理业务。' : '注册后可登录个人身份中心，实名状态以核验服务的有效结果为准。'} showIcon />{error && <Alert type="error" title={error} showIcon />}<Form size="large" layout="vertical" onFinish={submit} style={{ marginTop: 24 }}><Form.Item name="name" label={company ? '经办人姓名' : '姓名'} rules={[{ required: true }, { max: 100 }]}><Input autoComplete="name" /></Form.Item><Form.Item name="username" label="认证账号" rules={[{ required: true }, { pattern: /^[A-Za-z][A-Za-z0-9_.-]{2,99}$/, message: '以字母开头，至少三位，支持字母、数字和点、横线、下划线' }]}><Input autoComplete="username" /></Form.Item><Form.Item name="password" label="登录密码" rules={[{ required: true }, { min: 8 }]}><Input.Password autoComplete="new-password" /></Form.Item><Form.Item name="confirm" label="确认密码" dependencies={['password']} rules={[{ required: true }, ({ getFieldValue }) => ({ validator: (_, value) => value === getFieldValue('password') ? Promise.resolve() : Promise.reject(new Error('两次密码不一致')) })]}><Input.Password autoComplete="new-password" /></Form.Item><Form.Item name="phone" label="手机号"><Input autoComplete="tel" /></Form.Item><Form.Item name="email" label="邮箱" rules={[{ type: 'email' }]}><Input autoComplete="email" /></Form.Item>{company && <><Form.Item name="companyName" label="法人名称" rules={[{ required: true }, { max: 200 }]}><Input autoComplete="organization" /></Form.Item><Form.Item name="registrationCode" label="统一社会信用代码" rules={[{ required: true }, { pattern: /^[0-9A-HJ-NPQRTUWXY]{18}$/, message: '填写十八位统一社会信用代码' }]}><Input maxLength={18} /></Form.Item></>}<Form.Item name="consent" valuePropName="checked" rules={[{ validator: (_, value) => value === true ? Promise.resolve() : Promise.reject(new Error('请先同意本次注册资料处理')) }]}><Checkbox>同意为建立认证账号和身份档案处理本次提交资料</Checkbox></Form.Item><Button className="auth-primary-action" block type="primary" htmlType="submit" loading={busy}>提交注册</Button></Form></div></AuthFrame>;
}
export function PasswordFlow({ mfa = false }: { mfa?: boolean }) {
    const nav = useNavigate(); const [busy, setBusy] = useState(false); const [error, setError] = useState(''); const pending = pendingChallenge();
    if (!mfa) return <AuthFrame><div className="login-content"><Title className="auth-page-title" level={2}>重置账号密码</Title><Alert type="info" title="请联系对应系统的账号管理员" description="当前未配置短信或邮件找回渠道。管理员核验身份后，可重置相应平台账号或统一认证账号的密码；各应用本地账号由该应用管理员处理。" showIcon /><div className="auth-links"><Link to="/login">管理端登录</Link><Link to="/auth/identity/login">统一认证登录</Link></div></div></AuthFrame>;
    const verify = async (v: { code: string }) => { if (!pending) return; setBusy(true); setError(''); try { const result = await identityApi.verify(pending.challenge, v.code.trim()); clearChallenge(); if (pending.realm === 'platform') { if (!result.session) throw new Error('平台认证响应不完整'); const session = await acceptPlatformLogin({ token: result.token, session: result.session }, pending.domain); clearLogoutWarning('platform'); nav(`/console/${consoleHome(session)}/overview`); } else { retainIdentityToken(result.token); clearLogoutWarning('auth-account'); if (!await resumeAuthorization(pending.returnTo)) nav('/auth/identity/profile'); } } catch (e) { setError(e instanceof Error ? e.message : '验证失败'); } finally { setBusy(false); } };
    return <AuthFrame><div className="login-content"><Title className="auth-page-title" level={2}>二次验证</Title>{!pending ? <Alert type="warning" title="认证流程已失效，请重新登录" showIcon /> : <><Typography.Paragraph type="secondary">填写口令应用中的六位动态口令；无法使用口令应用时，可填写尚未使用的恢复码。</Typography.Paragraph>{error && <Alert type="error" title={error} showIcon />}<Form size="large" layout="vertical" onFinish={verify}><Form.Item name="code" label="动态口令或恢复码" rules={[{ required: true }]}><Input autoComplete="one-time-code" /></Form.Item><Button className="auth-primary-action" type="primary" block htmlType="submit" loading={busy}>验证并登录</Button></Form></>}<div className="auth-links"><Link to="/login">返回管理端登录</Link><Link to="/auth/identity/login">返回身份登录</Link></div></div></AuthFrame>;
}
