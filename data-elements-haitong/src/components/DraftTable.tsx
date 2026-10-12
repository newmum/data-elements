import { useEffect, useState } from 'react';
import { Alert, Button } from 'antd';
import type { TableProps } from 'antd';
import { platformApi } from '../api/platformApi';
import { DataTable } from './Common';
import { useServerSort } from './serverSort';

type Field<T>={kind:'text'|'number'|'time';value:(row:T)=>unknown};
/** Complete local drafts only; persisted records use the native database page endpoint. */
export function DraftTable<T extends object>({dataSource=[],columns,fields,...props}:TableProps<T>&{fields:Record<string,Field<T>>}) {
 const [page,setPage]=useState(1),[size,setSize]=useState(20),[keys,setKeys]=useState<string[]>([]),[total,setTotal]=useState(0),[error,setError]=useState(''),[loading,setLoading]=useState(false),[version,setVersion]=useState(0);
 const order=useServerSort(()=>setPage(1)),field=order.sort.sortField||Object.keys(fields)[0],definition=fields[field];
 const rows=dataSource.map((row,index)=>({id:String(typeof props.rowKey==='function'?props.rowKey(row,index):props.rowKey?(row as any)[props.rowKey]??index:(row as any).id??index),row}));
 const body=JSON.stringify({items:rows.map(item=>({id:item.id,value:definition?.value(item.row)??null})),kind:definition?.kind,direction:order.sort.sortDir||(definition?.kind==='text'?'asc':'desc'),page,size});
 useEffect(()=>{let active=true;setLoading(true);setError('');platformApi<{keys:string[];total:number}>('/sym/table/preview-order',JSON.parse(body)).then(result=>{
  const wanted=new Set(JSON.parse(body).items.map((item:{id:string})=>item.id));
  if(result.total!==wanted.size||result.keys.some(id=>!wanted.has(id))||new Set(result.keys).size!==result.keys.length)throw new Error('草稿排序结果不完整或超出选择范围');
  if(active){setKeys(result.keys);setTotal(result.total);}
 }).catch(cause=>{if(active){setError(cause instanceof Error?cause.message:'草稿排序失败');setKeys([]);setTotal(0);}}).finally(()=>{if(active)setLoading(false);});return()=>{active=false;};},[body,version]);
 const byId=new Map(rows.map(item=>[item.id,item.row]));
 return <>{error&&<Alert type="error" showIcon message={error} action={<Button onClick={()=>setVersion(v=>v+1)}>重试</Button>}/>}<DataTable<T> {...props} loading={loading} dataSource={keys.map(id=>byId.get(id)).filter((row):row is T=>!!row)} columns={columns?.map(column=>({...column,sorter:!!fields[String(column.key??('dataIndex' in column?column.dataIndex:''))]}))} serverSort={order} pagination={{current:page,pageSize:size,total,showSizeChanger:true,pageSizeOptions:[20,50,100],onChange:(p,n)=>{setPage(p);setSize(n);}}}/></>;
}
