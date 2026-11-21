import {useEffect,useRef,useState,type PointerEvent} from 'react';
import type {ModelNode} from './modeling';
import {modelPoint,validateModelConnection} from './modelingInteractions';
import {createFrameTask,type FrameTask} from './modelingUpdates';
type Link={sourceId:string;x:number;y:number;targetId:string;error:string};
type Point={clientX:number;clientY:number};
export function useModelingInteractions(nodes:ModelNode[],zoom:number,onConnect:(source:string,target:string)=>void){
 const canvasRef=useRef<HTMLDivElement|null>(null);
 const [pan,setPan]=useState({x:0,y:0}),[panning,setPanning]=useState(false),[link,setLink]=useState<Link|null>(null),[status,setStatus]=useState('');
 const panDrag=useRef<{x:number;y:number;panX:number;panY:number}|null>(null),linkDrag=useRef<string|null>(null);
 const current=useRef({nodes,zoom,onConnect,pan});current.current={nodes,zoom,onConnect,pan};
 const panFrame=useRef<FrameTask<{x:number;y:number}>|null>(null),linkFrame=useRef<FrameTask<Point>|null>(null);
 if(!panFrame.current)panFrame.current=createFrameTask(point=>{
  if(point.x===current.current.pan.x&&point.y===current.current.pan.y)return;
  current.current.pan=point;setPan(point);
 });
 if(!linkFrame.current)linkFrame.current=createFrameTask(point=>{
  const sourceId=linkDrag.current;if(!sourceId)return;
  const next=candidate(point);setLink({sourceId,...position(point),...next});setStatus(next.error||'可连接，松开完成');
 });
 function cancel(){panFrame.current?.cancel();linkFrame.current?.cancel();panDrag.current=null;linkDrag.current=null;setPanning(false);setLink(null);setStatus('')}
 useEffect(()=>{const key=(event:KeyboardEvent)=>{if(event.key==='Escape'){cancel()}};window.addEventListener('keydown',key);window.addEventListener('blur',cancel);return()=>{window.removeEventListener('keydown',key);window.removeEventListener('blur',cancel);panFrame.current?.cancel();linkFrame.current?.cancel()}},[]);
 function position(e:Point){const rect=canvasRef.current?.getBoundingClientRect();return rect?modelPoint(e.clientX,e.clientY,rect,current.current.zoom):{x:0,y:0}}
 function candidate(e:Point){const element=document.elementFromPoint(e.clientX,e.clientY);const targetId=element?.closest<HTMLElement>('[data-model-input]')?.dataset.modelInput||'';const sourceId=linkDrag.current||'';return {targetId,error:targetId?validateModelConnection(current.current.nodes,sourceId,targetId):'拖到节点的输入端口'};}
 function finishPan(){panFrame.current?.flush();panDrag.current=null;setPanning(false)}
 const viewportHandlers={
  onPointerDown:(e:PointerEvent<HTMLDivElement>)=>{if(e.button!==0||linkDrag.current||(e.target as Element).closest('.modeling-node,button,input,textarea,select,[role="separator"]'))return;const rect=e.currentTarget.getBoundingClientRect();if(e.clientX>=rect.left+e.currentTarget.clientWidth||e.clientY>=rect.top+e.currentTarget.clientHeight)return;e.preventDefault();e.currentTarget.setPointerCapture(e.pointerId);panDrag.current={x:e.clientX,y:e.clientY,panX:current.current.pan.x,panY:current.current.pan.y};setPanning(true);setStatus('')},
  onPointerMove:(e:PointerEvent<HTMLDivElement>)=>{const d=panDrag.current;if(d)panFrame.current?.schedule({x:Math.max(-4000,Math.min(4000,d.panX+e.clientX-d.x)),y:Math.max(-3000,Math.min(3000,d.panY+e.clientY-d.y))})},
  onPointerUp:finishPan,onPointerCancel:cancel,onLostPointerCapture:finishPan,
 };
 function outputHandlers(id:string){return {
  onPointerDown:(e:PointerEvent<HTMLButtonElement>)=>{if(e.button!==0)return;e.stopPropagation();e.preventDefault();e.currentTarget.setPointerCapture(e.pointerId);linkFrame.current?.cancel();linkDrag.current=id;setLink({sourceId:id,...position(e),targetId:'',error:'拖到节点的输入端口'});setStatus('从输出端口拖到输入端口连线')},
  onPointerMove:(e:PointerEvent<HTMLButtonElement>)=>{if(linkDrag.current!==id)return;e.stopPropagation();linkFrame.current?.schedule({clientX:e.clientX,clientY:e.clientY})},
  onPointerUp:(e:PointerEvent<HTMLButtonElement>)=>{if(linkDrag.current!==id)return;e.stopPropagation();linkFrame.current?.cancel();const next=candidate(e);if(next.targetId&&!next.error){current.current.onConnect(id,next.targetId);setStatus('')}else setStatus(next.targetId?next.error:'');linkDrag.current=null;setLink(null)},
  onPointerCancel:cancel,onLostPointerCapture:()=>{linkFrame.current?.cancel();linkDrag.current=null;setLink(null)},
 }}
 const source=link&&nodes.find(n=>n.id===link.sourceId);
 const path=link&&source?'M'+(source.x+(source.width||236))+' '+(source.y+(source.height||88)/2)+' C'+(source.x+(source.width||236)+40)+' '+(source.y+(source.height||88)/2)+','+(link.x-40)+' '+link.y+','+link.x+' '+link.y:'';
 return {canvasRef,pan,panning,link,status,path,viewportHandlers,outputHandlers,resetPan:()=>{cancel();current.current.pan={x:0,y:0};setPan({x:0,y:0});setStatus('')}};
}
