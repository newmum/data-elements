import component from '../src/ModelingStartNode.tsx?raw';
import workspace from '../src/ModelingWorkspace.tsx?raw';
import {it,expect} from 'vitest';
import {clampStartPosition,DEFAULT_START_POSITION} from '../src/ModelingStartNode';
import {saveModelDocument,loadModelDocument,renameModelDocument,changeModelCategory,type ModelStorage} from '../src/modelCatalogStore';
import {openOrderExample} from '../src/modelCatalogExample';
it('bounds start control movement independently of data nodes',()=>{expect(DEFAULT_START_POSITION.x).toBeLessThan(180);expect(clampStartPosition({x:-20,y:9999})).toEqual({x:12,y:2000})});
it('persists start position while preserving nodes, data and metadata operations',()=>{const m=new Map<string,string>();const s:ModelStorage={get length(){return m.size},key:i=>[...m.keys()][i]??null,getItem:k=>m.get(k)??null,setItem:(k,v)=>{m.set(k,v)}};const original=openOrderExample(s);expect(original.startPosition).toBeUndefined();const saved=saveModelDocument({...original,startPosition:{x:58,y:130}},original.revision,s);expect(loadModelDocument(saved.id,s)?.startPosition).toEqual({x:58,y:130});expect(saved.nodes).toEqual(original.nodes);expect(saved.importedTables).toEqual(original.importedTables);const renamed=renameModelDocument(saved.id,'订单',saved.revision,s);const changed=changeModelCategory(saved.id,'分类',renamed.revision,s);expect(changed.startPosition).toEqual(saved.startPosition);expect(()=>saveModelDocument({...changed,startPosition:{x:NaN,y:12}},changed.revision,s)).toThrow('位置');expect(loadModelDocument(saved.id,s)).toEqual(changed)});

it('keeps existing local preview behavior and separates pointer drag from activation',()=>{expect(workspace).toContain('onStart={()=>void run(true)} disabled={localRunning}');expect(component).toContain('drag.current&&!moved.current');expect(component).toContain("action.current==='start'");expect(component).toContain('if(e.detail===0)onStart()');expect(component).toContain('aria-label="启动任务"')});
