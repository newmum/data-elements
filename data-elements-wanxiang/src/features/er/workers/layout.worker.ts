import ELK from 'elkjs/lib/elk-api.js';
import elkWorkerUrl from 'elkjs/lib/elk-worker.min.js?url';
import type { Position } from '../types/domain';
import { clusteredLayout } from '../core/clusterLayout';
interface Input { id:string; direction:'RIGHT'|'DOWN'; children:Array<{id:string;width:number;height:number;ports?:Array<{id:string;x:number;y:number;width:number;height:number}>}>; edges:Array<{id:string;sources:string[];targets:string[]}> }
self.onmessage=async(event:MessageEvent<Input>)=>{
  const request=event.data;
  try{
    const links=request.edges.map(edge=>({source:edge.sources[0],target:edge.targets[0]}));
    const connected=new Set(links.flatMap(link=>[link.source,link.target]));
    let elkPositions:Record<string,Position>={};
    if(connected.size){
      // Use a real nested Web Worker. The bundled ELK fake Worker becomes a non-constructor after Vite transforms it.
      const elk=new ELK({workerUrl:elkWorkerUrl,workerFactory:url=>new Worker(url!)});
      try{
        const graph=await elk.layout({id:'root',layoutOptions:{'elk.algorithm':'layered','elk.direction':request.direction,'elk.spacing.nodeNode':'96','elk.layered.spacing.nodeNodeBetweenLayers':'150','elk.edgeRouting':'ORTHOGONAL','elk.layered.considerModelOrder.strategy':'NODES_AND_EDGES'},children:request.children.filter(node=>connected.has(node.id)),edges:request.edges});
        elkPositions=Object.fromEntries((graph.children??[]).map(node=>[node.id,{x:node.x??0,y:node.y??0}]));
      }finally{elk.terminateWorker();}
    }
    self.postMessage({id:request.id,positions:clusteredLayout(request.children,links,elkPositions,request.direction)});
  }catch(error){self.postMessage({id:request.id,error:error instanceof Error?error.message:String(error)});}
};
