import { useState } from 'react';
import { Alert, Button, Checkbox, Descriptions, Drawer, Form, Input, Modal, Select, Skeleton, Table, Tabs, Tag } from 'antd';
import { useNavigate } from 'react-router-dom';
import { assetsApi } from '../../../services/assets';
import type { AssetPage, Demand, DemandMatchResult, MatchCandidate } from '../../../services/assets.types';
import { ConsumerEmpty, ConsumerError, ConsumerName, ConsumerStatus, EventHistory, can, channelNames, formatTime, useConsumerAction, useConsumerRead } from '../market/consumerShared';
import { DemandDrawer, type EditableDemand } from './DemandDrawer';

export function DemandDetailDrawer({ id, onClose }: { id: string; onClose: () => void }) {
  const query = useConsumerRead(`demand:${id}`, signal => assetsApi.get<EditableDemand>(`/demands/${encodeURIComponent(id)}`, {}, { signal }));
  const [editing, setEditing] = useState(false), [action, setAction] = useState<string>(), [keyword, setKeyword] = useState(''), [selected, setSelected] = useState<string[]>([]), [form] = Form.useForm<{ content: string; createSubscriptionDraft?: boolean }>();
  const { busy, run, operation } = useConsumerAction(), nav = useNavigate();
  const candidates = useConsumerRead(`demand-candidates:${keyword}`, signal => assetsApi.get<AssetPage>('/search', { view: 'MARKET', keyword, pageNo: 1, pageSize: 50 }, { signal }), action === 'RECOMMEND');
  const demand = query.data;
  const handle = async () => {
    await form.validateFields(); if (!demand || !action) return; const values = form.getFieldsValue();
    await run(async () => {
      if (action === 'CONFIRM_MATCH') {
        const chosen = (demand.matches || []).filter(item => selected.includes(`${item.catalogId}:${item.versionId}`));
        if (!chosen.length) throw new Error('至少选择一个候选资源');
        const body = { expectedRevision: demand.revision, candidates: chosen, confirmation: values.content.trim(), createSubscriptionDraft: !!values.createSubscriptionDraft };
        const result = await assetsApi.post<DemandMatchResult>(`/demands/${encodeURIComponent(id)}/match`, body, operation({ id, action, ...body }));
        if (result.subscriptionDraftId) nav(`/assets/workbench/subscriptions?subscriptionId=${encodeURIComponent(result.subscriptionDraftId)}`);
      } else if (action === 'WITHDRAW') {
        const body = { expectedRevision: demand.revision, action: 'WITHDRAW', reason: values.content.trim() };
        await assetsApi.patch<Demand>(`/demands/${encodeURIComponent(id)}`, body, operation({ id, ...body }));
      } else {
        const chosen: MatchCandidate[] = (candidates.data?.items || []).filter(item => selected.includes(item.object.id) && item.publishedVersionId).map(item => ({ catalogId: item.object.id, versionId: item.publishedVersionId!, matchedFieldNames: [], missingFieldNames: [], channels: item.channels || [], reason: values.content.trim() }));
        if (action === 'RECOMMEND' && !chosen.length) throw new Error('请选择已发布的候选目录');
        const body = { expectedRevision: demand.revision, action, content: values.content.trim(), ...(action === 'RECOMMEND' ? { candidates: chosen } : {}) };
        await assetsApi.post<Demand>(`/demands/${encodeURIComponent(id)}/responses`, body, operation({ id, ...body }));
      }
      setAction(undefined); setSelected([]); form.resetFields(); query.refresh();
    }, '对接记录已保存');
  };
  const actionNames: Record<string, string> = { ACCEPT: '受理需求', COMMENT: '补充说明', RECOMMEND: '推荐资源', RETURN_FOR_INFO: '退回补充', MARK_UNFULFILLED: '无法满足', CONFIRM_MATCH: '确认匹配', WITHDRAW: '撤回需求' };
  return <><Drawer open className="hy-consumer-drawer" size={680} title="需求详情与供需对接" onClose={onClose} footer={demand && <div className="hy-dialog-footer">{can(demand.allowedActions, 'EDIT') && <Button onClick={() => setEditing(true)}>编辑需求</Button>}{Object.entries(actionNames).filter(([value]) => can(demand.allowedActions, value)).map(([value, label]) => <Button key={value} onClick={() => { setSelected([]); form.resetFields(); setAction(value); }}>{label}</Button>)}</div>}><ConsumerError error={query.error} retry={query.refresh}/>{query.loading && !demand ? <Skeleton active/> : demand && <><ConsumerName name={demand.title} code={demand.id}/><div style={{ margin: '18px 0' }}><ConsumerStatus value={demand.status}/></div><Tabs destroyOnHidden items={[
    { key: 'info', label: '需求信息', children: <><Descriptions column={1} items={[{ key: 'scenario', label: '业务场景', children: demand.draft?.scenario || '—' }, { key: 'expected', label: '期望完成', children: formatTime(demand.draft?.expectedAt) }, { key: 'frequency', label: '更新频率', children: demand.draft?.frequency || '—' }, { key: 'channels', label: '期望交付', children: demand.draft?.channels?.map(item => channelNames[item]).join('、') || '—' }]}/><Table rowKey={(row, index) => row.code || `${row.name}:${index}`} dataSource={demand.fields} pagination={false} size="middle" columns={[{ title: '数据项名称', dataIndex: 'name', ellipsis: true, sorter: (a, b) => a.name.localeCompare(b.name, 'zh-CN') }, { title: '期望类型', dataIndex: 'dataType', width: 150, ellipsis: true }, { title: '必需', dataIndex: 'required', width: 70, render: value => value ? '是' : '否' }]}/>{demand.closeReason && <Alert type="info" title="关闭原因" description={demand.closeReason}/>}</> },
    { key: 'events', label: '对接记录', children: <EventHistory events={demand.events}/> },
    { key: 'matches', label: '匹配资源', children: demand.matches.length ? demand.matches.map(candidate => <div className="hy-demand-match" key={`${candidate.catalogId}:${candidate.versionId}`}><h4>{candidate.catalogId}</h4>{candidate.channels.map(channel => <Tag key={channel}>{channelNames[channel]}</Tag>)}{candidate.reason && <p>{candidate.reason}</p>}{!!candidate.matchedFieldNames.length && <p>匹配数据项：{candidate.matchedFieldNames.join('、')}</p>}{!!candidate.missingFieldNames.length && <p>仍缺少：{candidate.missingFieldNames.join('、')}</p>}<Button type="link" onClick={() => nav(`/assets/market/${encodeURIComponent(candidate.catalogId)}`)}>查看资源详情</Button></div>) : <ConsumerEmpty text="暂无匹配资源"/> },
  ]}/></>}</Drawer>
    <Modal title={actionNames[action || '']} open={!!action} width={600} onCancel={() => setAction(undefined)} confirmLoading={busy} okText="确认" cancelText="取消" onOk={() => void handle().catch(() => undefined)}><Form form={form} layout="vertical">
      {action === 'RECOMMEND' && <><Input.Search placeholder="搜索已上架目录" onSearch={setKeyword} allowClear/><ConsumerError error={candidates.error} retry={candidates.refresh}/><Select style={{ width: '100%', margin: '14px 0' }} mode="multiple" value={selected} onChange={setSelected} optionFilterProp="label" loading={candidates.loading} placeholder="选择要推荐的目录" options={(candidates.data?.items || []).filter(item => item.publishedVersionId).map(item => ({ value: item.object.id, label: item.object.name }))}/></>}
      {action === 'CONFIRM_MATCH' && <><Checkbox.Group style={{ display: 'grid', gap: 10, marginBottom: 20 }} value={selected} onChange={values => setSelected(values as string[])}>{demand?.matches.map(item => <Checkbox key={`${item.catalogId}:${item.versionId}`} value={`${item.catalogId}:${item.versionId}`}>{item.catalogId} · {item.versionId}</Checkbox>)}</Checkbox.Group><Form.Item name="createSubscriptionDraft" valuePropName="checked"><Checkbox>确认后生成订阅草稿</Checkbox></Form.Item></>}
      <Form.Item name="content" label={action === 'WITHDRAW' ? '撤回原因' : '处理说明'} rules={[{ required: true, whitespace: true, message: '填写处理说明' }]}><Input.TextArea rows={4} maxLength={4000}/></Form.Item>
    </Form></Modal>
    {editing && demand && <DemandDrawer initial={demand} onClose={() => setEditing(false)} onSaved={query.refresh}/>}
  </>;
}
