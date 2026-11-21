import {build} from 'esbuild';
import {readFile,writeFile,mkdir,symlink} from 'node:fs/promises';
import {resolve,dirname,relative} from 'node:path';
import {execFileSync} from 'node:child_process';
import {pathToFileURL} from 'node:url';
import {performance} from 'node:perf_hooks';
import * as React from 'react';

const phase=process.argv[2]||'current';
const sourceRef=process.argv[3];
const readSource=path=>sourceRef?execFileSync('git',['show',sourceRef+':data-elements-fengchi/'+relative(process.cwd(),path)],{encoding:'utf8'}):readFile(path,'utf8');
const out=resolve('../logs/fengchi/interaction-measurement');
await mkdir(out,{recursive:true});
await symlink(resolve('node_modules'),resolve(out,'node_modules'),'dir').catch(error=>{if(error.code!=='EEXIST')throw error});
const bundle=resolve(out,phase+'.mjs');
await build({
 entryPoints:[resolve('src/ModelingWorkspace.tsx')],outfile:bundle,bundle:true,platform:'node',
 format:'esm',packages:'external',loader:{'.css':'empty'},jsx:'automatic',
 plugins:[{name:'measure-editor-javascript',setup(build){
  build.onResolve({filter:/^antd\/locale\/zh_CN$/},()=>({path:resolve('node_modules/antd/locale/zh_CN.js'),external:true}));
  build.onLoad({filter:/ModelingWorkspace\.tsx$/},async({path})=>({
   contents:(await readSource(path)).replaceAll('JSON.stringify(','globalThis.__modelBench.stringify(')+'\nexport {ModelingEditor};',
   loader:'tsx',resolveDir:dirname(path),
  }));
  build.onLoad({filter:/modelingSql\.ts$/},async({path})=>({
   contents:(await readSource(path)).replace(
    'const diagnostics:ModelSqlDiagnostic[]=[];',
    'globalThis.__modelBench.sql++;const diagnostics:ModelSqlDiagnostic[]=[];'
   ),loader:'ts',resolveDir:dirname(path),
  }));
  build.onLoad({filter:/\.tsx?$/},async({path})=>({contents:await readSource(path),loader:path.endsWith('.tsx')?'tsx':'ts',resolveDir:dirname(path)}));
 }}],
});
const {ModelingEditor}=await import(pathToFileURL(bundle).href);
const demoBundle=resolve(out,'demo.mjs');
await build({entryPoints:[resolve('src/modelingDemo.ts')],outfile:demoBundle,bundle:true,platform:'node',format:'esm',packages:'external'});
const {createModelingDemo}=await import(pathToFileURL(demoBundle).href);

