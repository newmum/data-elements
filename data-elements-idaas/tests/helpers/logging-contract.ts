import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { execFileSync } from 'node:child_process';
import { createHash } from 'node:crypto';

// Integration fixtures compile the canonical SFCs with the existing editor compiler.
// This is a browser test dependency; the independently deployed frontend never imports it.
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../..');
let cached:Array<Record<string,unknown>>|undefined;
export function loggingComponents(){
 if(cached)return cached;
 const directory=path.join(root,'lowcode/09.系统管理/09.日志管理');
 cached=fs.readdirSync(directory).filter(file=>file.endsWith('.vue')).map(file=>{
  const source=path.join(directory,file);
  const manifest=JSON.parse(fs.readFileSync(path.join(root,'lowcode/.manifest.json'),'utf8'));
  const resource=manifest.resources.find((row:{path:string})=>row.path==='09.系统管理/09.日志管理/'+file);
  const location=path.join(root,'logs/lowcode/compiled',createHash('sha256').update(resource.id).digest('hex')+'.json');
  const sourceHash=createHash('sha256').update(fs.readFileSync(source,'utf8').replace(/\r\n?/g,'\n')).digest('hex');
  let bundle:Record<string,unknown>|undefined;
  try{const prior=JSON.parse(fs.readFileSync(location,'utf8'));if(prior.sourceSha256===sourceHash)bundle=prior;}catch{}
  if(!bundle){
   const output=JSON.parse(execFileSync(process.execPath,[path.join(root,'tools/compile-lowcode.mjs'),source],{cwd:root,encoding:'utf8'}));
   bundle=JSON.parse(fs.readFileSync(path.join(root,output.compiled),'utf8'));
  }
  return {name:file.match(/\(([^)]+)\)\.vue$/)![1],compile_js:bundle!.compileJs,compile_css:bundle!.compileCss};
 });return cached;
}
export function loggingQuery(params:URLSearchParams){
 const operation=params.get('operation'),kind=params.get('kind')||'auth';
 if(operation==='components')return loggingComponents();
 if(operation==='capabilities')return ['auth','operations','requests','security','exports','archives','policies'];
 if(operation==='statistics')return {total:1,success_count:kind==='security'?0:1,failure_count:0,denied_count:kind==='security'?1:0,partial_count:0};
 if(operation==='summary')return {list:['auth','operations','requests','security'].map(category=>({category,total:1,success_count:1,failure_count:0,denied_count:0,last_event_time:new Date().toISOString()})),total:4};
 if(operation==='lifecycle')return kind==='policies'?['AUTH','OPERATION','REQUEST','SECURITY','TASK'].map(category=>({category,hot_days:180,total_retention_days:365,sample_rate:1,version:0})):{list:[],total:0};
 if(operation==='items')return {list:[],total:0};
 const row={tid:'a'.repeat(32),event_time:new Date().toISOString(),actor_kind:'OPERATOR',actor_id:'validation-user',actor_account:'validation.user',actor_name:'管理人员',actor_org_name:'业务部门',menu_path:'后台管理 → 应用管理',operation_name:'保存应用',action_code:kind==='auth'?'auth:login':'APP_UPDATED',target_name:'应用配置',result:kind==='security'?'DENIED':'SUCCESS',ip:'127.0.0.1',duration_ms:12,trace_id:'b'.repeat(32),request_id:'c'.repeat(32),details:{name:'应用配置',status:'ACTIVE'}};
 return operation==='detail'?row:{list:[row],total:1,page:Number(params.get('page')||1),size:Number(params.get('size')||20),appliedScopeSummary:'ALL'};
}
