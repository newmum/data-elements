import { useEffect, useLayoutEffect, useMemo, useRef, useState } from 'react';
import { Alert, App, AutoComplete, Button, Checkbox, Collapse, Drawer, Dropdown, Empty, Input, InputNumber, Skeleton, Space, Tag, Typography } from 'antd';
import { AppstoreOutlined, BulbOutlined, CodeOutlined, DeleteOutlined, EyeOutlined, MoreOutlined, PlusOutlined, SwapOutlined, UnorderedListOutlined } from '@ant-design/icons';
import { nanoid } from 'nanoid';
import { compileFieldMapping, fieldMappingCapabilities, mappingVersions, previewFieldMapping, recommendFieldMapping, validateFieldMapping, type FieldMeta, type FunctionParameter, type MappingIssue, type MappingTableRef, type PreviewResponse, type UnifiedMappingDraft, type UnifiedMappingRule, type MappingRule } from '../api/fieldMapping';
import { fetchDatabases } from '../api/dataassets';
import { useSessionRevision } from '../api/bridgeSession';
import { DraftTable } from '../../components/DraftTable';
import { getNifiOverlayContainer } from './overlayContainer';
import MappingAssetsPanel from './MappingAssetsPanel';
import FieldMappingVisual from './FieldMappingVisual';
import {RecommendModal,TemplateModal} from './FieldMappingDialogs';
import {displayPath,fieldName,fieldsForPresentation,normalizePath,sourceNames,type FieldInfo,type FieldType,type LocalRecommendation} from './fieldMappingPresentation';
import {findUniqueCaseInsensitiveFieldMatches,identifierMatchKey} from '../utils/fieldNameMatching';
import CodeMirror from '@uiw/react-codemirror';
import {json} from '@codemirror/lang-json';
import MappingRuleDialog from './MappingRuleDialog';
import { Choice, JsonValueEditor, type DatasourceChoice } from './UnifiedRuleForms';
import { assertExactJsonNumbers, createRule, emptyUnifiedDraft, inputSummary, logicalFieldType, includeOdsSystemMappings, isEditableRule, OPERATOR_LABELS, primaryRuleMode, stepSummary } from './unifiedRuleModel';
import './unifiedMapping.css';

interface Props {
  value:unknown; onChange:(value:string)=>void;
  columnMeta?: {sourceLabels:Record<string,string>;targetLabels:Record<string,string>;sourceSchema?:FieldMeta[];targetSchema?:FieldMeta[]};
  datasources?:DatasourceChoice[];
  columnsLoading?:boolean; columnsError?:string; onReloadColumns?:()=>void;
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
      if(spec.schemaVersion==='2.0'&&(!Array.isArray(spec.rules)||spec.rules.some((rule:unknown)=>!isEditableRule(rule))))throw new Error('映射规则结构不完整，请检查配置内容');
      return {spec,error:''};
    }catch(cause:any){return {spec:null,error:cause.message};}
  },[props.value]);
  const schemaSignature=JSON.stringify([props.columnMeta?.sourceSchema??[],props.columnMeta?.targetSchema??[]]);
  const [loaded,setLoaded]=useState<{source:unknown;schema:string;draft:UnifiedMappingDraft}|null>(null);
  const [error,setError]=useState(''),[retry,setRetry]=useState(0);
  useEffect(()=>{
    if(!parsed.spec||parsed.spec.schemaVersion==='2.0'||props.columnsLoading)return;
    let active=true;setError('');
    compileFieldMapping(parsed.spec,props.columnMeta?.sourceSchema??[],props.columnMeta?.targetSchema??[])
      .then(result=>{if(!result.plan)throw new Error('未能读取映射规则，请重试');if(active)setLoaded({source:props.value,schema:schemaSignature,draft:{...result.plan.executionPlan,executionEngine:'JAVA_RECORD'}});})
      .catch(cause=>{if(active)setError(cause?.response?.data?.error??cause.message??'读取映射规则失败');});
    return ()=>{active=false;};
  },[props.value,retry,props.columnsLoading,schemaSignature]);
  const problem=parsed.error||(parsed.spec?.schemaVersion==='2.0'?'':error);
  if(problem)return <Alert type="error" showIcon message={problem} action={<Button onClick={()=>setRetry(retry+1)}>重试</Button>}/>;
  // Keep an open form mounted while metadata is refreshed in the background.
  const draft=parsed.spec?.schemaVersion==='2.0'?parsed.spec:loaded&&loaded.source===props.value?loaded.draft:null;
  if(!draft)return <Skeleton active paragraph={{rows:6}}/>;
  return <FieldMappingEditorContent {...props} value={draft}/>;
}

