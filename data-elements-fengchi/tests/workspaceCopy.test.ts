import {describe,it,expect} from 'vitest';
import appSource from '../src/App.tsx?raw';
import dataSource from '../src/data.ts?raw';
import sqlSource from '../src/sqlEngine.ts?raw';
import htmlSource from '../index.html?raw';
import {initialTables} from '../src/data';
import {migrateLegacyTableCopy} from '../src/workspaceCopy';
import {executeSQL} from '../src/sqlEngine';
describe('neutral interface wording',()=>{
 it('removes built-in labels from saved earlier data without mutating or discarding edits',()=>{
  const old=structuredClone(initialTables);old.users.comment='用户账户 · 演示数据（全部虚构）';old.users.rows[0].email='demo.user01@example.com';old.users.rows[0].name='保留修改';old.audit_logs.columns.find(c=>c.name==='ip_address')!.comment='演示 IP（文档保留地址）';
  const next=migrateLegacyTableCopy(old);expect(next.users.comment).toBe('用户账户');expect(next.users.rows[0].email).toBe('user01@example.com');expect(next.users.rows[0].name).toBe('保留修改');expect(next.audit_logs.columns.find(c=>c.name==='ip_address')!.comment).toBe('IP 地址');expect(old.users.rows[0].email).toBe('demo.user01@example.com');
 });
 it('keeps custom values intact and is idempotent',()=>{
  const tables=structuredClone(initialTables);tables.users.comment='自定义备注';tables.users.rows[0].email='custom@example.com';expect(migrateLegacyTableCopy(tables)).toEqual(tables);expect(migrateLegacyTableCopy(migrateLegacyTableCopy(tables))).toEqual(tables);
 });
 it('has no simulation wording in shipped interface copy or seed data',()=>{
  for(const source of [appSource,dataSource,sqlSource,htmlSource])expect(source).not.toMatch(/模拟|演示|虚构|原型|\b(?:demo|mock|prototype)\b/i);
  expect(JSON.stringify(initialTables)).not.toMatch(/\b(?:demo|mock|prototype)\b/i);
 });
 it('uses the active schema name in SHOW TABLES',()=>{expect(executeSQL('SHOW TABLES',initialTables).columns).toEqual(['Tables_in_commerce_db']);});
 it('retains accurate SQL limitations without demo labels',()=>{for(const query of ['DELETE FROM users','SELECT * FROM users JOIN orders']){try{executeSQL(query,initialTables);throw new Error('unexpected success');}catch(e){expect((e as Error).message).not.toMatch(/演示|模拟/);expect((e as Error).message).toMatch(/不支持|只读/);}}});
});
