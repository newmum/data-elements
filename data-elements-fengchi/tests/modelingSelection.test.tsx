import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import * as React from 'react';
vi.mock('../src/ThemeProvider',()=>({useTheme:()=>({theme:'light'})}));
import ModelingWorkspace from '../src/ModelingWorkspace';
import ModelingStartNode from '../src/ModelingStartNode';
import ModelingNodeHeading from '../src/ModelingNodeHeading';
import ModelingExecutionPanel from '../src/ModelingExecutionPanel';
import ModelingConfigDrawer from '../src/ModelingConfigDrawer';
import ModelingCodeEditor from '../src/ModelingCodeEditor';
import ModelingSqlDiagnostics from '../src/ModelingSqlDiagnostics';
import {BUSINESS_MODEL_EXAMPLES} from '../src/modelBusinessExamples';
import {createModelNode,nodeLabels,type NodeKind} from '../src/modeling';
import {createModelingDemo} from '../src/modelingDemo';
import {loadModelDocument,saveModelDocument,type ModelDocument,type ModelDraft,type ModelStorage} from '../src/modelCatalogStore';

// Exercise production component handlers and effects without a DOM renderer or additional dependencies.
function find(tree:any,predicate:(element:any)=>boolean):any{
 if(!tree||typeof tree!=='object')return null;
 if(Array.isArray(tree)){for(const child of tree){const match=find(child,predicate);if(match)return match}return null}
 if(tree.props&&predicate(tree))return tree;
 return tree.props?find([tree.props.children,tree.props.items?.map((item:any)=>item.children)],predicate):null;
}
function harness(component:(props:any)=>any,props:any,effectsEnabled=true){
 const slots:any[]=[];let cursor=0,pending=true,tree:any,effects:(()=>void)[]=[];
 const dispatcher:any={
  useState(initial:any){const i=cursor++;if(!(i in slots))slots[i]={value:typeof initial==='function'?initial():initial};return [slots[i].value,(value:any)=>{const next=typeof value==='function'?value(slots[i].value):value;if(!Object.is(next,slots[i].value)){slots[i].value=next;pending=true}}]},
  useRef(initial:any){const i=cursor++;if(!(i in slots))slots[i]={current:initial};return slots[i]},
  useMemo(factory:()=>any,deps:any[]){const i=cursor++,old=slots[i];if(!old||deps.some((value,j)=>!Object.is(value,old.deps[j])))slots[i]={deps,value:factory()};return slots[i].value},
  useEffect(effect:()=>any,deps:any[]){const i=cursor++,old=slots[i];if(!old||deps.some((value,j)=>!Object.is(value,old.deps[j]))){const slot={deps,cleanup:old?.cleanup};slots[i]=slot;if(effectsEnabled)effects.push(()=>{slot.cleanup?.();slot.cleanup=effect()})}},
 };
 const internals=(React as any).__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE;
 function flush(){let cycles=0;while(pending){expect(++cycles).toBeLessThan(15);pending=false;cursor=0;effects=[];const previous=internals.H;internals.H=dispatcher;try{tree=component(props)}finally{internals.H=previous}effects.forEach(effect=>effect())}}
 flush();
 return {get tree(){return tree},pick:(predicate:(element:any)=>boolean)=>{const found=find(tree,predicate);expect(found).toBeTruthy();return found},flush,updateProps:(patch:any)=>{Object.assign(props,patch);pending=true;flush()},dispose:()=>slots.forEach(slot=>slot?.cleanup?.())};
}
let cleanups:(()=>void)[]=[];
beforeEach(()=>{
 vi.useFakeTimers();
 vi.stubGlobal('window',{location:{search:''},addEventListener:vi.fn(),removeEventListener:vi.fn()});
 vi.stubGlobal('requestAnimationFrame',vi.fn());vi.stubGlobal('cancelAnimationFrame',vi.fn());
});
afterEach(()=>{cleanups.forEach(cleanup=>cleanup());cleanups=[];vi.useRealTimers();vi.unstubAllGlobals()});
function fixture(firstId?:string,model?:ModelDraft,workspaceTables?:ModelDraft['importedTables']){
 const demo=model?{nodes:structuredClone(model.nodes),tables:structuredClone(model.importedTables)}:createModelingDemo();if(firstId){const previous=demo.nodes[0].id;demo.nodes=demo.nodes.map(node=>({...node,id:node.id===previous?firstId:node.id,inputs:node.inputs.map(id=>id===previous?firstId:id)}))}
 const outer=harness(ModelingWorkspace,{tables:workspaceTables||demo.tables,onClose:vi.fn()},false);
 const editor=outer.pick(e=>e.type?.name==='ModelingEditor');
 const memory=new Map<string,string>();const storage:ModelStorage={get length(){return memory.size},key:i=>[...memory.keys()][i]??null,getItem:k=>memory.get(k)??null,setItem:(k,v)=>{memory.set(k,v)}};
 let doc=saveModelDocument({id:model?.id||'selection-orders',name:model?.name||'订单',category:model?.category||'客户订单分析',nodes:demo.nodes,importedTables:demo.tables,executionSettings:{engine:'flink',mode:'batch'}},null,storage);
 const persist=vi.fn(draft=>{doc=saveModelDocument(draft,doc.revision,storage)});
 const view=harness(editor.type,{...editor.props,initialDocument:doc,visible:true,onPersist:persist});cleanups.push(view.dispose);
 return {...view,view,demo,persist,storage,remount:()=>{const reopened=harness(editor.type,{...editor.props,initialDocument:loadModelDocument(doc.id,storage),visible:true,onPersist:persist});cleanups.push(reopened.dispose);return reopened},get saved(){return loadModelDocument(doc.id,storage) as ModelDocument}};
}
function start(view:ReturnType<typeof harness>){return view.pick(e=>e.type===ModelingStartNode)}
function preview(view:ReturnType<typeof harness>){return view.pick(e=>e.props.className==='modeling-preview-edge-toggle')}
function heading(view:ReturnType<typeof harness>){return view.pick(e=>e.type===ModelingNodeHeading)}
describe('start selection and local execution',()=>{
 it('opens start configuration only through explicit configuration entries and closes without executing',()=>{
  const {view}=fixture();expect(preview(view).props['aria-expanded']).toBe(false);
  start(view).props.onSelect();view.flush();expect(view.pick(e=>e.type===ModelingConfigDrawer).props.open).toBe(false);
  for(const entrance of ['node','toolbar','summary']){
   if(entrance==='node')start(view).props.onConfigure();
   if(entrance==='toolbar')view.pick(e=>e.props.className==='modeling-start-action').props.onClick();
   if(entrance==='summary')view.pick(e=>e.props.children==='配置任务调度').props.onClick();
   view.flush();expect(heading(view).props.name).toBe('启动');expect(view.pick(e=>e.type===ModelingConfigDrawer).props.open).toBe(true);
   expect(preview(view).props['aria-expanded']).toBe(false);expect(view.pick(e=>e.props.className==='modeling-stage').props.inert).toBe(false);
   view.pick(e=>e.type===ModelingConfigDrawer).props.onClose();view.flush();expect(view.pick(e=>e.type===ModelingConfigDrawer).props.open).toBe(false);
  }
 });
 it.each(['play','toolbar'])('opens local output from %s and preserves the six-region result',async entrance=>{
  const {view,demo}=fixture();start(view).props.onConfigure();view.flush();
  if(entrance==='play')start(view).props.onStart();else view.pick(e=>e.props.className==='modeling-run').props.onClick();
  view.flush();expect(preview(view).props['aria-expanded']).toBe(true);
  expect(view.pick(e=>e.props.className==='modeling-stage').props.inert).toBe(true);
  await vi.advanceTimersByTimeAsync(750);view.flush();
  const table=view.pick(e=>e.props.dataSource?.length===6);
  expect(table.props.dataSource.map((r:any)=>r.total_amount)).toEqual([550,300,420,260,375,180]);
  expect(heading(view).props.name).toBe(demo.nodes.find(n=>n.kind==='output')?.name);
  expect(view.pick(e=>e.props.className==='modeling-stage').props.inert).toBe(false);
 });
 it('keeps naming drafts isolated when a data node ID matches the start control key',()=>{const {view}=fixture('start-control');const dataKey=heading(view).key;start(view).props.onConfigure();view.flush();expect(heading(view).key).not.toBe(dataKey)});
 it('renames start and data nodes through normal model saves without losing samples or schedule settings',()=>{
  const f=fixture(),{view}=f;start(view).props.onConfigure();view.flush();heading(view).props.onRename('每日订单调度');view.flush();
  expect(start(view).props.name).toBe('每日订单调度');
  const config=view.pick(e=>e.type===ModelingExecutionPanel);config.props.onSettingsChange({...config.props.settings,scheduleEnabled:true,intervalMinutes:120});view.flush();
  const source=f.demo.nodes[0];view.pick(e=>e.props['aria-label']==='选择或拖动 '+source.name).props.onClick();view.flush();
  expect(heading(view).props.name).toBe(source.name);heading(view).props.onRename('客户来源');view.flush();
  view.pick(e=>e.props['aria-label']==='保存模型').props.onClick();view.flush();
  expect(f.persist).toHaveBeenCalledTimes(1);expect(f.saved.startName).toBe('每日订单调度');
  expect(f.saved.nodes[0]).toMatchObject({name:'客户来源',config:{manualSourceName:'true'}});
  expect(f.saved.importedTables).toEqual(f.demo.tables);expect(f.saved.executionSettings.intervalMinutes).toBe(120);
  const reopened=f.remount();expect(heading(reopened).props.name).toBe('客户来源');start(reopened).props.onConfigure();reopened.flush();expect(heading(reopened).props.name).toBe('每日订单调度');expect(reopened.pick(e=>e.type===ModelingExecutionPanel).props.settings.intervalMinutes).toBe(120);
 });
 it('confirms schedule configuration locally without timers or a running-task claim',()=>{const demo=createModelingDemo();const view=harness(ModelingExecutionPanel,{nodes:demo.nodes,tables:demo.tables,settings:{engine:'flink',mode:'batch',scheduleEnabled:true,intervalMinutes:60,runImmediately:true},onSettingsChange:vi.fn()});view.pick(e=>e.props.children==='确认配置').props.onClick();view.flush();expect(view.pick(e=>e.props.role==='status').props.children).toContain('已确认本地配置；未创建任务，也未执行模型。');expect(vi.getTimerCount()).toBe(0);expect(find(view.tree,e=>e.props.children==='已启用')).toBeNull()});
});

