import type { PageKey } from '../app/config';
// Build-time asset imports produce hashed local URLs, including nested /wanxiang/ entry.
const assets = import.meta.glob('../assets/clear/*-*.svg', { eager: true, query: '?url', import: 'default' }) as Record<string, string>;
export function artworkFor(page: PageKey, mode: 'light' | 'dark'): string {
  const asset = assets[`../assets/clear/${page}-${mode}.svg`];
  if (!asset) throw new Error(`Missing resource artwork: ${page}/${mode}`);
  return asset;
}
export const layersDiagram = {
  light: new URL('../assets/clear/layers-diagram-light.svg', import.meta.url).href,
  dark: new URL('../assets/clear/layers-diagram-dark.svg', import.meta.url).href,
};
