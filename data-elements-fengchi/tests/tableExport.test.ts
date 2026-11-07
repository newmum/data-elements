import { describe, expect, it, vi } from 'vitest';
import type { Row } from '../src/data';
import { buildExportFilename, createExportSnapshot, prepareTableExport, validateExportCount, type ExportFormat } from '../src/tableExport';

const rows: Row[] = [
  { id: 3, name: '林知远', amount: -10.25, note: null, private: 'not selected' },
  { id: 1, name: '含,逗号', amount: 0, note: '第一行\r\n第二行' },
  { id: 2, name: '"引号"', amount: 18, note: '制表\t内容' },
];
const columns = ['id', 'name', 'amount', 'note'];
const source = { key: 'filtered', label: '筛选结果', rows };
const prepare = (format: ExportFormat, quantity: 'all' | string | number = 'all') => prepareTableExport({ title: '用户数据', columns, source, format, quantity });

describe('export count validation', () => {
  it('supports all or an exact positive integer up to the available count', () => {
    expect(validateExportCount('all', 3)).toEqual({ ok: true, count: 3 });
    expect(validateExportCount(1, 3)).toEqual({ ok: true, count: 1 });
    expect(validateExportCount('3', 3)).toEqual({ ok: true, count: 3 });
  });
  it.each(['', ' ', '1.5', '2e0', '0x2', '+1', '-1', 'NaN', 'Infinity', '0', '4', 0, -1, 1.5, 4, NaN, Infinity, 9007199254740992])('rejects invalid count %s without clamping or rounding', quantity => {
    expect(validateExportCount(quantity, 3).ok).toBe(false);
    expect(() => prepare('csv', quantity)).toThrow('1 到 3');
  });
  it('disables every quantity when the selected source is empty', () => {
    for (const quantity of ['all', 1, '']) expect(validateExportCount(quantity, 0)).toEqual({ ok: false, error: '此范围内没有可导出的记录。' });
    expect(() => prepareTableExport({ title: 'empty', columns, source: { ...source, rows: [] }, format: 'csv' })).toThrow('没有可导出');
  });
  it('rejects unavailable columns and unknown runtime formats', () => {
    expect(() => prepareTableExport({ title: 'empty', columns: [], source, format: 'json' })).toThrow('没有可导出的字段');
    expect(() => prepareTableExport({ title: 'users', columns, source, format: 'xlsx' as ExportFormat })).toThrow('不支持');
  });
});

describe('CSV and TSV export', () => {
  it('serializes headers, ordered rows, Chinese, quotes and multiline CSV with UTF-8 BOM', () => {
    const file = prepare('csv');
    expect(file).toEqual({
      filename: '用户数据.csv', mime: 'text/csv;charset=utf-8', count: 3, scope: 'filtered', format: 'csv',
      content: '\uFEFFid,name,amount,note\r\n3,林知远,-10.25,\r\n1,"含,逗号",0,"第一行\r\n第二行"\r\n2,"""引号""",18,制表\t内容\r\n',
    });
  });
  it('uses real tab delimiters and quotes tabs, quotes and multiline TSV', () => {
    const file = prepare('tsv');
    expect(file.filename).toBe('用户数据.tsv');
    expect(file.mime).toBe('text/tab-separated-values;charset=utf-8');
    expect(file.content).toBe('\uFEFFid\tname\tamount\tnote\r\n3\t林知远\t-10.25\t\r\n1\t含,逗号\t0\t"第一行\r\n第二行"\r\n2\t"""引号"""\t18\t"制表\t内容"\r\n');
  });
  it.each(['csv', 'tsv'] as const)('exports only the first requested rows, keeping source order for %s', format => {
    const file = prepare(format, 1);
    expect(file.count).toBe(1);
    expect(file.content).toContain('林知远');
    expect(file.content).not.toContain('含,逗号');
    expect(file.content).not.toContain('private');
  });
  it.each(['csv', 'tsv'] as const)('protects formula strings and headers while preserving numeric values in %s', format => {
    const values: Row[] = ['=1+1', '+1', '-10', '@SUM(A1)', '\tformula', '\rformula', '\nformula', '  =1+1', '\uFEFF=1+1'].map(value => ({ value }));
    const file = prepareTableExport({ title: 'formulas', columns: ['value'], source: { ...source, rows: [...values, { value: -10 }, { value: 12 }] }, format });
    for (const row of values) expect(file.content).toContain(`'${row.value}`);
    expect(file.content).toContain('\r\n-10\r\n12\r\n');
    const header = prepareTableExport({ title: 'header', columns: ['=HYPERLINK("x")'], source: { ...source, rows: [{}] }, format });
    expect(header.content).toContain('"\'=HYPERLINK(""x"")"');
  });
  it('documents the delimited NULL policy by emitting empty cells for NULL and empty text', () => {
    const file = prepareTableExport({ title: 'nulls', columns: ['null', 'empty', 'missing', 'literal'], source: { ...source, rows: [{ null: null, empty: '', literal: 'NULL' }] }, format: 'csv' });
    expect(file.content).toBe('\uFEFFnull,empty,missing,literal\r\n,,,NULL\r\n');
  });
  it.each(['csv', 'tsv'] as const)('encodes an actual UTF-8 %s Blob with BOM and Chinese bytes', async format => {
    const file = prepare(format);
    const bytes = new Uint8Array(await new Blob([file.content], { type: file.mime }).arrayBuffer());
    expect([...bytes.slice(0, 3)]).toEqual([239, 187, 191]);
    expect(new TextDecoder().decode(bytes)).toContain('林知远');
  });
});

