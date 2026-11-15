import {describe,it,expect} from 'vitest';
import {initialModel,createModelNode} from '../src/modeling';
import type {TableDef} from '../src/data';
import {advanceMockRun,cancelMockRun,createMockRun,retryMockRun,validateExecution,validateSchedule} from '../src/modelingExecution';
import panel from '../src/ModelingExecutionPanel.tsx?raw';
const tables:Record<string,TableDef>={t:{name:'t',comment:'',columns:[{name:'id',type:'int'}],rows:[{id:1},{id:2}]}};
const settings={engine:'flink',mode:'batch'} as const;
describe('mock execution control',()=>{
 it('validates complete graph and snapshots model configuration',()=>{const nodes=initialModel(tables);expect(validateExecution(nodes,settings).filter(i=>i.level==='error')).toEqual([]);const run=createMockRun(nodes,settings);nodes[0].config.table='changed';expect(run.nodes[0].config.table).toBe('t');expect(run.state).toBe('queued')});
 it('requires input/output and detects missing inputs, cycles, duplicates and orphan nodes',()=>{expect(validateExecution([],settings).filter(i=>i.level==='error')).toHaveLength(2);const nodes=initialModel(tables);nodes[1].inputs=['missing'];expect(validateExecution(nodes,settings).some(i=>i.message.includes('断开'))).toBe(true);nodes[1].inputs=[nodes[1].id];expect(validateExecution(nodes,settings).some(i=>i.message.includes('循环'))).toBe(true);nodes.push(createModelNode('source','orphan',3));expect(validateExecution(nodes,settings).some(i=>i.message.includes('未连接'))).toBe(true)});
 it('blocks incomplete and configuration-only operators rather than claiming success',()=>{const nodes=initialModel(tables);nodes[0].config.table='';expect(()=>createMockRun(nodes,settings)).toThrow('缺少配置');nodes[0]=createModelNode('kafkaInput','node-1',0);expect(()=>createMockRun(nodes,settings)).toThrow('尚未接入');expect(validateExecution(nodes,settings).some(i=>i.message.includes('实时模式'))).toBe(true)});
 it('clearly limits local streaming compatibility',()=>{const nodes=initialModel(tables);nodes[1]={...createModelNode('sort','node-2',1),inputs:['node-1'],config:{field:'id',direction:'asc'}};expect(validateExecution(nodes,{engine:'spark',mode:'stream'}).some(i=>i.level==='error'&&i.message.includes('有限数据集'))).toBe(true)});
 it('advances manually and returns genuine local preview row counts only',()=>{let run=createMockRun(initialModel(tables),settings);run=advanceMockRun(run,tables);expect(run.state).toBe('running');run=advanceMockRun(run,tables);expect(run.state).toBe('succeeded');expect(run.rows).toBe(2);expect(advanceMockRun(run,tables)).toBe(run);expect(()=>retryMockRun(run)).toThrow('仅失败')});
 it('bounds retries to three attempts and supports cancellation',()=>{let run=createMockRun(initialModel(tables),settings);for(let i=1;i<=3;i++){run=advanceMockRun(run,tables);run=advanceMockRun(run,tables,true);expect(run.state).toBe('failed');expect(run.attempt).toBe(i);if(i<3)run=retryMockRun(run)}expect(()=>retryMockRun(run)).toThrow('最多');expect(cancelMockRun(createMockRun(initialModel(tables),settings)).state).toBe('cancelled')});
 it('reports data failures and validates schedule ranges without timers',()=>{let run=createMockRun(initialModel(tables),settings);run=advanceMockRun(run,{});expect(advanceMockRun(run,{}).state).toBe('failed');expect(validateSchedule('0','','')).not.toBe('');expect(validateSchedule('60','2026-11-15T10:00','2026-11-15T09:00')).not.toBe('');expect(validateSchedule('60','','')).toBe('');expect(panel).not.toContain('setInterval(');expect(panel).not.toContain('fetch(');expect(panel).toContain('不代表已读取历史数据');expect(panel).toContain('structuredClone(tables)')});
});

describe('schedule ISO input and compact summary',()=>{
 it('accepts explicit offsets and rejects invalid calendar dates and absent timezone',()=>{
 expect(validateSchedule('60','2026-11-14T00:00+08:00','2026-11-15T00:00+08:00')).toBe('');
 expect(validateSchedule('60','2026-11-14T00:00Z','2026-11-15T00:00Z')).toBe('');
 for(const start of ['2026-11-14T00:00','2026-02-30T00:00Z','2026-11-14T24:00Z','2026-11-14T00:00+14:30'])expect(validateSchedule('60',start,'2026-12-15T00:00Z')).not.toBe('');
 });
 it('uses plain text dates, concise safety tag and actual graph input/output names',()=>{
 expect(panel).not.toContain('datetime-local');expect(panel).toContain('本地模拟 · 不提交引擎');expect(panel).not.toContain('<Alert type="info"');expect(panel).toContain('modelInputCapacity(n.kind)===0');expect(panel).toContain('!modelHasOutput(n.kind)');expect(panel).toContain('n.config.table');
 });
});
