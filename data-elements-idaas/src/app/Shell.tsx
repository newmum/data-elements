import { Component, Suspense, lazy, useEffect, useRef, useState } from 'react';
import type { ErrorInfo, ReactNode, CSSProperties } from 'react';
import { App, Alert, Avatar, Badge, Breadcrumb, Button, Card, Descriptions, Drawer, Dropdown, Layout, Menu, Result, Space, Tag, theme, Modal, Input, Empty, Tooltip } from 'antd';
import { BellOutlined, DownOutlined, LogoutOutlined, MenuFoldOutlined, MenuUnfoldOutlined, QuestionCircleOutlined, ReloadOutlined, SafetyCertificateOutlined, SettingOutlined, UserOutlined, SearchOutlined, ArrowRightOutlined, AppstoreOutlined, SafetyOutlined, CloseCircleOutlined } from '@ant-design/icons';
import { Navigate, useNavigate, useParams } from 'react-router-dom';
import { api, getSession, hasCapability, initializeWorkspace, useDatabase, useSession, useWorkspaceState } from '../mock/store';
import { logoutWarningText, showLogoutWarning } from '../services/logoutFeedback';
import type { Domain } from '../domain/types';
import { pages, groupIcons } from './navigation';
import { canView, canViewDomain, consolePages, isPublicRoute } from './console-navigation';
import { Text, Title } from '../components/common';
import { BrandMark } from '../components/Brand';
import { WorkspaceLoading } from '../components/WorkspaceLoading';
import { ProductCenterSwitcher } from './ProductCenterSwitcher';
import { platformTitle, systemName } from '../domain/branding';
import { UserAvatar, AppIcon } from '../components/visuals';
import { allowedUser, accessibleAppIds } from '../domain/engine';
import dayjs from 'dayjs';
const Dashboard = lazy(() => import('../pages/Dashboard'));
const Applications = lazy(() => import('../pages/Applications'));
const ApplicationDetail = lazy(() => import('../pages/Applications').then(m => ({ default: m.ApplicationDetail })));
const Organization = lazy(() => import('../pages/Organization'));
const Authorization = lazy(() => import('../pages/Authorization'));
const PermissionGroups = lazy(() => import('../pages/Authorization').then(m => ({ default: m.PermissionGroups })));
const Synchronization = lazy(() => import('../pages/Synchronization'));
const CatalogPage = lazy(() => import('../pages/CatalogPage'));
const PlatformAccess = lazy(() => import('../pages/PlatformAccess'));
const Audit = lazy(() => import('../pages/Audit'));
const Settings = lazy(() => import('../pages/Settings'));
const Profile = lazy(() => import('../pages/Settings').then(m => ({ default: m.Profile })));
const PublicEntities = lazy(() => import('../pages/PublicEntities'));
const AuthAccounts = lazy(() => import('../pages/IdentityAdministration').then(m => ({ default: m.AuthAccounts })));
const VerificationAdministration = lazy(() => import('../pages/IdentityAdministration').then(m => ({ default: m.VerificationAdministration })));
const { Header, Sider, Content } = Layout;
class PageBoundary extends Component<{
    children: ReactNode;
}, {
    error: boolean;
}> {
    state = { error: false };
    static getDerivedStateFromError() { return { error: true }; }
    componentDidCatch(error: Error, info: ErrorInfo) { console.error('Page render failed', error, info.componentStack); }
    render() { return this.state.error ? <Result status="error" title="页面暂时无法显示" subTitle="请刷新后重试；如问题持续，请联系管理员。" extra={<Button onClick={() => location.reload()}>刷新页面</Button>}/> : this.props.children; }
}
export default function Shell() {
    const params = useParams();
    const domain = (params.domain || 'workforce') as Domain;
    const path = params['*'] || 'overview';
    const db = useDatabase();
    const session = useSession();
    const workspace = useWorkspaceState();
    const nav = useNavigate();
    const { token } = theme.useToken();
    const { modal, message } = App.useApp();
    const [collapsed, setCollapsed] = useState(window.innerWidth < 1100);
    const [helpOpen, setHelpOpen] = useState(false);
    const [searchOpen, setSearchOpen] = useState(false);
    const [search, setSearch] = useState('');
    const [notifications, setNotifications] = useState(false);
    const [mobileNav, setMobileNav] = useState(false);
    const [contextError, setContextError] = useState<{ domain: Domain; message: string } | null>(null);
    const contextTarget = useRef('');
    contextTarget.current = `${domain}/${path}`;
    const pendingContext = useRef<{ domain: Domain; promise: Promise<unknown> } | null>(null);
    useEffect(() => {
        const handler = (event: KeyboardEvent) => { if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 'k') {
            event.preventDefault();
            setSearchOpen(v => !v);
        } };
        document.addEventListener('keydown', handler);
        return () => document.removeEventListener('keydown', handler);
    }, []);
    const [openKeys, setOpenKeys] = useState<string[]>([]);
    const [popupOpenKeys, setPopupOpenKeys] = useState<string[]>([]);
    const visible = session ? consolePages(session) : [];
    const info = visible.find(p => p.domain === domain && (p.key === path || (path.startsWith('apps/') && p.key === 'apps')))
        || pages.find(p => p.key === path) || pages.find(p => path.startsWith('apps/') && p.key === 'apps');
    useEffect(() => {
        if (info)
            setOpenKeys([info.group]);
    }, [info?.group, domain]);
    useEffect(() => { setPopupOpenKeys([]); }, [collapsed, domain, path, mobileNav]);
    useEffect(() => { void initializeWorkspace(); }, []);
    useEffect(() => {
        if (!session || workspace.status !== 'ready' || session.domain === domain || !['workforce', 'public'].includes(domain) || !canViewDomain(session, domain, path) || contextError?.domain === domain || pendingContext.current?.domain === domain) return;
        // The route chooses the business context; the console menu remains the same.
        const promise = api.changeDomain(domain);
        pendingContext.current = { domain, promise };
        void promise.catch(error => { if (contextTarget.current.startsWith(`${domain}/`)) setContextError({ domain, message: error instanceof Error ? error.message : '无法加载此功能' }); }).finally(() => { if (pendingContext.current?.promise === promise) pendingContext.current = null; });
    }, [domain, path, session, workspace.status, contextError]);
    useEffect(() => { document.title = `${info?.label || systemName} · ${db.settings[domain]?.title || platformTitle}`; }, [info?.label, domain, db.settings]);
    const loadingLabel = path === 'apps' || path.startsWith('apps/') ? '正在载入应用目录' : `正在载入${info?.label || '工作区'}`;
    if (workspace.status === 'loading' && !session) return <WorkspaceLoading label={loadingLabel} fullScreen/>;
    if (workspace.status === 'error') return <Result status="error" title="工作区加载失败" subTitle={workspace.error} extra={<Space><Button type="primary" onClick={() => { void initializeWorkspace(); }}>重新加载</Button><Button onClick={() => nav('/login')}>返回登录</Button></Space>}/>;
    if (!session)
        return <Navigate to="/login" replace/>;
    if (!['workforce', 'public'].includes(domain))
        return <Result status="404" title="身份域不存在" extra={<Button onClick={() => nav('/login')}>返回登录</Button>}/>;
    const menuItems = [...new Set(visible.map(p => p.group))].map(group => { const children = visible.filter(p => p.group === group); return children.length === 1 ? { key: children[0].id, icon: groupIcons[group], label: children[0].label } : { key: group, icon: groupIcons[group], label: group, popupClassName: 'business-navigation-popup', children: children.map(p => ({ key: p.id, label: p.label })) }; });
    // Hover and keyboard opening use Menu's native events; clicking an icon also opens its group.
    const desktopMenuItems = menuItems.map(item => 'children' in item ? { ...item, onTitleClick: () => { if (collapsed) setPopupOpenKeys([item.key]); } } : item);
    const logout = () => modal.confirm({ title: '退出当前账户？', content: '退出后需要重新登录才能继续访问控制台。', okText: '退出登录', cancelText: '取消', onOk: () => {
        const revocation = api.logout();
        nav('/login', { replace: true });
        void revocation.catch(() => { if (getSession()) message.warning(logoutWarningText); else showLogoutWarning('platform'); });
    } });
    const renderPage = (): ReactNode => {
        if (contextError?.domain === domain) return <Result status="error" title="此功能加载失败" subTitle={contextError.message} extra={<Space><Button onClick={() => setContextError(null)}>重新加载</Button><Button onClick={() => nav(`/console/${session.domain}/overview`)}>返回总览</Button></Space>}/>;
        if (session.domain !== domain && !canViewDomain(session, domain, path)) return <Result status="403" title="当前账号无此页面访问权限" subTitle="请联系管理员申请相应的访问权限。"/>;
        if (workspace.status === 'loading' || session.domain !== domain) return <WorkspaceLoading label={loadingLabel}/>;
        if (session.mustChangePassword && path !== 'profile') return <Profile domain={domain}/>;
        if (session.realm === 'platform' && session.permissions && ['system/access/admins', 'system/access/roles', 'locked-accounts', 'delegates'].includes(path)) {
            if (!canView(session, path)) return <Result status="403" title="当前账号无此功能权限"/>;
            return <PlatformAccess kind={path === 'system/access/roles' ? 'roles' : path === 'locked-accounts' ? 'locked' : 'operators'}/>;
        }
        if (!info && !/^apps\/[^/]+$/.test(path)) return <Result status="404" title="页面不存在" extra={<Button onClick={() => nav(`/console/${domain}/overview`)}>返回总览</Button>}/>;
        const capability = path === 'overview' ? 'workspace' : path === 'organization' ? 'users' : path === 'apps' || path.startsWith('apps/') ? 'apps' : path.startsWith('audit/') ? 'audit' : path === 'profile' ? 'profile' : path.startsWith('sync/') ? 'provisioning' : path === 'app-groups' ? 'apps' : path === 'persons' || path === 'entities' || path === 'verifications' ? 'public-identity' : path === 'auth-accounts' ? 'auth-accounts' : path.split('/')[0];
        if (!hasCapability(capability)) return <><div className="page-title"><h1 className="page-heading">{info?.label || '当前功能'}</h1></div><Result status="info" title="此功能尚未开通" subTitle="开通后即可在此管理相关业务。" extra={<Button onClick={() => nav(`/console/${domain}/overview`)}>返回总览</Button>}/></>;
        if (!canView(session, path))
            return <Result status="403" title="当前账号无此页面访问权限" subTitle="请联系管理员申请相应的访问权限。" extra={<Button onClick={() => nav(`/console/${domain}/overview`)}>返回总览</Button>}/>;
        if (domain === 'public' && !isPublicRoute(path))
            return <Result status="404" title="公众侧不提供此工作区"/>;
        if (path === 'overview')
            return <Dashboard domain={domain}/>;
        if (path === 'apps')
            return <Applications domain={domain}/>;
        if (/^apps\/[^/]+$/.test(path))
            return <ApplicationDetail domain={domain} appId={path.split('/')[1]}/>;
        if (path === 'organization')
            return <Organization domain={domain}/>;
        if (path === 'persons')
            return <Organization domain={domain} persons/>;
        if (path === 'entities')
            return <PublicEntities />;
        if (path === 'auth-accounts') return <AuthAccounts domain={domain}/>;
        if (path === 'verifications') return <VerificationAdministration/>;
        if (path === 'locked-accounts')
            return <Organization domain={domain} lockedOnly/>;
        if (path === 'permission-groups')
            return <PermissionGroups domain={domain}/>;
        if (['grants/apps', 'grants/users', 'grants/orgs'].includes(path))
            return <Authorization domain={domain} mode={path.split('/')[1] as 'apps' | 'users' | 'orgs'}/>;
        if (['sync/configs', 'sync/entities', 'sync/tasks'].includes(path))
            return <Synchronization domain={domain} view={path.split('/')[1] as 'configs' | 'entities' | 'tasks'}/>;
        if (path === 'audit/statistics')
            return <Dashboard domain={domain} statistics/>;
        if (['audit/operations', 'audit/logins', 'audit/api'].includes(path))
            return <Audit domain={domain} type={path === 'audit/operations' ? 'operation' : path === 'audit/logins' ? 'login' : 'api'}/>;
        if (['system/settings/security', 'system/settings/sso', 'system/settings/encryption', 'system/settings/api', 'system/settings/branding'].includes(path))
            return <Settings domain={domain} kind={path.split('/')[2]}/>;
        if (path === 'profile')
            return <Profile domain={domain}/>;
        if (['app-groups', 'delegates', 'org-settings/lines', 'org-settings/dictionaries', 'org-settings/fields', 'api-grants/apps', 'api-grants/users', 'system/access/admins', 'system/access/roles'].includes(path))
            return <CatalogPage domain={domain} path={path}/>;
        return <Result status="404" title="页面不存在" extra={<Button onClick={() => nav(`/console/${domain}/overview`)}>返回总览</Button>}/>;
    };
    const colors: Record<string, string> = { '--iam-primary': token.colorPrimary, '--iam-success': token.colorSuccess, '--iam-bg': token.colorBgLayout, '--iam-container': token.colorBgContainer, '--iam-text': token.colorText, '--iam-secondary': token.colorTextSecondary, '--iam-border': token.colorBorderSecondary, '--iam-fill': token.colorFillQuaternary, '--iam-primary-bg': token.colorPrimaryBg };
    const base = `/console/${domain}/`;
    const roleName = { admin: '系统管理员', auditor: '审计查看者', appmanager: '应用管理员', orgadmin: '机构管理员' }[session.role];
    const go = (key: string) => {
        const destination = visible.find(p => p.id === key);
        const group = destination?.group || pages.find(p => p.key === key.split('?')[0])?.group;
        if (group) setOpenKeys(keys => keys.includes(group) ? keys : [...keys, group]);
        setContextError(null);
        nav(destination ? `/console/${destination.domain}/${destination.key}` : base + key); setPopupOpenKeys([]); setSearchOpen(false); setMobileNav(false); setNotifications(false);
    };
    const matchingPages = visible.filter(p => !search || `${p.label} ${p.description}`.includes(search)).slice(0, 8);
    const matchingApps = search && session.domain === domain && canView(session, 'apps') ? db.apps.filter(a => a.domain === domain && accessibleAppIds(db, session).includes(a.id) && a.name.toLowerCase().includes(search.toLowerCase())).slice(0, 6) : [];
    const matchingUsers = search && session.domain === domain && canView(session, domain === 'public' ? 'persons' : 'organization') ? db.users.filter(u => allowedUser(db, session, u) && `${u.name} ${u.account}`.toLowerCase().includes(search.toLowerCase())).slice(0, 6) : [];
    const attention = [
        ...(domain === 'workforce' && canView(session, 'sync/tasks') ? [{ key: 'sync/tasks', icon: <ReloadOutlined />, title: '同步异常需要处理', text: `${db.tasks.filter(t => t.domain === domain && ['partial', 'failed'].includes(t.status)).length} 个任务包含失败项，请查看明细后重试。` }] : []),
        ...(canView(session, 'audit/logins') ? [{ key: 'audit/logins', icon: <SafetyOutlined />, title: '近期身份访问记录', text: '集中查看登录结果、来源地址和关联应用。' }] : []),
        ...(canView(session, 'locked-accounts') ? [{ key: 'locked-accounts', icon: <UserOutlined />, title: '账号锁定提醒', text: `${db.users.filter(u => allowedUser(db, session, u) && u.locked).length} 个账号处于锁定状态。` }] : []),
    ];
    const menu = <Menu className="business-navigation" theme="dark" mode="inline" inlineCollapsed={collapsed} items={desktopMenuItems} selectedKeys={[`${domain}:${info?.key || path}`]} openKeys={collapsed ? popupOpenKeys : openKeys} onOpenChange={collapsed ? setPopupOpenKeys : setOpenKeys} onClick={v => go(v.key)} style={{ borderInlineEnd: 0, background: 'transparent' }}/>;
    const mobileMenu = <Menu className="business-navigation" theme="dark" mode="inline" inlineCollapsed={false} items={menuItems} selectedKeys={[`${domain}:${info?.key || path}`]} openKeys={openKeys} onOpenChange={setOpenKeys} onClick={v => go(v.key)} style={{ borderInlineEnd: 0, background: 'transparent' }}/>;
    return <div className="iam-root" style={colors as CSSProperties}>
      <Layout className="platform-layout">
        <Sider className="app-sider" width={216} collapsedWidth={64} collapsed={collapsed} theme="dark">
          <ProductCenterSwitcher collapsed={collapsed} logoUrl={db.settings[domain].logoUrl}/>
          <div className="sider-content">{menu}</div>
          <div className="sider-bottom">{!collapsed ? <><div className="sider-banner"><strong>构建安全高效的<br />数字身份基础设施</strong><small>统一身份，连接每一份信任</small></div><div className="sider-version"><span>v2.2.0</span><span><i className="status-dot"/>统一身份服务</span></div></> : <div style={{ color: '#91A8CD', textAlign: 'center', fontSize: 'var(--iam-font-caption)' }}>V2.2</div>}</div>
        </Sider>
        <Layout className="main-layout">
          <Header className="app-header">
            <Button type="text" aria-label={collapsed ? '展开导航' : '折叠导航'} icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />} onClick={() => window.innerWidth < 768 ? setMobileNav(true) : setCollapsed(!collapsed)}/>
            <Breadcrumb className="header-breadcrumb" items={[{ title: <span onClick={() => go(visible.find(p => p.key === 'overview' && p.group === '总览')?.id || 'overview')} style={{ cursor: 'pointer' }}>工作台</span> }, { title: info?.group === '总览' ? '总览' : info?.group || '应用管理' }, ...(info?.label && info.label !== info.group && info.group !== '总览' ? [{ title: info.label }] : [])]}/>
            <div className="header-tools">
              <button className="global-search" onClick={() => setSearchOpen(true)} aria-label="搜索用户、应用或功能"><SearchOutlined /><span>搜索用户、应用或功能...</span><kbd>⌘ K</kbd></button>
              <span className="header-separator"/>
              <Button type="text" aria-label="通知" onClick={() => setNotifications(true)} icon={<Badge dot={attention.length > 0}><BellOutlined /></Badge>}/>
              <Tooltip title="使用帮助"><Button type="text" aria-label="使用帮助" icon={<QuestionCircleOutlined />} onClick={() => setHelpOpen(true)}/></Tooltip>
              <Dropdown menu={{ items: [{ key: 'profile', label: '个人中心', icon: <UserOutlined />, onClick: () => go('profile') }, { type: 'divider' }, { key: 'logout', label: '退出登录', icon: <LogoutOutlined />, onClick: logout }] }}>
                <Button type="text" className="account-button"><Avatar size={32} className="account-avatar" aria-hidden="true">{Array.from((session.name || session.username || '').trim())[0] || '用'}</Avatar><span className="account-copy"><span>{session.name || session.username}</span><small>{roleName}</small></span><DownOutlined style={{ fontSize: 9, color: '#8b99b3' }}/></Button>
              </Dropdown>
            </div>
          </Header>
          <Content className="main-content">
                        <PageBoundary key={domain + path}><Suspense fallback={<WorkspaceLoading label={loadingLabel}/>}><div className="route-page" key={domain + path}>{renderPage()}</div></Suspense></PageBoundary>
          </Content>
        </Layout>
      </Layout>
      <Drawer title="导航" open={mobileNav} onClose={() => setMobileNav(false)} placement="left" size={280} styles={{ body: { padding: 0, background: '#15243b' } }}><ProductCenterSwitcher logoUrl={db.settings[domain].logoUrl}/>{mobileMenu}</Drawer>
      <Modal title="搜索工作空间" open={searchOpen} onCancel={() => setSearchOpen(false)} footer={null} width={660} destroyOnHidden>
        <Input autoFocus size="small" prefix={<SearchOutlined />} allowClear placeholder="输入功能、应用或用户名称" value={search} onChange={e => setSearch(e.target.value)} aria-label="全局搜索内容"/>
        <div className="command-results">{matchingPages.map(p => <button className="command-result" key={p.id} onClick={() => go(p.id)}><span className="app-icon" style={{ width: 34, height: 34, background: '#eff3ff', color: '#677fec' }}>{groupIcons[p.group]}</span><div><strong>{p.label}</strong><small>{p.description}</small></div><Tag>功能</Tag><ArrowRightOutlined /></button>)}{matchingApps.map(a => <button className="command-result" key={a.id} onClick={() => go('apps/' + a.id)}><AppIcon app={a} size={34}/><div><strong>{a.name}</strong><small>{a.description}</small></div><Tag>应用</Tag></button>)}{matchingUsers.map(u => <button className="command-result" key={u.id} onClick={() => go((domain === 'public' ? 'persons' : 'organization') + '?user=' + u.id)}><UserAvatar user={u}/><div><strong>{u.name}</strong><small>{u.account}</small></div><Tag>用户</Tag></button>)}{!matchingPages.length && !matchingApps.length && !matchingUsers.length && <Empty description="未找到符合条件的内容"/>}</div>
      </Modal>
      <Drawer title="通知中心" open={notifications} onClose={() => setNotifications(false)} size={420}><div className="screen-top-note">当前身份域 · 业务通知</div>{attention.length ? attention.map(item => <div className="notification-row" key={item.key}><span className="attention-icon" style={{ background: '#edf2ff', color: '#627afa' }}>{item.icon}</span><div><strong>{item.title}</strong><p>{item.text}</p><Button type="link" style={{ padding: 0, fontSize: 'var(--iam-font-secondary)' }} onClick={() => go(item.key)}>前往处理 <ArrowRightOutlined /></Button></div></div>) : <Empty description="暂无待处理通知"/>}</Drawer>
      <Drawer title="使用帮助" open={helpOpen} onClose={() => setHelpOpen(false)} size={480}>
        <div className="section-intro"><BrandMark size={36}/><div><strong>{platformTitle}</strong><p>账户、应用与访问权限的一体化工作空间</p></div></div>
        <Descriptions size="small" column={1} items={[
            { key: 'scope', label: '当前身份域', children: domain === 'public' ? '公众身份域' : '政企身份域' },
            { key: 'role', label: '管理角色', children: roleName },
            { key: 'version', label: '平台版本', children: '2.2.0' },
        ]}/>
        <Card size="small" title="常用操作" className="section-gap"><div className="help-actions">
          {visible.filter(p => ['organization', 'apps', 'grants/users', 'audit/operations', 'persons', 'entities'].includes(p.key)).map(p => <Button key={p.id} block onClick={() => { setHelpOpen(false); go(p.id); }}>{p.label}<ArrowRightOutlined /></Button>)}
        </div></Card>
        <Card size="small" title="操作提示"><div className="help-copy"><p>使用顶部搜索快速定位用户、应用或功能，快捷键为 Ctrl / ⌘ + K。</p><p>授权前核对对象、角色与有效期；撤销单一来源不会移除其他仍有效的授权。</p><p>高风险操作请先检查影响范围。未保存的表单内容在关闭后不会保留。</p></div></Card>
      </Drawer>
    </div>;
}
