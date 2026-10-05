import { useEffect, useState } from 'react';
import { ProtocolConfiguration } from './ProtocolConfiguration';
import { useSession } from '../mock/store';
import { Alert, Button, Card, Space, Table, Tag } from 'antd';
import { foundationApi } from '../services/workspace';
import { RecordEditor, useAction, type FormValues } from './common';
interface Client { id: string; appId: string; clientKey: string; name: string; clientType: string; protocol: string; status: string; version: number; accessTtl: number; refreshTtl: number; }
export function AuthClients({ appId, writable }: { appId: string; writable: boolean }) {
 const domain = useSession()!.domain;
 const [rows, setRows] = useState<Client[]>([]); const [error, setError] = useState(''); const [editing, setEditing] = useState<Client | null>(null); const [open, setOpen] = useState(false); const { run, busy } = useAction();
 const load = async () => { try { setRows(await foundationApi.read<Client[]>('/idaas/clients/list?appId='+encodeURIComponent(appId)+'&domain='+domain)); setError(''); } catch (e) { setError(e instanceof Error ? e.message : '无法读取客户端'); } };
 useEffect(() => { void load(); }, [appId,domain]);
 return <Card size="small" title="应用客户端" extra={<Button type="primary" disabled={!writable} onClick={() => { setEditing(null); setOpen(true); }}>登记客户端</Button>}>
  {error && <Alert type="error" title={error} action={<Button onClick={() => void load()}>重试</Button>}/>}
  <Table size="small" rowKey="id" dataSource={rows} scroll={{ x: 'max-content' }} columns={[{ title:'客户端',dataIndex:'name' },{ title:'客户端编码',dataIndex:'clientKey' },{ title:'类型',dataIndex:'clientType',render:v=>(({PUBLIC:'公共客户端',CONFIDENTIAL:'服务端客户端',SERVICE:'机器客户端'} as Record<string,string>)[v]||v) },{ title:'协议',dataIndex:'protocol',render:v=>v==='NONE'?'未配置':v },{ title:'状态',dataIndex:'status',render:v=><Tag>{({REGISTERED:'已登记',ACTIVE:'已启用',DISABLED:'已停用'} as Record<string,string>)[v]||v}</Tag> },{ title:'操作',render:(_,r)=><Space><ProtocolConfiguration client={r} writable={writable} onChanged={load}/><Button type="link" disabled={!writable || busy || r.status === "ACTIVE"} onClick={() => { setEditing(r); setOpen(true); }}>编辑</Button><Button type="link" danger disabled={!writable || busy} onClick={() => run(async()=>{await foundationApi.write('/idaas/clients/remove',{id:r.id,version:r.version,domain});await load();},'客户端已归档')}>归档</Button></Space> }]}/>
  <RecordEditor open={open} title={editing?'编辑客户端':'登记客户端'} initial={(editing||{name:'',clientKey:'',clientType:'PUBLIC',accessTtl:600,refreshTtl:86400,status:'REGISTERED'}) as unknown as FormValues} onClose={()=>setOpen(false)} fields={[{name:'name',label:'客户端名称',required:true,max:100},{name:'clientKey',label:'客户端编码',required:true,disabled:!!editing,max:100,pattern:/^[A-Za-z][A-Za-z0-9_.-]{2,99}$/},{name:'clientType',label:'类型',type:'select',disabled:!!editing,options:[{value:'PUBLIC',label:'公共客户端'},{value:'CONFIDENTIAL',label:'服务端客户端'},{value:'SERVICE',label:'机器客户端'}]},{name:'accessTtl',label:'访问令牌有效期（秒）',type:'number',min:60,max:3600},{name:'refreshTtl',label:'刷新令牌有效期（秒）',type:'number',min:600,max:604800},{name:'status',label:'登记状态',type:'select',options:[{value:'REGISTERED',label:'已登记'},{value:'DISABLED',label:'已停用'}]}]} onSave={async values=>{await foundationApi.write('/idaas/clients/save',{creating:!editing,record:{...editing,...values,appId,domain,protocol:'NONE'}});await load();}}/>
 </Card>;
}
