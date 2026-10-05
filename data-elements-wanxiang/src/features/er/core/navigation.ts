export type StudioPage = 'canvas' | 'diagrams' | 'capabilities';
export const STUDIO_NAV_EVENT = 'wanxiang:studio-navigate';
export const STUDIO_ACTION_EVENT = 'wanxiang:studio-action';
/** A navigation intent. The host is the only owner of browser history. */
export function requestStudioNavigation(page: StudioPage, diagramId?: string) {
  if (typeof window !== 'undefined') window.dispatchEvent(new CustomEvent(STUDIO_NAV_EVENT, { detail: { page, diagramId } }));
}
export function studioPath(page: StudioPage, diagramId?: string): string {
  if (page === 'diagrams') return '/governance/metadata/models';
  if (page === 'capabilities') return '/governance/metadata/sources';
  return `/governance/metadata/er${diagramId ? `/${encodeURIComponent(diagramId)}` : ''}`;
}
