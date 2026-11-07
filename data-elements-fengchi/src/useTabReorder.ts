import {useEffect,useRef,useState} from 'react';
import type {Dispatch,SetStateAction,RefObject,PointerEvent as ReactPointerEvent,MouseEvent as ReactMouseEvent,KeyboardEvent as ReactKeyboardEvent} from 'react';

type Keyed={key:string};
/** Immutable, bounded move; the original tab objects (including unsaved SQL) survive. */
export function moveTab<T extends Keyed>(tabs:T[],key:string,to:number):T[]{
 const from=tabs.findIndex(tab=>tab.key===key);
 if(from<0||!Number.isFinite(to))return tabs;
 const index=Math.max(0,Math.min(tabs.length-1,Math.trunc(to)));
 if(from===index)return tabs;
 const next=tabs.slice();const [tab]=next.splice(from,1);next.splice(index,0,tab);return next;
}
/** Apply a preview to current data, never resurrect closed tabs or overwrite edits. */
export function orderTabs<T extends Keyed>(tabs:T[],keys:readonly string[]):T[]{
 const byKey=new Map(tabs.map(tab=>[tab.key,tab]));const ordered:T[]=[];
 for(const key of keys){const tab=byKey.get(key);if(tab){ordered.push(tab);byKey.delete(key)}}
 for(const tab of tabs)if(byKey.has(tab.key)){ordered.push(tab);byKey.delete(tab.key)}
 return ordered.every((tab,index)=>tab===tabs[index])?tabs:ordered;
}
export function tabEdgeScroll(clientX:number,left:number,right:number):number{
 const band=Math.min(44,Math.max(0,(right-left)/3));
 if(!band)return 0;
 if(clientX<left+band)return -12*Math.min(1,(left+band-clientX)/band);
 if(clientX>right-band)return 12*Math.min(1,(clientX-right+band)/band);
 return 0;
}
type Drag={key:string;id:number;startX:number;x:number;offset:number;width:number;dragging:boolean;keys:string[];strip:HTMLElement;cleanup:()=>void};

/** Render orderedTabs, add data-tab-key to each .document-tab, and spread stripHandlers
 * onto the strip. Attach onTabPointerDown/onTabKeyDown to .tab-main only.
 * Reordering is preview-only until release; cancellation never mutates application state. */
