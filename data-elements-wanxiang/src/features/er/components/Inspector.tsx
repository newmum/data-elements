import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Checkbox, Empty, Input, Progress, Select, Table, Tabs, Tag, Tooltip } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { Entity, FieldDef, Relationship } from '../types/domain';
import { useStudio, activeDiagram } from '../store';
import { displayDate, errorText } from '../core/model';
import { multiplicityLabel, relationLabels, relationshipKind } from '../core/relations';
import { canConfirmRelationship } from '../core/reviewSelection';
import { Icon } from './Icon';
import { SourceIcon } from '../../../design/Visuals';
import { ResizeHandle } from './ResizeHandle';
import { useRelationActions } from './RelationActions';
import { type Row } from '../../../services/api';
import { metadataWritable as canWrite, previewMetadataRecords, previewPhysicalForeignKey, createPhysicalForeignKey } from '../../../services/metadata';
import { canonicalId } from '../../../services/metadataSnapshot';
import { workspaceService } from '../services/workspaceService';

function Status({relation}:{relation:Relationship}){
  const kind=relationshipKind(relation);
  return <span className={`relationship-status status-${kind}`}><span/>{relation.reviewStatus==='rejected'?'已忽略':relation.reviewStatus==='archived'?'已归档':relationLabels[kind]}</span>;
}
function EntitySummary({entity}:{entity?:Entity}){
  return <div className="relation-entity-summary"><span className="mini-table"><Icon name={entity?.kind==='collection'?'braces':'table'} size={17}/></span><div><strong>{entity?.displayName??entity?.name??'实体不存在'}</strong><span>{entity?.name??'—'}</span></div></div>;
}
function AuthorizedPreview({id}:{id:string}) {
  const [rows,setRows]=useState<Row[] | null>(null),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const request=useRef<{id:string;promise:Promise<Row[]>}|null>(null);
  useEffect(()=>{
    let active=true;
    setBusy(true);setRows(null);setError('');
    const tableId=canonicalId(id);
    if(request.current?.id!==tableId)request.current={id:tableId,promise:previewMetadataRecords(tableId)};
    request.current.promise
      .then(result=>{if(active)setRows(result);})
      .catch(e=>{if(active)setError(errorText(e));})
      .finally(()=>{if(active)setBusy(false);});
    return ()=>{active=false;};
  },[id]);
  const fields=rows?.[0]?Object.keys(rows[0]):[];
  return <div className="panel-section">{error&&<Alert type="error" title="数据预览读取失败" description={error}/>} {!error&&<Table size="small" loading={busy} rowKey={(_,i)=>String(i)} dataSource={rows??[]} pagination={false} scroll={{x:'max-content'}} columns={fields.map(name=>({title:name,dataIndex:name,render:(v:unknown)=>v==null?'NULL':typeof v==='object'?JSON.stringify(v):String(v)}))}/>}</div>;
}
function EntityPanel({id}:{id:string}){
  const data=useStudio(s=>s.data)!;
  const entity=data.snapshot.entities.find(e=>e.id===id);
  const[search,setSearch]=useState('');
  if(!entity)return <Empty description="实体不存在"/>;
  const source=data.snapshot.sources.find(s=>s.id===entity.sourceId);
  const pk=new Set(entity.keys?.filter(k=>k.kind==='primary').flatMap(k=>k.fieldIds));
  const cols:ColumnsType<FieldDef>=[
    {title:'字段',key:'name',width:'45%',sorter:(a,b)=>a.path.localeCompare(b.path,'zh-CN',{numeric:true}),render:(_,f)=><div className="detail-field"><div><span className="detail-field-name"><code>{f.path}</code>{pk.has(f.id)&&<span className="detail-field-key" title="主键" aria-label="主键"><Icon name="key" size={12}/></span>}</span><small>{f.comment??'未提供注释'}</small></div></div>},
    {title:'类型',dataIndex:'nativeType',key:'type',width:'37%',render:(value:string)=><code className="field-native-type muted" title={value}>{value}</code>},
    {title:'可空',dataIndex:'nullable',key:'nullable',width:'18%',align:'center',render:(v:string)=>v==='false'?'否':v==='true'?'是':'?'}
  ];
  const fields=entity.fields.filter(f=>`${f.name} ${f.path} ${f.comment??''}`.toLowerCase().includes(search.toLowerCase()));

  return <>
    <Tabs size="small" className="inspector-tabs" items={[
      {key:'fields',label:<span className="inspector-field-tab">字段结构<span className="field-count-badge">{entity.fields.length}</span></span>,children:<div className="panel-section"><Input placeholder="搜索字段…" prefix={<Icon name="search" size={14}/>} value={search} onChange={e=>setSearch(e.target.value)} allowClear/><Table className="field-table" size="small" tableLayout="fixed" columns={cols} dataSource={fields} rowKey="id" pagination={fields.length>30?{pageSize:30,size:'small'}:false}/><div className="section-heading"><h3>键约束</h3></div>{entity.keys?.length?entity.keys.map(k=><div key={k.id} className="key-definition"><Icon name="key" size={14}/><div><strong>{k.kind==='primary'?'主键':'唯一键'} {k.fieldIds.length>1?'· 联合键':''}</strong><code>({k.fieldIds.map(fid=>entity.fields.find(f=>f.id===fid)?.name??fid).join(', ')})</code></div></div>):<p className="subtle-note">{entity.constraintsAvailability==='unknown'?'此来源未提供约束信息，不能判定是否存在主键。':'当前快照未声明主键或唯一键。'}</p>}</div>},
      {key:'preview',label:'数据预览',children:<AuthorizedPreview id={id}/>},
      {key:'info',label:'元信息',children:<div className="panel-section"><dl className="property-list"><dt>数据源</dt><dd>{source?.name}</dd><dt>数据库</dt><dd>{entity.catalog??'—'}</dd><dt>Schema</dt><dd>{entity.schemaName??'—'}</dd><dt>说明</dt><dd>{entity.comment??'—'}</dd><dt>采集时间</dt><dd>{displayDate(data.snapshot.capturedAt)}</dd><dt>结构依据</dt><dd>{entity.shapeBasis==='declared_complete'?'声明结构':entity.shapeBasis==='observed_sample'?'样本观测':'来源提供的结构'}</dd><dt>稳定标识</dt><dd><code>{entity.id}</code></dd></dl></div>},
    ]}/>
  </>;
}
function RelationshipPanel({id}:{id:string}){
  const data=useStudio(s=>s.data)!;const{message,modal}=App.useApp();const{confirmMany,archive,remove}=useRelationActions();
  const [busy,setBusy]=useState(false),[phase,setPhase]=useState('');
  const decide=async(status:'suggested'|'rejected')=>{setBusy(true);try{await useStudio.getState().decide(id,status);message.success('关系状态已保存到共享后端');}catch(e){message.error(errorText(e));}finally{setBusy(false);}};
  const validate=async()=>{setBusy(true);try{await useStudio.getState().save();const state=useStudio.getState();if(state.saveState==='error')throw new Error(state.saveError||'请先保存模型');await workspaceService.validate(id,state.data!.activeDiagramId,j=>setPhase(j.phase??j.status));const semantic=await workspaceService.refreshSemantics(state.data!.activeDiagramId);useStudio.setState(s=>({data:s.data?{...s.data,...semantic}:null}));message.success('验证已完成，请查看实际结果');}catch(e){message.error(errorText(e));}finally{setBusy(false);setPhase('');}};
  const rel=data.relationships.find(r=>r.id===id);
  if(!rel)return <Empty description="关系不存在"/>;
  const startValidation=()=>modal.confirm({title:'读取业务数据验证此关系？',content:'按已保存的联合键和条件读取最多 200 条源记录与 20,000 条目标键。只保存汇总结果，不保存原始业务值。结果有明确采样范围，不等同于关系正确概率。',okText:'开始只读验证',onOk:validate});
  const createForeignKey=async()=>{
    setBusy(true);
    try{
      const plan=await previewPhysicalForeignKey(id);let agreed=false;
      modal.confirm({title:plan.alreadyExists?'物理外键已存在':'确认创建物理数据库外键',width:720,content:<div>
        <Alert type="warning" showIcon title="将修改真实业务数据库结构" description="仅允许同一数据源、无条件引用、完整目标主键或唯一键。数据库会检查现有数据，操作可能锁表；失败不会关闭数据库约束检查。"/>
        <p>物理外键只约束引用完整性，不自动增加 NOT NULL 或源字段唯一键。因此不会单独强制“至少一个”或一对一的业务基数；这些仍需关系验证及单独的数据库约束设计。</p>
        <p>拟执行 SQL</p><pre style={{whiteSpace:'pre-wrap',wordBreak:'break-word'}}>{plan.ddl}</pre>
        <p>如需撤销，请由数据库维护人员执行下列 SQL；本应用不会自动删约束。</p><pre style={{whiteSpace:'pre-wrap',wordBreak:'break-word'}}>{plan.rollbackSql}</pre>
        <Checkbox onChange={e=>{agreed=e.target.checked;}}>我了解此操作会修改数据库，并已确认变更窗口</Checkbox>
      </div>,okText:plan.alreadyExists?'记录现有外键':'确认执行 DDL',onOk:async()=>{
        if(!agreed){message.warning('请先勾选数据库变更确认');throw new Error('尚未确认数据库变更');}
        try{await createPhysicalForeignKey(id,plan.hash);const semantic=await workspaceService.refreshSemantics();useStudio.setState(s=>({data:s.data?{...s.data,...semantic}:null}));message.success('物理外键已核验并记录');}
        catch(e){message.error(errorText(e));throw e;}
      }});
    }catch(e){message.error(errorText(e));}finally{setBusy(false);}
  };
  const source=data.snapshot.entities.find(e=>e.id===rel.sourceEntityId),target=data.snapshot.entities.find(e=>e.id===rel.targetEntityId);
  const evidence=data.evidence.filter(e=>e.relationshipId===id).sort((a,b)=>b.capturedAt.localeCompare(a.capturedAt));
  const current=evidence.find(e=>e.id===rel.verification?.evidenceId);
  const kind=relationshipKind(rel);
  const mappings=<div className="field-mappings">{rel.mappings?.map((m,i)=><div className="field-mapping" key={i}><code>{source?.fields.find(f=>f.id===m.sourceFieldId)?.path??'字段已失效'}</code><Icon name="arrow" size={13}/><code>{target?.fields.find(f=>f.id===m.targetFieldId)?.path??'字段已失效'}</code></div>)}</div>;
  return <>
    <div className="panel-section relation-hero"><Status relation={rel}/><h3>{rel.name}</h3><div className="relation-endpoints"><EntitySummary entity={source}/><div className="relation-direction"><Icon name="arrow" size={17}/><span>引用</span></div><EntitySummary entity={target}/></div>{mappings}<div className="identity-tags"><Tag>{rel.mappings?.length??0} 组字段映射</Tag><Tag>基数 {multiplicityLabel(rel)}</Tag>{rel.origin==='inference'&&<Tag color="gold">最初来源：系统推荐</Tag>}</div></div>
    <Tabs className="inspector-tabs" size="small" items={[
      {key:'detail',label:'关联信息',children:<div className="panel-section"><div className="section-heading"><h3>业务说明</h3></div><p className="description-text">{rel.description||'暂未填写业务说明。'}</p><dl className="property-list"><dt>关系语义</dt><dd>{rel.semanticType==='join'?'一般 JOIN':'引用关系'}</dd><dt>最初来源</dt><dd>{rel.origin==='catalog'?'导入的目录定义':rel.origin==='manual'?'人工创建':'结构推断'}</dd><dt>人工状态</dt><dd>{rel.reviewStatus==='observed'?'不适用（目录定义）':rel.reviewStatus==='confirmed'?'已确认':rel.reviewStatus==='rejected'?'已忽略':rel.reviewStatus==='archived'?'已归档':'待确认'}</dd><dt>最近更新</dt><dd>{displayDate(rel.updatedAt)}</dd><dt>版本</dt><dd>v{rel.version??1}</dd></dl>{kind==='catalog'?<Alert type="info" showIcon title="数据库外键只读" description="本应用仅展示目录定义，不创建或修改源数据库约束。可在本图隐藏此关系。"/>:<Alert type="info" showIcon title="共享逻辑关系台账" description="人工关系与审批状态保存在当前租户的共享后端。确认关系不会创建物理外键，也不表示完成业务数据验证。"/>}</div>},
      {key:'evidence',label:'推荐依据',children:<div className="panel-section">{rel.recommendation?<><div className="score-card"><div><span>推荐分</span><strong>{rel.recommendation.score}<small>/ 100</small></strong></div><div><Tag color="gold">{rel.recommendation.level==='structure_only'?'结构推荐':rel.recommendation.level==='conflict'?'存在冲突':'有数据支持'}</Tag><p>用于候选排序<br/>不是关联正确的概率</p></div></div>{(rel.recommendation.factors??[]).map(f=><div className="evidence-item" key={f.code}><span className="evidence-check"><Icon name="check" size={14}/></span><div><strong>{f.code==='name'?'名称与业务语义':f.code==='type'?'字段类型兼容':f.code==='key'?'目标键结构':'业务范围'}</strong><p>{f.explanation}</p></div></div>)}{!rel.recommendation.factors?.length&&<p className="subtle-note">当前关系尚未记录分项评分依据，请结合业务检查。</p>}{(rel.recommendation.penalties??[]).map((x,i)=><Alert key={i} type="warning" showIcon title={x} style={{margin:'12px 0'}}/>)}<div className="section-heading"><h3>尚未验证</h3></div>{rel.recommendation.unmetChecks?.map((x,i)=><div className="unchecked-item" key={i}><Icon name="info" size={13}/>{x}</div>)}<p className="subtle-note">规则版本：{rel.recommendation.ruleVersion}</p></>:<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={rel.origin==='catalog'?'这是一条目录外键，不需要推荐评分。':'此关系由人工创建，没有系统推荐评分。'}/>}</div>},
      {key:'validation',label:'验证情况',children:<div className="panel-section">{current?<>
        <Alert type={current.validationStatus==='violations_found'?'warning':'info'} showIcon title={current.validationStatus==='violations_found'?'发现未匹配或基数异常':current.validationStatus==='inconclusive'?'当前范围不足以得出结论':current.validationStatus==='full_supported'?'指定范围全量验证支持':'指定样本验证支持'} description="结果仅适用于记录的快照与范围，不代表业务语义必然成立。"/>
        <div className="validation-metrics"><div><span>去重包含率</span><strong>{current.metrics?.containmentRatio==null?'—':`${(current.metrics.containmentRatio*100).toFixed(1)}%`}</strong></div><div><span>匹配联合键</span><strong>{current.metrics?.matchedDistinctTuples??'—'}<small>/{current.metrics?.sourceDistinctTuples??'—'}</small></strong></div><div><span>未匹配行</span><strong className="warning-text">{current.metrics?.orphanRows??'—'}</strong></div></div>
        <dl className="property-list"><dt>采样方法</dt><dd>{current.sampleMethod??'未提供'}</dd><dt>目标范围</dt><dd>{current.targetDomain==='full'?'完整目标键域':current.targetDomain==='sample_only'?'仅目标样本':'未查询'}</dd><dt>目标唯一性</dt><dd>{current.targetUniquenessBasis==='constraint'?'依据目录键定义':current.targetUniquenessBasis==='full_exact'?'指定范围内精确检验':current.targetUniquenessBasis==='sample_only'?'仅样本内未发现重复':'未证明'}</dd><dt>记录时间</dt><dd>{displayDate(current.capturedAt)}</dd></dl>
        {current.warnings?.map((text,i)=><Alert key={i} type="warning" title={text} style={{marginTop:12}}/>)}
      </>:<div className="verification-empty"><span><Icon name="activity" size={28}/></span><h3>尚未进行数据验证</h3><p>点击验证后，按授权读取真实业务数据。<br/>使用完整联合键与已保存条件，只保留汇总证据。</p><Tag>验证状态：未验证</Tag></div>}
      {rel.origin!=='catalog'&&canWrite()&&<Button block loading={busy} disabled={rel.id.startsWith('candidate:')||rel.lifecycle==='broken'} onClick={startValidation}>按业务数据验证关系</Button>}{phase&&<p className="subtle-note">{phase}</p>}<p className="subtle-note">人工确认不改变数据验证状态，也不会在源数据库中创建外键。</p>{rel.physicalForeignKey&&<Alert type="success" title={`物理外键：${rel.physicalForeignKey.constraintName}`} description={<pre style={{whiteSpace:'pre-wrap'}}>{rel.physicalForeignKey.ddl}</pre>}/>}</div>},
    ]}/>
    <div className="panel-footer stacked-footer">
      {rel.reviewStatus==='suggested'&&<div className="footer-buttons"><Button disabled={!canWrite()||busy} onClick={()=>decide('rejected')}>忽略推荐</Button><Button type="primary" disabled={!canWrite()||busy||rel.lifecycle==='broken'} icon={<Icon name="check" size={15}/>} onClick={()=>confirmMany([rel])}>确认关系</Button></div>}
      {rel.origin!=='catalog'&&rel.reviewStatus==='confirmed'&&<div className="footer-buttons"><Button disabled={!canWrite()||busy} onClick={()=>useStudio.getState().openEditor({editingId:id})} icon={<Icon name="edit" size={15}/>}>编辑关系</Button><Button danger disabled={!canWrite()||busy} onClick={()=>archive(rel)}>归档关系</Button></div>}
      {rel.origin!=='catalog'&&['rejected','archived'].includes(rel.reviewStatus)&&<Button block disabled={!canWrite()||busy} onClick={()=>decide('suggested')}>恢复为待确认</Button>}
      {rel.origin==='inference'&&rel.reviewStatus==='confirmed'&&<Button type="link" disabled={!canWrite()||busy} onClick={()=>decide('suggested')}>撤回确认</Button>}
      {rel.origin!=='catalog'&&!rel.id.startsWith('candidate:')&&<Button type="link" danger disabled={!canWrite()||busy} onClick={()=>remove(rel)}>删除共享逻辑关系</Button>}
      {rel.origin!=='catalog'&&rel.reviewStatus==='confirmed'&&<Button block loading={busy} disabled={!canWrite()||rel.lifecycle==='broken'} onClick={createForeignKey}>预检查并创建物理外键</Button>}
      <Button type="text" block icon={<Icon name="hide" size={14}/>} onClick={()=>useStudio.getState().hideRelationship(id)}>仅在当前关系图中隐藏</Button>
    </div>
  </>;
}
function RelationCard({relation,checked,onCheck,onOpen}:{relation:Relationship;checked?:boolean;onCheck?:(value:boolean)=>void;onOpen:()=>void}){
  const entities=useStudio(s=>s.data?.snapshot.entities)??[];
  const source=entities.find(e=>e.id===relation.sourceEntityId),target=entities.find(e=>e.id===relation.targetEntityId);
  return <article className={`recommendation-card ${checked?'checked':''}`}>
    <div className="recommendation-card-top">{onCheck&&<Checkbox checked={checked} onChange={e=>onCheck(e.target.checked)} aria-label={`选择${relation.name}`}/>}<Status relation={relation}/>{relation.recommendation&&<span className="recommendation-score">{relation.recommendation.score}<small>分</small></span>}</div>
    <button className="recommendation-link" onClick={onOpen}><strong>{source?.displayName??source?.name}</strong><Icon name="arrow" size={15}/><strong>{target?.displayName??target?.name}</strong></button>
    <div className="recommendation-mapping">{relation.mappings?.map(m=>source?.fields.find(f=>f.id===m.sourceFieldId)?.name??'?').join(' + ')}</div>
    <div className="recommendation-meta"><span>{relation.mappings?.length??0} 组映射</span><span>{relation.verification?.status==='violations_found'?<span className="warning-text">数据校验有异常</span>:relation.recommendation?.level==='structure_only'?'仅结构分析':relation.origin==='catalog'?'目录定义':'逻辑模型'}</span><button onClick={onOpen}>查看依据<Icon name="chevron" size={12}/></button></div>
  </article>;
}
function DiscoveryPanel({onDiscover}:{onDiscover:()=>void}){
  const data=useStudio(s=>s.data)!;const job=useStudio(s=>s.job);const{confirmMany}=useRelationActions();
  const[selection,setSelection]=useState<string[]>([]);const[query,setQuery]=useState('');
  const suggestions=useMemo(()=>data.relationships.filter(r=>r.reviewStatus==='suggested'&&r.name.toLowerCase().includes(query.toLowerCase())).sort((a,b)=>(b.recommendation?.score??0)-(a.recommendation?.score??0)),[data.relationships,query]);
  const selected=suggestions.filter(r=>selection.includes(r.id));
  return <>
    <div className="panel-section discovery-intro"><div className="discovery-summary"><span className="discovery-spark"><Icon name="spark" size={24}/></span><div><strong>{suggestions.length} 条待确认关系</strong><p>从关联线索，到可解释的关系模型</p></div></div>
      {job&&<div className={`job-card job-${job.status}`}><div className="job-title"><Icon name={job.status==='running'?'activity':job.status==='completed'?'check':'info'} size={15}/><strong>{job.title}</strong>{job.status==='running'&&<Button type="link" size="small" onClick={()=>useStudio.getState().cancelDiscovery()}>取消</Button>}</div>{job.status==='running'?<><div className="job-stages"><span className="reached">{job.title}</span></div>{job.total>0&&<Progress percent={Math.round(100*job.processed/job.total)} showInfo={false} size="small"/>}</>:<p>{job.status==='completed'?`新增 ${job.newCount} 条推荐；${job.skippedCount} 个既有映射未被覆盖。`:job.error??'已保留之前的关系，本次未应用结果。'}</p>}</div>}
      <Alert showIcon type="info" title="推荐分不是正确率" description="结构推荐尚未经过数据验证。确认前请检查字段映射、租户范围与业务含义。"/>
    </div>
    <div className="panel-section recommendation-list"><Input value={query} onChange={e=>setQuery(e.target.value)} placeholder="搜索推荐关系…" prefix={<Icon name="search" size={14}/>} allowClear/>
      {!!suggestions.length&&<div className="batch-heading"><Checkbox checked={selected.length===suggestions.length} indeterminate={selected.length>0&&selected.length<suggestions.length} onChange={e=>setSelection(e.target.checked?suggestions.map(r=>r.id):[])}>全选</Checkbox><span>{selected.length?`已选 ${selected.length} 条`:'按推荐分排序'}</span></div>}
      {suggestions.map(r=><RelationCard key={r.id} relation={r} checked={selection.includes(r.id)} onCheck={checked=>setSelection(s=>checked?[...new Set([...s,r.id])]:s.filter(x=>x!==r.id))} onOpen={()=>{useStudio.getState().addEntities([r.sourceEntityId,r.targetEntityId]);useStudio.getState().selectRelationship(r.id);}}/>)}
      {!suggestions.length&&<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有待确认推荐。可重新分析或手动创建关系。"/>}
    </div>
    <div className="panel-footer footer-buttons"><Button onClick={onDiscover} disabled={!canWrite()||job?.status==='running'} icon={<Icon name="refresh" size={15}/>}>重新分析</Button><Button type="primary" disabled={!canWrite()||!selected.length} onClick={()=>confirmMany(selected,()=>setSelection([]))}>确认所选{selected.length?`（${selected.length}）`:''}</Button></div>
  </>;
}
function RelationsPanel(){
  const data=useStudio(s=>s.data)!;const diagram=useStudio(activeDiagram)!;const{confirmMany}=useRelationActions();
  const[filter,setFilter]=useState('active'),[query,setQuery]=useState(''),[selection,setSelection]=useState<string[]>([]);
  const relations=data.relationships.filter(r=>r.name.toLowerCase().includes(query.toLowerCase())&&(filter==='active'?!['rejected','archived'].includes(r.reviewStatus):filter==='rejected'||filter==='archived'?r.reviewStatus===filter:relationshipKind(r)===filter&&!['rejected','archived'].includes(r.reviewStatus)));
  const selectable=relations.filter(canConfirmRelationship),selected=selectable.filter(r=>selection.includes(r.id));
  const names=new Map(data.snapshot.entities.map(entity=>[entity.id,entity.displayName||entity.name]));
  const open=(id:string)=>useStudio.getState().selectRelationship(id);
  return <div className="panel-section relations-panel">
    <div className="relations-toolbar"><Input placeholder="搜索关系名称…" prefix={<Icon name="search" size={14}/>} allowClear value={query} onChange={e=>{setQuery(e.target.value);setSelection([]);}}/><Select className="relation-filter-select" value={filter} onChange={value=>{setFilter(value);setSelection([]);}} options={[{value:'active',label:'全部有效关系'},{value:'catalog',label:'数据库外键'},{value:'logical',label:'已确认逻辑关系'},{value:'suggested',label:'待确认推荐'},{value:'rejected',label:'已忽略'},{value:'archived',label:'已归档'}]}/></div>
    <p className="subtle-note">共 {relations.length} 条。关系在同一工作区的视图之间共享。</p>
    {!!diagram.hiddenRelationshipIds.length&&<Button type="link" size="small" onClick={()=>useStudio.getState().updateView({hiddenRelationshipIds:[]})}>恢复本图隐藏的 {diagram.hiddenRelationshipIds.length} 条关系</Button>}
    {!!selectable.length&&<div className="relations-batch"><Checkbox disabled={!canWrite()} checked={selected.length===selectable.length} indeterminate={selected.length>0&&selected.length<selectable.length} onChange={e=>setSelection(e.target.checked?selectable.map(r=>r.id):[])}>全选当前列表待确认项</Checkbox><Button type="primary" size="small" disabled={!canWrite()||!selected.length} onClick={()=>confirmMany(selected,()=>setSelection([]))}>批量确认{selected.length?`（${selected.length}）`:''}</Button></div>}
    <div className="relations-compact-list" role="list">{relations.map(r=><div className="relations-compact-row" role="listitem" key={r.id}>
      <Checkbox checked={selection.includes(r.id)&&canConfirmRelationship(r)} disabled={!canWrite()||!canConfirmRelationship(r)} aria-label={`选择关系：${r.name}`} onChange={e=>setSelection(ids=>e.target.checked?[...new Set([...ids,r.id])]:ids.filter(id=>id!==r.id))}/>
      <button type="button" className="relations-compact-main" onClick={()=>open(r.id)} title={r.name}><span className="relations-compact-endpoints"><span title={names.get(r.sourceEntityId)||'实体不存在'}>{names.get(r.sourceEntityId)||'实体不存在'}</span><Icon name="arrow" size={12}/><span title={names.get(r.targetEntityId)||'实体不存在'}>{names.get(r.targetEntityId)||'实体不存在'}</span></span><span className="relations-compact-details"><Status relation={r}/><span className="relations-compact-name">{r.name}</span>{r.recommendation&&<b>{r.recommendation.score} 分</b>}</span></button>
      <Button type="link" size="small" onClick={()=>open(r.id)}>详情</Button>
    </div>)}</div>
    {!relations.length&&<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="没有符合条件的关系"/>}
  </div>;
}
function ActivityPanel(){
  const events=useStudio(s=>s.data?.audit)??[];
  return <div className="panel-section"><p className="subtle-note">本地工作区操作摘要。完整记录可从顶部“操作审计”查看。</p><div className="activity-list">{events.map(e=><div className="activity-event" key={e.id}><span className="activity-point"/><time>{displayDate(e.at)}</time><strong>{e.action}</strong><p>{e.detail}</p></div>)}</div></div>;
}
export function Inspector({onDiscover}:{onDiscover:()=>void}){
  const panel=useStudio(s=>s.panel);const width=useStudio(s=>s.inspectorWidth);const snapshot=useStudio(s=>s.data?.snapshot);const {message}=App.useApp();if(!panel)return null;
  const entity=panel.type==='entity'?snapshot?.entities.find(item=>item.id===panel.id):undefined;
  const source=entity?snapshot?.sources.find(item=>item.id===entity.sourceId):undefined;
  const fullName=entity?[entity.catalog,entity.schemaName,entity.name].filter(Boolean).join('.'):'';
  const copyName=()=>{void (navigator.clipboard?.writeText(fullName)??Promise.reject(new Error('当前浏览器不支持剪贴板，请在安全上下文中打开。'))).then(()=>message.success('完整表名已复制。')).catch(()=>message.error('无法访问剪贴板，请手动复制表名。'));};
  const titles={entity:['table','数据表详情'],relationship:['link','关系详情'],discovery:['spark','关系发现'],relations:['list','工作区关系'],activity:['activity','操作记录']};
  return <aside className="inspector" aria-label={entity?`${entity.displayName??entity.name} · ${entity.name}`:titles[panel.type][1]} style={{width,flexBasis:width}}><ResizeHandle side="inspector" width={width} onChange={value=>useStudio.getState().setPanelWidth('inspector',value)}/><div className={`inspector-heading ${entity?'entity-inspector-heading':''}`}>{panel.type==='relationship'&&<Tooltip title="返回关系列表"><Button type="text" size="small" aria-label="返回关系列表" icon={<Icon name="back" size={16}/>} onClick={()=>useStudio.getState().setPanel({type:'relations'})}/></Tooltip>}{entity?<h2 className="entity-inspector-title"><SourceIcon engine={source?.engine} small/><span className="entity-inspector-names"><strong title={entity.displayName??entity.name}>{entity.displayName??entity.name}</strong><small title={entity.name}>{entity.name}</small></span></h2>:<h2><Icon name={titles[panel.type][0]} size={18}/>{titles[panel.type][1]}</h2>}<div className="inspector-heading-actions">{entity&&<Tooltip title={fullName}><Button type="text" size="small" className="inspector-copy-name" icon={<Icon name="copy" size={14}/>} onClick={copyName}>复制表名</Button></Tooltip>}<Tooltip title="关闭面板"><Button type="text" size="small" aria-label="关闭详情面板" icon={<Icon name="close" size={18}/>} onClick={()=>useStudio.getState().setPanel(null)}/></Tooltip></div></div><div className="inspector-content" key={panel.type==='entity'||panel.type==='relationship'?`${panel.type}-${panel.id}`:panel.type}>{panel.type==='entity'?<EntityPanel id={panel.id}/>:panel.type==='relationship'?<RelationshipPanel id={panel.id}/>:panel.type==='discovery'?<DiscoveryPanel onDiscover={onDiscover}/>:panel.type==='relations'?<RelationsPanel/>:<ActivityPanel/>}</div></aside>;
}
