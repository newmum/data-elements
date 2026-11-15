import {it,expect} from 'vitest';
import {openOrderExample,ORDER_EXAMPLE_ID} from '../src/modelCatalogExample';
import {saveModelDocument,listModelDocuments,type ModelStorage} from '../src/modelCatalogStore';
import {previewModel} from '../src/modeling';
import {generateModelSql} from '../src/modelingSql';
import catalog from '../src/ModelingCatalog.tsx?raw';
function memory(){const m=new Map<string,string>();return {get length(){return m.size},key:(i:number)=>[...m.keys()][i]??null,getItem:(k:string)=>m.get(k)??null,setItem:(k:string,v:string)=>{m.set(k,v)}} satisfies ModelStorage}
it('discovers and seeds a complete example in an empty browser store',()=>{const s=memory();expect(catalog).toContain('打开订单示例');const d=openOrderExample(s);expect(d.id).toBe(ORDER_EXAMPLE_ID);expect(d.nodes).toHaveLength(13);expect(listModelDocuments(s)).toHaveLength(1);const output=d.nodes.find(n=>n.kind==='output')!;expect(previewModel(d.nodes,output.id,d.importedTables).rows).toEqual([{'right.region_name':'华东',total_amount:550},{'right.region_name':'华南',total_amount:300}]);const sql=generateModelSql(d.nodes,d.importedTables,d.executionSettings);expect(sql.sql).toContain('AS VARCHAR');expect(sql.sql).toContain('JOIN DB3.regions');expect(sql.diagnostics.filter(x=>x.level==='error')).toEqual([])});
it('reopens without overriding saved edits or unrelated models',()=>{const s=memory(),d=openOrderExample(s);saveModelDocument({...d,name:'我的改名',category:'保留分类'},d.revision,s);saveModelDocument({...d,id:'my-other-model',name:'独立模型'},null,s);expect(openOrderExample(s).name).toBe('我的改名');expect(openOrderExample(s).revision).toBe(2);expect(listModelDocuments(s)).toHaveLength(2)});
it('fails honestly on quota errors',()=>{const s=memory();expect(()=>openOrderExample({...s,setItem:()=>{throw Error('quota')}})).toThrow('空间不足')});
