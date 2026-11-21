import { Graph } from '@antv/x6';
import { Alert, Button, Descriptions, Image, Select, Space, Table, Tabs } from 'antd';
import CodeMirror from '@uiw/react-codemirror';
import { json } from '@codemirror/lang-json';
import { useOceanTheme } from '../../design/theme';
import { useEffect, useMemo, useRef, useState } from 'react';
import { buildTemplateFlow, type TemplateSpec } from './accessTemplateDefinition';

type PreviewNode = { id: string; label: string; type: string; x: number; y: number; config: unknown };
type PreviewEdge = { id: string; source: string; target: string; label?: string; outlet?: string };
function FlowDiagram({ nodes, edges, onSelect }: {
  nodes: PreviewNode[]; edges: PreviewEdge[]; onSelect: (id: string) => void;
}) {
  const host = useRef<HTMLDivElement>(null);
  const diagram = useRef<Graph>();
  const selected = useRef(onSelect);
  selected.current = onSelect;
  useEffect(() => {
    if (!host.current) return;
    const graph = new Graph({ container: host.current, width: host.current.clientWidth, height: 380,
      interacting: false, panning: true, mousewheel: { enabled: true, modifiers: ['ctrl', 'meta'] },
      background: { color: '#f5f8fc' } });
    diagram.current = graph;
    const ids = new Set(nodes.map(node => node.id));
    for (const node of nodes) {
      graph.addNode({ id: node.id, x: node.x, y: node.y, width: 220, height: 62,
        label: node.label + '\n' + (node.type.split('.').at(-1) || ''),
        attrs: { body: { fill: '#fff', stroke: '#91b7e4', rx: 8, ry: 8 },
          label: { fill: '#1b3557', fontSize: 12, textWrap: { width: 205, height: 54, ellipsis: true } } } });
    }
    for (const edge of edges) {
      if (!ids.has(edge.source) || !ids.has(edge.target)) continue;
      graph.addEdge({ source: edge.source, target: edge.target,
        labels: edge.label ? [{ attrs: { label: { text: edge.label, fontSize: 10 } } }] : [],
        attrs: { line: { stroke: '#7897bf', strokeWidth: 1.5, targetMarker: 'block' } },
        connector: { name: 'rounded' } });
    }
    graph.on('node:click', ({ node }) => selected.current(node.id));
    graph.zoomToFit({ padding: 30, maxScale: 1 });
    let previousWidth = host.current.clientWidth;
    const observer = new ResizeObserver(([entry]) => {
      const width = Math.round(entry.contentRect.width);
      if (width <= 0 || width === previousWidth) return;
      previousWidth = width;
      graph.resize(width, 380);
      graph.zoomToFit({ padding: 30, maxScale: 1 });
    });
    observer.observe(host.current);
    return () => { observer.disconnect(); diagram.current = undefined; graph.dispose(); };
  }, [nodes, edges]);
  return <>
    <Space style={{ marginBottom: 8 }} wrap>
      <Button size="small" onClick={() => diagram.current?.zoom(0.15)}>放大</Button>
      <Button size="small" onClick={() => diagram.current?.zoom(-0.15)}>缩小</Button>
      <Button size="small" onClick={() => diagram.current?.zoomToFit({ padding: 30, maxScale: 1 })}>适应画布</Button>
      <span>拖动画布，点击组件查看配置</span>
    </Space>
    <div ref={host} aria-label="模板流程图" style={{ height: 380, overflow: 'hidden', border: '1px solid #dce5ef', borderRadius: 8 }}/>
  </>;
}

