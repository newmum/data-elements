import type {HTMLAttributes,MouseEvent,SyntheticEvent} from 'react';

const rowControls='button,a,input,select,textarea,[role="button"],[role="link"],[role="checkbox"],[role="switch"],[contenteditable="true"]';
function isRowControl(event:SyntheticEvent<HTMLTableRowElement>){
 const target=event.target as Element;
 const control=target.closest?.(rowControls);
 return Boolean(control&&control!==event.currentTarget&&event.currentTarget.contains(control));
}

/** A list row selects on one click and opens once on a double-click or focused Enter. */
export function objectRowHandlers(open:()=>void,select?:()=>void):HTMLAttributes<HTMLTableRowElement>{
 return {
  tabIndex:0,
  onClick:event=>{if(!isRowControl(event))select?.()},
  onDoubleClick:event=>{if(!isRowControl(event)){event.preventDefault();open()}},
  onKeyDown:event=>{if(event.key==='Enter'&&event.target===event.currentTarget){event.preventDefault();open()}},
 };
}

/** Keep immediate button/keyboard activation while ignoring the second click of a double-click. */
export function openObjectControl(event:MouseEvent<HTMLElement>,open:()=>void){
 event.stopPropagation();
 if(event.detail<2)open();
}
