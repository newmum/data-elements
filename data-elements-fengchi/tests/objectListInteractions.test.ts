import {describe,it,expect,vi} from 'vitest';
import type {MouseEvent,KeyboardEvent} from 'react';
import {objectRowHandlers,openObjectControl} from '../src/objectListInteractions';
import app from '../src/App.tsx?raw';
import catalog from '../src/ModelingCatalog.tsx?raw';
import functions from '../src/FunctionWorkspace.tsx?raw';

function mouse(target:'row'|'cell'|'text'|'control'='cell',selector='button',detail=1){
 const control={};
 const row={contains:(value:unknown)=>value===control};
 const child={closest:(selectors:string)=>target==='control'&&selectors.includes(selector)?control:null};
 return {target:target==='row'?row:child,currentTarget:row,detail,preventDefault:vi.fn(),stopPropagation:vi.fn()} as unknown as MouseEvent<HTMLTableRowElement>;
}
function keyboard(key:string,onRow=true){
 const row={};
 return {key,currentTarget:row,target:onRow?row:{},preventDefault:vi.fn(),stopPropagation:vi.fn()} as unknown as KeyboardEvent<HTMLTableRowElement>;
}

describe('whole object-row default activation',()=>{
 it.each(['row','cell','text'] as const)('selects on a click and opens once on double-click from %s',target=>{
  const open=vi.fn(),select=vi.fn(),handlers=objectRowHandlers(open,select);
  handlers.onClick!(mouse(target));
  expect(select).toHaveBeenCalledTimes(1);
  expect(open).not.toHaveBeenCalled();
  handlers.onClick!(mouse(target,undefined,2));
  const double=mouse(target,undefined,2);handlers.onDoubleClick!(double);
  expect(open).toHaveBeenCalledTimes(1);
  expect(double.preventDefault).toHaveBeenCalledTimes(1);
 });
 it.each(['button','a','input','select','textarea','[role="button"]','[role="link"]','[role="checkbox"]','[role="switch"]','[contenteditable="true"]'])('does not activate or select through nested %s controls',selector=>{
  const open=vi.fn(),select=vi.fn(),handlers=objectRowHandlers(open,select);
  const event=mouse('control',selector,2);
  handlers.onClick!(event);handlers.onDoubleClick!(event);
  expect(open).not.toHaveBeenCalled();
  expect(select).not.toHaveBeenCalled();
 });
 it('opens with Enter only when the row itself has keyboard focus',()=>{
  const open=vi.fn(),handlers=objectRowHandlers(open);
  expect(handlers.tabIndex).toBe(0);
  const enter=keyboard('Enter');handlers.onKeyDown!(enter);
  expect(open).toHaveBeenCalledTimes(1);
  expect(enter.preventDefault).toHaveBeenCalledTimes(1);
  handlers.onKeyDown!(keyboard('Enter',false));
  handlers.onKeyDown!(keyboard(' '));
  handlers.onKeyDown!(keyboard('ArrowDown'));
  expect(open).toHaveBeenCalledTimes(1);
 });
 it('keeps inline name buttons immediate and avoids a second activation from the same double-click',()=>{
  const open=vi.fn(),select=vi.fn(),handlers=objectRowHandlers(open,select);
  for(const detail of [1,2]){
   const event=mouse('control','button',detail);
   openObjectControl(event,open);
   expect(event.stopPropagation).toHaveBeenCalledTimes(1);
   handlers.onClick!(event);
  }
  handlers.onDoubleClick!(mouse('control','button',2));
  expect(open).toHaveBeenCalledTimes(1);
  expect(select).not.toHaveBeenCalled();
 });
 it('retains keyboard and assistive activation of inline buttons',()=>{
  const open=vi.fn(),event=mouse('control','button',0);
  openObjectControl(event,open);
  expect(open).toHaveBeenCalledTimes(1);
  expect(event.stopPropagation).toHaveBeenCalledTimes(1);
 });
});

describe('existing object opening routes',()=>{
 it('opens table and shared view records through the existing data-view route',()=>{
  expect(app).toContain('objectRowHandlers(()=>openTable(record.name),()=>setContextTable(record.name))');
  expect(app).toContain('onContextMenu:()=>setContextTable(record.name)');
  expect(app).toContain('openObjectControl(event,()=>openTable(name))');
  const openTable=app.split('function openTable(name:string)')[1].split('function refreshObjects')[0];
  expect(openTable).toContain("setView('数据')");
  expect(openTable).toContain('prev.some(t=>t.key===key)?prev');
  expect(app).toContain("row.objectType==='VIEW'");
  expect(app).toContain('openTable(view.tableName)');
 });
 it('opens the corresponding model canvas from a double-click and selects from a single click',()=>{
  expect(catalog).toContain('onRow={d=>objectRowHandlers(()=>onOpen(d.id),()=>setSelectedId(d.id))}');
  expect(catalog).toContain('openObjectControl(e,()=>onOpen(d.id))');
  expect(catalog).not.toContain('setSelectedId(d.id);onOpen(d.id)');
 });
 it('retains the already-working whole-row function and query-history buttons',()=>{
  expect(functions).toContain('onClick={() => setSelectedId(fn.id)}');
  expect(functions).toContain('const current = drafts[selectedId] ?? selected;');
  expect(app).toContain('onClick={()=>{newQuery(h.sql);setShowHistory(false)}}');
  expect(app).toContain('if(q)openSavedQuery(q)');
 });
});
