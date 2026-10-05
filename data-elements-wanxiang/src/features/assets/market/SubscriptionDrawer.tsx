import { useEffect, useState } from 'react';
import { Alert, Button, Checkbox, Drawer, Form, Input, Select, Spin } from 'antd';
import { useNavigate } from 'react-router-dom';
import { assetsApi } from '../../../services/assets';
import type { Attachment, AssetPage, Subscription, SubscriptionDraftInput, VersionDetail } from '../../../services/assets.types';
import { ConsumerError, ConsumerName, channelNames, dateToIso, toLocalDate, useConsumerAction, useConsumerRead } from './consumerShared';

// Published attachment identifiers are returned by the backend; never infer a snapshot ID from a live table ID.
export type SubscriptionAttachment = Attachment & { resourceSnapshotId?: string };
export type SubscriptionVersion = Omit<VersionDetail, 'attachments'> & { attachments: SubscriptionAttachment[] };
export type EditableSubscription = Subscription & { draft?: SubscriptionDraftInput & { recommendedCatalogId?: string; recommendedVersionId?: string }; purpose?: string; businessScenario?: string; applicationId?: string; validFrom?: string; validUntil?: string };
interface SubscriptionValues { title: string; purpose?: string; businessScenario?: string; applicationId?: string; validFrom?: string; validUntil?: string; attachmentId?: string; itemIds?: string[]; }

