import { useId } from 'react';
/** A faceted data nucleus: three coordinated planes, not an X or a crossed ribbon. */
export function BrandMark({ small = false }: { small?: boolean }) {
  const id = useId().replace(/:/g, '');
  return <svg className={`wx-brand-mark${small ? ' is-small' : ''}`} viewBox="0 0 40 44" aria-hidden="true" focusable="false">
    <defs>
      <linearGradient id={`${id}-top`} x1="0" y1="0" x2="1" y2="1"><stop stopColor="#B19AFF"/><stop offset="1" stopColor="#8252EB"/></linearGradient>
      <linearGradient id={`${id}-left`} x1="0" y1="0" x2="1" y2="1"><stop stopColor="#9563F4"/><stop offset="1" stopColor="#5C27CC"/></linearGradient>
      <linearGradient id={`${id}-right`} x1="0" y1="0" x2="1" y2="1"><stop stopColor="#7441D8"/><stop offset="1" stopColor="#431C9E"/></linearGradient>
    </defs>
    <path d="M20 2 37 11.5v20L20 42 3 32V12Z" fill="#A688FA" opacity=".12"/>
    <path d="M20 4 34 12 20 20 6 12Z" fill={`url(#${id}-top)`}/>
    <path d="M6 14.5 18.7 22v15.5L6 30Z" fill={`url(#${id}-left)`}/>
    <path d="M21.3 22 34 14.5V30l-12.7 7.5Z" fill={`url(#${id}-right)`}/>
    <path d="m7.8 23.7 10.9 6.5m2.6 0 11-6.5" stroke="#E5D9FF" strokeWidth="1.1" opacity=".65"/>
    <path d="M20 10.3 25.6 13.5 20 16.7l-5.6-3.2Z" fill="#FCFAFF" opacity=".95"/>
    <circle cx="20" cy="22.4" r="2.15" fill="#FBF8FF"/>
  </svg>;
}