function FieldMappingEditorContent({value,onChange,columnMeta,datasources=EMPTY_DATASOURCES,columnsLoading,columnsError,onReloadColumns,assetContext}:Omit<Props,'value'>&{value:UnifiedMappingDraft}) {
  const {message,modal}=App.useApp();
  const sourceSchema=columnMeta?.sourceSchema??EMPTY_SCHEMA,targetSchema=columnMeta?.targetSchema??EMPTY_SCHEMA;
  const sessionRevision=useSessionRevision();
  const [draft,setDraft]=useState(value),[selected,setSelected]=useState<React.Key[]>([]);
  const [editing,setEditing]=useState<UnifiedMappingRule|null>(null);
  const [mode,setMode]=useState<'list'|'visual'|'dsl'>('list'),[dslText,setDslText]=useState(()=>JSON.stringify(value,null,2));
  const dslDirty=useRef(false);
  const [functionSchemas,setFunctionSchemas]=useState<Record<string,FunctionParameter[]>>({}),[sourceOptions,setSourceOptions]=useState(datasources);
  const [assetsOpen,setAssetsOpen]=useState(false),[previewOpen,setPreviewOpen]=useState(false);
  const [recommendOpen,setRecommendOpen]=useState(false),[recommendations,setRecommendations]=useState<LocalRecommendation[]>([]),[templateOpen,setTemplateOpen]=useState(false);
  const [sampleLimit,setSampleLimit]=useState(20),[sampleText,setSampleText]=useState('[\n  {}\n]');
  const [preview,setPreview]=useState<PreviewResponse|null>(null),[busy,setBusy]=useState(false),[capabilityError,setCapabilityError]=useState('');
  const [issues,setIssues]=useState<{errors:MappingIssue[];warnings:MappingIssue[]}|null>(null);
  const listRef=useRef<HTMLElement>(null),[scrollY,setScrollY]=useState(240);
  useEffect(()=>{setDraft(value);setSelected(keys=>keys.filter(key=>value.rules.some(r=>r.ruleId===key)));if(!dslDirty.current)setDslText(JSON.stringify(value,null,2));},[value]);
  useEffect(()=>{if(datasources.length)setSourceOptions(datasources);},[datasources]);
  useEffect(()=>setSourceOptions(datasources),[sessionRevision]);
  useEffect(()=>{
    let active=true;
    fieldMappingCapabilities().then(result=>{if(active)setFunctionSchemas(Object.fromEntries(result.functions.filter(f=>f.previewSupported&&f.runtimeSupported).map(f=>[f.name,f.parameterSchema??[]])));}).catch(()=>{if(active)setCapabilityError('函数目录暂不可用，已有参数仍保留，请稍后重试。');});
    return ()=>{active=false;};
  },[]);
  useEffect(()=>{
    let active=true;setIssues(null);
    const timer=setTimeout(()=>validateFieldMapping(draft,sourceSchema,targetSchema).then(result=>{if(active)setIssues({errors:result.errors??[],warnings:result.warnings??[]});}).catch(()=>{if(active)setIssues({errors:[{path:'spec',message:'规则校验暂不可用',severity:'ERROR',code:'VALIDATE_FAILED'}],warnings:[]});}),350);
    return ()=>{active=false;clearTimeout(timer);};
  },[draft,sourceSchema,targetSchema]);
  useLayoutEffect(()=>{
    const view=listRef.current;if(!view)return;
    const sync=()=>setScrollY(Math.max(140,Math.floor(view.clientHeight-82)));
    const observer=new ResizeObserver(sync);observer.observe(view);sync();return ()=>observer.disconnect();
  },[mode]);
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
  async function action(kind: 'validate' | 'compile' | 'preview') {
    setBusy(true);
    try {
      if (kind === 'validate') { const result = await validateFieldMapping(draft, sourceSchema, targetSchema); if (!result.valid) modal.error({ title: '规则校验未通过', getContainer: getNifiOverlayContainer, content: result.errors.map(e => <div key={e.path + e.code}>{e.path}：{e.message}</div>) }); else if (result.warnings.length) modal.warning({ title: '规则有效，Schema 检查有提示', getContainer: getNifiOverlayContainer, content: result.warnings.map(e => <div key={e.path + e.code}>{e.message}</div>) }); else message.success('规则及 Schema 校验通过'); }
      if (kind === 'compile') { const result = await compileFieldMapping(draft, sourceSchema, targetSchema); modal.info({ title: '统一执行计划', width: 800, getContainer: getNifiOverlayContainer, content: <pre style={{ maxHeight: 520, overflow: 'auto' }}>{JSON.stringify(result.plan, null, 2)}</pre> }); }
      if (kind === 'preview') { assertExactJsonNumbers(sampleText); const samples: unknown = JSON.parse(sampleText); if (!Array.isArray(samples) || samples.length > 100 || samples.some(row => !row || typeof row !== 'object' || Array.isArray(row))) throw new Error('样本须为最多100个对象的 JSON 数组'); setPreview(await previewFieldMapping(draft, samples)); }
    } catch (error: any) { message.error(error?.response?.data?.error ?? error?.message ?? '操作失败'); } finally { setBusy(false); }
  }
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
        if (system && !existingTargets.has(normalizePath(system.to).toLowerCase())) {
          items.push(system);
          existingTargets.add(normalizePath(system.to).toLowerCase());
        }
      });
      setRecommendations(items);
      setRecommendOpen(true);
      if (items.length === 0) message.info('没有发现可推荐的字段映射');
    } catch (e: any) {
      message.error(e?.response?.data?.error ?? e?.message ?? '推荐失败');
    } finally {
      setBusy(false);
    }
  };

  async function normalizeMappings(mappings:MappingRule[]) {
    const compiled=await compileFieldMapping({version:'1.0',mappings},sourceSchema,targetSchema);
    if(!compiled.plan)throw new Error('无法生成映射规则');
    return compiled.plan.executionPlan.rules.map(rule=>({...rule,ruleId:nanoid()}));
  }
  async function applyRecommendations(picked:LocalRecommendation[]){
    const perform=async()=>{setBusy(true);try{const rules=await normalizeMappings(picked.map(item=>item.expression?{to:normalizePath(item.to),expression:item.expression,comment:item.comment}:{from:normalizePath(item.from),to:normalizePath(item.to)}));update({...draft,rules});setRecommendOpen(false);message.success('已应用 '+rules.length+' 条自动映射');}catch(error:any){message.error(error?.response?.data?.error??error.message);}finally{setBusy(false);}};
    if(draft.rules.length)modal.confirm({title:'覆盖确认',content:'当前已有 '+draft.rules.length+' 条映射规则，应用选中的自动映射将替换现有规则，是否继续？',getContainer:getNifiOverlayContainer,onOk:perform});else await perform();
  }
  async function applyTemplate(mappings:MappingRule[]){
    setBusy(true);try{
      const selected=mappings.filter(mapping=>!draft.rules.some(rule=>rule.target.name===displayPath(mapping.to)));
      if(!selected.length){message.info('对应目标字段已配置映射');setTemplateOpen(false);return;}
      const rules=await normalizeMappings(selected);
      update({...draft,rules:[...draft.rules,...rules]});setTemplateOpen(false);message.success('已添加 '+rules.length+' 条模板映射');
    }catch(error:any){message.error(error?.response?.data?.error??error.message);}finally{setBusy(false);}
  }
  async function applyDsl(){
    setBusy(true);try{
      assertExactJsonNumbers(dslText);let next=JSON.parse(dslText);
      if(next.schemaVersion!=='2.0'){const compiled=await compileFieldMapping(next,sourceSchema,targetSchema);if(!compiled.plan)throw new Error('映射规则格式无效');next=compiled.plan.executionPlan;}
      if(!Array.isArray(next.rules)||!next.rules.every(isEditableRule))throw new Error('映射配置须包含完整规则结构');
      dslDirty.current=false;update({...next,policies:next.policies??{}});message.success('已应用映射配置');
    }catch(error:any){message.error(error?.response?.data?.error??error.message??'配置格式无效');}finally{setBusy(false);}
  }
  async function lockSchema() { setBusy(true); try { update({ ...draft, schemaContract: await mappingVersions.schema(assetContext?.sourceRef, assetContext?.targetRef) }); message.success('已锁定物理 Schema'); } catch (error: any) { message.error(error?.response?.data?.error ?? error.message); } finally { setBusy(false); } }
  async function sourcePreview() { if (!assetContext?.sourceRef) return; setBusy(true); try { setPreview(await mappingVersions.sample(draft, assetContext.sourceRef, sampleLimit)); message.success('已执行规则，回传样本已脱敏'); } catch (error: any) { message.error(error?.response?.data?.error ?? error.message); } finally { setBusy(false); } }
  function upgradeRuntime() { modal.confirm({ title: '升级已发布任务的字段节点', getContainer: getNifiOverlayContainer, content: '请先保存画布。此次仅替换字段节点，保留来源与目标组件 ID、增量水位及运行状态；非空队列会阻止升级。', onOk: async () => { setBusy(true); try { const plan = await compileFieldMapping(draft, sourceSchema, targetSchema); if (!plan.plan || !assetContext?.pipelineId || !assetContext.nodeId) throw new Error('请保存并发布任务后使用运行升级'); await mappingVersions.upgrade(assetContext.pipelineId, assetContext.nodeId, plan.plan.planHash); message.success('运行字段节点已升级，来源 ID 与水位保留'); } catch (error: any) { message.error(error?.response?.data?.error ?? error.message); throw error; } finally { setBusy(false); } } }); }
  function startPublished() { modal.confirm({ title: '启动已发布运行版本', getContainer: getNifiOverlayContainer, content: '启动当前 NiFi 运行图并保留来源水位。画布中未发布修改继续保留为草稿。', onOk: async () => { try { await mappingVersions.startPublished(assetContext!.pipelineId!); message.success('已启动发布版本'); } catch (error: any) { message.error(error?.response?.data?.error ?? error.message); throw error; } } }); }
  const sourceFields=useMemo(()=>fieldsForPresentation(sourceSchema,columnMeta?.sourceLabels??{},draft.rules.flatMap(sourceNames)),[sourceSchema,columnMeta?.sourceLabels,draft.rules]);
  const targetFields=useMemo(()=>fieldsForPresentation(targetSchema,columnMeta?.targetLabels??{},draft.rules.map(rule=>rule.target.name)),[targetSchema,columnMeta?.targetLabels,draft.rules]);
  const patchPolicy=(key:string,value:unknown)=>update({...draft,policies:{...draft.policies,[key]:value}});
  const more=[{key:'assets',label:'规则版本与历史'},{key:'validate',label:'校验规则'},{key:'compile',label:'查看执行计划'},{key:'schema',label:'锁定物理 Schema',disabled:!assetContext?.sourceRef&&!assetContext?.targetRef},{key:'upgrade',label:'仅升级运行字段节点',disabled:!assetContext?.pipelineId||!assetContext?.nodeId},{key:'start',label:'启动已发布运行版本',disabled:!assetContext?.pipelineId}];
  const rows=useMemo(()=>draft.rules.map((rule,index)=>({rule,index,problems:[...(issues?.errors??[]),...(issues?.warnings??[])].filter(issue=>issue.path.startsWith('rules['+index+']')||issue.path.startsWith('mappings['+index+']')||issue.path==='target'&&issue.value===rule.target.name||issue.path==='input'&&issue.value===rule.input.name)})),[draft,issues]);
  const label=(name:string|undefined,labels:Record<string,string>|undefined)=>name&&labels?.[name];
  const summary=(rule:UnifiedMappingRule)=>rule.when!=null?'条件分支':rule.steps.filter(s=>!s.uiRole).map(stepSummary).join(' → ')||({LITERAL:'常量赋值',EXPRESSION:'表达式',SYSTEM:'系统值'}[rule.input.kind]??'原值');
  const kind=(rule:UnifiedMappingRule)=>({CONSTANT:'常量赋值',EXPRESSION:'表达式',SYSTEM:'系统值',CONDITIONAL:'条件映射'}[primaryRuleMode(rule)]??OPERATOR_LABELS[primaryRuleMode(rule)]??'字段映射');
  const fieldCell=(name:string,caption:string|undefined)=><div className="fm-field-label-cell"><Typography.Text ellipsis>{caption||name||'未配置'}</Typography.Text>{caption&&<Typography.Text type="secondary" className="fm-field-identifier">{name}</Typography.Text>}</div>;
  return <div className="field-mapping-editor field-mapping-editor-v11 fm-single-editor">
    <header className="fm-topbar">
      <div className="fm-topbar-left"><Typography.Text strong>字段映射</Typography.Text><span className="fm-summary">{draft.rules.length} 条映射 · 源字段 {sourceSchema.length} 个 · 目标字段 {targetSchema.length} 个</span></div>
      <div className="fm-topbar-right">
        <Space.Compact size="small"><Button type={mode==='list'?'primary':'default'} icon={<UnorderedListOutlined/>} onClick={()=>setMode('list')}>列表</Button><Button type={mode==='visual'?'primary':'default'} icon={<SwapOutlined/>} onClick={()=>setMode('visual')}>可视化</Button><Button type={mode==='dsl'?'primary':'default'} icon={<CodeOutlined/>} onClick={()=>setMode('dsl')}>高级</Button></Space.Compact>
        <Button size="small" icon={<BulbOutlined/>} loading={busy} disabled={!sourceSchema.length||!targetSchema.length} onClick={automatic}>自动映射</Button>
        <Button size="small" icon={<AppstoreOutlined/>} onClick={()=>setTemplateOpen(true)}>模板</Button>
        <Button size="small" icon={<EyeOutlined/>} onClick={()=>{setPreview(null);setPreviewOpen(true);}}>预览</Button>
        <Button size="small" type="primary" icon={<PlusOutlined/>} onClick={()=>add()}>映射规则</Button>
        <Dropdown menu={{items:more,onClick:({key})=>{if(key==='validate'||key==='compile')void action(key);if(key==='assets')setAssetsOpen(true);if(key==='schema')void lockSchema();if(key==='upgrade')upgradeRuntime();if(key==='start')startPublished();}}}><Button size="small" aria-label="更多映射操作" icon={<MoreOutlined/>}/></Dropdown>
      </div>
    </header>
    {(columnsLoading||columnsError)&&<Alert type={columnsError?'warning':'info'} showIcon message={columnsError||'正在读取上下游字段'} action={onReloadColumns&&<Button size="small" loading={columnsLoading} onClick={onReloadColumns}>重新读取</Button>}/>}
    {capabilityError&&<Alert message={capabilityError} type="warning" showIcon/>}
    {issues?.errors.length?<div className="fm-validation-strip is-error" role="alert">{issues.errors.slice(0,3).map(e=>e.message).join('；')}</div>:null}
    {mode==='list'?<section className="fm-list-view" ref={listRef}>
      {!!selected.length&&<div className="fm-selection-bar"><Typography.Text type="secondary">已选 {selected.length} 项</Typography.Text>{[true,false].map(enabled=><Button key={String(enabled)} size="small" onClick={()=>update({...draft,rules:draft.rules.map(r=>selected.includes(r.ruleId)?{...r,enabled}:r)})}>{enabled?'批量启用':'批量停用'}</Button>)}</div>}
      <DraftTable defaultOrder='input' sequenceColumnKey='sequence' fields={{sourceName:{kind:'text',value:row=>inputSummary(row.rule)},targetName:{kind:'text',value:row=>row.rule.target.name}}} className="fm-list-table" size="small" rowKey={row=>row.rule.ruleId} dataSource={rows} pagination={rows.length>30?{defaultPageSize:30,showSizeChanger:true,showTotal:total=>'共 '+total+' 条映射'}:false} scroll={{x:760,y:scrollY}} rowSelection={{selectedRowKeys:selected,onChange:setSelected}} locale={{emptyText:<Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无映射规则，可点击上方“映射规则”添加"/>}} columns={[
        {title:'序号',key:'sequence',width:56},
        {title:'来源表字段',key:'sourceName',ellipsis:true,render:(_,row)=>fieldCell(inputSummary(row.rule),label(row.rule.input.name,columnMeta?.sourceLabels))},
        {title:'目标表字段',key:'targetName',ellipsis:true,render:(_,row)=>fieldCell(row.rule.target.name,label(row.rule.target.name,columnMeta?.targetLabels))},
        {title:'转换 / 配置',width:175,render:(_,row)=><Button className="fm-config-link" type="link" size="small" title="配置映射规则" onClick={()=>openRule(row.rule)}>{summary(row.rule)} · 配置</Button>},
        {title:'状态',width:90,render:(_,row)=><Checkbox checked={row.rule.enabled!==false} onChange={e=>update({...draft,rules:draft.rules.map(r=>r.ruleId===row.rule.ruleId?{...r,enabled:e.target.checked}:r)})}>启用</Checkbox>},
        {title:'操作',width:132,render:(_,row)=><Space size={0}><Button size="small" type="text" disabled={!row.index} onClick={()=>moveRule(row.rule.ruleId,-1)}>上移</Button><Button size="small" type="text" disabled={row.index===draft.rules.length-1} onClick={()=>moveRule(row.rule.ruleId,1)}>下移</Button><Button size="small" type="text" danger aria-label={'删除映射 '+row.rule.target.name} icon={<DeleteOutlined/>} onClick={()=>update({...draft,rules:draft.rules.filter(r=>r.ruleId!==row.rule.ruleId)})}/></Space>}
      ]}/>
    </section>:mode==='visual'?<FieldMappingVisual draft={draft} sourceMeta={sourceSchema} targetMeta={targetSchema} sourceLabels={columnMeta?.sourceLabels??{}} targetLabels={columnMeta?.targetLabels??{}} onOpen={openRule} onChange={updateRule} onCreate={createMapping} onAdd={add} onDelete={id=>update({...draft,rules:draft.rules.filter(rule=>rule.ruleId!==id)})} onMove={moveRule}/>
      :<div className="field-mapping-dsl-view"><CodeMirror className="fm-dsl-editor" value={dslText} height="100%" extensions={[json()]} onChange={text=>{dslDirty.current=true;setDslText(text);}} basicSetup={{lineNumbers:true,highlightActiveLine:true}}/><div className="fm-dsl-actions"><Button size="small" onClick={()=>{dslDirty.current=false;setDslText(JSON.stringify(draft,null,2));}}>重置</Button><Button size="small" type="primary" loading={busy} onClick={applyDsl}>应用</Button></div></div>}
    <Collapse className="fm-flow-options" size="small" items={[{key:'policies',label:'流程设置',children:<div className="unified-form-grid">
      <Choice label="记录失败时" value={draft.policies.recordErrorMode??'FAIL_FLOWFILE'} choices={{FAIL_FLOWFILE:'整批失败并保留原输入',ROUTE_INVALID:'成功记录继续，错误记录隔离'}} onChange={value=>patchPolicy('recordErrorMode',value)}/>
      <Choice label="源字段不存在时" value={draft.policies.onMissingSource??'NULL'} choices={{NULL:'作为 NULL 进入规则',FAIL:'记录报错'}} onChange={value=>patchPolicy('onMissingSource',value)}/>
      <div className="unified-form-field"><Typography.Text>时区</Typography.Text><AutoComplete aria-label="流程时区" value={String(draft.policies.zoneId??'Asia/Shanghai')} options={['Asia/Shanghai','UTC','Asia/Hong_Kong'].map(value=>({value}))} onChange={value=>patchPolicy('zoneId',value)}/></div>
      <div className="unified-form-field"><Typography.Text>语言地区</Typography.Text><Input value={String(draft.policies.locale??'zh-CN')} onChange={e=>patchPolicy('locale',e.target.value)}/></div>
      <Checkbox checked={draft.policies.caseSensitive!==false} onChange={e=>patchPolicy('caseSensitive',e.target.checked)}>字段名区分大小写</Checkbox>
      <Checkbox checked={!!draft.policies.allowUnmappedPassthrough} onChange={e=>patchPolicy('allowUnmappedPassthrough',e.target.checked)}>未映射字段直通</Checkbox>
      {!!draft.policies.allowUnmappedPassthrough&&<div className="unified-form-field"><Typography.Text>直通时排除字段</Typography.Text><AutoComplete value={Array.isArray(draft.exclude)?draft.exclude.join(','):''} onChange={text=>update({...draft,exclude:text.split(',').map(x=>x.trim()).filter(Boolean)})} placeholder="字段名用英文逗号分隔"/></div>}
    </div>}]}/>
    {editing&&<MappingRuleDialog initial={editing} creating={!draft.rules.some(r=>r.ruleId===editing.ruleId)} draft={draft} fields={sourceSchema} targets={targetSchema} sourceLabels={columnMeta?.sourceLabels??{}} targetLabels={columnMeta?.targetLabels??{}} functionSchemas={functionSchemas} datasources={sourceOptions} loadDatasources={loadDatasources} onApply={saveRule} onClose={()=>setEditing(null)}/>}
    <Drawer title="映射预览" width={900} open={previewOpen} onClose={() => setPreviewOpen(false)} getContainer={getNifiOverlayContainer}><Alert type="info" message="最多100行样本。预览使用与 NiFi 相同的内核；来源取样在服务端执行并脱敏返回。" />{assetContext?.sourceRef && <Space style={{ margin: '12px 0' }}><InputNumber min={1} max={100} value={sampleLimit} onChange={limit => setSampleLimit(limit ?? 20)} /><Button loading={busy} onClick={sourcePreview}>来源取样并预览</Button></Space>}<Input.TextArea aria-label="手工样本 JSON" rows={8} value={sampleText} onChange={e => setSampleText(e.target.value)} /><Button type="primary" loading={busy} onClick={() => action('preview')} style={{ margin: '12px 0' }}>执行手工样本预览</Button>{preview && <><Alert type={preview.rowResults.some(r => !r.success) ? 'warning' : 'success'} message={`成功 ${preview.resultRows.length} 行，错误 ${preview.rowResults.filter(r => !r.success).length} 行，字典 SQL ${preview.stats.dictionarySqlCount ?? 0} 次`} />{preview.rowResults.filter(r => !r.success).map(r => <Alert key={r.rowIndex} type="error" message={`第${r.rowIndex + 1}行：${r.errors.join('；')}`} />)}<pre style={{ overflow: 'auto' }}>{JSON.stringify(preview.resultRows, null, 2)}</pre></>}</Drawer>
    <RecommendModal open={recommendOpen} recommendations={recommendations} onCancel={()=>setRecommendOpen(false)} onApply={picked=>void applyRecommendations(picked)}/>
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

