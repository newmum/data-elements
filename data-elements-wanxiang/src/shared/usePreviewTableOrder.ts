import { useEffect, useState } from 'react';
import { platformApi } from './platformApi';
import { useServerSort } from './serverSort';

/** For complete browser layout/draft collections only; business records use their database page API. */
export function usePreviewTableOrder<T extends Record<string,any>>(rows:T[], fields:Record<string,'text'|'number'|'time'>,
 value:(row:T,field:string)=>unknown, fallback:string, enabled=true, initialSize=20) {
 const [page,setPage]=useState(1),[size,setSize]=useState(initialSize),[keys,setKeys]=useState<string[]>([]),[total,setTotal]=useState(0),[loading,setLoading]=useState(false),[error,setError]=useState<unknown>();
 const [revision,setRevision]=useState(0);
 const order=useServerSort(()=>setPage(1));const field=order.sort.sortField||fallback;
 const items=rows.map(row=>({id:row.id,value:value(row,field)??null}));
 const serialized=JSON.stringify({items,kind:fields[field],direction:order.sort.sortDir||(fields[field]==='text'?'asc':'desc'),page,size});
 useEffect(()=>{
  if(!enabled)return;const controller=new AbortController();setLoading(true);setError(undefined);
  const body=JSON.parse(serialized);const wanted=new Set<string>(body.items.map((item:{id:string})=>item.id));
  if(!body.kind||wanted.size!==body.items.length){setError(new Error('预览排序字段或行标识无效'));setLoading(false);return;}
  platformApi<{keys:string[];total:number}>('/sym/table/preview-order',body,{signal:controller.signal}).then(result=>{
   if(!Array.isArray(result.keys)||result.keys.length>size||new Set(result.keys).size!==result.keys.length||result.keys.some(id=>!wanted.has(id))||result.total!==wanted.size)throw new Error('预览排序返回范围不一致');
   setKeys(result.keys);setTotal(result.total);
  }).catch(cause=>{if(!controller.signal.aborted){setKeys([]);setTotal(0);setError(cause);}}).finally(()=>{if(!controller.signal.aborted)setLoading(false);});
  return()=>controller.abort();
 },[serialized,enabled,size,revision]);
 const byId=new Map(rows.map(row=>[row.id,row]));
 return {refresh:()=>setRevision(v=>v+1),rows:keys.map(id=>byId.get(id)).filter((row):row is T=>!!row),order,loading,error,page,size,total,
  pagination:{page,size,total,onChange:(p:number,n:number)=>{setPage(p);setSize(n);}}};
}
