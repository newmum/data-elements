import {useLayoutEffect,useRef,useState,type RefObject,type PointerEvent as ReactPointerEvent} from 'react';
import './tabOverflowScrollbar.css';

export function tabScrollbarGeometry(clientWidth:number,scrollWidth:number,scrollLeft:number){
 const track=Math.max(0,clientWidth-8),maxScroll=Math.max(0,scrollWidth-clientWidth);
 const width=maxScroll>0?Math.min(track,Math.max(24,track*clientWidth/scrollWidth)):track;
 const left=maxScroll>0?Math.max(0,Math.min(maxScroll,scrollLeft))/maxScroll*(track-width):0;
 return {track,width,left,maxScroll,visible:maxScroll>1&&track>0};
}

/** A real overlay: the native viewport remains unchanged on hover or focus. */
export default function TabOverflowScrollbar({viewportRef}:{viewportRef:RefObject<HTMLDivElement|null>}){
 const [metrics,setMetrics]=useState(()=>tabScrollbarGeometry(0,0,0));
 const cleanup=useRef<()=>void>(()=>{});
 useLayoutEffect(()=>{
  const viewport=viewportRef.current;if(!viewport)return;
  const measure=()=>{const next=tabScrollbarGeometry(viewport.clientWidth,viewport.scrollWidth,viewport.scrollLeft);setMetrics(previous=>JSON.stringify(previous)===JSON.stringify(next)?previous:next)};
  const resize=new ResizeObserver(measure),mutations=new MutationObserver(measure);resize.observe(viewport);mutations.observe(viewport,{childList:true,subtree:true,characterData:true});viewport.addEventListener('scroll',measure,{passive:true});measure();
  return()=>{resize.disconnect();mutations.disconnect();viewport.removeEventListener('scroll',measure);cleanup.current()};
 },[viewportRef]);
 function start(event:ReactPointerEvent<HTMLDivElement>){
  const viewport=viewportRef.current;if(!viewport||event.button!==0||!event.isPrimary)return;
  event.preventDefault();event.stopPropagation();cleanup.current();
  const origin=event.clientX,initial=viewport.scrollLeft,id=event.pointerId,target=event.currentTarget;
  const travel=metrics.track-metrics.width;
  const move=(e:PointerEvent)=>{if(e.pointerId===id&&travel>0)viewport.scrollLeft=initial+(e.clientX-origin)/travel*metrics.maxScroll};
  const stop=()=>{window.removeEventListener('pointermove',move);window.removeEventListener('pointerup',finish);window.removeEventListener('pointercancel',finish);window.removeEventListener('blur',stop);if(target.hasPointerCapture(id))target.releasePointerCapture(id);cleanup.current=()=>{}};
  const finish=(e:PointerEvent)=>{if(e.pointerId===id)stop()};
  window.addEventListener('pointermove',move);window.addEventListener('pointerup',finish);window.addEventListener('pointercancel',finish);window.addEventListener('blur',stop);target.setPointerCapture(id);cleanup.current=stop;
 }
 if(!metrics.visible)return null;
 return <div className="tab-scrollbar-overlay" onPointerDown={event=>{if(event.target!==event.currentTarget||!viewportRef.current)return;event.preventDefault();const rect=event.currentTarget.getBoundingClientRect();const travel=metrics.track-metrics.width;viewportRef.current.scrollLeft=travel>0?(event.clientX-rect.left-metrics.width/2)/travel*metrics.maxScroll:0}}>
  <div className="tab-scrollbar-thumb" role="scrollbar" aria-label="页签横向滚动" aria-controls="workspace-document-tabs" aria-orientation="horizontal" aria-valuemin={0} aria-valuemax={Math.round(metrics.maxScroll)} aria-valuenow={Math.round(viewportRef.current?.scrollLeft??0)} tabIndex={0} style={{width:metrics.width,transform:`translateX(${metrics.left}px)`}} onPointerDown={start} onKeyDown={event=>{const viewport=viewportRef.current;if(!viewport)return;if(event.key==='ArrowLeft')viewport.scrollLeft-=60;else if(event.key==='ArrowRight')viewport.scrollLeft+=60;else if(event.key==='Home')viewport.scrollLeft=0;else if(event.key==='End')viewport.scrollLeft=metrics.maxScroll;else return;event.preventDefault()}}/>
 </div>;
}
