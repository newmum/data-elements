import { useEffect, useRef, useState } from 'react';
import { Alert, App, Button, Drawer, Empty, Input, Modal, Space, Table, Tag } from 'antd';
import { DeleteOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons';
import { api, request, type Row } from '../../services/api';

/** Editable records are local fixtures; no field value ever goes to an external database. */
export function LocalDatasetEditor({ entityId, onClose, onSaved }: { entityId?: string | null; onClose: () => void; onSaved?: () => void }) {
  const { message, modal } = App.useApp();
  const [entity, setEntity] = useState<Row | null>(null), [rows, setRows] = useState<Row[]>([]);
  const [loading, setLoading] = useState(false), [saving, setSaving] = useState(false), [error, setError] = useState('');
  const dirty = useRef(false), generation = useRef(0);
  useEffect(() => {
    const token=++generation.current;dirty.current=false;setEntity(null);setRows([]);setError('');
    if(!entityId)return;
    setLoading(true);
    api(`datasets/${entityId}`).then(data=>{if(token!==generation.current)return;setEntity(data.entity);setRows(data.items.map((r:Row)=>({...r,__rowKey:crypto.randomUUID()})));}).catch(e=>{if(token===generation.current)setError(e.message);}).finally(()=>{if(token===generation.current)setLoading(false);});
    return ()=>{generation.current++;};
  },[entityId]);
  const close=()=>{if(saving)return;if(dirty.current)modal.confirm({title:'放弃未保存的数据修改？',content:'关闭后恢复到上次保存的本地记录。',okText:'放弃修改',onOk:onClose});else onClose();};
  const save=async()=>{if(!entityId||saving)return;setSaving(true);try{await api(`datasets/${entityId}`,{method:'PUT',body:{items:rows.map(({__rowKey,...r})=>r)}});dirty.current=false;message.success('本地记录已保存，可重新执行质量检查');onSaved?.();}catch(e){message.error(e instanceof Error?e.message:String(e));}finally{setSaving(false);}};
  return <Drawer className="local-dataset-editor" open={!!entityId} width="min(1180px,100vw)" title={`本地数据记录 · ${entity?.displayName??entity?.name??''}`} onClose={close} maskClosable={!saving} extra={<Button type="primary" icon={<SaveOutlined/>} loading={saving} disabled={loading||!!error} onClick={save}>保存记录</Button>}>
    <Alert showIcon type="info" title="这些记录只保存在当前浏览器" description="关系验证、质量检查和工单复检会使用这份数据。修改后需重新执行任务，历史报告不会被改写。"/>
    <Space wrap style={{margin:'16px 0'}}><Button icon={<PlusOutlined/>} disabled={!entity||rows.length>=2000} onClick={()=>{dirty.current=true;setRows(r=>[...r,{...Object.fromEntries((entity?.fields??[]).map((f:Row)=>[f.name,null])),__rowKey:crypto.randomUUID()}]);}}>新增一行</Button><span className="muted">{rows.length} 条记录 · 精确数值以字符串保存</span></Space>
    {error?<Alert type="error" title={error}/>:<Table<Row> rowKey="__rowKey" size="small" loading={loading} dataSource={rows} scroll={{x:'max-content'}} pagination={{defaultPageSize:10,showSizeChanger:true,pageSizeOptions:[10,20,50]}} columns={[
      ...((entity?.fields??[]) as Row[]).map(f=>({title:<span>{f.comment||f.name}<small style={{display:'block',fontSize:12,fontWeight:400}}>{f.name}</small></span>,dataIndex:f.name,key:f.id,width:178,render:(value:unknown,row:Row)=>['array','object','binary'].includes(f.typeFamily)?<Tag title={JSON.stringify(value)}>{value==null?'NULL':JSON.stringify(value).slice(0,26)}</Tag>:<Input aria-label={`${f.name} 记录字段`} value={value==null?'':String(value)} placeholder={value==null?'NULL':''} onChange={e=>{const value=e.target.value;dirty.current=true;setRows(prev=>prev.map(r=>r.__rowKey===row.__rowKey?{...r,[f.name]:value}:r));}}/>})),
      {title:'操作',key:'op',className:'wx-action-column',width:72,fixed:'right',align:'center',render:(_,r)=><Button danger type="text" icon={<DeleteOutlined/>} aria-label="移除本地记录" onClick={()=>{dirty.current=true;setRows(prev=>prev.filter(x=>x.__rowKey!==r.__rowKey));}}/>}
    ]}/>}<p className="muted">NULL 与空字符串不同；未编辑的空值保持 NULL。数组及对象在此表格中只读，可通过工作区 JSON 备份编辑后重新导入。</p>
  </Drawer>;
}
export function LocalIdentityPicker({open,onClose,onSelect}:{open:boolean;onClose:()=>void;onSelect:(username:string)=>Promise<void>}) {
 const [items,setItems]=useState<Row[]>([]),[busy,setBusy]=useState(''),[error,setError]=useState('');
 useEffect(()=>{let live=true;if(open){setError('');request<{items:Row[]}>('/auth/identities').then(r=>{if(live)setItems(r.items);}).catch(e=>{if(live)setError(e.message);});}return()=>{live=false;};},[open]);
 return <Modal open={open} title="切换工作身份" footer={null} onCancel={onClose} maskClosable={!busy} closable={!busy}>
   <p className="muted">工作身份用于体验不同操作与审核流程，不需要密码，也不提供真实的账号安全隔离。</p>{error&&<Alert type="error" title={error}/>}
   <div className="local-identity-grid">{items.map(m=><button key={m.username} className="local-identity-choice" disabled={!!busy} onClick={async()=>{setBusy(m.username);try{await onSelect(m.username);onClose();}catch(e){setError(e instanceof Error?e.message:String(e));}finally{setBusy('');}}}><span className="wx-avatar">{m.name.slice(0,1)}</span><span><b>{m.name}{busy===m.username?' · 切换中':''}</b><small>{m.username} · {({OWNER:'管理员',STEWARD:'数据管理员',EDITOR:'编辑人员',VIEWER:'只读查看',REVIEWER:'标准审核员'} as Row)[m.role]}</small></span></button>)}</div>{!items.length&&!error&&<Empty description="正在读取工作身份"/>}
 </Modal>;
}
