/** Development-only real editor harness. No saved credential, API stub, or external sink. */
import React, {useState} from 'react';
import {createRoot} from 'react-dom/client';
import {App,Button,Checkbox,ConfigProvider,Input,Space,Typography} from 'antd';
import FieldMappingEditor from '../src/nifi/panels/FieldMappingEditor';
import {setBridgeToken} from '../src/nifi/api/bridgeSession';
import type {FieldMeta} from '../src/nifi/api/fieldMapping';
import '../src/nifi/styles/globals.css';
import '../src/nifi/styles/haitong-integration.css';

const fields:FieldMeta[]=[{name:'x',type:'VARCHAR',nullable:true},{name:'amount',type:'DECIMAL',nullable:true},{name:'occurred_at',type:'DATETIME',nullable:true},{name:'code',type:'VARCHAR',nullable:true}];
function Harness(){
 const [session,setSession]=useState(''),[ready,setReady]=useState(false),[value,setValue]=useState(JSON.stringify({schemaVersion:'2.0',executionEngine:'JAVA_RECORD',policies:{zoneId:'Asia/Shanghai',recordErrorMode:'ROUTE_INVALID'},rules:[]}));
 const [saved,setSaved]=useState(value),[mounted,setMounted]=useState(true),[boundTarget,setBoundTarget]=useState(false);
 return <main className="nifi-workspace" style={{height:'auto',maxWidth:1180,margin:'24px auto',padding:24,background:'#fff'}}><Typography.Title level={4}>映射规则开发验收</Typography.Title><Typography.Paragraph>使用正式编辑器和真实开发接口。仅合成数据预览，无数据库写入。会话仅保存在内存，关闭页面即清除。</Typography.Paragraph>
 {!ready?<Space><Input.Password aria-label="开发会话" autoComplete="off" value={session} onChange={event=>setSession(event.target.value)}/><Button onClick={()=>{setBridgeToken(session);setSession('');setReady(true);}}>连接开发后端</Button></Space>:<>
 <Space style={{marginBottom:20}}><Button onClick={()=>setSaved(value)}>保存测试草稿</Button><Button onClick={()=>{setValue(saved);setMounted(false);setTimeout(()=>setMounted(true),0);}}>重新打开已保存草稿</Button><Checkbox checked={boundTarget} onChange={event=>setBoundTarget(event.target.checked)}>绑定四字段测试目标结构</Checkbox></Space>
 <div style={{height:600}}>{mounted&&<FieldMappingEditor value={value} onChange={setValue} columnMeta={{sourceSchema:fields,targetSchema:boundTarget?fields:undefined,sourceLabels:{x:'测试文本',amount:'测试数值',occurred_at:'测试时间',code:'测试编码'},targetLabels:{}}}/>}</div>
 <details><summary>当前测试草稿</summary><pre aria-label="当前测试草稿">{value}</pre></details></>}
 </main>;
}
createRoot(document.getElementById('root')!).render(<ConfigProvider><App><Harness/></App></ConfigProvider>);
