import type { CSSProperties } from 'react';
const paths: Record<string, string[]> = {
    edit: ['m15 3 6 6-12 12H3v-6Z', 'm13 5 6 6'],
    database: ['M4 6c0-4 16-4 16 0s-16 4-16 0Z', 'M4 6v6c0 4 16 4 16 0V6', 'M4 12v6c0 4 16 4 16 0v-6'],
    upload: ['M12 16V3', 'm7 8 5-5 5 5', 'M4 15v5h16v-5'], download: ['M12 3v13', 'm7 11 5 5 5-5', 'M4 19v2h16v-2'], file: ['M14 2H5v20h14V7Z', 'M14 2v6h5', 'M8 12h8M8 16h6'], table: ['M3 4h18v16H3z', 'M3 10h18M9 4v16M15 10v10'],
    history: ['M3 11a9 9 0 1 1 3 8', 'M3 4v7h7', 'M12 7v5l3 2'], check: ['m5 12 4 4L19 6'], search: ['M10.5 18a7.5 7.5 0 1 0 0-15 7.5 7.5 0 0 0 0 15Z', 'm16 16 5 5'],
    chevron: ['m9 5 7 7-7 7'], down: ['m6 9 6 6 6-6'], arrow: ['M4 12h16', 'm14 6 6 6-6 6'], close: ['m6 6 12 12M6 18 18 6'], lock: ['M5 10h14v11H5z', 'M8 10V6a4 4 0 0 1 8 0v4', 'M12 14v3'],
    shield: ['m12 2 9 4v6c0 5-9 10-9 10S3 17 3 12V6Z', 'm8 12 3 3 5-6'], police: ['M12 2 20 5v5c0 5-3.5 8.7-8 12-4.5-3.3-8-7-8-12V5Z', 'M12 5l1.5 3 3.3.5-2.4 2.3.6 3.2-3-1.7-3 1.7.6-3.2-2.4-2.3 3.3-.5Z'], info: ['M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z', 'M12 10v7M12 6v1'], warning: ['m12 3 10 18H2Z', 'M12 9v5M12 17v1'],
    settings: ['M4 6h16M4 12h16M4 18h16', 'M8 3v6M16 9v6M10 15v6'], plus: ['M12 4v16M4 12h16'], eye: ['M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12Z', 'M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z'],
    book: ['M3 3h7c2 0 2 1 2 1s0-1 2-1h7v17h-7c-2 0-2 1-2 1s0-1-2-1H3Z', 'M12 4v17'], key: ['M8 13a5 5 0 1 0 0-10 5 5 0 0 0 0 10Z', 'm12 12 9 9M17 17l3-3'], clock: ['M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z', 'M12 6v6l4 2'],
    trash: ['M3 6h18M9 6V3h6v3', 'M5 6l1 15h12l1-15M10 10v7M14 10v7'], refresh: ['M20 7a9 9 0 0 0-16 1M4 17a9 9 0 0 0 16-1', 'M20 2v5h-5M4 22v-5h5'], columns: ['M3 4h18v16H3zM9 4v16M15 4v16'], spark: ['m12 2 2.5 7.5L22 12l-7.5 2.5L12 22l-2.5-7.5L2 12l7.5-2.5Z'], folder: ['M3 5h6l2 3h10v12H3Z'], help: ['M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z', 'M9 8a3 3 0 0 1 6 0c0 2-3 2-3 5M12 16v1'], user: ['M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z', 'M4 21v-3c0-6 16-6 16 0v3'], save: ['M4 3h13l3 3v15H4Z', 'M8 3v6h8V3M8 21v-8h8v8'], expand: ['M3 9V3h6M15 3h6v6M21 15v6h-6M9 21H3v-6']
};
export default function Icon({ name, size = 18, style }: {
    name: string;
    size?: number;
    style?: CSSProperties;
}) { return <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.65" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" style={{ flexShrink: 0, verticalAlign: 'middle', ...style }}>{(paths[name] || paths.file).map((d, i) => <path d={d} key={i}/>)}</svg>; }
