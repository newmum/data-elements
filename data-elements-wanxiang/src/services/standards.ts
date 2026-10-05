import { platformApi } from '../shared/platformApi';
import { changed, getSession, type Row } from './api';

export const fieldTypes = ['varchar','char','integer','bigint','decimal','date','datetime','boolean','text'];
const pick = (row:Row, camel:string, snake:string) => row[camel] ?? row[snake];
const string = (value:unknown) => value == null ? '' : String(value);
export function pageOf(value:unknown):{list:Row[];total:number} {
 if (!value || typeof value!=='object') throw new Error('标准列表返回格式不正确');
 const row=value as Row;
 if(Array.isArray(row))return {list:row,total:row.length};
 if(Array.isArray(row.list))return {list:row.list,total:Number(row.total??row.list.length)};
 if(row.list&&typeof row.list==='object')return pageOf(row.list);
 throw new Error('标准列表没有返回有效记录集合');
}
/** Only business fields cross this boundary. Tenant ownership/audit rows are never editable DTOs. */
export function elementRow(raw:Row):Row {
 const row:Row={id:string(raw.tid??raw.id)};
 for(const [camel,snake] of [['metaName','meta_name'],['metaCode','meta_code'],['standardEncode','standard_encode'],['bizDef','biz_def'],['dataTypeId','data_type_id'],['fieldType','field_type'],['fieldLength','field_length'],['numericPrecision','numeric_precision'],['numericScale','numeric_scale'],['formatPattern','format_pattern'],['exampleValue','example_value'],['standardCodeSet','standard_code_set'],['valueDomainId','value_domain_id'],['isNullable','is_nullable'],['dataLevel','data_level'],['dataCategoryId','data_category_id'],['dataCategoryName','data_category_name'],['dataCategoryPath','data_category_path'],['qualityRule','quality_rule'],['customRule','custom_rule'],['versionNo','version_no'],['publishStatus','publish_status'],['updatedTime','updated_time']])row[camel]=pick(raw,camel,snake)??null;
 row.name=string(row.metaName);row.code=string(row.metaCode);row.definition=string(row.bizDef);row.type=string(row.fieldType||row.dataTypeId);row.length=row.fieldLength;row.domain=string(row.dataCategoryName);row.status=Number(row.publishStatus)===1?'PUBLISHED':'PENDING';row.updatedAt=row.updatedTime;
 return row;
}
export async function listElements(signal?:AbortSignal):Promise<Row[]> {
 const rows=new Map<string,Row>();
 for(let pageNum=1;pageNum<=100;pageNum++) {
  const page=pageOf(await platformApi('/dwm/standard/element/page',{pageNum,pageSize:1000},{signal}));
  const before=rows.size;for(const raw of page.list){const row=elementRow(raw);if(!row.id)throw new Error('数据元缺少真实标识');rows.set(row.id,row);}
  if(rows.size>=page.total||!page.list.length)return [...rows.values()];
  if(before===rows.size)throw new Error('标准分页没有推进，请重新读取');
 }
 throw new Error('数据标准数量超出读取范围');
}
export async function readElement(id:string) {const raw=await platformApi<Row>('/dwm/standard/element/queryById',{tid:id});const row=elementRow(raw);if(row.id!==id)throw new Error('数据元不存在或无权访问');return row;}
export function classesOf(value:unknown):Row[] {
 if(Array.isArray(value))return value.flatMap(classesOf);
 if(!value||typeof value!=='object')return [];
 const node=value as Row;
 return [...(node.tid!=null&&String(node.tid)!=='0'?[{id:string(node.tid),name:string(node.label??node.dictName),path:string(node.treePath),children:node.children}]:[]),...classesOf(node.children)];
}
export async function standardChoices(signal?:AbortSignal) {
 const [classes,codes,types,rules]=await Promise.all([
  platformApi('/dwm/standard/standard-class-list',{}, {signal}),listCodes(signal),
  platformApi<Row[]>('/sym/dict?code=colType',undefined,{signal}),platformApi<Row[]>('/dwm/quality/list',{}, {signal}),
 ]);
 if(!Array.isArray(types)||!Array.isArray(rules))throw new Error('标准表单选项返回格式不正确');
 return {classes:classesOf(classes),codes,types:types.map(r=>({value:string(r.value??r.dictCode),label:string(r.label??r.dictName)})),rules:rules.map(r=>({value:string(r.tid),label:string(r.ruleName)}))};
}
export type StandardChoices=Awaited<ReturnType<typeof standardChoices>>;
const bounded = (value:unknown,limit:number,label:string,required=false) => {const result=string(value).trim();if(required&&!result)throw new Error(`请填写${label}`);if(result.length>limit)throw new Error(`${label}不能超过 ${limit} 个字符`);return result||null;};
export function elementBody(values:Row,classes:Row[],existing?:Row|null):Row {
 const v={...existing,...values};
 const category=classes.find(c=>c.id===v.dataCategoryId);
 if(v.dataCategoryId&&!category)throw new Error('所选标准分类已失效，请重新选择');
 const fieldType=bounded(v.fieldType||v.type,64,'规范字段类型',true)!;const fieldLength=Number(v.fieldLength??v.length);
 if(!fieldType||!Number.isInteger(fieldLength)||fieldLength<1||fieldLength>65535)throw new Error('请填写规范字段类型及 1—65535 的整数长度');
 const precision=v.numericPrecision==null||v.numericPrecision===''?null:Number(v.numericPrecision),scale=v.numericScale==null||v.numericScale===''?null:Number(v.numericScale);
 if(precision!=null&&(!Number.isInteger(precision)||precision<1||precision>65)||scale!=null&&(!Number.isInteger(scale)||scale<0||scale>30)||precision!=null&&scale!=null&&scale>precision)throw new Error('数值精度或小数位不正确，小数位不能大于精度');
 if(!['1','2','3','4'].includes(string(v.dataLevel)))throw new Error('请选择有效的数据分级');
 if(![0,1].includes(Number(v.isNullable)))throw new Error('请选择可空性');
 const body:Row={tid:existing?.id||undefined,metaName:bounded(v.name??v.metaName,50,'数据元名称',true),metaCode:bounded(v.code??v.metaCode,50,'英文代码',true),standardEncode:bounded(v.standardEncode,50,'标准编码',true),bizDef:bounded(v.definition??v.bizDef,255,'业务定义'),dataTypeId:bounded(v.dataTypeId||fieldType,100,'数据类型',true),fieldType,fieldLength,numericPrecision:precision,numericScale:scale,formatPattern:bounded(v.formatPattern,500,'格式规则'),exampleValue:bounded(v.exampleValue,500,'示例值'),standardCodeSet:bounded(v.standardCodeSet,100,'关联标准代码集'),valueDomainId:bounded(v.valueDomainId,64,'值域标识'),isNullable:Number(v.isNullable),dataLevel:string(v.dataLevel),dataCategoryId:category?.id||null,dataCategoryName:category?.name||null,dataCategoryPath:category?.path||null,qualityRule:bounded(Array.isArray(v.qualityRule)?v.qualityRule.join(','):v.qualityRule,1000,'关联质检规则'),customRule:bounded(v.customRule,500,'自定义规则'),publishStatus:0,updatedBy:getSession()?.principalId};
 if(!existing)body.createdBy=getSession()?.principalId;
 return body;
}
export function elementForm(row?:Row|null):Row {return row?{...row,qualityRule:string(row.qualityRule).split(',').filter(Boolean)}:{fieldType:'varchar',dataTypeId:'varchar',fieldLength:100,dataLevel:'2',isNullable:1,qualityRule:[]};}
export const canMaintainStandards=()=>getSession()?.authenticated===true;
export async function saveElement(values:Row,classes:Row[],existing?:Row|null) {
 if(existing&&(await readElement(existing.id)).status==='PUBLISHED')throw new Error('数据元已经发布，请先下线再修改');
 const body=elementBody(values,classes,existing);
 const result=elementRow(await platformApi<Row>('/dwm/standard/element/metaSaveOrUpdate',body));
 if(!result.id)throw new Error('保存接口未返回数据元标识，请刷新列表核实结果');changed();return result;
}
export async function setElementPublished(row:Row,published:boolean) {
 const current=await readElement(row.id);if(current.status!==(published?'PENDING':'PUBLISHED'))throw new Error('标准状态已变化，请刷新后重试');
 const saved=elementRow(await platformApi<Row>('/dwm/standard/element/updatePublishStatus',{tid:row.id,publishStatus:published?1:0,expectedPublishStatus:current.publishStatus,updatedBy:getSession()?.principalId}));
 if(saved.status!==(published?'PUBLISHED':'PENDING'))throw new Error('发布状态未更新，请刷新核实');changed();return saved;
}
export async function deleteElement(row:Row) {if((await readElement(row.id)).status==='PUBLISHED')throw new Error('请先下线再删除');const references=(await loadLanding()).items.filter(item=>item.target_id===row.id);if(references.length)throw new Error(`该数据元仍被 ${references.length} 个字段引用，请先解除引用`);await platformApi('/dwm/standard/element/meatDeleteById',{tid:row.id});changed();}

