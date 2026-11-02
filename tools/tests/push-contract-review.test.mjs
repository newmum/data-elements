import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import test from 'node:test';
const root=fileURLToPath(new URL('../../',import.meta.url));
const require=createRequire(path.join(root,'data-elements-chengtian/package.json'));
const Vue=require('vue');
const {ref,computed,watch}=Vue;
const {renderToString}=require('vue/server-renderer');
const file='lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/04.登记内容审查(register-confirm).vue';
const source=fs.readFileSync(path.join(root,file),'utf8').replace(/\r\n?/g,'\n');
const start=source.indexOf('// Push contract reads stay bounded');
const blockEnd=source.indexOf('function openReportWorkspace()',start);
assert.ok(start>=0 && blockEnd>start,'push contract source boundaries must exist');
const block=source.slice(start,blockEnd);
const resetStart=source.indexOf('watch([sourceTid, isDataPushSource]');
const resetEnd=source.indexOf('watch([sourceDetailLoading, pushDatasourceRegistered]',resetStart);
assert.ok(resetStart>=0 && resetEnd>resetStart,'source reset watcher must exist');
const resetWatcher=source.slice(resetStart,resetEnd);
const queueStart=source.indexOf('function drainFieldLoadQueue()');
const queueEnd=source.indexOf('watch(\n  () => [sourceTid.value',queueStart);
assert.ok(queueStart>=0 && queueEnd>queueStart,'field batch queue source boundaries must exist');
const queue=source.slice(queueStart,queueEnd);
function fixture({status=0,count=20,remote,loading=false,contentType='STRUCTURED',nodes}={}) {
  const reads=[],writes=[],metadata=[],copied=[],nodeReads=[];
  const tables=ref(Array.from({length:count},(_,i)=>({tid:'t'+i,tableName:'table_'+i,fields:[]})));
  const sourceRef=ref({tid:'source',dbName:'测试推送源',assetStatus:status,pushDataType:contentType,storageDomain:'GAW'});
  const state={isProxyAccess:ref(false),source:sourceRef,sourceTid:ref('source'),sourceDetailLoading:ref(loading),tables,connectionInfoExpanded:ref(true),
    pushContract:ref(null),pushContractLoading:ref(false),pushContractError:ref(''),pushContractLoadedDatasourceId:ref(''),pushContractOpenTables:ref([]),
    pushCredential:ref(null),pushCredentialError:ref(''),pushCredentialLoading:ref(false),pushCredentialDatasourceId:ref(''),isDataPushSource:ref(true),
    isDataUploadSource:ref(false),fileRegistrationRows:ref(Array.from({length:count},(_,i)=>({tableId:'file'+i,collectionId:'file_collection_'+i,fileName:'登记文件'+i,fileDescription:'说明',fileFormat:'pdf',maxFileSizeMb:20,maxFileBytes:20971520,fieldCount:6}))),store:{state:{steps:[]}},computed,ref,watch,governanceConfig:table=>table?.fieldGovernanceConfig||null,catalogFor:()=>null,fieldsFor:table=>table.fields||[],tableFieldKey:table=>table.tableName,fieldLoadingFor:()=>false,
    $message:{success(){},warning(){}},ElMessageBox:{confirm:async()=>{}},navigator:{clipboard:{writeText:async(text)=>{copied.push(text)}}},
    $common:{post:async(url,body)=>{nodeReads.push({url,body});return {list:nodes || [{networkCode:"GAW",baseUrl:"https://gaw.example.test",enabled:1,isDefault:1},{networkCode:"HLW",baseUrl:"https://internet.example.test",enabled:1,isDefault:1}]};},getSilently:async(url,params)=>{reads.push({url,params});return remote?remote(params):{datasourceId:params.datasourceId,tables:tables.value.map(t=>({tableId:t.tid,tableName:t.tableName,schemaVersion:3,fields:[{fieldName:'id',dataType:'bigint',required:true}],fieldCount:1}))}},postSilently:async(url,body)=>{writes.push({url,body});return {datasourceId:body.datasourceId,clientId:'push-source',revealed:false}}},
  };
  const create=new Function(...Object.keys(state), `
    const fieldLoadQueue=[];let fieldLoadsInFlight=0,fieldBatchScheduled=false,fieldLoadGeneration=0;
    const maxFieldIdsPerBatch=50;const fieldLoadStates=ref({});
    function fieldsLoadedFor(table){return fieldLoadStates.value[table.tableName]==='loaded'}
    function fieldLoadingFor(table){return ['queued','loading'].includes(fieldLoadStates.value[table.tableName])}
    function updateFieldLoadState(key,value){fieldLoadStates.value[key]=value}
    function requestTableFields(table,key,fields){table.fields=fields;updateFieldLoadState(key,'loaded')}
    ${queue}
    ${block}
    ${resetWatcher}
    return {ensurePushCredential,pushErrorMessage,loadPushContract,refreshRegistrationPushContract,issuePushCredential,pushContractIsPreview,pushContractSample,pushContractEndpointItems,pushAttachmentDescription,pushInterfaceType,selectPushInterface,pushIsAttachmentMode,pushVisibleTables,pushSelectedParameters,pushRequestContentType,pushContractHeaders,pushFileWorkflow,copyPushFileStageSample,pushProtocolUrl,pushProtocolBaseUrl,pushProtocolNodeError,pushFileMode,queueIdle:()=>!fieldLoadsInFlight&&!fieldBatchScheduled&&!fieldLoadQueue.length};`);
  // The real bounded queue uses the ordinary metadata GET; formal contracts use silent GET.
  state.$common.get=async(url,params)=>{const ids=params.tid.split(',');metadata.push(ids);return Object.fromEntries(ids.map(id=>[id,[{columnName:'id',columnType:'bigint',nullable:0,ordinalPosition:1}]]));};
  const api=create(...Object.values(state));
  return {state,api,reads,writes,metadata,copied,nodeReads,async flush(){for(let i=0;i<20&&!api.queueIdle();i++)await new Promise(resolve=>setImmediate(resolve));assert.ok(api.queueIdle());}};
}
test('unfinished source reads saved contract and explicitly issues its key without requiring registration completion; fields use bounded batches',async()=>{
  for(const count of [20,100]){
    const f=fixture({count,remote:params=>({datasourceId:params.datasourceId,tables:[]})});await f.api.loadPushContract();await f.flush();f.api.refreshRegistrationPushContract();
    assert.equal(f.reads.length,1);assert.equal(f.writes.length,0);assert.equal(f.metadata.length,Math.ceil(count/50));
    assert.ok(f.metadata.every(ids=>ids.length<=50));assert.equal(new Set(f.metadata.flat()).size,count);
    assert.equal(f.state.pushContract.value.tableSource,'registration');assert.equal(f.state.pushContract.value.tables.length,count);
    const sample=JSON.parse(f.api.pushContractSample(f.state.pushContract.value.tables[0]));assert.equal(sample.deliveryType,'FTP');assert.equal(sample.records[0].id,0);assert.ok(!('schemaVersion' in sample));
    await f.api.issuePushCredential('source');assert.equal(f.writes.length,1);assert.equal(f.reads.length,2);assert.equal(f.state.source.value.assetStatus,0);
    await f.api.issuePushCredential('source');assert.equal(f.writes.length,1);
  }
});
test('registered sources read one authoritative contract for 20/100 tables and keep credential operations explicit',async()=>{
  for(const count of [20,100]){
    const f=fixture({status:2,count});await f.api.loadPushContract();await f.api.loadPushContract();
    assert.equal(f.reads.length,1);assert.equal(f.metadata.length,0);assert.equal(f.writes.length,0);assert.equal(f.state.pushContract.value.tableSource,'published');
    assert.deepEqual(f.reads[0].params,{datasourceId:'source'});
    const sample=JSON.parse(f.api.pushContractSample(f.state.pushContract.value.tables[0]));assert.equal(sample.deliveryType,'FTP');assert.ok(!('schemaVersion' in sample));assert.ok(!('occurredAt' in sample));
    await f.api.issuePushCredential('source');assert.equal(f.writes.length,1);
  }
});

