import {useEffect,useRef,type PointerEvent as ReactPointerEvent} from 'react';
export const clampPaneWidth=(width:number,min:number,max:number)=>Math.max(min,Math.min(Math.max(min,max),width));
export default function PaneResizeHandle({side,value,min,max,onChange,onReset}:{side:'left'|'right';value:number;min:number;max:number;onChange:(width:number)=>void;onReset:()=>void}){
 const cleanup=useRef<()=>void>(()=>{});
 useEffect(()=>()=>cleanup.current(),[]);
 function start(event:ReactPointerEvent<HTMLDivElement>){
  if(event.button!==0||!event.isPrimary)return;
  event.preventDefault();cleanup.current();
  const target=event.currentTarget,id=event.pointerId,origin=event.clientX,initial=value;
  const previousCursor=document.body.style.cursor,previousSelection=document.body.style.userSelect;
  document.body.style.cursor='col-resize';document.body.style.userSelect='none';target.setAttribute('data-resizing','true');
  const stop=()=>{window.removeEventListener('pointermove',move);window.removeEventListener('pointerup',finish);window.removeEventListener('pointercancel',finish);window.removeEventListener('blur',stop);if(target.hasPointerCapture(id))target.releasePointerCapture(id);document.body.style.cursor=previousCursor;document.body.style.userSelect=previousSelection;target.removeAttribute('data-resizing');cleanup.current=()=>{};};
  const move=(e:PointerEvent)=>{if(e.pointerId!==id)return;if(e.pointerType==='mouse'&&e.buttons===0){stop();return;}e.preventDefault();onChange(clampPaneWidth(initial+(e.clientX-origin)*(side==='left'?1:-1),min,max));};
  const finish=(e:PointerEvent)=>{if(e.pointerId===id)stop();};
  window.addEventListener('pointermove',move,{passive:false});window.addEventListener('pointerup',finish);window.addEventListener('pointercancel',finish);window.addEventListener('blur',stop);target.setPointerCapture(id);cleanup.current=stop;
 }
 return <div className={`pane-resizer pane-resizer-${side}`} role="separator" aria-label={side==='left'?'调整连接栏宽度':'调整信息栏宽度'} aria-orientation="vertical" aria-valuemin={min} aria-valuemax={max} aria-valuenow={Math.round(value)} tabIndex={0} title="拖动调整宽度，双击恢复默认" onPointerDown={start} onDoubleClick={onReset} onKeyDown={event=>{let next=value;if(event.key==='Home')next=min;else if(event.key==='End')next=max;else if(event.key==='ArrowLeft')next+=side==='left'?-10:10;else if(event.key==='ArrowRight')next+=side==='left'?10:-10;else return;event.preventDefault();onChange(clampPaneWidth(next,min,max));}}/>;
}
