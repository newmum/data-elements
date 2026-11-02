import test from 'node:test';
import assert from 'node:assert/strict';
import { parseSource } from '../../src/report/core/import';
import { parseJsonRecords } from '../../src/report/core/textFormats';
import { normalizedReportFile, tableResource, type UploadTable } from '../../src/report/services/uploadWorkbench';

test('TXT detects tab, comma, semicolon and pipe outside quoted headers; preserves multiline cells and IDs', async () => {
  for (const separator of ['\t', ',', ';', '|']) {
    const book = await parseSource(new File([`id${separator}"备注,说明"\r\n001${separator}"第一行\n第二行"`], 'validation.TXT'));
    assert.equal(book.sheets[0].name, 'validation');
    assert.deepEqual(book.sheets[0].cells.map(row => row.map(cell => cell.v)), [['id', '备注,说明'], ['001', '第一行\n第二行']]);
  }
  const leading = await parseSource(new File(['\n \r\n||\r\nid|remark\n001|value'], 'validation.txt'));
  assert.deepEqual(leading.sheets[0].cells[3].map(cell => cell.v), ['id','remark']);
  assert.deepEqual(leading.sheets[0].cells[4].map(cell => cell.v), ['001','value']);
});

test('TXT supports explicit GB18030 decoding and manual delimiters', async () => {
  // GB18030 bytes for 姓名, with an ASCII identifier column.
  const bytes = new Uint8Array([0xd0, 0xd5, 0xc3, 0xfb, 0x7c, 0x69, 0x64, 0x0a, 0x41, 0x7c, 0x30, 0x30, 0x31]);
  const book = await parseSource(new File([bytes], 'validation.txt'), 'gb18030', '|');
  assert.equal(book.sheets[0].cells[0][0].v, '姓名');
  assert.equal(book.sheets[0].cells[1][1].v, '001');
});

test('JSON supports flat records and common record envelopes, unions fields and preserves numeric lexemes', () => {
  const records = '[{"id":12345678901234567890,"备注":"001","enabled":true,"optional":null},{"id":-1.234567890123456789e+20,"extra":"补充"}]';
  for (const source of [records, ...['data', 'rows', 'list'].map(key => `{"${key}":${records},"total":2}`)]) {
    assert.deepEqual(parseJsonRecords(source), [
      ['id', '备注', 'enabled', 'optional', 'extra'],
      ['12345678901234567890', '001', 'true', '', ''],
      ['-1.234567890123456789e+20', '', '', '', '补充'],
    ]);
  }
  const escaped = 'escaped "12" and \\path';
  assert.deepEqual(parseJsonRecords('\uFEFF'+JSON.stringify({id:123,text:escaped})), [['id', 'text'], ['123', escaped]]);
});

test('JSON rejects invalid syntax, ambiguous arrays, nested fields and unsafe resource sizes with actionable errors', () => {
  for (const source of ['[{1:2}]', '[{"id":01}]', '[{"id":1},]', '[{"id":NaN}]', '[{"id":1}] true']) assert.throws(() => parseJsonRecords(source), /JSON 格式不正确/);
  assert.throws(() => parseJsonRecords('{"data":[],"rows":[]}'), /多个记录数组/);
  assert.throws(() => parseJsonRecords('[{"info":{"id":1}}]'), /第 1 条.*info.*嵌套结构/);
  assert.throws(() => parseJsonRecords('[[1,2]]'), /第 1 条.*对象/);
  assert.throws(() => parseJsonRecords('[]'), /至少一条/);
  assert.throws(() => parseJsonRecords('[{}]'), /没有字段/);
  assert.throws(() => parseJsonRecords(JSON.stringify(Array.from({length:20001}, () => ({id:'x'})))), /20000/);
  assert.deepEqual(parseJsonRecords('[{"__proto__":"own","constructor":"safe"},{}]'), [['__proto__', 'constructor'], ['own', 'safe'], ['', '']]);
  assert.deepEqual(parseJsonRecords('[{"id":"a"},{"2":"two","1":"one","01":"zero-one","4294967295":"text"}]')[0], ['id','1','2','01','4294967295']);
});

test('oversized JSON object collections stop before allocating the object graph', t => {
  const original = JSON.parse;
  let calls = 0;
  t.mock.method(JSON, 'parse', (text: string) => { calls++; return original(text); });
  assert.throws(() => parseJsonRecords('['+'{},'.repeat(40002)+'{}]'), /对象数量过多/);
  assert.equal(calls, 0);
});

test('TXT and JSON feed the same registered-table preview and normalized CSV delivery without precision loss', async () => {
  for (const [name, contents] of [['validation.txt', 'id|remark\n12345678901234567890|001'], ['validation.json', '[{"id":12345678901234567890,"remark":"001"}]']]) {
    const book = await parseSource(new File([contents], name));
    const table: UploadTable = {key:'validation',fileId:'file',fileName:name,sheetName:'validation',tableName:'validation_text_formats',tableNameCn:'文本格式验证',fieldCount:2,recordCount:1,status:'success',startRow:1,endRow:2,startColumn:1,endColumn:2,fields:['id','remark'].map(columnName => ({columnName,columnComment:columnName,columnType:'varchar',primaryKey:columnName==='id',nullable:false,isTimestamp:false}))};
    const resource = tableResource(book, table);
    const file = normalizedReportFile(resource);
    assert.equal(file.name, 'validation_text_formats.csv');
    assert.match(await file.text(), /"12345678901234567890","001"/);
  }
});

test('JSON requires UTF-8, while malformed TXT quotes and oversized text matrices stop before registration', async () => {
  await assert.rejects(parseSource(new File([new Uint8Array([0xff,0xfe])], 'validation.json')), /UTF-8/);
  await assert.rejects(parseSource(new File(['id|name\n1|"unclosed'], 'validation.txt')), /TXT 解析失败/);
  await assert.rejects(parseSource(new File([Array.from({length:201},(_,i)=>`c${i}`).join('|')+'\n1'], 'validation.txt')), /200 列/);
});
