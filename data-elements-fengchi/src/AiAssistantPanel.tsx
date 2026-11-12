import {useEffect,useRef,useState} from 'react';
import {Alert,Button,Checkbox,Drawer,Input,Select,Space,Typography} from 'antd';
import './aiAssistant.css';

export type AiSchema={name:string;columns:{name:string;type:string}[]};
type Mode='generate'|'explain'|'review';
type Props={open:boolean;onClose:()=>void;schemas:AiSchema[];initialSql:string;dialect:string;contextKey:string;currentTableName?:string};
export default function AiAssistantPanel({open,onClose,schemas,initialSql,dialect,contextKey,currentTableName}:Props){
 const [mode,setMode]=useState<Mode>('generate');
 const [prompt,setPrompt]=useState('');const [sql,setSql]=useState('');
 const [selected,setSelected]=useState<string[]>([]);const [consent,setConsent]=useState(false);
 const [busy,setBusy]=useState(false);const [error,setError]=useState('');const [text,setText]=useState('');
 const [copied,setCopied]=useState(false);
 const controller=useRef<AbortController|null>(null);const sequence=useRef(0);
 function cancel(){sequence.current++;controller.current?.abort();controller.current=null;setBusy(false)}
 const currentSchema=schemas.find(s=>s.name===currentTableName);
 const currentSchemaKey=JSON.stringify(currentSchema||null);
 useEffect(()=>{cancel();setSql(initialSql);setSelected(currentSchema?[currentSchema.name]:[]);setConsent(false);setText('');setError('');setPrompt('')},[open,contextKey,currentTableName,currentSchemaKey]); // Revoke consent and synchronize only the active table when context changes.
 useEffect(()=>()=>{sequence.current++;controller.current?.abort()},[]);
 const chosen=schemas.filter(s=>selected.includes(s.name));
 const schemaError=chosen.length>20?'最多可选择 20 张表，请缩小范围。':chosen.reduce((n,s)=>n+s.columns.length,0)>300?'所选表总字段数超过 300，请减少表数量。':'';
 const inputError=prompt.length>4000||sql.length>12000?'需求限 4000 字，SQL 限 12000 字，请缩短后发送。':'';
 function change(){cancel();setConsent(false);setText('');setError('');setCopied(false)}
 async function request(){
  if(busy||!consent||schemaError||inputError||!(mode==='generate'?prompt.trim()&&chosen.length:sql.trim()))return;
  const current=++sequence.current;const abort=new AbortController();controller.current=abort;setBusy(true);setError('');setText('');setCopied(false);
  try{
   const response=await fetch('/api/ai/sql',{method:'POST',credentials:'same-origin',signal:abort.signal,headers:{'Content-Type':'application/json'},body:JSON.stringify({mode,prompt,sql,schema:chosen.map(s=>({name:s.name,columns:s.columns.map(c=>({name:c.name,type:c.type}))})),dialect})});
   if(!response.ok)throw new Error(response.status===503?'AI 服务尚未配置，请联系管理员完成服务端配置。':response.status===429?'请求过于频繁或额度不足，请稍后重试。':response.status===401||response.status===403?'当前请求未获授权，请检查服务端访问配置。':'请求失败，请检查服务状态后重试。');
   const result:unknown=await response.json();
   if(!result||typeof result!=='object'||!('text' in result)||typeof result.text!=='string')throw new Error('服务返回格式异常，请重试。');
   if(current===sequence.current)setText(result.text);
  }catch(e){if(current===sequence.current&&!abort.signal.aborted)setError(e instanceof Error?e.message:'请求失败，请重试。')}
  finally{if(current===sequence.current){setBusy(false);controller.current=null;setConsent(false)}}
 }
 return <Drawer title="智能问数" closable={false} extra={<Button type="text" aria-label="关闭智能问数" onClick={()=>{cancel();onClose()}}>×</Button>} mask={false} rootStyle={{top:92}} open={open} onClose={()=>{cancel();onClose()}} width="min(560px, 100vw)" className="ai-sql-assistant">
  <Space direction="vertical" size="middle" style={{width:'100%'}}>
   <label htmlFor="ai-sql-mode">功能</label><Select id="ai-sql-mode" value={mode} style={{width:'100%'}} onChange={value=>{change();setMode(value)}} options={[{value:'generate',label:'中文生成 SQL'},{value:'explain',label:'解释 SQL'},{value:'review',label:'检查潜在错误'}]}/>
   <Typography.Text type="secondary">SQL 方言：{dialect} · 仅显示当前已加载的表结构</Typography.Text>
   <Typography.Text type="secondary">{currentSchema?`已自动关联当前表：${currentSchema.name}（${currentSchema.columns.length} 个字段）`:currentTableName?`当前表 ${currentTableName} 的结构尚未加载，请先打开该表。`:"当前页签没有明确的表。请打开数据表自动关联，或自行选择需要的表结构。"}</Typography.Text>
   <label htmlFor="ai-sql-schema">发送的表结构{mode==='generate'?'（必选）':'（可选）'}</label>
   <Select id="ai-sql-schema" mode="multiple" value={selected} placeholder="选择要提供给 AI 的表" style={{width:'100%'}} onChange={value=>{change();setSelected(value)}} options={schemas.map(s=>({value:s.name,label:`${s.name} · ${s.columns.length} 个字段`}))}/>
   {(schemaError||inputError)&&<Alert type="warning" message={schemaError||inputError}/>}
   {!!chosen.length&&<details><summary>查看将发送的字段名称和类型</summary><pre className="ai-sql-preview">{chosen.map(s=>`${s.name}\n${s.columns.map(c=>`  ${c.name} ${c.type}`).join('\n')}`).join('\n\n')}</pre></details>}
   <label htmlFor="ai-sql-prompt">{mode==='generate'?'查询需求':'补充说明（可选）'}</label><Input.TextArea id="ai-sql-prompt" value={prompt} maxLength={4000} showCount autoSize={{minRows:3,maxRows:7}} placeholder="例如：统计上个月每位客户的订单总额，取前十名。请不要输入密码或个人敏感信息。" onChange={e=>{change();setPrompt(e.target.value)}}/>
   <label htmlFor="ai-sql-input">SQL {mode==='generate'?'参考（可选）':'（必填，可粘贴）'}</label><Input.TextArea id="ai-sql-input" value={sql} maxLength={12000} showCount autoSize={{minRows:5,maxRows:12}} onChange={e=>{change();setSql(e.target.value)}} placeholder="粘贴需要解释或检查的 SQL；本面板编辑不会修改原查询。"/>
   <Checkbox checked={consent} onChange={e=>setConsent(e.target.checked)}>我同意将以上需求、SQL（可能含字面值）及选中的表结构发送至 OpenAI，调用会消耗 API 额度。不发送表格行数据，请先移除 SQL 中的敏感信息。</Checkbox>
   <Space><Button type="primary" loading={busy} disabled={!consent||Boolean(schemaError||inputError)||!(mode==='generate'?prompt.trim()&&chosen.length:sql.trim())} onClick={()=>void request()}>发送请求</Button>{busy&&<Button onClick={cancel}>取消</Button>}</Space>
   {error&&<Alert type="error" showIcon message={error}/>}
   {text&&<section aria-label="AI 回复" aria-live="polite"><Typography.Title level={5}>审核结果</Typography.Title><pre className="ai-sql-result">{text}</pre><Button onClick={async()=>{try{await navigator.clipboard.writeText(text);setCopied(true)}catch{setError('无法访问剪贴板，请选中回复文字手动复制。')}}}>{copied?'已复制':'复制回复'}</Button><Typography.Paragraph type="secondary">请自行审核后粘贴到编辑器，不会自动执行。</Typography.Paragraph></section>}
  </Space>
 </Drawer>;
}
