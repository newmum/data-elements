import type { Bill, Check, DataTable, Difference, FieldMapping, Flow, Row, Task, TaskDefinition, Workspace } from './types';
export const uid = (prefix = 'id') => `${prefix}-${crypto.randomUUID()}`;
export const clone = <T,>(x: T): T => structuredClone(x);
/** Typed canonical values preserve null, numeric strings and field order. Never parse Int64 through Number. */
export const canonical = (value: unknown): string => value === undefined ? '["missing"]' : value === null ? '["null"]' : JSON.stringify([typeof value, value]);
export const rowSignature = (row: Row, fields: string[]) => JSON.stringify(fields.map(f => [f, canonical(row[f])]));
/** Small UTF-8 SHA-1 compatibility implementation, tested against Web Crypto/Node published vectors. Not an authentication primitive. */
export function sha1(text: string): string {
  const bytes = new TextEncoder().encode(text), len = Math.ceil((bytes.length + 9) / 64) * 64;
  const padded = new Uint8Array(len); padded.set(bytes); padded[bytes.length] = 128;
  const dv = new DataView(padded.buffer); dv.setUint32(len - 8, Math.floor(bytes.length / 0x20000000)); dv.setUint32(len - 4, bytes.length << 3);
  const h = [0x67452301, 0xefcdab89, 0x98badcfe, 0x10325476, 0xc3d2e1f0]; const w = new Int32Array(80);
  const rot = (n: number, bits: number) => (n << bits) | (n >>> (32 - bits));
  for (let off = 0; off < len; off += 64) {
    for (let i = 0; i < 16; i++) w[i] = dv.getInt32(off + i * 4);
    for (let i = 16; i < 80; i++) w[i] = rot(w[i - 3] ^ w[i - 8] ^ w[i - 14] ^ w[i - 16], 1);
    let [a,b,c,d,e] = h;
    for (let i = 0; i < 80; i++) {
      const f = i < 20 ? (b & c) | (~b & d) : i < 40 ? b ^ c ^ d : i < 60 ? (b & c) | (b & d) | (c & d) : b ^ c ^ d;
      const k = i < 20 ? 0x5a827999 : i < 40 ? 0x6ed9eba1 : i < 60 ? 0x8f1bbcdc : 0xca62c1d6;
      const temp = (rot(a,5) + f + e + k + w[i]) | 0; e=d; d=c; c=rot(b,30); b=a; a=temp;
    }
    [a,b,c,d,e].forEach((v,i)=> h[i] = (h[i] + v) | 0);
  }
  return h.map(x => (x>>>0).toString(16).padStart(8,'0')).join('');
}
export function project(row: Row, mappings: FieldMapping[]): Row {
  const result: Row = {};
  for (const m of mappings) { let v = row[m.sourceField]; if (v === undefined) v = null;
    if (m.transform !== 'EXACT' && v !== null) { if (typeof v !== 'string') throw new Error('文本转换只能用于字符串字段'); v = m.transform === 'TRIM' ? v.trim() : m.transform === 'UPPER' ? v.toUpperCase() : v.toLowerCase(); }
    result[m.targetField] = v;
  }
  return result;
}
export function compareRows(expected: Row[], actual: Row[], keys: string[], fields: string[]): Difference[] {
  const out: Difference[] = [];
  const index = (rows: Row[], side: string) => { const map = new Map<string, Row>(); for (const row of rows) {
    const key = JSON.stringify(keys.map(k => canonical(row[k])));
    if (keys.some(k => row[k] === null || row[k] === undefined)) { out.push({key, kind:'NULL_KEY', actual:side}); continue; }
    if (map.has(key)) out.push({key, kind:'DUPLICATE', actual:side}); else map.set(key,row);
  } return map; };
  const left=index(expected,'来源'), right=index(actual,'目标');
  for (const [key,row] of left) { const target=right.get(key); if(!target) out.push({key,kind:'MISSING'});
    else for (const field of fields) if (sha1(canonical(row[field])) !== sha1(canonical(target[field]))) out.push({key,kind:'CONTENT',field,expected:canonical(row[field]),actual:canonical(target[field])}); }
  for (const key of right.keys()) if(!left.has(key)) out.push({key,kind:'EXTRA'});
  return out;
}
export function validateFlow(flow: Flow): Check[] {
  const errors: Check[]=[]; const add=(message:string,path='flow')=>errors.push({level:'error',message,path});
  const ids=new Set(flow.nodes.map(n=>n.id)); if(ids.size!==flow.nodes.length) add('节点 ID 重复');
  for(const n of flow.nodes)if(typeof n.name!=='string'||!n.name.trim()||!Number.isFinite(n.x)||!Number.isFinite(n.y))add('组件名称或坐标无效',n.id);
  const reads=flow.nodes.filter(n=>n.kind==='read'), writes=flow.nodes.filter(n=>n.kind==='write');
  if (!reads.length || !writes.length) add('流程至少包含一个来源查询和一个目标写入');
  if(reads.length>1||writes.length>1||flow.nodes.filter(n=>n.kind==='transform').length>1)add('当前本地执行支持单条读取—转换—写入主链；多表请在任务映射中配置');
  if(flow.nodes.filter(n=>n.kind==='send').length!==flow.nodes.filter(n=>n.kind==='receive').length)add('跨网发送与接收组件必须配对');
  for (const e of flow.edges) if(!ids.has(e.source)||!ids.has(e.target)||e.source===e.target) add('连接端点无效或指向自身',e.id);
  for(const node of flow.nodes) if(!['read','transform','write','send','receive'].includes(node.kind)) add('存在不支持的组件',node.id);
  const visited=new Set<string>(), visiting=new Set<string>();
  const walk=(id:string):boolean=>{ if(visiting.has(id)) return false; if(visited.has(id)) return true; visiting.add(id);
    for(const e of flow.edges.filter(x=>x.source===id)) if(!walk(e.target)) return false;
    visiting.delete(id);visited.add(id);return true; };
  if(flow.nodes.some(n=>!walk(n.id))) add('当前批式任务不支持环路');
  const reached=new Set<string>(); const visit=(id:string)=>{if(reached.has(id))return;reached.add(id);flow.edges.filter(e=>e.source===id&&e.outlet==='success').forEach(e=>visit(e.target));}; reads.forEach(n=>visit(n.id));
  if(writes.some(n=>!reached.has(n.id))) add('目标写入节点没有有效的成功路径');
  if(flow.nodes.some(n=>!reached.has(n.id))) add('存在未连接到来源的孤立组件');
  return errors;
}
export function validateTask(task: Task | TaskDefinition, state: Workspace): Check[] {
  const checks=validateFlow(task.flow); const error=(message:string,path='task')=>checks.push({level:'error',message,path});
  if(!task.name.trim()) error('请输入任务名称','name');
  if(!state.clusters.some(c=>c.id===task.clusterId)) error('请选择有效运行集群','clusterId');
  if(!task.mappings.length) error('至少配置一组表映射','mappings');
  const targets = new Set<string>();
  for(const map of task.mappings){ const a=state.tables.find(t=>t.id===map.sourceTableId),b=state.tables.find(t=>t.id===map.targetTableId);
    if(!a||!b){error('来源或目标快照不存在',map.id);continue;}
    if(a.id===b.id)error('不能向同一来源表写入',map.id);
    if(targets.has(b.id))error('同一任务不允许多张来源写入同一目标',map.id); targets.add(b.id);
    if(!map.fields.length)error('请配置字段映射',map.id);
    if(new Set(map.fields.map(f=>f.targetField)).size!==map.fields.length) error('同一目标字段被重复映射',map.id);
    for(const m of map.fields){ const sf=a.fields.find(f=>f.name===m.sourceField),tf=b.fields.find(f=>f.name===m.targetField);
      if(!sf||!tf){error('映射字段不属于选中的表',map.id);continue;}
      if(!['EXACT','TRIM','UPPER','LOWER'].includes(m.transform))error('转换方式不受支持',map.id);
      if(sf.type!==tf.type)error(`${sf.name} → ${tf.name} 类型不兼容`,map.id);
      if(m.transform!=='EXACT'&&sf.type!=='String')error(`${sf.name} 不适用文本转换`,map.id);
    }
    b.fields.filter(f=>!f.nullable).forEach(f=>{if(!map.fields.some(m=>m.targetField===f.name))error(`目标必填字段 ${f.name} 尚未映射`,map.id);});
    const keys=b.fields.filter(f=>f.primary);if(map.writeMode==='UPSERT'&&!keys.length)error('更新写入需要目标主键',map.id);
  }
  return checks;
}
export function matchingMapping(source: DataTable,target:DataTable){return {id:uid('map'),sourceTableId:source.id,targetTableId:target.id,writeMode:'UPSERT' as const,fields:source.fields.filter(f=>target.fields.some(t=>t.name===f.name)).map(f=>({sourceField:f.name,targetField:f.name,transform:'EXACT' as const}))};}
export function defaultFlow(cross=false):Flow {
  const kinds = cross ? ['read','transform','send','receive','write'] as const : ['read','transform','write'] as const;
  const names={read:'来源查询',transform:'字段转换',send:'跨网发送',receive:'回执接收',write:'目标写入'};
  const nodes=kinds.map((kind,i)=>({id:uid('node'),kind,name:names[kind],x:80+i*260,y:180,config:{}}));
  return {nodes,edges:nodes.slice(1).map((n,i)=>({id:uid('edge'),source:nodes[i].id,target:n.id,outlet:'success' as const}))};
}
export const definition=(task:Task):TaskDefinition=>clone({id:task.id,name:task.name,scenario:task.scenario,clusterId:task.clusterId,mappings:task.mappings,flow:task.flow,revision:task.revision,fault:task.fault});
export function nextDaily(phase:number, now=Date.now()):string { const day=Math.floor((now+8*3600000)/86400000);let t=day*86400000-8*3600000+phase*60000;if(t<=now)t+=86400000;return new Date(t).toISOString(); }
export function billCanClose(bill:Bill,bills:Bill[]): boolean { return bill.status==='MATCH'||bills.some(b=>b.createdAt>bill.createdAt&&b.taskId===bill.taskId&&b.mappingId===bill.mappingId&&b.evidenceHash===bill.evidenceHash&&b.status==='MATCH'); }
export const safeCsv=(rows:Row[],fields:string[]):string=>'\ufeff'+[fields,...rows.map(r=>fields.map(f=>r[f]??''))].map(row=>row.map(v=>{let s=String(v);if(/^[=+\-@\t\r]/.test(s))s="'"+s;return '"'+s.replaceAll('"','""')+'"';}).join(',')).join('\r\n');
