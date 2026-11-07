import {describe,it,expect} from 'vitest';
import {initialTables,type TableDef} from '../src/data';
import {getTableDdl} from '../src/tableDdl';
import app from '../src/App.tsx?raw';
import viewer from '../src/DdlViewer.tsx?raw';

describe('table DDL',()=>{
 for(const table of Object.values(initialTables)) it(`creates exactly one complete ${table.name} statement`,()=>{
  const text=getTableDdl(table);
  expect(text.match(/CREATE TABLE/g)).toHaveLength(1);
  expect(text).toContain(`CREATE TABLE \`${table.name}\``);
  for(const column of table.columns)expect(text).toContain(`\`${column.name}\` ${column.type.toUpperCase()}`);
  expect(text).toContain('PRIMARY KEY (`id`)');
  expect(text).toContain("DEFAULT CHARSET=utf8mb4");
  expect(text).toContain(`COMMENT='${table.comment}'`);
  expect(text.endsWith(';')).toBe(true);
 });
 it('preserves typed defaults, nullability and column comments',()=>{
  expect(getTableDdl(initialTables.users)).toContain("`role` VARCHAR(20) NOT NULL DEFAULT 'member' COMMENT '用户角色'");
  expect(getTableDdl(initialTables.users)).toContain('DEFAULT CURRENT_TIMESTAMP');
  expect(getTableDdl(initialTables.products)).toContain('`stock` INT NOT NULL DEFAULT 0');
  expect(getTableDdl(initialTables.categories)).toContain('`description` VARCHAR(255) NULL');
 });
 it('escapes quoted names and text without producing another statement',()=>{
  const table:TableDef={name:'a`b',comment:"O'Reilly\\x",columns:[{name:'a`b',type:'varchar(50)',default:"O'Reilly",comment:"it's"}],rows:[]};
  const text=getTableDdl(table);
  expect(text).toContain('CREATE TABLE `a``b`');
  expect(text).toContain("DEFAULT 'O''Reilly'");
  expect(text).toContain("COMMENT 'it''s'");
  expect(text).toContain("COMMENT='O''Reilly\\\\x'");
 });
 it('quotes textual numeric defaults instead of changing their type',()=>{
  const table:TableDef={name:'strings',comment:'',columns:[{name:'code',type:'varchar(20)',default:'001'}],rows:[]};
  expect(getTableDdl(table)).toContain("DEFAULT '001'");
 });
 it('preserves expression-looking text defaults as quoted literals',()=>{
  for(const value of ['CURRENT_TIMESTAMP','current_timestamp','TRUE','FALSE','CURRENT_DATE','CURRENT_TIME',' CURRENT_TIMESTAMP ','']) {
   const table:TableDef={name:'defaults',comment:'',columns:[{name:'value',type:'varchar(50)',default:value}],rows:[]};
   expect(getTableDdl(table)).toContain(`DEFAULT '${value}'`);
  }
 });
 it('renders only compatible expressions without quotes',()=>{
  const table:TableDef={name:'defaults',comment:'',columns:[
   {name:'created',type:'datetime',default:'current_timestamp'},
   {name:'flag',type:'boolean',default:'true'},
   {name:'count',type:'int',default:'FALSE'},
   {name:'optional',type:'varchar(50)',nullable:true,default:'null'},
   {name:'required',type:'varchar(50)',default:'NULL'},
   {name:'pk',type:'int',primary:true,nullable:true,default:'NULL'},
   {name:'number',type:'boolean',default:'0'},
  ],rows:[]};
  const ddl=getTableDdl(table);
  expect(ddl).toContain('`created` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP');
  expect(ddl).toContain('`flag` BOOLEAN NOT NULL DEFAULT TRUE');
  expect(ddl).toContain('`count` INT NOT NULL DEFAULT FALSE');
  expect(ddl).toContain('`optional` VARCHAR(50) NULL DEFAULT NULL');
  expect(ddl).toContain("`required` VARCHAR(50) NOT NULL DEFAULT 'NULL'");
  expect(ddl).toContain("`pk` INT NOT NULL DEFAULT 'NULL'");
  expect(ddl).toContain('`number` BOOLEAN NOT NULL DEFAULT 0');
 });
 it('uses one generator for both views and copy actions',()=>{
  expect(app).not.toContain('<h4>CREATE TABLE</h4>');
  expect(app.match(/<DdlViewer /g)).toHaveLength(2);
  expect(app.match(/copyText\(tableDdl,'DDL '\)/g)).toHaveLength(2);
  expect(viewer).toContain('syntaxHighlighting(ddlHighlightStyle)');
  expect(viewer).toContain('editable={false} readOnly');
 });
});


describe('table navigation',()=>{
 it('provides data, form, structure and DDL in order without duplicate controls',()=>{
  const tabs=app.split('<div className="view-tabs">')[1].split('</div>')[0];
  expect(tabs.indexOf("setView('数据')")).toBeLessThan(tabs.indexOf("setView('表单')"));
  expect(tabs.indexOf("setView('表单')")).toBeLessThan(tabs.indexOf("setView('结构')"));
  expect(tabs.indexOf("setView('结构')")).toBeLessThan(tabs.indexOf("setView('DDL')"));
  expect(app).not.toContain('record-view-switch');
  expect(app).not.toContain('setRecordView');
 });
});
