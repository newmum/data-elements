/** Keep NiFi overlays inside its workbench so local light/dark tokens inherit. */
export function getNifiOverlayContainer(): HTMLElement {
  return document.querySelector<HTMLElement>('.nifi-workspace') ?? document.body;
}
