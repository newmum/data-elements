import { moduleIconPaths } from './iconPaths';
export function ModuleIcon({ name, className = '' }: { name: string; className?: string }) {
  const data = moduleIconPaths[name] ?? moduleIconPaths.catalog;
  return <span className={`wx-module-icon ${className}`} data-icon={name} aria-hidden="true"><svg viewBox="0 0 24 24" fill="none" focusable="false">{data.accents.map((d,i)=><path key={`a${i}`} d={d} fill="currentColor" opacity=".13" />)}<g stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round">{data.lines.map((d,i)=><path key={i} d={d} />)}</g></svg></span>;
}
