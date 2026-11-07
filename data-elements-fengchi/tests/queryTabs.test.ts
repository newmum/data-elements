import {describe,it,expect} from 'vitest';
import {nextQueryNumber,createQueryKey,upsertSavedQuery} from '../src/queryTabs';
describe('query identity and naming',()=>{
 it('starts at one and resumes numbering without relying on persistent keys',()=>{
  expect(nextQueryNumber([])).toBe(1);
  expect(nextQueryNumber([{name:'未命名查询 1'},{name:'Revenue'},{name:'未命名查询 4'}])).toBe(5);
 });
 it('uses independent unique persistent identities',()=>{
  const keys=Array.from({length:100},()=>createQueryKey());expect(new Set(keys).size).toBe(100);
 });
 it('renames and saves SQL under the same identity without duplicates or reordering',()=>{
  const saved=[{key:'a',name:'Old',sql:'SELECT 1'},{key:'b',name:'Other',sql:'SHOW TABLES'}];
  const renamed={key:'a',name:'Orders',sql:'SELECT * FROM orders'};
  expect(upsertSavedQuery(saved,renamed)).toEqual([renamed,saved[1]]);
  expect(saved[0].name).toBe('Old');
  expect(upsertSavedQuery(saved,{key:'c',name:'New',sql:''})).toHaveLength(3);
 });
});
