import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, ConfigProvider, Empty, Input, InputNumber, Modal, Segmented, Skeleton, Space, Tabs, Tag, Typography } from 'antd';
import { DatabaseOutlined, ExperimentOutlined, PlayCircleOutlined } from '@ant-design/icons';
import { isAxiosError } from 'axios';
import { mappingVersions, previewFieldMapping, type FieldMeta, type MappingTableRef, type PreviewResponse, type UnifiedMappingDraft } from '../api/fieldMapping';
import { useSessionRevision } from '../api/bridgeSession';
import { getNifiOverlayContainer } from './overlayContainer';
import MappingDraftTable from './MappingDraftTable';
import { mappingErrorText } from './MappingReviewDialogs';
import { checkedPreview, parsePreviewSamples, previewCell, previewTableRows, previewValue, type PreviewTableRow } from './governancePreview';

interface Props {
  open: boolean; onClose: () => void; draft: UnifiedMappingDraft; sourceRef?: MappingTableRef;
  sourceSchema: FieldMeta[]; targetSchema: FieldMeta[]; sourceLabels?: Record<string, string>; targetLabels?: Record<string, string>;
}
type SampleMode = 'source' | 'manual';
interface Outcome { result: PreviewResponse; inputs: Record<string, unknown>[]; mode: SampleMode; sampleText: string; limit: number }