export function parseCodeItems(value:unknown):Row[] {
 let parsed:unknown=value;if(typeof value==='string'){try{parsed=JSON.parse(value||'[]');}catch{throw new Error('代码条目格式损坏，当前内容不会被空数据覆盖');}}
 if(parsed==null)return [];if(!Array.isArray(parsed))throw new Error('代码条目不是有效列表');
 return parsed.map((r:Row)=>({value:string(r.code??r.value),label:string(r.name??r.label),parent:string(r.parent??r.parentCode),description:string(r.description)}));
}
export function codeRow(raw:Row):Row {return {id:string(raw.tid),name:string(raw.dictName??raw.dict_name),code:string(raw.dictCode??raw.dict_code),description:string(raw.description),dataCategoryId:pick(raw,'dataCategoryId','data_category_id'),dataCategoryName:pick(raw,'dataCategoryName','data_category_name'),dataCategoryPath:pick(raw,'dataCategoryPath','data_category_path'),codeSet:pick(raw,'codeSet','code_set'),parentCode:pick(raw,'parentCode','parent_code'),sortNo:pick(raw,'sortNo','sort_no')??0,items:parseCodeItems(pick(raw,'dictItemValue','dict_item_value')),status:Number(raw.status)===0?'DRAFT':'PUBLISHED',updatedAt:pick(raw,'updatedTime','updated_time')};}
export async function listCodes(signal?:AbortSignal):Promise<Row[]> {const raw=await platformApi<Row[]>('/dwm/standard/code/list',{}, {signal});if(!Array.isArray(raw))throw new Error('代码集列表返回格式不正确');return raw.map(codeRow);}
export async function readCode(id:string) {const row=codeRow(await platformApi<Row>('/dwm/standard/code/queryById',{tid:id}));if(row.id!==id)throw new Error('代码集不存在或无权访问');return row;}
export function codeBody(values:Row,existing?:Row|null,classes:Row[]=[]):Row {
 const row={...existing,...values},category=classes.find(c=>c.id===row.dataCategoryId);
 if(row.dataCategoryId&&!category)throw new Error('请选择有效的标准分类');
 const items=((row.items||[]) as Row[]).map(item=>({...item})),codes=new Set<string>();
 for(const item of items){item.value=string(item.value).trim();item.label=string(item.label).trim();if(!item.value||!item.label)throw new Error('每项代码值和名称都必须填写');if(codes.has(item.value))throw new Error('代码值不能重复');codes.add(item.value);if(item.value.length>128||item.label.length>255||string(item.description).length>500)throw new Error('代码条目的长度超过原接口限制');}
 // The editor is flat. Preserve historical parent metadata without offering or validating hierarchy.
 const dictItemValue=JSON.stringify(items.map(i=>({code:i.value,name:i.label,description:string(i.description),...(i.parent?{parent:string(i.parent)}:{})})));
 return {tid:existing?.id||undefined,dictName:bounded(row.name,255,'代码集名称',true),dictCode:bounded(row.code,100,'代码集代码',true),codeSet:bounded(row.codeSet||row.code,100,'标准代码集编码',true),codeValue:'__HEADER__',codeName:bounded(row.name,255,'代码集名称',true),description:bounded(row.description,1000,'说明'),dataCategoryId:category?.id||null,dataCategoryName:category?.name||null,dataCategoryPath:category?.path||null,dictItemValue,parentCode:row.parentCode||null,sortNo:row.sortNo||0,status:row.status==='PUBLISHED'?1:0,updatedBy:getSession()?.principalId,...(!existing?{createdBy:getSession()?.principalId}:{})};
}
export async function saveCode(values:Row,classes:Row[],existing?:Row|null) {if(existing&&(await readCode(existing.id)).status==='PUBLISHED')throw new Error('代码集已发布，请先下线再编辑');const row=codeRow(await platformApi<Row>('/dwm/standard/code/saveOrUpdate',codeBody({...values,status:'DRAFT'},existing,classes)));if(!row.id)throw new Error('保存接口未返回代码集标识');changed();return row;}
export async function setCodePublished(row:Row,published:boolean,classes:Row[]) {const current=await readCode(row.id);if(current.status!==(published?'DRAFT':'PUBLISHED'))throw new Error('代码集状态已变化，请刷新');const result=codeRow(await platformApi<Row>('/dwm/standard/code/saveOrUpdate',codeBody({...current,status:published?'PUBLISHED':'DRAFT'},current,classes)));changed();return result;}
export async function deleteCode(row:Row) {if((await readCode(row.id)).status==='PUBLISHED')throw new Error('请先下线再删除代码集');const references=(await listElements()).filter(e=>e.valueDomainId===row.id);if(references.length)throw new Error(`该代码集仍被 ${references.length} 个数据元引用，请先解除引用`);await platformApi('/dwm/standard/code/deleteById',{tid:row.id});changed();}

