import type { MetadataSnapshot } from '../types/domain';
import type { ViewModel } from './model';
export interface SearchResult { key: string; kind: 'entity' | 'field' | 'diagram'; id: string; fieldId?: string; title: string; subtitle: string; score: number }
export function searchWorkspace(snapshot: MetadataSnapshot, diagrams: ViewModel[], query: string, limit = 40): SearchResult[] {
  const needle = query.trim().toLocaleLowerCase();
  const results: SearchResult[] = [];
  const rank = (value: string) => !needle ? 1 : value.toLocaleLowerCase() === needle ? 100 : value.toLocaleLowerCase().startsWith(needle) ? 80 : value.toLocaleLowerCase().includes(needle) ? 50 : 0;
  for (const e of snapshot.entities) {
    const title = e.displayName || e.name;
    const score = Math.max(rank(e.name), rank(title), needle ? rank(e.comment || '') : 0);
    const source = snapshot.sources.find(s => s.id === e.sourceId)?.name || '';
    if (score) results.push({ key: `entity-${e.id}`, kind: 'entity', id: e.id, title, subtitle: `${e.name} · ${source}`, score });
    if (needle) for (const f of e.fields) {
      const fieldScore = Math.max(rank(f.name), rank(f.path), rank(f.comment || ''));
      if (fieldScore) results.push({ key: `field-${f.id}`, kind: 'field', id: e.id, fieldId: f.id, title: f.path, subtitle: `${title} · ${f.nativeType}${f.comment ? ` · ${f.comment}` : ''}`, score: fieldScore - 3 });
    }
  }
  for (const d of diagrams) {
    const score = Math.max(rank(d.name), needle ? rank(d.description || '') : 0);
    if (score) results.push({ key: `diagram-${d.id}`, kind: 'diagram', id: d.id, title: d.name, subtitle: `关系图 · ${d.nodes.length} 个实体`, score });
  }
  return results.sort((a, b) => b.score - a.score || a.title.localeCompare(b.title, 'zh-CN')).slice(0, Math.max(0, limit));
}