export default function AccessTemplatePreview({ spec }: { spec: TemplateSpec }) {
  const { dark } = useOceanTheme();
  const [tab, setTab] = useState('flow');
  const [definitionKind, setDefinitionKind] = useState('execution');
  const [selection, setSelection] = useState('');
  const executable = useMemo(() => {
    const flow = spec.flow || (spec.engine === 'GENERATED' ? buildTemplateFlow(spec) : undefined);
    return {
    nodes: (flow?.nodes || []).map(node => ({
      id: node.id, label: node.label,
      type: node.config.templateBinding === 'source' && ['ANY','JDBC'].includes(spec.sourceType)
        ? (spec.sourceType === 'ANY' ? '来源自动适配' : '数据库来源适配') : node.manifestKey,
      x: node.x, y: node.y, config: node.config,
    })),
    edges: flow?.edges || [],
  };
  }, [spec]);
  const data = executable;
  const current = data.nodes.find(node => node.id === selection) || data.nodes[0];
  return <>
    <Descriptions size="small" column={3} items={[
      { key: 'schedule', label: '来源调度', children: spec.sourceSchedule === 'REGISTERED' ? '沿用来源登记' : spec.intervalSeconds + ' 秒' },
      { key: 'batch', label: '每批读取', children: spec.fetchSize + ' 行' },
      { key: 'flow', label: '目标分支', children: spec.targets.length },
    ]}/>
    <Tabs activeKey={tab} onChange={value => { setTab(value); setSelection(''); }} items={[
      { key: 'flow', label: '流程图' },
      { key: 'definition', label: '流程定义' },
    ]}/>
    {tab === 'definition' ? <>
      {spec.nativeSnapshot && <Select value={definitionKind} onChange={setDefinitionKind} style={{ width: 260, marginBottom: 12 }}
        options={[{value: 'execution', label: '当前模板配置'}, {value: 'attachment', label: '导入的 NiFi 文件（参考）'}]}/>}
      <CodeMirror value={JSON.stringify(definitionKind === 'attachment' ? spec.nativeSnapshot : spec, null, 2)}
        extensions={[json()]} theme={dark ? 'dark' : 'light'} height="500px" editable={false}
        basicSetup={{lineNumbers: true, foldGutter: true, highlightActiveLine: false}}/>
    </> : <>
      <p>{spec.previewImage ? '此图为模板维护人员上传的已配置流程截图。'
        : spec.engine === 'GENERATED' ? '按模板的目标顺序自动生成接入链路概览，任务组件随登记来源实例化。'
          : '流程图按当前模板的组件与连线自动生成。'}创建任务时，来源与目标会绑定到登记的数据源。</p>
      {spec.previewImage ? <Image src={spec.previewImage} alt="已配置流程的截图" style={{maxHeight: 400, objectFit: 'contain'}}/> :
      !data.nodes.length ? <Alert type="warning" showIcon message="此版本尚未配置完整接入流程"/> :
        <FlowDiagram nodes={data.nodes} edges={data.edges} onSelect={setSelection}/>}
      <Space style={{ margin: '16px 0 8px', width: '100%' }}>
        <span>组件配置</span>
        <Select value={current?.id} style={{ minWidth: 300 }} onChange={setSelection}
          options={data.nodes.map(node => ({ value: node.id, label: node.label + ' · ' + node.type }))}/>
      </Space>
      <CodeMirror value={JSON.stringify(current?.config || {}, null, 2)} extensions={[json()]}
        theme={dark ? 'dark' : 'light'} height="240px" editable={false}
        basicSetup={{lineNumbers: true, foldGutter: true, highlightActiveLine: false}}/>
      <Table rowKey={edge => edge.id} size="small" pagination={{ pageSize: 10 }} dataSource={data.edges}
        columns={[
          { title: '来源组件', render: (_, edge) => data.nodes.find(node => node.id === edge.source)?.label || edge.source },
          { title: '目标组件', render: (_, edge) => data.nodes.find(node => node.id === edge.target)?.label || edge.target },
          { title: '分支关系', render: (_, edge) => 'label' in edge ? String(edge.label || '默认') : edge.outlet || '默认' },
        ]}/>
    </>}
  </>;
}
