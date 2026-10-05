import { useEffect, useMemo, useRef, useState } from 'react';
import { Button, Empty, Input, Pagination, Select, Skeleton, Space, Tag, Tooltip } from 'antd';
import { ArrowRightOutlined, ExpandOutlined, ReloadOutlined, SearchOutlined } from '@ant-design/icons';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { assetsApi, type GraphResult } from '../../../services/assets';
import { AssetError, AssetName, AssetPage, AssetPanel, useAssetQuery } from '../shared';
import { assetDetailPath, assetIcon, assetKind, assetKindLabels, assertAssetPage, formatAssetTime, type AssetKind, type AssetPageResult, type AssetRef } from '../registration/assetObjects';
import './map.css';

const kinds = (Object.keys(assetKindLabels) as AssetKind[]).filter(kind => kind !== 'FILE');
const edgeLabels: Record<string, string> = { OWNED_BY: '归属', CONTAINS: '包含', ATTACHED_TO: '资源挂接', STANDARD_BOUND: '关联标准', LINEAGE: '加工血缘' };
const nodeKey = (node: { kind: string; id: string }) => `${node.kind}:${node.id}`;
function validateGraph(value: GraphResult): GraphResult {
  if (!value || !Array.isArray(value.nodes) || !Array.isArray(value.edges) || value.nodes.some(node => !node.id || !node.kind || !(node.kind in assetKindLabels)) || value.edges.some(edge => !edge.source?.id || !edge.target?.id || !edge.kind)) throw new Error('资产关联信息未完整返回，请刷新重试');
  return value;
}

function RelationshipGraph({ graph, selected, onSelect }: { graph: GraphResult; selected: AssetRef; onSelect: (object: AssetRef) => void }) {
  const layout = useMemo(() => {
    const nodes = graph.nodes;
    const centerKey = nodeKey(selected);
    const incomingIds = new Set(graph.edges.filter(edge => nodeKey(edge.target) === centerKey).map(edge => nodeKey(edge.source)));
    const left = nodes.filter(node => nodeKey(node) !== centerKey && incomingIds.has(nodeKey(node)));
    const right = nodes.filter(node => nodeKey(node) !== centerKey && !incomingIds.has(nodeKey(node)));
    const height = Math.max(390, Math.max(left.length, right.length) * 104 + 52);
    const positions = new Map<string, { x: number; y: number }>([[centerKey, { x: 340, y: Math.min(height / 2 - 38, 214) }]]);
    left.forEach((node, index) => positions.set(nodeKey(node), { x: 24, y: 35 + index * 104 }));
    right.forEach((node, index) => positions.set(nodeKey(node), { x: 656, y: 35 + index * 104 }));
    return { height, positions, nodes: nodes.some(node => nodeKey(node) === centerKey) ? nodes : [selected, ...nodes] };
  }, [graph, selected]);
  return <div className="hy-map-canvas" tabIndex={0} aria-label="资产关系图，可滚动查看全部节点"><svg width="916" height={layout.height} role="group" aria-label="资产之间的真实关联关系">
    <defs><pattern id="hy-map-dots" width="20" height="20" patternUnits="userSpaceOnUse"><circle cx="1" cy="1" r="1" fill="currentColor" opacity=".14"/></pattern><marker id="hy-map-arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="6" markerHeight="6" orient="auto-start-reverse"><path d="M 0 0 L 10 5 L 0 10 z" fill="#9e91b8"/></marker></defs>
    <rect width="916" height={layout.height} fill="url(#hy-map-dots)"/>
    {graph.edges.map(edge => {
      const from = layout.positions.get(nodeKey(edge.source)), to = layout.positions.get(nodeKey(edge.target)); if (!from || !to) return null;
      const towardsRight = from.x < to.x; const start = from.x + (towardsRight ? 234 : 0), end = to.x + (towardsRight ? 0 : 234);
      const sy = from.y + 37, ty = to.y + 37, middle = (start + end) / 2;
      return <g key={edge.id}><path d={`M${start},${sy} C${middle},${sy} ${middle},${ty} ${end},${ty}`} stroke={edge.kind === 'LINEAGE' ? '#63bea9' : '#9e91b8'} strokeWidth="1.5" fill="none" markerEnd="url(#hy-map-arrow)"/><text x={middle} y={(sy + ty) / 2 - 10} textAnchor="middle" className="hy-graph-edge-label">{edge.label || edgeLabels[edge.kind]}</text><title>{`${edge.source.name} → ${edge.target.name} · ${edgeLabels[edge.kind]}`}</title></g>;
    })}
    {layout.nodes.map(node => { const position = layout.positions.get(nodeKey(node)); if (!position) return null; const current = nodeKey(node) === nodeKey(selected); return <foreignObject key={nodeKey(node)} x={position.x} y={position.y} width="234" height="80"><button type="button" className={`hy-graph-node${current ? ' is-current' : ''}`} onClick={() => onSelect(node)} title={node.name}><span className="hy-graph-node-icon">{assetIcon(node.kind)}</span><span><small>{assetKindLabels[node.kind]}</small><strong>{node.name}</strong><code>{node.code || node.id}</code></span></button></foreignObject>; })}
  </svg></div>;
}

