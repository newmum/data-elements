import { useEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, Button, Checkbox, Dropdown, Empty, Skeleton, Space, Tooltip, Typography } from 'antd';
import { AppstoreOutlined, BulbOutlined, CodeOutlined, DeleteOutlined, DownOutlined, EyeOutlined, MoreOutlined, PlusOutlined, SettingOutlined, SwapOutlined, UnorderedListOutlined, UpOutlined } from '@ant-design/icons';
import { nanoid } from 'nanoid';
import { compileFieldMapping, fieldMappingCapabilities, mappingVersions, recommendFieldMapping, validateFieldMapping, type FieldMeta, type FunctionParameterSchema, type MappingIssue, type MappingTableRef, type UnifiedMappingDraft, type UnifiedMappingRule, type MappingRule } from '../api/fieldMapping';
import { fetchDatabases } from '../api/dataassets';
import { useSessionRevision } from '../api/bridgeSession';
import MappingDraftTable from './MappingDraftTable';
import { getNifiOverlayContainer } from './overlayContainer';
import MappingAssetsPanel from './MappingAssetsPanel';
import FieldMappingVisual from './FieldMappingVisual';
import {RecommendModal,TemplateModal} from './FieldMappingDialogs';
import {displayPath,fieldName,fieldsForPresentation,normalizePath,sourceNames,type FieldInfo,type FieldType,type LocalRecommendation} from './fieldMappingPresentation';
import {findUniqueCaseInsensitiveFieldMatches,identifierMatchKey} from '../utils/fieldNameMatching';
import CodeMirror from '@uiw/react-codemirror';
import {json} from '@codemirror/lang-json';
import MappingRuleDialog from './MappingRuleDialog';
import { type DatasourceChoice } from './UnifiedRuleForms';
import { assertExactJsonNumbers, createRule, emptyUnifiedDraft, inputSummary, logicalFieldType, includeOdsSystemMappings, isEditableRule } from './unifiedRuleModel';
import { concreteInputLabel, concreteRuleLabel } from './mappingRulePresentation';
import MappingFlowSettings from './MappingFlowSettings';
import { MappingInspectionDialog, MappingRuntimeDialog, mappingErrorText } from './MappingReviewDialogs';
import GovernancePreviewDialog from './GovernancePreviewDialog';
import './unifiedMapping.css';

interface Props {
  value:unknown; onChange:(value:string)=>void;
  columnMeta?: {sourceLabels:Record<string,string>;targetLabels:Record<string,string>;sourceSchema?:FieldMeta[];targetSchema?:FieldMeta[]};
  datasources?:DatasourceChoice[];
  columnsLoading?:boolean; columnsError?:string; onReloadColumns?:()=>void;
  onViewModeChange?:(mode:'list'|'visual'|'dsl')=>void;
  assetContext?:{pipelineId?:string|null;nodeId?:string;sourceRef?:MappingTableRef;targetRef?:MappingTableRef};
}
const ODS_UUID_EXPRESSION = 'UUID()';
const EMPTY_SCHEMA:FieldMeta[]=[];
const EMPTY_DATASOURCES:DatasourceChoice[]=[];

