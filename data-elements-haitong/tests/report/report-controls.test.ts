import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import test from 'node:test';
import assert from 'node:assert/strict';
import {Select,Tooltip} from '../../src/report/components/ReportControls';
import ReportFileUpload from '../../src/report/components/ReportFileUpload';
import {reportRandomId} from '../../src/report/core/browserId';

test('Ant Design adapter preserves numeric sheet indices and ignores unknown or disabled options',()=>{
  const changes:number[]=[];
  const element=Select({value:0,options:[{value:0,label:'第一页'},{value:1,label:'第二页'},{value:2,label:'禁止选择',disabled:true}],onChange:value=>changes.push(value)});
  element.props.onChange(1);element.props.onChange(99);element.props.onChange(2);
  assert.deepEqual(changes,[1]);
  assert.equal(element.props.value,0);
});
test('format choices retain stable codes, long descriptions, and explicit clear',()=>{
  let selected='';
  const element=Select({value:'DATE',allowClear:true,options:[{value:'',label:'不设置'},{value:'DATE',label:'日期',description:'YYYY-MM-DD'}],onChange:value=>{selected=value;},onClear:()=>{selected='cleared';}});
  element.props.onChange('DATE');assert.equal(selected,'DATE');
  element.props.onChange(undefined);assert.equal(selected,'cleared');
  const markup=renderToStaticMarkup(element.props.optionRender({label:'日期',data:{description:'YYYY-MM-DD'}}));assert.match(markup,/日期/);assert.match(markup,/YYYY-MM-DD/);
  element.props.onChange('');assert.equal(selected,'');
});
test('native tooltip preserves wrapped trigger event and accessible expanded state',()=>{
  let opened=false;
  const trigger=Tooltip({title:'时间属性',children:createElement('button',null,'时钟'),onClick:()=>{opened=true;},'aria-expanded':true});
  trigger.props.onClick();assert.equal(opened,true);assert.equal(trigger.props['aria-expanded'],true);
  assert.match(renderToStaticMarkup(Tooltip({title:'回执',children:'上报结果'})),/title="回执"/);
});
test('upload uses a focusable real file input with multiple selection and disabled state',()=>{
  const markup=renderToStaticMarkup(createElement(ReportFileUpload,{disabled:false,onFiles:()=>{}}));
  assert.match(markup,/type="file"/);assert.match(markup,/multiple=""/);assert.doesNotMatch(markup,/<input[^>]*(?:\shidden|\stabindex="-1")/);
  assert.match(renderToStaticMarkup(createElement(ReportFileUpload,{disabled:true,onFiles:()=>{}})),/disabled=""/);
});

test('intranet HTTP creates request IDs without the secure-context-only randomUUID API',()=>{
  const id=reportRandomId({getRandomValues:array=>{new Uint8Array(array!.buffer).fill(5);return array;}});
  assert.equal(id,'05050505-0505-4505-8505-050505050505');
  assert.notEqual(reportRandomId(),reportRandomId());
});
