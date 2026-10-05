import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PanshiLogo } from '../panshi/design/PanshiLogo';
import { centerPath } from '../shared/centers/navigation';
import { App, Button, Descriptions, Divider, Dropdown, Modal, Space, Tooltip } from 'antd';
import { DownOutlined, HomeOutlined, LogoutOutlined, MenuFoldOutlined, MenuUnfoldOutlined, MoonOutlined, SunOutlined, CheckOutlined } from '@ant-design/icons';
import { BrandMark } from '../design/BrandMark';
import { centers, centerGroups, centerMarks } from '../shared/centerNavigation';
import { dataPlatformHomeUrl, logoutPlatformSession } from '../shared/platformSession';
import { getSession } from '../services/api';
export function CenterSwitcher({collapsed,center='governance',beforeLeave}:{collapsed:boolean;center?:'resource'|'governance'|'asset';beforeLeave?:()=>Promise<boolean>}) {
 const nav=useNavigate(),{message}=App.useApp();
 const change=async(key:string)=>{if(key===center)return;try{if(beforeLeave&&!await beforeLeave())return;if(key==='resource'||key==='governance'||key==='asset'){nav(centerPath(key==='resource'?'panshi':key==='asset'?'haoyue':'wanxiang'));return;}const destination=centers.find(c=>c.key===key);if(destination)location.assign(destination.url);}catch(e){message.error(e instanceof Error?e.message:String(e));}};
 return <Dropdown trigger={['click']} placement="bottomLeft" menu={{className:'wx-center-switcher-menu',selectedKeys:[center],items:centerGroups.map(([label,keys])=>({type:'group' as const,label,children:keys.map(key=>{const c=centers.find(item=>item.key===key)!;const [symbol,color]=centerMarks[key];return {key,label:<span className={`wx-center-option ${key===center?'is-current':''}`}><span className="wx-center-mark" style={{background:color}} aria-hidden="true">{symbol}</span><span className="wx-center-option-copy"><b>{c.name}</b><small>{label==='平台支撑'?'平台支撑 · ':''}{c.description}</small></span>{key===center&&<span className="wx-center-current"><CheckOutlined/> 当前</span>}</span>};})})),onClick:({key})=>{void change(key);}}}>
  <button className={`wanxiang-wordmark ${center==='resource'?'ps-wordmark':''}`} type="button" aria-label="切换系统" title="切换系统">{center==='resource'?<PanshiLogo/>:center==='asset'?<span className="hy-brand-mark"><MoonOutlined/></span>:<BrandMark/>}{!collapsed&&<><div><b>{center==='resource'?'数据资源中心':center==='asset'?'数据资产中心':'数据治理中心'}</b><small>政务大数据平台</small></div><DownOutlined className="wx-center-caret"/></>}</button>
 </Dropdown>;
}
export function SidebarTools({collapsed,onCollapse,theme,onTheme}:{collapsed:boolean;onCollapse:()=>void;theme:string;onTheme:()=>void}) {
 return <div className="side-nav-footer wx-sidebar-tools" data-collapsed={collapsed}><Tooltip title={collapsed?'展开菜单':'收起菜单'} placement="right"><Button type="text" aria-label={collapsed?'展开菜单':'收起菜单'} icon={collapsed?<MenuUnfoldOutlined/>:<MenuFoldOutlined/>} onClick={onCollapse}/></Tooltip>{!collapsed&&<><Tooltip title="返回数据中台"><Button type="text" aria-label="返回数据中台" icon={<HomeOutlined/>} onClick={()=>location.assign(dataPlatformHomeUrl())}/></Tooltip><Tooltip title={theme==='light'?'切换深色皮肤':'切换浅色皮肤'}><Button type="text" aria-label="切换皮肤" icon={theme==='light'?<MoonOutlined/>:<SunOutlined/>} onClick={onTheme}/></Tooltip></>}</div>;
}
export function PlatformAccount() {
 const [open,setOpen]=useState(false),[busy,setBusy]=useState(false);const {message}=App.useApp();
 const session=getSession(),user=session?.user,tenant=session?.tenant;
 const signout=async()=>{if(busy)return;setBusy(true);try{await logoutPlatformSession();}catch(e){message.error(e instanceof Error?e.message:String(e));setBusy(false);}};
 return <><button className="wx-user-menu" aria-label="查看个人信息" onClick={()=>setOpen(true)}><span className="wx-avatar">{session?.name?.slice(0,1)||'用'}</span><span className="wx-user-copy"><b>{session?.name||'当前用户'}</b><small>{session?.department||'未提供部门'}</small></span><DownOutlined style={{fontSize:10}}/></button><Modal open={open} title="个人与租户" width={640} onCancel={()=>!busy&&setOpen(false)} footer={<Space><Button danger icon={<LogoutOutlined/>} loading={busy} onClick={signout}>退出登录</Button><Button onClick={()=>setOpen(false)} disabled={busy}>关闭</Button></Space>}>
 <div className="wx-account-summary"><span className="wx-avatar">{session?.name?.slice(0,1)||'用'}</span><div><b>{session?.name||'当前用户'}</b><span>{session?.department||'服务端未提供'}</span></div></div>
 <Descriptions bordered size="small" column={{xs:1,sm:2}} items={[{key:'name',label:'姓名',children:session?.name||'服务端未提供'},{key:'account',label:'账号',children:user?.userName||'服务端未提供'},{key:'org',label:'所属机构',children:session?.department||'服务端未提供'},{key:'phone',label:'手机号',children:user?.phone||'服务端未提供'},{key:'tenant',label:'当前租户',children:tenant?.name||tenant?.tenantName||'服务端未提供'},{key:'roles',label:'角色',children:Array.isArray(user?.roles)&&user.roles.length?user.roles.join('、'):'服务端未提供'}]}/><Divider/><p className="wx-account-note">身份、所属机构及租户会话由数据中台统一维护；如信息发生变更，请返回数据中台完成维护后刷新当前页面。</p></Modal></>;
}
