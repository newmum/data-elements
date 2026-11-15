import {useMemo,useRef,useState} from 'react';
import {Alert,Button,Divider,Input,Select,Space,Tag,Tooltip} from 'antd';
import {nodeLabels,type ModelNode} from './modeling';
import {modelInputCapacity,modelHasOutput} from './modelingInteractions';
import type {TableDef} from './data';
import {advanceMockRun,cancelMockRun,createMockRun,retryMockRun,validateExecution,validateSchedule,type ExecutionSettings,type MockRun} from './modelingExecution';
export type ModelingExecutionPanelProps={nodes:ModelNode[];tables:Record<string,TableDef>};
const labels={queued:'模拟排队',running:'模拟运行中',succeeded:'快照预览完成',failed:'模拟失败',cancelled:'已取消'};
export default function ModelingExecutionPanel({nodes,tables}:ModelingExecutionPanelProps){
 const [settings,setSettings]=useState<ExecutionSettings>({engine:'flink',mode:'batch'}),[run,setRun]=useState<MockRun|null>(null),[error,setError]=useState(''),[checked,setChecked]=useState(false);
 const [interval,setIntervalMinutes]=useState('60'),[start,setStart]=useState(''),[end,setEnd]=useState(''),[schedule,setSchedule]=useState(''),[history,setHistory]=useState<string[]>([]);
 const snapshot=useRef<Record<string,TableDef>>({});const issues=useMemo(()=>validateExecution(nodes,settings),[nodes,settings]);const blocked=issues.some(i=>i.level==='error'),busy=!!run&&['queued','running'].includes(run.state);
 const act=(fn:()=>void)=>{try{setError('');fn()}catch(e){setError(e instanceof Error?e.message:'操作失败')}};
 const begin=(backfill=false)=>act(()=>{setChecked(true);if(backfill){const problem=validateSchedule(interval,start,end);if(problem)throw new Error(problem);if(!start)throw new Error('请填写补跑时间范围')}const next=createMockRun(nodes,settings,backfill?`${start} → ${end}`:undefined);snapshot.current=structuredClone(tables);if(run)setHistory(h=>[`${labels[run.state]} · ${run.settings.engine} · 尝试 ${run.attempt}${run.rows===undefined?'':` · ${run.rows} 行`}`,...h].slice(0,10));setRun(next)});
 return <div style={{maxHeight:'70vh',overflow:'auto',padding:'4px 2px'}}>
 <Tooltip title="状态手动推进；调度仅保存配置。补跑使用当前快照，不代表已读取历史数据。"><Tag>本地模拟 · 不提交引擎</Tag></Tooltip>
 <Space wrap style={{marginTop:16}}><label>计算引擎 <Select aria-label="计算引擎" value={settings.engine} style={{width:120}} options={[{value:'flink',label:'Flink'},{value:'spark',label:'Spark'}]} onChange={engine=>{setSettings(s=>({...s,engine}));setChecked(false)}}/></label><label>运行模式 <Select aria-label="运行模式" value={settings.mode} style={{width:140}} options={[{value:'batch',label:'批处理'},{value:'stream',label:'实时（快照模拟）'}]} onChange={mode=>{setSettings(s=>({...s,mode}));setChecked(false)}}/></label><Button onClick={()=>setChecked(true)}>运行前校验</Button><Button type="primary" disabled={busy||blocked} onClick={()=>begin()}>创建模拟任务</Button></Space>
 <div style={{marginTop:12,fontSize:12}}><Tooltip title="兼容性仅为本原型规则，非真实引擎支持矩阵；数据内容在预览时检查。"><span>{nodes.length} 个节点 · {blocked?'存在阻断项':'结构校验通过'}</span></Tooltip></div>
 <div style={{marginTop:8,fontSize:12}}>输入：{nodes.filter(n=>modelInputCapacity(n.kind)===0).map(n=>`${n.name}（${nodeLabels[n.kind]}${n.config.table?` · 表 ${n.config.table}`:''}）`).join('、')||'未配置'}</div>
 <div style={{marginTop:4,fontSize:12}}>输出：{nodes.filter(n=>!modelHasOutput(n.kind)).map(n=>`${n.name}（${nodeLabels[n.kind]}）`).join('、')||'未配置'}</div>
 {(checked||blocked)&&<ul style={{paddingLeft:20,maxHeight:160,overflow:'auto'}}>{issues.filter(i=>i.level==='error').map((i,index)=><li key={index} style={{color:i.level==='error'?'#cf5555':undefined}}>{i.level==='error'?'阻断':'提示'}：{i.message}</li>)}</ul>}
 <Divider style={{margin:'16px 0'}}><Tooltip title="时间须含时区，如 +08:00 或 Z；调度仅保存配置，补跑不读取历史数据。">调度与补跑配置</Tooltip></Divider>
 <Space wrap><label>间隔（分钟） <Input aria-label="调度间隔分钟" value={interval} onChange={e=>setIntervalMinutes(e.target.value)} style={{width:90}} maxLength={5}/></label><Button onClick={()=>act(()=>{const problem=validateSchedule(interval,start,end);if(problem)throw new Error(problem);setSchedule(`每 ${interval} 分钟 · ${settings.engine} / ${settings.mode}（仅当前面板保存，未启用真实调度）`)})}>保存调度配置</Button></Space>
 <div style={{marginTop:10}}><Space wrap><label>补跑开始 <Input type="text" placeholder="2026-11-14T00:00+08:00" maxLength={25} style={{width:250}} aria-label="补跑开始" value={start} onChange={e=>setStart(e.target.value)}/></label><label>补跑结束 <Input type="text" placeholder="2026-11-15T00:00+08:00" maxLength={25} style={{width:250}} aria-label="补跑结束" value={end} onChange={e=>setEnd(e.target.value)}/></label><Button disabled={busy||blocked} onClick={()=>begin(true)}>模拟补跑</Button></Space></div>
 {schedule&&<p role="status">{schedule}</p>}
 <Divider style={{margin:'16px 0'}}>模拟监控</Divider>
 {run?<><Space wrap><Tag color={run.state==='failed'?'error':run.state==='succeeded'?'success':'processing'}>{labels[run.state]}</Tag><span>{run.settings.engine} / {run.settings.mode} · 第 {run.attempt}/3 次尝试</span>{run.rows!==undefined&&<span>快照输出合计 {run.rows} 行</span>}</Space>{run.backfill&&<p>补跑配置范围：{run.backfill}（仍使用当前快照）</p>}
 <Space wrap style={{display:'flex',marginTop:12}}><Button disabled={!busy} onClick={()=>act(()=>setRun(advanceMockRun(run,snapshot.current)))}>{run.state==='queued'?'推进至模拟运行':'完成快照计算'}</Button><Button disabled={run.state!=='running'} onClick={()=>setRun(advanceMockRun(run,snapshot.current,true))}>模拟故障</Button><Button disabled={!busy} onClick={()=>setRun(cancelMockRun(run))}>取消</Button><Button disabled={run.state!=='failed'||run.attempt>=3} onClick={()=>act(()=>setRun(retryMockRun(run)))}>重试</Button></Space>
 <ol style={{fontSize:12,paddingLeft:20}}>{run.log.map((line,i)=><li key={i}>{line}</li>)}</ol><Tooltip title="无持续流量、吞吐量或检查点上报；日志为本地模拟事件，修改模型后需新建任务。"><span style={{fontSize:12}}>使用创建时快照</span></Tooltip></>:<p>尚未创建模拟任务。</p>}
 {history.length>0&&<details><summary>最近模拟记录（最多 10 条，刷新即清空）</summary><ul>{history.map((h,i)=><li key={i}>{h}</li>)}</ul></details>}
 {error&&<Alert type="error" showIcon message={error}/>}</div>;
}
