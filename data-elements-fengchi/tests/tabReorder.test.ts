import {describe,it,expect} from 'vitest';
import {moveTab,orderTabs,tabEdgeScroll} from '../src/useTabReorder';
const tabs=[{key:'objects'},{key:'query',sql:'SELECT unsaved'},{key:'users'},{key:'orders'}];
describe('document tab ordering',()=>{
 it('moves left and right without replacing tabs or changing SQL',()=>{
  const right=moveTab(tabs,'query',3);expect(right.map(t=>t.key)).toEqual(['objects','users','orders','query']);
  expect(right[3]).toBe(tabs[1]);expect(right[3].sql).toBe('SELECT unsaved');
  expect(moveTab(right,'query',0).map(t=>t.key)).toEqual(['query','objects','users','orders']);
  expect(tabs.map(t=>t.key)).toEqual(['objects','query','users','orders']);
 });
 it('clamps ends and ignores absent keys, nonfinite positions and no-op moves',()=>{
  expect(moveTab(tabs,'query',-20)[0]).toBe(tabs[1]);expect(moveTab(tabs,'query',99)[3]).toBe(tabs[1]);
  expect(moveTab(tabs,'missing',0)).toBe(tabs);expect(moveTab(tabs,'query',NaN)).toBe(tabs);expect(moveTab(tabs,'query',1)).toBe(tabs);expect(moveTab([],'query',1)).toEqual([]);
 });
 it('applies preview to the latest objects and preserves newly opened tabs',()=>{
  const updated={key:'query',sql:'SELECT newer'};const newTab={key:'new'};
  const current=[tabs[0],updated,tabs[2],newTab];
  const next=orderTabs(current,['orders','query','objects','users']);
  expect(next).toEqual([updated,tabs[0],tabs[2],newTab]);expect(next[0]).toBe(updated);
 });
 it('does not resurrect closed tabs or duplicate reopened ones',()=>{
  expect(orderTabs([tabs[0]],['query','objects'])).toEqual([tabs[0]]);
  const reopened={key:'query',sql:''};expect(orderTabs([tabs[0],reopened],['query','query','objects'])).toEqual([reopened,tabs[0]]);
  expect(orderTabs(tabs,tabs.map(t=>t.key))).toBe(tabs);
 });
 it('scrolls only in edge bands with bounded speed',()=>{
  expect(tabEdgeScroll(200,100,400)).toBe(0);expect(tabEdgeScroll(100,100,400)).toBe(-12);expect(tabEdgeScroll(400,100,400)).toBe(12);
  expect(tabEdgeScroll(-500,100,400)).toBe(-12);expect(tabEdgeScroll(900,100,400)).toBe(12);expect(tabEdgeScroll(122,100,400)).toBe(-6);expect(tabEdgeScroll(0,0,0)).toBe(0);
 });
});