export default function GovernancePreviewDialog({ open, onClose, draft, sourceRef, sourceSchema, targetSchema, sourceLabels = {}, targetLabels = {} }: Props) {
  const [mode, setMode] = useState<SampleMode>(sourceRef ? 'source' : 'manual');
  const [limit, setLimit] = useState(20), [sampleText, setSampleText] = useState('[\n  {}\n]');
  const [loading, setLoading] = useState(false), [error, setError] = useState('');
  const [outcome, setOutcome] = useState<Outcome | null>(null), [resultTab, setResultTab] = useState('output');
  const sessionRevision = useSessionRevision(), request = useRef(0), running = useRef(false);
  const sourceSignature = JSON.stringify(sourceRef);
  useEffect(() => {
    request.current++; running.current = false; setLoading(false); setError(''); setOutcome(null);
    return () => { request.current++; running.current = false; };
  }, [open, draft, sourceSignature, sessionRevision]);
  useEffect(() => { setSampleText('[\n  {}\n]'); setMode(sourceRef ? 'source' : 'manual'); }, [sourceSignature, sessionRevision]);

  async function run() {
    if (running.current) return;
    const revision = ++request.current; running.current = true; setLoading(true); setError('');
    try {
      let inputs: Record<string, unknown>[] = [], response: PreviewResponse;
      if (mode === 'source') {
        if (!sourceRef) throw new Error('请先配置来源表，或切换到手工样本');
        const sampled = await mappingVersions.sample(draft, sourceRef, limit);
        if (!Array.isArray(sampled.sampleRows) || sampled.sampleRows.length > 100 || sampled.sampleRows.some(row => !row || typeof row !== 'object' || Array.isArray(row))) throw new Error('服务未返回完整来源样本，请重新取样');
        inputs = sampled.sampleRows; response = sampled;
      } else {
        inputs = parsePreviewSamples(sampleText); response = await previewFieldMapping(draft, inputs);
      }
      const result = checkedPreview(response);
      if (revision === request.current) {
        setOutcome({ result, inputs, mode, sampleText, limit });
        setResultTab(!result.resultRows.length && result.rowResults.some(row => !row.success) ? 'errors' : 'output');
      }
    } catch (cause) {
      if (revision === request.current) setError(isAxiosError(cause) && cause.code === 'ECONNABORTED'
        ? '预览请求超时，请重试；若持续超时，请检查来源表或字典连接。'
        : mappingErrorText(cause));
    }
    finally { if (revision === request.current) { running.current = false; setLoading(false); } }
  }

  const errors = outcome?.result.rowResults.filter(row => !row.success) ?? [];
  const successfulIndices = outcome?.result.rowResults.filter(row => row.success).map(row => row.rowIndex);
  const stale = outcome && (Boolean(error) || outcome.mode !== mode || (mode === 'manual' ? outcome.sampleText !== sampleText : outcome.limit !== limit));
  return <ConfigProvider componentSize="small" theme={{ token: { controlHeightSM: 28, fontSizeSM: 13 } }}>
    <Modal className="fm-rule-modal fm-governance-preview" width={1120} open={open} title="治理预览" getContainer={getNifiOverlayContainer} onCancel={onClose}
      footer={<div className="fm-rule-footer"><Typography.Text type="secondary">仅试算当前治理草稿，不保存规则或启动任务。</Typography.Text><Button size="middle" onClick={onClose}>关闭</Button></div>}>
      <div className="fm-preview-workspace">
        <section className="fm-preview-input" aria-label="预览样本">
          <div className="fm-preview-pane-heading"><strong>准备样本</strong><Typography.Text type="secondary">最多 100 行</Typography.Text></div>
          <Segmented block disabled={loading} aria-label="样本输入方式" value={mode} onChange={value => { if (value === 'source' || value === 'manual') setMode(value); setError(''); }} options={[
            { value: 'source', label: '来源取样', disabled: !sourceRef }, { value: 'manual', label: '手工样本' },
          ]}/>
          {mode === 'source' ? <>
            <div className="fm-preview-source"><DatabaseOutlined/><div><Typography.Text strong>{sourceRef?.table || '当前节点的来源表'}</Typography.Text><Typography.Text type="secondary">读取授权来源样本，敏感值由服务端脱敏返回。</Typography.Text></div></div>
            <div className="fm-preview-limit"><Typography.Text>采样行数</Typography.Text><InputNumber aria-label="采样行数" disabled={loading} min={1} max={100} value={limit} onChange={value => setLimit(Math.max(1, Math.min(100, value ?? 20)))}/><Typography.Text type="secondary">1–100 行</Typography.Text></div>
          </> : <div className="fm-preview-manual"><Typography.Text>样本 JSON</Typography.Text><Input.TextArea aria-label="手工样本 JSON" disabled={loading} value={sampleText} onChange={event => { setSampleText(event.target.value); setError(''); }} rows={9} spellCheck={false} status={error ? 'error' : undefined}/><Typography.Text type="secondary">填写对象数组，例如 {`[{"字段名":"值"}]`}。</Typography.Text></div>}
          <Button block type="primary" icon={<PlayCircleOutlined/>} loading={loading} onClick={() => void run()}>{mode === 'source' ? '取样并预览' : '执行样本预览'}</Button>
          {error && <Alert className="fm-preview-request-error" showIcon type="error" message="预览未完成" description={error}/>}
        </section>
        <section className="fm-preview-output" aria-label="治理预览结果" aria-busy={loading}>
          <div className="fm-preview-pane-heading"><strong>预览结果</strong>{outcome && <Space size={8}><Typography.Text type="secondary">{outcome.mode === 'source' ? '来源取样' : '手工样本'} · {outcome.result.rowResults.length} 行</Typography.Text>{stale && <Tag color="warning">待重新预览</Tag>}</Space>}</div>
          {loading ? <div className="fm-preview-loading"><Skeleton active paragraph={{ rows: 7 }}/><Typography.Text type="secondary">正在执行当前治理规则…</Typography.Text></div>
            : !outcome ? <div className="fm-preview-empty"><ExperimentOutlined/><strong>查看治理规则的实际输出</strong><Typography.Text type="secondary">在左侧准备样本并执行预览，结果和逐行错误会显示在这里。</Typography.Text></div>
            : <>
              <div className="fm-preview-summary"><Tag color={errors.length ? 'warning' : 'success'}>{errors.length ? '存在未通过样本' : '预览完成'}</Tag><span>成功 <b>{outcome.result.resultRows.length}</b> 行</span><span>错误 <b>{errors.length}</b> 行</span><Typography.Text type="secondary">字典查询 {String(outcome.result.stats?.dictionarySqlCount ?? 0)} 次</Typography.Text></div>
              <Tabs className="fm-preview-result-tabs" activeKey={resultTab} onChange={setResultTab} items={[
                { key: 'output', label: '输出结果', children: <PreviewRecords records={outcome.result.resultRows} indices={successfulIndices} schema={targetSchema} labels={targetLabels} empty="没有成功输出的样本，请查看错误详情或调整输入"/> },
                { key: 'input', label: '输入样本', children: <PreviewRecords records={outcome.inputs} schema={sourceSchema} labels={sourceLabels} empty="本次没有返回输入样本"/> },
                { key: 'errors', label: `错误详情${errors.length ? ' · ' + errors.length : ''}`, children: errors.length ? <MappingDraftTable className="fm-preview-table" rowKey={row => String(row.rowIndex)} dataSource={errors} size="small" fields={{ index: { kind: 'number', value: row => row.rowIndex } }} columns={[
                  { title: '样本行', key: 'index', width: 88, render: (_: unknown, row) => row.rowIndex + 1 },
                  { title: '错误原因', render: (_: unknown, row) => <div className="fm-preview-errors">{row.errors.map((detail, index) => <div key={index}>{detail}</div>)}</div> },
                ]}/> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="本次预览没有行级错误"/> },
                { key: 'json', label: '结果 JSON', children: <pre className="fm-code-panel">{JSON.stringify(outcome.result.resultRows, null, 2)}</pre> },
              ]}/>
            </>}
        </section>
      </div>
    </Modal>
  </ConfigProvider>;
}

