import type { Entity, FieldDef, Relationship } from '../../features/er/types/domain';
import type { Binding, DesignField, LogicalModel, ModelContent, FrozenModel, LogicalType } from './types';
import { ResourceValidationError } from '../services/transport';
import { validateRelation } from '../../features/er/core/relations';
export function requireValue(condition:unknown,message:string,code='VALIDATION'):asserts condition{if(!condition)throw new ResourceValidationError(422,code,message);}
export function label(value:unknown,name:string,max=128){requireValue(typeof value==='string'&&value.trim().length>0&&value.trim().length<=max,`${name}不能为空，且不超过${max}字`);return value.trim();}
export const normalize=(s:string)=>s.normalize('NFKC').trim().toUpperCase();
export function hash(value:unknown){const s=JSON.stringify(value);let h=2166136261;for(let i=0;i<s.length;i++)h=Math.imul(h^s.charCodeAt(i),16777619);return (h>>>0).toString(16);}
export function checkVersion(v:{version:number},expected:unknown){if(expected==null)throw new ResourceValidationError(428,'VERSION_REQUIRED','请刷新后重试');if(v.version!==Number(expected))throw new ResourceValidationError(412,'VERSION_CONFLICT','记录已在另一页面更新，当前修改未覆盖新版本。');}
export function bindingHash(b:Binding,source:{version?:number;connection?:unknown;status?:string}){return hash([b.databaseId,b.environment,b.sourceId,b.catalog,b.schema,b.prefix,b.profile,b.category,source.connection,source.status]);}
export const contentHash=(m:ModelContent)=>hash({entities:m.entities,relationships:m.relationships,outputEntityId:m.outputEntityId,fusion:m.fusion});
export function fieldSignature(f:FieldDef){return hash([f.id,f.name,f.nativeType,f.typeFamily,f.length,f.precision,f.scale,f.nullable]);}
export function identifier(v:string){requireValue(/^[A-Za-z][A-Za-z0-9_]{0,62}$/.test(v),'技术名称须以字母开头，仅含字母、数字、下划线，最多63字符');requireValue(!['SELECT','TABLE','CREATE','DROP','FROM','WHERE','USER','ORDER','GROUP'].includes(v.toUpperCase()),'技术名称不能使用保留字');return v;}
export const logicalTypes:LogicalType[]=['String','Text','Int32','Int64','Decimal','Boolean','Date','Timestamp','Binary','UUID','JSON','Array','Object'];
export function logicalType(f:FieldDef):LogicalType {const map:Record<string,LogicalType>={integer:/INT\b/i.test(f.nativeType)&&!f.nativeType.includes('BIG')?'Int32':'Int64',decimal:'Decimal',float:'Decimal',string:'String',boolean:'Boolean',date:'Date',timestamp:'Timestamp',uuid:'UUID',objectid:'String',binary:'Binary',array:'Array',object:'JSON'};return map[f.typeFamily]??'String';}
export function fieldFamily(t:LogicalType):FieldDef['typeFamily'] {return ({String:'string',Text:'string',Int32:'integer',Int64:'integer',Decimal:'decimal',Boolean:'boolean',Date:'date',Timestamp:'timestamp',Binary:'binary',UUID:'uuid',JSON:'object',Array:'array',Object:'object'} as const)[t];}
/** Key uniqueness here is a design intention, never a claim about an external database. */
export function validateDesignRelation(r:Relationship,m:ModelContent,existing:Relationship[]=[],exceptId?:string):string[]{
 const errors=validateRelation(r,{id:'design',entities:m.entities,sources:[],capturedAt:''},existing,exceptId);
 if(r.semanticType==='reference'){
  const target=m.entities.find(e=>e.id===r.targetEntityId);const targets=new Set(r.mappings?.map(p=>p.targetFieldId)??[]);
  const keys=target?.keys?.filter(k=>k.scope!=='partial'&&(k.kind==='primary'||k.kind==='unique'))??[];
  if(!keys.some(k=>k.fieldIds.length===targets.size&&k.fieldIds.every(id=>targets.has(id))))errors.push('唯一目标引用须匹配完整设计主键或唯一键；否则使用一般业务关联。');
 }
 return [...new Set(errors)];
}
export function validateModel(m:ModelContent){
 requireValue(m.entities.length>0,'至少建立一个设计实体');requireValue(m.entities.length<=300,'单模型最多300实体');const names=new Set<string>(),ids=new Set<string>();
 for(const e of m.entities){identifier(e.name);requireValue(!names.has(normalize(e.name)),'模型内技术实体名重复');names.add(normalize(e.name));requireValue(!ids.has(e.id),'设计实体ID重复');ids.add(e.id);requireValue(e.fields.length>0,`${e.name} 至少需要一个字段`);const fnames=new Set<string>();for(const f of e.fields){identifier(f.name);requireValue(!fnames.has(normalize(f.name)),`${e.name} 字段技术名重复`);fnames.add(normalize(f.name));requireValue(!ids.has(f.id),'字段ID重复');ids.add(f.id);requireValue(logicalTypes.includes(f.logicalType),'逻辑类型无效');if(f.logicalType==='String')requireValue(Number.isInteger(f.length)&&Number(f.length)>0&&Number(f.length)<=4000,'字符长度范围1–4000');if(f.logicalType==='Decimal')requireValue(Number.isInteger(f.precision)&&Number.isInteger(f.scale)&&Number(f.precision)>0&&Number(f.precision)<=38&&Number(f.scale)>=0&&Number(f.scale)<=Number(f.precision),'Decimal 精度1–38，小数位不得超过精度');}for(const k of e.keys??[]){requireValue(k.fieldIds.length>0&&new Set(k.fieldIds).size===k.fieldIds.length&&k.fieldIds.every(id=>e.fields.some(f=>f.id===id)),'设计键成员无效');}}
 for(const r of m.relationships){const errors=validateDesignRelation(r,m);requireValue(!errors.length,errors.join('；'));}
 if(m.outputEntityId||m.fusion){
  const output=m.entities.find(e=>e.id===m.outputEntityId);
  requireValue(!!output&&output.role==='OUTPUT','模型须指定唯一输出表 D');
  requireValue(m.entities.filter(e=>e.role==='OUTPUT').length===1,'一个模型版本只能有一张输出表');
  const sources=m.entities.filter(e=>e.role==='SOURCE');
  const joins=m.fusion?.joins??[],mappings=m.fusion?.mappings??[];
  requireValue(!!m.fusion,'模型缺少融合设计');
  if(sources.length){
   requireValue(sources.some(e=>e.id===m.fusion!.baseEntityId),'请选择来源驱动表和输出粒度');
   const joinedRight=new Set<string>();
   for(const j of joins){const left=sources.find(e=>e.id===j.leftEntityId),right=sources.find(e=>e.id===j.rightEntityId);requireValue(!!left&&!!right&&left.id!==right.id,'融合关联只能连接两张不同的来源表');requireValue(!joinedRight.has(right.id)&&right.id!==m.fusion!.baseEntityId,'每张接入表只能有一条入向关联');joinedRight.add(right.id);requireValue(['INNER','LEFT'].includes(j.joinType)&&['ONE','MANY'].includes(j.rightCardinality)&&['NONE','LATEST'].includes(j.dedupe),'关联方式或基数无效');requireValue(j.pairs.length>0&&j.pairs.every(p=>left.fields.some(f=>f.id===p.leftFieldId)&&right.fields.some(f=>f.id===p.rightFieldId)),'融合关联键无效');for(const pair of j.pairs){const leftField=left.fields.find(f=>f.id===pair.leftFieldId),rightField=right.fields.find(f=>f.id===pair.rightFieldId);requireValue(leftField?.logicalType===rightField?.logicalType,'融合关联字段类型不一致，请调整字段或关联键');}requireValue(j.rightCardinality!=='ONE'||j.dedupe==='NONE','唯一右表无需去重');requireValue(j.rightCardinality!=='MANY'||j.dedupe==='LATEST','右表可能多条时须按排序字段取最新一条');requireValue(j.dedupe!=='LATEST'||!!j.orderFieldId&&right.fields.some(f=>f.id===j.orderFieldId),'取最新记录须指定排序字段');}
   const reached=new Set([m.fusion!.baseEntityId]);
   for(let i=0;i<joins.length;i++)for(const j of joins){if(!reached.has(j.leftEntityId)||reached.has(j.rightEntityId))continue;const left=sources.find(e=>e.id===j.leftEntityId),right=sources.find(e=>e.id===j.rightEntityId);requireValue(!!left&&!!right,'融合关联只能连接来源表');requireValue(j.pairs.length>0&&j.pairs.every(p=>left.fields.some(f=>f.id===p.leftFieldId)&&right.fields.some(f=>f.id===p.rightFieldId)),'融合关联键无效');requireValue(j.dedupe!=='LATEST'||!!j.orderFieldId&&right.fields.some(f=>f.id===j.orderFieldId),'取最新记录须指定排序字段');reached.add(j.rightEntityId);}
   requireValue(sources.every(e=>reached.has(e.id)),'所有来源表必须通过融合关联接入驱动表');
   const targets=new Set<string>();
   for(const map of mappings){const source=sources.find(e=>e.id===map.sourceEntityId),sourceField=source?.fields.find(f=>f.id===map.sourceFieldId),targetField=output!.fields.find(f=>f.id===map.targetFieldId);requireValue(!!sourceField,'字段映射来源无效');requireValue(!!targetField,'字段映射目标无效');requireValue(!targets.has(map.targetFieldId),'每个输出字段只能有一条直接映射');requireValue(sourceField.logicalType===targetField.logicalType,`${targetField.name} 的来源与目标类型不一致`);requireValue(map.transform==='DIRECT'||['String','Text'].includes(sourceField.logicalType),'文本转换只适用于字符字段');targets.add(map.targetFieldId);}
   requireValue(output!.fields.every(f=>targets.has(f.id)),'输出表仍有未建立来源映射的字段');
  }
 }
 requireValue(m.nodes.every(n=>m.entities.some(e=>e.id===n.entityId)&&Number.isFinite(n.position.x)&&Number.isFinite(n.position.y)),'画布对象或坐标无效');
}
export function physicalType(f:DesignField,engine:string):string{
 const t=f.logicalType;requireValue(['DM8','MYSQL','POSTGRESQL','ORACLE','SQLSERVER','MARIADB','ICEBERG'].includes(engine),'此目标类型不支持前端建表预览，请以服务端预检为准');
 if(t==='String')return engine==='ORACLE'?`VARCHAR2(${f.length??128})`:`VARCHAR(${f.length??128})`;
 if(t==='Text')return ['ORACLE','DM8'].includes(engine)?'CLOB':engine==='SQLSERVER'?'NVARCHAR(MAX)':'TEXT';
 if(t==='Int64')return engine==='ORACLE'?'NUMBER(19)':'BIGINT';if(t==='Int32')return 'INTEGER';
 if(t==='Decimal')return `DECIMAL(${f.precision??18},${f.scale??2})`;
 if(t==='Boolean')return ['SQLSERVER','DM8'].includes(engine)?'BIT':engine==='ORACLE'?'NUMBER(1)':'BOOLEAN';
 if(t==='Date')return 'DATE';if(t==='Timestamp')return engine==='SQLSERVER'?'DATETIME2':'TIMESTAMP';
 if(t==='UUID')return engine==='POSTGRESQL'?'UUID':'CHAR(36)';
 if(t==='Binary')return engine==='POSTGRESQL'?'BYTEA':engine==='SQLSERVER'?'VARBINARY(MAX)':'BLOB';
 if(t==='JSON'&&['MYSQL','POSTGRESQL','MARIADB'].includes(engine))return engine==='POSTGRESQL'?'JSONB':'JSON';
 throw new ResourceValidationError(422,'TYPE_UNSUPPORTED',`${f.name} 的 ${t} 尚无 ${engine} 无损映射，请调整设计类型或目标`);
}
export function buildDDL(rev:FrozenModel,b:Binding,engine:string,names:Record<string,string>){
 const quote=(s:string)=>{identifier(s);return engine==='MYSQL'||engine==='MARIADB'?`\`${s}\``:engine==='SQLSERVER'?`[${s}]`:`"${s}"`;};
 return '-- 结构预览；不会执行外部数据库命令。正式使用前请审核方言、权限与命名。\n'+rev.entities.filter(e=>!e.reference&&names[e.id]).map(e=>{
 const ns=b.schema||b.catalog;const full=(ns?quote(ns)+'.':'')+quote(names[e.id]);
 const fields=e.fields.map(f=>`  ${quote(f.name)} ${physicalType(f,engine)}${f.nullable==='false'?' NOT NULL':''}`);
 for(const k of e.keys??[])if(k.kind==='primary')fields.push(`  PRIMARY KEY (${k.fieldIds.map(id=>quote(e.fields.find(f=>f.id===id)!.name)).join(', ')})`);
 return `CREATE TABLE ${full} (\n${fields.join(',\n')}\n);`;
 }).join('\n\n');
}
export function allowsReference(modelId:string,targetId:string,models:LogicalModel[],visited=new Set<string>()):boolean{if(modelId===targetId)return false;if(visited.has(targetId))return true;visited.add(targetId);const target=models.find(m=>m.id===targetId);return (target?.entities??[]).filter(e=>e.reference).every(e=>allowsReference(modelId,e.reference!.modelId,models,visited));}
export function csvCell(v:unknown){let s=String(v??'');if(/^[\s]*[=+\-@\t\r]/.test(s))s="'"+s;return '"'+s.replace(/"/g,'""')+'"';}
export function nextCycle(time:string,frequency:string){const d=new Date(time);if(frequency==='DAILY')d.setUTCDate(d.getUTCDate()+1);else if(frequency==='WEEKLY')d.setUTCDate(d.getUTCDate()+7);else if(frequency==='MONTHLY'){const day=d.getUTCDate();d.setUTCDate(1);d.setUTCMonth(d.getUTCMonth()+1);const end=new Date(Date.UTC(d.getUTCFullYear(),d.getUTCMonth()+1,0)).getUTCDate();d.setUTCDate(Math.min(day,end));}else return undefined;return d.toISOString();}
export function projectionValid(structure:Entity,current:Entity|undefined,fields:string[]){return !!current&&fields.length>0&&fields.every(id=>{const before=structure.fields.find(f=>f.id===id),after=current.fields.find(f=>f.id===id);return !!before&&!!after&&fieldSignature(before)===fieldSignature(after);});}
