import {describe,it,expect} from 'vitest';
import source from '../src/App.tsx?raw';
import editor from '../src/ModelingWorkspace.tsx?raw';
import startNode from '../src/ModelingStartNode.tsx?raw';
describe('model workspace integration',()=>{
 it('reuses the Fengchi brand inside the immersive editor',()=>{expect(editor).toContain('ProductLogo');expect(editor).toContain('风驰数据计算平台');});
 it('keeps an independent immersive editor surface',()=>{expect(source).toContain('className="modeling-overlay"');});
 it('blocks background query shortcuts while modeling is open',()=>{expect(source).toContain('if(modelingOpen){if((e.ctrlKey||e.metaKey)');});
 it('places a single model entry after views and keeps the existing workspace',()=>{
  expect(source).toContain("'视图',openViews)}{tool(<BranchesOutlined");
  expect(source.split('<nav className="main-toolbar">')[1].split('</nav>')[0].match(/'模型',/g)).toHaveLength(1);
  expect(source).toContain('catalogTarget={modelCatalogTarget}');
  expect(source).toContain("display:modelingOpen&&modelingImmersive?'flex':'none'");
 });
});

describe('expanded modeling editor controls',()=>{
 it('offers typed controls and semantic icons for every registered kind',()=>{
  expect(editor).toContain('const enumFields:');
  expect(editor).toContain("{value:'multiply',label:'乘'}");
  expect(editor).toContain("['join','union','intersection','difference','lookup','exists','crossJoin','fuzzy','dimensionLookup','mergeFlow'].includes(node.kind)");
  expect(editor).toContain('<ModelOperatorIcon kind={n.kind}/>');
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
  expect(editor).toContain("validateExecution(nodes,executionSettings).filter(i=>i.level==='error')");
  expect(editor).toContain('仅配置：尚未接入执行器');
  expect(editor).toContain('Input.TextArea');
  expect(editor).toContain("['csvInput','jsonInput','xmlInput','excelInput','parquetInput'].includes(node.kind)&&<Form.Item label=\"本地文件");
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


describe('metadata return toolbar entry',()=>{
 it('keeps the existing target and places the right arrow return after AI',()=>{
 expect(source).not.toContain('>返回元数据管理平台</Button>');
 expect(source).toContain('<NativeIcon name="return"/>');
 expect(source).toContain("'返回',()=>window.location.assign(metadataHome),'metadata-return-button'");
 expect(source.indexOf("'metadata-return-button'")).toBeGreaterThan(source.indexOf("'智能问数',()=>setAiOpen"));
 });
});

describe('operator library refinement',()=>{it('uses collapsible categories and Ant Design form controls',()=>{expect(editor).toContain('<Collapse className="modeling-operator-collapse"');expect(editor).toContain('<Form layout="vertical"');expect(editor).toContain('useState(250)');expect(editor).toContain("operatorSearch.trim()?operatorGroups");});});

describe('compact modeling workspace',()=>{it('keeps equal default panels and inline preview status',()=>{expect(editor).toContain('[paletteWidth,setPaletteWidth]=useState(250),[propertiesWidth,setPropertiesWidth]=useState(250)');expect(editor).toContain('<ModelingNodeHeading');expect(editor).toContain('modeling-collapsed-rail');expect(editor).toContain('modeling-preview-tab-status');expect(editor).toContain("scroll={{x:'max-content'}}");expect(editor).not.toContain("y:Math.max(70,previewHeight-130)");});});

describe('modeling task entry',()=>{it('selects the start node and reuses the right sidebar instead of a dialog',()=>{expect(startNode).toContain('aria-label="启动：配置任务"');expect(editor).toContain('onConfigure={startTask}');expect(editor).toContain('startSelected?<ModelingExecutionPanel');expect(editor).toContain('function startTask(){setStartSelected(true);setSelected(\'\');setPropertiesOpen(true)}');expect(editor).not.toContain('executionOpen');expect(editor).not.toContain('<Modal');});});

describe("inline model catalog",()=>{it("ports only the catalog into the original workbench and retains the editor",()=>{expect(source).toContain('className="model-catalog-workbench"');expect(editor).toContain('createPortal(<ModelingCatalog');expect(editor).toContain('props.catalogTarget');expect(editor).toContain('返回模型列表');expect(editor).toContain('d.sourceId===props.sourceId')})});

describe('catalog matches original table workbench',()=>{it('inherits the workbench Ant prefix rather than the isolated canvas prefix',()=>{expect(editor).toContain('<ConfigProvider prefixCls="ant"><div');expect(editor).toContain('<ConfigProvider prefixCls="model-ant" componentSize="middle"');});});

describe("single model return action",()=>{it("keeps the close action and mounted editors without a duplicate list button",()=>{expect(editor).not.toContain(">模型列表</Button>");expect(editor).toContain('aria-label="返回模型列表"');expect(editor).toContain('onClose={()=>{setCatalog(true);refresh()}}');expect(editor).toContain("opened.map(doc=>");})});

describe('preview only bottom panel',()=>{it('retains preview without a JSON export toolbar action',()=>{expect(editor).not.toContain("label:'节点信息'");expect(editor).toContain("label:'数据预览'");expect(editor).not.toContain('>导出 JSON</Button>');})});

describe('local run control',()=>{it('separates control edges, locks editing and supports reduced motion',()=>{expect(editor).toContain('modeling-control-edges');expect(editor).toContain('inert={localRunning}');expect(editor).toContain('runToken.current!==token');expect(editor).toContain('validateExecution(nodes,executionSettings)');expect(editor).not.toContain('>调试单次</Button>');})});

it('keeps save run start as the three right-hand actions',()=>{const actions=editor.split('<div className="modeling-tools">')[1].split('</div></div>')[0];expect(actions.indexOf('onClick={save}')).toBeLessThan(actions.indexOf('className="modeling-run"'));expect(actions.indexOf('className="modeling-run"')).toBeLessThan(actions.indexOf('onClick={startTask}'));expect(actions).not.toContain('onClick={onClose}');expect(actions).not.toContain('导出 JSON');expect(actions).toContain('SaveOutlined');expect(actions).toContain('PlayCircleOutlined');expect(actions).toContain('PoweroffOutlined')});

it('separates run from start with the existing toolbar divider',()=>{expect(editor).toContain('>运行</Button><i/>{localRunning?')});

it('uses a back arrow and retains mounted draft editors on return',()=>{expect(editor).toContain('icon={<ArrowLeftOutlined/>} aria-label="返回模型列表"');expect(editor).toContain('disabled={localRunning} onClick={onClose}');expect(editor).toContain("display:!catalog&&active===doc.id?'block':'none'");expect(editor).toContain("window.addEventListener('beforeunload',warn)")});


describe('per-model preview visibility',()=>{
 it('starts each newly mounted editor collapsed while retaining other mounted editors',()=>{
  const localEditor=editor.split('function ModelingEditor(')[1];
  expect(localEditor).toContain('[previewOpen,setPreviewOpen]=useState(false)');
  expect(editor).toContain('opened.map(doc=><div key={doc.id}');
  expect(editor).toContain("display:!catalog&&active===doc.id?'block':'none'");
  expect(editor.split('function ModelingEditor(')[0]).not.toContain('setPreviewOpen');
  expect(localEditor.split(' return <ConfigProvider')[0]).not.toContain('setPreviewOpen(false)');
 });
 it('preserves manual expansion and the accessible collapsed-edge controls',()=>{
  expect(editor).toContain('if(!edgeMoved.current)setPreviewOpen(v=>!v)');
  expect(editor).toContain('aria-expanded={previewOpen}');
  expect(editor).toContain('onClick={()=>setPreviewOpen(false)}');
  expect(editor).toContain('previewOpen?"收起数据预览":"展开数据预览"');
  expect(editor).toContain("if(e.key==='Enter'||e.key===' '){e.preventDefault();setPreviewOpen(v=>!v)");
 });
 it('automatically opens the preview for local execution results and necessary run errors',()=>{
  const run=editor.split('async function run(')[1].split('function stopLocalRun')[0];
  expect(run.indexOf('setPreviewOpen(true)')).toBeLessThan(run.indexOf('validateExecution('));
  expect(run).toContain('setResult(shown.result)');
  expect(run).toContain('setSelected(shown.node.id)');
  expect(editor).toContain('onStart={()=>void run(true)}');
  expect(editor).toContain('{previewOpen&&<>');
 });
});
