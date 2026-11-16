import { Alert, App, Button, Descriptions, Form, Input, Modal, Space, Tag } from 'antd';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import type { AccessTable } from '../../api/integrationPlatformService';
import { nativeFlowAssociations, type NativeFlowAssociation, type NativeFlowObservation } from '../../api/nativeFlowAssociations';
import { DataTable, Panel, formatTime } from '../../components/Common';
import { useSessionRevision } from '../../nifi/api/bridgeSession';

const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;
const errorMessage = (cause: unknown) => cause instanceof Error ? cause.message : '请刷新后重试。';
export const nativeFlowQueryKey = ['platform', 'native-flow-associations'] as const;

export function NativeFlowLinkForm({ source, onBack, onCreated }: {
  source: AccessTable;
  onBack: () => void;
  onCreated: () => void;
}) {
  const [group, setGroup] = useState('');
  const [preview, setPreview] = useState<NativeFlowObservation>();
  const [touched, setTouched] = useState(false);
  const sourceId = String(source.sourceTableId || source.tableId || source.tid || '');
  const groupId = group.trim().toLowerCase();
  const inspect = useMutation({
    mutationFn: (id: string) => nativeFlowAssociations.inspect(sourceId, id),
    onSuccess: setPreview,
  });
  const bind = useMutation({
    mutationFn: (id: string) => nativeFlowAssociations.bind(sourceId, id),
    onSuccess: onCreated,
  });
  const confirmed = preview?.processGroupId === groupId && Boolean(groupId);
  const busy = inspect.isPending || bind.isPending;
  return <Form layout="vertical" onFinish={() => {
    setTouched(true);
    if (!uuid.test(groupId) || busy) return;
    if (confirmed) bind.mutate(groupId);
    else inspect.mutate(groupId);
  }}>
    <Descriptions size="small" column={1} items={[
      { key: 'source', label: '来源表', children: source.tableNameCn || source.tableName || sourceId },
      { key: 'datasource', label: '数据源', children: source.datasourceName || '—' },
    ]}/>
    <Form.Item label="NiFi 流程组编号" htmlFor="native-flow-group" required
      validateStatus={touched && !uuid.test(groupId) ? 'error' : undefined}
      help={touched && !uuid.test(groupId) ? '请输入正确的 Process Group ID（UUID）' : '在 NiFi 原生画布中复制流程组的 Process Group ID。'}>
      <Input id="native-flow-group" autoFocus value={group} spellCheck={false} maxLength={64}
       disabled={busy} onChange={event => {
        setGroup(event.target.value);
        setPreview(undefined);
        inspect.reset();
        bind.reset();
      }} placeholder="xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"/>
    </Form.Item>
    {(inspect.isError || bind.isError) && <Alert type="error" showIcon
      message={errorMessage(bind.error || inspect.error)} style={{marginBottom:16}}/>}
    {confirmed && preview && <Alert type="info" showIcon style={{marginBottom:16}} message={preview.groupName}
      description={<>{preview.nodeName} · {preview.runtimeLabel}。请确认该流程确实同步所选来源表，保存后可以查看状态和告警。</>}/>}
    <Space>
      <Button onClick={onBack} disabled={busy}>返回选表</Button>
      <Button type="primary" htmlType="submit" loading={busy}>{confirmed ? '确认关联' : '核对流程'}</Button>
    </Space>
  </Form>;
}

