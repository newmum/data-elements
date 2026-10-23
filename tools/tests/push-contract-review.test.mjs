import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
import test from 'node:test';
const root=fileURLToPath(new URL('../../',import.meta.url));
const require=createRequire(path.join(root,'data-elements-chengtian/package.json'));
const {ref,computed}=require('vue');
const file='lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/04.登记内容审查(register-confirm).vue';
const source=fs.readFileSync(path.join(root,file),'utf8').replace(/\r\n?/g,'\n');
const start=source.indexOf('// Push contract display is read-only');
const blockEnd=source.indexOf('function openReportWorkspace()',start);
assert.ok(start>=0 && blockEnd>start,'push contract source boundaries must exist');
const block=source.slice(start,blockEnd);
const queueStart=source.indexOf('function drainFieldLoadQueue()');
const queueEnd=source.indexOf('watch(\n  () => [sourceTid.value',queueStart);
assert.ok(queueStart>=0 && queueEnd>queueStart,'field batch queue source boundaries must exist');
const queue=source.slice(queueStart,queueEnd);
function fixture({status=0,count=20,remote,loading=false}={}) {
  const reads=[],writes=[],metadata=[],copied=[];
  const tables=ref(Array.from({length:count},(_,i)=>({tid:'t'+i,tableName:'table_'+i,fields:[]})));
  const sourceRef=ref({tid:'source',dbName:'测试推送源',assetStatus:status});
  const state={source:sourceRef,sourceTid:ref('source'),sourceDetailLoading:ref(loading),tables,connectionInfoExpanded:ref(true),
    pushContract:ref(null),pushContractLoading:ref(false),pushContractError:ref(''),pushContractLoadedDatasourceId:ref(''),pushContractOpenTables:ref([]),
    pushCredential:ref(null),pushCredentialError:ref(''),pushCredentialLoading:ref(false),pushCredentialDatasourceId:ref(''),isDataPushSource:ref(true),
    computed,ref,governanceConfig:table=>table?.fieldGovernanceConfig||null,catalogFor:()=>null,fieldsFor:table=>table.fields||[],tableFieldKey:table=>table.tableName,fieldLoadingFor:()=>false,
    $message:{success(){},warning(){}},ElMessageBox:{confirm:async()=>{}},navigator:{clipboard:{writeText:async(text)=>{copied.push(text)}}},
    $common:{getSilently:async(url,params)=>{reads.push({url,params});return remote?remote(params):{datasourceId:params.datasourceId,tables:tables.value.map(t=>({tableId:t.tid,tableName:t.tableName,schemaVersion:3,fields:[{fieldName:'id',dataType:'bigint',required:true}],fieldCount:1}))}},postSilently:async(url,body)=>{writes.push({url,body});return {datasourceId:body.datasourceId,clientId:'push-source',revealed:false}}},
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
    return {loadPushContract,refreshRegistrationPushContract,issuePushCredential,pushContractIsPreview,pushContractSample,pushContractEndpointItems,pushAttachmentDescription,pushAttachmentSample,copyPushAttachmentSample,pushInterfaceType,selectPushInterface,pushIsAttachmentMode,pushVisibleTables,pushSelectedParameters,pushRequestContentType,pushContractHeaders,pushAttachmentFieldCount,pushAttachmentFieldFor,selectPushAttachmentField,queueIdle:()=>!fieldLoadsInFlight&&!fieldBatchScheduled&&!fieldLoadQueue.length};`);
  // The real bounded queue uses the ordinary metadata GET; formal contracts use silent GET.
  state.$common.get=async(url,params)=>{const ids=params.tid.split(',');metadata.push(ids);return Object.fromEntries(ids.map(id=>[id,[{columnName:'id',columnType:'bigint',nullable:0,ordinalPosition:1}]]));};
  const api=create(...Object.values(state));
  return {state,api,reads,writes,metadata,copied,async flush(){for(let i=0;i<20&&!api.queueIdle();i++)await new Promise(resolve=>setImmediate(resolve));assert.ok(api.queueIdle());}};
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
  const pending=f.api.loadPushContract();await f.api.loadPushContract();assert.equal(f.reads.length,1);f.state.sourceTid.value='other';resolve({datasourceId:'source',tables:[]});await pending;assert.equal(f.state.pushContract.value,null);
});
test('unified contract exposes adjacent table and unstructured endpoints with field attachment constraints',()=>{
  const f=fixture();assert.deepEqual(f.api.pushContractEndpointItems.value.map(e=>e.path),['/api/v1/data-push','/api/v1/file-uploads/single']);
  f.state.pushContract.value={tables:[{tableName:'persons',fields:[{fieldName:'photo_id',attachment:{allowedExtensions:['png']}}]}]};
  const attachmentSample=f.api.pushAttachmentSample(f.state.pushContract.value.tables[0],f.state.pushContract.value.tables[0].fields[0]);
  assert.match(attachmentSample,/tableName=persons&fieldName=photo_id&fileName=attachment.png/);
  assert.ok(!/[?&](collectionId|size|sha256)=/.test(attachmentSample));assert.match(attachmentSample,/source/);assert.ok(!attachmentSample.includes('tenantId'));
  assert.equal(f.api.pushAttachmentDescription({maxFileBytes:20971520,allowedExtensions:['jpg','pdf']}),'单个≤20 MB · jpg、pdf');
  const sample=JSON.parse(f.api.pushContractSample({tableName:'cases',fields:[{fieldName:'file_id',attachment:{maxFileBytes:20971520}}]}));assert.equal(sample.records.file_id,undefined);assert.equal(sample.records[0].file_id,'<已上传完成的fileId>');
  assert.ok(!source.includes('<el-tab-pane label="照片与文档"'));assert.ok(!source.includes('pushFileExampleKind'));assert.ok(!source.includes('pushContractTab'));assert.ok(!source.includes('pushDeliveryType'));assert.match(source,/默认整文件推送/);assert.match(source,/application\/octet-stream/);assert.ok(!source.includes('<details v-if="item.key'));
});

test('specification wording, restored Content-Type card and public batch fields stay consistent',()=>{
  assert.ok(!source.includes('契约'));
  assert.match(source,/<strong>数据推送接口规范<\/strong>/);
  assert.ok(!source.includes('对外数据推送'));assert.ok(!source.includes('表数据按登记字段推送；关联附件单独上传'));
  assert.ok(source.indexOf('push-content-type-card')<source.indexOf('<div class="push-auth-header push-credential-card"'));
  assert.match(source,/grid-template-columns: repeat\(3, minmax\(0, 1fr\)\)/);
  assert.match(source,/push-content-type-card"><code>Content-Type<\/code><strong>{{ pushRequestContentType }}/);
  const f=fixture();const sample=JSON.parse(f.api.pushContractSample({tableName:'cases',schemaVersion:3,fields:[]}));
  assert.ok(!('schemaVersion' in sample));assert.ok(!('occurredAt' in sample));
});

test('interface selection changes headers, parameters and attachment tables without new requests for 20/100 tables',async()=>{
  for(const count of [20,100]){
    const f=fixture({count,status:2});await f.api.loadPushContract();
    const contract=f.state.pushContract.value;
    contract.requiredParameters=['deliveryType','datasourceId','tableName','batchId','records'].map(name=>({name,required:name!=='batchId'}));
    contract.tables.forEach((table,index)=>{
      table.fields=[{fieldName:'id',dataType:'bigint'}];
      if(index%2===0)table.fields.push({fieldName:'photo_id',attachment:{maxFileBytes:10485760,allowedExtensions:['png']}},{fieldName:'document_id',attachment:{maxFileBytes:20971520,allowedExtensions:['pdf','docx']}});
      table.fieldCount=table.fields.length;
    });
    assert.equal(f.api.pushInterfaceType.value,'dataPush');assert.equal(f.api.pushRequestContentType.value,'application/json');assert.equal(f.api.pushVisibleTables.value.length,count);
    assert.deepEqual(f.api.pushSelectedParameters.value.map(p=>p.name),['deliveryType','datasourceId','tableName','batchId','records']);
    for(let turn=0;turn<5;turn++){
      f.api.selectPushInterface('fileUpload');
      assert.equal(f.api.pushIsAttachmentMode.value,true);assert.equal(f.api.pushRequestContentType.value,'application/octet-stream');
      assert.deepEqual(f.api.pushSelectedParameters.value.map(p=>p.name),['datasourceId','tableName','fieldName','fileName','文件正文']);
      assert.deepEqual(f.api.pushSelectedParameters.value.map(p=>p.location),['查询参数','查询参数','查询参数','查询参数','请求体']);
      assert.match(f.api.pushContractHeaders.value[0].description,/文件/);assert.equal(f.api.pushVisibleTables.value.length,count/2);assert.equal(f.api.pushAttachmentFieldCount.value,count);
      assert.ok(f.api.pushVisibleTables.value.every(table=>table.fields.length===2 && table.fields.every(field=>field.attachment)));
      assert.deepEqual(f.state.pushContractOpenTables.value,[contract.tables[0].tableId]);
      const table=f.api.pushVisibleTables.value[0];assert.equal(f.api.pushAttachmentFieldFor(table).fieldName,'photo_id');
      f.api.selectPushAttachmentField(table,table.fields[1]);assert.equal(f.api.pushAttachmentFieldFor(table).fieldName,'document_id');
      await f.api.copyPushAttachmentSample(table);assert.match(f.copied.at(-1),/tableName=table_0&fieldName=document_id&fileName=attachment.pdf/);
      f.api.selectPushAttachmentField(table,{fieldName:'outside',attachment:{maxFileBytes:1}});assert.equal(f.api.pushAttachmentFieldFor(table).fieldName,'document_id');
      f.api.selectPushInterface('invalid');assert.equal(f.api.pushInterfaceType.value,'fileUpload');
      f.api.selectPushInterface('dataPush');assert.equal(f.api.pushVisibleTables.value.length,count);assert.equal(contract.tables[0].fields.length,3);
      assert.equal(f.reads.length,1);assert.equal(f.metadata.length,0);assert.equal(f.writes.length,0);
      // Reset the local field choice to verify the same first-field behavior next iteration.
      f.api.selectPushAttachmentField(table,table.fields[0]);
    }
  }
});
test('attachment mode shows an empty list without inventing fields or requesting credentials',async()=>{
  const f=fixture({status:2});await f.api.loadPushContract();f.api.selectPushInterface('fileUpload');
  assert.equal(f.api.pushVisibleTables.value.length,0);assert.equal(f.api.pushAttachmentFieldCount.value,0);assert.deepEqual(f.state.pushContractOpenTables.value,[]);assert.equal(f.reads.length,1);assert.equal(f.writes.length,0);
});
test('registration preview retains only explicitly declared attachment fields after asynchronous metadata arrives',async()=>{
  const f=fixture({count:20,remote:params=>({datasourceId:params.datasourceId,tables:[]})});await f.api.loadPushContract();await f.flush();
  f.api.selectPushInterface('fileUpload');assert.equal(f.api.pushVisibleTables.value.length,0);
  f.state.tables.value[3].fieldGovernanceConfig={attachmentFields:[{columnName:'photo_id',maxFileBytes:10485760,allowedExtensions:['png']}]};
  f.state.tables.value[3].fields.push({columnName:'photo_id',columnType:'varchar(64)',nullable:1});f.api.refreshRegistrationPushContract();
  assert.equal(f.api.pushVisibleTables.value.length,1);assert.equal(f.api.pushVisibleTables.value[0].tableName,'table_3');assert.equal(f.api.pushVisibleTables.value[0].fields[0].fieldName,'photo_id');assert.deepEqual(f.state.pushContractOpenTables.value,['t3']);
  assert.equal(f.reads.length,1);assert.equal(f.metadata.length,1);assert.equal(f.writes.length,0);
});

test('purpose descriptions, prominent key action and visible attachment policy stay in the specification',()=>{
 const f=fixture();f.state.pushContract.value={endpoints:{dataPush:{description:'deliveryType=FTP'}}};
 for(const item of f.api.pushContractEndpointItems.value)assert.ok(!/deliveryType|参数|选中|FTP|KAFKA/.test(item.description));
 assert.match(source,/class="push-key-button" type="primary"/);assert.match(source,/单个附件 ≤/);assert.match(source,/格式：/);
});
