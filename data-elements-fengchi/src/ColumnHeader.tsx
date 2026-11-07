import type {Column} from './data';
import {KeyOutlined} from '@ant-design/icons';

/** One source for the display labels used by the grid and the record form. */
export function getColumnLabel(column:Column):string{
 if(column.name==='id'&&(!column.comment||column.comment==='主键，自增'))return '编号';
 if(column.comment?.trim())return column.comment.trim();
 const labels:Record<string,string>={name:'名称',created_at:'创建时间',updated_at:'更新时间',email:'邮箱',description:'说明',status:'状态'};
 return labels[column.name]||column.name;
}
export function getFieldKind(type:string){
 if(/^(?:date|time|datetime|timestamp|year)\b/i.test(type))return 'temporal';
 if(/^(?:tinyint|smallint|mediumint|bigint|int|integer|decimal|numeric|float|double|real|number)\b/i.test(type))return 'number';
 if(/^(?:bool|boolean|bit)\b/i.test(type))return 'boolean';
 if(/^(?:json|xml)\b/i.test(type))return 'document';
 if(/^(?:binary|varbinary|blob|bytea)\b/i.test(type))return 'binary';
 return 'text';
}
export function FieldTypeIcon({type}:{type:string}){
 const kind=getFieldKind(type);
 return <svg className={`field-type-icon field-kind-${kind}`} width="13" height="13" viewBox="0 0 20 20" fill="none" stroke="currentColor" strokeWidth="1.35" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
 {kind==='temporal'?<><rect x="3" y="4" width="14" height="13" rx="2"/><path d="M6 2v4m8-4v4M3 8h14m-10 4h2m3 0h1m-6 3h2"/></>:kind==='number'?<><path d="m3 6 3-2v12m-3 0h6M11 7c0-4 6-4 6 0 0 2-6 5-6 9h6"/></>:kind==='boolean'?<><rect x="2" y="6" width="16" height="8" rx="4"/><circle cx="7" cy="10" r="2"/></>:kind==='document'?<path d="M7 3H5v5l-2 2 2 2v5h2m6-14h2v5l2 2-2 2v5h-2"/>:kind==='binary'?<><path d="M4 5h4v10H4Zm9 0h2v10m-2 0h4"/></>:<><path d="M4 6V4h12v2M10 4v12m-3 0h6"/></>}
 </svg>;
}
export default function ColumnHeader({column}:{column:Column}){
 const label=getColumnLabel(column);
 return <div className="column-label" title={`${column.name} · ${label} · ${column.type.toUpperCase()}${column.primary?' · 主键':''}`}>
  <div className="column-identity">{column.primary&&<KeyOutlined aria-label="主键"/>}<b>{column.name}</b></div>
  <div className="column-display-name">{label}</div>
  <div className="column-data-type"><FieldTypeIcon type={column.type}/><span>{column.type.toUpperCase()}</span></div>
 </div>;
}
export function getColumnWidth(column:Column):number{
 const metadata=column.type.length*5.4+48;
 const names=Math.max(column.name.length*6+38,getColumnLabel(column).length*10.5+30);
 return Math.ceil(Math.max(120,Math.min(250,Math.max(metadata,names,column.name==='email'?210:column.name==='created_at'?170:column.name==='name'?145:130))));
}