describe('start pointer and keyboard actions',()=>{
 it('selects or drags the body without running or configuring; double click opens configuration',()=>{
  const onStart=vi.fn(),onConfigure=vi.fn(),onSelect=vi.fn(),onChange=vi.fn();const view=harness(ModelingStartNode,{position:{x:28,y:110},zoom:2,onStart,onConfigure,onSelect,onChange});const group=view.tree;
  const pointer=(x:number)=>({button:0,clientX:x,clientY:20,pointerId:1,stopPropagation:vi.fn(),currentTarget:{setPointerCapture:vi.fn()}}),click={stopPropagation:vi.fn()};
  group.props.onPointerDown(pointer(10));group.props.onPointerUp();group.props.onClick(click);expect(onSelect).toHaveBeenCalled();expect(onStart).not.toHaveBeenCalled();expect(onConfigure).not.toHaveBeenCalled();
  group.props.onDoubleClick(click);expect(onConfigure).toHaveBeenCalledOnce();
  group.props.onPointerDown(pointer(10));group.props.onPointerMove(pointer(30));group.props.onPointerUp();group.props.onClick(click);group.props.onDoubleClick(click);expect(onChange).toHaveBeenCalledWith({x:38,y:110});expect(onConfigure).toHaveBeenCalledOnce();expect(onStart).not.toHaveBeenCalled();
  expect(find(group,e=>e.type==='button'&&e.props.className!=='modeling-start-play')).toBeNull();
  expect(view.pick(e=>e.props.className==='modeling-start-label').props.onClick).toBeUndefined();
 });
 it('isolates play pointer/keyboard events and ignores the second click of a double click',()=>{
  const onStart=vi.fn(),onConfigure=vi.fn(),onSelect=vi.fn();const view=harness(ModelingStartNode,{position:{x:28,y:110},zoom:1,onStart,onConfigure,onSelect,onChange:vi.fn()});
  const play=view.pick(e=>e.props.className==='modeling-start-play'),event={detail:1,stopPropagation:vi.fn(),preventDefault:vi.fn()};
  play.props.onPointerDown(event);play.props.onKeyDown(event);play.props.onClick(event);play.props.onClick({...event,detail:2});play.props.onDoubleClick(event);
  expect(onStart).toHaveBeenCalledOnce();expect(onConfigure).not.toHaveBeenCalled();expect(onSelect).not.toHaveBeenCalled();expect(event.stopPropagation).toHaveBeenCalledTimes(5);
  play.props.onClick({...event,detail:0});expect(onStart).toHaveBeenCalledTimes(2);
 });
 it('supports Enter to configure, Space to select, and Alt arrows to move the focused body',()=>{const onConfigure=vi.fn(),onSelect=vi.fn(),onChange=vi.fn();const view=harness(ModelingStartNode,{position:{x:28,y:110},zoom:1,onStart:vi.fn(),onConfigure,onSelect,onChange});const target={};for(const key of ['Enter',' ','ArrowRight'])view.tree.props.onKeyDown({key,altKey:key==='ArrowRight',target,currentTarget:target,preventDefault:vi.fn()});expect(onConfigure).toHaveBeenCalledOnce();expect(onSelect).toHaveBeenCalledOnce();expect(onChange).toHaveBeenCalledWith({x:38,y:110})});
});

