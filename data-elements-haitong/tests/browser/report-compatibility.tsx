// Development-only fixture. Real report components, synthetic data, zero business requests.
import React,{useState} from 'react';
import {createRoot} from 'react-dom/client';
import {Button,ConfigProvider} from 'antd';
import LegacyDialog from '../../src/report/components/LegacyDialog';
import FieldConfigurationDialog from '../../src/report/components/FieldConfigurationDialog';
import FileParsingDialog from '../../src/report/components/FileParsingDialog';
import PreviewPanel from '../../src/report/components/PreviewPanel';
import ReportFileUpload from '../../src/report/components/ReportFileUpload';
import {updateReportField} from '../../src/report/services/fieldGovernance';
import {tableResource,tableSample,type UploadTable} from '../../src/report/services/uploadWorkbench';
import type {SourceBook} from '../../src/report/core/model';
import '../../src/report/styles.css';
import '../../src/report/components/ReportControls.css';
import '../../src/styles/popup-compat.css';
import {reportControlTheme} from '../../src/report/controlTheme';

const requested=Number(new URLSearchParams(location.search).get('fields')||20),count=[20,100].includes(requested)?requested:20;
const book:SourceBook={id:'validation_browser',filename:'validation_browser.csv',size:64,importedAt:'2026-11-02',sheets:[{name:'validation_browser',cells:[[{v:'编号'},{v:'时间'}],[{v:'V001'},{v:'2026-11-02 08:00:00'}],[{v:'V002'},{v:'2026-11-02 09:00:00'}]],hidden:false,hiddenRows:[],hiddenCols:[],merges:[],rowCount:3,colCount:2,notes:[]}]};
const initial:UploadTable={key:'validation_browser',fileId:'validation_browser',fileName:book.filename,sheetName:book.sheets[0].name,tableName:'validation_browser',tableNameCn:'浏览器兼容验证表',businessType:'业务表',fieldCount:count,recordCount:2,status:'success',startRow:1,endRow:3,startColumn:1,endColumn:count,
  fields:Array.from({length:count},(_,index)=>({columnName:index===0?'record_id':`event_time_${index}`,columnComment:index===0?'记录编号':`时间${index}`,columnType:index===0?'varchar':'datetime',primaryKey:index===0,nullable:index!==0,isTimestamp:false,standardField:index===0?'':'DATETIME'}))};
function Fixture(){
  const [table,setTable]=useState(initial),[dialog,setDialog]=useState(''),[last,setLast]=useState('等待操作'),[files,setFiles]=useState<string[]>([]);
  const previewTable={...table,fields:table.fields.slice(0,2),fieldCount:2,endColumn:2},resource=tableResource(book,previewTable);resource.sync=Object.fromEntries(resource.rows.map(row=>[row.id,'success']));
  return <ConfigProvider prefixCls="report-ant" theme={{inherit:false,token:{colorPrimary:'#246bfd',motion:false},components:reportControlTheme}}>
    <main style={{padding:20,background:'#f3f6fa'}}><h2>数据上报浏览器兼容回归</h2><p>{navigator.userAgent}</p>
      <p>字段 {count}；业务请求 0；最近操作：<output>{last}</output></p>
      <section style={{display:'flex',gap:12,alignItems:'center',margin:'16px 0'}}><ReportFileUpload hasFiles={!!files.length} onFiles={items=>{setFiles(items.map(file=>file.name));setLast(`文件选择成功 ${items.length}：${items.map(file=>file.name).join(',')}`);}}/>
        <Button onClick={()=>setDialog('raw')}>原始样例弹框</Button><Button onClick={()=>setDialog('delivery')}>上报结果弹框</Button><Button onClick={()=>setDialog('parse')}>文件解析弹框</Button></section>
      <FieldConfigurationDialog open inline table={table} tables={[table]} readOnly={false} busy={false} onClose={()=>{}} onPreview={()=>setDialog('raw')} onSave={()=>setLast('规范保存交互成功（仅本地）')}
        onTableChange={(key,value)=>{setTable(current=>({...current,[key]:value}));setLast(`表属性 ${key}=${value}`);}}
        onFieldChange={(index,patch)=>{setTable(current=>({...current,fields:updateReportField(current.fields,index,patch)}));setLast(`字段 ${index} ${JSON.stringify(patch)}`);}}/>
    </main>
    <LegacyDialog open={dialog==='raw'||dialog==='delivery'} onClose={()=>setDialog('')} title={dialog==='raw'?'原始样例 · 兼容验证':'上报数据 · 兼容验证'} className="report-dialog--compact">
      <PreviewPanel resource={dialog==='raw'?tableSample(book,previewTable):resource} view={dialog==='raw'?'original':'normalized'} compact sampleLimit={10} readOnly showDeliveryStatus={dialog==='delivery'} dictionaries={[]} onEdit={()=>{}} onDelete={()=>{}}/>
    </LegacyDialog>
    <FileParsingDialog file={dialog==='parse'?{fileId:'validation_browser',fileName:book.filename}:undefined} tables={[previewTable]} loadBook={async()=>book} onSave={async()=>{setLast('解析配置交互成功（仅本地）');}} onClose={()=>setDialog('')}/>
  </ConfigProvider>;
}
createRoot(document.getElementById('root')!).render(<Fixture/>);
