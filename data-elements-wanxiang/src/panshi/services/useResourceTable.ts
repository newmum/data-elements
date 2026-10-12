import { useEffect, useState } from 'react';
import type { Row } from '../../services/api';
import { useServerSort } from '../../shared/serverSort';
import { resourcePost } from './transport';

export type ResourceTable = 'layers'|'databases'|'bindings'|'dst'|'models'|'materializations'|'itemSets'|'fields'|'materializationItems'|'catalogs'|'modelLogs';

/** Stored configuration values and permissions are read by the server; the browser supplies only view IDs. */
export function useResourceTable<T extends {id:string}>(source:T[], table?:ResourceTable,
  query:Record<string,unknown>={}, initialSearch='') {
 const [page,setPage]=useState(1),[size,setSize]=useState(20),[keyword,setKeyword]=useState(initialSearch);
 const [rows,setRows]=useState<T[]>([]),[total,setTotal]=useState(0),[loading,setLoading]=useState(false),[error,setError]=useState<unknown>();
 const [revision,setRevision]=useState(0);
 const order=useServerSort(()=>setPage(1));
 const ids=source.map(row=>row.id);
 const input=JSON.stringify({table,ids:table==='catalogs'||table==='modelLogs'?undefined:ids,...query,page,size,keyword,...order.sort});
 const scope=JSON.stringify({table,ids:table==='catalogs'||table==='modelLogs'?undefined:ids,...query});
 const versions=JSON.stringify(source.map(row=>[row.id,(row as Row).version,(row as Row).updatedAt]));
 useEffect(()=>{setPage(1);},[scope]);
 useEffect(()=>{setKeyword(initialSearch);setPage(1);},[initialSearch]);
 useEffect(()=>{
  if(!table)return;const controller=new AbortController();setLoading(true);setError(undefined);
  const request=JSON.parse(input);
  resourcePost<{keys?:string[];list?:T[];statistics?:Record<string,Record<string,number|null>>;total:number}>('/dwm/center/resource-command',
   {kind:'table',method:'GET',body:request},{signal:controller.signal}).then(result=>{
    if(!Number.isSafeInteger(result.total)||result.total<0)throw new Error('资源表格返回的总数无效');
    if(Array.isArray(result.list)) {
     if(result.list.length>size||new Set(result.list.map(row=>row.id)).size!==result.list.length)throw new Error('资源表格返回范围无效');
     setRows(result.list);
    } else {
     const byId=new Map(source.map(row=>[row.id,row]));
     if(!Array.isArray(result.keys)||result.keys.length>size||new Set(result.keys).size!==result.keys.length||result.keys.some(id=>!byId.has(id)))throw new Error('资源排序返回了当前视图以外的记录');
     if(result.statistics&&Object.keys(result.statistics).some(id=>!result.keys!.includes(id)))throw new Error('资源统计返回范围无效');
     setRows(result.keys.map(id=>({...byId.get(id)!,...(result.statistics?.[id]??{})})));
    }
    setTotal(result.total);
   }).catch(cause=>{if(!controller.signal.aborted){setError(cause);setRows([]);setTotal(0);}})
   .finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  return()=>controller.abort();
 },[input,versions,revision,table,size]);
 return {rows,total,loading,error,order,refresh:()=>setRevision(v=>v+1),search:(q:string)=>{setKeyword(q);setPage(1);},
  pagination:{page,size,total,onChange:(p:number,n:number)=>{setPage(n===size?p:1);setSize(n);}}};
}
