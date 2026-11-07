import { useId, type ReactNode } from 'react';
import ConnectionIcon from './ConnectionIcon';
// Non-connection artwork restored from published version 2 (e9af2c0).

export type NativeIconName = 'connection' | 'new-query' | 'table' | 'view' | 'function' | 'query' | 'export' | 'refresh' | 'history' | 'database' | 'folder' | 'user' | 'backup' | 'automation' | 'model' | 'bi' | 'server';
export interface NativeIconProps { name: NativeIconName; size?: number; className?: string }

/** Original, scalable desktop-style artwork, with independent SVG paint servers. */
export default function NativeIcon({ name, size = 32, className }: NativeIconProps) {
  const id = `ni${useId().replace(/:/g, '')}`;
  if(name === 'connection') return <ConnectionIcon size={size} className={className}/>;
  const paint = (name: string) => `url(#${id}-${name})`;
  const plus = <g><circle cx="25" cy="25" r="6" fill={paint('green')} stroke="#438a39" strokeWidth=".65"/><path d="M25 21.7v6.6M21.7 25h6.6" stroke="white" strokeWidth="1.8" strokeLinecap="round"/></g>;
  const sheet = (x: number, y: number, color = 'blue') => <g><rect x={x} y={y} width="18" height="21" rx="1.8" fill={paint('paper')} stroke={color === 'red' ? '#b35351' : '#34789d'} strokeWidth=".8"/><path d={`M${x + 2} ${y + 1}h14a1 1 0 0 1 1 1v3H${x + 1}v-3a1 1 0 0 1 1-1`} fill={paint(color)}/><path d={`M${x + 3} ${y + 8}h12m-12 4h12m-12 4h9`} stroke={color === 'red' ? '#d69490' : '#87b5ce'} strokeWidth="1.4"/><path d={`M${x + 7} ${y + 7}v11m5-11v11`} stroke="#a9c5d5" strokeWidth=".7"/></g>;
  const table = <g><rect x="3" y="5" width="26" height="23" rx="2.2" fill={paint('paper')} stroke="#3c87ad" strokeWidth=".8"/><path d="M5 5.5h22q1.5 0 1.5 1.5v5h-25V7q0-1.5 1.5-1.5" fill={paint('blue')}/><path d="M4.5 7h23" stroke="#b1e5ff" strokeOpacity=".7"/><path d="M11 12v15M20 12v15M4 17h24M4 22h24" stroke="#7db6d1" strokeWidth=".9"/><path d="M5 13h5v3H5zm7 5h7v3h-7zm9 5h6v3h-6z" fill="#9bd7ef"/><path d="M6 9h2m2 0h2" stroke="#fff" strokeWidth="1.2"/></g>;
  const cylinder = <g><path d="M5 8v17c0 6 22 6 22 0V8" fill={paint('blue')} stroke="#357ca5" strokeWidth=".8"/><path d="M5 14c0 6 22 6 22 0m-22 7c0 6 22 6 22 0" fill="none" stroke="#e1f5ff" strokeOpacity=".75"/><ellipse cx="16" cy="8" rx="11" ry="5" fill={paint('light')} stroke="#357ca5" strokeWidth=".8"/><path d="M7 8c1-4 16-4 18 0" fill="none" stroke="white" strokeOpacity=".75"/></g>;
  const cube = (x: number, y: number, s = 8) => <g stroke="#bd7727" strokeWidth=".5" strokeLinejoin="round"><path d={`M${x} ${y}l${s / 2} ${-s / 3}l${s / 2} ${s / 3}l${-s / 2} ${s / 3}Z`} fill="#ffe5a0"/><path d={`M${x} ${y}v${s * .75}l${s / 2} ${s / 3}v${-s * .75}Z`} fill={paint('gold')}/><path d={`M${x + s} ${y}v${s * .75}l${-s / 2} ${s / 3}v${-s * .75}Z`} fill="#d88b36"/></g>;
  let art: ReactNode;
  switch (name) {
    case 'new-query': case 'query': art = <g>{sheet(3, 3, 'red')}{sheet(10, 9)}{name === 'new-query' && plus}</g>; break;
    case 'table': art = table; break;
    case 'view': art = <g transform="translate(0 -1)">{table}<g fill={paint('blue')} stroke="#236b9e" strokeWidth=".8"><path d="M9 19l2-6h4l1 6m2 0 1-6h4l2 6"/><rect x="11" y="19" width="12" height="4" rx="2"/><circle cx="10" cy="23" r="5.5"/><circle cx="24" cy="23" r="5.5"/></g><g fill="#bcf0ff" stroke="#194f82" strokeWidth="1.2"><circle cx="10" cy="23" r="3.6"/><circle cx="24" cy="23" r="3.6"/></g><path d="m8 21 3-1m11 1 3-1" stroke="white" strokeWidth="1.3" strokeLinecap="round"/></g>; break;
    case 'function': art = <g fill={paint('blue')} stroke="#2574b2" strokeWidth=".4"><path d="M20 4c-1.1-.6-2.2-.9-3.7-.9-4.2 0-5.7 3.6-6.5 8.9H6.5L6 15h3.3L7.7 25c-.4 2.4-1.1 3.5-2.5 3.5-.6 0-1.3-.2-1.9-.5L2.5 31c1 .4 2 .6 3.1.6 4.3 0 6-3.3 6.8-8.2l1.4-8.4h4l.5-3h-4l.5-3c.4-2.3 1.1-3.6 2.7-3.6.7 0 1.2.2 1.8.5z"/><path d="m19 16 2.3 5.4L17 28h3.7l2.6-4.3L25 28h4l-3.4-6.7 4.4-5.3h-3.7l-2.4 3.2L22.8 16z"/></g>; break;
    case 'export': art = <g>{sheet(3, 4)}<path d="M16 17h7V11l8 9-8 9v-6h-7z" fill={paint('green')} stroke="#4b8c3f" strokeWidth=".8"/><path d="M17 18.5h7v-4" fill="none" stroke="#dbf5c7"/></g>; break;
    case 'refresh': art = <g fill={paint('green')} stroke="#4c923d" strokeWidth=".6"><path d="M26 8l3-4v12H17l4-4a8 8 0 0 0-14 5H2A13 13 0 0 1 26 8z"/><path d="m6 24-3 4V16h12l-4 4a8 8 0 0 0 14-5h5A13 13 0 0 1 6 24z"/></g>; break;
    case 'history': art = <g><circle cx="17" cy="16" r="12" fill={paint('paper')} stroke="#899da9" strokeWidth="2"/><path d="M17 8v9l6 3" fill="none" stroke="#527e99" strokeWidth="2" strokeLinecap="round"/><path d="M7 8H2V3" fill="none" stroke="#4b99c6" strokeWidth="2.6" strokeLinecap="round" strokeLinejoin="round"/><path d="m2 8 5-5" stroke="#4b99c6" strokeWidth="2"/></g>; break;
    case 'folder': art = <g><path d="M3 7q0-2 2-2h8l3 4h11q2 0 2 2v15H3z" fill={paint('gold')} stroke="#b88c36" strokeWidth=".8"/><path d="M2 12h28l-3 15H5z" fill={paint('gold')} stroke="#c6973d" strokeWidth=".8"/><path d="M4 13h24" stroke="#fff3bf" strokeWidth="1.2"/></g>; break;
    case 'user': art = <g><path d="M4 28v-4c0-6 6-9 12-9s12 3 12 9v4z" fill={paint('gold')} stroke="#c58b35" strokeWidth=".7"/><path d="m9 19 7 7 7-7" fill="#f2a33e"/><ellipse cx="16" cy="10" rx="6.7" ry="7.6" fill={paint('gold')} stroke="#c58b35" strokeWidth=".7"/><path d="M12 5c2-2 5-2 7 0" fill="none" stroke="#fff3b9" strokeWidth="1.2" strokeLinecap="round"/></g>; break;
    case 'backup': art = <g><rect x="3" y="3" width="26" height="26" rx="5" fill={paint('slate')} stroke="#687e8d" strokeWidth=".8"/><rect x="4.5" y="4.5" width="23" height="23" rx="4" fill="none" stroke="#ffffff" strokeOpacity=".3"/><path d="M10 11a8 8 0 1 1-1 10" fill="none" stroke="white" strokeWidth="3" strokeLinecap="round"/><path d="M7 7v10l9-3z" fill="white"/></g>; break;
    case 'automation': art = <g><path d="M16 7V3" stroke="#418c92" strokeWidth="2"/><circle cx="16" cy="3" r="2" fill="#a2dad6"/><rect x="4" y="8" width="24" height="19" rx="4" fill={paint('teal')} stroke="#36868b" strokeWidth=".8"/><path d="M2 13v8m28-8v8" stroke="#529b9d" strokeWidth="3" strokeLinecap="round"/><rect x="7" y="12" width="18" height="8" rx="2" fill="#e5ffff"/><circle cx="11" cy="16" r="2" fill="#337883"/><circle cx="21" cy="16" r="2" fill="#337883"/><path d="M12 24h8" stroke="#e5ffff" strokeWidth="2" strokeLinecap="round"/></g>; break;
    case 'model': art = <g><path d="M16 8v14M8 19h16" stroke="#b9a58b" strokeWidth="1.5"/>{cube(12, 5)}{cube(2, 18)}{cube(22, 18)}{cube(12, 23)}</g>; break;
    case 'bi': art = <g stroke="#9856a2" strokeWidth=".7"><path d="M3 16h6v13H3z" fill={paint('purple')}/><path d="M13 8h6v21h-6z" fill={paint('purple')}/><path d="M23 2h6v27h-6z" fill={paint('purple')}/><path d="M4 17h4m6-8h4m6-6h4" stroke="#ffd9f9"/><path d="M2 30h28" stroke="#b9a4bd"/></g>; break;
    case 'server': art = <g>{[3, 12, 21].map(y => <g key={y}><rect x="5" y={y} width="22" height="8" rx="1.6" fill={paint('slate')} stroke="#627888" strokeWidth=".7"/><path d={`M8 ${y + 3}h10m-10 2h10`} stroke="#dce7ee" strokeWidth=".7"/><circle cx="23" cy={y + 4} r="1.4" fill="#a4e96f"/></g>)}</g>; break;
    case 'database': default: art = cylinder;
  }
  return <svg width={size} height={size} viewBox="0 0 32 32" className={`native-icon ${className||''}`} aria-hidden="true" focusable="false" style={{ flexShrink: 0, overflow: 'visible', verticalAlign: 'middle' }}><defs>{[['blue', '#8ed4f6', '#2784c2'], ['light', '#e7f7ff', '#8dc9ed'], ['paper', '#ffffff', '#d9eaf3'], ['slate', '#c0cbd2', '#657c8b'], ['green', '#c0e88e', '#5eae47'], ['red', '#f6b5b1', '#d75c5b'], ['gold', '#ffdf88', '#e9a540'], ['teal', '#a3e2df', '#4ca5ac'], ['purple', '#e7a6d9', '#a45cbe']].map(([key, top, bottom]) => <linearGradient key={key} id={`${id}-${key}`} x1="0" y1="0" x2=".25" y2="1"><stop stopColor={top}/><stop offset="1" stopColor={bottom}/></linearGradient>)}</defs>{art}</svg>;
}