export function useTabReorder<T extends Keyed>(tabs:T[],onReorder:Dispatch<SetStateAction<T[]>>,stripRef:RefObject<HTMLDivElement|null>){
 const [preview,setPreview]=useState<string[]|null>(null);
 const [dragKey,setDragKey]=useState<string|null>(null);
 const latest=useRef({tabs,onReorder});latest.current={tabs,onReorder};
 const session=useRef<Drag|null>(null);
 const suppressUntil=useRef(0);
 const finishRef=useRef<(commit:boolean)=>void>(()=>{});
 const finish=(commit:boolean)=>{
  const drag=session.current;if(!drag)return;
  session.current=null;drag.cleanup();
  if(drag.strip.hasPointerCapture?.(drag.id))drag.strip.releasePointerCapture(drag.id);
  if(drag.dragging){
   suppressUntil.current=Date.now()+400;
   if(commit&&latest.current.tabs.some(tab=>tab.key===drag.key))latest.current.onReorder(current=>orderTabs(current,drag.keys));
  }
  setPreview(null);setDragKey(null);
 };
 finishRef.current=finish;
 useEffect(()=>()=>{const drag=session.current;session.current=null;drag?.cleanup();if(drag?.strip.hasPointerCapture?.(drag.id))drag.strip.releasePointerCapture(drag.id)},[]);
 // Closing/opening a tab during a gesture cancels it instead of restoring stale tabs.
 const membership=JSON.stringify(tabs.map(tab=>tab.key).sort());
 useEffect(()=>{if(session.current)finishRef.current(false)},[membership]);

 const onTabPointerDown=(event:ReactPointerEvent<HTMLElement>,key:string)=>{
  if(event.button!==0||!event.isPrimary||session.current)return;
  const target=event.target as HTMLElement;
  if(target.closest('button')!==event.currentTarget||!event.currentTarget.classList.contains('tab-main'))return;
  const strip=stripRef.current,tab=event.currentTarget.closest<HTMLElement>('[data-tab-key]');
  if(!strip||!tab||latest.current.tabs.length<2)return;
  suppressUntil.current=0;
  const rect=tab.getBoundingClientRect();
  const drag:Drag={key,id:event.pointerId,startX:event.clientX,x:event.clientX,offset:event.clientX-rect.left,width:rect.width,dragging:false,keys:latest.current.tabs.map(item=>item.key),strip,cleanup:()=>{}};
  session.current=drag;
  let frame=0,lastFrame=0;
  const update=()=>{
   if(session.current!==drag||!drag.dragging)return;
   const center=drag.x-drag.offset+drag.width/2;
   const others=Array.from(strip.querySelectorAll<HTMLElement>('[data-tab-key]')).filter(item=>item.dataset.tabKey!==key);
   const index=others.reduce((count,item)=>{const box=item.getBoundingClientRect();return count+(center>box.left+box.width/2?1:0)},0);
   const reordered=moveTab(drag.keys.map(key=>({key})),key,index).map(item=>item.key);
   if(reordered.some((key,index)=>key!==drag.keys[index])){drag.keys=reordered;setPreview(reordered)}
  };
  const tick=(time:number)=>{
   if(session.current!==drag)return;
   const box=strip.getBoundingClientRect();
   const speed=tabEdgeScroll(drag.x,box.left,box.right);
   const elapsed=lastFrame?Math.min(32,time-lastFrame):16;lastFrame=time;
   if(speed){const previous=strip.scrollLeft;strip.scrollLeft+=speed*elapsed/16;if(strip.scrollLeft!==previous)update()}
   frame=requestAnimationFrame(tick);
  };
  const move=(event:PointerEvent)=>{
   if(event.pointerId!==drag.id)return;
   if(event.pointerType==='mouse'&&event.buttons===0){finishRef.current(false);return}
   drag.x=event.clientX;
   if(!drag.dragging&&Math.abs(drag.x-drag.startX)<6)return;
   if(!drag.dragging){drag.dragging=true;setDragKey(key);strip.setPointerCapture?.(drag.id);frame=requestAnimationFrame(tick)}
   event.preventDefault();update();
  };
  const up=(event:PointerEvent)=>{if(event.pointerId===drag.id)finishRef.current(true)};
  const cancel=(event:PointerEvent)=>{if(event.pointerId===drag.id)finishRef.current(false)};
  const blur=()=>finishRef.current(false);
  const escape=(event:KeyboardEvent)=>{if(event.key==='Escape'){event.preventDefault();finishRef.current(false)}};
  window.addEventListener('pointermove',move,{passive:false});window.addEventListener('pointerup',up);
  window.addEventListener('pointercancel',cancel);window.addEventListener('blur',blur);window.addEventListener('keydown',escape);
  strip.addEventListener('lostpointercapture',cancel);
  drag.cleanup=()=>{cancelAnimationFrame(frame);window.removeEventListener('pointermove',move);window.removeEventListener('pointerup',up);window.removeEventListener('pointercancel',cancel);window.removeEventListener('blur',blur);window.removeEventListener('keydown',escape);strip.removeEventListener('lostpointercapture',cancel)};
 };
 const onClickCapture=(event:ReactMouseEvent<HTMLElement>)=>{
  if(event.detail!==0&&Date.now()<suppressUntil.current){event.preventDefault();event.stopPropagation();suppressUntil.current=0}
 };
 const onTabKeyDown=(event:ReactKeyboardEvent<HTMLElement>,key:string)=>{
  if(!event.altKey||!event.shiftKey||event.ctrlKey||event.metaKey||!['ArrowLeft','ArrowRight'].includes(event.key))return;
  event.preventDefault();finishRef.current(false);
  latest.current.onReorder(current=>moveTab(current,key,current.findIndex(tab=>tab.key===key)+(event.key==='ArrowLeft'?-1:1)));
 };
 return {orderedTabs:preview?orderTabs(tabs,preview):tabs,dragKey,onTabPointerDown,onTabKeyDown,
  stripHandlers:{onClickCapture,onDragStart:(event:ReactMouseEvent<HTMLElement>)=>event.preventDefault()}};
}
