import {useCallback, useEffect, useLayoutEffect, useRef, useState} from 'react';
import type {PointerEvent as ReactPointerEvent, MouseEvent as ReactMouseEvent} from 'react';
import type {Cell} from './data';
import {emptyGridSelection, edgeScrollDelta, gridCellKey, gridRowKey, rectangularSelection, type GridCellAddress, type GridSelection} from './gridRangeSelection';
import './gridRangeSelection.css';

const selector = '[data-grid-row][data-grid-column]';
const interactive = 'input,textarea,select,button,a,[contenteditable="true"],[role="textbox"]';
function address(element: HTMLElement): GridCellAddress {
  return {rowId: JSON.parse(element.dataset.gridRow!)[1] as Cell, column: element.dataset.gridColumn!};
}
function layout(root: HTMLElement) {
  const elements = Array.from(root.querySelectorAll<HTMLElement>(selector));
  const rows = new Map<string,Cell>(), columns = new Set<string>();
  for (const element of elements) {const cell = address(element);rows.set(gridRowKey(cell.rowId),cell.rowId);columns.add(cell.column);}
  return {elements, rowIds: [...rows.values()], columns: [...columns], signature: JSON.stringify([[...rows.keys()],[...columns]])};
}
function nearestCell(root: HTMLElement, x: number, y: number) {
  // Use each TD's full box, including padding. Clamp to the nearest rendered cell
  // when dragging outside the viewport, over scrollbars, or across virtual edges.
  let nearest: HTMLElement | undefined, distance = Infinity;
  for (const cell of root.querySelectorAll<HTMLElement>(selector)) {
    const rect = (cell.closest('td') || cell).getBoundingClientRect();
    if (!rect.width || !rect.height) continue;
    const dx = Math.max(rect.left-x,0,x-rect.right), dy = Math.max(rect.top-y,0,y-rect.bottom);
    const next = dx*dx+dy*dy;
    if (next < distance) {distance=next;nearest=cell;}
  }
  return nearest ? address(nearest) : undefined;
}
function scrollParents(start: HTMLElement, root: HTMLElement) {
  const parents: HTMLElement[] = [];
  for (let node: HTMLElement|null = start; node; node=node.parentElement) {
    if (node.scrollHeight>node.clientHeight+1 || node.scrollWidth>node.clientWidth+1) {
      const style=getComputedStyle(node);
      if (/(auto|scroll)/.test(`${style.overflowX} ${style.overflowY}`)) parents.push(node);
    }
    if(node===root) break;
  }
  return parents;
}

