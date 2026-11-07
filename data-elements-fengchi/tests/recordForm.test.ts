import {describe, expect, it} from 'vitest';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import RecordFormView, {createRecordDraftTracker, getRecordIndex, isRecordDraftDirty, parseRecordValue} from '../src/RecordFormView';
import {initialTables, type Column} from '../src/data';

describe('record form values', () => {
  const text: Column = {name:'description', type:'varchar(255)', nullable:true};
  const decimal: Column = {name:'price', type:'decimal(10,2)', nullable:false};
  it('preserves strings, whitespace, literal NULL, empty strings, and explicit null separately', () => {
    for (const value of ['hello', '  unchanged  ', 'NULL', '']) expect(parseRecordValue(text, value, false)).toEqual({value});
    expect(parseRecordValue(text, '', true)).toEqual({value:null});
    expect(parseRecordValue({...text, nullable:false}, '', true)).toHaveProperty('error');
  });
  it('keeps valid numeric edits numeric and never changes an empty required number to zero', () => {
    expect(parseRecordValue(decimal, '399.50', false)).toEqual({value:399.5});
    expect(parseRecordValue(decimal, ' 0 ', false)).toEqual({value:0});
    expect(parseRecordValue(decimal, ' -1.75 ', false)).toEqual({value:-1.75});
    expect(parseRecordValue(decimal, '1e2', false)).toEqual({value:100});
    expect(parseRecordValue(decimal, '', false)).toHaveProperty('error');
    expect(parseRecordValue({...decimal, nullable:true}, ' ', false)).toEqual({value:null});
  });
  it.each(['NaN','Infinity','1e1000','0xFF','3x','1,000'])('rejects invalid numeric entry %s', value => {
    expect(parseRecordValue(decimal, value, false)).toHaveProperty('error');
  });
  it('validates integers and unsigned values without rounding', () => {
    const integer: Column = {name:'id',type:'int unsigned'};
    expect(parseRecordValue(integer, '42', false)).toEqual({value:42});
    for (const value of ['1.5', '-1', '9007199254740993']) expect(parseRecordValue(integer, value, false)).toHaveProperty('error');
    expect(parseRecordValue({...decimal,type:'decimal(10,2) unsigned'}, '-0.5', false)).toHaveProperty('error');
  });
});

describe('record form selection and rendering', () => {
  const rows = [{id:5,name:'Five'},{id:8,name:'Eight'}];
  const table = {name:'items',comment:'',columns:[{name:'id',type:'int',primary:true},{name:'name',type:'varchar(40)'}],rows};
  const render = (selectedId: number | undefined, filteredRows = rows) => renderToStaticMarkup(createElement(RecordFormView, {table, rows:filteredRows, selectedId, onSelect:()=>{}, onChange:()=>{}}));
  it('follows selected records within the supplied filtered order', () => {
    expect(getRecordIndex(rows, 8)).toBe(1);
    expect(getRecordIndex([...rows].reverse(), 8)).toBe(0);
    expect(render(8)).toContain('value="Eight"');
    expect(render(8)).not.toContain('value="Five"');
  });
  it('falls back to the first record if selection is missing or filtered out', () => {
    expect(getRecordIndex(rows, undefined)).toBe(0);
    expect(getRecordIndex(rows, 999)).toBe(0);
    expect(render(999)).toContain('value="Five"');
  });
  it('shows an empty state and disables navigation for no rows', () => {
    expect(getRecordIndex([], 8)).toBe(-1);
    const html = render(8, []);
    expect(html).toContain('没有可显示的记录');
    expect(html.match(/disabled=""/g)).toHaveLength(4);
    expect(html).not.toContain('value="Eight"');
  });
  it('shows every column, labels its inputs, and keeps primary keys read-only', () => {
    const categories = initialTables.categories;
    const html = renderToStaticMarkup(createElement(RecordFormView, {table:categories, rows:categories.rows, selectedId:4, onSelect:()=>{}, onChange:()=>{}}));
    for (const column of categories.columns) expect(html).toContain(column.name);
    expect(html).toMatch(/readonly=""/i);
    expect(html).toContain('description 设为 NULL');
    expect(html).toContain('checked=""');
    expect(html).toContain('placeholder="NULL"');
    expect(html.match(/class="record-form-label"/g)).toHaveLength(categories.columns.length);
  });
});


describe('record form raw draft tracking', () => {
  it('tracks raw invalid and normalized numeric drafts until commit or reset', () => {
    expect(isRecordDraftDirty(12, 'invalid', false)).toBe(true);
    expect(isRecordDraftDirty(12, '12.0', false)).toBe(true);
    expect(isRecordDraftDirty(12, '12', false)).toBe(false);
    expect(isRecordDraftDirty('Name', 'Edited', false)).toBe(true);
    expect(isRecordDraftDirty('Name', 'Name', false)).toBe(false);
  });
  it('tracks NULL toggles separately from empty text and ignores hidden text while NULL', () => {
    expect(isRecordDraftDirty(null, '', false)).toBe(true);
    expect(isRecordDraftDirty('', '', true)).toBe(true);
    expect(isRecordDraftDirty(null, 'hidden previous text', true)).toBe(false);
    expect(isRecordDraftDirty('Name', 'Name', true)).toBe(true);
    expect(isRecordDraftDirty('Name', 'Name', false)).toBe(false);
  });
  it('aggregates dirty fields and reports only transitions, avoiding callback loops', () => {
    const notifications: boolean[] = [];
    const tracker = createRecordDraftTracker(dirty => notifications.push(dirty));
    tracker.update('name', false);
    tracker.update('name', true);
    tracker.update('name', true);
    tracker.update('amount', true);
    tracker.update('name', false);
    expect(notifications).toEqual([true]);
    tracker.update('amount', false);
    tracker.update('amount', false);
    expect(notifications).toEqual([true, false]);
  });
  it('clears all field drafts on record changes or unmount and can track again', () => {
    const notifications: boolean[] = [];
    const tracker = createRecordDraftTracker(dirty => notifications.push(dirty));
    tracker.update('name', true);
    tracker.update('amount', true);
    tracker.clear();
    tracker.clear();
    tracker.update('name', false);
    tracker.update('next', true);
    tracker.update('next', false);
    expect(notifications).toEqual([true, false, true, false]);
  });
});


describe('new schema numeric types',()=>{
 it('keeps boolean and year inputs numeric after a schema change',()=>{expect(parseRecordValue({name:'enabled',type:'boolean'},'1',false)).toEqual({value:1});expect(parseRecordValue({name:'year',type:'year'},'2026',false)).toEqual({value:2026});});
 it('does not stringify float and double cell values',()=>{expect(parseRecordValue({name:'amount',type:'float'},'1.5',false)).toEqual({value:1.5});expect(parseRecordValue({name:'amount',type:'double'},'2.75',false)).toEqual({value:2.75});});
});
