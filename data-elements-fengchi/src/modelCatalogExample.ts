import {createModelingDemo} from './modelingDemo';
import {loadModelDocument,saveModelDocument,type ModelStorage} from './modelCatalogStore';
export const ORDER_EXAMPLE_ID='builtin-cross-database-orders-v1';
/** Explicit opt-in seed. Existing saved changes, including this example, are never overwritten. */
export function openOrderExample(storage?:ModelStorage){
 const existing=loadModelDocument(ORDER_EXAMPLE_ID,storage);if(existing)return existing;
 const demo=createModelingDemo();
 return saveModelDocument({id:ORDER_EXAMPLE_ID,name:'跨库客户订单示例',category:'客户订单分析',nodes:demo.nodes,executionSettings:{engine:'flink',mode:'batch'},importedTables:demo.tables},null,storage);
}
