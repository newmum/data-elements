import { metadataScope, metadataWritable, readMetadata } from './metadata';
import { entityIdentity, sourceIdentity } from './metadataSnapshot';
import { changed, type Row } from './api';
/** The original wanxiang2 stores diagram presentation locally, not in a model master API.
 * Only names, layout and canonical IDs live here. Sources/fields/relations never do. */
export const modelViewKey=()=>`wanxiang:shared-metadata-views:v1:${metadataScope()}`;
export function listModelViews():Row[]{const value=localStorage.getItem(modelViewKey());if(!value)return [];let parsed;try{parsed=JSON.parse(value);}catch{throw new Error('本地模型布局无法读取，原布局未被覆盖');}if(!Array.isArray(parsed)||parsed.some(r=>!r||typeof r.id!=='string'||!r.layout||!Array.isArray(r.layout.nodes)))throw new Error('本地模型布局格式不正确，原布局未被覆盖');return parsed;}
function persist(rows:Row[]){try{localStorage.setItem(modelViewKey(),JSON.stringify(rows));}catch{throw new Error('本地模型布局未能保存，请检查浏览器存储空间');}changed();}
export function saveModelView(values:Row,id?:string,expectedVersion?:number):Row{
 const rows=listModelViews(),existing=id?rows.find(r=>r.id===id):undefined;
 if(id&&id!=='metadata-default-view'&&!existing)throw new Error('模型视图不存在');
 if(existing&&expectedVersion!==undefined&&Number(existing.version)!==expectedVersion)throw new Error('模型布局已在其他窗口修改，请刷新后重试');
 const name=String(values.name||existing?.name||'').trim();if(!name||name.length>128)throw new Error('请填写 1 至 128 字的模型名称');
 const code=String(values.code??existing?.code??'').trim();if(code.length>64)throw new Error('模型代码不能超过 64 个字符');if(code&&rows.some(r=>r.id!==id&&r.code===code))throw new Error('模型代码已存在');
 if((values.layout?.nodes?.length||0)>20000)throw new Error('单个模型最多展示 20000 张表，请按业务范围拆分');
 const row={...existing,id:id||crypto.randomUUID(),name,code,domain:String(values.domain??existing?.domain??''),description:String(values.description??existing?.description??''),sourceId:values.sourceId??existing?.sourceId,version:Number(existing?.version||0)+1,layout:values.layout||existing?.layout||{nodes:[]},updatedAt:new Date().toISOString(),storage:'BROWSER_LAYOUT'};
 persist([...rows.filter(r=>r.id!==row.id),row]);return row;
}
export function removeModelView(id:string,expectedVersion?:number){const rows=listModelViews(),row=rows.find(r=>r.id===id);if(!row)throw new Error('模型视图不存在');if(expectedVersion!==undefined&&Number(row.version)!==expectedVersion)throw new Error('模型已修改，请刷新后再删除');persist(rows.filter(r=>r.id!==id));}
export async function modelViewApi(path:string,options:{method:string;version?:number;body?:Row}):Promise<Row>{const id=path.split('/')[1];if(options.method==='DELETE'){removeModelView(id,options.version);return {id};}return saveModelView(options.body||{},options.method==='PUT'?id:undefined,options.version);}

/** Reuse one source model and preserve positions while including every currently collected table. */
export async function buildSourceModelView(sourceId:string):Promise<{model:Row;tableCount:number;relationCount:number}>{
 if(!metadataWritable())throw new Error('请先登录后构建模型');
 const catalog=await readMetadata();
 const source=catalog.sources.find(item=>item.tid===sourceId);
 if(!source)throw new Error('数据源已不存在，请刷新采集列表');
 const tables=catalog.tables.filter(item=>item.datasource_id===sourceId).sort((a,b)=>a.table_name.localeCompare(b.table_name,'zh-CN'));
 if(!tables.length)throw new Error('此数据源尚无已采集的表，请先完成元数据采集');
 const existing=listModelViews().find(item=>item.sourceId===sourceId);
 const before=new Map<string,Row>((existing?.layout?.nodes||[]).map((node:Row)=>[String(node.entityId),node] as [string,Row]));
 const tableByEntity=new Map(catalog.tables.map(table=>[entityIdentity(table.tid),table]));
 const columns=Math.min(24,Math.max(4,Math.ceil(Math.sqrt(tables.length))));
 const occupied=new Set([...before.values()].map((node:Row)=>`${node.position?.x}:${node.position?.y}`));
 let nextSlot=before.size;
 const sourceNodes=tables.map((table)=>{
  const id=entityIdentity(table.tid),saved=before.get(id);
  if(saved)return saved;
  let slot=nextSlot++,position={x:(slot%columns)*390,y:Math.floor(slot/columns)*400};
  while(occupied.has(`${position.x}:${position.y}`)){slot=nextSlot++;position={x:(slot%columns)*390,y:Math.floor(slot/columns)*400};}
  occupied.add(`${position.x}:${position.y}`);
  return {entityId:id,position};
 });
 const externalNodes=[...before.values()].filter(node=>{const table=tableByEntity.get(String(node.entityId));return table&&table.datasource_id!==sourceId;});
 const nodes=[...sourceNodes,...externalNodes];
 const ids=new Set(nodes.map(node=>tableByEntity.get(String(node.entityId))?.tid).filter(Boolean));
 const relationCount=catalog.relations.filter(item=>ids.has(String(item.source_table_id))&&ids.has(String(item.target_table_id))).length;
 const librarySourceIds=[...new Set([sourceIdentity(sourceId),...(existing?.layout?.librarySourceIds||[]).map((id:string)=>id.startsWith('shared-source:')?id:sourceIdentity(id))])];
 const model=saveModelView({name:existing?.name||`${source.db_name.slice(0,121)} · 数据模型`,code:existing?.code||`source-${crypto.randomUUID().slice(0,8)}`,domain:existing?.domain||source.app_name||'',description:existing?.description||`基于「${source.db_name}」已采集表构建；关系线来自平台已保存的关系台账。`,sourceId,layout:{...existing?.layout,nodes,librarySourceIds,fieldMode:existing?.layout?.fieldMode||(tables.length>300?'summary':'key')}},existing?.id,existing?.version);
 return {model,tableCount:tables.length,relationCount};
}
