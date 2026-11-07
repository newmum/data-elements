import {describe,it,expect} from 'vitest';
import {createElement} from 'react';
import {renderToStaticMarkup} from 'react-dom/server';
import StructureEditor from '../src/StructureEditor';
import {createSchemaDraft} from '../src/schemaEditing';
import {initialTables} from '../src/data';
function markup(table=initialTables.users,edit?:(draft:ReturnType<typeof createSchemaDraft>)=>void){const draft=createSchemaDraft(table);edit?.(draft);return renderToStaticMarkup(createElement(StructureEditor,{table,draft,onChange:()=>{},onShowDdl:()=>{}}))}
describe('editable structure controls',()=>{
 it('removes the persistent bottom help region while retaining the DDL entry',()=>{const html=markup();expect(html).not.toMatch(/直接编辑字段属性|长度独立设置|schema-guidance|schema-property-note/);expect(html).toContain('查看建表语句');});
 it('separates type and length and exposes each supported field attribute',()=>{const html=markup();expect(html).toContain('<th>数据类型</th><th>长度</th>');for(const property of ['字段名','数据类型','长度','无符号','允许空值','主键','启用默认值','默认值','注释'])expect(html).toContain(`aria-label="name ${property}"`);expect(html).toContain('value="VARCHAR" selected=""');expect(html).toContain('aria-label="name 长度"');expect(html).toContain('value="50"');});
 it('marks only edited properties and preserves stable source labels through rename',()=>{const html=markup(initialTables.users,d=>{d[1].name='display_name';d[1].length='80';d[1].comment='显示名称'});expect(html.match(/class="schema-property changed"/g)).toHaveLength(3);expect(html).toContain('aria-label="name 字段名"');expect(html).toContain('value="display_name"');});
 it('shows decimal precision separately and protects the record identity',()=>{const html=markup(initialTables.orders);expect(html).toContain('value="10,2"');expect(html).toContain('id 是当前记录标识，暂不可重命名');expect(html).not.toContain('已有字段暂不可编辑');});
 it('presents validation errors without claiming data was changed',()=>{const table=initialTables.users;const html=renderToStaticMarkup(createElement(StructureEditor,{table,draft:createSchemaDraft(table),error:'字段名不能重复',errorField:'field-1-name',onChange:()=>{},onShowDdl:()=>{}}));expect(html).toContain('role="alert"');expect(html).toContain('schema-invalid-row');expect(html).toContain('当前记录尚未改变');});
});
