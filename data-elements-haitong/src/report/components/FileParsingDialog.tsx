import { useEffect,useState } from 'react';
import { Alert,Button,Checkbox,InputNumber,Modal,Select,Table } from 'antd';
import type { Region,SourceBook } from '../core/model';
import { colName } from '../core/model';
import { defaultRegion,regionErrors,resourceFromRegion } from '../core/import';
import type { UploadTable } from '../services/uploadWorkbench';

export default function FileParsingDialog({entry,loadBook,onSave,onClose}:{
  entry?:UploadTable;loadBook:(encoding:string,delimiter:string)=>Promise<SourceBook>;
  onSave:(table:UploadTable,book:SourceBook)=>Promise<void>;onClose:()=>void;
}){
  const [book,setBook]=useState<SourceBook>(),[region,setRegion]=useState<Region>(),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  const [encoding,setEncoding]=useState('utf-8'),[delimiter,setDelimiter]=useState('');
  async function read(enc=encoding,sep=delimiter){setBusy(true);setError('');setBook(undefined);setRegion(undefined);try{
    const next=await loadBook(enc,sep);setBook(next);const index=Math.max(0,next.sheets.findIndex(sheet=>sheet.name===entry?.sheetName));
    setRegion(defaultRegion(next.sheets[index],index));
  }catch(cause){setError((cause as Error).message);}finally{setBusy(false);}}
  useEffect(()=>{if(entry){setEncoding(entry.parseEncoding||'utf-8');setDelimiter(entry.parseDelimiter||'');void read(entry.parseEncoding||'utf-8',entry.parseDelimiter||'');}},[entry?.key]);
  const sheet=book&&region?book.sheets[region.sheet]:undefined;
  const previewStart=Math.max(0,(region?.startRow||1)-3);
  const patch=(value:Partial<Region>)=>setRegion(current=>current?{...current,...value}:current);
  async function apply(){if(!book||!region||!entry)return;setError('');setBusy(true);try{
    const errors=regionErrors([region],book,{files:20,bytes:10485760,rows:20000,cols:200,cells:500000,parseMs:30000});
    if(errors.length)throw new Error(errors[0]);
    const resource=resourceFromRegion(book,region);if(!resource.rows.length)throw new Error('该区域没有数据行，请调整表头或数据起始行');
    const codes:Record<string,string>={text:'varchar',integer:'bigint',decimal:'decimal',date:'date',datetime:'datetime',boolean:'boolean'};
    const table:UploadTable={...entry,status:'success',error:undefined,normalized:false,normalizationVersion:undefined,
      tableName:entry.tableName||`report_${crypto.randomUUID().replace(/-/g,'').slice(0,12)}`,tableNameCn:entry.tableNameCn||sheet?.name||'数据表',
      sheetName:sheet!.name,startRow:region.startRow,endRow:region.endRow,startColumn:region.startCol,endColumn:region.endCol,
      headerMode:region.header,dataStartRow:region.dataStart,parseEncoding:encoding,parseDelimiter:delimiter,recordCount:resource.rows.length,fieldCount:resource.schema.fields.length,
      fields:resource.schema.fields.map(field=>({columnName:field.en,columnComment:field.zh,columnType:codes[field.type],columnLength:field.type==='text'?4000:undefined,primaryKey:false,nullable:true,isTimestamp:false,standardField:''}))};
    await onSave(table,book);onClose();
  }catch(cause){setError((cause as Error).message);}finally{setBusy(false);}}
  return <Modal title={`设置解析 · ${entry?.fileName||''}`} open={!!entry} onCancel={()=>{if(!busy)onClose();}} maskClosable={!busy} width={1040}
    footer={<><Button disabled={busy} onClick={onClose}>取消</Button><Button type="primary" loading={busy} disabled={!book||!region} onClick={()=>void apply()}>应用解析</Button></>}>
    <div className="report-parse-controls">
      <label>编码<Select aria-label="文件编码" disabled={busy} value={encoding} options={['utf-8','gb18030'].map(value=>({value,label:value.toUpperCase()}))} onChange={value=>{setEncoding(value);void read(value,delimiter);}}/></label>
      <label>分隔符<Select aria-label="文件分隔符" disabled={busy} value={delimiter} options={[{value:'',label:'自动'},{value:',',label:'逗号'},{value:';',label:'分号'},{value:'\t',label:'制表符'}]} onChange={value=>{setDelimiter(value);void read(encoding,value);}}/></label>
      {book&&<label>工作表<Select aria-label="解析工作表" disabled={busy} value={region?.sheet} options={book.sheets.map((s,index)=>({value:index,label:s.name}))} onChange={index=>setRegion(defaultRegion(book.sheets[index],index))}/></label>}
      {region&&<label>表头<Select aria-label="解析表头模式" disabled={busy} value={region.header} options={[{value:'single',label:'单行表头'},{value:'bilingual',label:'双语双行表头'},{value:'none',label:'无表头'}]} onChange={value=>patch({header:value,dataStart:region.startRow+(value==='none'?0:value==='bilingual'?2:1)})}/></label>}
    </div>
    {error&&<Alert type="error" showIcon message={error}/>}
    {region&&sheet&&<><div className="report-parse-controls">{([['startRow','表头起始行',sheet.rowCount],['dataStart','数据起始行',sheet.rowCount],['endRow','结束行',sheet.rowCount],['startCol','起始列',sheet.colCount],['endCol','结束列',sheet.colCount]] as const).map(([key,label,max])=><label key={key}>{label}<InputNumber aria-label={label} disabled={busy} min={1} max={max} value={region[key]} onChange={value=>patch({[key]:value||1})}/></label>)}</div>
      <Table size="small" pagination={false} scroll={{x:true,y:270}} rowKey="row" dataSource={sheet.cells.slice(previewStart,previewStart+12).map((cells,index)=>({row:previewStart+index+1,cells}))}
        columns={[{title:'行',width:48,dataIndex:'row'},...Array.from({length:Math.min(sheet.colCount,200)},(_,index)=>({title:colName(index),width:125,render:(_:unknown,row:{cells:{v:unknown}[]})=>String(row.cells[index]?.v??'')}))]}/>
      {(sheet.hidden||sheet.hiddenRows.length>0||sheet.hiddenCols.length>0)&&<Checkbox disabled={busy} checked={region.hiddenConfirmed} onChange={event=>patch({hiddenConfirmed:event.target.checked})}>已核对隐藏行列</Checkbox>}
    </>}
  </Modal>;
}