function PreviewRecords({ records, indices, schema, labels, empty }: { records: Record<string, unknown>[]; indices?: number[]; schema: FieldMeta[]; labels: Record<string, string>; empty: string }) {
  const container = useRef<HTMLDivElement>(null), [height, setHeight] = useState(240);
  useLayoutEffect(() => {
    const element = container.current;
    if (!element) return;
    const observer = new ResizeObserver(() => setHeight(Math.max(80, element.clientHeight - 40)));
    observer.observe(element);
    return () => observer.disconnect();
  }, [records.length]);
  const rows = useMemo(() => previewTableRows(records, indices), [records, indices]);
  const names = useMemo(() => [...new Set(records.flatMap(row => Object.keys(row)))], [records]);
  const fields = useMemo<Record<string, { kind: 'number' | 'text'; value: (row: PreviewTableRow) => unknown }>>(() => Object.fromEntries(names.flatMap((name, index) => {
    const values = records.map(row => previewCell(row, name)).filter(value => value != null);
    if (values.some(value => typeof value === 'object' || String(value).length > 1024)) return [];
    const type = schema.find(field => field.name === name)?.logicalType ?? '';
    const numeric = values.length > 0 && values.every(value => typeof value === 'number' || ['INT', 'LONG', 'DECIMAL', 'DOUBLE'].includes(type) && typeof value === 'string' && /^-?\d+(?:\.\d+)?(?:[eE][+-]?\d+)?$/.test(value));
    return [[String(index), { kind: numeric ? 'number' : 'text', value: (row: PreviewTableRow) => previewCell(row.record, name) == null ? null : previewValue(previewCell(row.record, name)) }]];
  })), [records, names, schema]);
  return records.length ? <div className="fm-preview-records" ref={container}><MappingDraftTable className="fm-preview-table" size="small" rowKey={row => row.key} dataSource={rows} fields={fields} scroll={{ x: 'max-content', y: height }} locale={{ emptyText: empty }} columns={[
    { title: '样本行', dataIndex: 'sequence', width: 72, fixed: 'left' },
    ...names.map((name, index) => ({ title: <span title={name}>{labels[name] || name}</span>, key: String(index), width: 150, ellipsis: true,
      render: (_: unknown, row: PreviewTableRow) => { const value = previewCell(row.record, name); return <span className={value == null || value === '' ? 'fm-preview-empty-value' : undefined} title={previewValue(value)}>{previewValue(value)}</span>; } })),
  ]}/></div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={empty}/>;
}
