import {useEffect,useState} from 'react';
import {Button,Popover} from 'antd';
import {ExclamationCircleFilled,WarningFilled} from '@ant-design/icons';
import type {ModelSqlDiagnostic} from './modelingSql';

type Props={active?:boolean;id:string;diagnostics:ModelSqlDiagnostic[];nodeNames:Record<string,string>};
/** No diagnostics means no control or reserved space. Popover supports pointer, keyboard and touch. */
export default function ModelingSqlDiagnostics({active=true,id,diagnostics,nodeNames}:Props){
 const [open,setOpen]=useState(false);
 useEffect(()=>{if(!active||!diagnostics.length)setOpen(false)},[active,diagnostics.length]);
 if(!active||!diagnostics.length)return null;
 const blocking=diagnostics.some(item=>item.level==='error');
 const label=(blocking?'SQL 阻断诊断':'SQL 生成提示')+`（${diagnostics.length} 项）`;
 return <Popover open={open} onOpenChange={setOpen} trigger={['hover','focus','click']} placement="bottomRight" title={label} content={<div id={id} className="modeling-sql-diagnostic-list" tabIndex={0}><ul>{diagnostics.map((item,index)=><li key={index} data-severity={item.level}><strong>{item.level==='error'?'阻断':'提示'}{item.nodeId?' · '+(nodeNames[item.nodeId]||item.nodeId):''}</strong><p>{item.message}</p></li>)}</ul></div>}><Button type="text" className="modeling-sql-diagnostics-button" data-severity={blocking?'error':'warning'} icon={blocking?<ExclamationCircleFilled/>:<WarningFilled/>} aria-label={label} aria-describedby={id} onClick={()=>setOpen(true)} onKeyDown={e=>{if(e.key==='Escape'){e.stopPropagation();setOpen(false)}}}/></Popover>;
}