describe('JSON export', () => {
  it('produces parseable JSON without a BOM and preserves types and NULL', () => {
    const file = prepare('json');
    expect(file.filename).toBe('用户数据.json');
    expect(file.mime).toBe('application/json;charset=utf-8');
    expect(file.content.startsWith('[')).toBe(true);
    expect(JSON.parse(file.content)).toEqual(rows.map(({ id, name, amount, note }) => ({ id, name, amount, note })));
    expect(file.content).not.toContain('private');
  });
  it('leaves formula-like strings untouched and keeps empty strings distinct from NULL', () => {
    const file = prepareTableExport({ title: 'values', columns: ['value', 'empty', 'null', 'missing'], source: { ...source, rows: [{ value: '=1+1', empty: '', null: null }] }, format: 'json' });
    expect(JSON.parse(file.content)).toEqual([{ value: '=1+1', empty: '', null: null, missing: null }]);
  });
  it('keeps special property names as ordinary data keys', () => {
    const row = Object.fromEntries([['__proto__', 'value'], ['constructor', 'constructor value']]);
    const file = prepareTableExport({ title: 'special', columns: ['__proto__', 'constructor'], source: { ...source, rows: [row] }, format: 'json' });
    expect(JSON.parse(file.content)[0]).toEqual(row);
  });
  it('treats missing prototype-named fields as NULL instead of inherited properties', () => {
    const options = { title: 'missing', columns: ['__proto__', 'constructor'], source: { ...source, rows: [{}] } };
    expect(prepareTableExport({ ...options, format: 'json' }).content).toContain('"__proto__": null');
    expect(prepareTableExport({ ...options, format: 'json' }).content).toContain('"constructor": null');
    expect(prepareTableExport({ ...options, format: 'csv' }).content).toBe('\uFEFF__proto__,constructor\r\n,\r\n');
  });
  it.each(['csv', 'tsv', 'json'] as const)('rejects non-finite numbers rather than silently losing them in %s', format => {
    for (const value of [NaN, Infinity, -Infinity]) expect(() => prepareTableExport({ title: 'invalid', columns: ['value'], source: { ...source, rows: [{ value }] }, format })).toThrow('无效数字');
  });
});

describe('export snapshots and filenames', () => {
  it('copies column, source and row values without mutating caller data', () => {
    const input = { title: 'snapshot', columns: ['id', 'name'], sources: [{ key: 'selected', label: '已选记录', rows: [{ id: 1, name: '原始记录' }] }] };
    const snapshot = createExportSnapshot(input);
    input.columns.push('new');
    input.sources[0].label = 'changed';
    input.sources[0].rows[0].name = '后续修改';
    input.sources[0].rows.push({ id: 2, name: '后来添加' });
    expect(snapshot.columns).toEqual(['id', 'name']);
    expect(snapshot.sources[0]).toEqual({ key: 'selected', label: '已选记录', rows: [{ id: 1, name: '原始记录' }] });
    const file = prepareTableExport({ ...snapshot, source: snapshot.sources[0], format: 'json', quantity: 1 });
    expect(JSON.parse(file.content)).toEqual([{ id: 1, name: '原始记录' }]);
    expect(createExportSnapshot(input).sources[0].rows).toHaveLength(2);
  });
  it('never broadens an explicitly selected scope to a nonempty alternative', () => {
    const sources = [{ ...source, key: 'all' }, { key: 'selected', label: '已选记录', rows: [] }];
    expect(() => prepareTableExport({ title: 'selection', columns, source: sources[1], format: 'csv' })).toThrow('没有可导出');
  });
  it('sanitizes unsafe names while preserving readable names and correct extensions', () => {
    expect(buildExportFilename('../../用户\\资料:*?\u0000', 'csv')).toBe('_.._用户_资料____.csv');
    expect(buildExportFilename('  .. ', 'json')).toBe('data.json');
    expect(buildExportFilename('CON', 'tsv')).toBe('_CON.tsv');
    expect(buildExportFilename('aux.txt', 'json')).toBe('_aux.txt.json');
    expect(buildExportFilename(' users. ', 'csv')).toBe('users.csv');
    expect(buildExportFilename('a'.repeat(400), 'csv')).toHaveLength(104);
  });
  it.each([20, 100])('does not make any read requests for %i already loaded records', count => {
    const fetch = vi.fn();
    vi.stubGlobal('fetch', fetch);
    try {
      const file = prepareTableExport({ title: 'loaded', columns: ['id'], source: { ...source, rows: Array.from({ length: count }, (_, id) => ({ id })) }, format: 'json' });
      expect(file.count).toBe(count);
      expect(fetch).not.toHaveBeenCalled();
    } finally {
      vi.unstubAllGlobals();
    }
  });
});