test('expanded table contract renders its JSON sample outside inert HTML template elements',async()=>{
  const f=fixture({status:2,count:1});await f.api.loadPushContract();
  const begin=source.indexOf('<el-collapse v-else v-model="pushContractOpenTables"');
  const end=source.indexOf('</el-collapse>',begin)+'</el-collapse>'.length;
  const template=source.slice(begin,end).replace('v-else ','');
  const app=Vue.createSSRApp({
    data:()=>({pushContractOpenTables:['t0'],pushVisibleTables:f.state.pushContract.value.tables,
      pushContractIsPreview:false,pushContractSample:f.api.pushContractSample,
      pushContractFieldsLoading:()=>false,copyPushSample:()=>{},pushAttachmentSize:()=>''}),
    render:Vue.compile(template),
  });
  const passthrough={setup:(_, {slots})=>()=>Vue.h('div',slots.default?.())};
  for(const name of ['ElCollapse','ElCollapseItem','ElButton','DataTable'])app.component(name,passthrough);
  app.component('ElTableColumn',{render:()=>null});
  app.component('LoadingState',{render:()=>null});
  app.component('ElEmpty',{render:()=>null});
  const html=await renderToString(app);
  const visibleHtml=html.replace(/<template(?:\s[^>]*)?>[\s\S]*?<\/template>/g,'');
  assert.match(visibleHtml,/<pre>/);
  assert.match(visibleHtml,/records/);
  assert.match(visibleHtml,/deliveryType/);
  assert.match(visibleHtml,/table_0/);
});
test('permission, network and mismatched datasource failures stay visible inline without masquerading as a preview',async()=>{
  for(const remote of [()=>{throw new Error('无权访问')},()=>{throw new Error('网络连接失败')},()=>({datasourceId:'foreign',tables:[]})]){
    const f=fixture({status:2,remote});await f.api.loadPushContract();assert.equal(f.state.pushContract.value,null);assert.ok(f.state.pushContractError.value);assert.equal(f.metadata.length,0);assert.equal(f.writes.length,0);
  }
});
test('a source still loading uses preview and transitions to formal contract only after the authoritative registration state is ready',async()=>{
  const f=fixture({status:2,loading:true});await f.api.loadPushContract();assert.equal(f.reads.length,0);
  f.state.sourceDetailLoading.value=false;await f.api.loadPushContract(true);assert.equal(f.reads.length,1);assert.equal(f.state.pushContract.value.tableSource,'published');
});
test('late formal responses cannot replace a different datasource and duplicate opens share one request',async()=>{
  let resolve;const promise=new Promise(r=>resolve=r);const f=fixture({status:2,remote:()=>promise});
  const pending=f.api.loadPushContract();await f.api.loadPushContract();await new Promise(resolve=>setImmediate(resolve));assert.equal(f.reads.length,1);f.state.sourceTid.value='other';resolve({datasourceId:'source',tables:[]});await pending;assert.equal(f.state.pushContract.value,null);
});
test('20/100 file definitions default to two single endpoints and switch to bounded resumable workflow without reads',async()=>{
 for(const count of [20,100]) {
  const f=fixture({count,status:2});await f.api.loadPushContract();
  assert.equal(f.api.pushInterfaceType.value,'dataPush');
  assert.deepEqual(f.api.pushContractEndpointItems.value.map(e=>e.path),['/api/v1/data-push']);
  f.state.source.value.pushDataType='UNSTRUCTURED';
  assert.equal(f.api.pushInterfaceType.value,'fileSingle');assert.equal(f.api.pushRequestContentType.value,'multipart/form-data');
  let stages=Object.fromEntries(f.api.pushFileWorkflow.value.map(s=>[s.key,s]));
  assert.deepEqual(Object.keys(stages),['fileSingle','fileStatus']);
  assert.deepEqual(stages.fileSingle.parameters.map(p=>p.name),['datasourceId','fileCode','businessKey','file','metadata']);
  assert.ok(stages.fileSingle.parameters.every(p=>p.location==='请求体'));
  assert.match(stages.fileSingle.sample,/^PUT https:\/\/gaw.example.test\/api\/v1\/data-push\r?\n/);
  const multipart = stages.fileSingle.sample;
  const form = await new Request('https://validation.invalid/',{method:'PUT',headers:{'Content-Type':multipart.match(/^Content-Type: ([^\r\n]+)/m)[1]},body:multipart.slice(multipart.indexOf('\r\n\r\n')+4)}).formData();
  assert.equal(form.get('datasourceId'),'source');assert.equal(form.get('fileCode'),'<已登记的文件英文名>');
  assert.equal(form.get('file').name,'sample.pdf');assert.ok(form.get('file').size>0);assert.deepEqual(JSON.parse(form.get('metadata')),{});
  assert.ok(!stages.fileSingle.sample.includes('file_collection_0'));
  f.api.pushFileMode.value='chunked';stages=Object.fromEntries(f.api.pushFileWorkflow.value.map(s=>[s.key,s]));
  assert.deepEqual(Object.keys(stages),['fileInit','filePart','fileComplete','fileStatus','fileParts']);
  assert.deepEqual(stages.fileInit.parameters.map(p=>p.name),['datasourceId','fileCode','fileName','size','sha256']);
  assert.equal(stages.fileInit.parameters.find(p=>p.name==='sha256').required,false);
  assert.ok(Object.values(stages).every(s=>s.parameters.every(p=>p.location!=='请求头')));
  assert.match(stages.filePart.sample,/X-Part-SHA256:/);assert.match(stages.filePart.sample,/Content-Length:/);
  assert.match(stages.fileParts.sample,/after=0&limit=100/);assert.equal(stages.fileParts.optional,true);
  assert.deepEqual(stages.fileComplete.parameters.map(p=>p.name),['id','businessKey','metadata']);
  assert.match(stages.fileComplete.sample,/businessKey/);assert.match(stages.fileComplete.sample,/Idempotency-Key:/);
  assert.match(stages.fileInit.sample,/"fileCode":/);assert.ok(!stages.fileInit.sample.includes('collectionId'));
  await f.api.copyPushFileStageSample(stages.filePart);assert.deepEqual(f.copied,[stages.filePart.sample]);
  assert.equal(f.reads.length,1);assert.equal(f.nodeReads.length,1);assert.equal(f.metadata.length,0);assert.equal(f.writes.length,0);
 }
});
test('unstructured registration preview needs no field associations or per-table metadata reads',async()=>{
 for(const count of [20,100]) {
  const f=fixture({count,contentType:'UNSTRUCTURED',remote:p=>({datasourceId:p.datasourceId,tables:[]})});await f.api.loadPushContract();await f.flush();
  assert.equal(f.api.pushInterfaceType.value,'fileSingle');assert.equal(f.metadata.length,0);assert.equal(f.reads.length,0);assert.equal(f.nodeReads.length,1);assert.equal(f.writes.length,0);
  assert.match(f.api.pushFileWorkflow.value[0].parameters.find(p=>p.name==='fileCode').description,/授权当前业务密钥/);
 }
});
test('step five uses the matching network default and never falls back to another network',async()=>{
 const f=fixture({contentType:'UNSTRUCTURED'});await f.api.loadPushContract();
 assert.equal(f.api.pushProtocolBaseUrl.value,'https://gaw.example.test');
 f.state.source.value.storageDomain='HLW';assert.equal(f.api.pushProtocolBaseUrl.value,'https://internet.example.test');
 f.state.source.value.storageDomain='SPZW';assert.equal(f.api.pushProtocolBaseUrl.value,'');assert.match(f.api.pushProtocolUrl('/api/v1/file-uploads'),/^<当前网络/);
 assert.equal(f.nodeReads.length,1);
});
test('file workflow distinguishes acceptance, file readiness and confirmed delivery',()=>{
 const f=fixture({contentType:'UNSTRUCTURED'});f.api.pushFileMode.value='chunked';
 const stages=Object.fromEntries(f.api.pushFileWorkflow.value.map(s=>[s.key,s]));
 assert.match(stages.fileComplete.description,/202.*WAITING_FILE/);assert.match(stages.fileComplete.description,/另调submit/);
 assert.match(stages.fileStatus.description,/deliveryStatus=SUCCEEDED/);assert.match(stages.fileStatus.description,/READY 只表示文件可用/);
 assert.ok(!source.includes('/api/v1/file-uploads/single'));assert.ok(!source.includes('尚未登记关联附件字段'));
 assert.match(source,/class="push-key-button" type="primary"/);
 assert.match(source,/<section v-if="!pushIsAttachmentMode" class="push-contract-section">/);
});

