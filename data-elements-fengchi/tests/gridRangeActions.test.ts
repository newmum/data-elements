import {describe,it,expect} from 'vitest';
import {initialTables,type TableDef} from '../src/data';
import {rangeAsTsv,rangeClearBlocker,clearRangeContents} from '../src/gridRangeActions';
describe('range actions',()=>{
 it('copies the exact visual row/column order',()=>expect(rangeAsTsv(initialTables.users,{rowIds:[3,1],columns:['email','name'],count:4})).toBe('user03@example.com\t周予安\r\nuser01@example.com\t林知远'));
 it('preserves tabs, quotes and null values in TSV',()=>{const table:TableDef={name:'x',comment:'',columns:[],rows:[{id:1,a:'a\tb',b:'say "yes"',c:null}]};expect(rangeAsTsv(table,{rowIds:[1],columns:['a','b','c'],count:3})).toBe('"a\tb"\t"say ""yes"""\t')});
 it('blocks primary and required numeric/date cells',()=>{for(const column of ['id','created_at'])expect(rangeClearBlocker(initialTables.users,{rowIds:[1],columns:[column],count:1})).toBeTruthy();expect(rangeClearBlocker(initialTables.products,{rowIds:[1],columns:['price'],count:1})).toBeTruthy()});
 it('stages clearing text without deleting records or mutating the source',()=>{const range={rowIds:[1,2],columns:['name','email'],count:4};const next=clearRangeContents(initialTables.users,range);expect(next.rows).toHaveLength(24);expect(next.rows[0].name).toBe('');expect(next.rows[1].email).toBe('');expect(next.rows[2]).toEqual(initialTables.users.rows[2]);expect(initialTables.users.rows[0].name).toBe('林知远')});
 it('clears nullable fields to NULL',()=>expect(clearRangeContents(initialTables.categories,{rowIds:[1],columns:['description'],count:1}).rows[0].description).toBeNull());
 it('refuses partial clearing of a range with protected fields',()=>expect(()=>clearRangeContents(initialTables.users,{rowIds:[1],columns:['id','name'],count:2})).toThrow());
});
