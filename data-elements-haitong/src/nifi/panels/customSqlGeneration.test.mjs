import test from 'node:test';
import assert from 'node:assert/strict';
import { EditorState } from '@codemirror/state';
import { history, undo, redo } from '@codemirror/commands';
import { generateSelectSql, quoteSqlIdentifier } from './customSqlGeneration.ts';
import { insertSqlField, replaceSqlStatement } from './sqlEditorTransactions.ts';

const columns = (...names) => names.map((name) => ({ name }));
function editor(doc, selection) {
  const editor = {
    state: EditorState.create({ doc, selection, extensions: [history()] }),
    dispatch: (transaction) => { editor.state = transaction.state; },
    apply: (spec) => editor.dispatch(editor.state.update(spec)),
    text: () => editor.state.doc.toString(),
  };
  return editor;
}

test('chip insertion uses caret after SELECT and preserves the FROM suffix', () => {
  const e = editor('SELECT\nFROM source_table;', { anchor: 6 });
  e.apply(insertSqlField(e.state, 'data_type'));
  assert.equal(e.text(), 'SELECT data_type, \nFROM source_table;');
  e.apply(insertSqlField(e.state, 'tid'));
  assert.equal(e.text(), 'SELECT data_type, tid, \nFROM source_table;');
  assert.ok(undo(e));
  assert.equal(e.text(), 'SELECT data_type, \nFROM source_table;');
  assert.ok(undo(e));
  assert.equal(e.text(), 'SELECT\nFROM source_table;');
});

test('chip replaces selection without duplicating an existing comma', () => {
  const e = editor('SELECT old, next FROM t;', { anchor: 7, head: 10 });
  e.apply(insertSqlField(e.state, 'new_field'));
  assert.equal(e.text(), 'SELECT new_field, next FROM t;');
  e.apply(insertSqlField(e.state, 'second'));
  assert.equal(e.text(), 'SELECT new_field, second, next FROM t;');
});

test('one undo restores the entire previous SQL, redo restores generation', () => {
  const original = 'WITH a AS (SELECT 1 AS id)\nSELECT id FROM a;';
  const e = editor(original);
  e.apply({ changes: { from: e.state.doc.length, insert: '\n-- user edit' }, userEvent: 'input.type' });
  const before = e.text();
  const generated = generateSelectSql('app.logs', columns('case_id'), columns('CASE_ID'), 'mysql').sql;
  e.apply(replaceSqlStatement(e.state, generated));
  assert.equal(e.text(), generated);
  assert.ok(undo(e));
  assert.equal(e.text(), before);
  assert.ok(redo(e));
  assert.equal(e.text(), generated);
  e.apply({ changes: { from: e.state.doc.length, insert: '\n-- after' }, userEvent: 'input.type' });
  assert.ok(undo(e));
  assert.equal(e.text(), generated);
  assert.ok(undo(e));
  assert.equal(e.text(), before);
});

test('repeated generation creates independent undo steps', () => {
  const e = editor('original');
  e.apply(replaceSqlStatement(e.state, 'SELECT a FROM a;'));
  e.apply(replaceSqlStatement(e.state, 'SELECT b FROM b;'));
  undo(e);
  assert.equal(e.text(), 'SELECT a FROM a;');
  undo(e);
  assert.equal(e.text(), 'original');
});

test('both case directions preserve source names and exact target aliases, without aliases for unmatched fields', () => {
  for (const [source, target] of [['CASE_ID', 'case_id'], ['case_id', 'CASE_ID']]) {
    const result = generateSelectSql('log_table', columns(source, 'extra'), columns(target), 'mysql');
    assert.equal(result.sql, `SELECT\n  \`${source}\` AS \`${target}\`,\n  \`extra\`\nFROM \`log_table\`;`);
    assert.equal(result.mappedCount, 1);
    assert.deepEqual(result.unmatched, ['extra']);
  }
});

test('physical name matches take priority; unique recommendations map renamed fields', () => {
  const result = generateSelectSql('t', columns('case_id', 'operator'), columns('CASE_ID', 'USER_NAME'), 'DM', [
    { from: '/case_id', to: '/USER_NAME', confidence: 1 },
    { from: '/operator', to: '/USER_NAME', confidence: 1 },
  ]);
  assert.equal(result.mappedCount, 2);
  assert.match(result.sql, /"case_id" AS "CASE_ID"/);
  assert.match(result.sql, /"operator" AS "USER_NAME"/);
});

test('ambiguous names, competing recommendations, constants and weak matches are excluded', () => {
  const result = generateSelectSql('t', columns('a', 'b', 'ID', 'id', 'weak'), columns('x', 'id', 'z'), 'hive', [
    { from: '/a', to: '/x', confidence: 1 }, { from: '/b', to: '/x', confidence: 1 },
    { from: '/ID', to: '/id', confidence: 1 }, { from: '', to: '/z', confidence: 1 },
    { from: '/weak', to: '/z', confidence: 0.5 },
  ]);
  assert.equal(result.mappedCount, 0);
  assert.deepEqual(result.unmatched, ['a', 'b', 'ID', 'id', 'weak']);
});

test('source dialect controls quoting including Hive, Oracle-mode OceanBase and qualified names', () => {
  assert.equal(quoteSqlIdentifier('a`b', 'hive'), '`a``b`');
  assert.equal(quoteSqlIdentifier('a"b', 'oceanbase oracle'), '"a""b"');
  assert.equal(quoteSqlIdentifier('a]b', 'sqlserver'), '[a]]b]');
  assert.match(generateSelectSql('"schema"."a.b"', columns('select'), [], 'oracle').sql, /FROM "schema"\."a.b";/);
  assert.match(generateSelectSql('db.logs', columns('case_id'), columns('CASE_ID'), 'hive').sql, /FROM `db`\.`logs`;/);
});

test('upstream record output uses FLOWFILE and quoted aliases', () => {
  const result = generateSelectSql('加工结果', columns('case_id'), columns('CASE_ID'), 'mysql', [], true);
  assert.equal(result.sql, 'SELECT\n  "case_id" AS "CASE_ID"\nFROM FLOWFILE;');
});

test('missing source metadata does not generate a fake query', () => {
  assert.throws(() => generateSelectSql('t', [], [], 'mysql'), /来源字段为空/);
  assert.throws(() => generateSelectSql('', columns('id'), [], 'mysql'), /来源表/);
});
