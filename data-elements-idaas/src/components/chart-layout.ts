/** Select readable x-axis labels without shrinking text when the panel gets narrower. */
export function chartTickIndices(count: number, plotWidth: number, minimumLabelGap = 52): number[] {
  if (!Number.isFinite(count) || count <= 0) return [];
  const n = Math.floor(count);
  if (n <= 0) return [];
  if (n === 1) return [0];
  const available = Number.isFinite(plotWidth) ? Math.max(0, plotWidth) : 0;
  const gap = Number.isFinite(minimumLabelGap) ? Math.max(1, minimumLabelGap) : 52;
  if (available < gap) return [n - 1];
  const slots = Math.min(n, Math.max(2, Math.floor(available / gap) + 1));
  return Array.from({ length: slots }, (_, i) => Math.round(i * (n - 1) / (slots - 1)));
}
