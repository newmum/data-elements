import {useState} from 'react';
import {Alert,Button,Checkbox,Input,InputNumber,Select,Switch,Tag} from 'antd';
import {CheckCircleOutlined,ClockCircleOutlined,PlayCircleOutlined,PoweroffOutlined} from '@ant-design/icons';
import type {ModelNode} from './modeling';
import type {TableDef} from './data';
import {normalizeExecutionSettings,validateExecution,type ExecutionSettings} from './modelingExecution';
import {scheduleConfig,scheduleError,nextScheduleTimes,type ModelSchedule} from './modelSchedule';
export type ModelingExecutionPanelProps={nodes:ModelNode[];tables:Record<string,TableDef>;settings:ExecutionSettings;onSettingsChange:(settings:ExecutionSettings)=>void};
export default function ModelingExecutionPanel({nodes,settings,onSettingsChange}:ModelingExecutionPanelProps){
 const [checked,setChecked]=useState(false),[enabled,setEnabled]=useState(false),[notice,setNotice]=useState('');
 const current=normalizeExecutionSettings(settings),plan=scheduleConfig(current.schedule);
 const planIssue=current.mode==='batch'&&current.scheduleEnabled?scheduleError(plan,current.intervalMinutes):'';
 const next=nextScheduleTimes(plan,current.intervalMinutes);
 const update=(patch:Partial<ExecutionSettings>)=>{onSettingsChange({...current,...patch});setChecked(false);setEnabled(false);setNotice('配置已修改，请重新校验并启动')};
 const updatePlan=(patch:Partial<ModelSchedule>)=>update({schedule:{...plan,...patch}});
 const errors=[...validateExecution(nodes,settings).filter(i=>i.level==='error').map(i=>i.message),...(planIssue?[planIssue]:[])];
 function start(){setChecked(true);if(errors.length){setNotice('校验未通过，请修正配置');return}if(current.mode==='batch'&&!current.scheduleEnabled){setNotice('请开启定时调度，或返回画布点击运行进行单次预览');return}if(current.mode==='batch'&&!next.length){setNotice('有效期内没有未来执行时间，请调整日期');return}setEnabled(true);setNotice('调度配置已启用')}
 const format=(iso:string)=>new Intl.DateTimeFormat('zh-CN',{timeZone:plan.timezone,dateStyle:'medium',timeStyle:'short',hour12:false}).format(new Date(iso));
 return <div className="execution-design">
 <header className="execution-design-header"><div><small>任务调度</small><h3>安排模型的执行节奏</h3><p>设置模型的执行周期与生效范围。</p></div><Tag color={enabled?'green':'blue'}>{enabled?'已启用':'配置草稿'}</Tag></header>

 <div className="execution-design-grid"><div className="execution-fields">
 <h4>01 · 执行环境</h4><div className="execution-field-pair"><label>计算引擎<Select aria-label="计算引擎" value={current.engine} options={[{value:'flink',label:'Flink'},{value:'spark',label:'Spark'}]} onChange={engine=>update({engine})}/></label><label>运行模式<Select aria-label="运行模式" value={current.mode} options={[{value:'batch',label:'批处理'},{value:'stream',label:'持续实时'}]} onChange={mode=>update({mode})}/></label></div>
 <h4>02 · 调度规则</h4>{current.mode==='batch'?<><div className="execution-switch"><strong>定时调度</strong><Switch aria-label="定时调度" checked={current.scheduleEnabled} onChange={scheduleEnabled=>update({scheduleEnabled})}/></div>
 {current.scheduleEnabled?<><div className="execution-field-pair"><label>执行频率<Select aria-label="执行频率" value={plan.frequency} options={[{value:'interval',label:'固定间隔'},{value:'daily',label:'每天'},{value:'weekly',label:'每周'},{value:'once',label:'指定日期 · 单次'}]} onChange={frequency=>updatePlan({frequency})}/></label><label>时区<Select aria-label="调度时区" value={plan.timezone} options={[{value:'Asia/Shanghai',label:'北京时间 UTC+08:00'},{value:'UTC',label:'协调世界时 UTC'}]} onChange={timezone=>updatePlan({timezone})}/></label></div>
 <div className="execution-field-pair">{plan.frequency==='interval'?<label>调度间隔（分钟）<InputNumber aria-label="调度间隔分钟" min={1} max={10080} precision={0} value={current.intervalMinutes} onChange={v=>update({intervalMinutes:v??0})}/></label>:<label>执行时间<Input type="time" aria-label="执行时间" value={plan.time} onChange={e=>updatePlan({time:e.target.value})}/></label>}{plan.frequency==='weekly'&&<label>执行星期<Select aria-label="执行星期" value={plan.weekday} options={['周日','周一','周二','周三','周四','周五','周六'].map((label,value)=>({label,value}))} onChange={weekday=>updatePlan({weekday})}/></label>}</div>
 <div className="execution-field-pair"><label>{plan.frequency==='once'?'执行日期':'生效日期（可选）'}<Input type="date" aria-label="生效日期" value={plan.startDate} onChange={e=>updatePlan({startDate:e.target.value})}/></label>{plan.frequency!=='once'&&<label>结束日期（可选）<Input type="date" aria-label="结束日期" value={plan.endDate} onChange={e=>updatePlan({endDate:e.target.value})}/></label>}</div>
 <Checkbox checked={current.runImmediately} onChange={e=>update({runImmediately:e.target.checked})}>启用后首次立即执行</Checkbox></>:<p>未开启定时调度；可由顶栏运行一次。</p>}</>:<Alert type="info" message="持续实时任务" description="持续处理输入数据，启用后保持实时运行。"/>}
 </div><aside className="execution-summary"><h4><ClockCircleOutlined/> 执行计划预览</h4><dl><div><dt>目标引擎</dt><dd>{current.engine==='flink'?'Flink':'Spark'}</dd></div><div><dt>模型规模</dt><dd>{nodes.length} 个节点</dd></div><div><dt>运行状态</dt><dd>{enabled?'已启用':'未启用'}</dd></div></dl><h5>接下来执行时间 · 预估</h5>{current.mode==='stream'?<p>持续实时 · 不生成批次时间</p>:!current.scheduleEnabled?<p>开启定时调度后查看计划</p>:planIssue?<p>{planIssue}</p>:<>{current.runImmediately&&<p>首次：确认启用时</p>}<ol>{next.map(t=><li key={t}>{format(t)}</li>)}</ol>{!next.length&&<p>有效期内没有未来执行时间</p>}</>}<small>按所选时区展示，固定间隔时间随启用时刻计算。</small></aside></div>
 {checked&&errors.length>0&&<Alert type="error" message="请修正后重新校验" description={<ul>{errors.map((v,i)=><li key={i}>{v}</li>)}</ul>}/>}
 {notice&&<div role="status" className="execution-status"><CheckCircleOutlined/> {notice}</div>}
 <footer className="execution-actions"><span>配置随模型保存</span><Button icon={<CheckCircleOutlined/>} onClick={()=>{setChecked(true);setNotice(errors.length?'请修正配置问题':'校验通过，可以确认启动')}}>运行前校验</Button>{enabled?<Button icon={<PoweroffOutlined/>} onClick={()=>{setEnabled(false);setNotice('调度配置已停止')}}>停止</Button>:<Button type="primary" icon={<PlayCircleOutlined/>} onClick={start}>确认启动</Button>}</footer>
 </div>
}
