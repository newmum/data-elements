import {describe,it,expect} from 'vitest';
import {activeToolbarSection} from '../src/toolbarSelection';
import source from '../src/App.tsx?raw';
describe('main toolbar selection',()=>{
 it.each([
  [{kind:'table'},'TABLE','表'],
  [{kind:'table'},'VIEW','视图'],
  [{kind:'objects'},undefined,'表'],
  [{kind:'functions'},undefined,'函数'],
  [{kind:'query'},undefined,'查询'],
  [{kind:'query',toolbarSection:'视图'},undefined,'视图'],
 ] as const)('reflects the current document %o',(tab,objectType,expected)=>{
  expect(activeToolbarSection({modelingOpen:false,tab,objectType})).toBe(expected);
 });
 it('prioritizes the model workspace and returns to its document when closed',()=>{
  const tab={kind:'objects'} as const;
  expect(activeToolbarSection({modelingOpen:true,tab})).toBe('模型');
  expect(activeToolbarSection({modelingOpen:false,tab})).toBe('表');
 });
 it('clears selection after the final document is closed',()=>{
  expect(activeToolbarSection({modelingOpen:false})).toBeUndefined();
 });
 it('styles and exposes only the selected workspace, not a permanent table button',()=>{
  expect(source).toContain("activeTool===label?'is-selected':''");
  expect(source).toContain('aria-pressed=');
  expect(source).not.toContain('table-tool');
 });
});
