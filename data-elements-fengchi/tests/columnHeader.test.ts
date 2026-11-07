import {describe,it,expect} from 'vitest';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import ColumnHeader,{FieldTypeIcon,getColumnLabel,getColumnWidth,getFieldKind} from '../src/ColumnHeader';
import RecordFormView from '../src/RecordFormView';
import {initialTables} from '../src/data';
describe('bilingual column headers',()=>{
 it('uses consistent display names in grid and form without losing English names or types',()=>{
  const table=initialTables.users;
  const form=renderToStaticMarkup(createElement(RecordFormView,{table,rows:table.rows,selectedId:1,onSelect:()=>{},onChange:()=>{}}));
  for(const column of table.columns){const grid=renderToStaticMarkup(createElement(ColumnHeader,{column}));expect(grid).toContain(column.name);expect(grid).toContain(column.type.toUpperCase());expect(grid).toContain(getColumnLabel(column));expect(form).toContain(getColumnLabel(column));}
 });
 it('preserves primary key labeling and gives text fields a drawn type symbol',()=>{const id=renderToStaticMarkup(createElement(ColumnHeader,{column:initialTables.users.columns[0]}));expect(id).toContain('主键');expect(id).toContain('编号');const text=renderToStaticMarkup(createElement(ColumnHeader,{column:initialTables.users.columns[1]}));expect(text).toContain('field-kind-text');expect(text).not.toContain('Aᵇc');});
 it('uses the requested drawn 12 instead of 1.0 or a hash and preserves the approved T',()=>{
  const number=renderToStaticMarkup(createElement(FieldTypeIcon,{type:'INT'}));
  expect(number).toContain('m3 6 3-2v12m-3 0h6M11 7c0-4 6-4 6 0 0 2-6 5-6 9h6');
  expect(number).toContain('field-kind-number');
  expect(number).toContain('stroke-width="1.5"');
  expect(number).not.toMatch(/m8 3-2 14|m2.5 6.5 2.5-2v11|<circle|<ellipse/);
  const text=renderToStaticMarkup(createElement(FieldTypeIcon,{type:'VARCHAR(50)'}));
  expect(text).toContain('M4 6V4h12v2M10 4v12m-3 0h6');
 });
 it('classifies common types without mistaking dates for text',()=>{expect(getFieldKind('VARCHAR(50)')).toBe('text');expect(getFieldKind('INT UNSIGNED')).toBe('number');expect(getFieldKind('DECIMAL(10,2)')).toBe('number');expect(getFieldKind('DATETIME')).toBe('temporal');expect(getFieldKind('JSON')).toBe('document');expect(getFieldKind('BOOLEAN')).toBe('boolean');});
 it('keeps bounded readable widths and custom labels',()=>{for(const table of Object.values(initialTables))for(const column of table.columns){expect(getColumnWidth(column)).toBeGreaterThanOrEqual(120);expect(getColumnWidth(column)).toBeLessThanOrEqual(250);}expect(getColumnLabel({name:'custom_field',type:'text',comment:'自定义中文名'})).toBe('自定义中文名');expect(getColumnLabel({name:'custom_field',type:'text'})).toBe('custom_field');});
});
