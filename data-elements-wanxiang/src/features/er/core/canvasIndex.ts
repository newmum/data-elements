import type { MetadataSnapshot, Relationship } from '../types/domain';

// Snapshot/relationship arrays are immutable. Build once per version, not once
// per node or pointer movement (the tenant catalog can contain thousands of tables).
const snapshots = new WeakMap<MetadataSnapshot, ReturnType<typeof buildSnapshot>>();
function buildSnapshot(snapshot: MetadataSnapshot) {
  return { entities: new Map(snapshot.entities.map(e => [e.id, e])), sources: new Map(snapshot.sources.map(s => [s.id, s])) };
}
export function canvasSnapshotIndex(snapshot: MetadataSnapshot) {
  let index = snapshots.get(snapshot);
  if (!index) { index = buildSnapshot(snapshot); snapshots.set(snapshot, index); }
  return index;
}
const relations = new WeakMap<Relationship[], ReturnType<typeof buildRelations>>();
function buildRelations(items: Relationship[]) {
  const active = items.filter(r => !['rejected', 'archived'].includes(r.reviewStatus));
  const byEntity = new Map<string, Relationship[]>();
  for (const relation of active) for (const id of new Set([relation.sourceEntityId, relation.targetEntityId])) {
    const group = byEntity.get(id) ?? []; group.push(relation); byEntity.set(id, group);
  }
  return { active, byEntity, byId: new Map(items.map(r => [r.id, r])) };
}
export function canvasRelationIndex(items: Relationship[]) {
  let index = relations.get(items);
  if (!index) { index = buildRelations(items); relations.set(items, index); }
  return index;
}