export function useGridRangeSelection({scopeKey,onFocusCell}: {scopeKey: string; onFocusCell?: (cell: GridCellAddress) => void}) {
  const containerRef=useRef<HTMLDivElement>(null);
  const [selection,setSelection]=useState<GridSelection>(emptyGridSelection);
  const selectedRef=useRef(selection), focusRef=useRef(onFocusCell);
  const cleanupRef=useRef<(() => void)|null>(null), suppressClick=useRef(false);
  const signatureRef=useRef('');
  focusRef.current=onFocusCell;
  const apply=useCallback((next: GridSelection)=>{selectedRef.current=next;setSelection(next);},[]);
  const clearSelection=useCallback(()=>{cleanupRef.current?.();apply(emptyGridSelection());},[apply]);
  const selectCell=useCallback((rowId: Cell,column: string)=>{
    apply(rectangularSelection([rowId],[column],{rowId,column},{rowId,column}));
    focusRef.current?.({rowId,column});
  },[apply]);
  useLayoutEffect(()=>{clearSelection();signatureRef.current='';},[scopeKey,clearSelection]);
  // AntD sorts rows internally. Invalidate an old rectangle when physical order
  // changes, even if the source rows prop has not changed.
  useEffect(()=>{
    const root=containerRef.current;if(!root)return;
    signatureRef.current=layout(root).signature;
    const observer=new MutationObserver(()=>{
      const next=layout(root).signature;
      if(next!==signatureRef.current){signatureRef.current=next;clearSelection();}
    });
    observer.observe(root,{childList:true,subtree:true});
    return()=>{observer.disconnect();cleanupRef.current?.();};
  },[scopeKey,clearSelection]);

  const begin=(event: ReactPointerEvent<HTMLElement>,cell: GridCellAddress)=>{
    if(event.button!==0 || event.isPrimary===false || (event.pointerType && event.pointerType!=='mouse' && event.pointerType!=='pen'))return;
    if((event.target as Element).closest(interactive))return;
    const root=containerRef.current;if(!root)return;
    cleanupRef.current?.();suppressClick.current=false;
    const snapshot=layout(root);signatureRef.current=snapshot.signature;
    selectCell(cell.rowId,cell.column);
    const startX=event.clientX,startY=event.clientY,pointerId=event.pointerId;
    let x=startX,y=startY,dragging=false,finished=false,frame=0,lastKey=gridCellKey(cell.rowId,cell.column);
    const parents=scrollParents(event.currentTarget,root);
    const update=()=>{
      const end=nearestCell(root,x,y);if(!end)return;
      const key=gridCellKey(end.rowId,end.column);if(key===lastKey)return;lastKey=key;
      apply(rectangularSelection(snapshot.rowIds,snapshot.columns,cell,end));
    };
    const scrollStep=()=>{
      if(!dragging || finished)return;
      const outer=root.getBoundingClientRect();
      for(const parent of parents){
        const rect=parent.getBoundingClientRect();
        const left=Math.max(rect.left,outer.left),right=Math.min(rect.right,outer.right),top=Math.max(rect.top,outer.top),bottom=Math.min(rect.bottom,outer.bottom);
        if(parent.scrollWidth>parent.clientWidth+1)parent.scrollLeft+=edgeScrollDelta(x,left,right);
        if(parent.scrollHeight>parent.clientHeight+1)parent.scrollTop+=edgeScrollDelta(y,top,bottom);
      }
      // Scroll synchronously before hit-testing so the rectangle reaches newly
      // exposed cells even when pointer events and pointerup precede the next frame.
      update();
    };
    const tick=()=>{
      if(!dragging || finished)return;
      scrollStep();frame=requestAnimationFrame(tick);
    };
    const move=(e:PointerEvent)=>{
      if(e.pointerId!==pointerId)return;
      if((e.buttons&1)===0){finish();return;}
      x=e.clientX;y=e.clientY;
      if(!dragging && Math.hypot(x-startX,y-startY)>=4){
        dragging=true;root.dataset.rangeDragging='true';document.getSelection()?.removeAllRanges();frame=requestAnimationFrame(tick);
      }
      if(dragging){e.preventDefault();scrollStep();}
    };
    const finish=()=>{
      if(finished)return;finished=true;
      if(dragging)suppressClick.current=true;
      cancelAnimationFrame(frame);delete root.dataset.rangeDragging;
      window.removeEventListener('pointermove',move);window.removeEventListener('pointerup',up);window.removeEventListener('pointercancel',up);window.removeEventListener('blur',finish);
      cleanupRef.current=null;
    };
    const up=(e:PointerEvent)=>{if(e.pointerId===pointerId)finish();};
    cleanupRef.current=finish;
    window.addEventListener('pointermove',move,{passive:false});window.addEventListener('pointerup',up);window.addEventListener('pointercancel',up);window.addEventListener('blur',finish);
  };
  const isSelected=(rowId:Cell,column:string)=>selectedRef.current.cells.some(row=>row.some(cell=>gridCellKey(cell.rowId,cell.column)===gridCellKey(rowId,column)));
  const getCellProps=(rowId:Cell,column:string)=>({
    'data-grid-row':gridRowKey(rowId), 'data-grid-column':column,
    'data-range-selected':isSelected(rowId,column)?'true':undefined,
    className:isSelected(rowId,column)?'range-selected-cell':'',
    onPointerDown:(e:ReactPointerEvent<HTMLElement>)=>begin(e,{rowId,column}),
    onClickCapture:(e:ReactMouseEvent<HTMLElement>)=>{if(suppressClick.current){suppressClick.current=false;e.preventDefault();e.stopPropagation();}},
    onContextMenu:(e:ReactMouseEvent<HTMLElement>)=>{if(!(e.target as Element).closest(interactive)&&!isSelected(rowId,column))selectCell(rowId,column);},
  });
  return {containerRef,selection,getCellProps,isSelected,clearSelection,selectCell};
}
