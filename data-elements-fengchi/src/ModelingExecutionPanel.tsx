import {useMemo,useState} from 'react';
import {Button,Checkbox,InputNumber,Select,Space,Switch,Tag,Tooltip} from 'antd';
import type {ModelNode} from './modeling';
import type {TableDef} from './data';
import {normalizeExecutionSettings,validateExecution,type ExecutionSettings} from './modelingExecution';
export type ModelingExecutionPanelProps={nodes:ModelNode[];tables:Record<string,TableDef>;settings:ExecutionSettings;onSettingsChange:(settings:ExecutionSettings)=>void};
/** Start-node configuration only. Running and starting jobs belong to the workspace toolbar. */
export default function ModelingExecutionPanel({nodes,settings,onSettingsChange}:ModelingExecutionPanelProps){
 const [checked,setChecked]=useState(false);const current=normalizeExecutionSettings(settings);
 const issues=useMemo(()=>validateExecution(nodes,settings),[nodes,settings]);
 const update=(patch:Partial<ExecutionSettings>)=>{onSettingsChange({...current,...patch});setChecked(false)};
 return <div style={{padding:'4px 2px'}}>
 <Tag>执行服务未连接</Tag>
 <div style={{display:'grid',gap:18,marginTop:16}}>
 <label>计算引擎 <Select aria-label="计算引擎" value={current.engine} style={{width:150}} options={[{value:'flink',label:'Flink'},{value:'spark',label:'Spark'}]} onChange={engine=>update({engine})}/></label>
 <label>运行模式 <Select aria-label="运行模式" value={current.mode} style={{width:150}} options={[{value:'batch',label:'批处理'},{value:'stream',label:'持续实时'}]} onChange={mode=>update({mode})}/></label>
 {current.mode==='batch'?<><label><Switch aria-label="定时调度" checked={current.scheduleEnabled} onChange={scheduleEnabled=>update({scheduleEnabled})}/> 定时调度</label>
 {current.scheduleEnabled&&<><label>调度间隔（分钟） <InputNumber aria-label="调度间隔分钟" min={1} max={10080} precision={0} value={current.intervalMinutes} onChange={value=>{if(value!==null&&Number.isSafeInteger(value)&&value>=1&&value<=10080)update({intervalMinutes:value})}}/></label>
 <Checkbox checked={current.runImmediately} onChange={e=>update({runImmediately:e.target.checked})}>启用后首次立即执行</Checkbox></>}
 <span style={{fontSize:12}}>{current.scheduleEnabled?'保存仅记录调度配置；启用需连接调度服务。':'未开启定时调度；可由顶栏运行一次。'}</span></>:<span style={{fontSize:12}}>持续实时需连接执行服务；当前不会启动后台任务。</span>}
 <Space><Button onClick={()=>setChecked(true)}>运行前校验</Button><Tooltip title="仅校验当前模型结构与已覆盖规则，不代表真实引擎已就绪。"><span style={{fontSize:12}}>{nodes.length} 个节点</span></Tooltip></Space>
 </div>
 {checked&&<div role="status" style={{marginTop:12}}>{issues.some(i=>i.level==='error')?<ul style={{paddingLeft:20,maxHeight:180,overflow:'auto'}}>{issues.filter(i=>i.level==='error').map((i,index)=><li key={index}>{i.message}</li>)}</ul>:<span>结构校验通过；执行服务仍未连接。</span>}</div>}
 </div>;
}
