import { useState } from 'react';
import { Alert, Button, Modal, Select, Space, Table } from 'antd';
import { useMutation, useQuery } from '@tanstack/react-query';
import { applyRuntimeAttributes, previewRuntimeAttributes, type AttributeChange } from '../api/pipelines';
import { getSessionRevision } from '../api/bridgeSession';
import { getApiErrorMessage } from '../api/response';
import { getNifiOverlayContainer } from './overlayContainer';

const rowKey = (row: AttributeChange) => JSON.stringify([row.nodeId, row.field]);
const statuses: Record<string, string> = {
  READY: '可同步', MATCHED: '已一致', REVIEW: '待核对', CONFLICT: '两边均已修改',
  STOP_REQUIRED: '处理器运行中', UNAVAILABLE: '待核对关联',
  SYNCED: '已同步', KEPT_NATIVE: '已保留 NiFi 值', CHANGED: '已变化', RETRY: '待复核',
};

export default function NifiAttributeSyncDialog({ pipelineId, onClose }: { pipelineId: string; onClose: () => void }) {
  const [choices, setChoices] = useState<Record<string, string>>({});
  const [results, setResults] = useState<AttributeChange[] | null>(null);
  const revision = getSessionRevision();
  const preview = useQuery({
    queryKey: ['nifi-attribute-sync', pipelineId, revision],
    queryFn: () => previewRuntimeAttributes(pipelineId),
    retry: false,
    refetchOnWindowFocus: false,
  });
  const changes = preview.data?.changes ?? [];
  const choice = (row: AttributeChange) => choices[rowKey(row)] ?? 'skip';
  const selected = changes.filter(row => choice(row) !== 'skip');
  const sync = useMutation({
    mutationFn: async () => applyRuntimeAttributes(pipelineId, preview.data!.processGroupId, selected.map(row => ({
      nodeId: row.nodeId, field: row.field, processorId: row.processorId, currentValue: row.currentValue,
      designValue: row.designValue, keepNative: choice(row) === 'keep',
    }))),
    onSuccess: rows => { if (revision === getSessionRevision()) setResults(rows); },
  });
  const retry = () => {
    setChoices({});
    setResults(null);
    sync.reset();
    void preview.refetch();
  };

  return <Modal open title="同步属性到 NiFi" width={900} getContainer={getNifiOverlayContainer}
    onCancel={onClose} closable={!sync.isPending} maskClosable={!sync.isPending}
    footer={<Space>
      <Button onClick={onClose} disabled={sync.isPending}>关闭</Button>
      {results ? <Button onClick={retry}>重新预览</Button>
        : <Button type="primary" loading={sync.isPending} disabled={preview.isFetching || !selected.length || selected.length > 100}
          onClick={() => sync.mutate()}>应用所选属性（{selected.length}）</Button>}
    </Space>}>
    {(preview.error || sync.error) && <Alert type="error" showIcon style={{ marginBottom: 12 }}
      message={getApiErrorMessage(preview.error || sync.error, '属性同步请求失败')} />}
    <Table rowKey={rowKey} size="small" loading={preview.isFetching} dataSource={results ?? changes}
      pagination={{ pageSize: 10, hideOnSinglePage: true }}
      locale={{ emptyText: '暂无可同步的属性差异' }}
      columns={[
        { title: '节点', dataIndex: 'nodeLabel', ellipsis: true },
        { title: '属性', dataIndex: 'label', render: (value, row) => value || row.field },
        { title: 'NiFi 当前值', dataIndex: 'currentValue', render: value => value ?? '—' },
        { title: '海通保存值', dataIndex: 'designValue', render: value => value ?? '—' },
        { title: '状态', dataIndex: 'status', render: (value, row) => <span title={row.message}>{statuses[value] ?? value}
          {row.message && <div style={{ fontSize: 12 }}>{row.message}</div>}</span> },
        ...(results ? [] : [{ title: '处理方式', width: 130, render: (_: unknown, row: AttributeChange) => {
          if (row.status === 'UNAVAILABLE') return '—';
          return <Select size="small" value={choice(row)} disabled={sync.isPending || preview.isFetching} style={{ width: '100%' }}
            options={[{ value: 'skip', label: '跳过' }, { value: 'use', label: '采用海通', disabled: row.status === 'STOP_REQUIRED' },
              { value: 'keep', label: '保留 NiFi' }]}
            onChange={value => setChoices(current => ({ ...current, [rowKey(row)]: value }))} />;
        } }]),
      ]} />
  </Modal>;
}
