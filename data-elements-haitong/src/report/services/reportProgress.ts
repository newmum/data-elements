import {hasReportSuccess,isReportReady} from './reportSchema';
import {inferBusinessType} from './tableClassification';
import type {UploadManifest,UploadTable} from './uploadWorkbench';

export function withDefaultTableTypes(manifest:UploadManifest):UploadManifest{
  return {...manifest,tables:manifest.tables.map(table=>hasReportSuccess(table,manifest.receipts)||manifest.receipts?.some(receipt=>receipt.tableId===table.tid&&receipt.status==='sending')?table:defaultTableType(table))};
}
export function defaultTableType(table:UploadTable):UploadTable{
  return table.businessType?table:{...table,businessType:inferBusinessType(table).type,normalized:false,normalizationVersion:0};
}
export function reportStepCompletion(manifest:UploadManifest){
  const uploaded=manifest.files.length>0;
  const normalized=uploaded&&manifest.tables.length>0&&!manifest.failedTables?.length
    &&manifest.files.every(file=>manifest.tables.some(table=>table.fileId===file.fileId))
    &&manifest.tables.every(table=>isReportReady(table,manifest.tables)||hasReportSuccess(table,manifest.receipts));
  return {uploaded,normalized,reported:manifest.receipts?.some(receipt=>receipt.status==='success')===true};
}
