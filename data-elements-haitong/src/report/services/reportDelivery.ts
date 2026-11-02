import {clone,type Resource,type SyncState} from '../core/model';
import type {ReportReceipt,UploadManifest,UploadTable} from './uploadWorkbench';
import {hasReportSuccess,isReportReady} from './reportSchema';

const registrationValue=(table:UploadTable)=>JSON.stringify([table.tableName,table.tableNameCn,table.businessType,table.fields,table.normalized]);

export const DELIVERY_STATE_LABEL:Record<SyncState,string>={idle:'未上报',sending:'投递中',waiting:'等待回执',success:'已上报',failed:'投递失败',unknown:'结果待确认'};

/** FTP acknowledges a whole file. It does not acknowledge individual target rows. */
export function resourceWithDeliveryReceipt(resource:Resource,table:UploadTable,receipts:ReportReceipt[]):Resource{
  const matches=receipts.filter(row=>row.tableId===table.tid);
  const receipt=matches.find(row=>row.status==='success')||matches.reduce<ReportReceipt|undefined>((latest,row)=>!latest||row.reportedAt>=latest.reportedAt?row:latest,undefined);
  const state:SyncState=receipt?.status==='success'?'success':receipt?.status==='failed'?'failed':receipt?.status==='sending'?'sending':'idle';
  return {...resource,locked:state==='success'||state==='sending',sync:Object.fromEntries(resource.rows.map(row=>[row.id,state]))};
}

export function hasRegistrationDraft(saved:UploadManifest,draft:UploadManifest){
  const previous=new Map(saved.tables.map(table=>[table.key,table]));
  return draft.tables.some(table=>{const before=previous.get(table.key);return !!before&&registrationValue(before)!==registrationValue(table);});
}

/** Rebase local schema edits without submitting them with another table's operation. */
export function rebaseRegistrationDraft(saved:UploadManifest,draft:UploadManifest,next:UploadManifest,committedKeys:string[]=[]):UploadManifest{
  const previous=new Map(saved.tables.map(table=>[table.key,table])),edits=new Map(draft.tables.map(table=>[table.key,table])),committed=new Set(committedKeys);
  return {...next,tables:next.tables.map(table=>{
    const before=previous.get(table.key),edit=edits.get(table.key);
    return !committed.has(table.key)&&before&&edit&&registrationValue(before)!==registrationValue(edit)
      ?{...table,tableName:edit.tableName,tableNameCn:edit.tableNameCn,businessType:edit.businessType,fields:clone(edit.fields),normalized:edit.normalized}:table;
  })};
}

export function savedDeliveryTable(saved:UploadManifest,key:string){
  const table=saved.tables.find(row=>row.key===key);
  if(!table||!(isReportReady(table,saved.tables)||hasReportSuccess(table,saved.receipts)))throw new Error('该表尚未保存通过规范，请先在第二步保存');
  const receipt=(saved.receipts||[]).find(row=>row.tableId===table.tid&&row.status==='success'&&row.registrationSnapshot);
  const snapshot=receipt?.registrationSnapshot;
  return clone(snapshot?{...snapshot,key:table.key,tid:table.tid}:table);
}

/** Only the selected saved table's actual delivery row count may change here. */
export function deliveryManifest(saved:UploadManifest,key:string,rowCount:number):UploadManifest{
  const table=savedDeliveryTable(saved,key);
  if(!Number.isInteger(rowCount)||rowCount<1)throw new Error('当前数据表没有可上报的数据');
  return {...saved,tables:saved.tables.map(row=>row.key===table.key?{...row,recordCount:rowCount}:row)};
}
