import {describe, expect, it} from 'vitest';
import {initialTables, type TableDef} from '../src/data';
import {compileSchemaDraft, createSchemaDraft, schemaDraftChanged, type SchemaFieldDraft} from '../src/schemaEditing';
const table = (): TableDef => structuredClone(initialTables.users);
function edit(patch: Partial<SchemaFieldDraft>, index = 1, source = table()) {
  const draft = createSchemaDraft(source); Object.assign(draft[index], patch);
  return compileSchemaDraft(source, draft);
}
describe('schema editing', () => {
  it('parses independent type, length, unsigned and default presence', () => {
    const fields = createSchemaDraft(initialTables.orders);
    expect(fields[0]).toMatchObject({type: 'INT', length: '', unsigned: true, nullable: false});
    expect(fields[3]).toMatchObject({type: 'DECIMAL', length: '10,2'});
    expect(fields[1].hasDefault).toBe(false);
    expect(fields[4]).toMatchObject({hasDefault: true, defaultValue: 'pending'});
  });
  it('validates every bundled table unchanged', () => {
    for (const source of Object.values(initialTables)) {
      expect(schemaDraftChanged(source, createSchemaDraft(source))).toBe(false);
      const result = compileSchemaDraft(source, createSchemaDraft(source));
      expect(result.ok).toBe(true);
      if (result.ok) expect(result.table.rows).toEqual(source.rows);
    }
  });
  it('detects undo and defaults differing from empty string', () => {
    const source = table(); const draft = createSchemaDraft(source);
    draft[1].hasDefault = true;
    expect(schemaDraftChanged(source, draft)).toBe(true);
    const result = compileSchemaDraft(source, draft);
    expect(result.ok && result.table.columns[1].default).toBe('');
    draft[1].hasDefault = false;
    expect(schemaDraftChanged(source, draft)).toBe(false);
  });
  it('renames atomically including swaps and keeps source immutable', () => {
    const source = table(); const before = structuredClone(source); const draft = createSchemaDraft(source);
    draft[1].name = 'email'; draft[2].name = 'name';
    const result = compileSchemaDraft(source, draft);
    expect(result.ok).toBe(true);
    if (result.ok) { expect(result.table.rows[0].email).toBe(source.rows[0].name); expect(result.table.rows[0].name).toBe(source.rows[0].email); expect(result.table.rows[0]).not.toBe(source.rows[0]); }
    expect(source).toEqual(before);
  });
  it.each([{name: 'email'}, {name: 'EMAIL'}, {name: 'bad name'}, {name: '1name'}, {name: 'a'.repeat(65)}, {type: 'UNKNOWN'}, {type: 'INT', length: ''}, {length: '1'}, {length: '0'}, {length: '16384'}, {length: '2.5'}, {unsigned: true}, {primary: true, nullable: true}])('rejects invalid change %j', patch => expect(edit(patch).ok).toBe(false));
  it('accepts unicode identifiers and measures characters rather than UTF-16 units', () => {
    const source = table(); source.rows = [{...source.rows[0], name: '😀'}];
    expect(edit({name: '姓名', length: '1'}, 1, source).ok).toBe(true);
  });
  it('rejects missing fields, forged source identities and duplicate keys', () => {
    const source = table(); const draft = createSchemaDraft(source);
    expect(compileSchemaDraft(source, draft.slice(1)).ok).toBe(false);
    draft[1].sourceName = 'email'; expect(compileSchemaDraft(source, draft).ok).toBe(false);
    draft[1] = {...draft[0]}; expect(compileSchemaDraft(source, draft).ok).toBe(false);
  });
  it.each([{name: 'key'}, {type: 'VARCHAR', length: '30'}, {nullable: true}])('protects id: %j', patch => expect(edit(patch, 0).ok).toBe(false));
  it('rejects unsafe numeric and unsigned narrowing without converting', () => {
    const source = structuredClone(initialTables.orders);
    expect(edit({type: 'TINYINT'}, 0, source).ok).toBe(false);
    source.rows[0].user_id = -1; expect(edit({}, 1, source).ok).toBe(false);
    source.rows[0].user_id = '1'; expect(edit({}, 1, source).ok).toBe(false);
    source.rows[0].user_id = Number.MAX_SAFE_INTEGER + 1; expect(edit({type: 'BIGINT'}, 1, source).ok).toBe(false);
  });
  it.each(['2,0', '10,0', '66,2', '4,5', '40,31', 'abc'])('checks decimal %s', length => {
    const source = structuredClone(initialTables.orders); source.rows[0].amount = 123.45;
    expect(edit({length}, 3, source).ok).toBe(false);
  });
  it('accepts exact decimal widths, including scientific notation', () => {
    const source = structuredClone(initialTables.orders); source.rows = [{...source.rows[0], amount: 1e-7}];
    expect(edit({length: '8,7'}, 3, source).ok).toBe(true);
    expect(edit({length: '8,6'}, 3, source).ok).toBe(false);
    source.rows[0].amount = 0; expect(edit({length: '2,2'}, 3, source).ok).toBe(true);
  });
  it('rejects NULL on non-nullable fields and invalid defaults', () => {
    expect(edit({nullable: false}, 2, structuredClone(initialTables.categories)).ok).toBe(false);
    expect(edit({hasDefault: true, defaultValue: ''}, 0).ok).toBe(false);
    expect(edit({hasDefault: true, defaultValue: 'abc'}, 0).ok).toBe(false);
    expect(edit({hasDefault: true, defaultValue: '999'}, 0).ok).toBe(true);
    expect(edit({length: '4', hasDefault: true, defaultValue: '12345'}).ok).toBe(false);
  });
  it('checks composite uniqueness rather than individual primary values', () => {
    const source: TableDef = {name: 'pairs', comment: '', columns: [{name: 'a', type: 'int'}, {name: 'b', type: 'int'}], rows: [{a: 1, b: 1}, {a: 1, b: 2}]};
    const draft = createSchemaDraft(source); draft.forEach(field => {field.primary = true; field.nullable = false;});
    expect(compileSchemaDraft(source, draft).ok).toBe(true);
    source.rows[1].b = 1; expect(compileSchemaDraft(source, draft).ok).toBe(false);
  });
  it('rejects invalid real dates and accepts leap dates', () => {
    const source = table(); source.rows = [{...source.rows[0], created_at: '2024-02-29 23:59:59'}];
    expect(edit({}, 5, source).ok).toBe(true);
    source.rows[0].created_at = '2023-02-29 12:00:00'; expect(edit({}, 5, source).ok).toBe(false);
  });
  it('prevents collisions with extra row data', () => {
    const source = table(); source.rows[0].extra = 'keep';
    expect(edit({name: 'extra'}, 1, source).ok).toBe(false);
  });
  it('rejects default precision loss and FLOAT rounding', () => {
    const source = structuredClone(initialTables.orders); source.rows = [{...source.rows[0], amount: 123.45}];
    expect(edit({type: 'FLOAT', length: ''}, 3, source).ok).toBe(false);
    expect(edit({length: '30,20', hasDefault: true, defaultValue: '0.12345678901234567890'}, 3, source).ok).toBe(false);
    expect(edit({hasDefault: true, defaultValue: '00123.4500'}, 3, source).ok).toBe(true);
  });
  it('uses existing non-null semantics for unspecified nullable flags', () => {
    const draft = createSchemaDraft(table());
    expect(draft[3].nullable).toBe(false);
  });
  it('reserves NULL defaults for nullable non-primary fields', () => {
    expect(edit({hasDefault: true, defaultValue: 'NULL'}).ok).toBe(false);
    expect(edit({hasDefault: true, defaultValue: 'null', nullable: true}).ok).toBe(true);
    expect(edit({hasDefault: true, defaultValue: 'NULL', primary: true, nullable: false}).ok).toBe(false);
    const source = structuredClone(initialTables.orders);
    expect(edit({hasDefault: true, defaultValue: 'NULL', nullable: true}, 3, source).ok).toBe(true);
  });
  it('accepts timestamp expressions case-insensitively only on temporal fields', () => {
    expect(edit({hasDefault: true, defaultValue: 'current_timestamp'}, 5).ok).toBe(true);
    expect(edit({hasDefault: true, defaultValue: ' CURRENT_TIMESTAMP '}, 5).ok).toBe(false);
    expect(edit({hasDefault: true, defaultValue: 'CURRENT_TIMESTAMP'}).ok).toBe(true);
    expect(edit({hasDefault: true, defaultValue: 'CURRENT_TIMESTAMP'}, 0).ok).toBe(false);
  });
  it('supports typed boolean default expressions and literal text words', () => {
    expect(edit({hasDefault: true, defaultValue: 'true'}, 0).ok).toBe(true);
    expect(edit({hasDefault: true, defaultValue: 'FALSE'}).ok).toBe(true);
    const source: TableDef = {name: 'flags', comment: '', columns: [{name: 'enabled', type: 'boolean'}], rows: [{enabled: 1}]};
    expect(edit({hasDefault: true, defaultValue: 'false'}, 0, source).ok).toBe(true);
  });
  it('returns a field-specific diagnostic without modifying inputs', () => {
    const source = table(); const draft = createSchemaDraft(source); draft[1].length = '1'; const before = structuredClone(draft);
    expect(compileSchemaDraft(source, draft)).toMatchObject({ok: false, fieldKey: draft[1].key, property: 'type'});
    expect(draft).toEqual(before);
  });
});
