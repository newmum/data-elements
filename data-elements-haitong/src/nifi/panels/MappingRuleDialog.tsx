import { useCallback, useRef, useState } from 'react';
import { App, AutoComplete, Button, Checkbox, Collapse, ConfigProvider, Input, InputNumber, Modal, Space, Typography } from 'antd';
import { nanoid } from 'nanoid';
import type { FieldMeta, FunctionParameterSchema, UnifiedMappingDraft, UnifiedMappingRule } from '../api/fieldMapping';
import { getNifiOverlayContainer } from './overlayContainer';
import { BranchBodyEditor, Choice, ConversionStepsEditor, ExpressionEditor, FallbackEditor, InlineRuleChoices, JsonValueEditor, NullHandlingEditor, PredicateEditor, RuleFormErrors, RuleInputEditor, StepParameterForm, TypedValueEditor, type DatasourceChoice } from './UnifiedRuleForms';
import { isEditableRule, logicalFieldType, RULE_TYPES, setRuleSteps, stepSummary, TYPE_LABELS, validateRuleForm, type Predicate, type RuleBody } from './unifiedRuleModel';
import { concreteRuleMode, ruleChoice, selectConcreteRule } from './mappingRulePresentation';
import MappingRuleCatalog, { MappingRuleIcon } from './MappingRuleCatalog';

interface Props {
  initial: UnifiedMappingRule;
  creating: boolean;
  draft: UnifiedMappingDraft;
  fields: FieldMeta[];
  targets: FieldMeta[];
  sourceLabels: Record<string,string>;
  targetLabels: Record<string,string>;
  functionSchemas: Record<string,FunctionParameterSchema>;
  datasources: DatasourceChoice[];
  loadDatasources: () => void;
  onApply: (rule: UnifiedMappingRule) => void;
  onClose: () => void;
}

