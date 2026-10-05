import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { App, Button, Input, Result, Tooltip } from 'antd';
import { BellOutlined, MenuFoldOutlined, MenuUnfoldOutlined, MoonOutlined, SearchOutlined, SunOutlined } from '@ant-design/icons';
import { Navigate, Route, Routes, useBlocker, useLocation, useNavigate } from 'react-router-dom';
import { CenterSwitcher } from '../../shared/centers/CenterSwitcher';
import { rememberCenter } from '../../shared/centers/navigation';
import { PlatformAccount, SidebarTools } from '../../app/PlatformShell';
import { ModuleIcon } from '../../design/ModuleIcon';
import { ResourceLoading } from '../components/ResourceLoading';
import { useStudio } from '../../features/er/store';
import { ResourceProvider } from '../services/context';
import { pages } from './config';
import { draftState } from './leave';
const navigationIcons: Record<string, string> = {
  overview: 'overview', layers: 'lineage', databases: 'catalog', bindings: 'sources',
  models: 'models', standardization: 'mapping', materialization: 'landing', logs: 'plans',
  warehouse: 'sources', 'catalog-overview': 'overview', 'catalog-entries': 'catalog',
  'catalog-reviews': 'review',
};
const Overview = lazy(() => import('../pages/Overview').then(m => ({ default: m.Overview })));
const LayersPage = lazy(() => import('../pages/Planning').then(m => ({ default: m.LayersPage })));
const DatabasesPage = lazy(() => import('../pages/Planning').then(m => ({ default: m.DatabasesPage })));
const BindingsPage = lazy(() => import('../pages/Planning').then(m => ({ default: m.BindingsPage })));
const ModelsPage = lazy(() => import('../pages/Models').then(m => ({ default: m.ModelsPage })));
const StandardizationPage = lazy(() => import('../pages/Models').then(m => ({ default: m.StandardizationPage })));
const ModelLogsPage = lazy(() => import('../pages/Models').then(m => ({ default: m.ModelLogsPage })));
const MaterializationPage = lazy(() => import('../pages/Materialization').then(m => ({ default: m.MaterializationPage })));
const WarehousePage = lazy(() => import('../pages/Warehouse').then(m => ({ default: m.WarehousePage })));
const CatalogEntriesPage = lazy(() => import('../pages/Catalog').then(m => ({ default: m.CatalogEntriesPage })));
const CatalogEditorPage = lazy(() => import('../pages/Catalog').then(m => ({ default: m.CatalogEditorPage })));
const CatalogReviewsPage = lazy(() => import('../pages/Catalog').then(m => ({ default: m.CatalogReviewsPage })));
const Designer = lazy(() => import('../pages/Designer').then(m => ({ default: m.Designer })));
/** Authentication and tenant changes are handled by the shared root AuthGate. */
export function ResourceApp() { return <ResourceShell/>; }
export function ResourceShell() {
 const { modal } = App.useApp(), nav = useNavigate(), location = useLocation();
 const [collapsed, setCollapsed] = useState(() => innerWidth < 1100), [query, setQuery] = useState('');
 const theme = useStudio(s => s.theme), dark = theme === 'dark';
 const current = [...pages].sort((a, b) => b.path.length - a.path.length).find(p => location.pathname.startsWith(p.path));
 const designer = /\/models\/[^/]+\/designer$/.test(location.pathname), checking = useRef(false);
 const blocker = useBlocker(({ currentLocation, nextLocation }) => draftState.dirty && (currentLocation.pathname !== nextLocation.pathname || currentLocation.search !== nextLocation.search));
 const leave = async () => { if (!draftState.dirty) return true; return new Promise<boolean>(resolve => modal.confirm({ title: '当前修改尚未保存', content: '保存后再离开，或取消继续编辑。', okText: draftState.save ? '保存并继续' : '放弃修改', cancelText: '继续编辑', onOk: async () => { if (draftState.save) await draftState.save(); draftState.dirty = false; resolve(true); }, onCancel: () => resolve(false) })); };
 useEffect(() => { if (blocker.state !== 'blocked' || checking.current) return; checking.current = true; void leave().then(ok => ok ? blocker.proceed?.() : blocker.reset?.()).finally(() => { checking.current = false; }); }, [blocker.state]);
 useEffect(() => { document.title = `${designer ? '逻辑建模' : current?.name ?? '资源总览'} · 数据资源中心`; rememberCenter('panshi'); }, [location.pathname, current?.name, designer]);
 useEffect(() => { const listener = (e: KeyboardEvent) => { if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k' && !designer) { e.preventDefault(); document.getElementById('resource-global-search')?.focus(); } }; window.addEventListener('keydown', listener); return () => window.removeEventListener('keydown', listener); }, [designer]);
 const toggle = () => useStudio.getState().setTheme(dark ? 'light' : 'dark');
 const groups = [...new Set(pages.map(p => p.group))];
 return <div className={`ps-app ${collapsed ? 'nav-collapsed' : ''}`}><div className="ps-frame">
  <aside className="ps-sidebar" inert={designer}><CenterSwitcher center="panshi" collapsed={collapsed} beforeLeave={leave}/><nav aria-label="数据资源中心导航">{groups.map(group => <section key={group || 'home'}>{group && !collapsed && <h3>{group}</h3>}{pages.filter(p => p.group === group).map(p => <Tooltip title={collapsed ? p.name : null} placement="right" key={p.key}><button aria-current={current?.key === p.key ? 'page' : undefined} className={`ps-nav-item ${current?.key === p.key ? 'active' : ''}`} onClick={() => { nav(p.path); if (innerWidth < 768) setCollapsed(true); }}><ModuleIcon name={navigationIcons[p.key]}/>{!collapsed && <span>{p.name}</span>}</button></Tooltip>)}</section>)}</nav><SidebarTools collapsed={collapsed} onCollapse={() => setCollapsed(!collapsed)} theme={theme} onTheme={toggle}/></aside>
  {!collapsed && <button className="ps-mobile-scrim" aria-label="收起导航" onClick={() => setCollapsed(true)}/>}
  <div className="ps-main"><header className="ps-topbar" inert={designer}><Button type="text" icon={collapsed ? <MenuUnfoldOutlined/> : <MenuFoldOutlined/>} aria-label={collapsed ? '展开菜单' : '收起菜单'} onClick={() => setCollapsed(!collapsed)}/><Input id="resource-global-search" className="ps-global-search" value={query} onChange={e => setQuery(e.target.value)} prefix={<SearchOutlined/>} suffix={<kbd>⌘ K</kbd>} placeholder="搜索数据资源、模型、目录或功能…" allowClear onPressEnter={() => { const term = query.trim(); if (!term) return; const match = pages.find(p => p.name.includes(term)); nav(match?.path ?? `/resource/catalog/entries?q=${encodeURIComponent(term)}`); }}/><span className="ps-top-spacer"/><Tooltip title={dark ? '浅色主题' : '深色主题'}><Button type="text" icon={dark ? <SunOutlined/> : <MoonOutlined/>} onClick={toggle} aria-label="切换主题"/></Tooltip><Tooltip title="查看操作记录"><Button type="text" icon={<BellOutlined/>} onClick={() => nav('/resource/models/logs')} aria-label="操作记录"/></Tooltip><PlatformAccount/></header>
  <main className="ps-content" id="main-content"><ResourceProvider><Suspense fallback={<ResourceLoading/>}><Routes>
  <Route path="/resource" element={<Navigate to="/resource/overview" replace/>}/><Route path="/resource/overview" element={<Overview/>}/><Route path="/resource/planning/layers" element={<LayersPage/>}/><Route path="/resource/planning/databases" element={<DatabasesPage/>}/><Route path="/resource/planning/bindings" element={<BindingsPage/>}/><Route path="/resource/models" element={<ModelsPage/>}/><Route path="/resource/models/standardization" element={<StandardizationPage/>}/><Route path="/resource/models/logs" element={<ModelLogsPage/>}/><Route path="/resource/models/:id/designer" element={<Designer onTheme={toggle} dark={dark}/>}/><Route path="/resource/models/designer/:id" element={<LegacyDesigner/>}/><Route path="/resource/materializations" element={<MaterializationPage/>}/><Route path="/resource/warehouse" element={<WarehousePage/>}/><Route path="/resource/catalog/overview" element={<Overview catalog/>}/><Route path="/resource/catalog/entries" element={<CatalogEntriesPage/>}/><Route path="/resource/catalog/entries/:id" element={<CatalogEditorPage/>}/><Route path="/resource/catalog/reviews" element={<CatalogReviewsPage/>}/><Route path="/resource/catalog/releases" element={<Navigate to="/resource/catalog/overview" replace/>}/><Route path="/resource/catalog/explore" element={<Navigate to="/resource/catalog/overview" replace/>}/><Route path="/resource/catalog/subscriptions" element={<Navigate to="/resource/catalog/overview" replace/>}/><Route path="*" element={<Result status="404" title="资源页面不存在" extra={<Button onClick={() => nav('/resource/overview')}>资源总览</Button>}/>}/>
   </Routes></Suspense></ResourceProvider></main></div></div></div>;
}
function LegacyDesigner() { const location = useLocation(); return <Navigate replace to={location.pathname.replace('/resource/models/designer/', '/resource/models/') + '/designer' + location.search}/>; }
