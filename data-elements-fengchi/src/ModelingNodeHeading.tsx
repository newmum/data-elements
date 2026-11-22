import {useRef,useState} from 'react';
import {Button,Input} from 'antd';
import {EditOutlined} from '@ant-design/icons';

type Props={name?:string;onRename:(name:string)=>void;disabled?:boolean};
/** The draft belongs to the selected node; the parent keys this heading by selection. */
export default function ModelingNodeHeading({name,onRename,disabled=false}:Props){
 const [editing,setEditing]=useState(false),[draft,setDraft]=useState(name||'');
 const active=useRef(false);
 function commit(){
  if(!active.current)return;
  active.current=false;setEditing(false);
  const next=draft.trim();
  if(next&&next!==name)onRename(next);
 }
 return <div className="modeling-node-heading">{editing?<Input aria-label="节点名称" autoFocus maxLength={100} value={draft} disabled={disabled} onChange={e=>setDraft(e.target.value)} onBlur={commit} onPressEnter={commit} onKeyDown={e=>{if(e.key==='Escape'){e.stopPropagation();active.current=false;setEditing(false)}}}/>:<><strong title={name||'属性配置'}>{name||'属性配置'}</strong>{name&&<Button type="text" size="small" icon={<EditOutlined/>} aria-label="编辑节点名称" title="编辑节点名称" disabled={disabled} onClick={()=>{active.current=true;setDraft(name);setEditing(true)}}/>}</>}</div>
}
