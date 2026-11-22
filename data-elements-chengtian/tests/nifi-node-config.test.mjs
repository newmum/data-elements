import {readFile} from 'node:fs/promises';
import test from 'node:test';
import assert from 'node:assert/strict';

const source=await readFile(new URL('../../lowcode/09.系统管理/02.用户与权限/NiFi 节点配置(nifi-node-config).vue',import.meta.url),'utf8');
const start=source.indexOf('let passwordRequestVersion = 0;');
const code=source.slice(start,source.indexOf('const requestSave = ',start));
function fixture(post) {
  const state={revealingPassword:{value:false},passwordVisible:{value:false},editingId:{value:'node-a'},
    hasStoredAuthPassword:{value:true},dialogVisible:{value:true},form:{authPassword:''},
    text:{passwordRevealFailed:'读取失败'},$common:{post},ElMessage:{error:()=>{}}};
  const toggle=new Function(...Object.keys(state),code+'return toggleAuthPasswordVisibility;')(...Object.values(state));
  return {...state,toggle};
}
test('saved credentials are fetched only on the first explicit reveal and can be hidden again',async()=>{
  const requests=[];
  const f=fixture(async(path,body)=>{requests.push({path,body});return {data:{nodeId:'node-a',authPassword:'validation-secret'}};});
  assert.equal(f.form.authPassword,'');assert.equal(requests.length,0);
  await f.toggle();assert.equal(f.passwordVisible.value,true);
  assert.deepEqual(requests,[{path:'/ods/node/page',body:{action:'password',nodeId:'node-a'}}]);
  await f.toggle();assert.equal(f.passwordVisible.value,false);
  await f.toggle();assert.equal(f.passwordVisible.value,true);assert.equal(requests.length,1);
});
test('a closing or changed editor never receives the previous node credential',async()=>{
  for(const changed of [false,true]) {
    let finish;
    const f=fixture(()=>new Promise(resolve=>{finish=resolve;}));
    const pending=f.toggle();
    if(changed)f.editingId.value='node-b';else f.dialogVisible.value=false;
    finish({data:{nodeId:'node-a',authPassword:'validation-secret'}});await pending;
    assert.equal(f.form.authPassword,'');assert.equal(f.passwordVisible.value,false);
  }
});
test('an unrelated response cannot reveal a different node credential',async()=>{
  const f=fixture(async()=>({data:{nodeId:'node-b',authPassword:'validation-secret'}}));
  await f.toggle();assert.equal(f.form.authPassword,'');assert.equal(f.passwordVisible.value,false);
});
test('editing other fields preserves a saved per-database JDBC mapping',()=>{
  const normalizeStart=source.indexOf('const normalizeDriverDirectory = ');
  const normalize=source.slice(normalizeStart,source.indexOf('const toNode = ',normalizeStart));
  const driverStart=source.indexOf('const driverLocationsForSave = ');
  const save=source.slice(driverStart,source.indexOf('const requestSave = ',driverStart));
  const resolve=new Function(normalize+save+'return driverLocationsForSave;')();
  const stored='DEFAULT=/opt/drivers;KINGBASE=/assets/kingbase8.jar';
  assert.equal(resolve({storedDriverLocations:stored,jdbcDriverDirectory:'/opt/drivers'}),stored);
  assert.equal(resolve({storedDriverLocations:stored,jdbcDriverDirectory:'/new/drivers'}),'/new/drivers');
});
