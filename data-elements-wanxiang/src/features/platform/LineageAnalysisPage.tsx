import { useEffect, useMemo, useState } from 'react';
import { ApartmentOutlined, CodeOutlined, DatabaseOutlined, ReloadOutlined, TableOutlined } from '@ant-design/icons';
import { Alert, App, Button, Checkbox, Descriptions, Drawer, Empty, Input, Pagination, Segmented, Select, Space, Spin, Tabs, Tag, Tooltip } from 'antd';
import { Background, Controls, MarkerType, Position, ReactFlow } from '@xyflow/react';
import type { Edge as FlowEdge, Node as FlowNode } from '@xyflow/react';
import { useSearchParams } from 'react-router-dom';
import { useStudio } from '../er/store';
import { Page, date } from './common';
import { SharedLineagePage } from './ProcessingLineagePage';
import {
  analyzeLineage, getLineageCoverage, getLineageEvidence, searchLineageObjects,
  type LineageAnalysis, type LineageCoverage, type LineageKind, type LineageMode,
  type LineageObject, type LineageRelation, type LineageScope,
} from '../../services/lineage';
import './lineage-analysis.css';

const kindLabels: Record<LineageKind, string> = { TABLE: '数据表', FIELD: '字段', MODEL: '模型' };
const evidenceLabels: Record<string, string> = {
  PIPELINE_PATH: '加工图路径', EXPLICIT_FIELD_RULE: '显式字段规则',
  REGISTERED_TASK: '登记接入任务', REGISTERED_FIELD_RULE: '登记字段映射',
};
const modeDescriptions: Record<LineageMode, string> = {
  FULL: '同时追溯上游来源和下游去向',
  ATTRIBUTION: '从当前对象向上追溯数据来自哪里',
  IMPACT: '从当前对象向下查看变更可能影响哪里',
};

function objectIcon(kind: LineageKind) {
  if (kind === 'FIELD') return <CodeOutlined />;
  if (kind === 'MODEL') return <ApartmentOutlined />;
  return <TableOutlined />;
}

function errorText(error: unknown) { return error instanceof Error ? error.message : String(error); }

function graphLayout(analysis: LineageAnalysis, scope: LineageScope): {nodes: FlowNode[]; edges: FlowEdge[]} {
  const groups = new Map<number, LineageObject[]>();
  for (const node of analysis.nodes) {
    const distance = Number(node.distance ?? 0);
    groups.set(distance, [...(groups.get(distance) || []), node]);
  }
  const levels = [...groups.keys()].sort((a, b) => a - b);
  const left = Math.min(...levels, 0);
  const flowNodes = levels.flatMap(level => (groups.get(level) || [])
    .sort((a, b) => a.kind.localeCompare(b.kind) || a.name.localeCompare(b.name, 'zh-CN'))
    .map((node, index): FlowNode => ({
      id: node.key,
      type: 'default',
      sourcePosition: Position.Right,
      targetPosition: Position.Left,
      className: `wx-lineage-graph-node${level === 0 ? ' is-root' : ''}`,
      position: { x: (level - left) * 286 + 30, y: index * 112 + 42 },
      data: { label: <div className="wx-lineage-node-copy">
        <span className="wx-lineage-node-kind">{objectIcon(node.kind)} {kindLabels[node.kind]}</span>
        <strong title={node.name}>{node.name || node.code}</strong>
        <small title={node.code}>{node.code || node.datasourceName || '—'}</small>
      </div> },
    })));
  const visible = new Set(flowNodes.map(node => node.id));
  const flowEdges = analysis.edges.filter(edge => visible.has(edge.source) && visible.has(edge.target))
    .map((edge): FlowEdge => ({
      id: edge.id, source: edge.source, target: edge.target, type: 'smoothstep',
      style: { stroke: scope === 'DEPLOYED' ? '#48a9df' : '#9977e7', strokeWidth: 1.8 },
      markerEnd: { type: MarkerType.ArrowClosed, color: scope === 'DEPLOYED' ? '#48a9df' : '#9977e7' },
    }));
  return { nodes: flowNodes, edges: flowEdges };
}