describe('selected node title editing',()=>{
 it('commits a trimmed name once across Enter and blur, and cancels Escape or blank drafts',()=>{
  const onRename=vi.fn();const view=harness(ModelingNodeHeading,{name:'订单',onRename});
  const edit=()=>{view.pick(e=>e.props['aria-label']==='编辑节点名称').props.onClick();view.flush()};
  const input=()=>view.pick(e=>e.props['aria-label']==='节点名称');
  edit();input().props.onChange({target:{value:'  区域汇总  '}});view.flush();const entered=input();entered.props.onPressEnter();entered.props.onBlur();view.flush();
  expect(onRename).toHaveBeenCalledExactlyOnceWith('区域汇总');
  edit();input().props.onChange({target:{value:'取消'}});view.flush();const canceled=input();canceled.props.onKeyDown({key:'Escape',stopPropagation:vi.fn()});canceled.props.onBlur();view.flush();
  edit();input().props.onChange({target:{value:'   '}});view.flush();input().props.onBlur();view.flush();expect(onRename).toHaveBeenCalledOnce();
 });
 it('shows the default when no node is selected without a rename button',()=>{const view=harness(ModelingNodeHeading,{onRename:vi.fn()});expect(view.pick(e=>e.type==='strong').props.children).toBe('属性配置');expect(find(view.tree,e=>e.props['aria-label']==='编辑节点名称')).toBeNull()});
});


