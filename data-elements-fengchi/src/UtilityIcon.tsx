import type {HTMLAttributes, ReactNode} from 'react';

/** Original 24-unit utility symbols; matching optical weight across the workspace. */
type Props=HTMLAttributes<HTMLSpanElement>;
function symbol(name:string,art:ReactNode){
 return function UtilityIcon({className='',...props}:Props){return <span role="img" aria-label={name} {...props} className={`anticon utility-icon ${className}`}><svg width="1em" height="1em" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">{art}</svg></span>};
}
export const SunOutlined=symbol('sun',<><circle cx="12" cy="12" r="4"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M5 5l1.5 1.5m11 11L19 19M5 19l1.5-1.5m11-11L19 5"/></>);
export const MoonOutlined=symbol('moon',<path d="M20 14.5A8.5 8.5 0 0 1 9.5 4 8.5 8.5 0 1 0 20 14.5Z"/>);
