export const paneToggleDirection=(side:'left'|'right',expanded:boolean)=>side==='left'?(expanded?'left':'right'):(expanded?'right':'left');

export default function PaneToggleIcon({side,expanded}:{side:'left'|'right';expanded:boolean}){
 const direction=paneToggleDirection(side,expanded);
 return <span className="anticon pane-toggle-symbol" aria-hidden="true" data-direction={direction}><svg width="14" height="14" viewBox="0 0 16 16" fill="none" stroke="currentColor" strokeWidth="1.25" strokeLinecap="round" strokeLinejoin="round">
  <path d={side==='left'?'M2.5 3v10':'M13.5 3v10'}/>
  <path d={direction==='left'?'M10.5 4.5 7 8l3.5 3.5M7 8h6':'M5.5 4.5 9 8l-3.5 3.5M3 8h6'}/>
 </svg></span>;
}