describe('shared viewport drawer',()=>{
 it('uses a body portal, viewport bounds, native mask/Escape/focus handling and explicit close controls',()=>{const onClose=vi.fn(),onSave=vi.fn(()=>true);const element=ModelingConfigDrawer({open:true,start:false,name:'客户',selectionKey:'data:customer',onRename:vi.fn(),onClose,onSave,saveStatus:'草稿',children:<div>配置内容</div>});expect(element.props.placement).toBe('right');expect(element.props.rootStyle).toEqual({position:'fixed',inset:0});expect(element.props.getContainer).toBeUndefined();expect(element.props.maskClosable).toBe(true);expect(element.props.keyboard).toBe(true);expect(element.props.autoFocus).toBe(true);expect(element.props.width).toContain('860px');expect(element.props.closable['aria-label']).toBe('关闭节点配置');element.props.onClose();expect(onClose).toHaveBeenCalledOnce();expect(element.props.children.props.onClick).toBeUndefined();expect(find(element.props.footer,e=>e.props.children==='关闭')).toBeTruthy()});
 it('leaves every current operator reachable by double click and the visible gear without changing selection semantics',()=>{for(const kind of Object.keys(nodeLabels) as NodeKind[]){const node=createModelNode(kind,'target',0),f=fixture(undefined,{id:'kind-'+kind,name:node.name,category:'测试',nodes:[node],importedTables:{},executionSettings:{engine:'flink',mode:'batch'}}),{view}=f;const card=view.pick(e=>e.props['aria-label']==='选择或拖动 '+node.name);card.props.onClick();view.flush();expect(view.pick(e=>e.type===ModelingConfigDrawer).props.open,kind).toBe(false);card.props.onDoubleClick();view.flush();expect(view.pick(e=>e.type===ModelingConfigDrawer).props.open,kind).toBe(true);view.pick(e=>e.type===ModelingConfigDrawer).props.onClose();view.flush();view.pick(e=>e.props['aria-label']==='配置 '+node.name).props.onClick({stopPropagation:vi.fn()});view.flush();expect(view.pick(e=>e.type===ModelingConfigDrawer).props.name).toBe(node.name);view.dispose()}});
 it('keeps selection changes and hidden model drawers isolated while retaining edits',()=>{const {view,demo}=fixture();start(view).props.onConfigure();view.flush();heading(view).props.onRename('调度草稿');view.flush();view.pick(e=>e.props['aria-label']==='选择或拖动 '+demo.nodes[0].name).props.onDoubleClick();view.flush();expect(view.pick(e=>e.type===ModelingConfigDrawer).props.name).toBe(demo.nodes[0].name);view.updateProps({visible:false});expect(view.pick(e=>e.type===ModelingConfigDrawer).props.open).toBe(false);view.updateProps({visible:true});expect(view.pick(e=>e.type===ModelingConfigDrawer).props.open).toBe(false);expect(start(view).props.name).toBe('调度草稿')});
});

