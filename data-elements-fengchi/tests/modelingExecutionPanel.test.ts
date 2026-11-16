import {describe,it,expect} from 'vitest';
import {normalizeExecutionSettings} from '../src/modelingExecution';
import panel from '../src/ModelingExecutionPanel.tsx?raw';
describe('start node execution configuration',()=>{
 it('defaults older settings without enabling schedules',()=>{expect(normalizeExecutionSettings({engine:'flink',mode:'batch'})).toEqual({engine:'flink',mode:'batch',scheduleEnabled:false,intervalMinutes:60,runImmediately:false})});
 it('preserves explicit preferences across engine/mode changes',()=>{expect(normalizeExecutionSettings({engine:'spark',mode:'stream',scheduleEnabled:true,intervalMinutes:120,runImmediately:true})).toMatchObject({scheduleEnabled:true,intervalMinutes:120,runImmediately:true});expect(panel).toContain('onSettingsChange({...current,...patch})')});
 it('contains only configuration and validation, with no duplicated lifecycle actions',()=>{expect(panel).toContain('定时调度');expect(panel).toContain('启用后首次立即执行');expect(panel).toContain('持续实时');expect(panel).toContain('确认启动');for(const text of ['调试单次','调试结果','advanceMockRun','createMockRun','fetch(','setInterval(','setTimeout('])expect(panel).not.toContain(text)});
});