test('first fifth-step entry prepares one credential regardless of 20/100 table count and never rotates implicitly',async()=>{
 for(const count of [20,100]) {
  const f=fixture({count,status:2});
  f.api.ensurePushCredential();f.api.ensurePushCredential();
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(f.writes.length,1);assert.equal(f.writes[0].url,'/dws/push/credential/issue');
  f.api.ensurePushCredential();await new Promise(resolve=>setImmediate(resolve));assert.equal(f.writes.length,1);
 }
 const loading=fixture({loading:true});loading.api.ensurePushCredential();assert.equal(loading.writes.length,0);
 const other=fixture();other.state.isDataPushSource.value=false;other.api.ensurePushCredential();assert.equal(other.writes.length,0);
});
test('the concrete server msg survives the generic Magic wrapper',()=>{
 const f=fixture();const actual='推送登记同步服务尚未配置，请检查平台 data-element.push-registry 的 relay-base-url 和 standalone-sync-key';
 assert.equal(f.api.pushErrorMessage({message:'接口处理失败，请稍后重试',detail:JSON.stringify({msg:actual,error:{message:'接口处理失败'}})}),actual);
 assert.equal(f.api.pushErrorMessage({response:{data:{msg:actual}}}),actual);
});
test('a pending synchronization keeps its revealed key through retry and shows the precise reason',async()=>{
 const f=fixture();let count=0;
 f.state.$common.postSilently=async()=>({datasourceId:'source',clientId:'push-source',ready:++count>1,apiKey:count===1?'validation-only-secret':undefined,revealed:count===1,syncError:count===1?'登记同步未配置':''});
 await f.api.issuePushCredential('source');assert.equal(f.state.pushCredentialError.value,'登记同步未配置');
 assert.equal(f.state.pushCredential.value.apiKey,'validation-only-secret');assert.equal(f.state.pushCredential.value.ready,false);
 await f.api.issuePushCredential('source',false,true);assert.equal(f.state.pushCredential.value.apiKey,'validation-only-secret');
 assert.equal(f.state.pushCredential.value.ready,true);assert.equal(f.state.pushCredentialError.value,'');
});
