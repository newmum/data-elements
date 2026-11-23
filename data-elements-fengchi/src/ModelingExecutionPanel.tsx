import {useState} from 'react';
import {Alert,Button,Checkbox,Input,InputNumber,Radio,Select,Tag} from 'antd';
import {CheckCircleOutlined,ClockCircleOutlined,SaveOutlined} from '@ant-design/icons';
import type {ModelNode} from './modeling';
import type {TableDef} from './data';
import {normalizeExecutionSettings,validateExecution,type ExecutionSettings} from './modelingExecution';
import {scheduleConfig,scheduleError,nextScheduleTimes,type ModelSchedule} from './modelSchedule';
export type ExecutionTrigger='manual'|'schedule'|'stream';
export function executionTrigger(settings:ExecutionSettings):ExecutionTrigger{return settings.mode==='stream'?'stream':settings.scheduleEnabled?'schedule':'manual'}
export function changeExecutionTrigger(settings:ExecutionSettings,trigger:ExecutionTrigger):ExecutionSettings{return {...normalizeExecutionSettings(settings),mode:trigger==='stream'?'stream':'batch',...(trigger==='stream'?{}:{scheduleEnabled:trigger==='schedule'})}}
export type ModelingExecutionPanelProps={nodes:ModelNode[];tables:Record<string,TableDef>;settings:ExecutionSettings;onSettingsChange:(settings:ExecutionSettings)=>void;onConfirm?:()=>boolean;onClose?:()=>void};
export default function ModelingExecutionPanel({nodes,settings,onSettingsChange,onConfirm,onClose}:ModelingExecutionPanelProps){
 const [checked,setChecked]=useState(false),[confirmed,setConfirmed]=useState(false),[notice,setNotice]=useState('');
 const current=normalizeExecutionSettings(settings),plan=scheduleConfig(current.schedule),trigger=executionTrigger(current);
 const planIssue=trigger==='schedule'?scheduleError(plan,current.intervalMinutes):'';
 const next=trigger==='schedule'?nextScheduleTimes(plan,current.intervalMinutes):[];
 const update=(patch:Partial<ExecutionSettings>)=>{onSettingsChange({...current,...patch});setChecked(false);setConfirmed(false);setNotice('')};
 const updatePlan=(patch:Partial<ModelSchedule>)=>update({schedule:{...plan,...patch}});
 const modelErrors=validateExecution(nodes,settings).filter(i=>i.level==='error').map(i=>i.message);
 const timeZoneLabel=plan.timezone==='UTC'?'UTC':'北京时间';
 const example=plan.frequency==='interval'?`例如：每 ${current.intervalMinutes||60} 分钟一次。实际首批时间以真实调度启用时刻为准。`:plan.frequency==='daily'?`例如：每天 ${plan.time}（${timeZoneLabel}）。`:plan.frequency==='weekly'?`例如：每${['周日','周一','周二','周三','周四','周五','周六'][plan.weekday]} ${plan.time}（${timeZoneLabel}）。`:`选择一个未来日期，在当天 ${plan.time}（${timeZoneLabel}）执行一次。`;
 const format=(iso:string)=>new Intl.DateTimeFormat('zh-CN',{timeZone:plan.timezone,dateStyle:'medium',timeStyle:'short',hour12:false}).format(new Date(iso));
 function confirm(){
  if(planIssue){setNotice(planIssue);return}
  if(trigger==='schedule'&&!next.length){setNotice('有效期内没有未来执行时间，请调整日期');return}
  if(onConfirm&&!onConfirm()){setNotice('配置尚未保存，请检查模型或脚本中的错误');return}
  setConfirmed(true);setNotice(onConfirm?'配置已保存到当前浏览器；未创建任务，也未执行模型。':'已确认本地配置；未创建任务，也未执行模型。');
 }
 return <div className="execution-design execution-start-editor">
  <div className="execution-content">
   <header className="execution-introduction"><div><h3>设置启动方式</h3><p>选择模型如何触发。确认只保存配置，本页不会上线或启动后台任务。</p></div><Tag color={confirmed?'green':'default'}>{confirmed?(onConfirm?'配置已保存':'配置已确认'):'配置草稿'}</Tag></header>
   <section className="execution-section" aria-label="触发方式"><h4>触发方式</h4><Radio.Group aria-label="触发方式" value={trigger} onChange={e=>{onSettingsChange(changeExecutionTrigger(current,e.target.value));setChecked(false);setConfirmed(false);setNotice('')}} className="execution-trigger-options">
    <Radio value="manual"><strong>手动运行</strong><span className="execution-trigger-description">需要时通过播放图标或工具栏运行，预览当前浏览器数据。</span></Radio>
    <Radio value="schedule"><strong>定时调度</strong><span className="execution-trigger-description">设置时间规则并预估计划；调度服务尚未接入。</span></Radio>
    <Radio value="stream"><strong>持续实时</strong><span className="execution-trigger-description">保留实时模式目标配置；尚未连接实时执行服务。</span></Radio>
   </Radio.Group><div className="execution-field-pair execution-engine"><label>目标计算引擎<Select aria-label="计算引擎" value={current.engine} options={[{value:'flink',label:'Flink'},{value:'spark',label:'Spark'}]} onChange={engine=>update({engine})}/></label><div className="execution-field-help">{current.mode==='batch'?'批处理目标':'实时处理目标'} · 本地试跑仍由浏览器计算，不向 {current.engine==='flink'?'Flink':'Spark'} 提交 SQL 或任务。</div></div></section>
   <div className="execution-start-grid"><section className="execution-section execution-fields" aria-label="时间规则"><h4>{trigger==='schedule'?'时间规则':trigger==='manual'?'本地试跑':'实时模式说明'}</h4>
    {trigger==='manual'?<div className="execution-mode-note"><p>使用启动节点左侧的播放图标，或工具栏「运行」，查看当前已加载记录的计算结果。</p><p>例如：打开跨库客户订单示例后试跑，可查看按地区汇总的订单金额。点击节点或确认配置都不会执行模型。</p></div>:trigger==='stream'?<Alert type="info" message="实时执行服务未连接" description="不会监听输入、启动持续任务或创建检查点。当前只保存实时模式和目标引擎；可运行算子仍按有限数据快照本地试跑。"/>:<>
     <div className="execution-field-pair"><label>执行频率<Select aria-label="执行频率" value={plan.frequency} options={[{value:'interval',label:'固定间隔'},{value:'daily',label:'每天'},{value:'weekly',label:'每周'},{value:'once',label:'指定日期 · 单次'}]} onChange={frequency=>updatePlan({frequency})}/></label><label>时区<Select aria-label="调度时区" value={plan.timezone} options={[{value:'Asia/Shanghai',label:'北京时间 · UTC+08:00'},{value:'UTC',label:'UTC · 协调世界时'}]} onChange={timezone=>updatePlan({timezone})}/></label></div>
     <div className="execution-field-pair">{plan.frequency==='interval'?<label>调度间隔（分钟）<InputNumber aria-label="调度间隔分钟" min={1} max={10080} precision={0} value={current.intervalMinutes} onChange={v=>update({intervalMinutes:v??0})}/><small>1–10080 分钟，按固定间隔预估。</small></label>:<label>执行时间<Input type="time" aria-label="执行时间" value={plan.time} onChange={e=>updatePlan({time:e.target.value})}/><small>以所选时区解释，不使用电脑本地时区。</small></label>}{plan.frequency==='weekly'&&<label>执行星期<Select aria-label="执行星期" value={plan.weekday} options={['周日','周一','周二','周三','周四','周五','周六'].map((label,value)=>({label,value}))} onChange={weekday=>updatePlan({weekday})}/></label>}</div>
     <div className="execution-field-pair"><label>{plan.frequency==='once'?'执行日期':'生效日期（可选）'}<Input type="date" aria-label="生效日期" value={plan.startDate} onChange={e=>updatePlan({startDate:e.target.value})}/><small>{plan.frequency==='once'?'单次计划必须选择未来日期。':'留空表示不限制起始日期。'}</small></label>{plan.frequency!=='once'&&<label>结束日期（可选）<Input type="date" aria-label="结束日期" value={plan.endDate} onChange={e=>updatePlan({endDate:e.target.value})}/><small>包含结束日；留空表示无截止日期。</small></label>}</div>
     {plan.frequency!=='once'&&<div className="execution-first-run"><Checkbox checked={current.runImmediately} onChange={e=>update({runImmediately:e.target.checked})}>真实调度启用后首次立即执行</Checkbox><small>只保存此偏好，确认配置时不会试跑或启动调度。</small></div>}
     <p className="execution-example">{example}</p>{planIssue&&<Alert type="error" showIcon message={planIssue}/>}
    </>}
   </section><aside className="execution-section execution-summary" aria-label="计划与状态"><h4><ClockCircleOutlined/> 计划与状态</h4><dl><div><dt>配置状态</dt><dd>{confirmed?(onConfirm?'已保存到浏览器':'本地已确认'):'当前模型草稿'}</dd></div><div><dt>触发方式</dt><dd>{trigger==='manual'?'手动运行':trigger==='schedule'?'定时调度':'持续实时'}</dd></div><div><dt>调度服务</dt><dd>未连接</dd></div><div><dt>目标引擎</dt><dd>{current.engine==='flink'?'Flink':'Spark'}</dd></div></dl>
    <h5>{trigger==='schedule'?'接下来执行时间 · 预估':'执行边界'}</h5>{trigger==='schedule'?<>{planIssue?<p>请先修正时间规则。</p>:<><ol>{next.map(t=><li key={t}>{format(t)}</li>)}</ol>{!next.length&&<p>有效期内没有未来执行时间</p>}</>}<small>按{timeZoneLabel}展示；只是计划预估，不会定时触发。{plan.frequency==='interval'?'实际间隔从真实启用时刻计算。':''}</small></>:<p>{trigger==='manual'?'不会自动触发；只在主动点击播放或运行时计算。':'不会创建后台实时任务；目标模式与引擎只作为配置保存。'}</p>}
   </aside></div>
   {checked&&<Alert type={modelErrors.length?'warning':'success'} showIcon message={modelErrors.length?'模型仍需完善，可继续保存配置':'模型基础检查通过'} description={modelErrors.length?<ul>{modelErrors.map((value,i)=><li key={i}>{value}</li>)}</ul>:'实际试跑仍会检查当前模型与样本，不代表真实引擎已接入。'}/>}
   {notice&&<div role="status" className="execution-status">{notice}</div>}
  </div>
  <footer className="execution-actions"><span>配置保存到当前模型，不创建任务</span>{onClose&&<Button onClick={onClose}>关闭</Button>}<Button icon={<CheckCircleOutlined/>} onClick={()=>setChecked(true)}>检查模型</Button><Button type="primary" icon={<SaveOutlined/>} onClick={confirm}>确认配置</Button></footer>
 </div>;
}
