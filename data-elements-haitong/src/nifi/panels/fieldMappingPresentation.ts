import type { FieldMeta, MappingRule, UnifiedMappingDraft, UnifiedMappingRule } from '../api/fieldMapping';
import {logicalFieldType} from './unifiedRuleModel.ts';

export type FieldSide='source'|'target';
export type FieldType='STRING'|'INT'|'DECIMAL'|'DATETIME'|'DATE'|'BOOL'|'JSON'|'BLOB'|'GEO';
export interface FieldInfo {name:string;path:string;label?:string;type:FieldType;fullType?:string;isPk?:boolean;nullable?:boolean}
export interface LocalRecommendation {id:string;from:string;fromLabel?:string;to:string;toLabel?:string;expression?:string;comment?:string;reason:string;confidence:number}
export function normalizePath(value:string){const raw=(value??'').trim();if(!raw)return '';if(raw.startsWith('/'))return raw;return '/'+raw.replace(/^root\./,'').replaceAll('.','/');}
export function displayPath(value?:string){return (value??'').trim().replace(/^\//,'');}
export function fieldName(path:string){return displayPath(path).split('/').pop()??displayPath(path);}
export function fieldKey(path:string){return fieldName(path).trim().toLowerCase();}
export function toFieldInfo(path:string,label?:string):FieldInfo {
 const name=fieldName(path),lower=name.toLowerCase();
 const type:FieldType=lower.includes('time')||lower.includes('date')?'DATETIME':lower==='id'||lower.endsWith('_id')||lower==='tid'||lower.includes('count')||lower.includes('num')||lower.includes('age')?'INT':lower.startsWith('is_')||lower.includes('enabled')?'BOOL':'STRING';
 return {name,path:normalizePath(path),label:label||undefined,type,isPk:['id','tid'].includes(lower)};
}
export function mergeFields(a:FieldInfo[],b:FieldInfo[]){const map=new Map<string,FieldInfo>();[...a,...b].forEach(field=>{if(field.path)map.set(field.path,field);});return Array.from(map.values()).sort((a,b)=>a.name.localeCompare(b.name,'zh-CN'));}
export function fieldsForPresentation(meta:FieldMeta[],labels:Record<string,string>,names:string[],custom:FieldInfo[]=[]):FieldInfo[] {
 const inferred=names.map(name=>toFieldInfo(name,labels[fieldName(name)]));
 const fields=meta.map(field=>{
  const logical=logicalFieldType(field);
  const type:FieldType=['INT','LONG','DOUBLE'].includes(logical)?'INT':logical==='BOOLEAN'?'BOOL':logical==='ARRAY'?'JSON':(['STRING','DECIMAL','DATETIME','DATE','JSON','BLOB','GEO'].includes(logical)?logical:'STRING') as FieldType;
  return {...toFieldInfo(field.name,labels[field.name]),type,fullType:field.type??logical,isPk:field.isPk,nullable:field.nullable};
 });
 return mergeFields(mergeFields(mergeFields(inferred,Object.keys(labels).map(name=>toFieldInfo(name,labels[name]))),fields),custom);
}
export function sourceNames(rule:UnifiedMappingRule):string[] {
 const names=new Set<string>();
 function walk(value:unknown,depth=0){
  if(depth>32||value==null)return;
  if(typeof value==='string'){for(const match of value.matchAll(/\$\{field:([^}]+)}/g))names.add(match[1]);return;}
  if(Array.isArray(value)){value.forEach(v=>walk(v,depth+1));return;}
  if(typeof value==='object'){const node=value as Record<string,unknown>;if(node.kind==='FIELD'&&typeof node.name==='string'&&node.name)names.add(node.name);Object.values(node).forEach(v=>walk(v,depth+1));}
 }
 walk(rule.input);walk(rule.steps);walk(rule.when);walk(rule.then);walk(rule.else);
 return [...names];
}
/** Read-only projection used by the original visual layout, never serialized as the draft. */
export function visualMapping(rule:UnifiedMappingRule):MappingRule {
 const sources=sourceNames(rule),step=rule.steps.find(s=>!s.uiRole);
 return {to:normalizePath(rule.target.name),enabled:rule.enabled,...(sources.length?{from:normalizePath(sources[0]),...(sources.length>1?{fromList:sources.map(normalizePath)}:{})}:{}),...(rule.input.kind==='LITERAL'?{constant:rule.input.value as MappingRule['constant']}:{}),...(rule.input.kind==='EXPRESSION'?{expression:rule.input.expression}:{}),...(rule.when!=null?{when:typeof rule.when==='string'?rule.when:'条件分支'}:{}),...(step?{transform:{fn:step.operator==='FUNCTION'?String(step.args?.function??''):step.operator}}:{})};
}
export function patchVisualField(rule:UnifiedMappingRule,side:FieldSide,path:string,meta:FieldMeta[]):UnifiedMappingRule {
 const name=displayPath(path);
 if(side==='source')return {...rule,input:{kind:'FIELD',name}};
 const field=meta.find(f=>f.name===name);
 return {...rule,target:{...rule.target,name,...(field?{logicalType:logicalFieldType(field),nullable:field.nullable,length:field.length,precision:field.precision,scale:field.scale}:{})}};
}
export function mergeMappingRules(draft:UnifiedMappingDraft,rules:UnifiedMappingRule[]):UnifiedMappingDraft {
 const targets=new Set(draft.rules.map(rule=>rule.target.name));
 return {...draft,rules:[...draft.rules,...rules.filter(rule=>{if(targets.has(rule.target.name))return false;targets.add(rule.target.name);return true;})]};
}
