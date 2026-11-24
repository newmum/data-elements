import type {ReactNode} from 'react';
import {Button,Drawer} from 'antd';
import {SaveOutlined} from '@ant-design/icons';
import ModelingNodeHeading from './ModelingNodeHeading';

type Props={open:boolean;name?:string;selectionKey:string;start:boolean;onRename:(name:string)=>void;onClose:()=>void;onSave:()=>boolean;saveStatus:string;children:ReactNode};
/** Keep the default body portal: the drawer must cover the complete viewport, not the canvas. */
export default function ModelingConfigDrawer({open,name,selectionKey,start,onRename,onClose,onSave,saveStatus,children}:Props){
 return <Drawer open={open} placement="right" width="min(860px, calc(100vw - 24px))" rootClassName={`modeling-config-drawer ${start?'is-start-configuration':''}`} rootStyle={{position:'fixed',inset:0}} zIndex={1200} maskClosable keyboard autoFocus closable={false} onClose={onClose} extra={<Button type="text" className="modeling-drawer-close" onClick={onClose}>关闭</Button>} title={<div className="modeling-drawer-heading"><ModelingNodeHeading key={selectionKey} name={name} onRename={onRename}/></div>} styles={{body:{padding:0,overflow:'hidden',display:'flex',flexDirection:'column'},footer:{padding:'14px 24px'}}} footer={start?null:<div className="modeling-drawer-actions"><span role="status">{saveStatus}</span><Button onClick={onClose}>关闭</Button><Button type="primary" icon={<SaveOutlined/>} onClick={()=>onSave()}>保存模型</Button></div>}>
  <div className={start?'modeling-drawer-start':'modeling-drawer-content'}>{children}</div>
 </Drawer>;
}
