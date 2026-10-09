import test from 'node:test';
import assert from 'node:assert/strict';
import { isRecordLookup, previewInlineRecordLookup } from '../utils/recordLookup.ts';

test('generated standard and dictionary multi-value contracts survive JSON round trips', () => {
  for (const lookup of [
    { multiValue: true, values: { U: '未知', F: '女' }, multiValueSeparator: ',' },
    { multiValue: true, query: 'SELECT code,label FROM dictionary WHERE code IN (:codes)', dataSource: { datasourceId: 'oracle-dictionary' } },
  ]) {
    assert.equal(isRecordLookup(JSON.parse(JSON.stringify(lookup))), true);
  }
  assert.equal(isRecordLookup({ sql: 'SELECT label FROM dictionary WHERE code=?' }), false);
});

test('multi-value preview preserves order, repeats, unmatched codes and literal separators', () => {
  for (const separator of [',', '|', '.', '\\', '"']) {
    const lookup = { multiValue: true, values: { F: '女', U: '未知' }, multiValueSeparator: separator };
    assert.equal(previewInlineRecordLookup(` F${separator}U${separator}F${separator}X${separator} `, lookup), ['女', '未知', '女', 'X'].join(separator));
    assert.equal(previewInlineRecordLookup('X', lookup), null);
    assert.equal(previewInlineRecordLookup(null, lookup), null);
    assert.equal(previewInlineRecordLookup('constructor', lookup), null);
  }
});

test('unmatched policies use the same result as backend preview', () => {
  const lookup = { multiValue: true, values: { F: '女' } };
  assert.equal(previewInlineRecordLookup(' X ', { ...lookup, onMissing: 'KEEP_SOURCE' }), ' X ');
  assert.throws(() => previewInlineRecordLookup('X', { ...lookup, onMissing: 'FAIL' }), /没有匹配/);
});
