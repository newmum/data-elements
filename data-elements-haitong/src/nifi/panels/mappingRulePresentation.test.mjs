import test from 'node:test';
import assert from 'node:assert/strict';
import { concreteRuleLabel, concreteRuleMode, concreteInputLabel, selectConcreteRule } from './mappingRulePresentation.ts';

const base = {ruleId:'r',input:{kind:'FIELD',name:'id'},target:{name:'out',logicalType:'STRING'},steps:[]};
test('generated legacy inputs display business meaning without rewriting saved expressions',()=>{
  for(const [input,mode,label] of [
    [{kind:'SYSTEM',generator:'UUID32'},'UUID32','生成 UUID（32 位）'],
    [{kind:'EXPRESSION',expression:'LOCALTIMESTAMP'},'CURRENT_TIME','写入当前时间'],
    [{kind:'EXPRESSION',expression:'UUID()'},'UUID32','生成 UUID（32 位）'],
    [{kind:'EXPRESSION',expression:'LOWER(${field:name})'},'EXPRESSION','按表达式计算'],
  ]) {
    const rule={...base,input},before=JSON.stringify(rule);
    assert.equal(concreteRuleMode(rule),mode);assert.equal(concreteRuleLabel(rule),label);
    assert.equal(concreteInputLabel(rule),label);assert.strictEqual(selectConcreteRule(rule,mode,'ignored'),rule);
    assert.equal(JSON.stringify(rule),before);
  }
});
test('summaries preserve the order of transformations and identify actual output conversions',()=>{
  assert.equal(concreteRuleLabel(base),'保持原值');
  const rule={...base,steps:[{operator:'FUNCTION',uiRole:'EMPTY_TO_NULL',args:{function:'blankToNull'}},{operator:'ID_CARD_15_TO_18',args:{}},{operator:'CAST',args:{logicalType:'STRING'}},{operator:'VALIDATE',args:{required:true}}]};
  assert.equal(concreteRuleLabel(rule),'身份证 15 位转 18 位 → 转为文本 → 校验输出值');
  assert.equal(concreteRuleLabel({...base,steps:[{operator:'FUNCTION',args:{function:'trim'}}]}),'去除首尾空白');
});
test('selecting a generator retains subsequent conversions, policy, identities and custom metadata',()=>{
  const original={...base,onError:'NULL',custom:{owner:'kept'},steps:[{stepId:'date',operator:'CAST',args:{logicalType:'DATETIME'}}]};
  const changed=selectConcreteRule(original,'CURRENT_TIME','new');
  assert.deepEqual(changed.input,{kind:'SYSTEM',generator:'NOW'});assert.deepEqual(changed.steps,original.steps);
  for(const key of ['ruleId','target','onError','custom'])assert.deepEqual(changed[key],original[key]);
  assert.equal(original.input.kind,'FIELD');
  assert.equal(concreteRuleLabel(changed),'写入当前时间 → 转为日期时间');
});
test('conditional rules take priority over their stored seed input',()=>{
  const rule={...base,input:{kind:'SYSTEM',generator:'UUID32'},when:{op:'isNull',left:{kind:'FIELD',name:'id'}},then:{input:{kind:'LITERAL',value:'x'},steps:[]}};
  assert.equal(concreteRuleMode(rule),'CONDITIONAL');assert.equal(concreteRuleLabel(rule),'按条件选择值');
  assert.equal(selectConcreteRule(rule,'UUID32','new').when,undefined);
});