export default function NativeFlowAssociations() {
  const revision = useSessionRevision();
  const client = useQueryClient();
  const { message, modal } = App.useApp();
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [keyword, setKeyword] = useState('');
  const [selected, setSelected] = useState<NativeFlowAssociation>();
  const records = useQuery({
    queryKey: [...nativeFlowQueryKey, revision, page, size, keyword],
    queryFn: ({ signal }) => nativeFlowAssociations.page(page, size, keyword, signal),
    retry: false,
  });
  const detail = useQuery({
    queryKey: [...nativeFlowQueryKey, 'detail', revision, selected?.id],
    queryFn: ({ signal }) => nativeFlowAssociations.detail(selected?.id ?? '', signal),
    enabled: Boolean(selected),
    retry: false,
  });
  const remove = (item: NativeFlowAssociation) => modal.confirm({
    title: `解除「${item.groupName}」的关联？`,
    content: '海通将移除这条关联。NiFi 流程仍按原计划运行。',
    okText: '解除关联',
    cancelText: '取消',
    async onOk() {
      try {
        await nativeFlowAssociations.unlink(item.id);
        setSelected(undefined);
        if (page > 1 && records.data?.list.length === 1) setPage(page - 1);
        await client.invalidateQueries({ queryKey: nativeFlowQueryKey });
        message.success('已解除关联');
      } catch (error) {
        message.error(errorMessage(error));
        throw error;
      }
    },
  });
  return <Panel>
    <div className="ht-toolbar">
      <Input.Search allowClear maxLength={128} placeholder="搜索来源表或流程组" aria-label="搜索来源表或流程组"
        style={{width:310,maxWidth:'100%'}} onSearch={value=>{setKeyword(value.trim());setPage(1);}}/>
    </div>
    {records.isError ? <Alert showIcon type="error" message={errorMessage(records.error)}
      action={<Button onClick={()=>void records.refetch()}>重试</Button>}/> :
      <DataTable<NativeFlowAssociation> rowKey="id" scroll={{x:780}} loading={records.isFetching}
        dataSource={records.data?.list ?? []} locale={{emptyText:'暂无符合条件的原生流程关联'}}
        pagination={{current:page,pageSize:size,total:records.data?.total ?? 0,showSizeChanger:true,
          pageSizeOptions:[20,50,100],onChange:(nextPage,nextSize)=>{setPage(nextSize===size?nextPage:1);setSize(nextSize);}}}
        columns={[
          {title:'来源表',width:210,render:(_,row)=><div className="entity-name"><b>{row.tableNameCn||row.tableName}</b><small>{row.datasourceName}</small></div>},
          {title:'NiFi 流程组',render:(_,row)=><div className="entity-name"><b>{row.groupName}</b><small>{row.processGroupId}</small></div>},
          {title:'节点',width:158,dataIndex:'nodeName'},
          {title:'操作',width:110,render:(_,row)=><Button type="link" onClick={()=>setSelected(row)}>查看任务</Button>},
        ]}/>}
    <Modal title="原生 NiFi 流程" open={Boolean(selected)} onCancel={()=>setSelected(undefined)} width={740}
      footer={selected&&<Space>
        {selected.canUnlink && <Button danger onClick={()=>remove(selected)}>解除关联</Button>}
        <Button loading={detail.isFetching} onClick={()=>void detail.refetch()}>刷新状态</Button>
        <Button onClick={()=>setSelected(undefined)}>关闭</Button>
      </Space>}>
      {selected && <Descriptions size="small" column={1} items={[
        {key:'group',label:'流程组',children:detail.data?.groupName || selected.groupName},
        {key:'id',label:'流程组编号',children:selected.processGroupId},
        {key:'node',label:'节点',children:selected.nodeName},
        {key:'source',label:'来源表',children:selected.tableNameCn || selected.tableName},
        {key:'status',label:'NiFi 状态',children:<Tag>{detail.isFetching?'正在读取…':detail.data?.runtimeLabel || '状态待确认'}</Tag>},
      ]}/>}
      {detail.isError && <Alert type="warning" showIcon message={errorMessage(detail.error)}/>}
      {detail.data && <DataTable rowKey={row=>`${row.id}:${row.source}:${row.time}`} pagination={false}
        dataSource={detail.data.logs ?? []} locale={{emptyText:'近期暂无 NiFi 错误或警告公告'}}
        scroll={{x:615,y:230}} columns={[
          {title:'时间',width:154,render:(_,row)=>formatTime(row.time)==='—'?row.time:formatTime(row.time)},
          {title:'级别',width:68,render:(_,row)=><Tag color={row.level==='ERROR'?'error':'warning'}>{row.level==='ERROR'?'错误':'警告'}</Tag>},
          {title:'来源',width:118,dataIndex:'source',ellipsis:true},
          {title:'消息',dataIndex:'message',ellipsis:true},
        ]}/>}
    </Modal>
  </Panel>;
}
