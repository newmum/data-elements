import type {Region,SourceBook} from '../core/model';
import {defaultRegion,regionErrors,resourceFromRegion} from '../core/import';
import type {UploadFile,UploadManifest,UploadTable} from './uploadWorkbench';
import {defaultTableType} from './reportProgress';

export const fileParsingLocked=(fileId:string,manifest:UploadManifest)=>manifest.tables.some(table=>table.fileId===fileId&&!!table.tid&&(manifest.receipts||[]).some(receipt=>receipt.tableId===table.tid&&receipt.status==='sending'));
export function parsingRegions(book:SourceBook,tables:UploadTable[]):Region[]{
  const regions=tables.flatMap(table=>{const index=book.sheets.findIndex(sheet=>sheet.name===table.sheetName);return index<0||!table.startRow||!table.endRow?[]:[{...defaultRegion(book.sheets[index],index),id:table.key,startRow:table.startRow,endRow:table.endRow,startCol:table.startColumn||1,endCol:table.endColumn||table.fields.length,header:table.headerMode||'single',dataStart:table.dataStartRow??table.startRow+(table.headerMode==='none'?0:table.headerMode==='bilingual'?2:1),name:table.tableNameCn,hiddenConfirmed:true}];});
  return regions.length?regions:book.sheets.flatMap((sheet,index)=>sheet.rowCount&&sheet.colCount?[defaultRegion(sheet,index)]:[]);
}
export function parsedFileTables(file:UploadFile,book:SourceBook,regions:Region[],existing:UploadTable[],encoding:string,delimiter:string):UploadTable[]{
  if(!regions.length||regions.length>100)throw new Error('请选择1至100个数据区域');
  const errors=regionErrors(regions,book,{files:20,bytes:10485760,rows:20000,cols:200,cells:500000,parseMs:30000});
  if(errors.length)throw new Error(errors[0]);
  const codes:Record<string,string>={text:'varchar',integer:'bigint',decimal:'decimal',date:'date',datetime:'datetime',boolean:'boolean'};
  return regions.map(region=>{
    const prior=existing.find(table=>table.key===region.id),resource=resourceFromRegion(book,region);
    if(!resource.rows.length)throw new Error(`${region.name}：该区域没有有效数据行`);
    const unchanged=prior&&prior.sheetName===book.sheets[region.sheet].name&&prior.startRow===region.startRow&&prior.endRow===region.endRow&&(prior.startColumn||1)===region.startCol&&(prior.endColumn||prior.fields.length)===region.endCol&&(prior.headerMode||'single')===region.header&&(prior.parseEncoding||'utf-8')===encoding&&(prior.parseDelimiter||'')===delimiter;
    const name=prior?.tableName||`report_${crypto.randomUUID().replace(/-/g,'').slice(0,12)}`;
    return defaultTableType({...prior,key:region.id,fileId:file.fileId,fileName:file.fileName,status:'success',error:undefined,normalized:false,normalizationVersion:undefined,tableName:name,tableNameCn:prior?.tableNameCn||region.name||file.fileName,
      sheetName:book.sheets[region.sheet].name,startRow:region.startRow,endRow:region.endRow,startColumn:region.startCol,endColumn:region.endCol,headerMode:region.header,dataStartRow:region.dataStart,parseEncoding:encoding,parseDelimiter:delimiter,
      recordCount:resource.rows.length,fieldCount:resource.schema.fields.length,fields:unchanged&&prior.fields.length===resource.schema.fields.length?prior.fields:resource.schema.fields.map((field,index)=>({columnName:field.en||`field_${index+1}`,columnComment:field.zh||`字段 ${field.en||index+1}`,columnType:codes[field.type],columnLength:field.type==='text'?4000:undefined,primaryKey:false,nullable:true,isTimestamp:false,standardField:''}))});
  });
}
export function applyParsedFile(manifest:UploadManifest,file:UploadFile,tables:UploadTable[]):UploadManifest{
  if(!manifest.files.some(row=>row.fileId===file.fileId)||fileParsingLocked(file.fileId,manifest))throw new Error('文件不存在或正在上报，暂不能修改解析');
  if(tables.some(table=>table.fileId!==file.fileId||manifest.tables.some(prior=>prior.key===table.key&&prior.fileId!==file.fileId))||new Set(tables.map(table=>table.key)).size!==tables.length)throw new Error('解析结果包含跨文件或重复数据表');
  const previous=manifest.tables.filter(table=>table.fileId===file.fileId),keys=new Set(tables.map(table=>table.key));
  if(previous.some(table=>table.tid&&!keys.has(table.key)))throw new Error('已登记的数据表不能在解析设置中移除，请保留原区域');
  const byKey=new Map(previous.map(table=>[table.key,table]));
  const next=[...manifest.tables.filter(table=>table.fileId!==file.fileId),...tables.map(table=>{const old=byKey.get(table.key);if(table.tid&&table.tid!==old?.tid)throw new Error('解析结果不能变更登记表归属');return {...table,tid:old?.tid,normalized:false,normalizationVersion:undefined};})];
  if(next.length>100)throw new Error('每个数据源最多100张表');
  return {...manifest,tables:next,failedTables:(manifest.failedTables||[]).filter(table=>table.fileId!==file.fileId)};
}