export function AssetMapPage() {
  const navigate = useNavigate(); const [params, setParams] = useSearchParams();
  const [search, setSearch] = useState(''), [keyword, setKeyword] = useState(''), [filter, setFilter] = useState<string>('');
  const [sort, setSort] = useState('updatedAt:desc'), [pageNo, setPageNo] = useState(1);
  const [selected, setSelected] = useState<AssetRef | null>(() => { const kind = assetKind(params.get('kind') || undefined), id = params.get('id'); return kind && id ? { kind, id, name: '' } : null; });
  const [additional, setAdditional] = useState<GraphResult | null>(null), [expanding, setExpanding] = useState(false), [expandError, setExpandError] = useState('');
  const selectedRef = useRef(selected ? nodeKey(selected) : ''); selectedRef.current = selected ? nodeKey(selected) : '';
  const [sortBy, sortOrder] = sort.split(':');
  const query = useAssetQuery(`asset-map-list:${keyword}:${filter}:${sort}:${pageNo}`, signal => assetsApi.get<AssetPageResult>('/search', { view: 'MAP', keyword, kinds: filter, sortBy, sortOrder, pageNo, pageSize: 10 }, { signal }).then(assertAssetPage));
  const graphQuery = useAssetQuery(`asset-graph:${selected?.kind || ''}:${selected?.id || ''}`, async signal => selected ? assetsApi.get<GraphResult>('/map', { kind: selected.kind, id: selected.id }, { signal }).then(validateGraph) : null);
  const pick = (object: AssetRef) => { setSelected(object); setAdditional(null); setExpandError(''); const next = new URLSearchParams(params); next.set('kind', object.kind); next.set('id', object.id); setParams(next, { replace: true }); };
  useEffect(() => { if (!selected && query.data?.items.length) pick(query.data.items[0].object); }, [query.data, selected]);
  const graph = useMemo<GraphResult | null>(() => {
    if (!graphQuery.data) return null;
    if (!additional) return graphQuery.data;
    return { ...additional, nodes: [...new Map([...graphQuery.data.nodes, ...additional.nodes].map(node => [nodeKey(node), node])).values()], edges: [...new Map([...graphQuery.data.edges, ...additional.edges].map(edge => [edge.id, edge])).values()] };
  }, [graphQuery.data, additional]);
  const center = graph?.nodes.find(node => selected && nodeKey(node) === nodeKey(selected)) || selected;
  const expand = async () => {
    if (!graph?.nextCursor || !selected || expanding) return; setExpanding(true); setExpandError('');
    const selection = nodeKey(selected);
    try { const next = validateGraph(await assetsApi.get<GraphResult>('/map', { kind: selected.kind, id: selected.id, cursor: graph.nextCursor })); if (selectedRef.current === selection) setAdditional(previous => previous ? { ...next, nodes: [...previous.nodes, ...next.nodes], edges: [...previous.edges, ...next.edges] } : next); }
    catch (cause) { if (selectedRef.current === selection) setExpandError(cause instanceof Error ? cause.message : String(cause)); }
    finally { setExpanding(false); }
  };
  const doSearch = () => { setPageNo(1); setKeyword(search.trim()); };
  return <AssetPage title="数据资产地图" description="沿系统、业务库、数据表与资源目录，发现数据的来源和关联。" actions={<Button icon={<ReloadOutlined/>} onClick={() => { query.refresh(); graphQuery.refresh(); }}>刷新地图</Button>}>
    <div className="hy-map-search"><Input size="large" aria-label="检索数据资产" prefix={<SearchOutlined/>} placeholder="搜索资产名称、英文标识或目录编码" value={search} onChange={event => { setSearch(event.target.value); if (!event.target.value) { setKeyword(''); setPageNo(1); } }} onPressEnter={doSearch} allowClear/><Button size="large" type="primary" onClick={doSearch}>搜索资产</Button><Select size="large" aria-label="资产类型" value={filter} onChange={value => { setFilter(value); setPageNo(1); }} options={[{ value: '', label: '全部资产类型' }, ...kinds.map(value => ({ value, label: assetKindLabels[value] }))]}/><Select size="large" aria-label="资产排序" value={sort} onChange={value => { setSort(value); setPageNo(1); }} options={[{ value: 'updatedAt:desc', label: '最近更新' }, { value: 'updatedAt:asc', label: '最早更新' }, { value: 'name:asc', label: '名称升序' }, { value: 'name:desc', label: '名称降序' }]}/></div>
    <div className="hy-map-layout">
      <AssetPanel className="hy-map-results" title="检索结果" extra={<span>{query.data?.page.total ?? '—'} 项资产</span>}>
        <AssetError error={query.error} retry={query.refresh}/>{query.loading ? <Skeleton active paragraph={{ rows: 12 }}/> : query.data?.items.length ? <div className="hy-map-result-list">{query.data.items.map(item => <button key={nodeKey(item.object)} type="button" className={selected && nodeKey(selected) === nodeKey(item.object) ? 'is-active' : ''} onClick={() => pick(item.object)}><div className="hy-map-result-kind"><span>{assetIcon(item.object.kind)} {assetKindLabels[item.object.kind]}</span><ArrowRightOutlined/></div><strong title={item.object.name}>{item.object.name}</strong><code title={item.object.code}>{item.object.code || '—'}</code><small title={item.departmentName}>{item.departmentName || '未设置提供部门'}</small><time>{formatAssetTime(item.updatedAt)}</time></button>)}</div> : <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={query.error ? '暂时无法读取资产' : '没有匹配的资产'}/>}
        <Pagination size="small" simple current={pageNo} pageSize={10} total={query.data?.page.total ?? 0} onChange={setPageNo}/>
      </AssetPanel>
      <AssetPanel className="hy-map-graph-panel" title="资产关联" extra={center && <Button type="link" icon={<ExpandOutlined/>} onClick={() => navigate(assetDetailPath(center))}>查看详情</Button>}>
        <AssetError error={graphQuery.error || expandError} retry={graphQuery.refresh}/>
        {graphQuery.loading ? <Skeleton active paragraph={{ rows: 14 }}/> : graph && center ? <><RelationshipGraph graph={graph} selected={center} onSelect={pick}/><div className="hy-map-legend"><span><i/>业务归属与资源挂接</span><span className="is-lineage"><i/>加工血缘</span><small>{graph.nodes.length} 个资产 · {graph.edges.length} 条关系</small></div>{graph.hasMore && <div className="hy-map-more"><Button loading={expanding} onClick={() => void expand()}>加载更多关联资产</Button></div>}<div className="hy-map-selected"><AssetName name={center.name} code={center.code} icon={assetIcon(center.kind)} onClick={() => navigate(assetDetailPath(center))}/><Space>{center.kind === 'TABLE' && <Tooltip title="在万象查看技术字段与治理信息"><Button onClick={() => navigate(`/governance/metadata/catalog?tableId=${encodeURIComponent(center.id)}&returnTo=${encodeURIComponent('/assets/map')}`)}>查看元数据</Button></Tooltip>}{center.kind === 'DATASOURCE' && <Button onClick={() => navigate(`/governance/metadata/sources?sourceId=${encodeURIComponent(center.id)}&returnTo=${encodeURIComponent('/assets/map')}`)}>进入数据治理</Button>}<Button type="primary" onClick={() => navigate(assetDetailPath(center))}>资产详情 <ArrowRightOutlined/></Button></Space></div>{graph.edges.length === 0 && <div className="hy-map-no-edges"><Tag>暂无已登记关联关系</Tag></div>}</> : !graphQuery.error && <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="选择左侧资产查看关联关系"/>}
      </AssetPanel>
    </div>
  </AssetPage>;
}
