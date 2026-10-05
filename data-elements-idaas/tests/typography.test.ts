import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import { typeScale } from '../src/app/typography.ts';
import { chartTickIndices } from '../src/components/chart-layout.ts';

test('导航、正文与表格使用14px基准，不随紧凑密度缩小', () => {
 assert.equal(typeScale.navigation.size,14); assert.equal(typeScale.body.size,14);
 assert.equal(typeScale.body.lineHeight,22);
});
test('标题与辅助文字层级严格递增', () => {
 const values=['caption','secondary','body','section','panel','page'].map(k=>typeScale[k as keyof typeof typeScale].size);
 for(let i=1;i<values.length;i++) assert.ok(values[i]>values[i-1]);
});
test('所有角色至少12px且行高不裁切', () => {
 for(const t of Object.values(typeScale)){assert.ok(t.size>=12); assert.ok(t.lineHeight>=t.size+4);}
});
test('指标数值独立于普通标题角色', () => {
 assert.equal(typeScale.statistic.size,28); assert.equal(typeScale.section.size,16);
});
test('CSS变量与语义字号源一致', () => {
 const css=fs.readFileSync(new URL('../src/styles/typography-tokens.css',import.meta.url),'utf8');
 for(const [key,t] of Object.entries(typeScale)) {
  const role=key.replace(/[A-Z]/g,m=>'-'+m.toLowerCase());
  assert.ok(css.includes(`--iam-font-${role}: ${t.size}px;`), role);
  assert.ok(css.includes(`--iam-leading-${role}: ${t.lineHeight}px;`), role);
 }
});
test('空图表与非法数据数量不会生成刻度', () => {
 for(const count of [0,-1,NaN,Infinity,.5]) assert.deepEqual(chartTickIndices(count,200),[]);
});
test('单个数据点仅有一个刻度', () => assert.deepEqual(chartTickIndices(1,200),[0]));
test('窄图不缩小文字而减少刻度', () => {
 assert.deepEqual(chartTickIndices(7,20),[6]); assert.deepEqual(chartTickIndices(7,0),[6]);
 assert.ok(chartTickIndices(7,180).length < chartTickIndices(7,640).length);
});
test('宽图保留全部七天刻度', () => assert.deepEqual(chartTickIndices(7,640),[0,1,2,3,4,5,6]));
test('大量数据刻度无重复、首尾保留且有序', () => {
 for(const width of [120,180,280,480,900]) {
  const ticks=chartTickIndices(90,width);
  assert.equal(ticks[0],0); assert.equal(ticks.at(-1),89);
  assert.equal(new Set(ticks).size,ticks.length);
  for(let i=1;i<ticks.length;i++) assert.ok(ticks[i]>ticks[i-1]);
 }
});
test('异常画布宽度和间距得到安全处理', () => {
 assert.deepEqual(chartTickIndices(7,NaN),[6]);
 assert.ok(chartTickIndices(7,200,NaN).length>0);
 assert.deepEqual(chartTickIndices(7,Infinity),[6]);
});
