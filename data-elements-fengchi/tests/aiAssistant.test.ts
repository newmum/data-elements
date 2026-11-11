import {describe,it,expect} from 'vitest';
import panel from '../src/AiAssistantPanel.tsx?raw';
import app from '../src/App.tsx?raw';
describe('AI assistant frontend safety contract',()=>{
 it('offers all three review-only modes and same-origin service',()=>{
  for(const mode of ['generate','explain','review'])expect(panel).toContain(`'${mode}'`);
  expect(panel).toContain("fetch('/api/ai/sql'");
  expect(panel).not.toMatch(/api\.openai\.com|dangerouslySetInnerHTML|executeSQL|runQuery|localStorage/);
 });
 it('requires consent and explicitly projects only schema fields',()=>{
  expect(panel).toContain('if(busy||!consent||schemaError||inputError||');
  expect(panel).toContain('chosen.length>20');
  expect(panel).toContain('n+s.columns.length,0)>300');
  expect(panel).toContain('schema:chosen.map(s=>({name:s.name,columns:s.columns.map(c=>({name:c.name,type:c.type}))}))');
  expect(panel).toContain('不发送表格行数据');
  expect(panel).not.toContain('.rows');
 });
 it('cancels on context changes and ignores stale responses',()=>{
  expect(panel).toContain('controller.current?.abort()');
  expect(panel).toContain('current===sequence.current');
  expect(panel).toContain('[open,contextKey,currentTableName,currentSchemaKey]');
 });
 it('integrates with shared and standalone workspaces without automatically executing',()=>{
  expect(app).toContain('<AiAssistantPanel');
  expect(panel).toContain('setSelected(currentSchema?[currentSchema.name]:[])');
  expect(panel).toContain('当前页签没有明确的表');
  expect(panel).toContain('<pre className="ai-sql-result">{text}</pre>');
  expect(panel).toContain('navigator.clipboard.writeText(text)');
  expect(panel).toContain('不会自动执行');
 });
});