describe('per-model generated and handwritten SQL',()=>{
 function script(view:ReturnType<typeof harness>){view.pick(e=>e.props['aria-label']==='编辑模式').props.onChange('script');view.flush();return view.pick(e=>e.type===ModelingCodeEditor&&e.props.label==='SQL 草稿')}
 it('fills every built-in editor with its own generated Flink logic and preserves it across mode switches and saves',()=>{const models=BUSINESS_MODEL_EXAMPLES.map(item=>item.create());const order=createModelingDemo();models.push({id:'order',name:'跨库',category:'订单',nodes:order.nodes,importedTables:order.tables,executionSettings:{engine:'flink',mode:'batch'}});const texts:string[]=[];for(const model of models){const f=fixture(undefined,model);const text=script(f.view).props.value;expect(text.length,model.id).toBeGreaterThan(500);expect(text).toContain('Flink SQL');expect(text).toContain(model.nodes.find(n=>n.kind==='source')!.config.table.split('.').map(s=>'`'+s+'`').join('.'));texts.push(text);f.view.pick(e=>e.props['aria-label']==='编辑模式').props.onChange('components');f.view.flush();expect(script(f.view).props.value).toBe(text);f.view.pick(e=>e.props['aria-label']==='保存模型').props.onClick();f.view.flush();expect(script(f.remount()).props.value).toBe(text)}expect(new Set(texts).size).toBe(5)});
 it.each(['SELECT 123 AS handwritten',''])('keeps a manual draft including intentional empty text through save/reopen and engine changes: %j',text=>{const f=fixture(),{view}=f;script(view).props.onChange(text);view.flush();expect(script(view).props.value).toBe(text);view.pick(e=>e.props['aria-label']==='保存模型').props.onClick();view.flush();expect(f.saved.sqlDraft?.text).toBe(text);const reopened=f.remount();expect(script(reopened).props.value).toBe(text);reopened.pick(e=>e.props['aria-label']==='SQL 目标引擎').props.onChange('spark');reopened.flush();expect(script(reopened).props.value).toBe(text);expect(reopened.pick(e=>e.type===ModelingSqlDiagnostics).props.diagnostics.some((d:any)=>d.level==='error'&&d.message.includes('已保留'))).toBe(true);expect(reopened.pick(e=>e.props.children==='应用到画布').props.disabled).toBe(true)});
 it('offers safe discard only when a draft exists and restores current generated SQL after confirmation',()=>{const {view}=fixture();script(view);let menu=view.pick(e=>Array.isArray(e.props.menu?.items));expect(menu.props.menu.items[0].disabled).toBe(true);script(view).props.onChange('handwritten');view.flush();menu=view.pick(e=>Array.isArray(e.props.menu?.items));menu.props.menu.onClick({key:'discard'});view.flush();const confirm=view.pick(e=>e.props.title==='放弃手写 SQL 草稿？');expect(confirm.props.open).toBe(true);expect(script(view).props.value).toBe('handwritten');confirm.props.onConfirm();view.flush();expect(script(view).props.value).toContain('INNER JOIN');expect(view.pick(e=>e.props.children==='应用到画布').props.disabled).toBe(true)});
 it('closes script portals when switching away from a model while preserving its text',()=>{const {view}=fixture();script(view).props.onChange('preserved');view.flush();const menu=view.pick(e=>Array.isArray(e.props.menu?.items));menu.props.onOpenChange(true);view.flush();menu.props.menu.onClick({key:'discard'});view.flush();expect(view.pick(e=>e.props.title==='放弃手写 SQL 草稿？').props.open).toBe(true);view.updateProps({visible:false});expect(view.pick(e=>e.props.title==='放弃手写 SQL 草稿？').props.open).toBe(false);expect(view.pick(e=>e.type===ModelingSqlDiagnostics).props.active).toBe(false);view.updateProps({visible:true});expect(script(view).props.value).toBe('preserved');expect(view.pick(e=>e.props.title==='放弃手写 SQL 草稿？').props.open).toBe(false)});
 it('keeps saved manual SQL free of false conflicts after geometry-only drag and reopen',()=>{const f=fixture(),{view}=f;script(view).props.onChange('manual geometry draft');view.flush();view.pick(e=>e.props['aria-label']==='编辑模式').props.onChange('components');view.flush();const node=f.demo.nodes[0],card=view.pick(e=>e.props['aria-label']==='选择或拖动 '+node.name),pointer={button:0,pointerId:1,clientX:100,clientY:100,preventDefault:vi.fn(),stopPropagation:vi.fn(),currentTarget:{setPointerCapture:vi.fn()}};card.props.onPointerDown(pointer);card.props.onPointerMove({...pointer,clientX:140,clientY:130});card.props.onPointerUp();view.flush();view.pick(e=>e.props['aria-label']==='保存模型').props.onClick();view.flush();expect(f.saved.nodes[0].x).toBe(node.x+40);const reopened=f.remount();expect(script(reopened).props.value).toBe('manual geometry draft');expect(reopened.pick(e=>e.type===ModelingSqlDiagnostics).props.diagnostics.some((d:any)=>d.message.includes('手写草稿已保留'))).toBe(false)});
 it('does not conflict on row-only changes and diagnoses schema changes without overwriting manual text',()=>{const demo=createModelingDemo(),{view}=fixture(undefined,{id:'user-schema',name:'用户模型',category:'测试',nodes:demo.nodes,importedTables:{},executionSettings:{engine:'flink',mode:'batch'}},demo.tables);script(view).props.onChange('manual');view.flush();const tables=structuredClone(demo.tables);for(const table of Object.values(tables))table.rows=[];view.updateProps({tables});expect(view.pick(e=>e.type===ModelingSqlDiagnostics).props.diagnostics.some((d:any)=>d.message.includes('手写草稿已保留'))).toBe(false);const key=demo.nodes[0].config.table;tables[key].columns[0].name='changed_schema';view.updateProps({tables:{...tables}});expect(script(view).props.value).toBe('manual');expect(view.pick(e=>e.type===ModelingSqlDiagnostics).props.diagnostics.some((d:any)=>d.level==='error')).toBe(true)});
});


