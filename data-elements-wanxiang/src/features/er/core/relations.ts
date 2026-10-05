import type { Entity, FieldDef, FieldMapping, MetadataSnapshot, RelationCreate, Relationship } from '../types/domain';
import { MAX_NODE_FIELDS } from './model';
import type { FieldMode } from './model';

/** Pair ordering is canonical; never sort the two sides independently. */
export function fingerprint(r: Pick<RelationCreate, 'sourceEntityId' | 'targetEntityId' | 'mappings' | 'semanticType' | 'conditions' | 'arraySemantics' | 'embeddedPath'>): string {
  const pairs = (r.mappings ?? []).map(m => [m.sourceFieldId, m.targetFieldId, m.comparisonRule ?? 'exact']).sort((a,b) => JSON.stringify(a).localeCompare(JSON.stringify(b)));
  return JSON.stringify([r.sourceEntityId, r.targetEntityId, r.semanticType ?? 'reference', pairs, r.conditions ?? [], r.arraySemantics ?? 'none', r.embeddedPath ?? null]);
}
export function compatibleTypes(a: FieldDef, b: FieldDef): boolean {
  const excluded = new Set(['unknown', 'float', 'object', 'array', 'binary']);
  if (excluded.has(a.typeFamily) || excluded.has(b.typeFamily)) return false;
  if (a.typeFamily !== b.typeFamily) return false;
  if (a.arrayDepth || b.arrayDepth) return false;
  if (a.typeFamily === 'integer' && a.signed && b.signed && a.signed !== 'unknown' && b.signed !== 'unknown' && a.signed !== b.signed) return false;
  if (a.typeFamily === 'decimal' && a.scale !== b.scale) return false;
  if (a.typeFamily === 'string' && a.collation && b.collation && a.collation !== b.collation) return false;
  return true;
}
export function relationshipKind(r: Relationship): 'catalog' | 'logical' | 'suggested' {
  if (r.origin === 'catalog') return 'catalog';
  return r.reviewStatus === 'suggested' ? 'suggested' : 'logical';
}
export const relationLabels = { catalog: '数据库外键', logical: '逻辑关系', suggested: '待确认推荐' } as const;
export function importantMapping(r: Relationship, snapshot: MetadataSnapshot): FieldMapping | undefined {
  const entity = snapshot.entities.find(e => e.id === r.sourceEntityId);
  return r.mappings?.find(m => !/^(tenant_id|org_scope_id|partition_key)$/.test(entity?.fields.find(f => f.id === m.sourceFieldId)?.name ?? '')) ?? r.mappings?.[0];
}
export function visibleFields(entity: Entity, relations: Relationship[], mode: FieldMode, pinned: string[] = []): { fields: FieldDef[]; hidden: FieldDef[] } {
  if (mode === 'summary') return { fields: [], hidden: entity.fields };
  const important = new Set(pinned);
  entity.keys?.forEach(k => k.fieldIds.forEach(id => important.add(id)));
  relations.forEach(r => r.mappings?.forEach(m => { important.add(m.sourceFieldId); important.add(m.targetFieldId); }));
  const sorted = [...entity.fields].sort((a,b) => a.ordinal - b.ordinal);
  const candidates = mode === 'all' ? sorted : sorted.filter((f, index) => important.has(f.id) || index < 4);
  const pinnedSet = new Set(pinned);
  const prioritized = [...candidates.filter(f => pinnedSet.has(f.id)), ...candidates.filter(f => !pinnedSet.has(f.id))];
  const fields = prioritized.slice(0, MAX_NODE_FIELDS).sort((a,b) => a.ordinal-b.ordinal);
  const ids = new Set(fields.map(f => f.id));
  return { fields, hidden: sorted.filter(f => !ids.has(f.id)) };
}
export function validateRelation(r: RelationCreate, snapshot: MetadataSnapshot, existing: Relationship[] = [], exceptId?: string): string[] {
  const errors: string[] = [];
  const source = snapshot.entities.find(e => e.id === r.sourceEntityId);
  const target = snapshot.entities.find(e => e.id === r.targetEntityId);
  if (!source || !target) return ['请选择有效的引用方与被引用方。'];
  if (!r.name?.trim()) errors.push('请输入关系名称。');
  if ((r.name?.length ?? 0) > 160) errors.push('关系名称不能超过 160 个字符。');
  const mappings = r.mappings ?? [];
  if (!mappings.length) errors.push('至少添加一组字段映射。');
  if (mappings.length > 64) errors.push('单条关系最多支持 64 组字段映射。');
  const pairs = new Set<string>(); const sf = new Set<string>(); const tf = new Set<string>();
  for (const m of mappings) {
    const a = source.fields.find(f => f.id === m.sourceFieldId);
    const b = target.fields.find(f => f.id === m.targetFieldId);
    if (!a || !b) { errors.push('映射字段不存在，或不属于所选数据表。'); continue; }
    const p = `${a.id}|${b.id}`;
    if (pairs.has(p) || sf.has(a.id) || tf.has(b.id)) errors.push('字段映射重复，同一侧的字段只能使用一次。');
    pairs.add(p); sf.add(a.id); tf.add(b.id);
    if (r.semanticType !== 'join' && !compatibleTypes(a,b)) errors.push(`${a.name} 与 ${b.name} 类型不兼容；请修正映射，或选择“一般 JOIN”并说明原因。`);
  }
  if (mappings.length && mappings.every(m => m.sourceFieldId === m.targetFieldId)) errors.push('不能将字段完整地关联到自身；允许同表不同字段的自关联。');
  if (r.semanticType === 'join' && !r.description?.trim()) errors.push('请为一般 JOIN 填写业务说明。');
  // An enforced composite target key must not be silently reduced to a non-unique subset.
  if ((r.semanticType ?? 'reference') === 'reference') {
    const mappedTargets = new Set(mappings.map(m => m.targetFieldId));
    const candidateKeys = (target.keys ?? []).filter(k => k.scope === 'full' && k.enforced === 'true');
    const hasCompleteKey = candidateKeys.some(k => k.fieldIds.length === mappedTargets.size && k.fieldIds.every(id => mappedTargets.has(id)));
    const partialKey = candidateKeys.some(k => k.fieldIds.length > 1 && [...mappedTargets].every(id => k.fieldIds.includes(id)) && !k.fieldIds.every(id => mappedTargets.has(id)));
    if (partialKey && !hasCompleteKey) errors.push('目标使用联合键，请补齐租户等作用域字段，或明确选择一般 JOIN。');
  }
  const fp = fingerprint(r);
  if (existing.some(x => x.id !== exceptId && x.reviewStatus !== 'archived' && fingerprint(x) === fp)) errors.push('已存在相同映射的关系，请在关系列表中查看或重新处理。');
  return [...new Set(errors)];
}
export function multiplicityLabel(r: Relationship): string {
  const label = (value?: string) => value === 'many' ? 'N' : value === '1' ? '1' : '?';
  // Left-hand value is records at the SOURCE per TARGET, not targetsPerSource.
  return `${label(r.sourcesPerTarget?.max)} : ${label(r.targetsPerSource?.max)}`;
}
export function relatedEntityIds(id: string, relationships: Relationship[]): Set<string> {
  const result = new Set([id]);
  relationships.filter(r => r.reviewStatus !== 'archived' && r.reviewStatus !== 'rejected').forEach(r => {
    if (r.sourceEntityId === id) result.add(r.targetEntityId);
    if (r.targetEntityId === id) result.add(r.sourceEntityId);
  });
  return result;
}