export default function FieldMappingEditor(props:Props) {
  const parsed=useMemo(()=>{
    try{
      const raw=typeof props.value==='string'?(assertExactJsonNumbers(props.value),JSON.parse(props.value)):props.value;
      const spec=raw||emptyUnifiedDraft();
      if(spec.schemaVersion==='2.0'&&(!Array.isArray(spec.rules)||spec.rules.some((rule:unknown)=>!isEditableRule(rule))))throw new Error('治理规则结构不完整，请检查配置内容');
      return {spec,error:''};
    }catch(cause:any){return {spec:null,error:cause.message};}
  },[props.value]);
  const schemaSignature=JSON.stringify([props.columnMeta?.sourceSchema??[],props.columnMeta?.targetSchema??[]]);
  const [loaded,setLoaded]=useState<{source:unknown;schema:string;draft:UnifiedMappingDraft}|null>(null);
  const [error,setError]=useState(''),[retry,setRetry]=useState(0);
  useEffect(()=>{
    if(!parsed.spec||parsed.spec.schemaVersion==='2.0'||props.columnsLoading)return;
    let active=true;setError('');
    const context=props.assetContext;
    const specification=context?.sourceRef||context?.targetRef
      ?mappingVersions.schema(context.sourceRef,context.targetRef).then(schemaContract=>({...parsed.spec,schemaContract}))
      :Promise.resolve(parsed.spec);
    specification.then(spec=>compileFieldMapping(spec,props.columnMeta?.sourceSchema??[],props.columnMeta?.targetSchema??[]))
      .then(result=>{if(!result.plan)throw new Error('未能读取治理规则，请重试');if(active)setLoaded({source:props.value,schema:schemaSignature,draft:{...result.plan.executionPlan,executionEngine:'JAVA_RECORD'}});})
      .catch(cause=>{if(active)setError(cause?.response?.data?.error??cause.message??'读取治理规则失败');});
    return ()=>{active=false;};
  },[props.value,retry,props.columnsLoading,schemaSignature]);
  const problem=parsed.error||(parsed.spec?.schemaVersion==='2.0'?'':error);
  if(problem)return <Alert type="error" showIcon message={problem} action={<Button onClick={()=>setRetry(retry+1)}>重试</Button>}/>;
  // Keep an open form mounted while metadata is refreshed in the background.
  const draft=parsed.spec?.schemaVersion==='2.0'?parsed.spec:loaded&&loaded.source===props.value?loaded.draft:null;
  if(!draft)return <Skeleton active paragraph={{rows:6}}/>;
  return <FieldMappingEditorContent {...props} value={draft}/>;
}

