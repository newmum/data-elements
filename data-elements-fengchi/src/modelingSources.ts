import type {TableDef} from './data';
export type SourceObject={key:string;database:string;databaseLabel:string;label:string;kind:'table'|'view'};
export function sourceObjects(tables:Record<string,TableDef>,imported:Record<string,TableDef>={}):SourceObject[]{
 return Object.entries(tables).filter(([,t])=>t.columns.length>0).map(([key,t])=>({key,database:t.source?.databaseId||(Object.hasOwn(imported,key)?'local-files':'workspace'),databaseLabel:t.source?.databaseLabel||(Object.hasOwn(imported,key)?'本地导入文件':'当前工作区'),label:t.source?.objectLabel||t.comment||t.name,kind:t.source?.kind||'table'}));
}
export function sourceDatabase(config:Record<string,string>,objects:SourceObject[]){return config.database||objects.find(o=>o.key===config.table)?.database||''}
export function changeSourceDatabase(config:Record<string,string>,database:string,objects:SourceObject[]){return {...config,database,mockSqlTable:config.database===database?config.mockSqlTable||'':'',table:objects.some(o=>o.key===config.table&&o.database===database)?config.table:''}}
export function selectSourceTable(config:Record<string,string>,table:string,objects:SourceObject[]){const database=sourceDatabase(config,objects);return {...config,database,table:objects.some(o=>o.key===table&&o.database===database)?table:''}}
export function automaticSourceName(config:Record<string,string>,objects:SourceObject[]){if(config.inputMode==='sql'){const db=objects.find(o=>o.database===config.database);return db?`${db.databaseLabel} / 自定义 SQL`:'请选择数据库'}const object=objects.find(o=>o.key===config.table&&o.database===sourceDatabase(config,objects));return object?`${object.databaseLabel} / ${object.label}`:'请选择数据库和表'}
export function sourceSelectionUpdate(node:{name:string;config:Record<string,string>},config:Record<string,string>,objects:SourceObject[]){return {name:node.config.manualSourceName==='true'?node.name:automaticSourceName(config,objects),config}}

export function changeSourceMode(config:Record<string,string>,inputMode:string):Record<string,string>{return {...config,inputMode,table:'',mockSqlTable:''}}
