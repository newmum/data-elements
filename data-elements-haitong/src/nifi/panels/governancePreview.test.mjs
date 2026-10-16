import test from 'node:test';
import assert from 'node:assert/strict';
import { checkedPreview, parsePreviewSamples, previewCell, previewTableRows, previewValue } from './governancePreview.ts';
import { governanceManifest, governanceNodeLabel } from '../utils/fieldGovernancePresentation.ts';

test('20/100 preview samples are parsed as one complete bounded input; malformed and imprecise inputs are rejected', () => {
  for (const count of [20,100]) {
    const rows=Array.from({length:count},(_,id)=>({id,enabled:false,text:'',nullable:null}));
    assert.deepEqual(parsePreviewSamples(JSON.stringify(rows)),rows);
  }
  for (const input of ['{','{}','[null]','[[]]',JSON.stringify(Array.from({length:101},()=>({}))), '[{"id":9007199254740993}]','[{"value":1e-400}]']) assert.throws(()=>parsePreviewSamples(input));
  assert.equal(parsePreviewSamples('[{"id":"9007199254740993"}]')[0].id,'9007199254740993');
});
test('preview output preserves original sample row numbers when earlier samples failed', () => {
  const response={resultRows:[{value:'kept'}],rowResults:[{rowIndex:0,success:false,errors:['invalid']},{rowIndex:1,success:true,errors:[]}],stats:{}};
  assert.equal(checkedPreview(response),response);
  assert.equal(previewTableRows(response.resultRows,response.rowResults.filter(row=>row.success).map(row=>row.rowIndex))[0].sequence,2);
  for (const invalid of [{...response,resultRows:[]},{...response,rowResults:[{rowIndex:80,success:true,errors:[]}]},{...response,rowResults:[null]},{...response,rowResults:[{rowIndex:0,success:true,errors:[]},{rowIndex:0,success:false,errors:[]}]}]) assert.throws(()=>checkedPreview(invalid));
});
test('preview values distinguish missing, null, empty, false and zero without reading object prototypes', () => {
  assert.deepEqual([undefined,null,'',false,0,{nested:'值'}].map(previewValue),['未输出','NULL','空字符串','false','0','{"nested":"值"}']);
  assert.equal(previewCell({},'toString'),undefined);
  assert.deepEqual(previewCell(JSON.parse('{"__proto__":{"value":1}}'),'__proto__'),{value:1});
});
test('governance naming preserves DSL identity, compile configuration and custom node labels', () => {
  for(const key of ['transform.field-mapping','transform.field-enrichment']) {
    const manifest={key,category:'transform',label:'字段映射',fields:[{key:'mappings',type:'field-mapping-dsl',label:'字段映射规则',default:'original DSL'}],compile:{inlet:'map'}};
    const shown=governanceManifest(manifest);
    assert.equal(shown.key,key);assert.equal(shown.label,'字段治理');assert.equal(shown.fields[0].label,'治理规则');
    assert.equal(shown.fields[0].default,manifest.fields[0].default);assert.equal(shown.compile,manifest.compile);assert.equal(manifest.label,'字段映射');
    assert.equal(governanceNodeLabel(key,'字段映射'),'字段治理');assert.equal(governanceNodeLabel(key,'人员字段映射'),'人员字段映射');
  }
  const other={key:'transform.sql',category:'transform',label:'自定义 SQL'};assert.equal(governanceManifest(other),other);
  assert.equal(governanceNodeLabel('transform.sql','字段映射'),'字段映射');
});
