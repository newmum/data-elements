import type {ModelNode} from './modeling';
import type {TableDef} from './data';
import type {ExecutionSettings} from './modelingExecution';
export type ModelSqlDiagnostic={level:'error'|'warning';message:string;nodeId?:string};
export type ModelSqlDraft={sql:string;diagnostics:ModelSqlDiagnostic[];engine:ExecutionSettings['engine'];mode:ExecutionSettings['mode'];nodeCtes:Record<string,string>};
export function quoteModelIdentifier(value:string):string {if(!value||value.includes('\0'))throw new Error('标识符为空或含无效字符');return '`'+value.replaceAll('`','``')+'`'}
export function quoteModelLiteral(value:string,engine:ExecutionSettings['engine']):string {if(value.includes('\0'))throw new Error('字符串包含无效字符');return "'"+(engine==='spark'?value.replaceAll('\\','\\\\').replaceAll("'","\\'"):value.replaceAll("'","''"))+"'"}
/** Generates review-only CTE SQL. No catalog access, custom SQL evaluation or execution. */
export function generateModelSql(nodes:ModelNode[],tables:Record<string,TableDef>,settings:ExecutionSettings):ModelSqlDraft {
 const diagnostics:ModelSqlDiagnostic[]=[];const finish=(sql=''):ModelSqlDraft=>({sql,diagnostics,...settings,nodeCtes:Object.fromEntries(nodes.map(n=>[n.id,`node_${n.id}`]))});
 const error=(message:string,nodeId?:string)=>diagnostics.push({level:'error',message,nodeId});
 const supported=new Set(['source','filter','join','aggregate','rename','output']);
 if(nodes.length>30){error('最多支持 30 个节点');return finish()}
 if(new Set(nodes.map(n=>n.id)).size!==nodes.length)error('存在重复节点 ID');
 for(const n of nodes){if(!supported.has(n.kind))error(n.kind==='sql'?'自定义 SQL 无法验证，未纳入生成':`暂不支持算子 ${n.kind} 的 SQL 生成`,n.id);if(Object.values(n.config).some(v=>typeof v!=='string'||v.length>10000))error('配置超过长度限制',n.id)}
 const outputs=nodes.filter(n=>n.kind==='output');if(!outputs.length)error('缺少输出节点');if(diagnostics.length)return finish();
 type Step={name:string;columns:string[];body:string};const steps:Step[]=[],cache=new Map<string,Step>(),visiting=new Set<string>();
 const q=quoteModelIdentifier;const field=(step:Step,key:string)=>{if(!step.columns.includes(key))throw new Error(`字段不存在或未配置：${key}`);return q(key)};
 function visit(id:string):Step {
  const existing=cache.get(id);if(existing)return existing;if(visiting.has(id))throw new Error('模型存在循环');const n=nodes.find(n=>n.id===id);if(!n)throw new Error('前序节点不存在');visiting.add(id);
  const count=n.kind==='source'?0:n.kind==='join'?2:1;if(n.inputs.length!==count||new Set(n.inputs).size!==count)throw new Error(`${n.name}输入连线数不正确`);
  const inputs=n.inputs.map(visit),a=inputs[0],b=inputs[1],c=n.config;let columns:string[]=[],body='';
  switch(n.kind){
   case 'source':{const tableKey=c.inputMode==='sql'?c.mockSqlTable:c.table;const table=Object.hasOwn(tables,tableKey)?tables[tableKey]:undefined;if(!table?.columns.length)throw new Error(`来源表未加载：${c.table||'未选择'}`);columns=table.columns.map(v=>v.name);if(columns.length>200||new Set(columns).size!==columns.length)throw new Error('来源字段过多或重名');if(c.inputMode==='sql'){
 const draft=(c.sqlText||'').replace(/^(?:\s*--[^\n]*(?:\n|$))*/,'').trim().replace(/;\s*$/,'');
 if(!/^SELECT\b/i.test(draft)||/[;\0]/.test(draft)||/--|\/\*|\*\//.test(draft)||/\b(?:INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|MERGE|CALL|INTO)\b/i.test(draft))throw new Error('自定义 SQL 仅支持单条 SELECT 草稿；复杂语句无法验证');
 body=`SELECT ${columns.map(q).join(', ')}\nFROM (\n${draft}\n) AS custom_source`;
 diagnostics.push({level:'warning',nodeId:n.id,message:'自定义 SQL 仅嵌入审核草稿，使用本地样本字段；实际返回字段、方言、跨库访问未验证，不执行 SQL。'});
 }else{const path=c.table.split('.');body=`SELECT ${columns.map(q).join(', ')}\nFROM ${path.map(q).join('.')}`;}diagnostics.push({level:'warning',nodeId:n.id,message:`来源 ${tableKey} 的 Catalog/跨库连接、权限及视图定义均未验证；这里只生成引用。`});break}
   case 'filter':{const f=field(a,c.field);let predicate='';if(c.operator==='notNull')predicate=`${f} IS NOT NULL`;else if(c.operator==='eq')predicate=`CAST(${f} AS ${settings.engine==='spark'?'STRING':'VARCHAR'}) = ${quoteModelLiteral(c.value,settings.engine)}`;else if(c.operator==='gt'){if(!c.value.trim()||!Number.isFinite(Number(c.value)))throw new Error('比较值必须为有限数字');predicate=`${f} > ${Number(c.value)}`}else throw new Error('筛选条件不支持');columns=[...a.columns];body=`SELECT ${columns.map(q).join(', ')}\nFROM ${q(a.name)}\nWHERE ${predicate}`;break}
   case 'join':{const left=field(a,c.left),right=field(b,c.right),renamed=b.columns.map(v=>`right.${v}`);if(renamed.some(v=>a.columns.includes(v)))throw new Error('关联后字段重名');columns=[...a.columns,...renamed];body=`SELECT ${[...a.columns.map(v=>`l.${q(v)} AS ${q(v)}`),...b.columns.map(v=>`r.${q(v)} AS ${q('right.'+v)}`)].join(', ')}\nFROM ${q(a.name)} AS l\nINNER JOIN ${q(b.name)} AS r ON l.${left} = r.${right}`;break}
   case 'aggregate':{const group=field(a,c.group);if(!['count','sum'].includes(c.operation))throw new Error('聚合方式不支持');const name=c.operation==='count'?'_count':'_sum';if(c.group===name)throw new Error('聚合输出字段重名');const expression=c.operation==='count'?'COUNT(*)':`COALESCE(SUM(${field(a,c.field)}), 0)`;columns=[c.group,name];body=`SELECT ${group}, ${expression} AS ${q(name)}\nFROM ${q(a.name)}\nGROUP BY ${group}`;break}
   case 'rename':{field(a,c.field);const name=c.name.trim();q(name);if(name!==c.field&&a.columns.includes(name))throw new Error('重命名字段已存在');columns=a.columns.map(v=>v===c.field?name:v);body=`SELECT ${a.columns.map(v=>v===c.field?`${q(v)} AS ${q(name)}`:q(v)).join(', ')}\nFROM ${q(a.name)}`;break}
   case 'output':columns=[...a.columns];body=`SELECT ${columns.map(q).join(', ')}\nFROM ${q(a.name)}`;break;
   default:throw new Error('算子不支持 SQL 生成');
  }
  if(columns.length>200)throw new Error('输出字段超过 200 列');const step={name:`node_${n.id}`,columns,body};steps.push(step);cache.set(id,step);visiting.delete(id);return step;
 }
 try{for(const output of outputs)visit(output.id);if(cache.size!==nodes.length)throw new Error('存在未连接到输出的节点');}
 catch(e){error(e instanceof Error?e.message:'生成失败');return finish()}
 if(settings.engine==='spark')diagnostics.push({level:'warning',message:'Spark 字符串转义按默认 spark.sql.parser.escapedStringLiterals=false 生成，请核对会话配置。'});
 diagnostics.push({level:'warning',message:'SQL 仅供审核；数据类型、空值、排序及真实引擎版本语义需实测。未生成连接器 DDL 或写入语句。'});
 if(settings.mode==='stream')diagnostics.push({level:'warning',message:'实时配置仅作目标标记；状态、窗口、水位线、检查点及结果更新流尚未配置，不能作为已就绪流作业。'});
 const ctes=steps.map(s=>`${q(s.name)} AS (\n  ${s.body.replaceAll('\n','\n  ')}\n)`).join(',\n');
 const sql=`-- ${settings.engine==='flink'?'Flink SQL':'Spark SQL'} · ${settings.mode==='batch'?'批处理':'实时目标'} · 审核草稿，不执行\n-- 来源名称为模型引用；请核对 Catalog 与跨库连接配置。\n`+outputs.map(n=>`WITH ${ctes}\nSELECT * FROM ${q(cache.get(n.id)!.name)};`).join('\n\n');
 return finish(sql);
}

/** Strict reverse subset: only existing eq/gt filter values. Full regeneration must match. */
export function applyModelSqlDraft(originalNodes:ModelNode[],tables:Record<string,TableDef>,settings:ExecutionSettings,editedSql:string):{nodes?:ModelNode[];error?:string}{
 const original=generateModelSql(originalNodes,tables,settings);if(!original.sql)return {error:'当前模型无法生成可回写草稿'};
 if(editedSql===original.sql)return {nodes:structuredClone(originalNodes)};
 if(editedSql.length>1000000)return {error:'SQL 草稿过长'};
 const slots:{start:number;end:number;nodeId:string;operator:string}[]=[];
 for(const n of originalNodes.filter(n=>n.kind==='filter'&&['eq','gt'].includes(n.config.operator))){
  const c=n.config;const prefix=c.operator==='eq'?`CAST(${quoteModelIdentifier(c.field)} AS ${settings.engine==='spark'?'STRING':'VARCHAR'}) = `:`${quoteModelIdentifier(c.field)} > `;
  const value=c.operator==='eq'?quoteModelLiteral(c.value,settings.engine):String(Number(c.value));
  const head=`${quoteModelIdentifier(original.nodeCtes[n.id])} AS (\n`;let cursor=0;
  while((cursor=original.sql.indexOf(head,cursor))!==-1){const where=original.sql.indexOf(`\n  WHERE ${prefix}`,cursor+head.length);if(where===-1)return {error:'无法定位筛选条件'};const start=where+`\n  WHERE ${prefix}`.length;if(original.sql.slice(start,start+value.length)!==value)return {error:'无法无损定位筛选值'};slots.push({start,end:start+value.length,nodeId:n.id,operator:c.operator});cursor=start+value.length;}
 }
 slots.sort((a,b)=>a.start-b.start);if(!slots.length)return {error:'仅支持修改已有等于或大于筛选值'};
 const captured:string[]=[];let originalOffset=0,editedOffset=0;
 for(const slot of slots){
  if(slot.start<originalOffset)return {error:'筛选定位冲突'};
  const fixed=original.sql.slice(originalOffset,slot.start);if(!editedSql.startsWith(fixed,editedOffset))return {error:'仅允许修改已有筛选值，其他 SQL 结构更改不支持回写'};
  editedOffset+=fixed.length;const begin=editedOffset;
  if(slot.operator==='gt'){const number=/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:e[+-]?\d+)?/i.exec(editedSql.slice(begin));if(!number)return {error:'请输入有限数值'};editedOffset+=number[0].length;}
  else {if(editedSql[editedOffset++]!=="'")return {error:'等于条件须为字符串字面量'};let closed=false;while(editedOffset<editedSql.length){const ch=editedSql[editedOffset++];if(settings.engine==='spark'&&ch==='\\'){editedOffset++;continue}if(ch==="'"){if(settings.engine==='flink'&&editedSql[editedOffset]==="'"){editedOffset++;continue}closed=true;break}}if(!closed)return {error:'字符串引号不完整'};}
  captured.push(editedSql.slice(begin,editedOffset));originalOffset=slot.end;
 }
 if(editedSql.slice(editedOffset)!==original.sql.slice(originalOffset))return {error:'同一节点的重复条件不一致或SQL结构更改不支持回写'};
 const changes=new Map<string,string>();
 try{for(let i=0;i<slots.length;i++){const slot=slots[i],raw=captured[i];let value='';
  if(slot.operator==='gt'){if(!/^-?(?:0|[1-9]\d*)(?:\.\d+)?(?:e[+-]?\d+)?$/i.test(raw)||!Number.isFinite(Number(raw)))throw new Error('大于条件必须是有限数值');value=String(Number(raw));}
  else {if(raw.length<2||raw[0]!=="'"||raw.at(-1)!=="'")throw new Error('等于条件须为字符串字面量');for(let j=1;j<raw.length-1;j++){const ch=raw[j];if(settings.engine==='spark'&&ch==='\\'){const next=raw[++j];if(!['\\',"'"].includes(next)||j>=raw.length-1)throw new Error('不支持的字符串转义');value+=next}else if(ch==="'"){if(settings.engine==='flink'&&raw[j+1]==="'"&&j+1<raw.length-1){value+="'";j++}else throw new Error('字符串引号不完整')}else value+=ch}if(value.length>10000)throw new Error('筛选值过长');}
  if(changes.has(slot.nodeId)&&changes.get(slot.nodeId)!==value)throw new Error('同一节点的重复条件不一致');changes.set(slot.nodeId,value);
 }
 const nodes=structuredClone(originalNodes).map(n=>changes.has(n.id)?{...n,config:{...n.config,value:changes.get(n.id)!}}:n);
 if(generateModelSql(nodes,tables,settings).sql!==editedSql)return {error:'编辑内容不能无损回写；请仅修改规范格式的筛选值'};return {nodes};
 }catch(e){return {error:e instanceof Error?e.message:'无法解析筛选值'}}
}
