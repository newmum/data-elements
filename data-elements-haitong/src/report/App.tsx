import {reportRandomId} from './core/browserId';
import { useEffect, useRef, useState } from 'react';
import {Alert,Avatar,Button,Checkbox,Dropdown,Input,Spin,Table,Tag,message} from 'antd';
import {DownOutlined,QuestionCircleOutlined,SettingOutlined,UserOutlined} from '@ant-design/icons';
import {Select,Segmented} from './components/ReportControls';
import type { DataRow, Resource, SourceBook } from './core/model';
import { clone } from './core/model';
import { readFile } from './services/parser';
import { reportDatasourceIdFromLocation } from './services/reportApi';
import { applyTableSchema, loadUploadContext, loadUploadManifest, loadReportReceipts, prepareReportFile, parseUploadedFiles, readUploadedOriginal,
  saveUploadManifest, submitUploadTable, tableResource, tableSample, uploadOriginal,
  type UploadContext, type UploadField, type UploadFile, type UploadManifest, type UploadTable } from './services/uploadWorkbench';
import PreviewPanel from './components/PreviewPanel';
import Icon from './components/Icon';
import ReportTableDetails from './components/ReportTableDetails';
import RegistrationWorkspace from './components/RegistrationWorkspace';
import ReportDeliveryTable from './components/ReportDeliveryTable';
import type {ReportDeliveryProgress} from './components/DeliveryProgress';
import {updateReportField} from './services/fieldGovernance';
import {deliveryManifest,hasRegistrationDraft,rebaseRegistrationDraft,savedDeliveryTable,resourceWithDeliveryReceipt} from './services/reportDelivery';
import {applyParsedFile,fileParsingLocked} from './services/reportFiles';
import UploadProgress,{type UploadStage} from './components/UploadProgress';
import ProgressOverlay from './components/ProgressOverlay';
import {defaultTableType,withDefaultTableTypes,reportStepCompletion} from './services/reportProgress';
import {useRegistrationFilesPage,useReportTablesPage} from './services/useRegistrationPage';
import LegacyDialog from './components/LegacyDialog';
import { PortalEmpty as Empty } from './components/PortalUI';
import ReportLoading from './components/ReportLoading';
import ReportFileUpload from './components/ReportFileUpload';
import { getSessionRevision } from '../nifi/api/bridgeSession';
import FileParsingDialog from './components/FileParsingDialog';
import { hasReportSuccess,isReportReady,reportSchemaIssues,REPORT_BUSINESS_TYPES } from './services/reportSchema';
import { createReportSourceLoader } from './services/reportSource';
const POLICE_TENANT_LOGO = 'data:image/x-icon;base64,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AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAEep5gBtsu4CT5vlBDaG3wZFn+AGdrjsBFSz5gAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA/////////////////////////gPAf/////wYAAAYP///8AAAAAAP///AAAAAAAP//4AAAAAAAf//AAAAAAAAf/8AAAAAAAD//wAAAAAAAP//AAAAAAAA//8AAAAAAAD//4AAAAAAAP//gAAAAAAB//+AAAAAAAH//wAAAAAAAf//AAAAAAAA//4AAAAAAAB//AAAAAAAAH/8AAAAAAAAP/gAAAAAAAAf+AAAAAAAAB/wAAAAAAAAD/AAAAAAAAAP4AAAAAAAAAfgAAAAAAAAB8AAAAAAAAAHwAAAAAAAAAPAAAAAAAAAA8AAAAAAAAADgAAAAAAAAAGAAAAAAAAAAYAAAAAAAAABgAAAAAAAAAHAAAAAAAAAAcAAAAAAAAABwAAAAAAAAAHAAAAAAAAAAcAAAAAAAAADwAAAAAAAAAPAAAAAAAAAA+AAAAAAAAAD4AAAAAAAAAPgAAAAAAAAB/AAAAAAAAAH8AAAAAAAAA/4AAAAAAAAD/gAAAAAAAAf/AAAAAAAAB/8AAAAAAAAP/4AAAAAAAA//gAAAAAAAH//gAAAAAAA///AAAAAAAH//+AAAAAAA///8AAAAAAP///4AAAAAB////4AAAAAP////wAAAAD/////4AAAA//////4AAAf//////8AAP////////4f//////////////8=';
const emptyManifest:UploadManifest={revision:'',files:[],tables:[],failedTables:[],receipts:[]};
const bookKey=(entry:UploadTable,encoding=entry.parseEncoding||'utf-8',delimiter=entry.parseDelimiter||'')=>JSON.stringify([entry.fileId,encoding,delimiter]);
export default function App({onReturn,datasourceId,onDatasourceChange}:{onReturn:()=>void;datasourceId:string;onDatasourceChange:(id:string)=>void}){
  const [context,setContext]=useState<UploadContext>(),[manifest,setManifest]=useState<UploadManifest>(emptyManifest),[savedManifest,setSavedManifest]=useState<UploadManifest>(emptyManifest);
  const [busy,setBusy]=useState(''),[error,setError]=useState(''),[dirty,setDirty]=useState(false);
  const [uploadStage,setUploadStage]=useState<UploadStage>();
  const [loadPhase,setLoadPhase]=useState(0),[loadAttempt,setLoadAttempt]=useState(0);
  const [parsing,setParsing]=useState<UploadFile>(),[reportFilter,setReportFilter]=useState('all');
  const [sourceLoader]=useState(createReportSourceLoader),[switchingId,setSwitchingId]=useState('');
  const workspaceRevision=useRef(0);
  const [dataReviewOpen,setDataReviewOpen]=useState(false),[previewError,setPreviewError]=useState('');
  const [reviewOriginal,setReviewOriginal]=useState<Resource>();
  const [previewTableKey,setPreviewTableKey]=useState(''),[fileSearch,setFileSearch]=useState(''),[schemaSearch,setSchemaSearch]=useState(''),[reportSearchInput,setReportSearchInput]=useState(''),[registrationFocus,setRegistrationFocus]=useState('');
  const [comfortable,setComfortable]=useState(false),[settingsOpen,setSettingsOpen]=useState(false),[helpOpen,setHelpOpen]=useState(false);
  const [confirmSync,setConfirmSync]=useState(false),[removeId,setRemoveId]=useState('');
  const [selected,setSelected]=useState(''),[resources,setResources]=useState<Record<string,Resource>>({});
  const [deliveryKey,setDeliveryKey]=useState(''),[deliveryResources,setDeliveryResources]=useState<Record<string,Resource>>({});
  const [deliveryProgress,setDeliveryProgress]=useState<ReportDeliveryProgress>();
  const deliveryAbort=useRef<AbortController|undefined>(undefined);
  const deliveryPageSnapshot=useRef<{rows:UploadTable[];summary:{all:number;pending:number;reported:number}}|undefined>(undefined);
  const [editing,setEditing]=useState<DataRow>(),[historyOpen,setHistoryOpen]=useState(false);
  const [history,setHistory]=useState<{list:import('./services/uploadWorkbench').ReportReceipt[];total:number}>({list:[],total:0}),[historyPage,setHistoryPage]=useState(1);
  const books=useRef(new Map<string,SourceBook>()),removed=useRef<string[]>([]),locked=useRef(false);
  const originals=useRef(new Map<string,File>());
  const sessionRevision=useRef(getSessionRevision());
  function assertSession(){if(sessionRevision.current!==getSessionRevision())throw new Error('登录身份已变化，请重新打开工作台');}
  const [notice,noticeContext]=message.useMessage();
  const table=manifest.tables.find(item=>item.key===selected)||manifest.tables[0];
  const deliveryEntry=savedManifest.tables.find(item=>item.key===deliveryKey);
  const deliveryTable=deliveryEntry&&(isReportReady(deliveryEntry,savedManifest.tables)||hasReportSuccess(deliveryEntry,savedManifest.receipts))?savedDeliveryTable(savedManifest,deliveryKey):undefined,deliveryResource=deliveryTable?deliveryResources[deliveryTable.key]:undefined;
  const frozen=(entry:UploadTable)=>!!entry.tid&&manifest.receipts?.some(receipt=>receipt.tableId===entry.tid&&receipt.status==='sending')===true;
  const [schemaSearchInput,setSchemaSearchInput]=useState(''),[savingSchema,setSavingSchema]=useState(false);
  const registration=useRegistrationFilesPage(context?.context.datasourceId||'',manifest,schemaSearch);
  const reporting=useReportTablesPage(context?.context.datasourceId||'',savedManifest,fileSearch,reportFilter);
  // A manifest revision refresh must not remove the in-flight row or reset its counts.
  const deliveryPage=busy==='正在提交上报'&&deliveryPageSnapshot.current?deliveryPageSnapshot.current:reporting;
  const normalizationLoading=registration.loading||!!switchingId||savingSchema;
  function submitRegistrationSearch(){
    if(busy||normalizationLoading)return;
    const value=schemaSearchInput.trim();
    if(value!==schemaSearch)setSchemaSearch(value);
    else registration.setQuery(current=>({...current,pageNo:1}));
  }
  async function operate(label:string,action:()=>Promise<void>){
    if(locked.current)return false;locked.current=true;setBusy(label);setError('');
    try{assertSession();await action();assertSession();return true;}catch(cause){const detail=cause instanceof Error?cause.message:'操作失败，请重试';setError(detail);void notice.error(detail);return false;}
    finally{locked.current=false;setBusy('');}
  }
  useEffect(()=>{let active=true;setError('');setLoadPhase(0);void loadUploadContext(reportDatasourceIdFromLocation()).then(async loaded=>{
    if(!active)return;setLoadPhase(1);const saved=await loadUploadManifest(loaded.context.datasourceId);if(!active)return;
    saved.failedTables||=[];saved.receipts||=[];setLoadPhase(2);sessionRevision.current=getSessionRevision();setContext(loaded);const prepared=withDefaultTableTypes(saved);setManifest(prepared);setSavedManifest(prepared);setSelected(saved.tables[0]?.key||'');
  }).catch(cause=>{if(active)setError(cause.message);});return()=>{active=false;};},[loadAttempt]);
  useEffect(()=>()=>{sourceLoader.cancel();deliveryAbort.current?.abort();workspaceRevision.current++;},[sourceLoader]);
  async function switchDatasource(id:string){
    if(!context||locked.current||id===context.context.datasourceId)return;
    const revision=workspaceRevision.current;
    if((dirty||[...Object.values(resources),...Object.values(deliveryResources)].some(entry=>entry.dataVersion>0&&!entry.locked))&&!window.confirm('当前有未保存或未上报的修改，确认切换数据源？')){onDatasourceChange(context.context.datasourceId);return;}
    setSwitchingId(id);
    try{
      const success=await operate('正在切换数据源',async()=>{
        const loaded=await sourceLoader.load(id,context.sources);if(!loaded)return;
        workspaceRevision.current++;books.current.clear();originals.current.clear();removed.current=[];
        setContext({...context,context:{...context.context,datasourceId:loaded.source.id,datasourceName:loaded.source.name}});
        const prepared=withDefaultTableTypes(loaded.manifest);setManifest(prepared);setSavedManifest(prepared);setDeliveryResources({});setDeliveryKey('');setSelected(loaded.manifest.tables[0]?.key||'');setResources({});setDirty(false);
        setPreviewTableKey('');setFileSearch('');setSchemaSearch('');setSchemaSearchInput('');setReportSearchInput('');setRegistrationFocus('');reporting.setQuery(current=>({...current,pageNo:1}));setReportFilter('all');
        setParsing(undefined);setDataReviewOpen(false);setConfirmSync(false);setEditing(undefined);
        setRemoveId('');setHistoryOpen(false);setHistory({list:[],total:0});setHistoryPage(1);
        onDatasourceChange(loaded.source.id);
      });
      if(!success&&revision===workspaceRevision.current&&sessionRevision.current===getSessionRevision())onDatasourceChange(context.context.datasourceId);
    }finally{setSwitchingId('');}
  }
  useEffect(()=>{if(context&&datasourceId&&datasourceId!==context.context.datasourceId)void switchDatasource(datasourceId);},[datasourceId,context?.context.datasourceId,busy]);
  async function persist(next=manifest,normalizedKeys:string[]=[],options:{preserveDrafts?:boolean;committedKeys?:string[]}={}){
    if(!context)throw new Error('请先选择数据源');
    assertSession();const deletedIds=removed.current;const result=await saveUploadManifest(context.context.datasourceId,next,deletedIds,normalizedKeys);assertSession();
    result.failedTables||=[];result.receipts||=[];const saved=withDefaultTableTypes(result);
    const draft=options.preserveDrafts?rebaseRegistrationDraft(savedManifest,manifest,saved,options.committedKeys||normalizedKeys):saved;
    setSavedManifest(saved);setManifest(draft);setDeliveryResources({});removed.current=[];setDirty(hasRegistrationDraft(saved,draft));return saved;
  }
  async function upload(files:File[]){if(locked.current)return;setUploadStage({phase:0,completed:0,total:files.length});try{await operate('正在上传并识别文件',async()=>{
    if(files.length>20||manifest.files.length+files.length>100)throw new Error('每次最多20份文件，每个数据源最多100份');
    const next=clone(savedManifest),ids:string[]=[];let completed=0;
    for(const file of files){try{
      if(!/\.(csv|xlsx|xls|txt|json)$/i.test(file.name)||!file.size||file.size>10*1024*1024)throw new Error('支持 10 MB 以内的 CSV、Excel、TXT、JSON 数据文件');
      assertSession();const id=await uploadOriginal(file);originals.current.set(id,file);ids.push(id);next.files.push({fileId:id,fileName:file.name});
    }catch(cause){next.failedTables!.push({key:reportRandomId(),fileId:'',fileName:file.name,tableName:'',tableNameCn:file.name,fields:[],fieldCount:0,recordCount:0,status:'failed',error:(cause as Error).message});}finally{setUploadStage({phase:0,completed:++completed,total:files.length});}}
    if(ids.length){setUploadStage({phase:1,completed:files.length,total:files.length});const result=await parseUploadedFiles(ids);const names=new Set(next.tables.map(entry=>entry.tableName.toLowerCase()));
      for(const file of result.files)for(const parsed of file.tables){const row={...parsed,fileId:file.fileId,fileName:file.fileName};
        if(row.status==='failed'){next.failedTables!.push(row);continue;}
        const stem=row.tableName.slice(0,55);let suffix=1;while(names.has(row.tableName.toLowerCase()))row.tableName=stem+'_'+(++suffix);names.add(row.tableName.toLowerCase());
        row.tableNameCn=/[\u4e00-\u9fff]/.test(row.tableNameCn)?row.tableNameCn:'数据表 '+row.tableNameCn;
        next.tables.push(defaultTableType({...row,normalized:false}));
      }}
    setUploadStage({phase:2,completed:files.length,total:files.length});const saved=await persist(next,[],{preserveDrafts:true});setSelected(saved.tables[0]?.key||'');
    void notice.success('上传完成，请在第二步规范数据');
  });}finally{setUploadStage(undefined);}}
  async function removeFile(fileId:string){await operate('正在移除文件',async()=>{
    if(dirty)throw new Error('请先保存当前数据规范');
    if(manifest.receipts?.some(receipt=>receipt.fileId===fileId&&receipt.status==='success'))throw new Error('已上报文件保留结果记录，请追加新的数据文件');
    const deleted=manifest.tables.filter(entry=>entry.fileId===fileId);removed.current.push(...deleted.flatMap(entry=>entry.tid?[entry.tid]:[]));
    await persist({...manifest,files:manifest.files.filter(file=>file.fileId!==fileId),tables:manifest.tables.filter(entry=>entry.fileId!==fileId),failedTables:manifest.failedTables?.filter(entry=>entry.fileId!==fileId)});
    setResources(current=>Object.fromEntries(Object.entries(current).filter(([key])=>!deleted.some(entry=>entry.key===key))));setRemoveId('');setPreviewTableKey('');
  });}
  function changeTable(key:string,property:'tableName'|'tableNameCn'|'businessType',value:string){if(manifest.tables.some(entry=>entry.key===key&&frozen(entry)))return;setManifest(current=>({...current,tables:current.tables.map(entry=>entry.key===key?{...entry,[property]:value,normalized:false}:entry)}));setDirty(true);}
  function changeField(key:string,index:number,patch:Partial<UploadField>){const target=manifest.tables.find(entry=>entry.key===key);if(!target||frozen(target))return;setManifest(current=>({...current,tables:current.tables.map(entry=>entry.key===key?{...entry,normalized:false,fields:updateReportField(entry.fields,index,patch)}:entry)}));setDirty(true);}
  async function loadBook(entry:UploadTable,encoding=entry.parseEncoding||'utf-8',delimiter=entry.parseDelimiter||'',force=false){
    const revision=workspaceRevision.current;
    const assertWorkspace=()=>{assertSession();if(revision!==workspaceRevision.current)throw new Error('数据源已切换，旧文件读取结果已丢弃');};
    const key=bookKey(entry,encoding,delimiter);let book=books.current.get(key);if(book&&!force)return book;
    const original=originals.current.get(entry.fileId)||await readUploadedOriginal(entry.fileId,entry.fileName);
    assertWorkspace();originals.current.set(entry.fileId,original);
    try{book=await readFile(original,encoding,delimiter,new AbortController().signal);}catch(cause){if(encoding!=='utf-8'||!/\.(csv|txt)$/i.test(original.name))throw cause;book=await readFile(original,'gb18030',delimiter,new AbortController().signal);}
    assertWorkspace();books.current.set(key,book);return book;
  }
  async function loadDeliveryData(entry:UploadTable){await operate('正在读取已保存规范数据',async()=>{
    const snapshot=savedDeliveryTable(savedManifest,entry.key),book=await loadBook(snapshot),loaded=resourceWithDeliveryReceipt(tableResource(book,snapshot),snapshot,savedManifest.receipts||[]);
    setDeliveryResources(current=>({...current,[snapshot.key]:loaded}));
  });}
  async function normalize(key=table?.key){if(locked.current)return;setSavingSchema(true);try{await operate('正在保存数据规范',async()=>{
    const target=manifest.tables.find(entry=>entry.key===key);if(!target)throw new Error('请选择待规范的数据表');if(frozen(target))throw new Error('该表正在上报，请完成后保存规范');
    const issues=reportSchemaIssues(target,savedManifest.tables);if(issues.length)throw new Error(issues.slice(0,3).join('；'));
    const reviewed=resources[target.key]?applyTableSchema(resources[target.key],target):tableResource(await loadBook(target),target);
    await prepareReportFile(reviewed);
    await persist({...savedManifest,tables:savedManifest.tables.map(entry=>entry.key===target.key?{...target,recordCount:reviewed.rows.length}:entry)},[target.key],{preserveDrafts:true});
    setResources(current=>({...current,[target.key]:reviewed}));setDataReviewOpen(false);setSelected(target.key);void notice.success('规范已通过，可在第三步上报');
  });}finally{setSavingSchema(false);}}
  async function submit(){if(!deliveryTable||!deliveryResource||!context||locked.current)return;
    const target=deliveryTable,prepared=deliveryResource;
    deliveryPageSnapshot.current={rows:reporting.rows.map(row=>row.key===target.key?{...row,recordCount:prepared.rows.length}:row),summary:reporting.summary};
    const controller=new AbortController();deliveryAbort.current=controller;
    try{await operate('正在提交上报',async()=>{
      if(!isReportReady(target,savedManifest.tables))throw new Error('该表尚未保存通过规范');
      const file=await prepareReportFile(prepared,value=>{
        if(!controller.signal.aborted)setDeliveryProgress({tableKey:target.key,phase:'preparing',...value});
      },controller.signal);
      setDeliveryProgress({tableKey:target.key,phase:'saving',completed:0});
      const saved=await persist(deliveryManifest(savedManifest,target.key,prepared.rows.length),[],{preserveDrafts:true});
      const current=savedDeliveryTable(saved,target.key);
      setDeliveryResources(current=>({...current,[target.key]:prepared}));
      assertSession();let result;
      setDeliveryProgress({tableKey:target.key,phase:'uploading',completed:0,total:file.size});
      try{result=await submitUploadTable(context.context.datasourceId,current,prepared,{file,signal:controller.signal,onUploadProgress:value=>{
        if(!controller.signal.aborted)setDeliveryProgress({tableKey:target.key,phase:value.complete?'waiting':'uploading',completed:value.loaded,total:value.total});
      }});}
      catch(cause){
        if(controller.signal.aborted)throw cause;
        try{
          const latest=withDefaultTableTypes(await loadUploadManifest(context.context.datasourceId));assertSession();
          const draft=rebaseRegistrationDraft(savedManifest,manifest,latest);
          setSavedManifest(latest);setManifest(draft);setDirty(hasRegistrationDraft(latest,draft));
          setDeliveryResources(cache=>({...cache,[target.key]:resourceWithDeliveryReceipt(prepared,target,latest.receipts||[])}));
          registration.retry();reporting.retry();
        }catch{if(sessionRevision.current===getSessionRevision())setDeliveryResources(cache=>({...cache,[target.key]:{...prepared,locked:true,sync:Object.fromEntries(prepared.rows.map(row=>[row.id,'unknown' as const]))}}));}
        throw cause;
      }
      const withReceipt=(state:UploadManifest)=>({...state,receipts:[...(state.receipts||[]).filter(row=>row.id!==result.id),result]});
      setSavedManifest(withReceipt);setManifest(withReceipt);
      setDeliveryResources(current=>({...current,[target.key]:{...prepared,locked:true,sync:Object.fromEntries(prepared.rows.map(row=>[row.id,'success' as const]))}}));
      registration.retry();reporting.retry();
      reporting.setQuery(current=>({...current,pageNo:1}));void notice.success('所选数据表已成功上报');
    });}finally{
      if(deliveryAbort.current===controller)deliveryAbort.current=undefined;
      if(!controller.signal.aborted)setDeliveryProgress(undefined);
    }
  }
  async function refreshDeliveryReceipts(){if(!context)return;await operate('正在读取上报回执',async()=>{
    const latest=withDefaultTableTypes(await loadUploadManifest(context.context.datasourceId));assertSession();
    const draft=rebaseRegistrationDraft(savedManifest,manifest,latest);
    setSavedManifest(latest);setManifest(draft);setDirty(hasRegistrationDraft(latest,draft));
    setDeliveryResources(cache=>Object.fromEntries(Object.entries(cache).map(([key,resource])=>{
      const table=latest.tables.find(row=>row.key===key);return [key,table?resourceWithDeliveryReceipt(resource,table,latest.receipts||[]):resource];
    })));
    registration.retry();reporting.retry();
  });}

  function entryForFile(file:UploadFile):UploadTable{return manifest.tables.find(entry=>entry.fileId===file.fileId)||{key:file.fileId,fileId:file.fileId,fileName:file.fileName,tableName:'',tableNameCn:file.fileName,fields:[],fieldCount:0,recordCount:0,status:'failed'};}
  async function saveParsed(file:UploadFile,tables:UploadTable[],book:SourceBook){
    const saved=await operate('正在保存文件解析',async()=>{
      const next=applyParsedFile(savedManifest,file,tables);await persist(next,[],{preserveDrafts:true,committedKeys:manifest.tables.filter(entry=>entry.fileId===file.fileId).map(entry=>entry.key)});
      for(const entry of tables)books.current.set(bookKey(entry),book);
      setResources(current=>Object.fromEntries(Object.entries(current).filter(([,resource])=>resource.source.fileId!==file.fileId)));
      setRegistrationFocus(tables[0]?.key||'');setSelected(tables[0]?.key||'');
    });
    if(!saved)throw new Error('保存解析失败，请查看页面提示后重试');
  }
  function focusRegistration(entry:UploadTable){
    setRegistrationFocus(entry.key);setSchemaSearchInput(entry.tableName);setSchemaSearch(entry.tableName);registration.setQuery(current=>({...current,pageNo:1}));
    document.getElementById('report-registration')?.scrollIntoView({block:'start'});
  }
  async function reopenNorm(entry:UploadTable){await operate('正在返回数据规范',async()=>{
    if(frozen(entry))throw new Error('该表正在上报，请完成后保存规范');
    await persist({...savedManifest,tables:savedManifest.tables.map(table=>table.key===entry.key?{...table,normalized:false}:table)},[],{preserveDrafts:true,committedKeys:[entry.key]});
    setPreviewTableKey('');focusRegistration(entry);
  });}
  async function reviewData(entry:UploadTable){
    setSelected(entry.key);setDataReviewOpen(true);setPreviewError('');setReviewOriginal(undefined);
    const success=await operate('正在读取数据表样例',async()=>{
      setReviewOriginal(tableSample(await loadBook(entry),entry));
    });
    if(!success)setPreviewError('样例读取失败；仍可查看字段信息，请关闭后重试。');
  }
  async function prepareReport(entry:UploadTable){
    const snapshot=savedDeliveryTable(savedManifest,entry.key);
    if(!isReportReady(snapshot,savedManifest.tables)||hasReportSuccess(snapshot,savedManifest.receipts))return;
    setDeliveryKey(snapshot.key);
    if(deliveryResources[snapshot.key]){setConfirmSync(true);return;}
    await operate('正在读取上报数据',async()=>{
      const book=await loadBook(snapshot),loaded=tableResource(book,snapshot);
      setDeliveryResources(current=>({...current,[snapshot.key]:loaded}));setConfirmSync(true);
    });
  }
  function updateRow(row:DataRow,remove=false){if(!deliveryTable||!deliveryResource||deliveryResource.locked||locked.current)return;
    if(remove&&deliveryResource.rows.length<=1){void notice.warning('至少保留一行数据；也可在第二步移除原始文件');return;}
    const next={...deliveryResource,dataVersion:deliveryResource.dataVersion+1,rows:remove?deliveryResource.rows.filter(item=>item.id!==row.id):deliveryResource.rows.map(item=>item.id===row.id?row:item)};
    setDeliveryResources(current=>({...current,[deliveryTable.key]:next}));setEditing(undefined);
  }
  useEffect(()=>{let active=true;const revision=workspaceRevision.current;if(historyOpen&&context)void loadReportReceipts(context.context.datasourceId,historyPage).then(result=>{if(active&&revision===workspaceRevision.current&&sessionRevision.current===getSessionRevision())setHistory(result);}).catch(cause=>{if(active&&revision===workspaceRevision.current&&sessionRevision.current===getSessionRevision())setError(cause.message);});return()=>{active=false;};},[historyOpen,historyPage,context]);
  function requestReturn(){
    if((dirty||[...Object.values(resources),...Object.values(deliveryResources)].some(entry=>entry.dataVersion>0&&!entry.locked))&&!window.confirm('当前有未保存或未上报的修改，确认返回上一页面？'))return;
    onReturn();
  }
  async function dismissFailure(key:string){await operate('正在移除识别结果',async()=>{
    if(dirty)throw new Error('请先保存当前数据规范');
    await persist({...manifest,failedTables:manifest.failedTables?.filter(entry=>entry.key!==key)});
  });}
  if (!context) return <ReportLoading phase={loadPhase} error={error} onRetry={() => setLoadAttempt(value => value + 1)} onReturn={onReturn}/>;
  const canSubmit = !!deliveryTable && isReportReady(deliveryTable,savedManifest.tables) && !!deliveryResource && !deliveryResource.locked && !busy && !savedManifest.receipts?.some(receipt=>receipt.tableId===deliveryTable.tid&&['success','sending'].includes(receipt.status));
  const pendingTables=manifest.tables.filter(entry=>!isReportReady(entry,manifest.tables)&&!hasReportSuccess(entry,manifest.receipts));
  const failed = manifest.failedTables || [];
  const completedSteps=reportStepCompletion(manifest);
  const openTablePreview=(entry:UploadTable)=>{
    setDeliveryKey(entry.key);setPreviewTableKey(entry.key);if(!deliveryResources[entry.key])void loadDeliveryData(entry);
  };
  function submitReportSearch(){if(busy||reporting.loading)return;const value=reportSearchInput.trim();if(value!==fileSearch)setFileSearch(value);else reporting.setQuery(current=>({...current,pageNo:1}));}
  return <div className={`portal-shell restored-report ${comfortable ? 'comfortable' : ''}`}>{noticeContext}
    <header className="portal-header"><div className="portal-header-inner">
      <div className="portal-brand"><span className="portal-emblem"><img src={POLICE_TENANT_LOGO} alt="公安租户"/></span><div><h1>数据上报工作台</h1><p>当前部门：{context.context.user.orgName || '未设置所属部门'}</p></div></div>
      <div className="portal-header-actions"><button onClick={requestReturn}><Icon name="arrow" style={{transform:'rotate(180deg)'}}/>返回</button><button onClick={() => { setHistoryPage(1); setHistoryOpen(true); }}><Icon name="history"/>上报记录</button>
        <Dropdown trigger={['click']} placement="bottomRight" overlayClassName="report-profile-dropdown" menu={{style:{minWidth:140},items:[{key:'help',icon:<QuestionCircleOutlined/>,label:'使用帮助'},{key:'settings',icon:<SettingOutlined/>,label:'系统设置'}],onClick:({key})=>key==='help'?setHelpOpen(true):setSettingsOpen(true)}}><Button type="text" className="report-profile-trigger" aria-label="用户菜单"><Avatar size={28} icon={<UserOutlined/>}/><span className="report-profile-name">{context.context.user.realName || context.context.user.userName || '当前用户'}</span><DownOutlined className="report-profile-arrow"/></Button></Dropdown>
      </div>
    </div></header>
    <main className="portal-main">
      {error && <Alert type="error" showIcon closable onClose={()=>setError('')} message={error}/>}
      {busy && !uploadStage && !savingSchema && !switchingId && <Alert type="info" role="status" message={<><Spin size="small"/> {busy}</>}/>}

      <section className={`portal-upload report-upload-panel ${uploadStage?'is-uploading':''}`} aria-label="选择数据源并上传文件" aria-busy={!!uploadStage}>
        <div className="report-upload-content">
          <div className="report-step-copy"><span className={`report-target-step ${completedSteps.uploaded?'is-complete':'is-active'}`}>第 1 步</span><h2>选择数据源并上传文件</h2><small id="report-upload-file-requirements" className="report-upload-formats">CSV / Excel / TXT / JSON · 单文件 10 MB</small></div>
          <div className="report-upload-source-row"><div className="report-upload-source"><Select aria-label="选择上报数据源" disabled={!!busy} loading={!!switchingId} value={context.context.datasourceId} options={context.sources.map(source=>({value:source.id,label:source.name}))} onChange={id=>void switchDatasource(id)}/></div><ReportFileUpload disabled={!!busy} hasFiles={manifest.files.length>0} onFiles={files=>void upload(files)}/></div>
          <div className="report-upload-metrics">
            <div><span><Icon name="file" size={14}/>已上传文件</span><strong>{manifest.files.length}<small>份</small></strong></div>
            <div><span><Icon name="table" size={14}/>数据表</span><strong>{manifest.tables.length}<small>张</small></strong></div>
            <div><span><Icon name="database" size={14}/>数据量</span><strong>{manifest.tables.reduce((sum,item)=>sum+(item.recordCount||0),0).toLocaleString()}<small>条</small></strong></div>
          </div>
        </div>
        <div className="upload-visual" aria-hidden="true"><div className="upload-tagline"><span>让数据多跑路</span><span>让民警少跑腿</span><div className="tagline-lines"/></div><div className="upload-illustration"><div className="paper-back"/><div className="paper-front"><i/><i/><i/><span><Icon name="upload" size={27}/></span></div></div></div>
        {uploadStage&&<UploadProgress stage={uploadStage}/>}
      </section>
      <section id="report-registration" className="report-target-selector report-normalize-section" aria-label="规范数据表登记信息" aria-busy={normalizationLoading}>
        <div className="report-normalize-heading"><div className="report-target-copy"><span className={`report-target-step ${completedSteps.normalized?'is-complete':'is-active'}`}>第 2 步</span><h2>规范数据表登记信息 <small>共 {manifest.tables.length} 张 · 待规范 {pendingTables.length}{failed.length>0&&<> · 待解析 {failed.length}</>}</small></h2></div>
          <div className="report-registration-tools"><Select aria-label="登记文件排序" disabled={!!busy||normalizationLoading} value={`${registration.query.sortField}:${registration.query.sortDir}`} onChange={value=>{const [sortField,sortDir]=value.split(':');registration.setQuery(current=>({...current,pageNo:1,sortField,sortDir:sortDir as 'asc'|'desc'}));}} options={[{value:'updatedAt:desc',label:'最近更新'},{value:'updatedAt:asc',label:'最早更新'},{value:'fileName:asc',label:'文件名升序'},{value:'fileName:desc',label:'文件名降序'},{value:'fieldCount:desc',label:'字段数降序'},{value:'recordCount:desc',label:'数据量降序'}]}/>
          <Input className="report-registration-search" allowClear aria-label="搜索登记数据表" placeholder="搜索文件或表名" value={schemaSearchInput} disabled={!!busy||normalizationLoading}
            onChange={event=>{const value=event.target.value;setSchemaSearchInput(value);if(!value&&schemaSearch)setSchemaSearch('');}}
            onPressEnter={event=>{if(!event.nativeEvent.isComposing)submitRegistrationSearch();}}
            suffix={<Button type="text" size="small" className="report-registration-search-button" aria-label="搜索登记数据表" disabled={!!busy||normalizationLoading} icon={<Icon name="search" size={16}/>} onClick={()=>submitRegistrationSearch()}/>}/></div></div>
        {registration.error&&<Alert type="error" showIcon message={registration.error} action={<Button onClick={registration.retry}>重试</Button>}/>}
        <RegistrationWorkspace key={context.context.datasourceId} groups={registration.groups} manifest={manifest} busy={!!busy||normalizationLoading} error={error} focusKey={registrationFocus}
          onSelect={entry=>setSelected(entry.key)} onTableChange={changeTable} onFieldChange={changeField} onSave={key=>void normalize(key)} onPreview={entry=>void reviewData(entry)}
          onParse={group=>{if(group.fileId&&!group.locked&&!fileParsingLocked(group.fileId,manifest))setParsing({fileId:group.fileId,fileName:group.fileName});}} onRemove={fileId=>{if(dirty){setError('请先保存当前数据规范');return;}setRemoveId(fileId);}} onDismiss={key=>void dismissFailure(key)}/>
        {normalizationLoading&&!uploadStage&&<ProgressOverlay title={savingSchema?'正在保存登记信息':switchingId?'正在切换数据源':'正在加载登记信息'} description={savingSchema?'保存完成后自动更新数据表':'请稍候，正在读取数据表'} icon={savingSchema?'save':'table'} keepVisible/>}
      </section>

      <section className="portal-data-section report-table-delivery" aria-label="选择数据表并上报数据" aria-busy={reporting.loading&&busy!=='正在提交上报'}>
        <div className="report-data-flow"><div className="report-final-step"><span className={`report-target-step ${completedSteps.reported?'is-complete':'is-active'}`}>第 3 步</span><h2>选择数据表并上报数据</h2></div>
          <div className="report-delivery-tools"><Segmented aria-label="上报状态" value={reportFilter} options={[{label:`全部 ${deliveryPage.summary.all}`,value:'all'},{label:`待上报 ${deliveryPage.summary.pending}`,value:'pending'},{label:`已上报 ${deliveryPage.summary.reported}`,value:'reported'}]} onChange={value=>setReportFilter(value)}/><Select aria-label="上报表排序" disabled={!!busy||reporting.loading} value={`${reporting.query.sortField}:${reporting.query.sortDir}`} options={[{value:'updatedAt:desc',label:'最近更新'},{value:'updatedAt:asc',label:'最早更新'},{value:'tableNameCn:asc',label:'表名升序'},{value:'tableNameCn:desc',label:'表名降序'},{value:'recordCount:desc',label:'数据量降序'}]} onChange={value=>{const [sortField,sortDir]=value.split(':');reporting.setQuery(current=>({...current,pageNo:1,sortField,sortDir:sortDir as 'asc'|'desc'}));}}/>
          <Input allowClear aria-label="搜索规范数据表" placeholder="搜索表名或来源文件" value={reportSearchInput} disabled={!!busy||reporting.loading} onChange={event=>{setReportSearchInput(event.target.value);if(!event.target.value&&fileSearch)setFileSearch('');}} onPressEnter={event=>{if(!event.nativeEvent.isComposing)submitReportSearch();}} suffix={<Button type="text" size="small" className="report-registration-search-button" aria-label="搜索规范数据表" icon={<Icon name="search" size={16}/>} disabled={!!busy||reporting.loading} onClick={submitReportSearch}/>}/></div></div>
        {reporting.error&&<Alert type="error" showIcon message={reporting.error} action={<Button onClick={reporting.retry}>重试</Button>}/>}
        <ReportDeliveryTable rows={deliveryPage.rows} manifest={savedManifest} busy={!!busy||reporting.loading} submittingKey={busy==='正在提交上报'?deliveryKey:undefined} onReport={entry=>void prepareReport(entry)} onPreview={openTablePreview} progress={deliveryProgress} onRefresh={()=>void refreshDeliveryReceipts()}/>
        {reporting.loading&&busy!=='正在提交上报'&&<ProgressOverlay title="正在加载规范数据表" description="请稍候，正在核对上报状态" keepVisible/>}
      </section>
    </main>
    <LegacyDialog open={!!previewTableKey} onClose={()=>setPreviewTableKey('')} className="report-dialog--compact report-delivery-preview-dialog" ariaLabel="上报数据查看" title={`上报数据 · ${deliveryTable?.tableNameCn||'数据表'}`}>
      {error&&<Alert type="error" showIcon message={error}/>}
      {deliveryResource&&deliveryKey===previewTableKey?<PreviewPanel key={deliveryResource.id} resource={busy||switchingId?{...deliveryResource,locked:true}:deliveryResource} readOnly={!!deliveryTable&&hasReportSuccess(deliveryTable,savedManifest.receipts)} compact sampleLimit={10} showDeliveryStatus dictionaries={[]} onEdit={row=>setEditing(clone(row))} onDelete={row=>updateRow(row,true)}/>:<div className="report-file-read"><Spin/><span>读取规范数据</span></div>}
    </LegacyDialog>
    <FileParsingDialog key={parsing?.fileId||'closed'} file={parsing} tables={manifest.tables.filter(entry=>entry.fileId===parsing?.fileId)} loadBook={(encoding,delimiter)=>loadBook(entryForFile(parsing!),encoding,delimiter,true)} onSave={saveParsed} onClose={()=>setParsing(undefined)}/>
    <ReportTableDetails key={dataReviewOpen?table?.key:'closed'} entry={dataReviewOpen?table:undefined} original={reviewOriginal} error={previewError} onClose={()=>setDataReviewOpen(false)}/>
    <LegacyDialog title={<span className="dialog-title"><Icon name="history"/>上报记录</span>} ariaLabel="上报记录" open={historyOpen} onClose={()=>setHistoryOpen(false)} className="report-dialog--wide" footer={<Button onClick={()=>setHistoryOpen(false)}>关闭</Button>}><Table rowKey="id" dataSource={history.list} pagination={{showSizeChanger:false,current:historyPage,pageSize:10,total:history.total,onChange:setHistoryPage}} columns={[
      {title:'原始文件',dataIndex:'fileName'},{title:'数据表',dataIndex:'tableName'},{title:'状态',dataIndex:'status',render:value=><Tag color={value==='success'?'success':value==='sending'?'processing':'error'}>{value==='success'?'上报成功':value==='sending'?'处理中':'上报失败'}</Tag>},
      {title:'时间',dataIndex:'reportedAt',render:value=>value?new Date(value).toLocaleString('zh-CN',{timeZone:'Asia/Shanghai',hour12:false}):'—'},{title:'说明',dataIndex:'message'},
    ]}/></LegacyDialog>
    <LegacyDialog title={<span className="dialog-title"><Icon name="edit"/>编辑本次上报数据</span>} ariaLabel="编辑本次上报数据" open={!!editing} onClose={()=>setEditing(undefined)} className="report-dialog--medium" footer={<><span className="report-dialog-hint">仅修改本次上报内容</span><Button onClick={()=>setEditing(undefined)}>取消</Button><Button type="primary" onClick={()=>editing&&updateRow(editing)}>保存修改</Button></>}>
      {deliveryResource?.schema.fields.map(field=><label className="report-row-editor" key={field.id}>{field.zh||field.en}<small>{field.en}</small><Input value={String(editing?.values[field.id]??'')} onChange={event=>setEditing(row=>row?{...row,values:{...row.values,[field.id]:event.target.value}}:row)}/></label>)}
    </LegacyDialog>
    <LegacyDialog open={confirmSync} onClose={()=>setConfirmSync(false)} className="report-dialog--narrow" ariaLabel="确认上报" title={<span className="dialog-title"><Icon name="upload"/>确认上报</span>} footer={<><Button onClick={()=>setConfirmSync(false)}>取消</Button><Button type="primary" disabled={!canSubmit} onClick={()=>{setConfirmSync(false);void submit();}}>确认上报</Button></>}>
      {deliveryTable && deliveryResource && <p className="report-submit-confirm">确认上报 <strong>“{deliveryTable.tableNameCn}”</strong>（{deliveryTable.tableName}）的 <strong>{deliveryResource.rows.length}</strong> 条数据吗？<small>来源文件：{deliveryTable.fileName}</small><small className="report-confirm-target"><Icon name="database" size={15}/>ODS / 非结构化存储（FJGHCC）</small><small>投递目录：{context.context.datasourceId}/{deliveryTable.tableName}/</small></p>}
    </LegacyDialog>
    <LegacyDialog title="从工作区移除文件？" open={!!removeId} onClose={()=>setRemoveId('')} busy={!!busy} className="report-dialog--narrow" footer={<><Button disabled={!!busy} onClick={()=>setRemoveId('')}>取消</Button><Button danger type="primary" loading={!!busy} onClick={()=>removeId&&void removeFile(removeId)}>确认移除</Button></>}><p>移除当前数据源中的文件与表清单，原始文件仍然保留。已成功上报的文件保留结果记录，请通过追加文件上报新数据。</p></LegacyDialog>
    <LegacyDialog title="系统设置" open={settingsOpen} onClose={()=>setSettingsOpen(false)} className="report-dialog--narrow" footer={<Button type="primary" onClick={()=>setSettingsOpen(false)}>完成</Button>}><Checkbox checked={comfortable} onChange={event=>setComfortable(event.target.checked)}>舒适阅读模式</Checkbox></LegacyDialog>
    <LegacyDialog title="使用帮助" open={helpOpen} onClose={()=>setHelpOpen(false)} className="report-dialog--medium" footer={<Button onClick={()=>setHelpOpen(false)}>关闭</Button>}><div className="help-content"><h2>数据上报操作指引</h2>{[
      ['01','选择数据源并上传文件','上传有表头的 CSV、TXT、Excel 或包含对象记录的 JSON，可追加多份文件、多工作表及多个数据区域；无法识别的内容会独立提示。'],
      ['02','规范数据表登记信息','选择表类型，配置字段名、统一格式和时间戳，保存通过后继续保留在本步，并可在第三步上报。'],
      ['03','选择数据表并上报数据','只显示已通过规范的数据，分为待上报和已上报。'],
    ].map(([number,title,text])=><section key={number}><span>{number}</span><div><h3>{title}</h3><p>{text}</p></div></section>)}</div></LegacyDialog>
  </div>;
}
