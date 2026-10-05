import { useState, useEffect, useRef, Children, isValidElement, type ReactNode } from 'react';
import { App, Button, Dropdown, Form, Modal, Tag, type TableColumnsType, type FormInstance } from 'antd';
import { ArrowRightOutlined, MoreOutlined } from '@ant-design/icons';
import { pages, type PageKey } from '../app/config';
import type { Row } from '../../services/api';
import { DataTable } from '../../features/platform/common';
import { ResourceIcon } from '../design/ResourceIcon';
import { artworkFor, layersDiagram } from '../design/artwork';
export const stackArt=layersDiagram.light;
export const monumentArt=layersDiagram.light;
export function PsIcon({name}:{name:string}){return <ResourceIcon name={name}/>;}
export function Hero({page,actions}:{page:PageKey;actions?:ReactNode}){
 const config=pages.find(p=>p.key===page)!;
 return <header className={`ps-hero ps-hero-clear hero-${page}`} data-hero={page}>
  <div className="ps-hero-copy"><h1>{config.name}</h1><p>{config.subtitle}</p>{actions&&<div className="ps-hero-actions">{actions}</div>}</div>
  <div className="ps-hero-visual" aria-hidden="true"><img className="ps-art-light" src={artworkFor(page,'light')} width="1340" height="620" alt="" decoding="async"/><img className="ps-art-dark" src={artworkFor(page,'dark')} width="1340" height="620" alt="" decoding="async"/></div>
 </header>;
}
export function Panel({title,extra,children,className=''}:{title:string;extra?:ReactNode;children:ReactNode;className?:string}){return <section className={`ps-panel ${className}`}><header><h2>{title}</h2>{extra}</header><div className="ps-panel-body">{children}</div></section>;}
export function Metric({label,value,note,icon,tone='cyan',onClick}:{label:string;value:ReactNode;note:string;icon:string;tone?:string;onClick?:()=>void}){return <button className={`ps-metric ${tone}`} onClick={onClick} disabled={!onClick}><span className="ps-metric-icon"><PsIcon name={icon}/></span><span><span className="ps-metric-label">{label}</span><strong>{value}</strong><small>{note}</small></span>{onClick&&<ArrowRightOutlined className="ps-metric-arrow"/>}</button>;}
const statusNames:Record<string,string>={ACTIVE:'有效',ARCHIVED:'已归档',REFERENCE:'外部引用',MANAGED:'管理型',VALID:'检查通过',INVALID:'配置异常',NOT_CHECKED:'待检查',DRAFT:'草稿',FROZEN:'已冻结',PENDING_REVIEW:'待审核',APPROVED:'已批准',RETURNED:'已退回',PUBLISHED:'已发布',OFFLINE:'已下线',SUPERSEDED:'已替代',BLOCKED:'有阻断',READY:'可执行',QUEUED:'排队中',RUNNING:'执行中',CANCELLING:'取消中',CANCELLED:'已取消',SUCCEEDED:'已完成',PARTIAL:'部分完成',FAILED:'失败',PENDING:'待审批',SCHEDULED:'待生效',PAUSED:'已暂停',EXPIRED:'已到期',REVOKED:'已撤销',NOT_CONFIGURED:'未配置',SYNCED:'已同步',SUBMITTED:'已提交'};
export function StateTag({state}:{state:string}){const color=['ACTIVE','VALID','PUBLISHED','APPROVED','SUCCEEDED'].includes(state)?'green':['FAILED','BLOCKED','INVALID'].includes(state)?'error':['PENDING','PENDING_REVIEW','RETURNED','PARTIAL'].includes(state)?'orange':['RUNNING','READY','FROZEN','SUBMITTED'].includes(state)?'blue':undefined;return <Tag color={color} bordered={false}>{statusNames[state]??state}</Tag>;}
export const timeText=(s?:string)=>s?new Date(s).toLocaleString('zh-CN',{hour12:false}).replace(/\//g,'-'):'—';
export function More({items}:{items:Array<{label:string;onClick:()=>void;danger?:boolean;disabled?:boolean}>}){return <Dropdown trigger={['click']} menu={{items:items.map((i,n)=>({key:String(n),label:i.label,danger:i.danger,disabled:i.disabled})),onClick:({key})=>items[Number(key)].onClick()}}><Button type="text" size="small" aria-label="更多操作" icon={<MoreOutlined/>}/></Dropdown>;}
export function saveFile(content:string,name:string,type='application/json'){const url=URL.createObjectURL(new Blob([content],{type})),a=document.createElement('a');a.href=url;a.download=name;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
function actionWidth(node: ReactNode): number {
 const widths: number[] = [];
 const visit = (value: ReactNode) => Children.forEach(value, child => {
  if (!isValidElement<{children?:ReactNode;icon?:ReactNode}>(child)) return;
  if (child.type === Button) { const label = Children.toArray(child.props.children).filter(v => typeof v === 'string' || typeof v === 'number').join(''); widths.push(Math.max(24, [...label].reduce((n,c) => n + (/[\u3000-\u9fff]/.test(c) ? 13 : 7), 0) + (child.props.icon ? 18 : 0))); }
  else if (child.type === More) widths.push(28);
  else visit(child.props.children);
 });
 visit(node); return widths.length ? Math.max(80, Math.min(256, 24 + widths.reduce((a,b) => a+b,0) + (widths.length-1)*10)) : 128;
}
function sortingValue(row:Row, title:string, dataIndex:unknown):string {
 if(typeof dataIndex==='string')return String(row[dataIndex]??'');
 if(Array.isArray(dataIndex))return String(dataIndex.reduce((value,key)=>value?.[key],row)??'');
 const nested=row.draft??row.revision??row;
 if(/时间|日期/.test(title))return String(row.createdAt??row.updatedAt??row.time??row.scheduledFor??'');
 return String(nested.name??row.name??row.filename??nested.code??'');
}
/** Reuse metadata mapping's toolbar, density, sorting and fixed action treatment. */
export function PTable<T extends {id:string}>({rows,columns,tools,title,onRefresh,onSelect,searchText,initialSearch,emptyTitle,emptyDescription}:{rows:T[];columns:TableColumnsType<T>;tools?:ReactNode;title?:string;onRefresh?:()=>void;onSelect?:(keys:string[])=>void;searchText?:(row:T)=>string;initialSearch?:string;emptyTitle?:string;emptyDescription?:string}){
 const normalized=columns.map(column=>{
  if(column.title==='操作'){
   const sample=rows.slice(0,30).map((row,i)=>column.render?.(undefined,row,i));
   const width=sample.length?Math.max(...sample.map(node=>actionWidth(node as ReactNode))):Math.min(Number(column.width)||128,224);
   return {...column,width,fixed:'right' as const,align:'center' as const,className:'wx-action-column'};
  }
  if(typeof column.title==='string'&&/名称|名字|字段名|分库(?:\s*\/|$)|共享数据源|资源目录|时间|日期|最近检查/.test(column.title)){
   const titleText=column.title,index='dataIndex' in column?column.dataIndex:undefined;
   return {...column,width:/时间|日期|最近检查/.test(titleText)?(column.width??168):Math.min(Number(column.width)||220,240),ellipsis:true,sorter:column.sorter??((a:T,b:T)=>sortingValue(a,titleText,index).localeCompare(sortingValue(b,titleText,index),'zh-CN',{numeric:true}))};
  }
  return {...column,ellipsis:column.ellipsis??true};
 });
 const records=searchText?rows.map(row=>({...row,__search:searchText(row)})):rows;
 return <section className="ps-table-panel">{title&&<header className="ps-table-title"><h2>{title}</h2></header>}<DataTable titleKey={`resource:${title??columns.map(c=>String(c.title)).join('|')}`} rows={records} columns={normalized as TableColumnsType<Row>} onRefresh={onRefresh} actions={tools} initialSearch={initialSearch} emptyTitle={emptyTitle} emptyDescription={emptyDescription} rowSelection={onSelect?{onChange:(keys:Array<string|number>)=>onSelect(keys.map(String))}:undefined}/></section>;
}
export const required=[{required:true,message:'请填写此项'}];
export function EditDialog({title,open,initial,onClose,onSave,children,width=600,form:external}:{title:string;open:boolean;initial?:Row;onClose:()=>void;onSave:(v:Row)=>Promise<unknown>;children:ReactNode;width?:number;form?:FormInstance}){const [internal]=Form.useForm();const form=external??internal;const [busy,setBusy]=useState(false),[dirty,setDirty]=useState(false);const lock=useRef(false);const {message,modal}=App.useApp();useEffect(()=>{if(open){form.resetFields();form.setFieldsValue(initial??{});setDirty(false);}},[open,initial?.id]);const close=()=>{if(busy)return;if(dirty)modal.confirm({title:'放弃未保存的修改？',okText:'放弃修改',cancelText:'继续编辑',onOk:onClose});else onClose();};return <Modal open={open} title={title} width={width} onCancel={close} maskClosable={false} destroyOnHidden afterOpenChange={visible=>{if(visible){form.resetFields();form.setFieldsValue(initial??{});setDirty(false);}}} okText="保存" cancelText="取消" confirmLoading={busy} onOk={async()=>{if(lock.current)return;lock.current=true;try{const value=await form.validateFields();setBusy(true);await onSave(value);setDirty(false);message.success('已保存');onClose();}catch(e){if(e instanceof Error)message.error(e.message);}finally{lock.current=false;setBusy(false);}}}><Form form={form} initialValues={initial} layout="vertical" onValuesChange={()=>setDirty(true)} className="ps-form" preserve={false}>{children}</Form></Modal>;}
export function NameCell({name,code}:{name:ReactNode;code?:ReactNode}){return <span className="ps-name"><strong title={typeof name==='string'?name:undefined}>{name}</strong>{code&&<small title={typeof code==='string'?code:undefined}>{code}</small>}</span>;}
export function useCommand(){const {message}=App.useApp();const [busy,setBusy]=useState(false);const lock=useRef(false);const run=async(fn:()=>Promise<unknown>,success='操作已保存')=>{if(lock.current)return;lock.current=true;setBusy(true);try{const value=await fn();if(success)message.success(success);return value;}catch(e){message.error(e instanceof Error?e.message:String(e));}finally{lock.current=false;setBusy(false);}};return {run,busy};}
