import {describe,it,expect} from 'vitest';
import {sourceObjects,sourceDatabase,changeSourceDatabase,selectSourceTable,sourceSelectionUpdate,changeSourceMode} from '../src/modelingSources';
import {createModelingDemo} from '../src/modelingDemo';
import {initialTables} from '../src/data';
describe('source cascade',()=>{
 it('groups five objects into three databases with Chinese labels and explicit views',()=>{const o=sourceObjects(createModelingDemo().tables);expect(new Set(o.map(x=>x.database)).size).toBe(3);expect(o.filter(x=>x.database==='DB2')).toHaveLength(3);expect(o.find(x=>x.kind==='view')?.label).toBe('客户地区视图');expect(o[0].databaseLabel).toBe('客户库')});
 it('infers existing selections, clears incompatible tables, never auto-picks another table',()=>{const o=sourceObjects(createModelingDemo().tables),c={table:'demo.DB1.customers'};expect(sourceDatabase(c,o)).toBe('DB1');const next=changeSourceDatabase(c,'DB2',o);expect(next.table).toBe('');expect(selectSourceTable(next,'demo.DB1.customers',o).table).toBe('');expect(selectSourceTable(next,'demo.DB2.orders',o).table).toBe('demo.DB2.orders');expect(changeSourceDatabase(c,'DB1',o).table).toBe(c.table)});
 it('does not invent database identity for legacy workspace tables',()=>{expect(sourceObjects(initialTables).every(x=>x.database==='workspace'&&x.databaseLabel==='当前工作区')).toBe(true);expect(sourceDatabase({table:''},sourceObjects(initialTables))).toBe('')});
});

it('automatic source names follow selections while manual names survive changes',()=>{const o=sourceObjects(createModelingDemo().tables),n={name:'请选择数据库和表',config:{table:''}};const c={database:'DB1',table:'demo.DB1.customers'};expect(sourceSelectionUpdate(n,c,o).name).toBe('客户库 / 客户表');expect(sourceSelectionUpdate(n,{database:'DB2',table:''},o).name).toBe('请选择数据库和表');expect(sourceSelectionUpdate({name:'我的客户输入',config:{...c,manualSourceName:'true'}},{database:'DB2',table:''},o).name).toBe('我的客户输入')});

it('mode switch clears incompatible bindings and retains explicit database and manual name flag',()=>{const c=changeSourceMode({database:'DB3',table:'old',mockSqlTable:'old',manualSourceName:'true'},'sql');expect(c.database).toBe('DB3');expect(c.table).toBe('');expect(c.mockSqlTable).toBe('');expect(c.manualSourceName).toBe('true')});
