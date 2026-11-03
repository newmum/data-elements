import {Alert,Button,Input} from 'antd';
import {Select,Tooltip} from './ReportControls';
import type {UploadField,UploadTable} from '../services/uploadWorkbench';
import {reportSchemaIssues,REPORT_BUSINESS_TYPES} from '../services/reportSchema';
import FieldGovernanceTable from './FieldGovernanceTable';
import LegacyDialog from './LegacyDialog';
import Icon from './Icon';

export default function FieldConfigurationDialog({open,table,tables,readOnly,busy,error,onClose,onTableChange,onFieldChange,onSave,inline=false,onPreview}:{
  inline?:boolean;onPreview?:()=>void;open:boolean;table?:UploadTable;tables:UploadTable[];readOnly:boolean;busy:boolean;error?:string;onClose:()=>void;
  onTableChange:(property:'tableName'|'tableNameCn'|'businessType',value:string)=>void;
  onFieldChange:(index:number,patch:Partial<UploadField>)=>void;onSave:()=>void;
}){
  const issues=table?reportSchemaIssues(table,tables):[],disabled=busy||readOnly;
  const saveButton=!readOnly&&<Tooltip title={issues.slice(0,3).join('；')}><Button type="primary" loading={busy} disabled={!table||issues.length>0} onClick={onSave}>保存</Button></Tooltip>;
  const metadata=table&&<div className="report-form-grid report-table-metadata">
    <label>表英文名<Input aria-label="规范表英文名" value={table.tableName} disabled={disabled} onChange={event=>onTableChange('tableName',event.target.value)}/></label>
    <label>表中文名<Input aria-label="规范表中文名" value={table.tableNameCn} disabled={disabled} onChange={event=>onTableChange('tableNameCn',event.target.value)}/></label>
    <label>表类型<Select aria-label="规范表类型" value={table.businessType} options={REPORT_BUSINESS_TYPES.map(value=>({value,label:value}))} disabled={disabled} onChange={value=>onTableChange('businessType',value)}/></label>
  </div>;
  const fields=table&&<>{error&&<Alert type="error" message={error} showIcon style={{marginBottom:12}}/>}<FieldGovernanceTable table={table} disabled={disabled} onChange={onFieldChange}/></>;
  if(inline)return <section className="report-registration-editor" aria-label="数据表规范编辑">
    <header className="report-editor-heading report-editor-metadata-heading">{metadata}<div className="report-editor-actions">{onPreview&&<Button disabled={busy||!table} onClick={onPreview}>数据预览</Button>}{saveButton}</div></header>
    {fields}
  </section>;
  return <LegacyDialog open={open} onClose={onClose} busy={busy} className="report-dialog--wide report-field-dialog" ariaLabel="字段规范" title={<span className="dialog-title"><Icon name="table"/>字段规范 · {table?.tableNameCn||'数据表'}</span>} footer={<><Button onClick={onClose} disabled={busy}>关闭</Button>{saveButton}</>}>{metadata}{fields}</LegacyDialog>;
}
