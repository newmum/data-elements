import { useEffect, useState } from 'react';
import { Alert, Button, Empty, Modal, Skeleton, Space, Tabs, Tag, Typography } from 'antd';
import { CheckCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { compileFieldMapping, mappingVersions, validateFieldMapping, type CompileResponse, type FieldMeta, type MappingIssue, type MappingTableRef, type UnifiedMappingDraft, type ValidationResponse } from '../api/fieldMapping';
import MappingDraftTable from './MappingDraftTable';
import { concreteInputLabel, concreteRuleLabel } from './mappingRulePresentation';
import { getNifiOverlayContainer } from './overlayContainer';

export function mappingErrorText(error: unknown): string {
  if (error && typeof error === 'object' && 'response' in error) {
    const response = error.response as { data?: { error?: string; message?: string } } | undefined;
    const detail = response?.data?.error ?? response?.data?.message;
    if (typeof detail === 'string' && detail) return detail;
  }
  return error instanceof Error ? error.message : '请求未完成，请检查当前会话或服务状态后重试';
}
export function MappingPlanView({ draft, metadata }: { draft: UnifiedMappingDraft; metadata?: Omit<NonNullable<CompileResponse['plan']>, 'executionPlan'> }) {
  return <Tabs className="fm-review-tabs" items={[
    { key: 'overview', label: '规则概览', children: <>
      <div className="fm-review-summary"><span>{draft.rules.length} 条规则</span><span>{draft.rules.filter(rule => rule.enabled !== false).length} 条启用</span>{metadata && <span>{metadata.dictionaryGroupCount} 组字典</span>}</div>
      <MappingDraftTable rowKey={rule => rule.ruleId} dataSource={draft.rules} size="small" sequenceColumnKey="sequence" fields={{ target: { kind: 'text', value: rule => rule.target.name } }} columns={[
        { title: '顺序', key:'sequence', width: 58 },
        { title: '取值来源', render: (_: unknown, rule) => concreteInputLabel(rule) },
        { title: '目标字段', key: 'target', render: (_: unknown, rule) => rule.target.name },
        { title: '处理规则', render: (_: unknown, rule) => concreteRuleLabel(rule) },
        { title: '状态', width: 76, render: (_: unknown, rule) => <Tag>{rule.enabled === false ? '停用' : '启用'}</Tag> },
      ]} />
      {metadata && <dl className="fm-review-metadata"><dt>引擎版本</dt><dd>{metadata.engineVersion}</dd><dt>计划指纹</dt><dd><Typography.Text copyable>{metadata.planHash}</Typography.Text></dd></dl>}
    </> },
    { key: 'json', label: '完整定义', children: <pre className="fm-code-panel">{JSON.stringify(metadata ? { ...metadata, executionPlan: draft } : draft, null, 2)}</pre> },
  ]} />;
}
type InspectionKind = 'validate' | 'compile' | 'schema';
type InspectionResult = { kind: 'validate'; data: ValidationResponse } | { kind: 'compile'; data: CompileResponse } | { kind: 'schema'; data: Record<string, unknown> };
const INSPECTIONS = {
  validate: { title: '校验治理规则', description: '检查规则结构、字段类型、必填约束和字典引用。校验不会修改草稿或运行任务。' },
  compile: { title: '查看执行计划', description: '将当前草稿编译为执行计划，查看实际生效的取值和转换顺序。此处不会部署或启动任务。' },
  schema: { title: '锁定字段结构', description: '读取当前授权来源表与目标表的真实字段结构，并将快照附到草稿。后续校验和发布会检查结构是否变化。' },
};
export function MappingInspectionDialog({ kind, draft, sourceSchema, targetSchema, sourceRef, targetRef, onClose, onApplySchema }: {
  kind: InspectionKind; draft: UnifiedMappingDraft; sourceSchema: FieldMeta[]; targetSchema: FieldMeta[];
  sourceRef?: MappingTableRef; targetRef?: MappingTableRef; onClose: () => void; onApplySchema: (schema: Record<string, unknown>) => void;
}) {
  const [result, setResult] = useState<InspectionResult | null>(null), [error, setError] = useState('');
  const [loading, setLoading] = useState(true), [revision, setRevision] = useState(0);
  const references=JSON.stringify({sourceRef,targetRef});
  useEffect(() => {
    let active = true; setLoading(true); setError('');
    const request = kind === 'validate' ? validateFieldMapping(draft, sourceSchema, targetSchema).then(data => ({ kind, data } as const))
      : kind === 'compile' ? compileFieldMapping(draft, sourceSchema, targetSchema).then(data => ({ kind, data } as const))
      : mappingVersions.schema(JSON.parse(references).sourceRef, JSON.parse(references).targetRef).then(data => ({ kind, data } as const));
    request.then(value => { if (active) setResult(value); }).catch(cause => { if (active) setError(mappingErrorText(cause)); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [kind, draft, sourceSchema, targetSchema, references, revision]);
  const info = INSPECTIONS[kind];
  return <Modal className="fm-rule-modal fm-review-modal" width={900} open title={info.title} getContainer={getNifiOverlayContainer} onCancel={onClose} footer={<div className="fm-review-footer">
    <Button icon={<ReloadOutlined />} loading={loading} onClick={() => setRevision(value => value + 1)}>重新读取</Button><Space><Button onClick={onClose}>关闭</Button>{kind === 'schema' && <Button type="primary" disabled={loading || !!error || result?.kind !== 'schema'} onClick={() => { if (result?.kind === 'schema') onApplySchema(result.data); }}>应用到当前草稿</Button>}</Space>
  </div>}>
    <Typography.Paragraph type="secondary">{info.description}</Typography.Paragraph>
    {loading ? <Skeleton active paragraph={{ rows: 5 }} /> : error ? <Alert type="error" showIcon message="读取未完成" description={error} /> : result?.kind === 'validate' ? <ValidationView result={result.data} draft={draft} />
      : result?.kind === 'compile' ? result.data.plan ? <MappingPlanView draft={result.data.plan.executionPlan} metadata={result.data.plan} /> : <Alert type="warning" showIcon message="服务未返回完整执行计划，请检查后端版本" />
      : result?.kind === 'schema' ? <SchemaView contract={result.data} /> : null}
  </Modal>;
}
function ValidationView({ result, draft }: { result: ValidationResponse; draft: UnifiedMappingDraft }) {
  const rows = [...result.errors, ...result.warnings].map((issue, index) => ({ ...issue, id: String(index) }));
  const issueField = (issue: MappingIssue) => {
    const match = /(?:rules|mappings)\[(\d+)]/.exec(issue.path);
    if (match) return draft.rules[Number(match[1])]?.target.name ?? issue.path;
    const named = draft.rules.find(rule => issue.value === rule.target.name || issue.message.endsWith('：' + rule.target.name) || issue.message.endsWith(': ' + rule.target.name));
    const labels:Record<string,string>={spec:'整体规则',target:'目标字段',input:'取值来源'};
    return named?.target.name ?? labels[issue.path] ?? issue.path;
  };
  return <>
    <div className="fm-review-summary"><Tag color={result.valid ? 'success' : 'error'} icon={result.valid ? <CheckCircleOutlined /> : undefined}>{result.valid ? '规则有效' : '需要修正'}</Tag><span>{result.errors.length} 项错误</span><span>{result.warnings.length} 项提示</span></div>
    {rows.length ? <MappingDraftTable size="small" rowKey={row => row.id} dataSource={rows} columns={[
      { title: '级别', width: 80, render: (_: unknown, row) => <Tag color={row.severity === 'ERROR' ? 'error' : 'warning'}>{row.severity === 'ERROR' ? '错误' : '提示'}</Tag> },
      { title: '字段 / 位置', width: 170, render: (_: unknown, row) => issueField(row) },
      { title: '检查结果', render: (_: unknown, row) => <div className="fm-diagnostic-message"><span>{row.message}</span>{!!row.suggestions?.length && <Typography.Text type="secondary">{row.suggestions.join('；')}</Typography.Text>}</div> },
    ]} /> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="规则检查通过，可以继续预览转换结果" />}
  </>;
}
interface ContractField { id: string; side: string; name: string; type: string; nullable: string }
function SchemaView({ contract }: { contract: Record<string, unknown> }) {
  const rows: ContractField[] = [];
  for (const side of ['source', 'target']) {
    const fields = contract[side];
    if (!Array.isArray(fields)) continue;
    for (const [index, raw] of fields.entries()) {
      if (!raw || typeof raw !== 'object') continue;
      const field = raw as Record<string, unknown>;
      rows.push({ id: side + index, side: side === 'source' ? '来源' : '目标', name: String(field.name ?? ''), type: String(field.logicalType ?? field.type ?? '保留类型'), nullable: field.nullable === false ? '不允许' : '允许' });
    }
  }
  return <><div className="fm-review-summary"><span>{rows.filter(row => row.side === '来源').length} 个来源字段</span><span>{rows.filter(row => row.side === '目标').length} 个目标字段</span><span>应用后随画布保存</span></div>
    <MappingDraftTable size="small" rowKey={row => row.id} dataSource={rows} fields={{ name: { kind: 'text', value: row => row.name } }} columns={[
      { title: '所属表', dataIndex: 'side', width: 88 }, { title: '字段名称', dataIndex: 'name', key: 'name' }, { title: '字段类型', dataIndex: 'type', width: 160 }, { title: '空值', dataIndex: 'nullable', width: 90 },
    ]} />
  </>;
}
export function MappingRuntimeDialog({ operation, onClose, onConfirm }: { operation: 'upgrade' | 'start'; onClose: () => void; onConfirm: () => Promise<void> }) {
  const [busy, setBusy] = useState(false), [error, setError] = useState('');
  const upgrade = operation === 'upgrade';
  return <Modal className="fm-rule-modal fm-review-modal" open width={620} title={upgrade ? '升级运行字段节点' : '启动已发布运行版本'} getContainer={getNifiOverlayContainer} onCancel={() => { if (!busy) onClose(); }} closable={!busy} maskClosable={!busy}
    footer={<Space><Button disabled={busy} onClick={onClose}>取消</Button><Button type="primary" loading={busy} onClick={async () => { setBusy(true); setError(''); try { await onConfirm(); onClose(); } catch (cause) { setError(mappingErrorText(cause)); } finally { setBusy(false); } }}>{upgrade ? '升级并启动字段节点' : '启动已发布版本'}</Button></Space>}>
    <Typography.Paragraph type="secondary">{upgrade ? '更新当前任务已发布流程中的字段治理处理节点。请先保存画布，并确认当前规则已发布。' : '使用最近一次成功发布的规则启动当前任务，继续处理来源数据。'}</Typography.Paragraph>
    <dl className="fm-operation-details"><dt>作用范围</dt><dd>{upgrade ? '当前任务的字段治理节点' : '当前任务的已发布流程'}</dd><dt>数据进度</dt><dd>保留来源增量水位，不重置采集位置</dd><dt>当前草稿</dt><dd>{upgrade ? '必须与已保存发布计划一致；不匹配时会拒绝升级' : '未发布的草稿不会在此次启动中生效'}</dd></dl>
    <Alert showIcon type="warning" message={upgrade ? '升级会替换字段节点并启动流程；存在排队记录时将阻止升级。' : '启动后会实际读取来源并写入目标，请确认运行版本。'} />
    {error && <Alert className="fm-dialog-error" type="error" showIcon message="操作未完成" description={error} />}
  </Modal>;
}
