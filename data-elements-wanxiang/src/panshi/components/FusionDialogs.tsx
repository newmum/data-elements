import { useEffect } from 'react';
import { Button, Form, Modal, Select, Space } from 'antd';
import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import type { FusionJoin, FusionMapping, ModelContent } from '../domain/types';

const sourceOptions = (content: ModelContent) => content.entities.filter(entity => entity.role === 'SOURCE').map(entity => ({
  value: entity.id, label: `${entity.displayName || entity.name} · ${entity.name}`,
}));
const fieldsOf = (content: ModelContent, entityId?: string) => content.entities.find(entity => entity.id === entityId)?.fields.map(field => ({
  value: field.id, label: `${field.businessName || field.name} · ${field.name}`,
})) ?? [];

export function FusionJoinDialog({ value, content, onClose, onSave }: {
  value: FusionJoin | null; content: ModelContent; onClose: () => void; onSave: (join: FusionJoin) => void;
}) {
  const [form] = Form.useForm<FusionJoin>();
  const leftId = Form.useWatch('leftEntityId', form) || value?.leftEntityId;
  const rightId = Form.useWatch('rightEntityId', form) || value?.rightEntityId;
  const dedupe = Form.useWatch('dedupe', form) || value?.dedupe;
  useEffect(() => { form.resetFields(); if (value) form.setFieldsValue(value); }, [value, form]);
  const save = async () => {
    const next = await form.validateFields();
    if (next.leftEntityId === next.rightEntityId) { form.setFields([{ name: 'rightEntityId', errors: ['关联两端须为不同来源表'] }]); return; }
    if (next.rightCardinality === 'ONE' && next.dedupe !== 'NONE') { form.setFields([{ name: 'dedupe', errors: ['唯一右表无需取最新记录'] }]); return; }
    if (next.rightCardinality === 'MANY' && next.dedupe !== 'LATEST') { form.setFields([{ name: 'dedupe', errors: ['可能多条时须指定取最新记录'] }]); return; }
    if (new Set(next.pairs.map(pair => `${pair.leftFieldId}/${pair.rightFieldId}`)).size !== next.pairs.length) { form.setFields([{ name: 'pairs', errors: ['关联字段组合不能重复'] }]); return; }
    onSave({ ...next, id: value!.id });
  };
  return <Modal open={!!value} title="画布关联 · 来源表融合" width={740} maskClosable={false} onCancel={onClose}
    onOk={() => void save()} okText="保存关联" destroyOnHidden>
    <Form form={form} layout="vertical" style={{ paddingTop: 12 }}>
      <div className="ps-form-two">
        <Form.Item name="leftEntityId" label="左侧来源表" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={sourceOptions(content)} /></Form.Item>
        <Form.Item name="rightEntityId" label="接入来源表" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={sourceOptions(content)} /></Form.Item>
      </div>
      <div className="ps-form-two">
        <Form.Item name="joinType" label="关联方式" rules={[{ required: true }]}><Select options={[{ value: 'LEFT', label: 'LEFT JOIN · 保留左表数据' }, { value: 'INNER', label: 'INNER JOIN · 只保留匹配数据' }]} /></Form.Item>
        <Form.Item name="rightCardinality" label="右表每个关联键" rules={[{ required: true }]}><Select options={[{ value: 'ONE', label: '最多一条' }, { value: 'MANY', label: '可能多条，须定义取值方式' }]} /></Form.Item>
      </div>
      <div className="ps-form-two">
        <Form.Item name="dedupe" label="右表重复记录" rules={[{ required: true }]}><Select options={[{ value: 'NONE', label: '不去重（仅适用唯一键）' }, { value: 'LATEST', label: '按排序字段取最新一条' }]} /></Form.Item>
        {dedupe === 'LATEST' && <Form.Item name="orderFieldId" label="取最新记录的排序字段" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={fieldsOf(content, rightId)} /></Form.Item>}
      </div>
      <label style={{ display: 'block', marginBottom: 10 }}>字段关联条件 · 多个字段同时匹配</label>
      <Form.List name="pairs">
        {(items, { add, remove }, meta) => <>
          {items.map(item => <Space key={item.key} align="baseline" style={{ display: 'flex', marginBottom: 8 }}>
            <Form.Item {...item} name={[item.name, 'leftFieldId']} rules={[{ required: true, message: '请选择左表字段' }]}><Select style={{ width: 260 }} showSearch optionFilterProp="label" placeholder="左表字段" options={fieldsOf(content, leftId)} /></Form.Item>
            <span>=</span>
            <Form.Item {...item} name={[item.name, 'rightFieldId']} rules={[{ required: true, message: '请选择右表字段' }]}><Select style={{ width: 260 }} showSearch optionFilterProp="label" placeholder="右表字段" options={fieldsOf(content, rightId)} /></Form.Item>
            <Button icon={<DeleteOutlined />} type="text" danger disabled={items.length === 1} onClick={() => remove(item.name)} />
          </Space>)}
          <Button type="dashed" icon={<PlusOutlined />} onClick={() => add({ leftFieldId: '', rightFieldId: '' })}>增加关联字段</Button>
          <Form.ErrorList errors={meta.errors} />
        </>}
      </Form.List>
    </Form>
  </Modal>;
}

export function FusionMappingDialog({ value, content, onClose, onSave }: {
  value: FusionMapping | null; content: ModelContent; onClose: () => void; onSave: (mapping: FusionMapping) => void;
}) {
  const [form] = Form.useForm<FusionMapping>();
  const sourceId = Form.useWatch('sourceEntityId', form) || value?.sourceEntityId;
  useEffect(() => { form.resetFields(); if (value) form.setFieldsValue(value); }, [value, form]);
  const save = async () => onSave({ ...(await form.validateFields()), id: value!.id });
  return <Modal open={!!value} title="画布连线 · 输出字段映射" width={620} maskClosable={false} onCancel={onClose}
    onOk={() => void save()} okText="保存映射" destroyOnHidden>
    <Form form={form} layout="vertical" style={{ paddingTop: 12 }}>
      <Form.Item name="sourceEntityId" label="来源表" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={sourceOptions(content)} /></Form.Item>
      <Form.Item name="sourceFieldId" label="来源字段" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={fieldsOf(content, sourceId)} /></Form.Item>
      <Form.Item name="targetFieldId" label="目标表 D 的字段" rules={[{ required: true }]}><Select showSearch optionFilterProp="label" options={fieldsOf(content, content.outputEntityId)} /></Form.Item>
      <Form.Item name="transform" label="字段转换" rules={[{ required: true }]}><Select options={[
        { value: 'DIRECT', label: '直接取值' }, { value: 'TRIM', label: '去除首尾空格' },
        { value: 'UPPER', label: '转大写' }, { value: 'LOWER', label: '转小写' },
      ]} /></Form.Item>
    </Form>
  </Modal>;
}
