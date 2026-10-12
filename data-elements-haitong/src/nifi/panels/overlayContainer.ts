/** Keep themed overlays outside the graph's overflow/paint clipping boundary. */
export function getNifiOverlayContainer(trigger?: HTMLElement): HTMLElement {
  const ownerDocument = trigger?.ownerDocument ?? document;
  return trigger?.closest<HTMLElement>('.nifi-workspace')
    ?? ownerDocument.querySelector<HTMLElement>('.nifi-workspace')
    ?? ownerDocument.body;
}