export default function LineagePage() {
  const [tab, setTab] = useState('analysis');
  const [focusPipelineId, setFocusPipelineId] = useState('');
  return <Page title="数据血缘" description="跨任务查看全链、归因与影响，并追溯每条关系的依据。">
    <Tabs className="wx-lineage-tabs" activeKey={tab} onChange={setTab} items={[
      { key: 'analysis', label: '血缘分析', children: <LineageExplorer onOpenProcessing={pipelineId => { setFocusPipelineId(pipelineId); setTab('processing'); }} /> },
      { key: 'processing', label: '加工链路', children: tab === 'processing' ? <SharedLineagePage embedded initialPipelineId={focusPipelineId} /> : null },
    ]} />
  </Page>;
}

function LineageExplorer({onOpenProcessing}: {onOpenProcessing: (pipelineId: string) => void}) {
  const [params, setParams] = useSearchParams();
  const initialKind = params.get('kind');
  const [selected, setSelected] = useState<{kind: LineageKind; id: string} | null>(
    initialKind && ['TABLE', 'FIELD', 'MODEL'].includes(initialKind) && params.get('id')
      ? {kind: initialKind as LineageKind, id: params.get('id')!} : null);
  const [searchInput, setSearchInput] = useState('');
  const [keyword, setKeyword] = useState('');
  const [kind, setKind] = useState('');
  const [datasourceId, setDatasourceId] = useState('');
  const [linkedOnly, setLinkedOnly] = useState(true);
  const [pageNo, setPageNo] = useState(1);
  const [list, setList] = useState<{items: LineageObject[]; total: number}>({items: [], total: 0});
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState('');
  const [coverage, setCoverage] = useState<LineageCoverage | null>(null);
  const [coverageError, setCoverageError] = useState('');
  const [mode, setMode] = useState<LineageMode>('FULL');
  const [scope, setScope] = useState<LineageScope>('DESIGN');
  const [depth, setDepth] = useState(3);
  const [analysis, setAnalysis] = useState<LineageAnalysis | null>(null);
  const [analysisLoading, setAnalysisLoading] = useState(false);
  const [analysisError, setAnalysisError] = useState('');
  const [detail, setDetail] = useState<LineageObject | null>(null);
  const [evidence, setEvidence] = useState<(LineageRelation & {sourceObject: LineageObject; targetObject: LineageObject; explanation: string}) | null>(null);
  const [evidenceLoading, setEvidenceLoading] = useState(false);
  const [reload, setReload] = useState(0);
  const {message} = App.useApp();
  const theme = useStudio(state => state.theme);

  useEffect(() => {
    const controller = new AbortController();
    getLineageCoverage(controller.signal).then(setCoverage).catch(error => {
      if (!controller.signal.aborted) setCoverageError(errorText(error));
    });
    return () => controller.abort();
  }, [reload]);

  useEffect(() => {
    const controller = new AbortController();
    setListLoading(true); setListError('');
    searchLineageObjects({keyword, kind, datasourceId, linkedOnly, pageNo, pageSize: 20}, controller.signal)
      .then(result => { if (!controller.signal.aborted) setList(result); })
      .catch(error => { if (!controller.signal.aborted) setListError(errorText(error)); })
      .finally(() => { if (!controller.signal.aborted) setListLoading(false); });
    return () => controller.abort();
  }, [keyword, kind, datasourceId, linkedOnly, pageNo, reload]);

  useEffect(() => {
    if (!selected) { setAnalysis(null); return; }
    const controller = new AbortController();
    setAnalysisLoading(true); setAnalysisError(''); setAnalysis(null);
    analyzeLineage({...selected, mode, scope, depth}, controller.signal)
      .then(result => { if (!controller.signal.aborted) setAnalysis(result); })
      .catch(error => { if (!controller.signal.aborted) setAnalysisError(errorText(error)); })
      .finally(() => { if (!controller.signal.aborted) setAnalysisLoading(false); });
    return () => controller.abort();
  }, [selected?.kind, selected?.id, mode, scope, depth, reload]);

  const graph = useMemo(() => analysis ? graphLayout(analysis, scope) : {nodes: [], edges: []}, [analysis, scope]);
  const focusKey = selected?.kind === 'MODEL' ? `MODEL:${selected.id}` : analysis?.nodes.find(node => node.distance === 0)?.key;
  const related = (analysis?.nodes || []).filter(node => node.key !== focusKey)
    .sort((a, b) => Math.abs(Number(a.distance || 0)) - Math.abs(Number(b.distance || 0)) || a.name.localeCompare(b.name, 'zh-CN'));

  const choose = (item: LineageObject) => {
    setSelected({kind: item.kind, id: item.id}); setDetail(null); setEvidence(null);
    setDepth(3); setParams({kind: item.kind, id: item.id}, {replace: true});
  };
  const openEvidence = async (relation: LineageRelation) => {
    setEvidenceLoading(true);
    try { setEvidence(await getLineageEvidence(relation.id, scope)); }
    catch (error) { message.error(errorText(error)); }
    finally { setEvidenceLoading(false); }
  };

  return <div className="wx-lineage-explorer">
    <div className="wx-lineage-summary">
      <div><span>已登记数据表</span><strong>{coverage?.tables ?? '—'}</strong></div>
      <div><span>已采集字段</span><strong>{coverage?.fields ?? '—'}</strong></div>
      <div><span>设计依赖</span><strong>{coverage ? coverage.designTableEdges + coverage.designFieldEdges : '—'}</strong></div>
      <div><span>部署版本依赖</span><strong>{coverage?.deployedEdges ?? '—'}</strong><small>{coverage ? `${coverage.indexedPipelines} / ${coverage.deployedPipelines} 个部署已有可验证快照` : ''}</small></div>
      <Button icon={<ReloadOutlined />} onClick={() => { setCoverageError(''); setReload(value => value + 1); }}>刷新</Button>
    </div>
    {coverageError && <Alert type="error" showIcon title={coverageError} />}
    <div className="wx-lineage-body">
      <aside className="wx-lineage-picker">
        <div className="wx-lineage-section-heading"><strong>选择分析对象</strong><span>{list.total} 项</span></div>
        <Input.Search placeholder="搜索表、字段或模型名称" value={searchInput} onChange={event => setSearchInput(event.target.value)} onSearch={value => { setPageNo(1); setKeyword(value.trim()); }} allowClear />
        <div className="wx-lineage-picker-filters">
          <Select aria-label="对象类型" value={kind} onChange={value => { setKind(value); setPageNo(1); }} options={[
            {value: '', label: '全部类型'}, {value: 'TABLE', label: '数据表'},
            {value: 'FIELD', label: '字段'}, {value: 'MODEL', label: '模型'},
          ]} />
          <Select aria-label="数据源" value={datasourceId} onChange={value => { setDatasourceId(value); setPageNo(1); }} showSearch optionFilterProp="label" options={[
            {value: '', label: '全部数据源'}, ...(coverage?.datasources || []).map(source => ({value: source.id, label: source.name || source.id})),
          ]} />
        </div>
        <Checkbox checked={linkedOnly} onChange={event => {setLinkedOnly(event.target.checked); setPageNo(1);}}>仅显示已有关系的对象</Checkbox>
        {listError && <Alert type="error" showIcon title={listError} />}
        <Spin spinning={listLoading}><div className="wx-lineage-object-list">
          {list.items.length ? list.items.map(item => <button type="button" key={item.key} className={`wx-lineage-object${selected?.kind === item.kind && selected.id === item.id ? ' is-selected' : ''}`} onClick={() => choose(item)}>
            <span className="wx-lineage-object-icon">{objectIcon(item.kind)}</span>
            <span className="wx-lineage-object-copy"><strong title={item.name}>{item.name || item.code}</strong><small title={item.code}>{item.code || item.tableName || '—'}</small><small title={item.datasourceName}>{kindLabels[item.kind]} · {item.datasourceName || item.tableName || '未指定数据源'}</small></span>
          </button>) : !listLoading && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={linkedOnly ? '没有匹配的关系对象，可取消“仅显示已有关系的对象”查看全部' : '没有匹配的对象'} />}
        </div></Spin>
        {list.total > 20 && <Pagination className="wx-lineage-pagination" simple current={pageNo} pageSize={20} total={list.total} onChange={setPageNo} />}
      </aside>
      <section className="wx-lineage-results">
        <div className="wx-lineage-controls">
          <Segmented<LineageMode> aria-label="分析方式" value={mode} onChange={setMode} options={[
            {value: 'FULL', label: '全链分析'}, {value: 'ATTRIBUTION', label: '归因分析'}, {value: 'IMPACT', label: '影响分析'},
          ]} />
          <div className="wx-lineage-control-right">
            <Select aria-label="关系依据" value={scope} onChange={setScope} options={[{value: 'DESIGN', label: '已保存设计'}, {value: 'DEPLOYED', label: '已部署版本'}]} />
            <Select aria-label="查询层数" value={depth} onChange={setDepth} options={[1,2,3,4,5,6,7,8].map(value => ({value, label: `${value} 层`}))} />
          </div>
        </div>
        <div className="wx-lineage-results-caption">
          <span>{modeDescriptions[mode]}</span>
          <span>{scope === 'DESIGN' ? '关系依据：已保存任务和字段规则' : '关系依据：成功部署时归档的版本'}</span>
        </div>
        {!selected ? <div className="wx-lineage-placeholder"><ApartmentOutlined /><h2>从左侧选择一个对象</h2><p>可以按数据表、字段或模型开始追溯；只展示具有明确对象标识的关系。</p></div>
          : analysisLoading ? <div className="wx-lineage-placeholder"><Spin size="large" tip="正在分析链路"><div className="wx-lineage-spin-space" /></Spin></div>
          : analysisError ? <Alert type="error" showIcon title="血缘分析失败" description={analysisError} action={<Button onClick={() => setReload(value => value + 1)}>重试</Button>} />
          : analysis && <>
            <div className="wx-lineage-focus">
              <div className="wx-lineage-focus-icon">{objectIcon(analysis.root.kind)}</div>
              <div><span>当前分析对象 · {kindLabels[analysis.root.kind]}</span><strong>{analysis.root.name || analysis.root.code}</strong><small>{analysis.root.code} · {analysis.root.datasourceName || '未指定数据源'}</small></div>
              <Tag color={scope === 'DEPLOYED' ? 'blue' : 'purple'}>{scope === 'DEPLOYED' ? '已部署版本' : '设计依赖'}</Tag>
            </div>
            {analysis.note && <Alert type="info" showIcon title={analysis.note} />}
            <div className="wx-lineage-graph-header"><strong>关系图</strong><span>{analysis.nodes.length} 个对象 · {analysis.edges.length} 条关系</span></div>
            <div className="wx-lineage-canvas" aria-label="数据血缘关系图">
              <ReactFlow key={`${selected.kind}:${selected.id}:${mode}:${scope}:${depth}`} colorMode={theme} nodes={graph.nodes} edges={graph.edges} fitView fitViewOptions={{padding: 0.18, maxZoom: 1.2}} minZoom={0.12} maxZoom={1.8} nodesDraggable={false} nodesConnectable={false} elementsSelectable onNodeClick={(_, node) => setDetail(analysis.nodes.find(item => item.key === node.id) || null)} onEdgeClick={(_, edge) => { const relation = analysis.edges.find(item => item.id === edge.id); if (relation) void openEvidence(relation); }}>
                <Background gap={22} size={1} /><Controls showInteractive={false} />
              </ReactFlow>
            </div>
            {analysis.hasMore && <Alert className="wx-lineage-more" type="warning" showIcon title={depth < 8 ? '还有更远的关系未展开' : '已达到单次查询范围上限'} action={depth < 8 ? <Button onClick={() => setDepth(value => value + 1)}>再展开一层</Button> : undefined} />}
            <div className="wx-lineage-related-heading"><strong>{mode === 'ATTRIBUTION' ? '上游来源' : mode === 'IMPACT' ? '下游影响' : '关联对象'}</strong><span>{related.length} 项</span></div>
            {related.length ? <div className="wx-lineage-related-list">{related.map(item => <button type="button" key={item.key} onClick={() => setDetail(item)}>
              <span className="wx-lineage-related-kind">{objectIcon(item.kind)} {kindLabels[item.kind]}</span>
              <span className="wx-lineage-related-name" title={item.name}>{item.name || item.code}<small>{item.code}</small></span>
              <span>{item.distance && item.distance < 0 ? `上游 ${Math.abs(item.distance)} 层` : `下游 ${item.distance || 0} 层`}</span>
            </button>)}</div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={scope === 'DEPLOYED' ? '当前对象没有已归档的部署依赖' : '当前对象没有已登记的明确加工依赖'} />}
            {analysis.edges.length > 0 && <div className="wx-lineage-evidence-list"><strong>关系依据</strong>{analysis.edges.map(relation => <button type="button" key={relation.id} onClick={() => void openEvidence(relation)} disabled={evidenceLoading}>
              <span>{evidenceLabels[relation.evidenceKind] || relation.evidenceKind}</span><small>{relation.ruleSummary}</small><span>查看依据 ›</span>
            </button>)}</div>}
          </>}
      </section>
    </div>
    <Drawer title="对象详情" width="min(520px, 100vw)" open={!!detail} onClose={() => setDetail(null)}>{detail && <>
      <div className="wx-lineage-drawer-title">{objectIcon(detail.kind)} <strong>{detail.name || detail.code}</strong></div>
      <Descriptions bordered size="small" column={1} items={[
        {key:'name',label:'中文名',children:detail.name || '—'},
        {key:'code',label:'英文名 / 技术标识',children:detail.code || '—'},
        {key:'type',label:'对象类型',children:detail.objectType ? `${kindLabels[detail.kind]} · ${detail.objectType}` : kindLabels[detail.kind]},
        {key:'domain',label:'所属板块 / 数据域',children:detail.domainName || detail.domainId || '—'},
        {key:'source',label:'数据源',children:detail.datasourceName || '—'},
        {key:'sourceType',label:'数据源类型',children:detail.datasourceType || '—'},
        ...(detail.kind === 'FIELD' ? [
          {key:'table',label:'所属数据表',children:detail.tableName || '—'},
          {key:'dataType',label:'字段类型',children:detail.dataType || '—'},
          {key:'length',label:'长度',children:detail.length ?? '—'},
        ] : []),
        {key:'updated',label:'元数据更新时间',children:date(detail.updatedAt)},
      ]} />
      <Space className="wx-lineage-drawer-actions"><Button icon={<DatabaseOutlined />} onClick={() => { choose(detail); setDetail(null); }}>以此对象分析</Button></Space>
    </>}</Drawer>
    <Drawer title="关系依据" width="min(580px, 100vw)" open={!!evidence} onClose={() => setEvidence(null)}>{evidence && <>
      <Tag color={scope === 'DEPLOYED' ? 'blue' : 'purple'}>{scope === 'DEPLOYED' ? '已部署版本' : '设计依赖'}</Tag>
      <div className="wx-lineage-evidence-route"><div><strong>{evidence.sourceObject?.name || evidence.source}</strong><small>{evidence.sourceObject?.code || ''}</small></div><span>→</span><div><strong>{evidence.targetObject?.name || evidence.target}</strong><small>{evidence.targetObject?.code || ''}</small></div></div>
      <Descriptions bordered size="small" column={1} items={[
        {key:'kind',label:'依据类型',children:evidenceLabels[evidence.evidenceKind] || evidence.evidenceKind},
        {key:'rule',label:'规则说明',children:evidence.ruleSummary || '—'},
        {key:'pipeline',label:'加工任务',children:(evidence as typeof evidence & {pipelineName?:string}).pipelineName || evidence.pipelineId || '—'},
        {key:'pipelineId',label:'加工任务 ID',children:evidence.pipelineId || '—'},
        {key:'ref',label:'规则 / 节点 ID',children:evidence.evidenceRefId || '—'},
      ]} />
      <p className="wx-lineage-evidence-explanation">{evidence.explanation}</p>
      {evidence.pipelineId && <Tooltip title="在加工链路中查看并编辑当前租户的共享任务"><Button onClick={() => { setEvidence(null); onOpenProcessing(evidence.pipelineId); }}>查看加工链路</Button></Tooltip>}
    </>}</Drawer>
  </div>;
}
