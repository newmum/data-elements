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
function fixture(rows){
 const reads=[];
 const state={ref,computed,reactive,formRef:ref(null),$common:{post:async(url,body)=>{reads.push({url,body});return {list:rows};}}};
 const api=new Function(...Object.keys(state),block+'\nreturn {receiveDeliveryProfile,syncReceiveDeliveryProfile,syncReceiveAccessNode,loadReceivePushNodes,receivePushAddress,receiveNetworkName,accessNetworkOptions,refreshAccessNetworkAliases,normalizeAccessNodeValue};')(...Object.values(state));
 api.accessNetworkOptions.value=[{value:'GAW',label:'公安网'},{value:'HLW',label:'互联网'},{value:'SPZW',label:'专网'}];
 return {...api,reads};
}
test('content type defaults to structured and follows normalized saved values independently of old frequency',()=>{
 const f=fixture([]);assert.equal(f.receiveDeliveryProfile.pushDataType,'STRUCTURED');
 f.syncReceiveDeliveryProfile({dataTimeliness:'非实时'});assert.equal(f.receiveDeliveryProfile.pushDataType,'STRUCTURED');
 f.syncReceiveDeliveryProfile({pushDataType:'unstructured'});assert.equal(f.receiveDeliveryProfile.pushDataType,'UNSTRUCTURED');
 f.syncReceiveDeliveryProfile({push_data_type:'非结构化数据'});assert.equal(f.receiveDeliveryProfile.pushDataType,'UNSTRUCTURED');
 f.syncReceiveDeliveryProfile({});assert.equal(f.receiveDeliveryProfile.pushDataType,'STRUCTURED');
});
test('20/100 node configurations use one read and switches select the matching network default without extra requests',async()=>{
 for(const count of [20,100]) {
  const rows=Array.from({length:count},(_,i)=>({networkCode:'GAW',baseUrl:`https://secondary-${i}.example.test`,enabled:1,isDefault:0}));
  rows.push({networkCode:'GAW',baseUrl:'https://gaw.example.test/',enabled:1,isDefault:1},
   {networkCode:'HLW',baseUrl:'https://internet.example.test',enabled:1,isDefault:1},
   {networkCode:'SPZW',baseUrl:'https://special.example.test',enabled:1,isDefault:1},
   {networkCode:'HLW',baseUrl:'https://disabled.example.test',enabled:0,isDefault:1});
  const f=fixture(rows);await f.loadReceivePushNodes();
  for(const [network,label,address] of [['GAW','公安网','gaw'],['HLW','互联网','internet'],['SPZW','专网','special']]) {
   f.syncReceiveAccessNode(network);assert.equal(f.receiveNetworkName.value,label);
   assert.equal(f.receivePushAddress.value,`https://${address}.example.test/api/v1/data-push`);
   f.syncReceiveDeliveryProfile({pushDataType:'UNSTRUCTURED'});assert.equal(f.receivePushAddress.value,`https://${address}.example.test/api/v1/file-uploads`);
   f.syncReceiveDeliveryProfile({});
  }
  f.syncReceiveAccessNode('ZWW');assert.equal(f.receiveNetworkName.value,'专网');
  assert.equal(f.reads.length,1);assert.deepEqual(f.reads[0].body,{nodeType:'push'});
 }
});
test('no default in a multi-node network and an unconfigured network never reuse the police-network address',async()=>{
 const f=fixture([{networkCode:'GAW',baseUrl:'https://gaw.example.test',enabled:1,isDefault:1},
 {networkCode:'HLW',baseUrl:'https://one.example.test',enabled:1,isDefault:0},
 {networkCode:'HLW',baseUrl:'https://two.example.test',enabled:1,isDefault:0}]);await f.loadReceivePushNodes();
 f.syncReceiveAccessNode('HLW');assert.match(f.receivePushAddress.value,/设置默认节点/);
 f.syncReceiveAccessNode('SPZW');assert.match(f.receivePushAddress.value,/未配置/);
 assert.equal(f.reads.length,1);
 assert.ok(!source.includes('当前所属网络：'));
 assert.match(source,/推送数据类型/);assert.ok(!source.includes('推送频率'));
});