globalThis.window={addEventListener(){},removeEventListener(){},location:{search:''}};
let hitTests=0;
globalThis.document={elementFromPoint(){hitTests++;return {closest(){return {dataset:{modelInput:'demo-join-orders'}}}}}};
let frameId=0;const frames=new Map();
globalThis.requestAnimationFrame=callback=>{const id=++frameId;frames.set(id,callback);return id};
globalThis.cancelAnimationFrame=id=>frames.delete(id);
const frame=()=>{const tasks=[...frames.values()];frames.clear();tasks.forEach(callback=>callback(0))};
const counters=()=>({sql:0,stringifies:0,renders:0,updates:0,layoutReads:0,stringify(...args){this.stringifies++;return JSON.stringify(...args)}});
function find(tree,predicate){
 if(!tree||typeof tree!=='object')return null;
 if(Array.isArray(tree)){for(const child of tree){const match=find(child,predicate);if(match)return match}return null}
 if(tree.props&&predicate(tree))return tree;
 return tree.props?find(tree.props.children,predicate):null;
}
function fixture(){
 const demo=createModelingDemo(),slots=[];let cursor=0,pending=true,effects=[],tree;
 const metrics=counters();globalThis.__modelBench=metrics;
 const dispatcher={
  useContext(){cursor++;return {theme:'light'}},
  useState(initial){
   const i=cursor++;if(!(i in slots))slots[i]={value:typeof initial==='function'?initial():initial};
   const set=value=>{const next=typeof value==='function'?value(slots[i].value):value;if(!Object.is(next,slots[i].value)){slots[i].value=next;pending=true;metrics.updates++}};
   return [slots[i].value,set];
  },
  useRef(initial){const i=cursor++;if(!(i in slots))slots[i]={current:initial};return slots[i]},
  useMemo(factory,deps){const i=cursor++,old=slots[i];if(!old||!deps||deps.some((value,j)=>!Object.is(value,old.deps[j]))){slots[i]={deps,value:factory()}}return slots[i].value},
  useCallback(callback,deps){return this.useMemo(()=>callback,deps)},
  useEffect(effect,deps){
   const i=cursor++,old=slots[i];
   if(!old||!deps||deps.some((value,j)=>!Object.is(value,old.deps[j]))){
    const slot={deps,cleanup:old?.cleanup};slots[i]=slot;
    effects.push(()=>{slot.cleanup?.();slot.cleanup=effect()});
   }
  },
  useLayoutEffect(effect,deps){return this.useEffect(effect,deps)},
 };
 const props={tables:demo.tables,onClose(){},initialDocument:{
  schemaVersion:1,id:'measurement-orders',name:'跨库客户订单示例',category:'客户订单分析',
  nodes:demo.nodes,importedTables:demo.tables,executionSettings:{engine:'flink',mode:'batch'},revision:1,createdAt:'2026-11-20T00:00:00Z',updatedAt:'2026-11-20T00:00:00Z',
 },onPersist(){}};
 const internals=React.__CLIENT_INTERNALS_DO_NOT_USE_OR_WARN_USERS_THEY_CANNOT_UPGRADE;
 const viewport={clientWidth:2000,clientHeight:1400,setPointerCapture(){},getBoundingClientRect(){return {left:0,top:0}}};
 const canvas={getBoundingClientRect(){metrics.layoutReads++;return {left:0,top:0}}};
 function flush(){
  let cycles=0;
  while(pending){
   if(++cycles>20)throw Error('Unexpected render loop');
   pending=false;cursor=0;effects=[];const previous=internals.H;internals.H=dispatcher;
   try{metrics.renders++;tree=ModelingEditor(props)}finally{internals.H=previous}
   const element=find(tree,element=>element.props.className==='modeling-canvas');
   element.props.ref.current=canvas;
   for(const effect of effects)effect();
  }
 }
 const pointer=(x=500,y=300)=>({button:0,pointerId:1,clientX:x,clientY:y,currentTarget:viewport,target:{closest(){return null}},preventDefault(){},stopPropagation(){}});
 flush();
 return {metrics,flush,pointer,get tree(){return tree},demo};
}
function run(kind){
 frames.clear();hitTests=0;const f=fixture();
 const pick=predicate=>{const value=find(f.tree,predicate);if(!value)throw Error('Missing '+predicate);return value};
 let target;
 if(kind==='pan')target=()=>pick(e=>String(e.props.className).split(' ').includes('modeling-scroll'));
 if(kind==='node')target=()=>pick(e=>e.props['aria-label']==='选择或拖动 '+f.demo.nodes[0].name);
 if(kind==='link')target=()=>pick(e=>e.props['aria-label']===f.demo.nodes[0].name+' 输出端口');
 if(target){target().props.onPointerDown(f.pointer());f.flush()}
 Object.assign(f.metrics,{sql:0,stringifies:0,renders:0,updates:0,layoutReads:0});hitTests=0;
 const begin=performance.now();
 for(let i=0;i<240;i++){
  if(kind==='zoom')pick(e=>e.props['aria-label']===(i%2?'缩小画布':'放大画布')).props.onClick();
  else target().props.onPointerMove(f.pointer(501+i%120,301+Math.floor(i/120)));
  f.flush();
  if(i%4===3){frame();f.flush()}
 }
 if(target){target().props.onPointerUp(f.pointer(620,302));f.flush()}
 frame();f.flush();
 const cpu_ms=performance.now()-begin;const canvas=pick(e=>e.props.className==='modeling-canvas');const node=pick(e=>e.type==='article'&&String(e.props.className).split(' ').includes('modeling-node'));
 return {cpu_ms,sql:f.metrics.sql,stringifies:f.metrics.stringifies,editor_evaluations:f.metrics.renders,state_updates:f.metrics.updates,layout_reads:f.metrics.layoutReads,hit_tests:hitTests,final_geometry:{transform:canvas.props.style.transform,x:node.props.style.left,y:node.props.style.top}};
}
const results={};
for(const kind of ['pan','node','link','zoom']){
 run(kind);
 const samples=Array.from({length:5},()=>run(kind));
 const sorted=[...samples].sort((a,b)=>a.cpu_ms-b.cpu_ms);
 results[kind]={median:sorted[2],samples};
}
async function verifyPreview(entry){
 frames.clear();const f=fixture();const pick=predicate=>find(f.tree,predicate);
 if(pick(e=>e.props.id==='modeling-preview-panel'))throw Error('Preview was initially expanded');
 pick(e=>e.props['aria-label']==='调整预览面板高度').props.onClick();f.flush();
 if(!pick(e=>e.props.id==='modeling-preview-panel'))throw Error('Manual expansion failed');
 let tabs=pick(e=>Array.isArray(e.props.items)&&e.props.items.some(item=>item.key==='preview'));
 find(tabs.props.tabBarExtraContent,e=>e.props['aria-label']==='收起底部预览').props.onClick();f.flush();
 if(pick(e=>e.props.id==='modeling-preview-panel'))throw Error('Manual collapse failed');
 if(entry==='toolbar')pick(e=>e.props.className==='modeling-run').props.onClick();
 else pick(e=>typeof e.props.onStart==='function'&&typeof e.props.onConfigure==='function').props.onStart();
 f.flush();await new Promise(resolve=>setTimeout(resolve,entry==='toolbar'?250:750));f.flush();
 tabs=pick(e=>Array.isArray(e.props.items)&&e.props.items.some(item=>item.key==='preview'));
 if(!tabs)throw Error('Local execution did not open preview');
 const table=find(tabs.props.items.find(item=>item.key==='preview').children,e=>Array.isArray(e.props.dataSource));
 const rows=table?.props.dataSource;if(rows?.length!==6)throw Error('Expected six regional results');
 const actual=rows.map(row=>[row['right.region_name'],row.total_amount]);
 const expected=f.demo.expectedRows.map(row=>[row['right.region_name'],row.total_amount]);
 if(JSON.stringify(actual)!==JSON.stringify(expected))throw Error('Order results changed');
 return {entry,initially_collapsed:true,manual_expansion:true,manual_collapse:true,execution_expansion:true,regional_results:actual};
}
const verification=[await verifyPreview('toolbar'),await verifyPreview('canvas')];
const report={phase,verification,source_ref:sourceRef||'working tree',environment:'Node '+process.version,model:'createModelingDemo(), 18-node cross-database orders, unchanged synthetic rows',workload:'240 input events; one virtual animation-frame flush after every four events; five measured repeats after warmup',measurement:'Production editor function, hooks and event-handler JavaScript with a deterministic hook dispatcher and stub DOM. Excludes React DOM commit, layout/paint, browser FPS, network and Dotty video.',results};
await writeFile(resolve(out,phase+'.json'),JSON.stringify(report,null,2));
console.log(JSON.stringify({phase,verification,measurement:report.measurement,results:Object.fromEntries(Object.entries(results).map(([kind,value])=>[kind,value.median])),report:resolve(out,phase+'.json')},null,2));