export const loadLanding=(signal?:AbortSignal)=>platformApi<{items:Row[];entities:Row[];coverage:Row}>('/dwm/standard/landing/list',{}, {signal});
export const loadMetadataBindings=(signal?:AbortSignal)=>platformApi<{items:Row[];rules:Row[]}>('/dwm/metadata-governance/bindings/list',{}, {signal});
export async function saveBinding(values:Row) {const mode=values.mode||'STANDARD';if(!['STANDARD','QUALITY','REFERENCE'].includes(mode))throw new Error('无效的对标类型');const result=await platformApi(mode==='STANDARD'?'/dwm/standard/landing/save':'/dwm/metadata-governance/bindings/save',mode==='STANDARD'?{tableId:values.entityId,columnId:values.fieldId,standardId:values.targetId||null,expectedStandardId:values.expectedStandardId??null}:{tableId:values.entityId,columnId:values.fieldId,mode,targetId:values.targetId||null,expectedTargetId:values.expectedTargetId??null});changed();return result;}

export type EncodingSegment={type:'LITERAL'|'DIGITS'|'LETTERS'|'ALPHANUMERIC';value?:string;length?:number};
export function compileSegments(segments:EncodingSegment[]):{pattern:string;length:number} {
 if(!Array.isArray(segments)||!segments.length||segments.length>32)throw new Error('请填写 1—32 个编码段');
 let pattern='^',length=0;for(const segment of segments){if(segment.type==='LITERAL'){const literal=string(segment.value);if(!literal)throw new Error('固定文本不能为空');pattern+=literal.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');length+=literal.length;}else{if(!['DIGITS','LETTERS','ALPHANUMERIC'].includes(segment.type)||!Number.isInteger(segment.length)||Number(segment.length)<1||Number(segment.length)>128)throw new Error('编码段类型或长度不正确');pattern+=({DIGITS:'[0-9]',LETTERS:'[A-Za-z]',ALPHANUMERIC:'[A-Za-z0-9]'}[segment.type])+`{${segment.length}}`;length+=Number(segment.length);}}
 pattern+='$';if(pattern.length>500||length>65535)throw new Error('编码规则超过原字段容量');return {pattern,length};
}
/** Recover supported segments from the authoritative pattern, never from browser-only definitions. */
export function segmentsFromPattern(pattern:string):EncodingSegment[]|null {
 if(!pattern.startsWith('^')||!pattern.endsWith('$'))return null;
 const body=pattern.slice(1,-1),segments:EncodingSegment[]=[];let literal='';
 const flush=()=>{if(literal){segments.push({type:'LITERAL',value:literal});literal='';}};
 for(let i=0;i<body.length;){const token=/^(\[0-9\]|\\d|\[A-Za-z\]|\[A-Za-z0-9\])\{(\d+)\}/.exec(body.slice(i));if(token){flush();segments.push({type:token[1]==='[A-Za-z]'?'LETTERS':token[1]==='[A-Za-z0-9]'?'ALPHANUMERIC':'DIGITS',length:Number(token[2])});i+=token[0].length;continue;}if(body[i]==='\\'&&/[.*+?^${}()|[\]\\]/.test(body[i+1]||'')){literal+=body[i+1];i+=2;continue;}if(/[.*+?^${}()|[\]\\]/.test(body[i]))return null;literal+=body[i++];}flush();return segments;
}
export async function saveEncoding(values:Row,classes:Row[],existing?:Row|null) {
 const compiled=values.definition?.segments?compileSegments(values.definition.segments):{pattern:string(values.formatPattern).trim(),length:Number(existing?.fieldLength||100)};
 if(!compiled.pattern||compiled.pattern.length>500)throw new Error('请填写有效的编码格式规则');
 try {new RegExp(compiled.pattern);}catch{throw new Error('编码格式规则不是有效的正则表达式');}
 return saveElement({...values,fieldType:'varchar',dataTypeId:existing?.dataTypeId||'varchar',fieldLength:compiled.length,formatPattern:compiled.pattern,definition:values.description,standardEncode:values.standardEncode||values.code,dataLevel:values.dataLevel||'2',isNullable:values.isNullable??0},classes,existing);
}
export async function testEncoding(pattern:string,value:string):Promise<Row> {
 if(value.length>1024||pattern.length>500)throw new Error('校验输入或规则过长');
 // A worker timeout prevents a legacy/custom expression from blocking the UI.
 return new Promise((resolve,reject)=>{const worker=new Worker(new URL('./encodingValidation.worker.ts',import.meta.url),{type:'module'});const timer=setTimeout(()=>{worker.terminate();reject(new Error('格式规则执行超时，请简化规则'));},500);const end=()=>{clearTimeout(timer);worker.terminate();};worker.onmessage=e=>{end();e.data.error?reject(new Error(e.data.error)):resolve({valid:e.data.valid,value,pattern,message:e.data.valid?'符合当前格式规则':'不符合当前格式规则；此校验不发放编码，也不检查号码唯一性'});};worker.onerror=()=>{end();reject(new Error('无法执行格式校验'));};worker.postMessage({pattern,value});});
}
