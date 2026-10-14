import test from 'node:test';
import assert from 'node:assert/strict';
import {fieldsForPresentation,patchVisualField,sourceNames,visualMapping} from './fieldMappingPresentation.ts';

test('visual presentation preserves the execution document and finds sources across branches and expressions',()=>{
 const rule={ruleId:'r',input:{kind:'FIELD',name:'city'},target:{name:'label',logicalType:'STRING',length:64},steps:[{stepId:'null',uiRole:'NULL_DEFAULT',operator:'FUNCTION',args:{function:'coalesce',parameters:['未知']}},{stepId:'func',operator:'FUNCTION',args:{function:'upper'},onError:'NULL'}],when:{op:'eq',left:{kind:'FIELD',name:'status'},right:{kind:'LITERAL',value:'1'}},then:{input:{kind:'EXPRESSION',expression:'upper($'+'{field:name})'},steps:[]},else:{input:{kind:'LITERAL',value:null},steps:[]},custom:{preserved:true}};
 const before=JSON.stringify(rule),display=visualMapping(rule);
 assert.deepEqual(sourceNames(rule),['city','status','name']);assert.equal(display.to,'/label');assert.equal(display.when,'条件分支');assert.equal(JSON.stringify(rule),before);
});
test('editing visual field chips retains conditional bodies, remaining transformations and failure policies',()=>{
 const rule={ruleId:'r',input:{kind:'FIELD',name:'old'},target:{name:'label',logicalType:'STRING',length:64},steps:[{operator:'FUNCTION',args:{function:'trim'},onError:'DEFAULT_VALUE',defaultValue:'unknown'},{operator:'VALIDATE',args:{required:true}}],when:{op:'isNull',left:{kind:'FIELD',name:'old'}},then:{input:{kind:'LITERAL',value:'A'},steps:[]},else:{input:{kind:'LITERAL',value:'B'},steps:[]},onError:'NULL',custom:{owner:'retained'}};
 const changed=patchVisualField(rule,'target','/amount',[{name:'amount',type:'NUMBER(18,2)',precision:18,scale:2,nullable:false}]);
 assert.deepEqual(changed.target,{name:'amount',logicalType:'DECIMAL',length:undefined,precision:18,scale:2,nullable:false});
 for(const key of ['ruleId','steps','when','then','else','onError','custom'])assert.deepEqual(changed[key],rule[key]);
 assert.equal(patchVisualField(rule,'source','/city',[]).input.name,'city');assert.equal(rule.input.name,'old');
});
test('visual field lists retain registered physical types and custom fields without fetching per row',()=>{
 const result=fieldsForPresentation([{name:'amount',type:'DECIMAL(18,2)',nullable:false},{name:'customer_id',type:'BIGINT',isPk:true}],{amount:'金额',customer_id:'客户编号'},['/amount','/legacy_field'],[{name:'extra',path:'/extra',type:'STRING',label:'自定义'}]);
 assert.equal(result.find(f=>f.name==='amount').type,'DECIMAL');assert.equal(result.find(f=>f.name==='amount').nullable,false);assert.equal(result.find(f=>f.name==='customer_id').isPk,true);assert.ok(result.some(f=>f.path==='/legacy_field'));assert.ok(result.some(f=>f.path==='/extra'));
});
