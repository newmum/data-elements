import {useRef,useState} from 'react';
import {Dropdown,type MenuProps} from 'antd';
export type TopMenu={label:string;items:MenuProps['items']};
export function hoverTopMenu(current:string|null,next:string){return current===null?null:next}
export function closeTopMenu(current:string|null,closing:string){return current===closing?null:current}
export default function TopMenuBar({menus,onAction}:{menus:TopMenu[];onAction:(key:string)=>void}){
 const [open,setOpen]=useState<string|null>(null);const [keyboard,setKeyboard]=useState(false);const buttons=useRef<(HTMLButtonElement|null)[]>([]);
 function move(index:number,delta:number){const next=(index+delta+menus.length)%menus.length;buttons.current[next]?.focus();setOpen(current=>hoverTopMenu(current,menus[next].label))}
 return <div role="menubar" aria-label="主菜单" onKeyDown={e=>{if(e.key==='Escape'){e.preventDefault();const index=menus.findIndex(m=>m.label===open);setOpen(null);buttons.current[index]?.focus()}}}>{menus.map((m,index)=><Dropdown key={m.label} open={open===m.label} autoFocus={keyboard} onOpenChange={value=>setOpen(current=>value?m.label:closeTopMenu(current,m.label))} menu={{items:m.items,onClick:({key})=>{setOpen(null);onAction(key)},onKeyDown:e=>{if(e.key==='ArrowRight'||e.key==='ArrowLeft'){e.preventDefault();move(index,e.key==='ArrowRight'?1:-1)}}}} trigger={['click']}><button ref={el=>{buttons.current[index]=el}} type="button" role="menuitem" aria-haspopup="menu" aria-expanded={open===m.label} className="menu-button" onPointerDown={()=>setKeyboard(false)} onMouseEnter={()=>{setKeyboard(false);setOpen(current=>hoverTopMenu(current,m.label))}} onKeyDown={e=>{if(e.key==='ArrowRight'||e.key==='ArrowLeft'){e.preventDefault();move(index,e.key==='ArrowRight'?1:-1)}else if(e.key==='ArrowDown'){e.preventDefault();setKeyboard(true);setOpen(m.label)}else if(e.key==='Tab')setOpen(null)}}>{m.label}</button></Dropdown>)}</div>
}
