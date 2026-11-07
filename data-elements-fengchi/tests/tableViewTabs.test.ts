import {describe,expect,it} from 'vitest';
import app from '../src/App.tsx?raw';
// CSS modules are stubbed by Vitest; read authored styles without browser transforms.
const fileSystemModule='node:fs';
const {readFileSync}=await import(/* @vite-ignore */ fileSystemModule) as {readFileSync:(path:URL,encoding:'utf8')=>string};
const css=readFileSync(new URL('../src/tableViewTabs.css',import.meta.url),'utf8');

describe('table view tabs share workspace borders',()=>{
 it('removes inset spacing, button frames, rounded corners and shadows',()=>{
  expect(css).toContain('padding:0;gap:0;align-items:stretch');
  expect(css).toContain('height:100%;margin:0');
  expect(css).toContain('border:0;border-radius:0;box-shadow:none');
 });
 it('keeps one continuous separator visible beneath the active tab',()=>{
  expect(css).toContain('border-bottom:1px solid var(--border)');
  expect(css).not.toContain('button.active::after');
  expect(css).not.toMatch(/bottom:\s*-1px|margin-bottom:\s*-1px/);
 });
 it('keeps themed selected and hover feedback with an inset keyboard focus ring',()=>{
  expect(css).toContain('button:hover{background:var(--hover);color:var(--text)}');
  expect(css).toContain('button.active{background:var(--surface);color:var(--text)');
  expect(css).toContain('outline:2px solid var(--accent);outline-offset:-3px');
  expect(css).not.toMatch(/#[\da-f]{3,8}\b/i);
 });
 it('is scoped to table views and retains all four existing navigation actions',()=>{
  expect(app).toContain("import './tableViewTabs.css'");
  const tabs=app.split('<div className="view-tabs">')[1].split('</div>')[0];
  for(const view of ['数据','表单','结构','DDL'])expect(tabs).toContain(`setView('${view}')`);
 });
});
