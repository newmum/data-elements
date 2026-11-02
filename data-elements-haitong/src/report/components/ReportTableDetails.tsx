import type {Resource} from '../core/model';
import type {UploadTable} from '../services/uploadWorkbench';
import PreviewPanel from './PreviewPanel';
import LegacyDialog from './LegacyDialog';

export default function ReportTableDetails({entry,original,error,onClose}:{entry?:UploadTable;original?:Resource;error:string;onClose:()=>void}){
  if(!entry)return null;
  return <LegacyDialog title={`原始样例 · ${entry.tableNameCn||entry.tableName}`} ariaLabel="原始样例" open onClose={onClose} className="report-dialog--compact report-table-details">
    {original?<PreviewPanel resource={original} view="original" readOnly compact sampleLimit={10} dictionaries={[]} onEdit={()=>{}} onDelete={()=>{}}/>:<p role="status">{error||'正在读取原始样例…'}</p>}
  </LegacyDialog>;
}
