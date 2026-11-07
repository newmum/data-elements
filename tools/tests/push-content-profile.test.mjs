import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import {createRequire} from 'node:module';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../../',import.meta.url));
const require=createRequire(path.join(root,'data-elements-chengtian/package.json'));
const {ref,computed,reactive}=require('vue');
const file=path.join(root,'lowcode/01.系统门户/01.数据源管理/02.数据源管理列表(department-datasource)/01.登记数据源(register-db).vue');
const source=fs.readFileSync(file,'utf8');
const block=source.slice(source.indexOf('const receiveAccessNode ='),source.indexOf('const accessModeOptions ='));
function fixture(){
 const reads=[];
 const state={ref,computed,reactive,formRef:ref(null),parsePoolCfg:value=>typeof value==='string'?JSON.parse(value):value||{},$common:{post:async(url,body)=>{reads.push({url,body});return {list:[]};}}};
 const api=new Function(...Object.keys(state),block+'\nreturn {receiveDeliveryProfile,syncReceiveDeliveryProfile,buildReceiveDeliveryData,syncReceiveAccessNode,receiveNetworkName,accessNetworkOptions,refreshAccessNetworkAliases,normalizeAccessNodeValue};')(...Object.values(state));
 api.accessNetworkOptions.value=[{value:'GAW',label:'公安网'},{value:'HLW',label:'互联网'},{value:'SPZW',label:'专网'}];
 return {...api,reads};
}

test('content type defaults to structured and follows saved values independently of old frequency',()=>{
 const f=fixture();assert.equal(f.receiveDeliveryProfile.pushDataType,'STRUCTURED');
 f.syncReceiveDeliveryProfile({dataTimeliness:'非实时'});assert.equal(f.receiveDeliveryProfile.pushDataType,'STRUCTURED');
 f.syncReceiveDeliveryProfile({pushDataType:'unstructured'});assert.equal(f.receiveDeliveryProfile.pushDataType,'UNSTRUCTURED');
 f.syncReceiveDeliveryProfile({push_data_type:'非结构化数据'});assert.equal(f.receiveDeliveryProfile.pushDataType,'UNSTRUCTURED');
 f.syncReceiveDeliveryProfile({});assert.equal(f.receiveDeliveryProfile.pushDataType,'STRUCTURED');
});

test('20/100 source addresses keep network labels without node reads or replacing input',()=>{
 for(const count of [20,100]) {
  const f=fixture();
  for(let i=0;i<count;i++) {
   const address=`https://upstream-${i}.example.test/events`;
   f.syncReceiveDeliveryProfile({host:address,pushDataType:i%2?'UNSTRUCTURED':'STRUCTURED'});
   for(const [network,label] of [['GAW','公安网'],['HLW','互联网'],['SPZW','专网']]) {
    f.syncReceiveAccessNode(network);assert.equal(f.receiveNetworkName.value,label);
    assert.equal(f.buildReceiveDeliveryData().host,address);
   }
  }
  f.syncReceiveAccessNode('ZWW');assert.equal(f.receiveNetworkName.value,'专网');
  assert.equal(f.reads.length,0);
 }
});

test('source reloads from extraction pool_cfg.host and accepts IPv4, IPv6 or URL',()=>{
 const f=fixture();
 for(const address of ['192.0.2.8','2001:db8::8','https://source.example.test:8443/program?from=business']) {
  for(const key of ['poolCfg','pool_cfg']) {
   f.syncReceiveDeliveryProfile({[key]:JSON.stringify({host:address})});
   assert.equal(f.receiveDeliveryProfile.sourceAddress,address);
  }
  f.syncReceiveDeliveryProfile({host:`  ${address}  `,poolCfg:{host:'old.example.test'}});
  assert.equal(f.buildReceiveDeliveryData().host,address);
 }
 f.syncReceiveDeliveryProfile({});assert.equal(f.receiveDeliveryProfile.sourceAddress,'');
});

test('both push types require a manual source address instead of an automatically generated receiver URL',()=>{
 const f=fixture();
 for(const type of ['STRUCTURED','UNSTRUCTURED']) {
  f.syncReceiveDeliveryProfile({pushDataType:type});
  for(const empty of ['', '   ',null]) {
   f.receiveDeliveryProfile.sourceAddress=empty;
   assert.throws(()=>f.buildReceiveDeliveryData(),/请填写数据来源地址/);
  }
 }
 assert.match(source,/v-model="receiveDeliveryProfile.sourceAddress"/);
 assert.match(source,/数据来源地址/);
 assert.ok(!source.includes('推送数据地址'));
 assert.ok(!source.includes('loadReceivePushNodes'));
 assert.match(source,/if \(accessMode.value === "receive"\) \{\s+buildReceiveDeliveryData\(\)/);
 assert.ok(!source.includes('当前所属网络：'));
 assert.match(source,/推送数据类型/);assert.ok(!source.includes('推送频率'));
});