export function SubscriptionDrawer({ catalogId, versionId, initial, initialAttachmentId, onClose, onSaved }: { catalogId: string; versionId: string; initial?: EditableSubscription; initialAttachmentId?: string; onClose: () => void; onSaved?: () => void }) {
  const nav = useNavigate(), [form] = Form.useForm<SubscriptionValues>(), [saved, setSaved] = useState<EditableSubscription | undefined>(initial);
  const [error, setError] = useState<unknown>(null), { busy, run, operation } = useConsumerAction();
  const version = useConsumerRead(`subscription-version:${catalogId}:${versionId}`, signal => assetsApi.get<SubscriptionVersion>(`/catalogs/${encodeURIComponent(catalogId)}/versions/${encodeURIComponent(versionId)}`, {}, { signal }));
  const applications = useConsumerRead('subscription-applications', signal => assetsApi.get<AssetPage>('/search', { view: 'MAP', kinds: 'APPLICATION', pageSize: 100, pageNo: 1, sortBy: 'name', sortOrder: 'asc' }, { signal }));
  const attachmentId = Form.useWatch('attachmentId', form), itemIds = Form.useWatch('itemIds', form) || [];
  const attachments = (version.data?.attachments || []).filter(item => item.bindingRole === 'DELIVERY' && item.resourceType !== 'DIRECTORY' && item.channel && item.resourceSnapshotId && item.resourceAvailability !== 'MISSING' && item.resourceAvailability !== 'INCOMPATIBLE');
  const chosen = attachments.find(item => item.id === attachmentId);
  const availableItems = (version.data?.items || []).filter(item => item.id && (!chosen?.mappings.length || chosen.mappings.some(mapping => mapping.catalogItemId === item.id)));
  useEffect(() => {
    if (!version.data) return;
    const draft = initial?.draft || initial;
    const resource = initial?.resources.find(item => item.catalogId === catalogId && item.versionId === versionId);
    form.setFieldsValue({ title: initial?.title || `${version.data.catalogName}订阅申请`, purpose: draft?.purpose, businessScenario: draft?.businessScenario, applicationId: draft?.applicationId, validFrom: toLocalDate(draft?.validFrom), validUntil: toLocalDate(draft?.validUntil), attachmentId: resource?.attachmentId || initialAttachmentId || attachments[0]?.id, itemIds: resource?.itemIds || [] });
  // Initialize once per object/version; a background refresh must not erase an edited form.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [version.data?.version.id, initial?.id]);
  const save = async (submit: boolean) => {
    setError(null);
    const values = await form.validateFields(submit ? undefined : ['title']);
    const all = { ...form.getFieldsValue(), ...values };
    if (submit && (!chosen?.id || !chosen.resourceSnapshotId || !chosen.channel || !all.itemIds?.length)) { setError(new Error('请选择当前发布版本的交付资源和所需数据项。')); return; }
    if (all.validFrom && all.validUntil && all.validUntil < all.validFrom) { setError(new Error('使用截止日期不能早于开始日期。')); return; }
    const resources = chosen?.id && chosen.resourceSnapshotId && chosen.channel && all.itemIds?.length ? [{ catalogId, versionId, attachmentId: chosen.id, resourceSnapshotId: chosen.resourceSnapshotId, channel: chosen.channel, itemIds: all.itemIds }] : [];
    const payload: SubscriptionDraftInput = { subscriptionId: saved?.id, expectedRevision: saved?.revision || '0', requestType: 'CATALOG_SUBSCRIPTION', title: all.title.trim(), purpose: all.purpose?.trim(), businessScenario: all.businessScenario?.trim(), applicationId: all.applicationId, validFrom: dateToIso(all.validFrom), validUntil: dateToIso(all.validUntil, true), resources };
    await run(async () => {
      const updated = await assetsApi.post<EditableSubscription>('/subscriptions/drafts', payload, operation({ action: 'SAVE_SUBSCRIPTION', ...payload })); setSaved(updated);
      if (submit) {
        const body = { expectedRevision: updated.revision };
        try { await assetsApi.post<Subscription>(`/subscriptions/${encodeURIComponent(updated.id)}/submit`, body, operation({ action: 'SUBMIT_SUBSCRIPTION', id: updated.id, ...body })); }
        catch (cause) { setError(new Error(`草稿已保存，提交未完成：${cause instanceof Error ? cause.message : String(cause)}`)); return; }
      }
      onSaved?.(); onClose(); nav('/assets/workbench/subscriptions');
    }, submit ? undefined : '订阅草稿已保存');
  };
  return <Drawer className="hy-consumer-drawer" open title={initial ? '编辑订阅申请' : '申请目录订阅'} size={600} onClose={onClose} maskClosable={!busy} footer={<div className="hy-dialog-footer"><Button onClick={onClose} disabled={busy}>取消</Button><Button loading={busy} onClick={() => void save(false).catch(() => undefined)}>暂存草稿</Button><Button type="primary" loading={busy} disabled={!version.data || !attachments.length} onClick={() => void save(true).catch(() => undefined)}>提交申请</Button></div>}>
    <ConsumerError error={version.error || error} retry={version.error ? version.refresh : undefined}/>
    {version.loading && !version.data ? <Spin/> : version.data && <><ConsumerName name={version.data.catalogName} code={`发布版本 v${version.data.version.versionNo}`}/>{!attachments.length && <Alert style={{ marginTop: 18 }} type="warning" showIcon title="当前版本暂无可申请的交付资源" description="资源提供方完成交付资源挂接和发布后，可选择数据项提交申请。"/>}
      <Form form={form} layout="vertical" style={{ marginTop: 22 }}>
        <Form.Item name="title" label="申请名称" rules={[{ required: true, whitespace: true, message: '填写申请名称' }]}><Input maxLength={255}/></Form.Item>
        <Form.Item name="purpose" label="使用用途" rules={[{ required: true, whitespace: true, message: '填写数据使用用途' }]}><Input.TextArea rows={3} maxLength={2000} showCount placeholder="说明使用目的和预期用途"/></Form.Item>
        <Form.Item name="businessScenario" label="业务场景" rules={[{ required: true, whitespace: true, message: '说明数据将用于哪些具体业务' }]}><Input.TextArea rows={2} maxLength={4000} placeholder="说明数据将用于哪些具体业务"/></Form.Item>
        <Form.Item name="applicationId" label="使用系统"><Select allowClear showSearch optionFilterProp="label" loading={applications.loading} placeholder="选择已登记的应用系统" options={(applications.data?.items || []).map(item => ({ value: item.object.id, label: item.object.name }))}/></Form.Item>
        <ConsumerError error={applications.error} retry={applications.refresh}/>
        <Form.Item name="attachmentId" label="申请资源与交付方式" rules={[{ required: true, message: '选择交付资源' }]}><Select onChange={() => form.setFieldValue('itemIds', [])} options={attachments.map(item => ({ value: item.id, label: `${item.resource.name} · ${channelNames[item.channel || ''] || item.channel}` }))} placeholder="选择交付资源"/></Form.Item>
        <Form.Item name="itemIds" label={<span>申请数据项 <small className="hy-text-muted">已选 {itemIds.length} 项</small></span>} rules={[{ type: 'array', required: true, min: 1, message: '至少选择一个数据项' }]}>
          <Checkbox.Group className="hy-field-selection">{availableItems.map(item => <Checkbox key={item.id} value={item.id} title={`${item.name} · ${item.code}`}>{item.name}</Checkbox>)}</Checkbox.Group>
        </Form.Item>
        {!!availableItems.length && <Button type="link" onClick={() => form.setFieldValue('itemIds', availableItems.map(item => item.id))}>选择当前资源全部数据项</Button>}
        <div className="hy-form-two"><Form.Item name="validFrom" label="开始日期" rules={[{ required: true, message: '选择开始日期' }]}><Input type="date"/></Form.Item><Form.Item name="validUntil" label="截止日期" rules={[{ required: true, message: '选择截止日期' }]}><Input type="date"/></Form.Item></div>
      </Form></>}
  </Drawer>;
}
