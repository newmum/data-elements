import {parsePreciseJson} from '../utils/preciseJson.ts';
import test from 'node:test';
import assert from 'node:assert/strict';
import {createRule,emptyUnifiedDraft,setConditional,setNullHandling,nullHandling,parseNumericConstant,validateRuleForm,usesUnifiedRecordRules,isEditableRule,assertExactJsonNumbers,primaryRuleMode,changePrimaryRuleMode,includeOdsSystemMappings,logicalFieldType} from './unifiedRuleModel.ts';

test('physical field types use the kernel type semantics in the configuration form',()=>{
 for(const [type,logical] of [['datetime','DATETIME'],['varchar2(128)','STRING'],['timestamp with time zone','INSTANT'],['NUMERIC(38,10)','DECIMAL'],['INT unsigned','LONG'],['BIGINT UNSIGNED','DECIMAL'],['bit','BOOLEAN']])assert.equal(logicalFieldType({name:'f',type}),logical);
 assert.equal(logicalFieldType({name:'f',type:'timestamp',logicalType:'INSTANT'}),'INSTANT');
});

test('automatic mapping retains ODS generators without duplicating or overwriting selected fields',()=>{
 const spec={version:'1.0',mappings:[{to:'ODS_UUID',constant:'fixed'},{from:'name',to:'name'}]};
 const result=includeOdsSystemMappings(spec,[{name:'ODS_UUID'},{name:'ods_rksj'},{name:'ODS_GXSJ'},{name:'name'}]);
 assert.deepEqual(result.mappings.slice(0,2),spec.mappings);assert.deepEqual(result.mappings.slice(2),[{to:'ods_rksj',expression:'LOCALTIMESTAMP'},{to:'ODS_GXSJ',expression:'LOCALTIMESTAMP'}]);assert.equal(spec.mappings.length,2);
});

test('simple rule selector retains null handling, subsequent steps, identities and unknown metadata',()=>{
 const original={...createRule('FUNCTION','r'),target:{name:'label'},input:{kind:'FIELD',name:'code'},custom:{owner:'preserved'},steps:[{stepId:'blank',uiRole:'EMPTY_TO_NULL',operator:'FUNCTION',args:{function:'blankToNull'}},{stepId:'primary',operator:'FUNCTION',args:{function:'upper'},onError:'NULL'},{stepId:'check',operator:'VALIDATE',args:{required:true}}]};
 const next=changePrimaryRuleMode(original,'LOOKUP','new');
 assert.equal(primaryRuleMode(next),'LOOKUP');assert.equal(next.steps[1].stepId,'primary');assert.equal(next.steps[1].onError,'NULL');
 assert.deepEqual(next.steps[0],original.steps[0]);assert.deepEqual(next.steps[2],original.steps[2]);assert.deepEqual(next.custom,original.custom);assert.equal(original.steps[1].operator,'FUNCTION');
 assert.strictEqual(changePrimaryRuleMode(next,'LOOKUP','ignored'),next);
});
test('add and edit use the same primary modes and retain additional conversions for value inputs',()=>{
 const original={...createRule('CAST','r'),input:{kind:'FIELD',name:'amount'},target:{name:'output'}};
 for(const [mode,kind] of [['CONSTANT','LITERAL'],['EXPRESSION','EXPRESSION'],['SYSTEM','SYSTEM']]){
   const value=changePrimaryRuleMode(original,mode,'step');assert.equal(value.input.kind,kind);assert.equal(primaryRuleMode(value),mode);assert.deepEqual(value.steps,original.steps);
 }
 const conditional=changePrimaryRuleMode(original,'CONDITIONAL','step');assert.equal(primaryRuleMode(conditional),'CONDITIONAL');assert.deepEqual(conditional.then.steps,original.steps);
});

