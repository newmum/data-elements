import type { FieldMappingSpec, FieldMeta, UnifiedMappingDraft, UnifiedMappingRule } from '../api/fieldMapping';

export const RULE_TYPES = ['ANY','STRING','INT','LONG','DECIMAL','DOUBLE','BOOLEAN','DATE','TIME','DATETIME','INSTANT','ARRAY'];
export const TYPE_LABELS:Record<string,string> = {ANY:'保留类型',STRING:'文本',INT:'整数',LONG:'长整数',DECIMAL:'精确小数',DOUBLE:'浮点数',BOOLEAN:'布尔值',DATE:'日期',TIME:'时分秒',DATETIME:'日期时间',INSTANT:'绝对时间',ARRAY:'数组'};
export function logicalFieldType(field:FieldMeta):string {
  let type=(field.logicalType??field.type??'ANY').toUpperCase().replace(/\([^)]*\)/g,'').trim();
  if(['INT UNSIGNED','INTEGER UNSIGNED'].includes(type))return 'LONG';
  if(type==='BIGINT UNSIGNED')return 'DECIMAL';
  type=type.replace(/\s+UNSIGNED$/,'');
  const aliases:Record<string,string>={VARCHAR:'STRING',VARCHAR2:'STRING',NVARCHAR:'STRING',NVARCHAR2:'STRING',CHAR:'STRING',NCHAR:'STRING',TEXT:'STRING',CLOB:'STRING',LONGVARCHAR:'STRING',TINYINT:'INT',SMALLINT:'INT',INTEGER:'INT',INT2:'INT',INT4:'INT',BIGINT:'LONG',INT8:'LONG',NUMERIC:'DECIMAL',NUMBER:'DECIMAL',DEC:'DECIMAL',FLOAT:'DOUBLE',REAL:'DOUBLE','DOUBLE PRECISION':'DOUBLE',BOOL:'BOOLEAN',BIT:'BOOLEAN',TIMESTAMP:'DATETIME',DATETIME2:'DATETIME',SMALLDATETIME:'DATETIME',DATETIME64:'DATETIME',DATETIMEOFFSET:'INSTANT','TIMESTAMP WITH TIME ZONE':'INSTANT',TIMESTAMPTZ:'INSTANT','TIMESTAMP WITHOUT TIME ZONE':'DATETIME','TIMESTAMP WITH LOCAL TIME ZONE':'INSTANT','TIME WITHOUT TIME ZONE':'TIME'};
  return aliases[type]??type;
}
export const INPUT_LABELS:Record<string,string> = {FIELD:'源字段',LITERAL:'常量',EXPRESSION:'安全表达式',SYSTEM:'系统值'};
export const OPERATOR_LABELS:Record<string,string> = {DIRECT:'原值',CAST:'类型转换',FUNCTION:'函数',LOOKUP:'单值码表',LOOKUP_MULTI:'拆分码表',STATIC_MAP:'枚举对标',DATE_PARSE:'日期解析',DATE_FORMAT:'日期格式化',ID_CARD_15_TO_18:'身份证15转18位',ID_CARD_VALIDATE:'身份证校验',CONCAT:'多字段合并',EXPRESSION:'安全表达式',VALIDATE:'输出校验',SYSTEM_VALUE:'系统值'};
export const PRESETS = [{key:'DIRECT',label:'原值映射'},{key:'CONSTANT',label:'常量字段'},{key:'CAST',label:'类型转换'},{key:'FUNCTION',label:'函数处理'},{key:'DATE_PARSE',label:'日期处理'},{key:'ID_CARD_15_TO_18',label:'身份证转换'},{key:'ID_CARD_VALIDATE',label:'身份证校验'},{key:'LOOKUP',label:'单值码表'},{key:'LOOKUP_MULTI',label:'拆分码表'},{key:'CONDITIONAL',label:'条件分支'},{key:'EXPRESSION',label:'安全表达式'},{key:'VALIDATE',label:'输出校验'}];
export type RuleStep = UnifiedMappingRule['steps'][number];
export type RuleBody = {input:UnifiedMappingRule['input'];steps:RuleStep[];when?:unknown;then?:unknown;else?:unknown;[key:string]:unknown};
export interface Predicate {op:string;left?:UnifiedMappingRule['input'];right?:UnifiedMappingRule['input'];values?:UnifiedMappingRule['input'][];children?:Predicate[];child?:Predicate;[key:string]:unknown}
export const FALLBACK_LABELS:Record<string,string> = {ERROR:'交给记录错误策略',NULL:'置为 NULL',KEEP_SOURCE:'保留字段原输入',KEEP_INPUT:'保留当前步骤输入',KEEP_ORIGINAL:'保留字段原输入',DEFAULT_VALUE:'使用指定默认值'};
export function usesUnifiedRecordRules(value:unknown):boolean {
  try {const spec=typeof value==='string'?JSON.parse(value):value;return !!spec&&(spec.schemaVersion==='2.0'||spec.executionEngine==='JAVA_RECORD');}catch{return false;}
}
export function isEditableRule(value:unknown):value is UnifiedMappingRule {
  if(!value||typeof value!=='object')return false;
  const rule=value as UnifiedMappingRule;
  if(typeof rule.ruleId!=='string'||!rule.ruleId.trim()||typeof rule.target?.name!=='string'||typeof rule.input?.kind!=='string'||!Array.isArray(rule.steps))return false;
  if(rule.steps.some(step=>!step||typeof step.operator!=='string'||step.args!=null&&(typeof step.args!=='object'||Array.isArray(step.args))))return false;
  try{validateRuleForm(rule);return true;}catch{return false;}
}
export function emptyUnifiedDraft(legacy?:Record<string,unknown>):UnifiedMappingDraft {
  return {schemaVersion:'2.0',executionEngine:'JAVA_RECORD',policies:{recordErrorMode:'FAIL_FLOWFILE',onMissingSource:legacy?.onMissingSource??'NULL',allowUnmappedPassthrough:!!legacy?.passthroughUnmapped,caseSensitive:legacy?.passthroughCaseSensitive!==false,zoneId:legacy?.zoneId??'Asia/Shanghai',locale:legacy?.defaultLocale??'zh-CN'},rules:[],...(Array.isArray(legacy?.exclude)?{exclude:legacy.exclude}:{})};
}
export function stepDefaults(operator:string):Record<string,unknown> {
  switch(operator){
    case 'FUNCTION':return {function:'trim',parameters:[]};
    case 'CAST':return {logicalType:'STRING',precision:38,scale:10,roundingMode:'UNNECESSARY'};
    case 'DATE_PARSE':return {pattern:'uuuu-MM-dd',logicalType:'DATE'};
    case 'DATE_FORMAT':return {pattern:'uuuuMMdd',logicalType:'DATE'};
    case 'ID_CARD_15_TO_18':return {centuryPolicy:'PREFIX_19',already18Policy:'VALIDATE_AND_KEEP'};
    case 'ID_CARD_VALIDATE':return {};
    case 'LOOKUP':case 'LOOKUP_MULTI':return {definition:{datasourceId:'',tableId:'',keyColumns:[],valueColumn:'',duplicatePolicy:'ERROR'},missPolicy:'NULL',allMissingPolicy:'NULL',partialMissingPolicy:'KEEP_TOKEN',separator:',',outputSeparator:',',outputMode:'STRING',nullLabelPolicy:'PRESERVE',emptyLabelPolicy:'PRESERVE'};
    case 'STATIC_MAP':return {values:{},fallback:null};
    case 'CONCAT':return {inputs:[],separator:',',nullElementPolicy:'EMPTY'};
    case 'EXPRESSION':return {expression:'${value}'};
    case 'VALIDATE':return {required:false};
    case 'SYSTEM_VALUE':return {generator:'UUID'};
    default:return {};
  }
}
export function createRule(template:string,id:string):UnifiedMappingRule {
  const rule:UnifiedMappingRule={ruleId:id,enabled:true,input:{kind:'FIELD',name:''},target:{name:'',logicalType:'STRING',nullable:true},steps:[],onError:'ERROR'};
  if(template==='CONSTANT')rule.input={kind:'LITERAL',value:''};
  else if(template==='CONDITIONAL')return setConditional(rule,true);
  else if(template==='EXPRESSION')rule.input={kind:'EXPRESSION',expression:'${field:字段名}'};
  else if(template!=='DIRECT')rule.steps=[{stepId:id+'-step',operator:template,args:stepDefaults(template)}];
  if(template==='ID_CARD_VALIDATE')rule.target.logicalType='BOOLEAN';
  if(template==='DATE_PARSE')rule.target.logicalType='DATE';
  return rule;
}
export function setConditional(rule:UnifiedMappingRule,enabled:boolean):UnifiedMappingRule {
  if(enabled){if(rule.when!=null)return rule;return {...rule,when:{op:'eq',left:{kind:'FIELD',name:rule.input.name??''},right:{kind:'LITERAL',value:''}},then:{input:structuredClone(rule.input),steps:structuredClone(rule.steps)},else:{input:{kind:'LITERAL',value:null},steps:[]}};}
  const result={...rule};delete result.when;delete result.then;delete result.else;return result;
}
export function nullHandling(body:RuleBody){return {blankToNull:body.steps.some(s=>s.uiRole==='EMPTY_TO_NULL'),useDefault:body.steps.some(s=>s.uiRole==='NULL_DEFAULT'),defaultValue:body.steps.find(s=>s.uiRole==='NULL_DEFAULT')?.args?.parameters instanceof Array?(body.steps.find(s=>s.uiRole==='NULL_DEFAULT')!.args!.parameters as unknown[])[0]:''};}
export function setNullHandling(body:RuleBody,settings:ReturnType<typeof nullHandling>):RuleBody {
  const steps=body.steps.filter(s=>!['EMPTY_TO_NULL','NULL_DEFAULT'].includes(s.uiRole??''));
  const first:RuleStep[]=[];
  if(settings.blankToNull)first.push({stepId:'null-policy-blank',uiRole:'EMPTY_TO_NULL',operator:'FUNCTION',args:{function:'blankToNull',parameters:[]}});
  if(settings.useDefault)first.push({stepId:'null-policy-default',uiRole:'NULL_DEFAULT',operator:'FUNCTION',args:{function:'coalesce',parameters:[settings.defaultValue]}});
  return {...body,steps:[...first,...steps]};
}
export function stepSummary(step:RuleStep):string {
  if(step.uiRole==='EMPTY_TO_NULL')return '空白转 NULL';if(step.uiRole==='NULL_DEFAULT')return 'NULL 补默认值';
  return step.operator==='FUNCTION'?String(step.args?.function??'函数'):OPERATOR_LABELS[step.operator]??step.operator;
}
export function inputSummary(rule:UnifiedMappingRule):string {
  if(rule.when!=null)return '条件分支';if(rule.input.kind==='FIELD')return rule.input.name||'未选源字段';
  if(rule.input.kind==='LITERAL')return '常量：'+String(rule.input.value===null?'NULL':typeof rule.input.value==='object'?JSON.stringify(rule.input.value):rule.input.value??'');
  return rule.input.kind==='EXPRESSION'?'表达式':`系统值：${rule.input.generator??'UUID'}`;
}
/** The simple editor changes only the primary operation; downstream steps and metadata survive. */
export function primaryRuleMode(rule:UnifiedMappingRule):string {
  if(rule.when!=null)return 'CONDITIONAL';
  if(rule.input.kind==='LITERAL')return 'CONSTANT';
  if(rule.input.kind==='EXPRESSION')return 'EXPRESSION';
  if(rule.input.kind==='SYSTEM')return 'SYSTEM';
  return rule.steps.find(s=>!s.uiRole)?.operator??'DIRECT';
}
export function changePrimaryRuleMode(rule:UnifiedMappingRule,mode:string,id:string):UnifiedMappingRule {
  if(mode===primaryRuleMode(rule))return rule;
  if(mode==='CONDITIONAL')return setConditional(rule,true);
  const next=setConditional(rule,false);
  if(['CONSTANT','EXPRESSION','SYSTEM'].includes(mode))return {...next,input:mode==='CONSTANT'?{kind:'LITERAL',value:''}:mode==='SYSTEM'?{kind:'SYSTEM',generator:'UUID32'}:{kind:'EXPRESSION',expression:''}};
  const steps=[...next.steps],index=steps.findIndex(s=>!s.uiRole);
  const step={stepId:index<0?id:steps[index].stepId??id,operator:mode,args:stepDefaults(mode)};
  if(mode==='DATE_PARSE'&&['DATETIME','INSTANT'].includes(next.target.logicalType??''))step.args={pattern:'uuuu-MM-dd HH:mm:ss',logicalType:next.target.logicalType};
  if(index<0)steps.push(step);else steps[index]={...steps[index],...step};
  return {...next,input:next.input.kind==='FIELD'||next.input.kind==='TARGET'?next.input:{kind:'FIELD',name:''},steps};
}
export function includeOdsSystemMappings(spec:FieldMappingSpec,targets:FieldMeta[]):FieldMappingSpec {
  const configured=new Set(spec.mappings.map(rule=>(rule.to??'').toUpperCase()));
  const extra=targets.filter(field=>!configured.has(field.name.toUpperCase())&&['ODS_UUID','ODS_RKSJ','ODS_GXSJ'].includes(field.name.toUpperCase())).map(field=>({to:field.name,expression:field.name.toUpperCase()==='ODS_UUID'?'UUID()':'LOCALTIMESTAMP'}));
  return {...spec,mappings:[...spec.mappings,...extra]};
}
export {parseNumericConstant,assertExactJsonNumbers} from '../utils/preciseJson.ts';
export function validateRuleForm(rule:UnifiedMappingRule):string[] {
  const errors:string[]=[];if(!rule.target.name.trim())errors.push('请选择或填写目标字段');
  function input(value:UnifiedMappingRule['input']|undefined,label:string){
    if(!value){errors.push(label+'缺少输入');return;}
    if(value.kind==='FIELD'&&!value.name?.trim())errors.push(label+'请选择源字段');
    if(value.kind==='EXPRESSION'&&!value.expression?.trim())errors.push(label+'请填写表达式');
  }
  function predicate(value:Predicate,label:string,depth:number){
    if(depth>16){errors.push(label+'条件层级过深');return;}
    if(['and','or'].includes(value.op)){if(!value.children?.length)errors.push(label+'请增加条件');value.children?.forEach(v=>predicate(v,label,depth+1));return;}
    if(value.op==='not'){if(!value.child)errors.push(label+'缺少取反条件');else predicate(value.child,label,depth+1);return;}
    input(value.left,label+'比较对象：');
    if(['in','notIn'].includes(value.op)){if(!value.values?.length)errors.push(label+'请增加集合值');value.values?.forEach(v=>input(v,label+'集合值：'));}
    else if(!['isNull','isEmpty','isMissing'].includes(value.op))input(value.right,label+'比较值：');
  }
  function body(value:RuleBody,label:string,depth=0){
    if(depth>16){errors.push(label+'分支层级过深');return;}
    if(value.when!=null){if(typeof value.when==='string'){if(!value.when.trim())errors.push(label+'请填写条件表达式');}else predicate(value.when as Predicate,label,0);if(!value.then)errors.push(label+'缺少成立分支');else body(value.then as RuleBody,label+'成立分支',depth+1);if(value.else)body(value.else as RuleBody,label+'不成立分支',depth+1);return;}
    input(value.input,label);
    for(const step of value.steps??[]){const args=step.args??{};
      if(['DATE_PARSE','DATE_FORMAT'].includes(step.operator)&&!String(args.pattern??'').trim())errors.push(label+'请填写日期格式');
      if(step.operator==='FUNCTION'&&!args.function)errors.push(label+'请选择函数');
      if(['LOOKUP','LOOKUP_MULTI'].includes(step.operator)&&!args.dictionaryId&&!args.definitionRef&&!args.values){const d=(args.definition??{}) as Record<string,unknown>;if(!d.datasourceId||!d.tableId||!Array.isArray(d.keyColumns)||!d.keyColumns.length||!d.valueColumn)errors.push(label+'请选择码表数据源、登记表、键列与取值列');}
    }
  }
  body(rule as RuleBody,'');return errors;
}
