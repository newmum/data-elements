import { describe, expect, it, vi } from 'vitest';
import { selectedActionRows, sortTableRows, withoutRows } from '../src/tableRowActions';
import { initialTables, type Row } from '../src/data';

describe('explicit table row actions', () => {
  const rows: Row[] = [{ id: 1, name: 'B' }, { id: 2, name: 'A' }, { id: '1', name: 'C' }];
  it('does not delete the initially highlighted inspector record without selection', () => {
    expect(selectedActionRows(rows, [], [])).toEqual({ source: 'none', rows: [] });
  });
  it('prefers checked records to a cell range, without coercing IDs', () => {
    expect(selectedActionRows(rows, [1], [2]).rows).toEqual([rows[0]]);
    expect(selectedActionRows(rows, ['1'], []).rows).toEqual([rows[2]]);
  });
  it('maps a single or rectangular cell selection to whole distinct records', () => {
    expect(selectedActionRows(rows, [], [1]).rows).toEqual([rows[0]]);
    expect(selectedActionRows(rows, [], [2, 1, 2]).rows).toEqual(rows.slice(0, 2));
  });
  it('never silently falls back from stale checkboxes or includes filtered-out records', () => {
    expect(selectedActionRows(rows, [999], [1]).rows).toEqual([]);
    expect(selectedActionRows(rows.slice(1), [1, 2], []).rows).toEqual([rows[1]]);
  });
  it('can explicitly use the current form record', () => {
    expect(selectedActionRows(rows, [], [], 2)).toEqual({ source: 'form', rows: [rows[1]] });
  });
  it('stages exact deletions without mutating the committed baseline', () => {
    const original = structuredClone(initialTables.users);
    const next = withoutRows(original, [1, 3, 999]);
    expect(next.rows).toHaveLength(22);
    expect(next.rows.some(row => row.id === 1 || row.id === 3)).toBe(false);
    expect(original.rows).toHaveLength(24);
    expect(withoutRows(original, [999])).toBe(original);
  });
  it('uses stable sorted order for displayed pages and limited exports', () => {
    expect(sortTableRows(rows, { field: 'name', order: 'ascend' }).map(row => row.id)).toEqual([2, 1, '1']);
    expect(sortTableRows(rows, { field: 'name', order: 'descend' }).slice(0, 2).map(row => row.id)).toEqual(['1', 1]);
    expect(rows.map(row => row.id)).toEqual([1, 2, '1']);
  });
  it.each([20, 100])('processes %i records locally with no business network calls', count => {
    const fetch = vi.fn(); vi.stubGlobal('fetch', fetch);
    try {
      const items = Array.from({ length: count }, (_, index) => ({ id: index + 1, name: `row-${index}` }));
      const result = selectedActionRows(items, items.map(row => row.id), []);
      expect(result.rows).toHaveLength(count);
      expect(sortTableRows(items, { field: 'id', order: 'descend' })[0].id).toBe(count);
      expect(fetch).not.toHaveBeenCalled();
    } finally { vi.unstubAllGlobals(); }
  });
});