test('conditional branches preserve independent inputs, transformations and metadata',()=>{
  const original={...createRule('FUNCTION','r'),input:{kind:'FIELD',name:'name'},target:{name:'label',logicalType:'STRING'},comment:'说明',onError:'DEFAULT_VALUE',defaultValue:'未知'};
  const conditional=setConditional(original,true);
  conditional.then.steps[0].args.function='upper';
  assert.equal(original.steps[0].args.function,'trim');
  assert.equal(conditional.then.input.name,'name');
  assert.equal(conditional.defaultValue,'未知');
  assert.equal(conditional.else.input.value,null);
  assert.deepEqual(setConditional(conditional,false),original);
});
test('null handling uses actual kernel functions before conversions and preserves step fallbacks',()=>{
  const body={input:{kind:'FIELD',name:'amount'},steps:[{stepId:'cast',operator:'CAST',args:{logicalType:'DECIMAL'},onError:'DEFAULT_VALUE',defaultValue:'0'},{stepId:'check',operator:'VALIDATE',args:{minimum:0}}]};
  const configured=setNullHandling(body,{blankToNull:true,useDefault:true,defaultValue:'123.45'});
  assert.deepEqual(configured.steps.map(s=>s.args.function??s.operator),['blankToNull','coalesce','CAST','VALIDATE']);
  assert.deepEqual(configured.steps[1].args.parameters,['123.45']);
  assert.equal(configured.steps[2].defaultValue,'0');
  assert.deepEqual(nullHandling(configured),{blankToNull:true,useDefault:true,defaultValue:'123.45'});
  assert.deepEqual(setNullHandling(configured,{blankToNull:false,useDefault:false,defaultValue:''}),body);
  assert.equal(body.steps.length,2);
});
test('numeric constants reject silent precision loss while accepting ordinary numeric values',()=>{
  for(const value of ['42','-0.25','1.25e3','.5'])assert.equal(parseNumericConstant(value),Number(value));
  for(const value of ['9007199254740993','1234567890123456.78','1e400','abc',''])assert.throws(()=>parseNumericConstant(value));
});
test('new ID validation and date rules have the correct target semantics',()=>{
  assert.equal(createRule('ID_CARD_VALIDATE','id').target.logicalType,'BOOLEAN');
  assert.equal(createRule('DATE_PARSE','date').target.logicalType,'DATE');
  assert.equal(createRule('CONSTANT','const').input.kind,'LITERAL');
  assert.equal(createRule('LOOKUP_MULTI','dict').steps[0].args.partialMissingPolicy,'KEEP_TOKEN');
});
test('incomplete conditions and dictionary resources cannot be applied',()=>{
  const rule=setConditional({...createRule('DIRECT','r'),target:{name:'result'},input:{kind:'FIELD',name:'code'}},true);
  rule.when={op:'and',children:[{op:'in',left:{kind:'FIELD',name:''},values:[]}]};
  assert.ok(validateRuleForm(rule).some(s=>s.includes('源字段')));
  assert.ok(validateRuleForm(rule).some(s=>s.includes('集合值')));
  const dict=createRule('LOOKUP','dict');dict.input.name='code';dict.target.name='label';
  assert.ok(validateRuleForm(dict).some(s=>s.includes('登记表')));
  dict.steps[0].args.definition={datasourceId:'db',tableId:'table',keyColumns:['code'],valueColumn:'label'};
  assert.deepEqual(validateRuleForm(dict),[]);
});
test('empty upgraded drafts retain passthrough, missing source, excludes and temporal settings',()=>{
  const draft=emptyUnifiedDraft({passthroughUnmapped:true,passthroughCaseSensitive:false,onMissingSource:'FAIL',exclude:['secret'],zoneId:'UTC',defaultLocale:'en-US'});
  assert.equal(draft.policies.caseSensitive,false);assert.equal(draft.policies.zoneId,'UTC');assert.equal(draft.policies.onMissingSource,'FAIL');assert.deepEqual(draft.exclude,['secret']);
});
test('execution controls reflect both unified schema and legacy Java engine without accepting malformed drafts',()=>{
  assert.equal(usesUnifiedRecordRules(JSON.stringify(emptyUnifiedDraft())),true);
  assert.equal(usesUnifiedRecordRules({version:'1.0',executionEngine:'JAVA_RECORD'}),true);
  assert.equal(usesUnifiedRecordRules({version:'1.0',mappings:[]}),false);
  assert.equal(usesUnifiedRecordRules('{'),false);
});
test('advanced JSON rejects malformed rule structures before they can break the visual form',()=>{
  const rule=createRule('DIRECT','r');assert.equal(isEditableRule(rule),true);
  for(const malformed of [{...rule,target:{}},{...rule,input:null},{...rule,steps:[null]},{...rule,steps:[{operator:'FUNCTION',args:[]}]},{...rule,when:{op:'and',children:[null]},then:{input:null,steps:[]}}])assert.equal(isEditableRule(malformed),false);
});
test('advanced JSON preserves long text values and blocks imprecise numeric tokens even in nested arguments',()=>{
  assert.doesNotThrow(()=>assertExactJsonNumbers(JSON.stringify({id:'9007199254740993',escape:'"12345678901234567890"',number:12.35,nested:[1e5,-0.25]})));
  for(const text of ['{"value":9007199254740993}','{"args":{"values":[1234567890123456.78]}}','{"number":1e400}'])assert.throws(()=>assertExactJsonNumbers(text));
});

test('precision checks reject underflow and subnormal rounding; preview decoding preserves server tokens',()=>{
  for(const value of ['1e-400','4e-324'])assert.throws(()=>parseNumericConstant(value));
  assert.equal(parseNumericConstant('1.25e3'),1250);
  assert.deepEqual(parsePreciseJson('{"amount":12345678901234567890.123456789012345678,"id":9007199254740993,"count":100,"name":"12345678901234567890"}'),{amount:'12345678901234567890.123456789012345678',id:'9007199254740993',count:100,name:'12345678901234567890'});
});
