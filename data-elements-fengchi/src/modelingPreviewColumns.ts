import type {Column,TableDef} from './data';
import type {ModelNode,ModelResult} from './modeling';

type PreviewKind='text'|'number'|'time'|'boolean'|'other';
export type PreviewColumn={key:string;comment?:string;type?:string;kind:PreviewKind};
type Meta=Pick<Column,'comment'|'type'>;
const timeValue=/^(?:\d{4}-\d{2}-\d{2}(?:[T ]\d{2}:\d{2}(?::\d{2}(?:\.\d+)?)?(?:Z|[+-]\d{2}:?\d{2})?)?|\d{2}:\d{2}:\d{2})$/;
function typeKind(type:string):PreviewKind{
 const t=type.trim().toLowerCase();
 if(/^(?:datetime|timestamp|date|time|timestamptz)(?:\b|\()/.test(t))return 'time';
 if(/^(?:tinyint|smallint|mediumint|int|integer|bigint|float|double|real|numeric|number|decimal|dec|money|smallmoney|serial|bigserial)(?:\b|\()/.test(t))return 'number';
 if(/^(?:bool|boolean|bit)(?:\b|\()/.test(t))return 'boolean';
 if(/^(?:n?varchar|n?char|character|text|ntext|string|tinytext|mediumtext|longtext|clob|varchar2)(?:\b|\()/.test(t))return 'text';
 return 'other';
}
/** Follow schema lineage for unchanged/projected/qualified fields; otherwise inspect actual result values. */
export function getPreviewColumns(result:ModelResult,nodes:readonly ModelNode[],tables:Record<string,TableDef>,nodeId?:string):PreviewColumn[]{
 const byId=new Map(nodes.map(n=>[n.id,n]));
 const lineage=(id:string|undefined,key:string,seen:Set<string>):Meta|undefined=>{
  if(!id||seen.has(id))return;
  const n=byId.get(id);if(!n)return;
  const next=new Set(seen).add(id),c=n.config;const left=(k=key)=>lineage(n.inputs[0],k,next);
  switch(n.kind){
   case 'source':case 'csvInput':case 'jsonInput':case 'xmlInput':case 'excelInput':case 'parquetInput': {
    const tableKey=n.kind==='source'&&c.inputMode==='sql'?c.mockSqlTable:c.table;
    return Object.hasOwn(tables,tableKey)?tables[tableKey]?.columns.find(col=>col.name===key):undefined;
   }
   case 'join':case 'crossJoin':case 'lookup':return key.startsWith('right.')?lineage(n.inputs[1],key.slice(6),next)??left():left();
   case 'rename':return key===c.name.trim()?left(c.field):key===c.field?undefined:left();
   case 'cast':return key===c.field?{comment:left()?.comment,type:c.type==='number'?'number':'string'}:left();
   case 'aggregate': {
    if(key===c.group)return left();
    if(key==='_count')return {comment:'记录数',type:'integer'};
    if(key==='_sum'){const source=left(c.field);return {comment:source?.comment?source.comment+'合计':'合计',type:source?.type&&typeKind(source.type)==='number'?source.type:'number'};}
    return undefined;
   }
   case 'select':return c.fields.split(',').some(field=>field.trim()===key)?left():undefined;
   case 'union': {const l=left(),r=lineage(n.inputs[1],key,next);return l&&r&&l.type.trim().toLowerCase()===r.type.trim().toLowerCase()?{type:l.type,comment:l.comment===r.comment?l.comment:undefined}:undefined;}
   case 'filter':case 'sort':case 'distinct':case 'limit':case 'sample':case 'exists':case 'intersection':case 'difference':case 'conditional':case 'assert':case 'output':case 'csvOutput':case 'jsonOutput':case 'xmlOutput':case 'excelOutput':case 'parquetOutput':
    return left();
   default:return undefined;
  }
 };
 return result.columns.map(key=>{
  const meta=lineage(nodeId,key,new Set());
  if(meta?.type)return {key,comment:meta.comment?.trim()||undefined,type:meta.type,kind:typeKind(meta.type)};
  const values=result.rows.map(row=>row[key]).filter(value=>value!==null&&value!==undefined);
  if(values.length&&values.every(value=>typeof value==='number'&&Number.isFinite(value)))return {key,type:'number',kind:'number'};
  if(values.length&&values.every(value=>typeof value==='string'&&timeValue.test(value)&&(value.length===8||!Number.isNaN(Date.parse(value)))))return {key,type:values.every(value=>String(value).length===10)?'date':values.every(value=>String(value).length===8)?'time':'datetime',kind:'time'};
  if(values.length&&values.every(value=>typeof value==='string'))return {key,type:'string',kind:'text'};
  return {key,kind:'other'};
 });
}
