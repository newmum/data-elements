import { lazy, Suspense, useEffect, useState } from 'react';
import { Button, Input, Layout, Result, Spin, Tooltip } from 'antd';
import { AppstoreOutlined, BellOutlined, DatabaseOutlined, FolderOutlined, GlobalOutlined, MenuUnfoldOutlined, SearchOutlined, ShopOutlined, UnorderedListOutlined } from '@ant-design/icons';
import { Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import { CenterSwitcher, PlatformAccount, SidebarTools } from '../../app/PlatformShell';
import { useWorkspaceLifecycle } from '../../app/useWorkspaceLifecycle';
import { useStudio } from '../er/store';
import { centerPath, rememberCenter } from '../../shared/centers/navigation';
const Overview=lazy(()=>import('./overview/AssetOverviewPage').then(m=>({default:m.AssetOverviewPage})));
const Registration=lazy(()=>import('./registration/AssetRegistrationPage').then(m=>({default:m.AssetRegistrationPage})));
const MapPage=lazy(()=>import('./map/AssetMapPage').then(m=>({default:m.AssetMapPage})));
const Detail=lazy(()=>import('./detail/AssetDetailPage').then(m=>({default:m.AssetDetailPage})));
const Market=lazy(()=>import('./market/AssetMarketPage').then(m=>({default:m.AssetMarketPage})));
const MarketDetail=lazy(()=>import('./market/AssetMarketDetailPage').then(m=>({default:m.AssetMarketDetailPage})));
const Workbench=lazy(()=>import('./workbench/AssetWorkbenchPage').then(m=>({default:m.AssetWorkbenchPage})));
const menus=[{path:'/assets/overview',label:'资产全景',icon:<AppstoreOutlined/>},{path:'/assets/register',label:'资产登记',icon:<FolderOutlined/>},{path:'/assets/map',label:'资产地图',icon:<GlobalOutlined/>},{path:'/assets/market',label:'数据超市',icon:<ShopOutlined/>},{path:'/assets/workbench',label:'部门工作台',icon:<UnorderedListOutlined/>}];
export default function AssetCenter(){
 useWorkspaceLifecycle();
 const nav=useNavigate(),location=useLocation(),theme=useStudio(s=>s.theme);
 const [collapsed,setCollapsed]=useState(false),[search,setSearch]=useState('');
 const current=menus.find(m=>location.pathname===m.path||location.pathname.startsWith(m.path+'/'));
 useEffect(()=>{document.documentElement.dataset.center='asset';return()=>{if(document.documentElement.dataset.center==='asset')delete document.documentElement.dataset.center;};},[]);
 useEffect(()=>{rememberCenter('haoyue');document.title=(current?.label??'资产详情')+' · 数据资产中心';},[location.pathname,location.search,current?.label]);
 const go=(path:string)=>{nav(path);if(matchMedia('(max-width:767px)').matches)setCollapsed(true);};
 return <div className="application-frame haoyue-frame">{!collapsed&&<button className="shell-scrim" aria-label="收起导航菜单" onClick={()=>setCollapsed(true)}/>}<Layout className="app-shell governance-shell hy-shell"><Layout.Sider width={232} collapsedWidth={64} className="side-nav" collapsed={collapsed} breakpoint="lg" onBreakpoint={setCollapsed} trigger={null}>
 <CenterSwitcher collapsed={collapsed} center="asset"/>
 <nav className="wx-product-nav hy-nav" data-collapsed={collapsed} aria-label="资产功能导航"><section className="wx-nav-group">{!collapsed&&<h2>资产管理与流通</h2>}<ul>{menus.map(m=><li key={m.path}><Tooltip title={collapsed?m.label:null} placement="right"><button className="wx-nav-link" aria-label={m.label} aria-current={current?.path===m.path?'page':undefined} onClick={()=>go(m.path)}>{m.icon}{!collapsed&&<span className="wx-nav-label">{m.label}</span>}</button></Tooltip></li>)}</ul></section><section className="wx-nav-group">{!collapsed&&<h2>治理协同</h2>}<ul><li><Tooltip title={collapsed?'元数据与结构':null} placement="right"><button className="wx-nav-link" onClick={()=>go('/governance/metadata/catalog')} aria-label="元数据与结构"><DatabaseOutlined/>{!collapsed&&<span>元数据与结构</span>}</button></Tooltip></li><li><Tooltip title={collapsed?'标准与质量':null} placement="right"><button className="wx-nav-link" onClick={()=>go('/governance/standards/elements')} aria-label="标准与质量"><AppstoreOutlined/>{!collapsed&&<span>标准与质量</span>}</button></Tooltip></li></ul></section></nav>
 {!collapsed&&<div className="hy-side-context"><span>共享资产底座</span><p>关联元数据 · 标准 · 质量</p><Button type="link" onClick={()=>go(centerPath('wanxiang'))}>进入万象治理中心 →</Button></div>}
 <SidebarTools collapsed={collapsed} onCollapse={()=>setCollapsed(v=>!v)} theme={theme} onTheme={()=>useStudio.getState().setTheme(theme==='dark'?'light':'dark')}/>
 </Layout.Sider><Layout className="shell-main"><Layout.Header className="topbar"><Button type="text" className="wx-mobile-navigation-toggle" aria-label="展开导航菜单" icon={<MenuUnfoldOutlined/>} onClick={()=>setCollapsed(false)}/><Input className="global-search" value={search} onChange={e=>setSearch(e.target.value)} allowClear prefix={<SearchOutlined/>} placeholder="搜索应用、表、目录或 API…" onPressEnter={()=>go('/assets/map?q='+encodeURIComponent(search.trim()))}/><span className="topbar-spacer"/><Tooltip title="我的待办"><Button type="text" aria-label="我的待办" icon={<BellOutlined/>} onClick={()=>go('/assets/workbench/todos')}/></Tooltip><PlatformAccount/></Layout.Header><Layout.Content id="main-content" className="main-content"><Suspense fallback={<div className="hy-route-loading"><Spin description="正在打开资产页面"/></div>}><Routes>
 <Route path="/assets" element={<Navigate to="/assets/overview" replace/>}/><Route path="/assets/overview" element={<Overview/>}/><Route path="/assets/panorama" element={<Overview/>}/>
 <Route path="/assets/register" element={<Navigate to="/assets/register/catalogs" replace/>}/><Route path="/assets/register/:type" element={<Registration/>}/>
 <Route path="/assets/map" element={<MapPage/>}/><Route path="/assets/detail/:type/:id" element={<Detail/>}/><Route path="/assets/objects/:type/:id" element={<Detail/>}/>
 <Route path="/assets/market" element={<Market/>}/><Route path="/assets/market/:catalogId" element={<MarketDetail/>}/>
 <Route path="/assets/workbench" element={<Navigate to="/assets/workbench/todos" replace/>}/><Route path="/assets/workbench/:tab" element={<Workbench/>}/>
 <Route path="/assets/my" element={<Navigate to="/assets/register/catalogs" replace/>}/><Route path="/assets/applications" element={<Navigate to="/assets/register/applications" replace/>}/><Route path="/assets/databases" element={<Navigate to="/assets/register/databases" replace/>}/><Route path="/assets/catalogs" element={<Navigate to="/assets/register/catalogs" replace/>}/><Route path="/assets/apis" element={<Navigate to="/assets/register/apis" replace/>}/><Route path="/assets/department-workbench" element={<Navigate to="/assets/workbench/todos" replace/>}/>
 <Route path="*" element={<Result status="404" title="资产页面不存在" extra={<Button onClick={()=>go('/assets/overview')}>资产全景</Button>}/>}/>
 </Routes></Suspense></Layout.Content></Layout></Layout></div>;
}
