import {describe, expect, it} from 'vitest';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import {isCellChanged} from '../src/cellChanges';
import RecordFormView from '../src/RecordFormView';
import type {Row, TableDef} from '../src/data';

const original: Row = {id: 1, name: 'Original', note: null, amount: 0};
const baseline: TableDef = {
  name: 'items', comment: '', rows: [original],
  columns: [{name: 'id', type: 'int', primary: true}, {name: 'name', type: 'varchar(80)'}, {name: 'note', type: 'text', nullable: true}, {name: 'amount', type: 'decimal(10,2)'}],
};

describe('committed cell comparisons', () => {
  it('marks only edited cells and clears the mark when restored', () => {
    const edited = {...original, name: 'Edited'};
    expect(isCellChanged(baseline, edited, 'name')).toBe(true);
    for (const column of ['id', 'note', 'amount']) expect(isCellChanged(baseline, edited, column)).toBe(false);
    expect(isCellChanged(baseline, {...edited, name: original.name}, 'name')).toBe(false);
    expect(isCellChanged({...baseline, rows: [edited]}, edited, 'name')).toBe(false);
  });
  it('distinguishes null, empty strings, numbers, and strings without normalization', () => {
    expect(isCellChanged(baseline, {...original, note: ''}, 'note')).toBe(true);
    expect(isCellChanged(baseline, {...original, amount: '0'}, 'amount')).toBe(true);
    expect(isCellChanged(baseline, {...original, name: ' Original '}, 'name')).toBe(true);
    expect(isCellChanged(baseline, {...original, amount: -0}, 'amount')).toBe(true);
    expect(isCellChanged({...baseline, rows: [{...original, amount: NaN}]}, {...original, amount: NaN}, 'amount')).toBe(false);
  });
  it('marks every present field in newly added rows or tables', () => {
    for (const column of Object.keys(original)) {
      expect(isCellChanged(undefined, original, column)).toBe(true);
      expect(isCellChanged({...baseline, rows: []}, original, column)).toBe(true);
    }
    expect(isCellChanged(undefined, original, 'missing')).toBe(false);
    expect(isCellChanged(baseline, {...original, added: null}, 'added')).toBe(true);
  });
  it('matches rows by original primary key rather than order or current ID', () => {
    const edited = {...original, id: 2};
    const other = {...original, id: 2, name: 'Other'};
    const reordered = {...baseline, rows: [other, original]};
    expect(isCellChanged(reordered, edited, 'id', 1)).toBe(true);
    expect(isCellChanged(reordered, edited, 'name', 1)).toBe(false);
    expect(isCellChanged(reordered, {...edited, name: 'Edited'}, 'name', 1)).toBe(true);
    expect(isCellChanged(baseline, {...original, id: '1'}, 'id')).toBe(true);
  });
});

describe('modified record field rendering', () => {
  const render = (row: Row, mark = true) => renderToStaticMarkup(createElement(RecordFormView, {
    table: baseline, rows: [row], selectedId: row.id, onSelect: () => {}, onChange: () => {},
    isModified: mark ? (record, column) => isCellChanged(baseline, record, column) : undefined,
  }));
  it('marks only the changed field and exposes a descriptive hint and tooltip', () => {
    const html = render({...original, name: 'Edited'});
    expect(html.match(/record-form-field is-modified/g)).toHaveLength(1);
    expect(html).toContain('title="未应用更改"');
    expect(html).toContain('class="record-form-modified-hint">未应用更改</span>');
    expect(html).toMatch(/aria-describedby="[^"]+-type [^"]+-modified"/);
    expect(html).toContain('value="Edited"');
  });
  it('removes indicators after revert and keeps the optional prop backward compatible', () => {
    expect(render(original)).not.toContain('is-modified');
    expect(render({...original, name: 'Edited'}, false)).not.toContain('未应用更改');
  });
});