/** Both the table configuration link and the toolbar add button use this form. */
export default function MappingRuleDialog(props:Props) {
  const {modal}=App.useApp();
  const [rule,setRule]=useState(()=>structuredClone(props.initial));
  const [errors,setErrors]=useState<string[]>([]);
  const [expanded,setExpanded]=useState<string[]>([]);
  const detailRef=useRef<HTMLDivElement>(null);
  const formErrors=useRef(new Map<string,string>());
  const report=useCallback((id:string,error:string)=>{if(error)formErrors.current.set(id,error);else formErrors.current.delete(id);},[]);
  const mode=concreteRuleMode(rule), conditional=rule.when!=null;
  const generated=['UUID32','UUID','CURRENT_TIME'].includes(mode);
  const stepIndex=rule.steps.findIndex(s=>!s.uiRole),step=rule.steps[stepIndex];
  const common={fields:props.fields,functionSchemas:props.functionSchemas,datasources:props.datasources,dictionaryBindings:props.draft.dictionaryBindings,loadDatasources:props.loadDatasources};
  const visibleSteps=rule.steps.filter(s=>!s.uiRole);
  function changeMode(next:string) {
    const apply=()=>{setRule(current=>selectConcreteRule(current,next,nanoid()));setErrors([]);detailRef.current?.scrollTo({top:0});};
    if(conditional&&next!=='CONDITIONAL')modal.confirm({title:'切换治理规则',content:'切换后将使用所选规则，当前判断条件及两个分支会移除。',getContainer:getNifiOverlayContainer,onOk:apply});
    else apply();
  }
  function apply() {
    const problems=[...formErrors.current.values(),...validateRuleForm(rule)];
    if(props.draft.rules.some(r=>r.ruleId!==props.initial.ruleId&&r.target.name===rule.target.name))problems.push('目标字段已有治理规则，请编辑已有规则');
    setErrors(problems);if(!problems.length)props.onApply({...rule,ruleId:props.initial.ruleId});
  }
  function patchStep(updated:typeof step) { setRule(current=>{const steps=[...current.steps];steps[stepIndex]=updated;return setRuleSteps(current,steps,props.targets.some(field=>field.name===current.target.name));}); }
  const fieldOptions=(fields:FieldMeta[],labels:Record<string,string>)=>fields.map(f=>({value:f.name,label:labels[f.name]?f.name+' · '+labels[f.name]:f.name}));
  const sourceInput=rule.input.kind==='FIELD'
    ? <div className="unified-form-field"><Typography.Text>源字段</Typography.Text><AutoComplete aria-label="源字段" value={rule.input.name??''} placeholder="选择或填写源字段" options={fieldOptions(props.fields,props.sourceLabels)} filterOption={(text,option)=>String(option?.label??'').toLowerCase().includes(text.toLowerCase())} onChange={name=>setRule(current=>({...current,input:{...current.input,name}}))}/></div>
    : rule.input.kind==='LITERAL'
      ? <TypedValueEditor value={rule.input.value??null} onChange={value=>setRule({...rule,input:{...rule.input,value}})}/>
      : !generated&&rule.input.kind!=='EXPRESSION'&&<RuleInputEditor input={rule.input} fields={props.fields} onChange={input=>setRule({...rule,input})}/>;
  const conditionalForm=conditional&&<div className="fm-rule-condition">
    <div className="fm-rule-section-title">判断条件</div>
    {typeof rule.when==='string'
      ? <Input.TextArea aria-label="条件表达式" rows={3} value={rule.when} onChange={e=>setRule({...rule,when:e.target.value})}/>
      : <PredicateEditor value={rule.when as Predicate} fields={props.fields} onChange={when=>setRule({...rule,when})}/>}
    <Collapse defaultActiveKey={['then','else']} items={(['then','else'] as const).map(branch=>({key:branch,label:branch==='then'?'条件成立时':'条件不成立时',children:<BranchBodyEditor {...common} body={(rule[branch]??{input:{kind:'LITERAL',value:null},steps:[]}) as RuleBody} onChange={body=>setRule({...rule,[branch]:body})}/>}))}/>
  </div>;
  return <ConfigProvider componentSize="small" theme={{token:{controlHeightSM:28,fontSizeSM:13}}}><Modal className={'fm-rule-modal fm-rule-workbench'+(errors.length?' has-errors':'')} width={1080} open title={props.creating?'添加治理规则':'治理规则 · '+(rule.target.name||'字段配置')} getContainer={getNifiOverlayContainer} onCancel={props.onClose} footer={<>
    {!!errors.length&&<div className="fm-rule-errors" role="alert">{errors.map((error,i)=><div key={i}>{error}</div>)}</div>}
    <div className="fm-rule-footer"><Typography.Text type="secondary">配置应用后，保存画布即可保留。</Typography.Text><Space><Button size="middle" onClick={props.onClose}>取消</Button><Button size="middle" type="primary" onClick={apply}>应用规则</Button></Space></div>
  </>}>
    <RuleFormErrors.Provider value={report}>
    <InlineRuleChoices.Provider value>
    <div className="fm-rule-workspace">
      <MappingRuleCatalog mode={mode} onChange={changeMode}/>
      <div className="fm-rule-detail" ref={detailRef}>
        <header className="fm-rule-detail-heading"><div className="fm-rule-detail-icon"><MappingRuleIcon mode={mode}/></div><div><h3>{ruleChoice(mode).label}</h3><Typography.Text type="secondary" className="fm-rule-description">{ruleChoice(mode).description}</Typography.Text></div></header>
      <div className="fm-rule-main">
        <section className="fm-rule-fields">
        <div className="fm-rule-section-title">字段对应</div>
        <div className="unified-form-grid">
          {!conditional&&sourceInput}
          <div className="unified-form-field"><Typography.Text>目标字段</Typography.Text><AutoComplete aria-label="目标字段" value={rule.target.name} placeholder="选择或填写目标字段" options={fieldOptions(props.targets,props.targetLabels)} filterOption={(text,option)=>String(option?.label??'').toLowerCase().includes(text.toLowerCase())} onChange={name=>setRule({...rule,target:{...rule.target,name}})} onSelect={name=>{
            const field=props.targets.find(f=>f.name===name);
            setRule({...rule,target:{...rule.target,name,...(field?{logicalType:logicalFieldType(field),nullable:field.nullable,length:field.length,precision:field.precision,scale:field.scale}:{})}});
          }}/><Typography.Text type="secondary" className="fm-rule-type-note">输出类型：{TYPE_LABELS[rule.target.logicalType??'ANY']??rule.target.logicalType}</Typography.Text></div>
        </div></section>
        <div className="fm-rule-conversion">
          {conditionalForm}
          {!conditional&&mode==='EXPRESSION'&&<ExpressionEditor expression={rule.input.expression??''} fields={props.fields} onChange={expression=>setRule({...rule,input:{...rule.input,expression}})}/>}
          {!conditional&&step&&!generated&&!['DIRECT','CONSTANT','EXPRESSION','SYSTEM'].includes(mode)&&<section className="fm-rule-parameters"><div className="fm-rule-section-title">规则参数</div><StepParameterForm {...common} steps={rule.steps} step={step} onChange={patchStep}/></section>}
          {!conditional&&visibleSteps.some(s=>s.operator!=='DIRECT')&&(generated||['CONSTANT','EXPRESSION','SYSTEM'].includes(mode)||visibleSteps.length>1)&&<div className="fm-rule-chain-note"><Typography.Text type="secondary">后续处理：{visibleSteps.map(stepSummary).join(' → ')}</Typography.Text><Button type="link" size="small" onClick={()=>setExpanded(keys=>[...new Set([...keys,'steps'])])}>查看转换步骤</Button></div>}
        </div>
      </div>
      <section className="fm-rule-policy-section"><div className="fm-rule-section-title">空值与失败处理</div><div className="fm-rule-policy-grid">
          {!conditional&&<NullHandlingEditor body={rule as RuleBody} onChange={body=>setRule({...rule,input:body.input,steps:body.steps})}/>}
          <FallbackEditor value={rule.onError??'ERROR'} defaultValue={rule.defaultValue} onChange={(onError,defaultValue)=>setRule({...rule,onError,defaultValue})}/>
          {!conditional&&step&&<details className="fm-rule-step-error"><summary>当前转换步骤的失败策略</summary><FallbackEditor step value={step.onError??'ERROR'} defaultValue={step.defaultValue} onChange={(onError,defaultValue)=>patchStep({...step,onError,defaultValue})}/></details>}
      </div></section>
      <section className="fm-rule-output-section"><div className="fm-rule-section-title">输出类型与校验</div><div className="unified-form-grid">
          <Choice label="输出类型" value={rule.target.logicalType??'ANY'} choices={Object.fromEntries(RULE_TYPES.map(t=>[t,TYPE_LABELS[t]]))} onChange={logicalType=>setRule({...rule,target:{...rule.target,logicalType}})}/>
          <div className="unified-form-field"><Typography.Text>最大字符数</Typography.Text><InputNumber aria-label="目标最大字符数" min={0} max={262144} value={rule.target.length??0} onChange={length=>setRule({...rule,target:{...rule.target,length:length??0}})}/><Typography.Text type="secondary">0 表示不限</Typography.Text></div>
          <Checkbox checked={rule.target.nullable!==false} onChange={e=>setRule({...rule,target:{...rule.target,nullable:e.target.checked}})}>允许 NULL</Checkbox>
          {rule.target.logicalType==='DECIMAL'&&<div className="unified-form-field"><Typography.Text>精度 / 小数位</Typography.Text><Space><InputNumber aria-label="目标精度" min={0} max={1000} value={rule.target.precision??0} onChange={precision=>setRule({...rule,target:{...rule.target,precision:precision??0,...(!precision?{scale:0}:{})}})}/><InputNumber aria-label="目标小数位" min={-1000} max={1000} disabled={!rule.target.precision} value={!rule.target.precision?0:rule.target.scale??0} onChange={scale=>setRule({...rule,target:{...rule.target,scale:scale??0}})}/></Space><Typography.Text type="secondary">精度为 0 时保留原始小数位；已登记目标仍按物理列容量校验</Typography.Text></div>}
          <Typography.Text type="secondary">范围、格式及允许值可通过“输出校验”转换或附加转换步骤配置。</Typography.Text>
        </div></section>
      <Collapse className="fm-rule-options" activeKey={expanded} onChange={keys=>setExpanded(Array.isArray(keys)?keys:[keys])} items={[
        {key:'steps',label:'附加转换步骤'+(visibleSteps.length>1?' · '+visibleSteps.length+' 个步骤':''),children:conditional?<Typography.Text type="secondary">请在各条件分支内配置转换步骤。</Typography.Text>:<ConversionStepsEditor {...common} steps={rule.steps} onChange={steps=>setRule(current=>setRuleSteps(current,steps,props.targets.some(field=>field.name===current.target.name)))}/>},
        {key:'advanced',label:'高级设置',children:<div className="unified-rule-section">
          {!conditional&&<RuleInputEditor input={rule.input} fields={props.fields} onChange={input=>setRule({...rule,input})}/>}
          {conditional&&<Choice label="条件配置方式" value={typeof rule.when==='string'?'expression':'visual'} choices={{visual:'可视化条件',expression:'安全条件表达式'}} onChange={mode=>{
            modal.confirm({title:'切换条件配置方式',content:'切换后请重新配置判断条件，分支内容会保留。',getContainer:getNifiOverlayContainer,onOk:()=>setRule({...rule,when:mode==='expression'?'':{op:'eq',left:{kind:'FIELD',name:''},right:{kind:'LITERAL',value:''}}})});
          }}/>}
          <div className="unified-form-field"><Typography.Text>规则说明</Typography.Text><Input.TextArea aria-label="规则说明" value={rule.comment} rows={2} onChange={e=>setRule({...rule,comment:e.target.value})}/></div>
          <details><summary>完整规则 JSON</summary><JsonValueEditor label="完整规则 JSON" value={rule} objectOnly onChange={raw=>{if(!isEditableRule(raw)){report('whole-rule','规则结构不完整，请检查输入、目标和步骤');return;}report('whole-rule','');setRule({...raw,ruleId:props.initial.ruleId});}}/></details>
        </div>}
      ]}/>
      </div>
    </div>
    </InlineRuleChoices.Provider>
    </RuleFormErrors.Provider>
  </Modal></ConfigProvider>;
}
