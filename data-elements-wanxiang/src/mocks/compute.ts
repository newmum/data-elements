import type { Entity, Evidence, Relationship } from '../features/er/types/domain';
import { MockError, type LocalWorkspace, type MockRow } from './types';
export const tuple = (row: MockRow, fields: string[]) => fields.map(n => row[n]);
export const keyOf = (value: unknown[]) => JSON.stringify(value);
const isEmpty = (v: unknown) => v === null || v === undefined || v === '';
export function decimalCompare(a: unknown, b: unknown): number {
  const parse = (v: unknown) => { const s=String(v).trim();if(!/^[+-]?\d+(?:\.\d+)?$/.test(s))throw new MockError(422,'INVALID_DECIMAL','数值格式不正确'); const negative=s.startsWith('-'),[i,f='']=s.replace(/^[+-]/,'').split('.');return {n:BigInt(i+f)*(negative?-1n:1n),scale:f.length};};
  const x=parse(a),y=parse(b),scale=Math.max(x.scale,y.scale);const l=x.n*10n**BigInt(scale-x.scale),r=y.n*10n**BigInt(scale-y.scale);return l<r?-1:l>r?1:0;
}
function fieldsOf(w: LocalWorkspace, entityId: string, ids: string[]): { entity: Entity; names: string[] } {
  const entity=w.entities.find(e=>e.id===entityId);if(!entity)throw new MockError(404,'ENTITY_NOT_FOUND','找不到对应实体');
  if(!Array.isArray(ids)||!ids.length||new Set(ids).size!==ids.length)throw new MockError(422,'FIELDS_REQUIRED','请选择不重复的字段');
  const names=ids.map(id=>{const f=entity.fields.find(x=>x.id===id);if(!f)throw new MockError(422,'FIELD_NOT_FOUND','字段不属于选中的实体');return f.name;});return {entity,names};
}
export function profileDataset(w: LocalWorkspace, input: MockRow, now: string): MockRow {
  const {entity,names}=fieldsOf(w,input.entityId,input.fieldIds);const all=w.records[entity.id]??[],max=Math.min(100000,Math.max(1,Number(input.budget?.maxRows??1000))),rows=all.slice(0,max);
  return {entity_id:entity.id,status:'COMPLETE',observation_json:{method:'本地数据集前N条观测',complete:rows.length===all.length,sampleRows:String(rows.length),observedAt:now,source:'LOCAL_DATASET'},metrics_json:names.map(name=>{const vals=rows.map(r=>r[name]),nonNull=vals.filter(v=>v!==null&&v!==undefined);return {name,rows:String(rows.length),nullCount:String(vals.length-nonNull.length),emptyCount:String(vals.filter(v=>v==='').length),distinctCount:String(new Set(nonNull.map(v=>JSON.stringify(v))).size),nullRatio:rows.length?(vals.length-nonNull.length)/rows.length:null};})};
}
export function evaluateRule(w: LocalWorkspace, rule: MockRow, budget: MockRow, now: string): MockRow {
  const def=rule.definition??{}, {entity,names}=fieldsOf(w,def.entityId,def.fieldIds);const all=w.records[entity.id]??[],max=Math.min(100000,Math.max(1,Number(budget.maxRows??1000))),rows=all.slice(0,max),complete=rows.length===all.length;
  let eligible=0,violations=0;const counts=new Map<string,number>();
  if(rule.type==='UNIQUE')for(const row of rows){const values=tuple(row,names);if(values.some(isEmpty)&&def.nullPolicy!=='VIOLATION')continue;const key=keyOf(values);counts.set(key,(counts.get(key)??0)+1);}
  let targetKeys:Set<string>|null=null;
  if(rule.type==='REFERENCE'){const rel=w.relationships.find(r=>r.id===def.relationshipId&&r.reviewStatus==='confirmed');if(!rel)throw new MockError(422,'RELATION_REQUIRED','引用规则需要已确认的关系');if(rel.conditions?.length||rel.arraySemantics&&rel.arraySemantics!=='none')throw new MockError(422,'LOCAL_VALIDATION_UNSUPPORTED','本地引用检查暂不支持条件或数组展开');const target=w.entities.find(e=>e.id===rel.targetEntityId)!;targetKeys=new Set((w.records[target.id]??[]).map(r=>keyOf(rel.mappings!.map(m=>r[target.fields.find(f=>f.id===m.targetFieldId)!.name]))));}
  for(const row of rows){const values=tuple(row,names),empty=values.some(isEmpty);if(empty&&rule.type!=='NOT_NULL'&&def.nullPolicy!=='VIOLATION')continue;eligible++;let bad=empty;
    if(!empty||rule.type==='NOT_NULL')switch(rule.type){
      case 'NOT_NULL':bad=empty;break;
      case 'UNIQUE':bad=(counts.get(keyOf(values))??0)>1;break;
      case 'ENUM':bad=!def.values?.some((v:unknown)=>String(v)===String(values[0]));break;
      case 'RANGE':try{bad=(def.min!==undefined&&def.min!==''&&decimalCompare(values[0],def.min)<0)||(def.max!==undefined&&def.max!==''&&decimalCompare(values[0],def.max)>0);}catch{bad=true;}break;
      case 'LENGTH':{const n=Array.from(String(values[0])).length;bad=(def.min!==undefined&&def.min!==''&&n<Number(def.min))||(def.max!==undefined&&def.max!==''&&n>Number(def.max));break;}
      case 'REGEX':{const patterns:Record<string,RegExp>={EMAIL:/^[^\s@]+@[^\s@]+\.[^\s@]+$/,PHONE:/^\+?[\d\s-]{7,20}$/,ALPHANUMERIC:/^[a-z\d]+$/i};const re=patterns[def.preset];if(!re)throw new MockError(422,'FORMAT_UNSUPPORTED','请选择支持的预设格式');bad=!re.test(String(values[0]));break;}
      case 'TIMELINESS':{const time=Date.parse(String(values[0])),age=Date.parse(now)-time;bad=!Number.isFinite(time)||age<0||age>Number(def.maxAgeSeconds)*1000;break;}
      case 'REFERENCE':{const rel=w.relationships.find(r=>r.id===def.relationshipId)!;bad=!targetKeys!.has(keyOf(rel.mappings!.map(m=>row[entity.fields.find(f=>f.id===m.sourceFieldId)!.name])));break;}
      default:throw new MockError(422,'RULE_UNSUPPORTED','不支持的质量规则模板');
    }
    if(bad)violations++;
  }
  const status=budget.mode==='FULL'&&!complete?'INCOMPLETE':!eligible?'NO_DATA':violations?'VIOLATIONS_FOUND':complete?'PASSED':'SAMPLE_PASSED';
  return {entity_id:entity.id,rule_id:rule.id,rule_revision_id:rule.revisionId,status,eligible_rows:String(eligible),violating_rows:String(violations),score:eligible?Number((100*(1-violations/eligible)).toFixed(1)):null,observation_json:{method:'本地数据集有界检查',source:'LOCAL_DATASET',complete,sampleRows:String(rows.length),totalRows:String(all.length),observedAt:now},details_json:{rule:rule.name,template:rule.type,fields:names,eligibleRows:String(eligible),violationRows:String(violations),nullPolicy:def.nullPolicy??'IGNORE'}};
}
export function validateDataset(w: LocalWorkspace, rel: Relationship, limit: number, now: string, id: string): Evidence {
  if(rel.conditions?.length || (rel.arraySemantics && rel.arraySemantics!=='none'))throw new MockError(422,'LOCAL_VALIDATION_UNSUPPORTED','当前本地验证仅支持无条件的标量等值关系；此关系可保存，但不能直接执行数据验证');
  const {entity:source,names:sn}=fieldsOf(w,rel.sourceEntityId,rel.mappings!.map(m=>m.sourceFieldId));
  const {entity:target,names:tn}=fieldsOf(w,rel.targetEntityId,rel.mappings!.map(m=>m.targetFieldId));
  const sourceAll=w.records[source.id],targetAll=w.records[target.id];
  if(!sourceAll||!targetAll)throw new MockError(422,'DATASET_UNAVAILABLE','当前结构没有可供验证的本地数据集');
  const sampled=sourceAll.slice(0,Math.max(1,Math.min(limit,5000)));const rows=sampled.filter(r=>tuple(r,sn).every(v=>v!==null&&v!==undefined));
  const targets=targetAll.filter(r=>tuple(r,tn).every(v=>v!==null&&v!==undefined));const targetKeys=new Set(targets.map(r=>keyOf(tuple(r,tn)))),sourceKeys=new Set(rows.map(r=>keyOf(tuple(r,sn))));
  const matched=rows.filter(r=>targetKeys.has(keyOf(tuple(r,sn)))).length,matchedDistinct=[...sourceKeys].filter(k=>targetKeys.has(k)).length,unique=targetKeys.size===targets.length,complete=sampled.length===sourceAll.length;
  const status=!rows.length?'inconclusive':matched<rows.length||!unique?'violations_found':complete?'full_supported':'sample_supported';
  return {id,relationshipId:rel.id,snapshotId:'local-dataset',kind:'data',summary:'在当前浏览器数据集上进行完整元组比较，不访问外部数据库。',capturedAt:now,simulated:true,validationStatus:status,sampleMethod:`本地数据集：源端前 ${sampled.length} 行，目标端完整键域`,targetDomain:'full',targetUniquenessBasis:unique?'full_exact':'duplicate_found',sourceObservedAt:now,targetObservedAt:now,ruleVersion:'local-reference-1',metrics:{sourceEligibleRows:String(rows.length),sourceDistinctTuples:String(sourceKeys.size),targetEligibleRows:String(targets.length),targetDistinctTuples:String(targetKeys.size),matchedDistinctTuples:String(matchedDistinct),orphanRows:String(rows.length-matched),containmentRatio:sourceKeys.size?matchedDistinct/sourceKeys.size:null,rowMatchRatio:rows.length?matched/rows.length:null,targetUniquenessRatio:targets.length?targetKeys.size/targets.length:null},warnings:[...(!unique?['目标键在本地数据集中有重复']:[]),...(!complete?['结果仅覆盖本次源端观测范围']:[]),'结果仅用于当前本地数据集，不代表任何外部数据库的验证结论。']};
}
