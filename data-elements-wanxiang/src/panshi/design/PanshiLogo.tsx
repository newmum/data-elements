import { useId } from 'react';

/** Interlocking strata: a stable foundation and the data layers built on it. */
export function PanshiLogo({ className = '' }: { className?: string }) {
  const id = useId().replace(/:/g, '');
  return <svg className={`ps-logo ${className}`} viewBox="0 0 48 48" width="36" height="36" aria-hidden="true" focusable="false">
    <defs><linearGradient id={`${id}-face`} x1="4" y1="4" x2="42" y2="44" gradientUnits="userSpaceOnUse"><stop stopColor="#66A8FF"/><stop offset="1" stopColor="#2453BE"/></linearGradient><linearGradient id={`${id}-side`} x1="20" y1="12" x2="39" y2="44" gradientUnits="userSpaceOnUse"><stop stopColor="#3676DD"/><stop offset="1" stopColor="#173882"/></linearGradient></defs>
    <path d="M24 3 43 13.5v21L24 45 5 34.5v-21Z" fill={`url(#${id}-face)`}/>
    <path d="m24 24 19-10.5v21L24 45Z" fill={`url(#${id}-side)`}/>
    <path d="m10 14 14-7.7L38 14l-14 7.8Z" fill="#C4DFFF"/>
    <path d="m13 14 11-6 11 6-11 6Z" fill="#F2F7FF"/>
    <path d="m12 24 12 6.5 12-6.5v5l-12 6.5L12 29Z" fill="#DCEBFF"/>
    <path d="m12 32 12 6.5 12-6.5v3l-12 6.5L12 35Z" fill="#99C4FF"/>
    <path d="m17 13 7-3.9 7 3.9-7 3.9Z" fill="#508CE6"/>
  </svg>;
}
