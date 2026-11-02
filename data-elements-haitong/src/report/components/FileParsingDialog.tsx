import {useEffect,useRef,useState} from 'react';
import {Alert,Button,Checkbox,InputNumber,Spin,Table} from 'antd';
import {Select,Tooltip} from './ReportControls';
import type {Region,SourceBook} from '../core/model';
import {colName} from '../core/model';
import {defaultRegion} from '../core/import';
import {parsedFileTables,parsingRegions} from '../services/reportFiles';
import type {UploadFile,UploadTable} from '../services/uploadWorkbench';
import LegacyDialog from './LegacyDialog';
import Icon from './Icon';

export default function FileParsingDialog({file,tables,loadBook,onSave,onClose}:{
  file?:UploadFile;tables:UploadTable[];loadBook:(encoding:string,delimiter:string)=>Promise<SourceBook>;
  onSave:(file:UploadFile,tables:UploadTable[],book:SourceBook)=>Promise<void>;onClose:()=>void;
}){
  const [book,setBook]=useState<SourceBook>(),[regions,setRegions]=useState<Region[]>([]),[active,setActive]=useState(0),[error,setError]=useState(''),[busy,setBusy]=useState(false);
  const [encoding,setEncoding]=useState('utf-8'),[delimiter,setDelimiter]=useState(''),readRevision=useRef(0);
  const isDelimited=/\.(csv|txt)$/i.test(file?.fileName||'');
  async function read(enc=encoding,sep=delimiter){const version=++readRevision.current;setBusy(true);setError('');setBook(undefined);setRegions([]);try{
    const next=await loadBook(enc,sep);if(version!==readRevision.current)return;setBook(next);setRegions(parsingRegions(next,tables));setActive(0);
  }catch(cause){if(version===readRevision.current)setError((cause as Error).message);}finally{if(version===readRevision.current)setBusy(false);}}
  useEffect(()=>{if(file){const enc=tables[0]?.parseEncoding||'utf-8',sep=tables[0]?.parseDelimiter||'';setEncoding(enc);setDelimiter(sep);void read(enc,sep);}return()=>{readRevision.current++;};},[file?.fileId]);
  const region=regions[active],sheet=book&&region?book.sheets[region.sheet]:undefined,previewStart=Math.max(0,(region?.startRow||1)-3);
  const registered=!!tables.find(table=>table.key===region?.id)?.tid;
  function patch(value:Partial<Region>){setRegions(current=>current.map((item,index)=>index===active?{...item,...value}:item));}
  function addRegion(){if(!book?.sheets.length)return;const index=region?.sheet||0;setRegions(current=>[...current,defaultRegion(book.sheets[index],index)]);setActive(regions.length);}
  function removeRegion(){if(registered)return;setRegions(current=>current.filter((_,index)=>index!==active));setActive(Math.max(0,active-1));}
  async function apply(){if(!book||!file)return;setError('');setBusy(true);try{
    const parsed=parsedFileTables(file,book,regions,tables,isDelimited?encoding:'utf-8',isDelimited?delimiter:'');await onSave(file,parsed,book);onClose();
  }catch(cause){setError((cause as Error).message);}finally{setBusy(false);}}
  return <LegacyDialog title={`设置文件解析 · ${file?.fileName||''}`} open={!!file} onClose={onClose} busy={busy} className="report-dialog--wide report-parse-dialog"
    footer={<><span className="report-dialog-hint">应用后，请重新保存该文件中各表的规范</span><Button disabled={busy} onClick={onClose}>取消</Button><Button type="primary" loading={busy} disabled={!book||!regions.length} onClick={()=>void apply()}>应用解析</Button></>}>
    {busy&&!book&&<div className="report-dialog-loading" role="status"><Spin/><span>正在读取原始文件…</span></div>}
    <div className="report-parse-controls">{isDelimited&&<><label>编码<Select aria-label="文件编码" disabled={busy} value={encoding} options={['utf-8','gb18030'].map(value=>({value,label:value.toUpperCase()}))} onChange={value=>{setEncoding(value);void read(value,delimiter);}}/></label><label>分隔符<Select aria-label="文件分隔符" disabled={busy} value={delimiter} options={[{value:'',label:'自动'},{value:',',label:'逗号'},{value:';',label:'分号'},{value:'|',label:'竖线'},{value:'\t',label:'制表符'}]} onChange={value=>{setDelimiter(value);void read(encoding,value);}}/></label></>}
      {book&&<div className="report-parse-regions-toolbar"><span>{book.sheets.length} 张工作表 · {regions.length} 个数据区域</span><Button disabled={busy||regions.length>=100} icon={<Icon name="plus" size={15}/>} onClick={addRegion}>添加区域</Button></div>}
    </div>
    {book&&<div className="report-parse-region-tabs" role="tablist" aria-label="文件中的数据区域">{regions.map((item,index)=><button type="button" key={item.id} role="tab" aria-selected={index===active} disabled={busy} className={index===active?'is-active':''} onClick={()=>setActive(index)}><Icon name="table" size={15}/>{book.sheets[item.sheet]?.name||'工作表'} · 区域 {index+1}</button>)}</div>}
    {error&&<Alert type="error" showIcon message={error}/>}
    {region&&sheet&&<><div className="report-parse-controls"><label>工作表<Select aria-label="解析工作表" disabled={busy} value={region.sheet} options={book!.sheets.map((item,index)=>({value:index,label:item.name}))} onChange={index=>patch({...defaultRegion(book!.sheets[index],index),id:region.id})}/></label><label>表头<Select aria-label="解析表头模式" disabled={busy} value={region.header} options={[{value:'single',label:'单行表头'},{value:'bilingual',label:'双语双行表头'},{value:'none',label:'无表头'}]} onChange={value=>patch({header:value,dataStart:region.startRow+(value==='none'?0:value==='bilingual'?2:1)})}/></label><Tooltip title={registered?'已登记的数据表须保留对应区域':'移除未登记的数据区域'}><Button danger disabled={busy||registered} icon={<Icon name="trash" size={14}/>} onClick={removeRegion}>移除区域</Button></Tooltip></div>
      <div className="report-parse-controls">{([['startRow','表头起始行',sheet.rowCount],['dataStart','数据起始行',sheet.rowCount],['endRow','结束行',sheet.rowCount],['startCol','起始列',sheet.colCount],['endCol','结束列',sheet.colCount]] as const).map(([key,label,max])=><label key={key}>{label}<InputNumber aria-label={label} disabled={busy} min={1} max={max} value={region[key]} onChange={value=>patch({[key]:value||1})}/></label>)}</div>
      <Table size="small" pagination={false} scroll={{x:'max-content'}} rowKey="row" dataSource={sheet.cells.slice(previewStart,previewStart+12).map((cells,index)=>({row:previewStart+index+1,cells}))} columns={[{title:'行',width:48,dataIndex:'row'},...Array.from({length:Math.min(sheet.colCount,200)},(_,index)=>({title:colName(index),width:125,render:(_:unknown,row:{cells:{v:unknown}[]})=>String(row.cells[index]?.v??'')}))]}/>
      {(sheet.hidden||sheet.hiddenRows.length>0||sheet.hiddenCols.length>0)&&<Checkbox disabled={busy} checked={region.hiddenConfirmed} onChange={event=>patch({hiddenConfirmed:event.target.checked})}>已核对隐藏行列</Checkbox>}
    </>}
  </LegacyDialog>;
}
