import {useEffect,useState} from 'react';
import {Button,Empty} from 'antd';
import {Tooltip} from './ReportControls';
import type {RegistrationFileGroup,UploadField,UploadManifest,UploadTable} from '../services/uploadWorkbench';
import {hasReportSuccess,isReportReady} from '../services/reportSchema';
import {fileParsingLocked} from '../services/reportFiles';
import FieldConfigurationDialog from './FieldConfigurationDialog';
import SourceFileIcon from './SourceFileIcon';
import Icon from './Icon';

export default function RegistrationWorkspace({groups,manifest,busy,error,focusKey,onSelect,onTableChange,onFieldChange,onSave,onPreview,onParse,onRemove,onDismiss}:{
  groups:RegistrationFileGroup[];manifest:UploadManifest;busy:boolean;error?:string;focusKey?:string;
  onSelect:(table:UploadTable)=>void;
  onTableChange:(key:string,property:'tableName'|'tableNameCn'|'businessType',value:string)=>void;onFieldChange:(key:string,index:number,patch:Partial<UploadField>)=>void;
  onSave:(key:string)=>void;onPreview:(table:UploadTable)=>void;onParse:(group:RegistrationFileGroup)=>void;onRemove:(fileId:string)=>void;onDismiss:(key:string)=>void;
}){
  const [activeKey,setActiveKey]=useState(''),[collapsed,setCollapsed]=useState<Set<string>>(new Set());
  const byKey=new Map(manifest.tables.map(table=>[table.key,table])),failures=new Map((manifest.failedTables||[]).map(table=>[table.key,table]));
  const visible=groups.flatMap(group=>group.tableKeys.flatMap(key=>byKey.get(key)?[byKey.get(key)!]:[]));
  const active=visible.find(table=>table.key===activeKey)||visible[0];
  useEffect(()=>{if(focusKey&&visible.some(table=>table.key===focusKey)){setActiveKey(focusKey);setCollapsed(current=>new Set([...current].filter(key=>!groups.find(group=>group.key===key)?.tableKeys.includes(focusKey))));}},[focusKey,groups]);
  function choose(table:UploadTable){setActiveKey(table.key);onSelect(table);}
  return <>
    <div className="report-registration-workspace">
      <aside className="report-file-tree" aria-label="原始文件与数据表">
        {!groups.length&&<Empty description="没有匹配的原始文件"/>}
        {groups.map(group=>{const tables=group.tableKeys.flatMap(key=>byKey.get(key)?[byKey.get(key)!]:[]),ready=tables.filter(table=>isReportReady(table,manifest.tables)||hasReportSuccess(table,manifest.receipts)).length,open=!collapsed.has(group.key),locked=group.locked||fileParsingLocked(group.fileId,manifest);return <section className="report-source-file" key={group.key}>
          <div className="report-source-file-heading"><button type="button" className="report-source-file-toggle" disabled={busy} aria-expanded={open} aria-label={`${open?'收起':'展开'}文件：${group.fileName}`} onClick={()=>setCollapsed(current=>{const next=new Set(current);if(next.has(group.key))next.delete(group.key);else next.add(group.key);return next;})}><Icon name="down" size={13} style={!open?{transform:'rotate(-90deg)'}:undefined}/><SourceFileIcon name={group.fileName} small/><span><strong title={group.fileName}>{group.fileName}</strong></span></button>
            <div className="report-source-file-meta"><small>{tables.length} 张表 · 已规范 {ready}{group.failedKeys.length?` · 待解析 ${group.failedKeys.length}`:''}</small><div className="report-source-file-actions"><Tooltip title={!group.fileId?'没有可读取的原始文件':locked?'正在上报，处理完成后可设置解析':'设置解析'}><Button type="text" size="small" aria-label={`设置解析：${group.fileName}`} disabled={busy||!group.fileId||locked} icon={<Icon name="edit" size={14}/>} onClick={()=>onParse(group)}/></Tooltip>{group.fileId?<Tooltip title="移除文件"><Button type="text" size="small" aria-label={`移除文件：${group.fileName}`} disabled={busy||locked||tables.some(table=>hasReportSuccess(table,manifest.receipts))} icon={<Icon name="trash" size={14}/>} onClick={()=>onRemove(group.fileId)}/></Tooltip>:group.failedKeys.length>0&&<Tooltip title="移除失败项"><Button type="text" size="small" aria-label={`移除失败项：${group.fileName}`} disabled={busy} icon={<Icon name="trash" size={14}/>} onClick={()=>group.failedKeys[0]&&onDismiss(group.failedKeys[0])}/></Tooltip>}</div></div>
          </div>
          {open&&<div className="report-file-children">{tables.map(table=>{const saved=isReportReady(table,manifest.tables)||hasReportSuccess(table,manifest.receipts);return <button type="button" key={table.key} className={`report-tree-table${active?.key===table.key?' is-active':''}`} aria-current={active?.key===table.key?'true':undefined} disabled={busy} onClick={()=>choose(table)}><span className="report-tree-table-name" title={table.tableNameCn||table.tableName}>{table.tableNameCn||table.tableName||'待填写表名'}</span><span className={`report-table-save-state ${saved?'is-saved':''}`} title={saved?'已登记':'未登记'} aria-label={saved?'已登记':'未登记'}>{saved?<Icon name="check" size={15}/>:'未登记'}</span></button>;})}
            {group.failedKeys.map(key=><div key={key} className="report-parse-problem"><Icon name="warning" size={16}/><span>{failures.get(key)?.error||'尚未识别出有效数据表'}</span></div>)}
            {!tables.length&&!group.failedKeys.length&&<p className="report-file-no-tables">尚未识别出数据表，请设置解析</p>}
          </div>}
        </section>;})}
      </aside>
      <div className="report-registration-details">{active?<FieldConfigurationDialog inline open table={active} tables={manifest.tables} readOnly={false} busy={busy} error={error} onClose={()=>{}} onTableChange={(property,value)=>onTableChange(active.key,property,value)} onFieldChange={(index,patch)=>onFieldChange(active.key,index,patch)} onSave={()=>onSave(active.key)} onPreview={()=>onPreview(active)}/>:<div className="report-registration-empty"><Icon name="table" size={38}/><strong>选择文件中的数据表</strong><span>无法识别的文件可先设置解析</span></div>}</div>
    </div>
  </>;
}
