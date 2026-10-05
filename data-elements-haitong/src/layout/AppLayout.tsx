import { App, Avatar, Button, Descriptions, Divider, Dropdown, Input, Modal, Space, Tooltip } from 'antd';
import { CheckOutlined, LogoutOutlined } from '@ant-design/icons';
import { useEffect, useState } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import Icon from '../design/Icon';
import { useOceanTheme } from '../design/theme';
import { routes } from '../app/routes';
import { useWorkbench } from '../app/Workbench';
import { downloadText, restoreWorkspace } from '../services/workspace';
import { IconButton } from '../components/Common';
import { useQuery } from '@tanstack/react-query';
import { platformApi } from '../api/platformApi';
import { useSessionRevision } from '../nifi/api/bridgeSession';
import { centers, centerGroups, centerMarks } from './centerNavigation';
import { logoutPlatformSession } from '../services/platformSession';
import './centerSwitcher.css';
import './account.css';
const accountText=(value:unknown)=>value==null?'':String(value).trim();
export default function AppLayout({children,live=false}:{children:React.ReactNode;live?:boolean}){
 const {dark,toggle,motion,toggleMotion}=useOceanTheme();const {state,showTask,showRun}=useWorkbench();const navigate=useNavigate(),location=useLocation();const {message,modal}=App.useApp();
 const sessionRevision=useSessionRevision();
 const account=useQuery({queryKey:['platform','account',sessionRevision],enabled:live,queryFn:async()=>{
  const user=await platformApi<Record<string,unknown>>('/sym/user/me',{});
  return {name:accountText(user.realName||user.userName),avatar:accountText(user.avatar),department:accountText(user.orgName||user.departmentName||user.deptName),user};
 }});
 const accountName=live?account.data?.name||'当前用户':'林澄';
 const [accountOpen,setAccountOpen]=useState(false),[signingOut,setSigningOut]=useState(false);
 const tenant=useQuery({queryKey:['platform','tenant',sessionRevision],enabled:live&&accountOpen,queryFn:()=>platformApi<Record<string,unknown>>('/sym/tenant/current',{})});
 const signout=async()=>{if(signingOut)return;setSigningOut(true);try{await logoutPlatformSession();}catch(error){message.error(error instanceof Error?error.message:String(error));setSigningOut(false);}};
 const [collapsed,setCollapsed]=useState(()=>localStorage.getItem('haitong:collapsed')==='true');const [mobileNav,setMobileNav]=useState(false);const [searchOpen,setSearchOpen]=useState(false),[query,setQuery]=useState('');const [noticeOpen,setNoticeOpen]=useState(false);
 const current=routes.find(r=>r.path===location.pathname)||routes[0];
 useEffect(()=>{try{localStorage.setItem('haitong:collapsed',String(collapsed));}catch{}},[collapsed]);
 useEffect(()=>{const listener=(e:KeyboardEvent)=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k'){e.preventDefault();setSearchOpen(v=>!v);}};window.addEventListener('keydown',listener);return()=>window.removeEventListener('keydown',listener);},[]);
 const importBackup=()=>{const input=document.createElement('input');input.type='file';input.accept='.json';input.onchange=async()=>{const file=input.files?.[0];if(!file)return;if(file.size>10*1024*1024){message.error('备份不能超过 10 MB');return;}try{const parsed:unknown=JSON.parse(await file.text());modal.confirm({title:'导入并替换本地工作区？',content:'请先导出当前备份。导入后所有任务暂停、待执行作业取消，不会自动运行。',okText:'确认导入',onOk:async()=>{await restoreWorkspace(parsed);message.success('已导入，请检查后按需恢复任务');}});}catch(e){message.error(String(e));}};input.click();};
 return <div className={`ht-shell ${collapsed?'is-collapsed':''} ${mobileNav?'mobile-nav-open':''}`}>
  <header className="ht-topbar">
   <Dropdown trigger={['click']} placement="bottomLeft" menu={{className:'ht-center-switcher-menu',selectedKeys:['integration'],items:centerGroups.map(([label,keys])=>({type:'group' as const,label,children:keys.map(key=>{const center=centers.find(item=>item.key===key)!;const [symbol,color]=centerMarks[key];return {key,label:<span className={`ht-center-option ${key==='integration'?'is-current':''}`}><span className="ht-center-mark" style={{background:color}} aria-hidden="true">{symbol}</span><span className="ht-center-option-copy"><b>{center.name}</b><small>{label==='平台支撑'?'平台支撑 · ':''}{center.description}</small></span>{key==='integration'&&<span className="ht-center-current"><CheckOutlined/> 当前</span>}</span>};})})),onClick:({key})=>{if(key==='integration')return;const center=centers.find(item=>item.key===key);if(center)window.location.assign(center.url);}}}><button className="ht-brand" type="button" aria-label="切换系统" title="切换系统"><img src="brand/haitong-mark.svg" alt=""/><div><strong>海通数据集成中心</strong><small>政务大数据平台</small></div><Icon name="down" size={15}/></button></Dropdown>
   <div className="ht-top-left"><div className="desktop-collapse"><IconButton icon="menu" label={collapsed?'展开导航':'收起导航'} onClick={()=>setCollapsed(v=>!v)}/></div><div className="mobile-collapse"><IconButton icon="menu" label="打开导航" onClick={()=>setMobileNav(v=>!v)}/></div><button className="top-search" onClick={()=>setSearchOpen(true)}><Icon name="search" size={17}/><span>搜索任务、账单或功能…</span><kbd>⌘ K</kbd></button></div>
   <div className="ht-top-actions"><IconButton icon={dark?'sun':'moon'} label={dark?'切换浅色':'切换深色'} onClick={toggle}/><button className="notification-button" aria-label="运行通知" onClick={()=>setNoticeOpen(true)}><Icon name="bell"/>{!live&&state.runs.some(r=>r.status==='FAILED')&&<i/>}</button><button className="profile-button" onClick={()=>setAccountOpen(true)} aria-label="查看个人信息"><Avatar src={live?account.data?.avatar||undefined:undefined} size={32} style={{background:dark?'#294477':'#E8EFFF',color:dark?'#C2D5FF':'#2457D6'}}>{accountName.slice(0,1)}</Avatar><span><b>{accountName}</b><small>{live?account.data?.department||'未提供部门':'本地演示'}</small></span><Icon name="down" size={14}/></button></div>
  </header>
  {mobileNav&&<button className="nav-scrim" aria-label="关闭导航遮罩" onClick={()=>setMobileNav(false)}/>}
  <aside className="ht-sidebar"><nav aria-label="业务导航">{['集成工作台','任务开发','任务运维','数据对账'].map(group=><section className="nav-group" key={group}><span className="nav-group-label">{group}</span>{routes.filter(r=>r.group===group).map(r=><Tooltip key={r.path} title={collapsed?r.title:undefined} placement="right"><button className={`nav-link ${r.path===location.pathname?'selected':''}`} aria-current={r.path===location.pathname?'page':undefined} aria-label={r.title} onClick={()=>{navigate(r.path);setMobileNav(false);}}><Icon name={r.icon} size={22}/>{!collapsed&&<span>{r.title}</span>}</button></Tooltip>)}</section>)}</nav><footer className="sidebar-footer"><Icon name="wave" size={24}/>{!collapsed&&<div><strong>连接有序 · 流转有据</strong><span>HAITONG · DATA INTEGRATION</span></div>}</footer></aside>
  <main className="ht-main" id="main"><div className="breadcrumbs"><span>{current.group}</span><Icon name="chevron" size={13}/><b>{current.title}</b><span className="runtime-indicator"><i/>{live?'数据中台':'本地工作区'}</span></div>{account.isError&&live&&<div role="alert" className="runtime-error">{account.error instanceof Error?account.error.message:'当前会话读取失败'}</div>}{children}</main>
  <Modal title="快速查找" open={searchOpen} footer={null} onCancel={()=>setSearchOpen(false)} destroyOnClose width={600}><Input autoFocus prefix={<Icon name="search"/>} placeholder="输入任务或功能名称" value={query} onChange={e=>setQuery(e.target.value)}/><div className="command-results">{routes.filter(r=>r.title.includes(query)).map(r=><button key={r.path} onClick={()=>{navigate(r.path);setSearchOpen(false);}}><Icon name={r.icon}/><span>{r.title}<small>{r.group}</small></span><Icon name="arrow" size={16}/></button>)}{state.tasks.filter(t=>!t.archived&&t.name.includes(query)).slice(0,8).map(t=><button key={t.id} onClick={()=>{showTask(t.id);setSearchOpen(false);}}><Icon name="tasks"/><span>{t.name}<small>{t.code}</small></span><Icon name="arrow" size={16}/></button>)}</div></Modal>
  <Modal title="运行通知" open={noticeOpen} footer={null} onCancel={()=>setNoticeOpen(false)}><div className="command-results">{live?<p>实时通知接口尚未接入，请前往任务监控查看运行状态。</p>:<>{state.runs.filter(r=>r.status==='FAILED').map(r=><button key={r.id} onClick={()=>{showRun(r.id);setNoticeOpen(false);}}><Icon name="warning"/><span>{r.task.name}<small>运行失败，查看表级原因</small></span></button>)}{!state.runs.some(r=>r.status==='FAILED')&&<p>当前没有失败运行。</p>}</>}</div></Modal>
  <Modal className="ht-account-modal" title="个人与租户" open={accountOpen} width={640} onCancel={()=>!signingOut&&setAccountOpen(false)} footer={<Space>{live&&<Button danger icon={<LogoutOutlined/>} loading={signingOut} onClick={signout}>退出登录</Button>}{!live&&<><Button onClick={toggleMotion}>{motion?'关闭界面动效':'开启界面动效'}</Button><Button onClick={()=>downloadText('haitong-workspace.json',JSON.stringify(state,null,2),'application/json')}>导出备份</Button><Button onClick={importBackup}>导入备份</Button></>}<Button onClick={()=>setAccountOpen(false)} disabled={signingOut}>关闭</Button></Space>}>
   <div className="ht-account-summary"><Avatar src={live?account.data?.avatar||undefined:undefined} size={48}>{accountName.slice(0,1)}</Avatar><div><strong>{accountName}</strong><span>{live?account.data?.department||'服务端未提供':'本地演示工作区'}</span></div></div>
   <Descriptions bordered size="small" column={{xs:1,sm:2}} items={[{key:'name',label:'姓名',children:accountName},{key:'account',label:'账号',children:live?accountText(account.data?.user.userName)||'服务端未提供':'本地演示'},{key:'org',label:'所属机构',children:live?account.data?.department||'服务端未提供':'本地演示'},{key:'phone',label:'手机号',children:live?accountText(account.data?.user.phone)||'服务端未提供':'本地演示'},{key:'tenant',label:'当前租户',children:live?accountText(tenant.data?.name||tenant.data?.tenantName)||'服务端未提供':'本地演示'},{key:'roles',label:'角色',children:live&&Array.isArray(account.data?.user.roles)&&account.data.user.roles.length?account.data.user.roles.map(String).join('、'):'服务端未提供'}]}/>
   {live&&<><Divider/><p className="ht-account-note">身份、所属机构及租户信息由数据中台统一维护；信息变更后请刷新当前页面。</p></>}
  </Modal>
 </div>;
}
