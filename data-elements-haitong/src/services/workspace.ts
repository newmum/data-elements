import { execute, type Action } from '../domain/engine';
import { createSeed } from '../domain/seed';
import type { Workspace } from '../domain/types';
const DB='haitong-local-workspace-v2', STORE='workspaces', KEY='default';
let connection:Promise<IDBDatabase>|undefined;
let channel:BroadcastChannel|undefined;
const notify=()=>{window.dispatchEvent(new Event('haitong:updated'));channel?.postMessage({type:'updated'});};
function db():Promise<IDBDatabase>{
 if(!connection)connection=new Promise((resolve,reject)=>{
  if(!window.indexedDB){reject(new Error('此浏览器不支持本地存储，不能保存工作区'));return;}
  const req=indexedDB.open(DB,1);req.onupgradeneeded=()=>req.result.createObjectStore(STORE);
  req.onerror=()=>{connection=undefined;reject(req.error||new Error('本地存储初始化失败'));};
  req.onsuccess=()=>{req.result.onversionchange=()=>{req.result.close();connection=undefined;};resolve(req.result);};
 });
 if(!channel&&typeof BroadcastChannel!=='undefined'){channel=new BroadcastChannel(DB);channel.onmessage=()=>window.dispatchEvent(new Event('haitong:updated'));}
 return connection;
}
export async function readWorkspace():Promise<Workspace>{
 const d=await db();return new Promise((resolve,reject)=>{const tx=d.transaction(STORE,'readwrite');const req=tx.objectStore(STORE).get(KEY);let state:Workspace;
  req.onsuccess=()=>{state=req.result as Workspace;if(!state){state=createSeed();tx.objectStore(STORE).put(state,KEY);}};
  tx.oncomplete=()=>resolve(structuredClone(state));tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error||new Error('读取事务中止'));});
}
export async function dispatch(action:Action):Promise<Workspace>{
 const d=await db();return new Promise((resolve,reject)=>{const tx=d.transaction(STORE,'readwrite');const req=tx.objectStore(STORE).get(KEY);let state:Workspace;let error:unknown;
  req.onsuccess=()=>{try{state=execute(req.result||createSeed(),action);tx.objectStore(STORE).put(state,KEY);}catch(e){error=e;tx.abort();}};
  tx.oncomplete=()=>{notify();resolve(state);};tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(error||tx.error||new Error('保存失败，未提交修改'));});
}
export function validateBackup(data:unknown):asserts data is Workspace {
 if(!data||typeof data!=='object')throw new Error('备份格式不正确');const d=data as Workspace;
 if(d.schemaVersion!==2||!Number.isSafeInteger(d.revision))throw new Error('备份版本不支持');
 const arrays=['sources','tables','tasks','runs','bills','clusters','strategies','statements','batches','registrations','logs'] as const;
 for(const k of arrays)if(!Array.isArray(d[k])||d[k].length>10000)throw new Error(`备份 ${k} 缺失或过大`);
 const tableIds=new Set(d.tables.map(t=>t.id));const sourceIds=new Set(d.sources.map(t=>t.id));
 if(tableIds.size!==d.tables.length)throw new Error('表身份重复');
 for(const t of d.tables){if(!sourceIds.has(t.sourceId)||!Array.isArray(t.fields)||!Array.isArray(t.rows)||t.rows.length>1000)throw new Error('表结构或来源引用无效');}
 const clusterIds=new Set(d.clusters.map(c=>c.id));
 for(const t of d.tasks){if(!t.flow||!Array.isArray(t.flow.nodes)||!Array.isArray(t.flow.edges)||!Array.isArray(t.mappings)||!clusterIds.has(t.clusterId)||!t.schedule)throw new Error('任务结构无效');for(const m of t.mappings)if(!tableIds.has(m.sourceTableId)||!tableIds.has(m.targetTableId)||!Array.isArray(m.fields))throw new Error('任务映射引用无效');}
}
export async function restoreWorkspace(raw:unknown){validateBackup(raw);const data=structuredClone(raw);data.tasks.forEach(t=>{t.paused=true;});data.runs.filter(r=>['QUEUED','RUNNING'].includes(r.status)).forEach(r=>{r.status='CANCELLED';r.finishedAt=new Date().toISOString();});data.batches.filter(b=>b.status==='RUNNING').forEach(b=>b.status='CANCELLED');const d=await db();return new Promise<void>((resolve,reject)=>{const tx=d.transaction(STORE,'readwrite');tx.objectStore(STORE).put(data,KEY);tx.oncomplete=()=>{notify();resolve();};tx.onerror=()=>reject(tx.error);});}
export function downloadText(name:string,text:string,mime='text/plain;charset=utf-8'){
 const u=URL.createObjectURL(new Blob([text],{type:mime})),a=document.createElement('a');a.href=u;a.download=name;document.body.append(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(u),2000);
}
