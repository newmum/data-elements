import { lazy, Suspense, useCallback, useEffect, useRef, useState, type CSSProperties } from 'react';
import { App as AntApp, Button, Input, Layout, Result, Space, Tooltip } from 'antd';
import { BellOutlined, SearchOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import { Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom';
import { ProductNavigation } from './ProductNavigation';
import { CenterSwitcher, SidebarTools, PlatformAccount } from './PlatformShell';
import { navigation, selectedMenu } from './routes';
import { useWorkspaceLifecycle } from './useWorkspaceLifecycle';
import { FullscreenWorkspace } from './FullscreenWorkspace';
import { backgroundFromState, backgroundUrl, canvasBackground, canvasDiagramId, fallbackCanvasBackground, isCanvasPath } from './canvasNavigation';
import { useCanvasLeaveGuard } from './useCanvasLeaveGuard';
import AuthGate, { clearWorkspaceView } from './AuthGate';
import StartupScene from './StartupScene';
import { useStudio } from '../features/er/store';
import { STUDIO_NAV_EVENT, studioPath, type StudioPage } from '../features/er/core/navigation';
import { getSession, logout } from '../services/api';
import { metadataWritable as canWrite } from '../services/metadata';
import { rememberCenter } from '../shared/centers/navigation';
// Do not parse every table editor and the full canvas engine before login/first
// paint. Each capability loads only when its route (or task drawer) is opened.
const OverviewPage=lazy(()=>import('../features/platform/OverviewPage').then(m=>({default:m.OverviewPage})));
const SourcesPage=lazy(()=>import('../features/platform/SourcesPage').then(m=>({default:m.SourcesPage})));
const ModelsPage=lazy(()=>import('../features/platform/ModelsPage').then(m=>({default:m.ModelsPage})));
const CollectionPage=lazy(()=>import('../features/platform/MetadataPages').then(m=>({default:m.CollectionPage})));
const CatalogPage=lazy(()=>import('../features/platform/MetadataPages').then(m=>({default:m.CatalogPage})));
const ElementsPage=lazy(()=>import('../features/platform/StandardPages').then(m=>({default:m.ElementsPage})));
const ReviewPage=lazy(()=>import('../features/platform/StandardPages').then(m=>({default:m.ReviewPage})));
const MappingPage=lazy(()=>import('../features/platform/StandardPages').then(m=>({default:m.MappingPage})));
const CodesPage=lazy(()=>import('../features/platform/StandardPages').then(m=>({default:m.CodesPage})));
const EncodingPage=lazy(()=>import('../features/platform/StandardPages').then(m=>({default:m.EncodingPage})));
const RulesPage=lazy(()=>import('../features/platform/QualityPages').then(m=>({default:m.RulesPage})));
const PlansPage=lazy(()=>import('../features/platform/QualityPages').then(m=>({default:m.PlansPage})));
const ProfilingPage=lazy(()=>import('../features/platform/QualityPages').then(m=>({default:m.ProfilingPage})));
const ReportsPage=lazy(()=>import('../features/platform/QualityPages').then(m=>({default:m.ReportsPage})));
const OrdersPage=lazy(()=>import('../features/platform/QualityPages').then(m=>({default:m.OrdersPage})));
const QualityTaskCenter=lazy(()=>import('../features/platform/QualityPages').then(m=>({default:m.QualityTaskCenter})));
const LineagePage=lazy(()=>import('../features/platform/LineageAnalysisPage'));
const ERModule = lazy(() => import('../features/er/app/App'));
const ResourceApp = lazy(() => import('../panshi/app/ResourceApp').then(m => ({ default: m.ResourceApp })));
const AssetCenter = lazy(() => import('../features/assets/AssetCenter'));
function Loading() { return <StartupScene state="loading" phase="module" contained/>; }
function CanvasRoute({ id }: {
    id?: string;
}) {
    const data = useStudio(s => s.data);
    const ready = useStudio(s => s.ready);
    const [error, setError] = useState('');
    const pending = !!id && data?.activeDiagramId !== id;
    useEffect(() => {
        let live = true;
        if (ready && id && data?.activeDiagramId !== id) {
            setError('');
            void useStudio.getState().switchDiagram(id).catch(e => {
                if (live)
                    setError(e instanceof Error ? e.message : String(e));
            });
        }
        return () => { live = false; };
    }, [ready, id, data?.activeDiagramId]);
    if (error)
        return <Result status="error" title="无法打开此模型" subTitle={error}/>;
    if (!ready || pending)
        return <Loading />;
    return <ERModule view="canvas"/>;
}
function LegacyDiagram() { const { id } = useParams(); return <Navigate replace to={studioPath('canvas', id)}/>; }
function Shell() {
    useWorkspaceLifecycle();
    const { message, modal } = AntApp.useApp();
    const nav = useNavigate(), location = useLocation();
    const theme = useStudio(s => s.theme), ready = useStudio(s => s.ready), error = useStudio(s => s.loadError);
    const [collapsed, setCollapsed] = useState(false), [query, setQuery] = useState(''), [tasks, setTasks] = useState(false);
    const isER = isCanvasPath(location.pathname);
    const lastPage = useRef(canvasBackground(location));
    const pageLocation = isER ? (backgroundFromState(location.state) ?? lastPage.current ?? fallbackCanvasBackground()) : location;
    const current = selectedMenu(pageLocation.pathname);
    const leaving = useCanvasLeaveGuard();
    useEffect(() => {
        if (!isER)
            lastPage.current = canvasBackground(location);
    }, [location, isER]);
    const safeNavigate = useCallback((path: string) => {
        if (window.matchMedia('(max-width:767px)').matches)
            setCollapsed(true);
        if (isCanvasPath(path.split('?')[0]))
            nav(path, { replace: isER, state: { canvasBackground: canvasBackground(pageLocation) ?? fallbackCanvasBackground(), canvasOpened: true } });
        else
            nav(path);
    }, [nav, isER, pageLocation]);
    const closeCanvas = () => {
        if (location.state?.canvasOpened && backgroundFromState(location.state))
            nav(-1);
        else
            nav(backgroundUrl(canvasBackground(pageLocation) ?? fallbackCanvasBackground()), { replace: true });
    };
    useEffect(() => {
        const navigate = (e: Event) => {
            const d = (e as CustomEvent<{
                page: StudioPage;
                diagramId?: string;
            }>).detail;
            safeNavigate(studioPath(d.page, d.diagramId));
        };
        window.addEventListener(STUDIO_NAV_EVENT, navigate);
        return () => { window.removeEventListener(STUDIO_NAV_EVENT, navigate); };
    }, [safeNavigate]);
    useEffect(() => { rememberCenter('wanxiang'); document.title = `${isER ? 'ER 全屏工作台' : current?.label ?? '数据治理'} · 数据治理中心`; }, [isER, current?.label, location.pathname]);
    useEffect(() => {
        const key = (e: KeyboardEvent) => {
            if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k' && !isER) {
                e.preventDefault();
                document.getElementById('global-center-search')?.focus();
            }
        };
        window.addEventListener('keydown', key);
        return () => window.removeEventListener('keydown', key);
    }, [isER]);
    useEffect(()=>{let last='';const localError=(e:Event)=>{const text=(e as CustomEvent<string>).detail;if(text!==last){last=text;message.error(text,8);}};window.addEventListener('wanxiang:local-error',localError);return()=>window.removeEventListener('wanxiang:local-error',localError);},[message]);
    const signout = () => modal.confirm({ title: '保存并退出当前工作区？', onOk: async () => {
            if(canWrite())await useStudio.getState().save();
            if (useStudio.getState().saveState === 'error')
                throw new Error('当前模型保存失败，暂不退出');
            await logout();
            clearWorkspaceView();
            window.dispatchEvent(new Event('wanxiang:session-expired'));
        } });
    if (!getSession()?.authenticated)
        return <Result title="暂无工作区访问权限" subTitle="请联系工作区管理员添加成员。" extra={<Button onClick={signout}>退出</Button>}/>;
    // Only the canvas needs the ER workspace before its route can render.
    // Unknown paths must reach the 404 route instead of waiting forever for
    // an initialization that useWorkspaceLifecycle never starts for them.
    if (!ready && isER)
        return <StartupScene state="loading" phase="workspace"/>;
    if (error && isER)
        return <Result status="error" title="共享元数据加载失败" subTitle={error} extra={<Space><Button onClick={() => useStudio.getState().initialize()}>重试</Button><Button onClick={signout}>退出</Button></Space>}/>;
    const toggle = () => useStudio.getState().setTheme(theme === 'light' ? 'dark' : 'light');
    return <><div className="application-frame" inert={isER} aria-hidden={isER ? true : undefined}>{!collapsed && <button className="shell-scrim" aria-label="收起导航菜单" onClick={() => setCollapsed(true)}/>}<Layout className="app-shell governance-shell" style={{ '--center-accent': 'var(--primary)', '--center-accent-soft': 'var(--primary-soft)', '--center-accent-hover': 'var(--surface-hover)' } as CSSProperties}><Layout.Sider className="side-nav" width={232} collapsedWidth={64} collapsed={collapsed} breakpoint="lg" onBreakpoint={setCollapsed} trigger={null}><CenterSwitcher collapsed={collapsed}/><ProductNavigation collapsed={collapsed} selected={isER ? '/governance/metadata/er' : current?.key ?? '/governance/overview'} onNavigate={safeNavigate}/><SidebarTools collapsed={collapsed} onCollapse={()=>setCollapsed(!collapsed)} theme={theme} onTheme={toggle}/></Layout.Sider><Layout className="shell-main"><Layout.Header className="topbar"><Button className="wx-mobile-navigation-toggle" type="text" aria-label="展开导航菜单" icon={<MenuUnfoldOutlined/>} onClick={()=>setCollapsed(false)}/><Input id="global-center-search" className="global-search" prefix={<SearchOutlined />} value={query} onChange={e => setQuery(e.target.value)} placeholder="搜索数据表、字段或功能…" suffix={<kbd className="global-search-shortcut">⌘ K</kbd>} allowClear onPressEnter={() => {
            const term = query.trim();
            if (term) {
                const menu = navigation.flatMap(g => g.children).find(i => i.label === term);
                safeNavigate(menu?.key ?? `/governance/metadata/catalog?q=${encodeURIComponent(term)}`);
            }
        }}/><span className="topbar-spacer"/><Tooltip title="质检执行中心"><Button type="text" aria-label="任务中心" icon={<BellOutlined />} onClick={() => setTasks(true)}/></Tooltip><PlatformAccount/></Layout.Header><Layout.Content className="main-content" id="main-content"><Suspense fallback={<Loading />}><Routes location={pageLocation}>
 <Route path="/governance/overview" element={<OverviewPage />}/><Route path="/governance/metadata/sources" element={<SourcesPage />}/><Route path="/governance/metadata/collection" element={<CollectionPage />}/><Route path="/governance/metadata/catalog" element={<CatalogPage />}/><Route path="/governance/metadata/models" element={<ModelsPage />}/><Route path="/governance/metadata/mapping" element={<MappingPage />}/><Route path="/governance/metadata/lineage" element={<LineagePage />}/><Route path="/governance/standards/elements" element={<ElementsPage />}/><Route path="/governance/standards/review" element={<ReviewPage />}/><Route path="/governance/standards/landing" element={<MappingPage landing/>}/><Route path="/governance/standards/codes" element={<CodesPage />}/><Route path="/governance/standards/encoding" element={<EncodingPage />}/><Route path="/governance/quality/profiling" element={<ProfilingPage />}/><Route path="/governance/quality/profiling/reports" element={<ReportsPage profile/>}/><Route path="/governance/quality/rules" element={<RulesPage />}/><Route path="/governance/quality/plans" element={<PlansPage />}/><Route path="/governance/quality/reports" element={<ReportsPage />}/><Route path="/governance/quality/workorders" element={<OrdersPage />}/><Route path="/" element={<Navigate replace to="/governance/overview"/>}/><Route path="/governance" element={<Navigate replace to="/governance/overview"/>}/><Route path="/diagrams" element={<Navigate replace to="/governance/metadata/models"/>}/><Route path="/diagrams/:id" element={<LegacyDiagram />}/><Route path="/resource/models/designer/:id" element={<LegacyDiagram />}/><Route path="/resource/overview" element={<Result status="404" title="数据资源中心不在此地址" subTitle="当前地址提供数据治理中心。请从左上角的系统切换入口进入数据资源中心。" extra={<Button onClick={() => safeNavigate('/governance/overview')}>打开治理总览</Button>}/>}/><Route path="/integration" element={<Navigate replace to="/governance/metadata/sources"/>}/><Route path="*" element={<Result status="404" title="页面不存在" extra={<Button onClick={() => safeNavigate('/governance/overview')}>治理总览</Button>}/>}/>
 </Routes></Suspense></Layout.Content></Layout></Layout></div><Suspense fallback={<Loading />}>{tasks && <QualityTaskCenter onClose={() => setTasks(false)}/>}</Suspense>{isER && <FullscreenWorkspace busy={leaving} onClose={closeCanvas}><Suspense fallback={<Loading />}><CanvasRoute id={canvasDiagramId(location.pathname)}/></Suspense></FullscreenWorkspace>}</>;
}
function CenterRoute() {
    const location = useLocation(), nav = useNavigate();
    useEffect(() => { const change = (event: Event) => { const path = (event as CustomEvent<string>).detail; if (/^\/(?:resource|governance|assets)(?:\/|$)/.test(path)) nav(path); }; window.addEventListener('wanxiang:center-navigate', change); return () => window.removeEventListener('wanxiang:center-navigate', change); }, [nav]);
    return /^\/resource(?:\/|$)/.test(location.pathname) ? <Suspense fallback={<Loading/>}><ResourceApp/></Suspense> : /^\/assets(?:\/|$)/.test(location.pathname) ? <Suspense fallback={<Loading/>}><AssetCenter/></Suspense> : <Shell/>;
}
export default function App() { return <AuthGate><CenterRoute/></AuthGate>; }
