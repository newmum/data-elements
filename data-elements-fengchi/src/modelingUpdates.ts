import type {ModelNode} from './modeling';

/** Immutable semantic view for SQL only. Render and save current nodes, not this cached geometry. */
export function reuseModelComputation(previous:ModelNode[]|null,nodes:ModelNode[]):ModelNode[]{
 if(previous&&previous.length===nodes.length&&nodes.every((node,i)=>{
  const old=previous[i];
  return old===node||old.id===node.id&&old.kind===node.kind&&old.name===node.name&&old.inputs===node.inputs&&old.config===node.config;
 }))return previous;
 return nodes;
}
export type FrameTask<T>={schedule:(value:T)=>void;flush:()=>void;cancel:()=>void};
/** Latest absolute sample per animation frame; finish flushes, cancellation discards pending work. */
export function createFrameTask<T>(
 consume:(value:T)=>void,
 request:(callback:FrameRequestCallback)=>number=callback=>requestAnimationFrame(callback),
 cancel:(id:number)=>void=id=>cancelAnimationFrame(id),
):FrameTask<T>{
 let frame:number|null=null,pending:T|undefined;
 function consumePending(){const value=pending;pending=undefined;if(value!==undefined)consume(value)}
 function clearFrame(){if(frame!==null){cancel(frame);frame=null}}
 return {
  schedule(value){pending=value;if(frame===null)frame=request(()=>{frame=null;consumePending()})},
  flush(){clearFrame();consumePending()},
  cancel(){clearFrame();pending=undefined},
 };
}
