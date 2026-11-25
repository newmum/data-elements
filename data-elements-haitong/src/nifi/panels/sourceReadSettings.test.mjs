import fs from 'node:fs';
import test from 'node:test';
import assert from 'node:assert/strict';
import { sourceReadConfigurationError, sourceReadFieldVisible } from '../utils/sourceReadSettings.ts';
const root = new URL('../../../../data-elements-parent/src/main/resources/manifests/sources/', import.meta.url);
const manifests = fs.readdirSync(root).filter(n => n.endsWith('.json')).map(n => JSON.parse(fs.readFileSync(new URL(n,root),'utf8')));
test('all 33 source manifests expose supported read controls independently of incremental fields',()=>{
  assert.equal(manifests.length,33);
  for(const m of manifests){
    assert.equal(new Set(m.fields.map(f=>f.key)).size,m.fields.length,m.key);
    if(m.compile.processors.some(p=>p.type.endsWith('.ExecuteSQLRecord'))) {
      for(const k of ['batchSize','maxRowsPerRun']) assert.equal(m.fields.find(f=>f.key===k)?.group,'read',m.key);
      assert.match(m.fields.find(f=>f.key==='maxRowsPerRun').help,/不限制/);
    }
  }
  const mongo=manifests.find(m=>m.key==='source.mongodb');
  for(const k of ['batchSize','fetchSize','queryLimit','queryJson','projectionJson','sortJson'])assert.ok(mongo.fields.some(f=>f.key===k));
  assert.equal(mongo.fields.find(f=>f.key==='queryLimit').default,undefined);
  assert.equal(mongo.compile.processors[0].properties['Results Per FlowFile'],'${config.batchSize}');
  assert.equal(mongo.compile.processors[0].properties['Batch Size'],'${config.fetchSize}');
  assert.equal(mongo.compile.processors[0].properties.Limit,'${config.queryLimit}');
  const kafka=manifests.find(m=>m.key==='source.kafka');
  assert.equal(kafka.compile.controllerServices[0].properties['max.poll.records'],'${config.maxPollRecords}');
});
test('20 and 100 source configurations validate locally with no requests and preserve manual values',()=>{
  const mongo=manifests.find(m=>m.key==='source.mongodb');
  for(const count of [20,100])for(let i=0;i<count;i++){
    const config={batchSize:7,fetchSize:13,queryLimit:19};
    assert.equal(sourceReadConfigurationError(mongo.fields,config),undefined);assert.deepEqual(config,{batchSize:7,fetchSize:13,queryLimit:19});
  }
  for(const value of [-1,0,1.5,2147483648,'1;drop'])assert.match(sourceReadConfigurationError(mongo.fields,{batchSize:value}),/单批最大条数/);
  assert.equal(sourceReadConfigurationError(mongo.fields,{queryLimit:null}),undefined);
  const mysql=manifests.find(m=>m.key==='source.mysql');
  assert.equal(sourceReadConfigurationError(mysql.fields,{defaultFetchSize:0,maxRowsPerRun:0}),undefined);
  assert.match(sourceReadConfigurationError(mongo.fields,{fetchSize:0}),/抓取批大小/);
});
test('only protocol-appropriate file controls appear and bounds match the runtime',()=>{
  const ftp=manifests.find(m=>m.key==='source.ftp');
  assert.equal(sourceReadFieldVisible('remotePollBatchSize',{protocol:'ftps'}),false);
  assert.equal(sourceReadFieldVisible('maxFilesPerRun',{protocol:'ftps'}),true);
  assert.equal(sourceReadFieldVisible('maxFilesPerRun',{protocol:'ftp',fileConsumptionPolicy:'DELETE_AFTER_DATABASE_COMMIT'}),false);
  assert.match(sourceReadConfigurationError(ftp.fields,{protocol:'ftps',maxFilesPerRun:101}),/100/);
  assert.equal(sourceReadConfigurationError(ftp.fields,{protocol:'ftp',maxFilesPerRun:101}),undefined);
});
test('MinIO exposes filename scope while retaining the old full object-key default',()=>{
  const minio=manifests.find(m=>m.key==='source.minio');
  const scope=minio.fields.find(f=>f.key==='minioFilePatternScope');
  assert.equal(scope.group,'read');assert.equal(scope.type,'select');
  assert.equal(scope.default,'OBJECT_KEY');
  assert.deepEqual(scope.options.map(o=>o.value),['OBJECT_KEY','FILE_NAME']);
  assert.equal(sourceReadFieldVisible(scope.key,{}),true);
  const config={minioFilePatternScope:'FILE_NAME',minioFilePattern:'(?i)^orders_.*\\.xlsx$'};
  assert.equal(sourceReadConfigurationError(minio.fields,config),undefined);
  assert.equal(config.minioFilePatternScope,'FILE_NAME');
});