function FieldMappingEditorContent({value,onChange,columnMeta,datasources=EMPTY_DATASOURCES,columnsLoading,columnsError,onReloadColumns,assetContext,onViewModeChange}:Omit<Props,'value'>&{value:UnifiedMappingDraft}) {
  const {message,modal}=App.useApp();
  const schemaFields=(fields:FieldMeta[],side:'source'|'target')=>{const contract=(value.schemaContract as any)?.[side];if(!Array.isArray(contract))return fields;const byName=new Map(contract.map((field:any)=>[field.name,field.logicalType]));return fields.map(field=>byName.has(field.name)?{...field,logicalType:String(byName.get(field.name))}:field);};
  const sourceSchema=useMemo(()=>schemaFields(columnMeta?.sourceSchema??EMPTY_SCHEMA,'source'),[columnMeta?.sourceSchema,value.schemaContract]);
  const targetSchema=useMemo(()=>schemaFields(columnMeta?.targetSchema??EMPTY_SCHEMA,'target'),[columnMeta?.targetSchema,value.schemaContract]);
  const sessionRevision=useSessionRevision();
  const [draft,setDraft]=useState(value),[selected,setSelected]=useState<React.Key[]>([]);
  const [editing,setEditing]=useState<UnifiedMappingRule|null>(null);
  const [mode,setMode]=useState<'list'|'visual'|'dsl'>('list'),[dslText,setDslText]=useState(()=>JSON.stringify(value,null,2));
  useEffect(()=>onViewModeChange?.(mode),[mode,onViewModeChange]);
  const dslDirty=useRef(false);
  const [functionSchemas,setFunctionSchemas]=useState<Record<string,FunctionParameterSchema>>({}),[sourceOptions,setSourceOptions]=useState(datasources);
  const [assetsOpen,setAssetsOpen]=useState(false),[previewOpen,setPreviewOpen]=useState(false);
  const [flowSettingsOpen,setFlowSettingsOpen]=useState(false);
  const [recommendOpen,setRecommendOpen]=useState(false),[recommendations,setRecommendations]=useState<LocalRecommendation[]>([]),[templateOpen,setTemplateOpen]=useState(false);
  const [busy,setBusy]=useState(false),[capabilityError,setCapabilityError]=useState('');
  const [issues,setIssues]=useState<{errors:MappingIssue[];warnings:MappingIssue[]}|null>(null);
  const [validationError,setValidationError]=useState(''),[validationRetry,setValidationRetry]=useState(0);
  const [inspection,setInspection]=useState<'validate'|'compile'|'schema'|null>(null);
  const [runtimeOperation,setRuntimeOperation]=useState<'upgrade'|'start'|null>(null);
  useEffect(()=>{setDraft(value);setSelected(keys=>keys.filter(key=>value.rules.some(r=>r.ruleId===key)));if(!dslDirty.current)setDslText(JSON.stringify(value,null,2));},[value]);
  useEffect(()=>{if(datasources.length)setSourceOptions(datasources);},[datasources]);
  useEffect(()=>setSourceOptions(datasources),[sessionRevision]);
  useEffect(()=>{
    let active=true;
    fieldMappingCapabilities().then(result=>{if(active)setFunctionSchemas(Object.fromEntries(result.functions.filter(f=>f.previewSupported&&f.runtimeSupported).map(f=>[f.name,Object.assign(f.parameterSchema??[],{additionalParametersAllowed:f.additionalParametersAllowed})])));}).catch(()=>{if(active)setCapabilityError('函数目录暂不可用，已有参数仍保留，请稍后重试。');});
    return ()=>{active=false;};
  },[]);
  useEffect(()=>{
    let active=true;setIssues(null);setValidationError('');
    const timer=setTimeout(()=>validateFieldMapping(draft,sourceSchema,targetSchema).then(result=>{if(active)setIssues({errors:result.errors??[],warnings:result.warnings??[]});}).catch(cause=>{if(active)setValidationError(mappingErrorText(cause));}),350);
    return ()=>{active=false;clearTimeout(timer);};
  },[draft,sourceSchema,targetSchema,validationRetry]);
  function update(next:UnifiedMappingDraft){setDraft(next);onChange(JSON.stringify({...next,executionEngine:'JAVA_RECORD'},null,2));}
  function openRule(rule:UnifiedMappingRule){setEditing(structuredClone(rule));}
  function add(template='DIRECT'){setEditing(createRule(template,nanoid()));}
  function updateRule(rule:UnifiedMappingRule){update({...draft,rules:draft.rules.map(item=>item.ruleId===rule.ruleId?rule:item)});}
  function createMapping(source:string,target:string){const current=draft.rules.find(rule=>rule.target.name===target);if(current){openRule(current);return;}const rule=createRule('DIRECT',nanoid());rule.input.name=source;const field=targetSchema.find(field=>field.name===target);rule.target={...rule.target,name:target,...(field?{logicalType:logicalFieldType(field),nullable:field.nullable,length:field.length,precision:field.precision,scale:field.scale}:{})};openRule(rule);}
  function saveRule(rule:UnifiedMappingRule){
    const index=draft.rules.findIndex(r=>r.ruleId===editing?.ruleId),rules=[...draft.rules];
    if(index<0)rules.push(rule);else rules[index]=rule;
    update({...draft,rules});setEditing(null);
  }
  function moveRule(id:string,offset:number){
    const rules=[...draft.rules],index=rules.findIndex(r=>r.ruleId===id);
    if(index<0||index+offset<0||index+offset>=rules.length)return;
    [rules[index],rules[index+offset]]=[rules[index+offset],rules[index]];update({...draft,rules});
  }
  async function loadDatasources() { try { const rows = await fetchDatabases(); setSourceOptions(rows.map(row => ({ value: String(row.id ?? row.value), label: row.label }))); } catch { message.error('读取授权数据源失败，请检查会话或权限'); } }
  async function automatic() {
    if (columnsLoading) return;
    if (columnsError) {
      message.warning('字段探查未完成，请重新探查后再推荐');
      return;
    }
    if (sourceFields.length === 0) {
      message.warning('尚未加载来源字段，请检查来源表配置并探查字段');
      return;
    }
    setBusy(true);
    try {
      let items: LocalRecommendation[] = [];
      const sourceByPath = new Map(sourceFields.map((field) => [field.path, field]));
      const targetByPath = new Map(targetFields.map((field) => [field.path, field]));
      try {
        const response = await recommendFieldMapping(
          sourceFields.map((field) => ({ name: field.label?.trim() || field.name, path: field.path, type: field.type })),
          targetFields.map((field) => ({ name: field.label?.trim() || field.name, path: field.path, type: field.type })),
        );
        response.recommendations.forEach((rec, index) => {
          const source = sourceByPath.get(normalizePath(rec.from));
          const target = targetByPath.get(normalizePath(rec.to));
          items.push({
            id: rec.id || `rec-${index}`,
            from: normalizePath(rec.from),
            fromLabel: source?.label,
            to: normalizePath(rec.to),
            toLabel: target?.label,
            reason: rec.reason,
            confidence: rec.confidence,
          });
        });
      } catch {
        const labeledSources = sourceFields.filter((f) => f.label);
        const labeledTargets = targetFields.filter((f) => f.label);
        const srcByLabel = new Map<string, FieldInfo[]>();
        const tgtByLabel = new Map<string, FieldInfo[]>();
        labeledSources.forEach((f) => {
          const key = f.label!.trim();
          const arr = srcByLabel.get(key) ?? [];
          arr.push(f);
          srcByLabel.set(key, arr);
        });
        labeledTargets.forEach((f) => {
          const key = f.label!.trim();
          const arr = tgtByLabel.get(key) ?? [];
          arr.push(f);
          tgtByLabel.set(key, arr);
        });
        let id = 0;
        const confidenceMap: Record<number, number> = { 2: 0.95, 1.5: 0.85, 1: 0.75 };
        for (const [label, srcGroup] of srcByLabel) {
          const tgtGroup = tgtByLabel.get(label);
          if (!tgtGroup || tgtGroup.length === 0) continue;
          if (srcGroup.length === 1 && tgtGroup.length === 1) {
            items.push({
              id: `rec-${id++}`,
              from: srcGroup[0].path,
              fromLabel: srcGroup[0].label,
              to: tgtGroup[0].path,
              toLabel: tgtGroup[0].label,
              reason: '中文名完全匹配',
              confidence: 1.0,
            });
          } else {
            const remainingSrc = [...srcGroup];
            const remainingTgt = [...tgtGroup];
            for (let si = remainingSrc.length - 1; si >= 0; si--) {
              for (let ti = remainingTgt.length - 1; ti >= 0; ti--) {
                if (identifierMatchKey(remainingSrc[si].name) === identifierMatchKey(remainingTgt[ti].name)) {
                  items.push({
                    id: `rec-${id++}`,
                    from: remainingSrc[si].path,
                    fromLabel: remainingSrc[si].label,
                    to: remainingTgt[ti].path,
                    toLabel: remainingTgt[ti].label,
                    reason: remainingSrc[si].name === remainingTgt[ti].name ? '中文名+英文名完全匹配' : '中文名+英文名忽略大小写匹配',
                    confidence: 1.0,
                  });
                  remainingSrc.splice(si, 1);
                  remainingTgt.splice(ti, 1);
                  break;
                }
              }
            }
            while (remainingSrc.length > 0 && remainingTgt.length > 0) {
              let bestSi = 0;
              let bestTi = 0;
              let bestScore = -1;
              for (let si = 0; si < remainingSrc.length; si++) {
                for (let ti = 0; ti < remainingTgt.length; ti++) {
                  const { score } = typeMatchScore(remainingSrc[si].type, remainingTgt[ti].type);
                  if (score > bestScore) {
                    bestScore = score;
                    bestSi = si;
                    bestTi = ti;
                  }
                }
              }
              items.push({
                id: `rec-${id++}`,
                from: remainingSrc[bestSi].path,
                fromLabel: remainingSrc[bestSi].label,
                to: remainingTgt[bestTi].path,
                toLabel: remainingTgt[bestTi].label,
                reason: typeMatchScore(remainingSrc[bestSi].type, remainingTgt[bestTi].type).reason,
                confidence: confidenceMap[bestScore] ?? 0.75,
              });
              remainingSrc.splice(bestSi, 1);
              remainingTgt.splice(bestTi, 1);
            }
          }
        }
      }
      // A direct identifier match must win over a fuzzy/server recommendation.
      // CASE_ID -> case_id is the same business field even when comments are
      // absent or different. Both original spellings are retained for SQL.
      const directNameItems = exactIdentifierRecommendations(sourceFields, targetFields);
      const directTargetKeys = new Set(directNameItems.map((item) => normalizePath(item.to).toLowerCase()));
      items = [
        ...items.filter((item) => !directTargetKeys.has(normalizePath(item.to).toLowerCase())),
        ...directNameItems,
      ];
      const existingTargets = new Set(items.map((item) => normalizePath(item.to).toLowerCase()));
      targetFields.forEach((field) => {
        const system = odsRecommendation(field);
        if (system) {
          const existingIndex=items.findIndex(item=>normalizePath(item.to).toLowerCase()===normalizePath(system.to).toLowerCase());
          if(existingIndex<0)items.push(system);else items[existingIndex]=system;
          existingTargets.add(normalizePath(system.to).toLowerCase());
        }
      });
      setRecommendations(items);
      setRecommendOpen(true);
      if (items.length === 0) message.info('没有发现可推荐的字段治理');
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? e?.message ?? '推荐失败');
    } finally {
      setBusy(false);
    }
  };

  async function normalizeMappings(mappings:MappingRule[]) {
    const compiled=await compileFieldMapping({version:'1.0',mappings},sourceSchema,targetSchema);
    if(!compiled.plan)throw new Error('无法生成治理规则');
    return compiled.plan.executionPlan.rules.map(rule=>({...rule,ruleId:nanoid()}));
  }
  async function applyRecommendations(picked:LocalRecommendation[]){
    const perform=async()=>{setBusy(true);try{const rules=await normalizeMappings(picked.map(item=>item.expression?{to:normalizePath(item.to),expression:item.expression,comment:item.comment}:{from:normalizePath(item.from),to:normalizePath(item.to)}));update({...draft,rules});setRecommendOpen(false);message.success('已应用 '+rules.length+' 条自动匹配');}catch(error:any){message.error(error?.response?.data?.error??error.message);}finally{setBusy(false);}};
    if(draft.rules.length)modal.confirm({title:'覆盖确认',content:'当前已有 '+draft.rules.length+' 条治理规则，应用选中的自动匹配将替换现有规则，是否继续？',getContainer:getNifiOverlayContainer,onOk:perform});else await perform();
  }
  async function applyTemplate(mappings:MappingRule[]){
    setBusy(true);try{
      const selected=mappings.filter(mapping=>!draft.rules.some(rule=>rule.target.name===displayPath(mapping.to)));
      if(!selected.length){message.info('对应目标字段已配置治理规则');setTemplateOpen(false);return;}
      const rules=await normalizeMappings(selected);
      update({...draft,rules:[...draft.rules,...rules]});setTemplateOpen(false);message.success('已添加 '+rules.length+' 条模板规则');
    }catch(error:any){message.error(error?.response?.data?.error??error.message);}finally{setBusy(false);}
  }
  async function applyDsl(){
    setBusy(true);try{
      assertExactJsonNumbers(dslText);let next=JSON.parse(dslText);
      if(next.schemaVersion!=='2.0'){const compiled=await compileFieldMapping(next,sourceSchema,targetSchema);if(!compiled.plan)throw new Error('治理规则格式无效');next=compiled.plan.executionPlan;}
      if(!Array.isArray(next.rules)||!next.rules.every(isEditableRule))throw new Error('治理配置须包含完整规则结构');
      dslDirty.current=false;update({...next,policies:next.policies??{}});message.success('已应用治理配置');
    }catch(error:any){message.error(error?.response?.data?.error??error.message??'配置格式无效');}finally{setBusy(false);}
  }
  async function confirmRuntimeOperation() {
    if (!assetContext?.pipelineId) throw new Error('请先保存并发布当前任务');
    if (runtimeOperation === 'upgrade') {
      const plan = await compileFieldMapping(draft, sourceSchema, targetSchema);
      if (!plan.plan || !assetContext.nodeId) throw new Error('未能确认当前节点的执行计划，请先保存并发布');
      await mappingVersions.upgrade(assetContext.pipelineId, assetContext.nodeId, plan.plan.planHash);
      message.success('运行字段节点已升级，来源 ID 与水位保留');
    } else { await mappingVersions.startPublished(assetContext.pipelineId); message.success('已启动发布版本'); }
  }
  const sourceFields=useMemo(()=>fieldsForPresentation(sourceSchema,columnMeta?.sourceLabels??{},draft.rules.flatMap(sourceNames)),[sourceSchema,columnMeta?.sourceLabels,draft.rules]);
  const targetFields=useMemo(()=>fieldsForPresentation(targetSchema,columnMeta?.targetLabels??{},draft.rules.map(rule=>rule.target.name)),[targetSchema,columnMeta?.targetLabels,draft.rules]);
  const more=[{key:'inspect',type:'group' as const,label:'检查与管理',children:[{key:'assets',label:'规则版本与历史'},{key:'validate',label:'校验规则'},{key:'compile',label:'查看执行计划'},{key:'schema',label:'锁定字段结构',disabled:!assetContext?.sourceRef&&!assetContext?.targetRef}]},{key:'runtime',type:'group' as const,label:'运行操作',children:[{key:'upgrade',label:'升级运行字段节点',disabled:!assetContext?.pipelineId||!assetContext?.nodeId},{key:'start',label:'启动已发布运行版本',disabled:!assetContext?.pipelineId}]}];
  const rows=useMemo(()=>draft.rules.map((rule,index)=>({rule,index,problems:[...(issues?.errors??[]),...(issues?.warnings??[])].filter(issue=>issue.path.startsWith('rules['+index+']')||issue.path.startsWith('mappings['+index+']')||issue.path==='target'&&issue.value===rule.target.name||issue.path==='input'&&issue.value===rule.input.name)})),[draft,issues]);
  const label=(name:string|undefined,labels:Record<string,string>|undefined)=>name&&labels?.[name];
  const fieldCell=(name:string,caption:string|undefined)=><div className="fm-field-label-cell"><Typography.Text ellipsis>{caption||name||'未配置'}</Typography.Text>{caption&&<Typography.Text type="secondary" className="fm-field-identifier">{name}</Typography.Text>}</div>;
  return <div className={'field-mapping-editor field-mapping-editor-v11 fm-single-editor fm-mode-'+mode}>
    <header className="fm-topbar">
      <div className="fm-topbar-left"><Typography.Text strong>{draft.rules.length} 条规则</Typography.Text><span className="fm-summary">源字段 {sourceSchema.length} 个 · 目标字段 {targetSchema.length} 个</span></div>
      <div className="fm-topbar-right">
        <Space.Compact size="small"><Button type={mode==='list'?'primary':'default'} icon={<UnorderedListOutlined/>} onClick={()=>setMode('list')}>列表</Button><Button type={mode==='visual'?'primary':'default'} icon={<SwapOutlined/>} onClick={()=>setMode('visual')}>可视化</Button><Button type={mode==='dsl'?'primary':'default'} icon={<CodeOutlined/>} onClick={()=>setMode('dsl')}>高级</Button></Space.Compact>
        <Button size="small" icon={<BulbOutlined/>} loading={busy} disabled={!sourceSchema.length||!targetSchema.length} onClick={automatic}>自动匹配</Button>
        <Button size="small" icon={<AppstoreOutlined/>} onClick={()=>setTemplateOpen(true)}>模板</Button>
        <Button size="small" icon={<EyeOutlined/>} onClick={()=>setPreviewOpen(true)}>预览</Button>
        <Tooltip title="流程设置"><Button size="small" aria-label="流程设置" icon={<SettingOutlined/>} onClick={()=>setFlowSettingsOpen(true)}/></Tooltip>
        <Button size="small" type="primary" icon={<PlusOutlined/>} onClick={()=>add()}>添加规则</Button>
        <Dropdown trigger={['click']} getPopupContainer={getNifiOverlayContainer} menu={{items:more,onClick:({key})=>{if(key==='validate'||key==='compile'||key==='schema')setInspection(key);if(key==='assets')setAssetsOpen(true);if(key==='upgrade'||key==='start')setRuntimeOperation(key);}}}><Button size="small" aria-label="更多治理操作" icon={<MoreOutlined/>}/></Dropdown>
      </div>
    </header>
    {(columnsLoading||columnsError)&&<Alert type={columnsError?'warning':'info'} showIcon message={columnsError||'正在读取上下游字段'} action={onReloadColumns&&<Button size="small" loading={columnsLoading} onClick={onReloadColumns}>重新读取</Button>}/>}
    {capabilityError&&<Alert message={capabilityError} type="warning" showIcon/>}
    {validationError&&<Alert type="warning" showIcon message="自动校验未完成，草稿仍可编辑" description={validationError} action={<Button size="small" onClick={()=>setValidationRetry(value=>value+1)}>重试校验</Button>}/>}
    {issues?.errors.length?<div className="fm-validation-strip is-error" role="alert">{issues.errors.slice(0,3).map(e=>e.message).join('；')}</div>:null}
    {mode==='list'?<section className="fm-list-view">
      {!!selected.length&&<div className="fm-selection-bar"><Typography.Text type="secondary">已选 {selected.length} 项</Typography.Text>{[true,false].map(enabled=><Button key={String(enabled)} size="small" onClick={()=>update({...draft,rules:draft.rules.map(r=>selected.includes(r.ruleId)?{...r,enabled}:r)})}>{enabled?'批量启用':'批量停用'}</Button>)}</div>}
      <MappingDraftTable sequenceColumnKey='sequence' fields={{sourceName:{kind:'text',value:row=>inputSummary(row.rule)},targetName:{kind:'text',value:row=>row.rule.target.name}}} className="fm-list-table" size="small" rowKey={row=>row.rule.ruleId} dataSource={rows} rowSelection={{selectedRowKeys:selected,onChange:setSelected}} locale={{emptyText:<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无治理规则，可点击上方“添加规则”添加"/>}} columns={[
        {title:'序号',key:'sequence',width:56},
        {title:'来源表字段 / 取值',key:'sourceName',ellipsis:true,render:(_,row)=>fieldCell(concreteInputLabel(row.rule),label(row.rule.input.name,columnMeta?.sourceLabels))},
        {title:'目标表字段',key:'targetName',ellipsis:true,render:(_,row)=>fieldCell(row.rule.target.name,label(row.rule.target.name,columnMeta?.targetLabels))},
        {title:'治理规则',width:190,render:(_,row)=><Button className="fm-config-link" type="link" size="small" title={concreteRuleLabel(row.rule)} aria-label={'编辑规则 '+row.rule.target.name+'：'+concreteRuleLabel(row.rule)} onClick={()=>openRule(row.rule)}>{concreteRuleLabel(row.rule)}</Button>},
        {title:'状态',width:90,render:(_,row)=><Checkbox checked={row.rule.enabled!==false} onChange={e=>update({...draft,rules:draft.rules.map(r=>r.ruleId===row.rule.ruleId?{...r,enabled:e.target.checked}:r)})}>启用</Checkbox>},
        {title:'操作',width:104,render:(_,row)=><Space size={0}><Tooltip title="上移"><Button aria-label={'上移规则 '+row.rule.target.name} size="small" type="text" icon={<UpOutlined/>} disabled={!row.index} onClick={()=>moveRule(row.rule.ruleId,-1)}/></Tooltip><Tooltip title="下移"><Button aria-label={'下移规则 '+row.rule.target.name} size="small" type="text" icon={<DownOutlined/>} disabled={row.index===draft.rules.length-1} onClick={()=>moveRule(row.rule.ruleId,1)}/></Tooltip><Tooltip title="删除"><Button size="small" type="text" danger aria-label={'删除规则 '+row.rule.target.name} icon={<DeleteOutlined/>} onClick={()=>update({...draft,rules:draft.rules.filter(r=>r.ruleId!==row.rule.ruleId)})}/></Tooltip></Space>}
      ]}/>
    </section>:mode==='visual'?<FieldMappingVisual draft={draft} sourceMeta={sourceSchema} targetMeta={targetSchema} sourceLabels={columnMeta?.sourceLabels??{}} targetLabels={columnMeta?.targetLabels??{}} onOpen={openRule} onChange={updateRule} onCreate={createMapping} onAdd={add} onDelete={id=>update({...draft,rules:draft.rules.filter(rule=>rule.ruleId!==id)})} onMove={moveRule}/>
      :<div className="field-mapping-dsl-view"><CodeMirror className="fm-dsl-editor" value={dslText} height="100%" extensions={[json()]} onChange={text=>{dslDirty.current=true;setDslText(text);}} basicSetup={{lineNumbers:true,highlightActiveLine:true}}/><div className="fm-dsl-actions"><Button size="small" onClick={()=>{dslDirty.current=false;setDslText(JSON.stringify(draft,null,2));}}>重置</Button><Button size="small" type="primary" loading={busy} onClick={applyDsl}>应用</Button></div></div>}
    {flowSettingsOpen&&<MappingFlowSettings draft={draft} onApply={update} onClose={()=>setFlowSettingsOpen(false)}/>}
    {inspection&&<MappingInspectionDialog kind={inspection} draft={draft} sourceSchema={sourceSchema} targetSchema={targetSchema} sourceRef={assetContext?.sourceRef} targetRef={assetContext?.targetRef} onClose={()=>setInspection(null)} onApplySchema={schema=>{update({...draft,schemaContract:schema});setInspection(null);message.success('字段结构已附到当前草稿，保存画布后保留');}}/>}
    {runtimeOperation&&<MappingRuntimeDialog operation={runtimeOperation} onClose={()=>setRuntimeOperation(null)} onConfirm={confirmRuntimeOperation}/>}
    {editing&&<MappingRuleDialog initial={editing} creating={!draft.rules.some(r=>r.ruleId===editing.ruleId)} draft={draft} fields={sourceSchema} targets={targetSchema} sourceLabels={columnMeta?.sourceLabels??{}} targetLabels={columnMeta?.targetLabels??{}} functionSchemas={functionSchemas} datasources={sourceOptions} loadDatasources={loadDatasources} onApply={saveRule} onClose={()=>setEditing(null)}/>}
    <GovernancePreviewDialog open={previewOpen} onClose={()=>setPreviewOpen(false)} draft={draft} sourceRef={assetContext?.sourceRef} sourceSchema={sourceSchema} targetSchema={targetSchema} sourceLabels={columnMeta?.sourceLabels} targetLabels={columnMeta?.targetLabels}/>
    <RecommendModal open={recommendOpen} recommendations={recommendations} applying={busy} onCancel={()=>setRecommendOpen(false)} onApply={picked=>void applyRecommendations(picked)}/>
    <TemplateModal open={templateOpen} sourceFields={sourceFields} targetFields={targetFields} onCancel={()=>setTemplateOpen(false)} onApply={mappings=>void applyTemplate(mappings)}/>
    <MappingAssetsPanel open={assetsOpen} onClose={()=>setAssetsOpen(false)} draft={draft} onChange={update} pipelineId={assetContext?.pipelineId} nodeId={assetContext?.nodeId}/>
  </div>;
}
function odsRecommendation(field: FieldInfo): LocalRecommendation | null {
  const target = field.name.toUpperCase();
  if (target === 'ODS_UUID') {
    return {
      id: 'ods-uuid',
      from: '',
      to: field.path,
      toLabel: field.label,
      expression: ODS_UUID_EXPRESSION,
      comment: '逐记录生成32位UUID，重试保持相同行身份',
      reason: 'ODS系统字段自动生成',
      confidence: 1,
    };
  }
  if (target === 'ODS_RKSJ') {
    return {
      id: 'ods-rksj',
      from: '',
      to: field.path,
      toLabel: field.label,
      expression: 'LOCALTIMESTAMP',
      comment: 'NiFi写入当前入库时间',
      reason: 'ODS系统字段自动生成',
      confidence: 1,
    };
  }
  if (target === 'ODS_GXSJ') {
    return {
      id: 'ods-gxsj',
      from: '',
      to: field.path,
      toLabel: field.label,
      expression: 'LOCALTIMESTAMP',
      comment: 'NiFi写入当前更新时间',
      reason: 'ODS系统字段自动生成',
      confidence: 1,
    };
  }
  return null;
}

