import type {ModelNode} from './modeling';
import type {TableDef} from './data';
import type {ExecutionSettings} from './modelingExecution';
export type ModelSqlDiagnostic={level:'error'|'warning';message:string;nodeId?:string};
export type ModelSqlDraft={sql:string;diagnostics:ModelSqlDiagnostic[];engine:ExecutionSettings['engine'];mode:ExecutionSettings['mode'];nodeCtes:Record<string,string>};
export function quoteModelIdentifier(value:string):string {if(!value||value.includes('\0'))throw new Error('标识符为空或含无效字符');return '`'+value.replaceAll('`','``')+'`'}
export function quoteModelLiteral(value:string,engine:ExecutionSettings['engine']):string {if(value.includes('\0'))throw new Error('字符串包含无效字符');return "'"+(engine==='spark'?value.replaceAll('\\','\\\\').replaceAll("'","\\'"):value.replaceAll("'","''"))+"'"}
/** Only referenced field schemas participate in draft conflicts; row snapshots never do. */
export function modelSqlSchema(nodes:ModelNode[],tables:Record<string,TableDef>){
 return [...new Set(nodes.filter(n=>n.kind==='source').map(n=>n.config.inputMode==='sql'?n.config.mockSqlTable:n.config.table))].sort().map(key=>({key,columns:tables[key]?.columns.map(({name,type})=>({name,type}))??null}));
}
/** Generates review-only CTE SQL and explicit preflight queries. Never reads records or executes SQL. */
export function generateModelSql(nodes:ModelNode[],tables:Record<string,TableDef>,settings:ExecutionSettings):ModelSqlDraft {
 const diagnostics:ModelSqlDiagnostic[]=[];const finish=(sql=''):ModelSqlDraft=>({sql,diagnostics,...settings,nodeCtes:Object.fromEntries(nodes.map(n=>[n.id,`node_${n.id}`]))});
 const error=(message:string,nodeId?:string)=>diagnostics.push({level:'error',message,nodeId});
 const warn=(message:string,nodeId?:string)=>diagnostics.push({level:'warning',message,nodeId});
 const supported=new Set(['source','filter','conditional','join','lookup','aggregate','rename','select','distinct','output','derived','constant','union','sort','runningSum','lagLead','datePart','jsonScalar','trim','fillNull','assert','regex','mask']);
 if(nodes.length>30){error('最多支持 30 个节点');return finish()}
 if(new Set(nodes.map(n=>n.id)).size!==nodes.length)error('存在重复节点 ID');
 for(const n of nodes){if(!supported.has(n.kind))error(n.kind==='sql'?'自定义 SQL 无法验证，未纳入生成':`暂不支持算子 ${n.kind} 的 SQL 生成`,n.id);if(Object.values(n.config).some(v=>typeof v!=='string'||v.length>10000))error('配置超过长度限制',n.id)}
 const outputs=nodes.filter(n=>n.kind==='output');if(!outputs.length)error('缺少输出节点');if(diagnostics.length)return finish();
 type Order={field:string;direction:'asc'|'desc'};
 type Step={id:string;name:string;columns:string[];body:string;order:Order[];parents:string[]};
 type Check={nodeId:string;sourceId:string;label:string;body:string};
 const steps:Step[]=[],checks:Check[]=[],cache=new Map<string,Step>(),visiting=new Set<string>();
 const q=quoteModelIdentifier,lit=(v:string)=>quoteModelLiteral(v,settings.engine),textType=settings.engine==='spark'?'STRING':'VARCHAR';
 const field=(step:Step,key:string)=>{if(!step.columns.includes(key))throw new Error(`字段不存在或未配置：${key}`);return q(key)};
 const number=(v:string)=>{if(!v?.trim()||!Number.isFinite(Number(v)))throw new Error('请输入有限数字');return Number(v)};
 const newField=(step:Step,key:string)=>{q(key);if(key!==key.trim()||step.columns.includes(key))throw new Error('输出字段为空、含首尾空白或已存在');return q(key)};
 const literal=(c:Record<string,string>)=>c.type==='number'?String(number(c.value)):c.type==='string'?lit(c.value):(()=>{throw new Error('常量类型须为 string 或 number')})();
 const orderBy=(order:Order[])=>order.map(v=>`${q(v.field)} ${v.direction.toUpperCase()} NULLS LAST`).join(', ');
 function regexPattern(pattern:string):{pattern:string;acceptsEmpty:boolean}{
  if(!pattern||pattern.length>128)throw new Error('正则模式须为 1–128 字符');
  const chars=[...pattern];if(chars[0]==='^')chars.shift();if(chars.at(-1)==='$'&&chars.at(-2)!=='\\')chars.pop();let out='',atoms=0,acceptsEmpty=true;
  for(let i=0;i<chars.length;i++){
   let atom=chars[i];if(atom==='\\'){const escaped=chars[++i];if(escaped==='w')atom='[A-Za-z0-9_]';else if(escaped==='d')atom='[0-9]';else if(escaped==='s')throw new Error('空白字符简写的跨引擎转义尚未映射，请保留本地正则校验');else if(escaped&&'.?*+$(){}|'.includes(escaped))atom='['+escaped+']';else throw new Error('不支持的正则转义')}
   else if(atom==='.')atom='[^\r\n]';else if('[](){}|?*+^$'.includes(atom))throw new Error('仅支持本地安全正则子集，不支持分组或字符类');
   out+=atom;let quantifier='';if(['?','*','+'].includes(chars[i+1])){quantifier=chars[++i];out+=quantifier}if(!['?','*'].includes(quantifier))acceptsEmpty=false;if(++atoms>64)throw new Error('正则最多 64 个原子');
  }
  return {pattern:'^(?:'+out+')$',acceptsEmpty};
 }
 function predicate(step:Step,c:Record<string,string>){const f=field(step,c.field);if(c.operator==='notNull')return `${f} IS NOT NULL`;if(c.operator==='eq')return `CAST(${f} AS ${textType}) = ${lit(c.value)}`;if(c.operator==='gt')return `${f} > ${number(c.value)}`;throw new Error('筛选条件不支持')}
 function visit(id:string):Step {
  const existing=cache.get(id);if(existing)return existing;if(visiting.has(id))throw new Error('模型存在循环');const n=nodes.find(n=>n.id===id);if(!n)throw new Error('前序节点不存在');visiting.add(id);
  const count=n.kind==='source'?0:['join','lookup','union'].includes(n.kind)?2:1;if(n.inputs.length!==count||new Set(n.inputs).size!==count)throw new Error(`${n.name}输入连线数不正确`);
  const inputs=n.inputs.map(visit),a=inputs[0],b=inputs[1],c=n.config;let columns:string[]=[],body='',order:Order[]=a?[...a.order]:[];
  const from=()=>`FROM ${q(a.name)}`;
  const replaceField=(key:string,expression:string)=>a.columns.map(v=>v===key?`${expression} AS ${q(v)}`:q(v)).join(', ');
  const append=(expression:string)=>{newField(a,c.name);columns=[...a.columns,c.name];body=`SELECT ${a.columns.map(q).join(', ')}, ${expression} AS ${q(c.name)}\n${from()}`};
  switch(n.kind){
   case 'source':{const tableKey=c.inputMode==='sql'?c.mockSqlTable:c.table;const table=Object.hasOwn(tables,tableKey)?tables[tableKey]:undefined;if(!table?.columns.length)throw new Error(`来源表未加载：${c.table||'未选择'}`);columns=table.columns.map(v=>v.name);if(columns.length>200||new Set(columns).size!==columns.length)throw new Error('来源字段过多或重名');if(c.inputMode==='sql'){
    const draft=(c.sqlText||'').replace(/^(?:\s*--[^\n]*(?:\n|$))*/,'').trim().replace(/;\s*$/,'');
    if(!/^SELECT\b/i.test(draft)||/[;\0]/.test(draft)||/--|\/\*|\*\//.test(draft)||/\b(?:INSERT|UPDATE|DELETE|DROP|CREATE|ALTER|MERGE|CALL|INTO)\b/i.test(draft))throw new Error('自定义 SQL 仅支持单条 SELECT 草稿；复杂语句无法验证');
    body=`SELECT ${columns.map(q).join(', ')}\nFROM (\n${draft}\n) AS custom_source`;
    warn('自定义 SQL 仅嵌入审核草稿，使用本地样本字段；实际返回字段、方言、跨库访问未验证，不执行 SQL。',n.id);
   }else{const path=c.table.split('.');if(path.some(part=>!part))throw new Error('来源表引用含空路径');body=`SELECT ${columns.map(q).join(', ')}\nFROM ${path.map(q).join('.')}`;}warn(`来源 ${tableKey} 的 Catalog/跨库连接、权限及视图定义均未验证；这里只生成引用。`,n.id);break}
   case 'filter':case 'conditional':{let condition=predicate(a,c);if(n.kind==='conditional'){if(!['matched','unmatched'].includes(c.branch))throw new Error('分支须为 matched 或 unmatched');if(c.branch==='unmatched')condition=`NOT COALESCE((${condition}), FALSE)`;}columns=[...a.columns];body=`SELECT ${columns.map(q).join(', ')}\n${from()}\nWHERE ${condition}`;break}
   case 'join':case 'lookup':{const left=field(a,c.left),right=field(b,c.right),renamed=b.columns.map(v=>`right.${v}`);if(renamed.some(v=>a.columns.includes(v)))throw new Error('关联后字段重名');columns=[...a.columns,...renamed];body=`SELECT ${[...a.columns.map(v=>`l.${q(v)} AS ${q(v)}`),...b.columns.map(v=>`r.${q(v)} AS ${q('right.'+v)}`)].join(', ')}\nFROM ${q(a.name)} AS l\n${n.kind==='join'?'INNER':'LEFT'} JOIN ${q(b.name)} AS r ON l.${left} = r.${right}`;if(n.kind==='join')order=[];else{checks.push({nodeId:n.id,sourceId:b.id,label:'查找右键唯一性：必须返回 0 行',body:`SELECT ${right}, COUNT(*) AS ${q('duplicate_count')}\nFROM ${q(b.name)}\nWHERE ${right} IS NOT NULL\nGROUP BY ${right}\nHAVING COUNT(*) > 1`});warn('键值查找附带右键唯一性检查 SQL；执行前须确认检查返回 0 行，失败中止需由真实执行流程实现，否则 LEFT JOIN 会放大行数。',n.id)}break}
   case 'aggregate':{const group=field(a,c.group);if(!['count','sum'].includes(c.operation))throw new Error('聚合方式不支持');const name=c.operation==='count'?'_count':'_sum';if(c.group===name)throw new Error('聚合输出字段重名');const expression=c.operation==='count'?'COUNT(*)':`COALESCE(SUM(${field(a,c.field)}), 0)`;columns=[c.group,name];body=`SELECT ${group}, ${expression} AS ${q(name)}\n${from()}\nGROUP BY ${group}`;order=[];break}
   case 'rename':{field(a,c.field);const name=c.name.trim();q(name);if(name!==c.field&&a.columns.includes(name))throw new Error('重命名字段已存在');columns=a.columns.map(v=>v===c.field?name:v);body=`SELECT ${a.columns.map(v=>v===c.field?`${q(v)} AS ${q(name)}`:q(v)).join(', ')}\n${from()}`;order=order.map(v=>({...v,field:v.field===c.field?name:v.field}));break}
   case 'select':{columns=[...new Set((c.fields||'').split(',').map(x=>x.trim()).filter(Boolean))];if(!columns.length)throw new Error('请至少选择一个字段');body=`SELECT ${columns.map(v=>field(a,v)).join(', ')}\n${from()}`;order=order.filter(v=>columns.includes(v.field));break}
   case 'distinct':{const keys=[...new Set((c.fields||'').split(',').map(x=>x.trim()).filter(Boolean))];keys.forEach(k=>field(a,k));columns=[...a.columns];if(!keys.length||keys.length===a.columns.length){body=`SELECT DISTINCT ${columns.map(q).join(', ')}\n${from()}`;}else{const rankName='__model_dedup_rank';if(columns.includes(rankName))throw new Error('去重内部字段名称冲突');const ordering=[...order,...a.columns.filter(v=>!order.some(o=>o.field===v)).map(field=>({field,direction:'asc' as const}))];body=`SELECT ${columns.map(q).join(', ')}\nFROM (\n  SELECT ${columns.map(q).join(', ')}, ROW_NUMBER() OVER (PARTITION BY ${keys.map(q).join(', ')} ORDER BY ${orderBy(ordering)}) AS ${q(rankName)}\n  ${from()}\n) AS dedup_candidates\nWHERE ${q(rankName)} = 1`;warn('部分字段去重：本地保留输入首行；SQL 按已配置排序及其余字段升序选择候选。同键不同值且没有业务排序时，需确认保留规则，不能依赖数据库物理顺序。',n.id)}break}
   case 'derived':{const operand=number(c.operand),operators:Record<string,string>={add:'+',subtract:'-',multiply:'*',divide:'/'};if(!operators[c.operation]||c.operation==='divide'&&operand===0)throw new Error('四则操作或除数无效');append(`CAST(${field(a,c.field)} AS DOUBLE) ${operators[c.operation]} ${operand}`);break}
   case 'constant':append(literal(c));break;
   case 'fillNull':columns=[...a.columns];body=`SELECT ${replaceField(c.field,`COALESCE(${field(a,c.field)}, ${literal(c)})`)}\n${from()}`;break;
   case 'trim':columns=[...a.columns];body=`SELECT ${replaceField(c.field,`TRIM(${field(a,c.field)})`)}\n${from()}`;warn('TRIM 对普通首尾空格与当前样例一致；浏览器 trim 与 SQL 对 Unicode 空白的范围可能不同，真实字段需核对。',n.id);break;
   case 'mask':{const keep=number(c.keep);if(!Number.isSafeInteger(keep)||keep<0)throw new Error('脱敏保留位数须为非负整数');const f=field(a,c.field);columns=[...a.columns];const stars=`REPEAT('*', GREATEST(CHAR_LENGTH(${f}) - ${keep}, 0))`,tail=keep?`SUBSTRING(${f}, GREATEST(CHAR_LENGTH(${f}) - ${keep} + 1, 1))`:"''";body=`SELECT ${replaceField(c.field,`CASE WHEN ${f} IS NULL THEN NULL ELSE CONCAT(${stars}, ${tail}) END`)}\n${from()}`;warn('脱敏按 SQL 字符长度计算；非 BMP 字符与浏览器 UTF-16 截断语义需核对。',n.id);break}
   case 'union':if(a.columns.length!==b.columns.length||a.columns.some(k=>!b.columns.includes(k)))throw new Error('合并输入须具有相同字段');columns=[...a.columns];body=`SELECT ${columns.map(q).join(', ')}\n${from()}\nUNION ALL\nSELECT ${columns.map(q).join(', ')}\nFROM ${q(b.name)}`;order=[];break;
   case 'sort':field(a,c.field);if(!['asc','desc'].includes(c.direction))throw new Error('排序方向必须为 asc 或 desc');columns=[...a.columns];order=[{field:c.field,direction:c.direction as 'asc'|'desc'}];body=`SELECT ${columns.map(q).join(', ')}\n${from()}\nORDER BY ${orderBy(order)}`;warn('排序在批处理审核 SQL 中生成；文本排序依赖真实引擎排序规则，与浏览器 zh-CN 排序可能不同。',n.id);break;
   case 'runningSum':case 'lagLead':{if(!order.length)throw new Error('累计或偏移 SQL 需要前序显式排序，不能依赖物理输入顺序');const f=field(a,c.field),over=`ORDER BY ${orderBy(order)}`;if(n.kind==='runningSum')append(`COALESCE(SUM(${f}) OVER (${over} ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW), 0)`);else{const offset=number(c.offset);if(!Number.isSafeInteger(offset)||offset<1||!['lag','lead'].includes(c.operation))throw new Error('窗口偏移方式或步长无效');append(`${c.operation.toUpperCase()}(${f}, ${offset}) OVER (${over})`)}warn('窗口按前序排序字段计算；排序键相同时本地输入顺序与 SQL 未必一致，需业务唯一排序键。当前只生成批处理审核 SQL，实时 OVER 约束需另行验证。',n.id);break}
   case 'datePart':{if(!['year','month','day','hour'].includes(c.part))throw new Error('日期分量不支持');const f=field(a,c.field);const date=c.part==='hour'?`CAST(REPLACE(REPLACE(CAST(${f} AS ${textType}), 'T', ' '), 'Z', '') AS TIMESTAMP(3))`:`CAST(SUBSTRING(CAST(${f} AS ${textType}), 1, 10) AS DATE)`;append(`EXTRACT(${c.part.toUpperCase()} FROM ${date})`);warn('日期按 ISO 日期或 UTC 文本生成；真实输入须满足相同格式，SQL 转换错误与浏览器严格校验不完全等价。',n.id);break}
   case 'jsonScalar':{const path=c.path?c.path.split('.'):[];if(path.some(v=>!v||['__proto__','constructor','prototype'].includes(v)))throw new Error('不允许的 JSON 路径');if(path.some(v=>!/^[$A-Z_a-z][$\w]*$/.test(v)))throw new Error('JSON SQL 暂只支持简单属性路径');const jsonPath='$'+path.map(v=>'.'+v).join('');append(settings.engine==='flink'?`JSON_VALUE(${field(a,c.field)}, ${lit(jsonPath)} RETURNING VARCHAR NULL ON EMPTY ERROR ON ERROR)`:`get_json_object(${field(a,c.field)}, ${lit(jsonPath)})`);warn('JSON 标量 SQL 默认返回文本；布尔与文本值可审核，数值下游须核对字段类型。缺失路径返回 NULL，非法 JSON 的引擎行为需核对。',n.id);break}
   case 'assert':case 'regex':{const f=field(a,c.field);let check='';if(n.kind==='assert'){if(c.rule==='unique')check=`SELECT ${f}, COUNT(*) AS ${q('duplicate_count')}\n${from()}\nGROUP BY ${f}\nHAVING COUNT(*) > 1`;else if(c.rule==='notNull')check=`SELECT *\n${from()}\nWHERE ${f} IS NULL`;else throw new Error('断言规则不支持')}else{const pattern=regexPattern(c.pattern);check=`SELECT *\n${from()}\nWHERE ${f} IS NULL OR CHAR_LENGTH(${f}) > 1024 OR CASE WHEN CHAR_LENGTH(${f}) = 0 THEN ${pattern.acceptsEmpty?'FALSE':'TRUE'} ELSE COALESCE(REGEXP_EXTRACT(${f}, ${lit(pattern.pattern)}, 0), '') <> ${f} END`;}
    checks.push({nodeId:n.id,sourceId:a.id,label:n.kind==='assert'?'质量断言：必须返回 0 行':'正则完整匹配检查：必须返回 0 行',body:check});columns=[...a.columns];body=`SELECT ${columns.map(q).join(', ')}\n${from()}`;warn('质量校验已生成独立检查 SELECT，必须先确认返回 0 行再读取输出；SQL 不会像本地执行器一样自动抛错中止，失败中止仍需真实执行流程接入。',n.id);break}
   case 'output':columns=[...a.columns];body=`SELECT ${columns.map(q).join(', ')}\n${from()}`;break;
   default:throw new Error('算子不支持 SQL 生成');
  }
  if(columns.length>200)throw new Error('输出字段超过 200 列');const step={id:n.id,name:`node_${n.id}`,columns,body,order,parents:n.inputs};steps.push(step);cache.set(id,step);visiting.delete(id);return step;
 }
 try{for(const output of outputs)visit(output.id);if(cache.size!==nodes.length)throw new Error('存在未连接到输出的节点');}
 catch(e){error(e instanceof Error?e.message:'生成失败');return finish()}
 if(settings.engine==='spark')warn('Spark 字符串转义按默认 spark.sql.parser.escapedStringLiterals=false 生成，请核对会话配置。');
 warn('SQL 仅供审核；数据类型、空值、排序及真实引擎版本语义需实测。未生成连接器 DDL 或写入语句。');
 if(settings.mode==='stream')warn('实时配置仅作目标标记；状态、窗口、水位线、检查点及结果更新流尚未配置，不能作为已就绪流作业。');
 function statement(id:string,query:string){const needed=new Set<string>();const collect=(key:string)=>{if(needed.has(key))return;needed.add(key);cache.get(key)!.parents.forEach(collect)};collect(id);const ctes=steps.filter(s=>needed.has(s.id)).map(s=>`${q(s.name)} AS (\n  ${s.body.replaceAll('\n','\n  ')}\n)`).join(',\n');return `WITH ${ctes}\n${query};`}
 const safeComment=(value:string)=>value.replace(/[\r\n\0]/g,' ');
 const preflights=checks.map(c=>`-- ${safeComment(c.label)} · ${safeComment(c.nodeId)}\n`+statement(c.sourceId,c.body));
 const queries=outputs.map(n=>{const step=cache.get(n.id)!;return `-- 输出：${safeComment(n.name)}\n`+statement(n.id,`SELECT * FROM ${q(step.name)}${step.order.length?'\nORDER BY '+orderBy(step.order):''}`)});
 const sql=`-- ${settings.engine==='flink'?'Flink SQL':'Spark SQL'} · ${settings.mode==='batch'?'批处理':'实时目标'} · 审核草稿，不执行\n-- 来源名称为模型引用；请核对 Catalog 与跨库连接配置。\n`+[...preflights,...queries].join('\n\n');
 if(sql.length>100000){error('生成 SQL 超过编辑器 10 万字符上限，请减少输出分支');return finish()}
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
