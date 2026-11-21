import {describe,it,expect} from 'vitest';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import ModelOperatorIcon,{MODEL_OPERATOR_ICONS} from '../src/ModelOperatorIcon';
import {MODEL_OPERATOR_CATALOG,nodeLabels,type NodeKind} from '../src/modeling';
import {paneToggleDirection} from '../src/PaneToggleIcon';
import workspace from '../src/ModelingWorkspace.tsx?raw';
const fileSystemModule='node:fs';
const {readFileSync}=await import(/* @vite-ignore */ fileSystemModule) as {
 readFileSync:(path:URL,encoding:'utf8')=>string;
};
const styles=readFileSync(new URL('../src/modeling.css',import.meta.url),'utf8');

const kinds=Object.keys(nodeLabels) as NodeKind[];
function geometry(kind:NodeKind){
 const html=renderToStaticMarkup(createElement(ModelOperatorIcon,{kind}));
 const svg=html.match(/<svg\b[\s\S]*?<\/svg>/)![0];
 return (svg.match(/<(?:path|circle|rect|line|polyline|polygon|ellipse)\b[^>]*>/g)||[])
  .map(tag=>tag.replace(/\s(?:fill|stroke|fill-rule|clip-rule|stroke-width|stroke-linecap|stroke-linejoin|opacity)="[^"]*"/g,'')).join('');
}

describe('complete semantic operator icon set',()=>{
 it('covers every evaluator kind and every palette entry without generic fallback',()=>{
  expect(Object.keys(MODEL_OPERATOR_ICONS).sort()).toEqual([...kinds].sort());
  expect(MODEL_OPERATOR_CATALOG.every(item=>item.kind&&MODEL_OPERATOR_ICONS[item.kind])).toBe(true);
  expect(new Set(MODEL_OPERATOR_CATALOG.map(item=>item.kind)).size).toBe(MODEL_OPERATOR_CATALOG.length);
  expect(new Set(Object.values(MODEL_OPERATOR_ICONS).map(icon=>icon.id)).size).toBe(kinds.length);
 });
 it('renders different geometry for every operator, independent of color and semantic ID',()=>{
  const seen=new Map<string,NodeKind>();
  for(const kind of kinds){
   const shape=geometry(kind);
   expect(shape.length,kind).toBeGreaterThan(15);
   expect(seen.get(shape),kind+' duplicates '+seen.get(shape)).toBeUndefined();
   seen.set(shape,kind);
   const html=renderToStaticMarkup(createElement(ModelOperatorIcon,{kind}));
   expect(html).toContain('data-model-icon="'+MODEL_OPERATOR_ICONS[kind].id+'"');
   expect(html).toContain('aria-hidden="true"');
  }
  expect(seen.size).toBe(kinds.length);
 });
 it('distinguishes each file format and direction with geometry, not recoloring',()=>{
  const files:NodeKind[]=['csvInput','jsonInput','xmlInput','excelInput','parquetInput','csvOutput','jsonOutput','xmlOutput','excelOutput','parquetOutput'];
  expect(new Set(files.map(geometry)).size).toBe(10);
  expect(MODEL_OPERATOR_ICONS.filter.id).toBe('row-filter');
  expect(MODEL_OPERATOR_ICONS.lookup.id).toBe('lookup-by-key');
  expect(MODEL_OPERATOR_ICONS.conditional.id).toBe('conditional-branch');
  expect(MODEL_OPERATOR_ICONS.output.id).toBe('result-preview');
 });
 it('reuses the same icons on cards and canvas nodes',()=>{
  expect(workspace).toContain('<ModelOperatorIcon kind={kind}/>');
  expect(workspace).toContain('<ModelOperatorIcon kind={n.kind}/>');
  expect(workspace).not.toContain('icons[n.kind]||');
 });
});

describe('matching panes and the full two-column palette',()=>{
 const palette=workspace.split('<aside id="modeling-palette"')[1].split('</aside>')[0];
 const cards=workspace.split('const paletteItems=useMemo(')[1].split(' async function run')[0];
 it('shares header height, divider, typography and directional accessible toggles',()=>{
  expect(workspace).toContain('className="modeling-palette-title modeling-panel-heading"');
  expect(workspace).toContain('className="modeling-properties-heading modeling-panel-heading"');
  expect(styles).toMatch(/\.modeling-panel-heading\{[^}]*height:47px[^}]*border-bottom:1px solid var\(--model-line\)/);
  expect(styles).toContain('.modeling-panel-heading>strong{font-size:13px;font-weight:600;line-height:20px}');
  expect(workspace.match(/<PaneToggleIcon side="right" expanded=\{propertiesOpen\}\/>/g)).toHaveLength(2);
  expect(workspace.match(/icon=\{paletteOpen\?<MenuFoldOutlined\/>:<MenuUnfoldOutlined\/>\}/g)).toHaveLength(2);
  expect(workspace).not.toContain('<PaneToggleIcon side="left" expanded={paletteOpen}/>');
  expect(paneToggleDirection('left',true)).toBe('left');
  expect(paneToggleDirection('left',false)).toBe('right');
  expect(paneToggleDirection('right',true)).toBe('right');
  expect(paneToggleDirection('right',false)).toBe('left');
  expect(workspace).toContain('aria-controls="modeling-properties"');
  expect(workspace).toContain('aria-expanded={propertiesOpen}');
  expect(workspace).toContain('propertiesOpen?"收起属性面板":"展开属性面板"');
 });
 it('uses the same two-column card grid for every category and preserves interaction wiring',()=>{
  expect(cards).toContain('className="modeling-operator-list modeling-operator-grid"');
  expect(palette).not.toContain("group==='输入连接器'");
  expect(styles).toContain('.modeling-operator-list.modeling-operator-grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr))');
  expect(styles).not.toContain('.modeling-operator-list.modeling-operator-grid{grid-template-columns:1fr}');
  expect(palette).toContain('onChange={e=>setOperatorSearch(e.target.value)}');
  expect(palette).toContain('activeKey={operatorSearch.trim()?operatorGroups:operatorGroups.filter(g=>!collapsedGroups.includes(g))}');
  expect(palette).toContain('onChange={keys=>setCollapsedGroups(operatorGroups.filter(g=>!keys.includes(g)))}');
  expect(cards).toContain('onDragStart=');
  expect(cards).toContain("e.dataTransfer.setData('application/x-fengchi-model-node',kind)");
  expect(cards).toContain('onClick={()=>{if(kind)addOperatorRef.current(kind)}}');
  expect(workspace).toContain('onClick={()=>setPropertiesOpen(v=>!v)}');
  expect(workspace).toContain('onClick={()=>setPaletteOpen(v=>!v)}');
 });
 it('shows business names and icons without capability marks, hints or muted variants',()=>{
  expect(cards).toContain('title={item.label} aria-label={item.label}');
  expect(cards).toContain('disabled={nodes.length>=MODEL_NODE_LIMIT}');
  expect(cards+palette).not.toMatch(/仅配置|待支持|未接入|当前不可执行|configuration|item\.description|modeling-unavailable/);
  expect(workspace).toContain("validateExecution(nodes,executionSettings).filter(i=>i.level==='error')");
 });
});
