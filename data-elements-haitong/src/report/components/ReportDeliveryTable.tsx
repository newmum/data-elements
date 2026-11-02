import {Button,Table,Tag,Tooltip} from 'antd';
import type {ReportReceipt,UploadManifest,UploadTable} from '../services/uploadWorkbench';
import {hasReportSuccess,isReportReady} from '../services/reportSchema';

export default function ReportDeliveryTable({rows,manifest,busy,onReport,onPreview,submittingKey}:{rows:UploadTable[];manifest:UploadManifest;busy:boolean;onReport:(table:UploadTable)=>void;onPreview:(table:UploadTable)=>void;submittingKey?:string}){
  const receipts=new Map<string,ReportReceipt>();
  for(const receipt of manifest.receipts||[]){if(receipt.status!=='success')continue;const previous=receipts.get(receipt.tableId);if(!previous||receipt.reportedAt>previous.reportedAt)receipts.set(receipt.tableId,receipt);}
  return <Table className="report-delivery-list" size="middle" rowKey="key" dataSource={rows} pagination={false} scroll={{x:900}} locale={{emptyText:'暂无匹配的规范数据表'}} columns={[
    {title:'序号',width:50,align:'center',render:(_value,_row,index)=>index+1},
    {title:'数据表名称',render:(_,row)=><div className="report-delivery-table-name"><Button type="link" disabled={busy} title={row.tableNameCn} onClick={()=>onPreview(row)}>{row.tableNameCn}</Button><small title={row.tableName}>{row.tableName}</small></div>},
    {title:'表类型',dataIndex:'businessType',width:90,render:value=><Tag>{value||'历史数据表'}</Tag>},
    {title:'字段数',width:68,align:'right',render:(_,row)=>row.fields.length},
    {title:'数据量',dataIndex:'recordCount',width:88,align:'right',render:value=>Number(value||0).toLocaleString()},
    {title:'来源文件',dataIndex:'fileName',width:170,ellipsis:true},
    {title:'是否上报',width:154,render:(_,row)=>{const reported=hasReportSuccess(row,manifest.receipts),sending=!reported&&(row.key===submittingKey||(manifest.receipts||[]).some(receipt=>receipt.tableId===row.tid&&receipt.status==='sending')),time=row.tid&&receipts.get(row.tid)?.reportedAt;return <div className="report-delivery-status"><span className={`report-delivery-badge ${reported?'is-reported':sending?'is-sending':'is-pending'}`}><i/>{reported?'已上报':sending?'上报中':'待上报'}</span>{reported&&time&&<small>{new Date(time).toLocaleString('zh-CN',{hour12:false})}</small>}</div>;}},
    {title:'操作',width:100,align:'center',fixed:'right',className:'report-delivery-operation',render:(_,row)=>{const reported=hasReportSuccess(row,manifest.receipts),sending=row.key===submittingKey||(manifest.receipts||[]).some(receipt=>receipt.tableId===row.tid&&receipt.status==='sending'),ready=isReportReady(row,manifest.tables);return reported?<Button size="small" type="link" disabled={busy} onClick={()=>onPreview(row)}>查看数据</Button>:<Tooltip title={sending?'正在上报中':!ready?'请先完善登记信息':''}><Button size="small" type="link" disabled={busy||!ready||sending} onClick={()=>onReport(row)}>上报数据</Button></Tooltip>;}},
  ]}/>;
}
