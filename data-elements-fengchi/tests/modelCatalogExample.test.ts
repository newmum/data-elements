import {it,expect} from 'vitest';
import {openOrderExample,ORDER_EXAMPLE_ID} from '../src/modelCatalogExample';
import {saveModelDocument,listModelDocuments,type ModelStorage} from '../src/modelCatalogStore';
import {previewModel} from '../src/modeling';
import {generateModelSql} from '../src/modelingSql';
import catalog from '../src/ModelingCatalog.tsx?raw';
function memory(){const m=new Map<string,string>();return {get length(){return m.size},key:(i:number)=>[...m.keys()][i]??null,getItem:(k:string)=>m.get(k)??null,setItem:(k:string,v:string)=>{m.set(k,v)}} satisfies ModelStorage}
it('discovers and seeds a complete example in an empty browser store',()=>{const s=memory();expect(catalog).not.toContain('打开订单示例');expect(catalog).not.toContain('继续当前模型');const d=openOrderExample(s);expect(d.id).toBe(ORDER_EXAMPLE_ID);expect(d.nodes).toHaveLength(13);expect(listModelDocuments(s)).toHaveLength(1);const output=d.nodes.find(n=>n.kind==='output')!;expect(previewModel(d.nodes,output.id,d.importedTables).rows).toEqual([{'right.region_name':'华东',total_amount:550},{'right.region_name':'华南',total_amount:300}]);const sql=generateModelSql(d.nodes,d.importedTables,d.executionSettings);expect(sql.sql).toContain('AS VARCHAR');expect(sql.sql).toContain('JOIN DB3.regions');expect(sql.diagnostics.filter(x=>x.level==='error')).toEqual([])});
it('reopens without overriding saved edits or unrelated models',()=>{const s=memory(),d=openOrderExample(s);saveModelDocument({...d,name:'我的改名',category:'保留分类'},d.revision,s);saveModelDocument({...d,id:'my-other-model',name:'独立模型'},null,s);expect(openOrderExample(s).name).toBe('我的改名');expect(openOrderExample(s).revision).toBe(2);expect(listModelDocuments(s)).toHaveLength(2)});
it('fails honestly on quota errors',()=>{const s=memory();expect(()=>openOrderExample({...s,setItem:()=>{throw Error('quota')}})).toThrow('空间不足')});

it('keeps edited node definitions, imported rows and execution settings when catalog seeding repeats',()=>{
 const s=memory(),d=openOrderExample(s);
 d.nodes[0].name='我的输入节点';
 const tableKey=Object.keys(d.importedTables)[0];
 d.importedTables[tableKey].rows[0]={...d.importedTables[tableKey].rows[0],validation_value:77};
 const saved=saveModelDocument({...d,executionSettings:{...d.executionSettings,engine:'spark'}},d.revision,s);
 expect(openOrderExample(s)).toEqual(saved);
 expect(listModelDocuments(s)[0]).toEqual(saved);
});