describe('start configuration modes and confirmation',()=>{
 it.each(['manual','schedule','stream'])('maps the %s trigger to existing settings while retaining saved time rules',trigger=>{const onSettingsChange=vi.fn(),settings={engine:'spark' as const,mode:'batch' as const,scheduleEnabled:true,intervalMinutes:120,runImmediately:true,schedule:{frequency:'weekly' as const,time:'10:30',weekday:2,timezone:'UTC' as const,startDate:'',endDate:''}};const view=harness(ModelingExecutionPanel,{nodes:[],tables:{},settings,onSettingsChange});view.pick(e=>e.props['aria-label']==='触发方式'&&typeof e.props.onChange==='function').props.onChange({target:{value:trigger}});expect(onSettingsChange).toHaveBeenCalledWith({...settings,mode:trigger==='stream'?'stream':'batch',scheduleEnabled:trigger==='stream'?true:trigger==='schedule'});expect(vi.getTimerCount()).toBe(0)});
 it.each(['interval','daily','weekly','once'] as const)('shows only applicable %s time fields and permits saved local configuration without starting anything',frequency=>{vi.setSystemTime(new Date('2026-11-22T00:00:00Z'));const onConfirm=vi.fn(()=>true),onClose=vi.fn(),settings={engine:'flink',mode:'batch',scheduleEnabled:true,intervalMinutes:60,runImmediately:true,schedule:{frequency,time:'09:00',weekday:1,timezone:'Asia/Shanghai',startDate:frequency==='once'?'2026-12-01':'',endDate:''}};const view=harness(ModelingExecutionPanel,{nodes:[],tables:{},settings,onSettingsChange:vi.fn(),onConfirm,onClose});expect(Boolean(find(view.tree,e=>e.props['aria-label']==='调度间隔分钟'))).toBe(frequency==='interval');expect(Boolean(find(view.tree,e=>e.props['aria-label']==='执行时间'))).toBe(frequency!=='interval');expect(Boolean(find(view.tree,e=>e.props['aria-label']==='执行星期'))).toBe(frequency==='weekly');expect(Boolean(find(view.tree,e=>e.props['aria-label']==='结束日期'))).toBe(frequency!=='once');view.pick(e=>e.props.children==='确认配置').props.onClick();view.flush();expect(onConfirm).toHaveBeenCalledOnce();expect(view.pick(e=>e.props.role==='status').props.children).toContain('未创建任务，也未执行模型');expect(vi.getTimerCount()).toBe(0);view.pick(e=>e.props.children==='关闭').props.onClick();expect(onClose).toHaveBeenCalledOnce()});
 it('saves manual preferences and blocks invalid or past date schedules without requesting a run',()=>{const manualConfirm=vi.fn(()=>true),manual=harness(ModelingExecutionPanel,{nodes:[],tables:{},settings:{engine:'flink',mode:'batch'},onSettingsChange:vi.fn(),onConfirm:manualConfirm});manual.pick(e=>e.props.children==='确认配置').props.onClick();expect(manualConfirm).toHaveBeenCalledOnce();const onConfirm=vi.fn(()=>true),view=harness(ModelingExecutionPanel,{nodes:[],tables:{},settings:{engine:'flink',mode:'batch',scheduleEnabled:true,intervalMinutes:0},onSettingsChange:vi.fn(),onConfirm});view.pick(e=>e.props.children==='确认配置').props.onClick();view.flush();expect(onConfirm).not.toHaveBeenCalled();expect(view.pick(e=>e.props.role==='status').props.children).toContain('间隔');expect(vi.getTimerCount()).toBe(0)});
 it('keeps ordinary node configuration and manual source names across save and reopen',()=>{const f=fixture(),node=f.demo.nodes.find(n=>n.kind==='filter')!;f.view.pick(e=>e.props['aria-label']==='选择或拖动 '+node.name).props.onDoubleClick();f.view.flush();f.view.pick(e=>e.props['aria-label']==='匹配值').props.onChange({target:{value:'cancelled'}});f.view.flush();f.view.pick(e=>e.type===ModelingConfigDrawer).props.onSave();f.view.flush();expect(f.saved.nodes.find(n=>n.id===node.id)?.config.value).toBe('cancelled');const reopened=f.remount();reopened.pick(e=>e.props['aria-label']==='选择或拖动 '+node.name).props.onDoubleClick();reopened.flush();expect(reopened.pick(e=>e.props['aria-label']==='匹配值').props.value).toBe('cancelled')});
});
