import {BUSINESS_MODEL_EXAMPLES} from './modelBusinessExamples';
import {createModelingDemo} from './modelingDemo';
import {loadModelDocument,saveModelDocument,type ModelStorage} from './modelCatalogStore';
export const ORDER_EXAMPLE_ID='builtin-cross-database-orders-v1';
/** Idempotent catalog seed for the standalone demo workspace. Only migrate the untouched initial seed; explicit edits are never overwritten. */
export function openOrderExample(storage?:ModelStorage){
 const existing=loadModelDocument(ORDER_EXAMPLE_ID,storage);
 if(existing){
  if(existing.revision!==1||existing.name!=='跨库客户订单示例'||existing.category!=='客户订单分析'||existing.sqlDraft||existing.startName||existing.startPosition||existing.sourceId||existing.sourceLabel||existing.executionSettings.engine!=='flink'||existing.executionSettings.mode!=='batch'||existing.executionSettings.scheduleEnabled)return existing;
  const legacy=createModelingDemo({legacy:true});
  if(JSON.stringify(existing.nodes)!==JSON.stringify(legacy.nodes)||JSON.stringify(existing.importedTables)!==JSON.stringify(legacy.tables))return existing;
  const upgraded=createModelingDemo();
  return saveModelDocument({...existing,nodes:upgraded.nodes,importedTables:upgraded.tables},existing.revision,storage);
 }
 const demo=createModelingDemo();
 return saveModelDocument({id:ORDER_EXAMPLE_ID,name:'跨库客户订单示例',category:'客户订单分析',nodes:demo.nodes,executionSettings:{engine:'flink',mode:'batch'},importedTables:demo.tables},null,storage);
}

/** Seed only absent stable IDs. Existing names, categories, graphs and snapshots belong to the user. */
export function ensureModelExamples(storage?:ModelStorage){
 return [openOrderExample(storage),...BUSINESS_MODEL_EXAMPLES.map(({id,create})=>
  loadModelDocument(id,storage)??saveModelDocument(create(),null,storage)
 )];
}
