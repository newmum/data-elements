// Development-only synthetic fixture: no platform reads, writes or file delivery.
import {useState} from 'react';
import {createRoot} from 'react-dom/client';
import {Button,ConfigProvider,Space} from 'antd';
import type {ReportDeliveryProgress} from '../../src/report/components/DeliveryProgress';
import ReportDeliveryTable from '../../src/report/components/ReportDeliveryTable';
import type {UploadTable,UploadManifest} from '../../src/report/services/uploadWorkbench';
import {prepareReportFile} from '../../src/report/services/reportCsv';
import type {Resource} from '../../src/report/core/model';
import '../../src/report/styles.css';

function Fixture(){
  const [progress,setProgress]=useState<ReportDeliveryProgress>(),[message,setMessage]=useState('待验证'),[working,setWorking]=useState(false),[confirmed,setConfirmed]=useState(false);
  const table:UploadTable={key:'validation',tid:'validation_table',fileId:'validation',fileName:'validation.csv',sheetName:'validation',tableName:'validation_progress',tableNameCn:'十万行进度验证',businessType:'业务表',fields:[{columnName:'record_id',columnComment:'记录编号',columnType:'varchar',primaryKey:true,nullable:false,isTimestamp:false}],fieldCount:1,recordCount:100_000,status:'success',startRow:1,endRow:100001,startColumn:1,endColumn:1,normalized:true,normalizationVersion:1};
  const manifest:UploadManifest={revision:'validation',files:[],tables:[table],receipts:confirmed?[{id:'validation_receipt',fileId:'validation',fileName:'validation.csv',tableId:'validation_table',tableName:'validation_progress',status:'success',rowCount:100_000,reportedAt:'2026-11-03T01:00:00Z'}]:[]};
  async function prepare(){
    setWorking(true);setConfirmed(false);setMessage('正在校验合成数据');
    const schema:Resource['schema']={name:'十万行进度验证',englishName:'validation_progress',description:'合成数据，仅用于开发验证',sameTimeConfirmed:false,reviewed:true,timestamp:'',eventTime:'',fields:[
      {id:'id',column:1,headers:['编号'],en:'record_id',zh:'编号',type:'text',nullable:'forbid',confirmed:true,pk:true,time:{format:'',timezone:'Asia/Shanghai'}},
    ]};
    const resource:Resource={id:'validation_progress',schema,savedSchema:schema,schemaVersion:1,dirty:false,notes:[],rows:Array.from({length:100_000},(_,i)=>({id:`row-${i}`,sourceRow:i+2,values:{id:`V${i}`},formulaFields:[],precisionFields:[]})),source:{fileId:'validation',filename:'validation.csv',sheet:'validation',range:'A1:A100001',importedAt:'2026-11-03',demo:false},dataVersion:0,locked:false,sync:{}};
    try{const file=await prepareReportFile(resource,value=>setProgress({tableKey:'validation',phase:'preparing',...value}));setMessage(`实际校验 100,000 行；CSV ${file.size.toLocaleString()} 字节；未调用业务接口`);setProgress({tableKey:'validation',phase:'uploading',completed:0,total:100});}
    catch(error){setMessage(error instanceof Error?error.message:'验证失败');}finally{setWorking(false);}
  }
  return <ConfigProvider prefixCls="report-ant" theme={{token:{colorPrimary:'#246bfd',motion:false}}}>
    <main style={{padding:24}}><h2>上报进度验证（合成数据，不会投递文件）</h2><p style={{margin:'16px 0'}}>{message}</p>
      <Space wrap><Button disabled={working} onClick={()=>void prepare()}>校验十万行</Button><Button disabled={working} onClick={()=>setProgress({tableKey:'validation',phase:'uploading',completed:50,total:100})}>模拟上传 50%</Button><Button disabled={working} onClick={()=>setProgress({tableKey:'validation',phase:'waiting',completed:100,total:100})}>模拟上传完成</Button><Button disabled={working} onClick={()=>{setProgress(undefined);setConfirmed(true);setMessage('模拟收到成功回执');}}>模拟收到回执</Button></Space>
      <div style={{marginTop:24,background:'white'}}><ReportDeliveryTable rows={[table]} manifest={manifest} busy={working} progress={progress} submittingKey={progress?'validation':undefined} onReport={()=>void prepare()} onPreview={()=>setMessage('此页仅用合成数据验证进度，不读取业务文件')}/></div>
    </main>
  </ConfigProvider>;
}
createRoot(document.getElementById('root')!).render(<Fixture/>);
