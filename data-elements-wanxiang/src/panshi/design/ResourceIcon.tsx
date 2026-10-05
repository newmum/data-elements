import type { SVGProps } from 'react';

/** Semantic 24px vector icons. No bitmap masks, font glyphs or screenshot crops. */
export const RESOURCE_ICON_PATHS: Record<string, readonly string[]> = {
  overview: ['m3 10 9-7 9 7', 'M5 9v11h5v-6h4v6h5V9'],
  layers: ['m3 7 9-4 9 4-9 4Z', 'm3 12 9 4 9-4', 'm3 17 9 4 9-4'],
  databases: ['M3 3h7v7H3Z', 'M14 3h7v7h-7Z', 'M3 14h7v7H3Z', 'M14 14h7v7h-7Z'],
  bindings: ['M9 3v4M15 3v4', 'M6 7h12v3a6 6 0 0 1-12 0Z', 'M12 16v5', 'M9 21h6'],
  models: ['m12 3 8 4v10l-8 4-8-4V7Z', 'm4 7 8 4 8-4', 'M12 11v10'],
  standardization: ['M3 4h7v5H3Z', 'M14 15h7v5h-7Z', 'M6 9v8h8', 'm14 6 2 2 4-4'],
  materialization: ['M3 3h8v8H3Z', 'M14 14h7v7h-7Z', 'M15 5h4v5', 'm16 8 3 3 3-3', 'M5 15v4h4'],
  logs: ['M8 3h11v7', 'M8 6h8M8 10h4', 'M5 3v11', 'M5 14a4 4 0 1 0 0 8h13a3 3 0 0 0 0-6', 'M16 10v5l3 2'],
  warehouse: ['m3 8 9-5 9 5v12H3Z', 'M7 20V10h10v10', 'M7 14h10M7 17h10', 'M5 8h.01M19 8h.01'],
  'catalog-overview': ['M3 4h7v7H3Z', 'M14 4h7v4h-7Z', 'M14 12h7M14 16h7M14 20h7', 'M3 15h7v5H3Z'],
  'catalog-entries': ['M4 3h10l4 4v5', 'M14 3v5h4', 'M4 3v18h7', 'm12 18 7-7 3 3-7 7-4 1Z', 'M7 8h3M7 12h5'],
  'catalog-reviews': ['M5 3h10l4 4v6', 'M15 3v5h4', 'M5 3v18h7', 'M8 9h3M8 13h3', 'm13 17 3 3 6-7'],
  table: ['M3 4h18v16H3Z', 'M3 9h18M3 14h18M9 9v11'],
  field: ['M5 4h14v16H5Z', 'M8 8h8M8 12h5M8 16h8'],
  relation: ['M3 4h6v6H3Z', 'M15 14h6v6h-6Z', 'M9 7h3v10h3'],
  check: ['m5 12 4 4L19 6'],
  cursor: ['m5 3 14 9-7 1-3 7Z'],
  hand: ['M8 12V5a2 2 0 0 1 4 0v6', 'M12 10V4a2 2 0 0 1 4 0v8', 'M16 10V7a2 2 0 0 1 4 0v7c0 4-3 7-7 7-2 0-4-1-6-3l-4-5a2 2 0 0 1 3-2l2 2'],
  key: ['M4 8a4 4 0 1 0 8 0 4 4 0 0 0-8 0', 'm11 11 9 9M17 17l3-3M14 14l3-3'],
};
export type ResourceIconName = keyof typeof RESOURCE_ICON_PATHS;
export function ResourceIcon({ name, size = 24, className = '', ...props }: SVGProps<SVGSVGElement> & { name: string; size?: number }) {
  const paths = RESOURCE_ICON_PATHS[name] ?? RESOURCE_ICON_PATHS.table;
  return <svg {...props} width={size} height={size} viewBox="0 0 24 24" className={`ps-icon ${className}`} fill="none" stroke="currentColor" strokeWidth={1.8} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false">
    {paths.map((d, index) => <path key={index} d={d} />)}
  </svg>;
}
