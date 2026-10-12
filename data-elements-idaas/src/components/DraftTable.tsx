import { useEffect, useState } from 'react';
import { Alert, Button, Table, type TableProps } from 'antd';
import { foundationApi } from '../services/workspace';
import { useTableOrder } from '../hooks/useTableOrder';

type Field<T> = { kind: 'text' | 'number' | 'time'; value: (row:T) => unknown };
/** Orders a complete unsaved preview on the server while retaining the original objects. */
export function DraftTable<T extends {id:string}>({dataSource = [], columns, fields, enabled=true, ...props}:TableProps<T> & {fields:Record<string,Field<T>>;enabled?:boolean}) {
    const order=useTableOrder(),params=new URLSearchParams(order.query);
    const field=params.get('sortField')||Object.keys(fields)[0],definition=fields[field];
    const [keys,setKeys]=useState<string[]>([]),[total,setTotal]=useState(0),[loading,setLoading]=useState(false),[error,setError]=useState(''),[version,setVersion]=useState(0);
    const serialized=JSON.stringify({items:dataSource.map(row=>({id:row.id,value:definition?.value(row)??null})),kind:definition?.kind,direction:params.get('sortDir')||(definition?.kind==='text'?'asc':'desc'),page:order.page,size:order.size});
    useEffect(()=>{
        if(!enabled)return;
        let active=true;setLoading(true);setError('');
        const request=JSON.parse(serialized),wanted=new Set<string>(request.items.map((item:{id:string})=>item.id));
        foundationApi.write<{keys:string[];total:number}>('/idaas/workspace/preview-order',request).then(result=>{
            if(!Array.isArray(result.keys)||result.total!==wanted.size||result.keys.length>request.size||result.keys.some(id=>!wanted.has(id))||new Set(result.keys).size!==result.keys.length)throw new Error('授权预览排序范围不一致');
            if(active){setKeys(result.keys);setTotal(result.total);}
        }).catch(cause=>{if(active){setError(cause instanceof Error?cause.message:'授权预览排序失败');setKeys([]);setTotal(0);}}).finally(()=>{if(active)setLoading(false);});
        return()=>{active=false;};
    },[serialized,enabled,version]);
    const byId=new Map(dataSource.map(row=>[row.id,row]));
    return <>{error&&<Alert showIcon type="error" title={error} action={<Button onClick={()=>setVersion(v=>v+1)}>重试</Button>}/>}<Table<T> {...props} rowKey="id" loading={loading} dataSource={keys.flatMap(id=>byId.has(id)?[byId.get(id)!]:[])} columns={columns?.map(column=>{const key=String(column.key??('dataIndex' in column?column.dataIndex:''));return {...column,...(fields[key]?order.column(key):{sorter:undefined})};})} onChange={order.onChange} pagination={order.pagination(total)}/></>;
}
