import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import * as React from 'react';
vi.mock('../src/ThemeProvider',()=>({useTheme:()=>({theme:'light'})}));
import ModelingWorkspace from '../src/ModelingWorkspace';
import ModelingStartNode from '../src/ModelingStartNode';
import ModelingNodeHeading from '../src/ModelingNodeHeading';
import ModelingExecutionPanel from '../src/ModelingExecutionPanel';
import {createModelingDemo} from '../src/modelingDemo';
import {loadModelDocument,saveModelDocument,type ModelDocument,type ModelStorage} from '../src/modelCatalogStore';

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
 return {get tree(){return tree},pick:(predicate:(element:any)=>boolean)=>{const found=find(tree,predicate);expect(found).toBeTruthy();return found},flush,dispose:()=>slots.forEach(slot=>slot?.cleanup?.())};
}
let cleanups:(()=>void)[]=[];
beforeEach(()=>{
 vi.useFakeTimers();
 vi.stubGlobal('window',{location:{search:''},addEventListener:vi.fn(),removeEventListener:vi.fn()});
 vi.stubGlobal('requestAnimationFrame',vi.fn());vi.stubGlobal('cancelAnimationFrame',vi.fn());
});
afterEach(()=>{cleanups.forEach(cleanup=>cleanup());cleanups=[];vi.useRealTimers();vi.unstubAllGlobals()});
function fixture(firstId?:string){
 const demo=createModelingDemo();if(firstId){const previous=demo.nodes[0].id;demo.nodes=demo.nodes.map(node=>({...node,id:node.id===previous?firstId:node.id,inputs:node.inputs.map(id=>id===previous?firstId:id)}))}
 const outer=harness(ModelingWorkspace,{tables:demo.tables,onClose:vi.fn()},false);
 const editor=outer.pick(e=>e.type?.name==='ModelingEditor');
 const memory=new Map<string,string>();const storage:ModelStorage={get length(){return memory.size},key:i=>[...memory.keys()][i]??null,getItem:k=>memory.get(k)??null,setItem:(k,v)=>{memory.set(k,v)}};
 let doc=saveModelDocument({id:'selection-orders',name:'订单',category:'客户订单分析',nodes:demo.nodes,importedTables:demo.tables,executionSettings:{engine:'flink',mode:'batch'}},null,storage);
 const persist=vi.fn(draft=>{doc=saveModelDocument(draft,doc.revision,storage)});
 const view=harness(editor.type,{...editor.props,initialDocument:doc,onPersist:persist});cleanups.push(view.dispose);
 return {...view,view,demo,persist,storage,remount:()=>{const reopened=harness(editor.type,{...editor.props,initialDocument:loadModelDocument(doc.id,storage),onPersist:persist});cleanups.push(reopened.dispose);return reopened},get saved(){return loadModelDocument(doc.id,storage) as ModelDocument}};
}
function start(view:ReturnType<typeof harness>){return view.pick(e=>e.type===ModelingStartNode)}
function preview(view:ReturnType<typeof harness>){return view.pick(e=>e.props.className==='modeling-preview-edge-toggle')}
function heading(view:ReturnType<typeof harness>){return view.pick(e=>e.type===ModelingNodeHeading)}
describe('start selection and local execution',()=>{
 it('routes node configuration, toolbar start and script engine settings to the same sidebar without running',()=>{
  const {view}=fixture();expect(preview(view).props['aria-expanded']).toBe(false);view.pick(e=>e.props['aria-label']==='收起属性面板').props.onClick();view.flush();
  for(const entrance of ['node','toolbar','engine']){
   if(entrance==='node')start(view).props.onConfigure();
   if(entrance==='toolbar')view.pick(e=>e.props.className==='modeling-start-action').props.onClick();
   if(entrance==='engine'){
    view.pick(e=>e.props['aria-label']==='编辑模式').props.onChange('script');view.flush();
    view.pick(e=>e.props.children==='切换引擎').props.onClick();
   }
   view.flush();expect(heading(view).props.name).toBe('启动');
   expect(view.pick(e=>e.type===ModelingExecutionPanel)).toBeTruthy();
   expect(preview(view).props['aria-expanded']).toBe(false);
   expect(view.pick(e=>e.props.className==='modeling-stage').props.inert).toBe(false);
   expect(find(view.tree,e=>e.props.open===true)).toBeNull();
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
 it('confirms schedule configuration locally without timers or a running-task claim',()=>{const demo=createModelingDemo();const view=harness(ModelingExecutionPanel,{nodes:demo.nodes,tables:demo.tables,settings:{engine:'flink',mode:'batch',scheduleEnabled:true,intervalMinutes:60,runImmediately:true},onSettingsChange:vi.fn()});view.pick(e=>e.props.children==='确认配置').props.onClick();view.flush();expect(view.pick(e=>e.props.role==='status').props.children).toContain('已确认本地配置；调度服务未连接，未启动任务');expect(vi.getTimerCount()).toBe(0);expect(find(view.tree,e=>e.props.children==='已启用')).toBeNull()});
});

describe('start pointer and keyboard actions',()=>{
 it.each(['start','configure','body'])('activates %s only once and ignores dragging',action=>{
  const onStart=vi.fn(),onConfigure=vi.fn(),onChange=vi.fn();const view=harness(ModelingStartNode,{position:{x:28,y:110},zoom:1,onStart,onConfigure,onChange});
  const group=view.tree;const pointer=(x:number)=>({button:0,clientX:x,clientY:20,pointerId:1,stopPropagation:vi.fn(),target:{closest:()=>action==='body'?null:{getAttribute:()=>action}},currentTarget:{setPointerCapture:vi.fn()}});
  group.props.onPointerDown(pointer(10));group.props.onPointerUp();
  expect(onStart).toHaveBeenCalledTimes(action==='start'?1:0);expect(onConfigure).toHaveBeenCalledTimes(action==='start'?0:1);
  group.props.onPointerDown(pointer(10));group.props.onPointerMove(pointer(30));group.props.onPointerUp();
  expect(onStart).toHaveBeenCalledTimes(action==='start'?1:0);expect(onConfigure).toHaveBeenCalledTimes(action==='start'?0:1);expect(onChange).toHaveBeenCalledOnce();
 });
 it('keeps text and subtitle as keyboard configuration buttons and play as local run',()=>{
  const onStart=vi.fn(),onConfigure=vi.fn();const view=harness(ModelingStartNode,{position:{x:28,y:110},zoom:1,onStart,onConfigure,onChange:vi.fn()});
  const play=view.pick(e=>e.props.className==='modeling-start-play'),label=view.pick(e=>e.props.children?.type==='strong');
  expect(play.props['data-start-action']).toBe('start');expect(label.props['data-start-action']).toBe('configure');
  const click={detail:0,stopPropagation:vi.fn()};play.props.onClick(click);label.props.onClick(click);
  view.pick(e=>e.props['aria-label']==='启动：配置任务').props.onClick(click);
  expect(onStart).toHaveBeenCalledOnce();expect(onConfigure).toHaveBeenCalledTimes(2);
 });
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
