import type {ModelNode,NodeKind} from './modeling';
const sourceKinds:NodeKind[]=['source','csvInput','jsonInput','xmlInput','excelInput','parquetInput','httpInput','kafkaInput','cdcInput','objectInput','fileWatcher'];
const doubleKinds:NodeKind[]=['join','union','intersection','difference','lookup','exists','crossJoin','fuzzy','dimensionLookup','mergeFlow'];
const sinkKinds:NodeKind[]=['output','csvOutput','jsonOutput','xmlOutput','excelOutput','parquetOutput','databaseOutput','httpOutput','kafkaOutput','objectOutput'];
export function modelInputCapacity(kind:NodeKind){return sourceKinds.includes(kind)?0:doubleKinds.includes(kind)?2:1}
export function modelHasOutput(kind:NodeKind){return !sinkKinds.includes(kind)}
export function validateModelConnection(nodes:ModelNode[],sourceId:string,targetId:string):string{
 const source=nodes.find(n=>n.id===sourceId),target=nodes.find(n=>n.id===targetId);
 if(!source||!target)return '节点已不存在';
 if(sourceId===targetId)return '不能连接到自身';
 if(!modelHasOutput(source.kind))return '输出节点不能作为前序节点';
 if(!modelInputCapacity(target.kind))return '输入算子不能接收前序连线';
 if(target.inputs.includes(sourceId))return '这条连线已存在';
 if(target.inputs.length>=modelInputCapacity(target.kind))return '该节点的输入端口已满';
 const seen=new Set<string>(),pending=[sourceId];
 while(pending.length){const id=pending.pop()!;if(id===targetId)return '此连线会形成循环';if(seen.has(id))continue;seen.add(id);pending.push(...(nodes.find(n=>n.id===id)?.inputs||[]))}
 return '';
}
export function validateModelInputs(nodes:ModelNode[],targetId:string,inputs:string[]):string{
 let draft=nodes.map(n=>n.id===targetId?{...n,inputs:[]}:n);
 for(const sourceId of inputs){const error=validateModelConnection(draft,sourceId,targetId);if(error)return error;draft=draft.map(n=>n.id===targetId?{...n,inputs:[...n.inputs,sourceId]}:n)}return '';
}
export function modelPoint(clientX:number,clientY:number,rect:{left:number;top:number},zoom:number){return {x:(clientX-rect.left)/zoom,y:(clientY-rect.top)/zoom}}