export function DatabaseBadge({ type, size = 32 }: { type: string; size?: number }) {
  const id = `db${useId().replace(/:/g, '')}`;
  const key = type.toLowerCase().replace(/[\s_-]/g, '');
  const palette: Record<string, [string, string, string]> = {
    mysql: ['#9bca6e', '#51832e', 'M'], postgresql: ['#91bddb', '#39739d', 'P'], postgres: ['#91bddb', '#39739d', 'P'],
    sqlserver: ['#efba78', '#b6732b', 'S'], mssql: ['#efba78', '#b6732b', 'S'], oracle: ['#ef9891', '#c64d44', 'O'],
    sqlite: ['#8bc9c2', '#3b8f87', 'S'], mariadb: ['#e5cca2', '#9d7a49', 'M'], mongodb: ['#d1af87', '#906b43', 'M'],
    redis: ['#f4ac9b', '#cc6352', 'R'], snowflake: ['#96dcef', '#389fbb', '❄'],
  };
  const [light, dark, letter] = palette[key] || ['#a5c4d8', '#557f9a', 'D'];
  return <svg width={size} height={size} viewBox="0 0 32 32" className="native-icon" aria-hidden="true" focusable="false" style={{ flexShrink: 0, verticalAlign: 'middle' }}><defs><linearGradient id={id} x1="0" x2="1"><stop stopColor={dark}/><stop offset=".35" stopColor={light}/><stop offset=".7" stopColor={light}/><stop offset="1" stopColor={dark}/></linearGradient></defs><path d="M4 8v17c0 7 24 7 24 0V8" fill={`url(#${id})`} stroke={dark} strokeWidth=".8"/><path d="M4 23c1 6 23 6 24 0" fill="none" stroke="#fff" strokeOpacity=".3"/><ellipse cx="16" cy="8" rx="12" ry="5" fill={light} stroke={dark} strokeWidth=".8"/><path d="M6 8c1-4 18-5 20 0" fill="none" stroke="white" strokeOpacity=".5"/><text x="16" y="24" fill="white" stroke={dark} strokeWidth=".25" paintOrder="stroke" fontFamily="Arial, sans-serif" fontSize="14" fontWeight="700" textAnchor="middle">{letter}</text></svg>;
}
