import { useEffect, useState } from 'react';
import { Button, Checkbox, Drawer, Form, Input, Select, Space, TreeSelect } from 'antd';
import { MinusCircleOutlined, PlusOutlined } from '@ant-design/icons';
import { assetsApi } from '../../../services/assets';
import { datasourceRegistrationApi, type SourceOption } from '../../../services/datasourceRegistrationApi';
import type { Demand, DemandDraftInput, DemandField, DeliveryChannel } from '../../../services/assets.types';
import { ConsumerError, channelNames, dateToIso, toLocalDate, useConsumerAction, useConsumerRead } from '../market/consumerShared';

function departmentTree(options: SourceOption[]): { value: string; title: string; children: ReturnType<typeof departmentTree> }[] {
  return options.flatMap(option => option.value === 'ROOT' ? departmentTree(option.children || []) : [{ value: option.value, title: option.label, children: departmentTree(option.children || []) }]);
}

export type EditableDemand = Demand & { draft?: DemandDraftInput };
interface DemandValues { title: string; scenario?: string; departmentId?: string; handlerDepartmentId?: string; resourceKinds?: Array<'TABLE' | 'API' | 'FILE'>; fields?: DemandField[]; expectedAt?: string; frequency?: string; channels?: DeliveryChannel[]; }
export function DemandDrawer({ initial, onClose, onSaved }: { initial?: EditableDemand; onClose: () => void; onSaved?: () => void }) {
  const [form] = Form.useForm<DemandValues>(), [saved, setSaved] = useState(initial), [error, setError] = useState<unknown>(null), { busy, run, operation } = useConsumerAction();
  const departments = useConsumerRead('demand-departments', () => datasourceRegistrationApi.choices().then(value => value.organizations));
  useEffect(() => { if (initial) form.setFieldsValue({ ...initial.draft, title: initial.title, departmentId: initial.departmentId || initial.draft?.departmentId, handlerDepartmentId: initial.handlerDepartmentId || initial.draft?.handlerDepartmentId, fields: initial.draft?.fields || initial.fields, expectedAt: toLocalDate(initial.draft?.expectedAt) }); }, [initial?.id]);
  const save = async (submit: boolean) => {
    await form.validateFields(submit ? undefined : ['title']); const values = form.getFieldsValue(); setError(null);
    const payload: DemandDraftInput = { ...values, fields: values.fields?.filter(item => item?.name?.trim()).map(item => ({ ...item, name: item.name.trim(), required: item.required ?? true })), title: values.title.trim(), demandId: saved?.id, expectedRevision: saved?.revision || '0', expectedAt: dateToIso(values.expectedAt, true) };
    await run(async () => { const result = await assetsApi.post<EditableDemand>('/demands/drafts', payload, operation({ action: 'SAVE_DEMAND', ...payload })); setSaved(result); if (submit) { const body = { expectedRevision: result.revision }; try { await assetsApi.post<Demand>(`/demands/${encodeURIComponent(result.id)}/submit`, body, operation({ action: 'SUBMIT_DEMAND', id: result.id, ...body })); } catch (cause) { setError(new Error(`草稿已保存，提交未完成：${cause instanceof Error ? cause.message : String(cause)}`)); return; } } onSaved?.(); onClose(); }, submit ? undefined : '需求草稿已保存');
  };
  return <Drawer className="hy-consumer-drawer" open size={620} title={initial ? '编辑数据需求' : '提交数据需求'} onClose={onClose} maskClosable={!busy} footer={<div className="hy-dialog-footer"><Button disabled={busy} onClick={onClose}>取消</Button><Button loading={busy} onClick={() => void save(false).catch(() => undefined)}>暂存草稿</Button><Button type="primary" loading={busy} onClick={() => void save(true).catch(() => undefined)}>提交需求</Button></div>}><ConsumerError error={error}/><Form form={form} layout="vertical" initialValues={{ resourceKinds: ['TABLE'], fields: [{ name: '', required: true }] }}>
    <Form.Item name="title" label="需求名称" rules={[{ required: true, whitespace: true, message: '填写需求名称' }]}><Input maxLength={255} placeholder="为本次数据需求命名"/></Form.Item>
    <ConsumerError error={departments.error} retry={departments.refresh}/>
    <div className="hy-form-two"><Form.Item name="departmentId" label="申请部门" rules={[{ required: true, message: '选择申请部门' }]}><TreeSelect showSearch treeNodeFilterProp="title" treeData={departmentTree(departments.data || [])} placeholder="选择您所属的申请部门"/></Form.Item><Form.Item name="handlerDepartmentId" label="对接处理部门" rules={[{ required: true, message: '选择对接处理部门' }]}><TreeSelect showSearch treeNodeFilterProp="title" treeData={departmentTree(departments.data || [])} placeholder="选择受理该需求的部门"/></Form.Item></div>
    <Form.Item name="scenario" label="业务场景与用途" rules={[{ required: true, whitespace: true, message: '说明业务场景与用途' }]}><Input.TextArea rows={4} maxLength={4000} showCount placeholder="说明需要解决的业务问题、数据范围和使用方式"/></Form.Item>
    <Form.Item name="resourceKinds" label="需要的资源类型" rules={[{ required: true, type: 'array', min: 1, message: '至少选择一种资源类型' }]}><Checkbox.Group options={[{ value: 'TABLE', label: '数据库表' }, { value: 'API', label: 'API 服务' }, { value: 'FILE', label: '文件资源' }]}/></Form.Item>
    <Form.Item label="期望数据项"><Form.List name="fields">{(fields, { add, remove }) => <Space direction="vertical" style={{ width: '100%' }}>{fields.map(field => <div className="hy-demand-field-row" key={field.key}><Form.Item name={[field.name, 'name']} style={{ margin: 0 }} rules={[{ required: true, whitespace: true, message: '填写数据项名称' }]}><Input placeholder="数据项名称" maxLength={255}/></Form.Item><Form.Item name={[field.name, 'dataType']} style={{ margin: 0 }}><Input placeholder="期望类型" maxLength={64}/></Form.Item><Button type="text" aria-label="删除数据项" icon={<MinusCircleOutlined/>} onClick={() => remove(field.name)}/></div>)}<Button type="dashed" icon={<PlusOutlined/>} onClick={() => add({ name: '', required: true })}>添加数据项</Button></Space>}</Form.List></Form.Item>
    <div className="hy-form-two"><Form.Item name="expectedAt" label="期望完成日期"><Input type="date"/></Form.Item><Form.Item name="frequency" label="更新频率"><Select allowClear options={['实时', '每日', '每周', '每月', '按需'].map(value => ({ value, label: value }))}/></Form.Item></div>
    <Form.Item name="channels" label="期望交付方式"><Select mode="multiple" allowClear options={Object.entries(channelNames).map(([value, label]) => ({ value, label }))}/></Form.Item>
  </Form></Drawer>;
}
