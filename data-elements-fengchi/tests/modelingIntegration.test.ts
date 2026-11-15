import {describe,it,expect} from 'vitest';
import source from '../src/App.tsx?raw';
import editor from '../src/ModelingWorkspace.tsx?raw';
describe('model workspace integration',()=>{
 it('reuses the Fengchi brand inside the immersive editor',()=>{expect(editor).toContain('ProductLogo');expect(editor).toContain('风驰数据计算平台');});
 it('keeps an independent immersive editor surface',()=>{expect(source).toContain('className="modeling-overlay"');});
 it('blocks background query shortcuts while modeling is open',()=>{expect(source).toContain('if(modelingOpen){if((e.ctrlKey||e.metaKey)');});
 it('places a single model entry after views and keeps the existing workspace',()=>{
  expect(source).toContain("'视图',openViews)}{tool(<BranchesOutlined");
  expect(source.match(/'模型',/g)).toHaveLength(1);
  expect(source).toContain('<ModelingWorkspace tables={tables} shared={Boolean(workspace)} onClose={()=>setModelingOpen(false)}/>');
  expect(source).toContain("display:modelingOpen?'flex':'none'");
 });
});

describe('expanded modeling editor controls',()=>{
 it('offers typed controls and a safe default icon for every registered kind',()=>{
  expect(editor).toContain('const enumFields:');
  expect(editor).toContain("{value:'multiply',label:'乘'}");
  expect(editor).toContain("['join','union','intersection','difference','lookup','exists','crossJoin','fuzzy','dimensionLookup','mergeFlow'].includes(node.kind)");
  expect(editor).toContain('icons[n.kind]||<FunctionOutlined/>');
 });
});


describe('local file modeling boundaries',()=>{
 it('keeps files local and exposes downloads only after preview',()=>{
  expect(editor).toContain("file.size>2000000");
  expect(editor).toContain('parseModelFile(await file.text()');
  expect(editor).toContain('importBusy.current=true');
  expect(editor).toContain('disabled={importing}');
  expect(editor).toContain('nodesRef.current.some(item=>item.id!==id&&item.config.table===previousKey)');
  expect(editor).toContain('disabled={!result||Boolean(error)||exporting}');
  expect(editor).toContain('仅风驰表格 XML 格式');
 });
});

describe('configuration-only modeling boundary',()=>{
 it('allows editing future execution nodes without enabling execution',()=>{
  expect(editor).toContain("selectedOperator?.execution==='configuration'");
  expect(editor).toContain('disabled={!node||configurationOnly}');
  expect(editor).toContain('仅配置：尚未接入执行器');
  expect(editor).toContain('Input.TextArea');
  expect(editor).toContain("['csvInput','jsonInput','xmlInput','excelInput','parquetInput'].includes(node.kind)&&<label>本地文件");
 });
});


import {modelNodeSummary,createModelNode} from '../src/modeling';
describe('node configuration summaries',()=>{
 it('shows missing values and current configuration without invented metadata',()=>{
 const node=createModelNode('filter','f',0);
 expect(modelNodeSummary(node,{},[])).toBe('未配置 不为空');
 node.config.field='status';node.config.operator='eq';node.config.value='active';
 expect(modelNodeSummary(node,{},[])).toBe('status 等于 active');
 const join=createModelNode('join','j',1);join.config={left:'user_id',right:'id'};
 expect(modelNodeSummary(join,{},[])).toBe('user_id ↔ id · 输入 0/2');
 expect(modelNodeSummary(createModelNode('source','s',0),{},[])).toBe('来源：未配置');
 });
});