const TYPE_COMPAT_GROUPS: { types: Set<FieldType> }[] = [
  { types: new Set<FieldType>(['INT', 'DECIMAL']) },
  { types: new Set<FieldType>(['DATETIME', 'DATE']) },
];

function typeMatchScore(a: FieldType, b: FieldType): { score: number; reason: string } {
  if (a === b) return { score: 2, reason: '中文名+类型匹配（贪心配对）' };
  for (const grp of TYPE_COMPAT_GROUPS) {
    if (grp.types.has(a) && grp.types.has(b)) return { score: 1.5, reason: '中文名+兼容类型配对' };
  }
  return { score: 1, reason: '中文名匹配（贪心配对）' };
}

function exactIdentifierRecommendations(
  sourceFields: FieldInfo[],
  targetFields: FieldInfo[],
): LocalRecommendation[] {
  return findUniqueCaseInsensitiveFieldMatches(sourceFields, targetFields).map(({ source, target, differsOnlyByCase }) => ({
    id: `exact-name-${source.path}-${target.path}`,
    from: source.path,
    fromLabel: source.label,
    to: target.path,
    toLabel: target.label,
    reason: differsOnlyByCase ? '英文名忽略大小写匹配' : '英文名完全匹配',
    confidence: 1,
  }));
}

