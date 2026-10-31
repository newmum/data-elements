import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import test from 'node:test';
const root=fileURLToPath(new URL('../../',import.meta.url));
const require=createRequire(path.join(root,'data-elements-chengtian/package.json'));
const {ref,computed,watch}=require('vue');
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
  const state={source:sourceRef,sourceTid:ref('source'),sourceDetailLoading:ref(loading),tables,connectionInfoExpanded:ref(true),
    pushContract:ref(null),pushContractLoading:ref(false),pushContractError:ref(''),pushContractLoadedDatasourceId:ref(''),pushContractOpenTables:ref([]),
    pushCredential:ref(null),pushCredentialError:ref(''),pushCredentialLoading:ref(false),pushCredentialDatasourceId:ref(''),isDataPushSource:ref(true),
    isDataUploadSource:ref(false),fileRegistrationRows:ref(Array.from({length:count},(_,i)=>({tableId:'file'+i,collectionId:'file_collection_'+i,fileName:'登记文件'+i,fileDescription:'说明',fileFormat:'pdf',maxFileSizeMb:20,maxFileBytes:20971520,fieldCount:6}))),pushFileSelection:ref(''),store:{state:{steps:[]}},computed,ref,watch,governanceConfig:table=>table?.fieldGovernanceConfig||null,catalogFor:()=>null,fieldsFor:table=>table.fields||[],tableFieldKey:table=>table.tableName,fieldLoadingFor:()=>false,
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
    return {ensurePushCredential,pushErrorMessage,loadPushContract,refreshRegistrationPushContract,issuePushCredential,pushContractIsPreview,pushContractSample,pushContractEndpointItems,pushAttachmentDescription,pushInterfaceType,selectPushInterface,pushIsAttachmentMode,pushVisibleTables,pushSelectedParameters,pushRequestContentType,pushContractHeaders,pushChunkSample,copyPushChunkSample,pushProtocolUrl,pushProtocolBaseUrl,pushProtocolNodeError,pushPrimaryEndpoints,pushAuxiliaryEndpoints,pushAuxiliaryExpanded,selectedFileRegistration,queueIdle:()=>!fieldLoadsInFlight&&!fieldBatchScheduled&&!fieldLoadQueue.length};`);
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
test('the persisted content type chooses the structured endpoint or a chunked protocol without new reads for 20/100 tables',async()=>{
 for(const count of [20,100]) {
  const f=fixture({count,status:2});await f.api.loadPushContract();
  assert.equal(f.api.pushInterfaceType.value,'dataPush');assert.equal(f.api.pushRequestContentType.value,'application/json');
  assert.deepEqual(f.api.pushContractEndpointItems.value.map(e=>e.path),['/api/v1/data-push']);
  const sample=JSON.parse(f.api.pushContractSample(f.state.pushContract.value.tables[0]));assert.equal(sample.deliveryType,'FTP');assert.ok(Array.isArray(sample.records));
  f.state.source.value.pushDataType='UNSTRUCTURED';assert.equal(f.api.pushInterfaceType.value,'filePart');
  assert.equal(f.api.pushPrimaryEndpoints.value.length,1);assert.equal(f.api.pushAuxiliaryEndpoints.value.length,5);assert.equal(f.api.pushAuxiliaryExpanded.value,false);
  f.api.selectPushInterface('fileInit');
  assert.equal(f.api.pushContractEndpointItems.value.length,6);assert.equal(f.api.pushRequestContentType.value,'application/json');
  assert.deepEqual(f.api.pushSelectedParameters.value.map(p=>p.name),['datasourceId','collectionId','fileName','size','sha256']);
  assert.ok(!f.api.pushSelectedParameters.value.some(p=>['tableName','fieldName'].includes(p.name)));
  assert.match(f.api.pushChunkSample.value,/https:\/\/gaw.example.test\/api\/v1\/file-uploads/);
  assert.match(f.api.pushChunkSample.value,/"datasourceId": "source"/);
  for(let i=0;i<5;i++) {
   f.api.selectPushInterface('filePart');assert.equal(f.api.pushRequestContentType.value,'application/octet-stream');
   assert.deepEqual(f.api.pushContractHeaders.value.map(h=>h.name),['X-Part-SHA256','Content-Length']);
   assert.match(f.api.pushChunkSample.value,/parts\/1/);assert.match(f.api.pushChunkSample.value,/X-Part-SHA256/);
   f.api.selectPushInterface('fileParts');assert.match(f.api.pushChunkSample.value,/after=0&limit=100/);
   f.api.selectPushInterface('fileComplete');assert.equal(f.api.pushContractHeaders.value.length,0);assert.match(f.api.pushChunkSample.value,/\/complete/);
   f.api.selectPushInterface('fileSubmit');assert.match(f.api.pushChunkSample.value,/businessKey/);assert.equal(f.api.pushContractHeaders.value[0].name,'Idempotency-Key');
   f.api.selectPushInterface('fileStatus');assert.match(f.api.pushChunkSample.value,/^GET /);
   f.api.selectPushInterface('invalid');assert.equal(f.api.pushInterfaceType.value,'fileStatus');
  }
  await f.api.copyPushChunkSample();assert.equal(f.copied.length,1);
  assert.equal(f.reads.length,1);assert.equal(f.nodeReads.length,1);assert.equal(f.metadata.length,0);assert.equal(f.writes.length,0);
  f.state.source.value.pushDataType='STRUCTURED';assert.equal(f.api.pushInterfaceType.value,'dataPush');
 }
});
test('unstructured registration preview needs no field associations or per-table metadata reads',async()=>{
 for(const count of [20,100]) {
  const f=fixture({count,contentType:'UNSTRUCTURED',remote:p=>({datasourceId:p.datasourceId,tables:[]})});await f.api.loadPushContract();await f.flush();
  assert.equal(f.api.pushInterfaceType.value,'filePart');assert.equal(f.metadata.length,0);assert.equal(f.reads.length,0);assert.equal(f.nodeReads.length,1);assert.equal(f.writes.length,0);
  f.api.selectPushInterface('fileInit');
  assert.match(f.api.pushSelectedParameters.value.find(p=>p.name==='collectionId').description,/YAML/);
 }
});
test('step five uses the matching network default and never falls back to another network',async()=>{
 const f=fixture({contentType:'UNSTRUCTURED'});await f.api.loadPushContract();
 assert.equal(f.api.pushProtocolBaseUrl.value,'https://gaw.example.test');
 f.state.source.value.storageDomain='HLW';assert.equal(f.api.pushProtocolBaseUrl.value,'https://internet.example.test');
 f.state.source.value.storageDomain='SPZW';assert.equal(f.api.pushProtocolBaseUrl.value,'');assert.match(f.api.pushProtocolUrl('/api/v1/file-uploads'),/^<当前网络/);
 assert.equal(f.nodeReads.length,1);
});
test('the visible guide requires submit after READY and contains no default single-file protocol',()=>{
 assert.match(source,/默认使用分片上传/);assert.match(source,/调用“提交推送”/);assert.match(source,/只有 READY 不代表已写入目标/);
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
