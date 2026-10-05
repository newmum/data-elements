import type { Entity, FieldDef, FieldMapping, KeyConstraint, MetadataSnapshot, Relationship } from '../types/domain';
import { compatibleTypes, fingerprint } from './relations';
import { uid } from './model';
export const RULE_VERSION = 'local-structure-1.0';
const scopeNames = new Set(['tenant_id', 'partition_key', 'tenant_code']);
const generic = new Set(['id','_id','status','type','state','name','code','created_at','updated_at']);
export function normalizeName(s: string): string {
  return s.replace(/([a-z\d])([A-Z])/g,'$1_$2').replace(/[\s-]+/g,'_').replace(/^(tbl_|biz_|t_)/i,'').toLowerCase();
}
function singular(s: string): string { return s.endsWith('ies') ? `${s.slice(0,-3)}y` : s.endsWith('s') && !s.endsWith('ss') ? s.slice(0,-1) : s; }
function nameMatch(source: FieldDef, target: FieldDef, targetEntity: Entity): boolean {
  const a = normalizeName(source.name); const b = normalizeName(target.name);
  if (generic.has(a) && generic.has(b)) return false;
  if (scopeNames.has(b)) return a === b;
  if (a === b && !generic.has(a)) return true;
  if (!generic.has(b) && a.endsWith(`_${b}`)) return true; // e.g. parent_org_id -> org_id
  if (b === 'id' || b === '_id') {
    const root = singular(normalizeName(targetEntity.name));
    return a === `${root}_id` || a.endsWith(`_${root}_id`);
  }
  return false;
}
export function discoverRelationships(snapshot: MetadataSnapshot, scopeIds: string[], existing: Relationship[]): { candidates: Relationship[]; suppressed: number } {
  const scope = new Set(scopeIds);
  const entities = snapshot.entities.filter(e => scope.has(e.id));
  if (entities.length > 300) throw new Error('本地分析最多选择 300 个实体，请缩小范围。');
  const known = new Map(existing.map(r => [fingerprint(r), r]));
  const seen = new Set<string>(); const candidates: Relationship[] = []; let suppressed = 0;
  const now = new Date().toISOString();
  for (const target of entities) {
    const declared = target.keys ?? [];
    const root = singular(normalizeName(target.name));
    // No declared PK is required: names may supply only an UNVERIFIED candidate key.
    // This never changes the snapshot or marks a field as PK/UNIQUE.
    const inferredKeys: KeyConstraint[] = declared.length ? [] : target.fields
      .filter(f => ['id','_id',`${root}_id`,`${root}_code`].includes(normalizeName(f.name)) && !['float','object','array','unknown','binary'].includes(f.typeFamily))
      .slice(0,3)
      .map(f => ({ id:`candidate-key:${target.id}:${f.id}`, kind:'unique_index' as const,
        fieldIds:[...target.fields.filter(x => scopeNames.has(normalizeName(x.name)) && x.id !== f.id).slice(0,2).map(x=>x.id),f.id],
        enforced:'unknown' as const, scope:'unknown' as const }));
    for (const key of [...declared, ...inferredKeys]) {
      if (key.fieldIds.length > 3 || key.scope === 'partial' || key.scope === 'expression') continue;
      const keyFields = key.fieldIds.map(id => target.fields.find(f => f.id === id)).filter((f): f is FieldDef => !!f);
      if (keyFields.length !== key.fieldIds.length || !keyFields.length) continue;
      const principal = keyFields.find(f => !scopeNames.has(normalizeName(f.name))) ?? keyFields[0];
      for (const source of entities) {
        const anchors = source.fields.filter(f => nameMatch(f, principal, target) && compatibleTypes(f, principal)).slice(0,20);
        for (const anchor of anchors) {
          const mappings: FieldMapping[] = []; let valid = true;
          for (const tf of keyFields) {
            const sf = tf.id === principal.id ? anchor : source.fields.find(f => nameMatch(f,tf,target) && compatibleTypes(f,tf));
            if (!sf) { valid = false; break; }
            mappings.push({ sourceFieldId: sf.id, targetFieldId: tf.id, comparisonRule: 'exact' });
          }
          if (!valid || mappings.every(m => m.sourceFieldId === m.targetFieldId)) continue;
          if (new Set(mappings.map(m => m.sourceFieldId)).size !== mappings.length) continue;
          const base = { sourceEntityId: source.id, targetEntityId: target.id, mappings, semanticType: 'reference' as const };
          const fp = fingerprint(base);
          if (seen.has(fp)) continue;
          seen.add(fp);
          const old = known.get(fp);
          if (old) { suppressed++; continue; } // Catalog, decisions and ignored fingerprints are never overwritten.
          const k = key.enforced === 'true' && key.scope === 'full' ? 1 : key.id.startsWith('candidate-key:') ? .2 : .4;
          const b = source.sourceId === target.sourceId ? 1 : .65;
          const score = Math.round(100 * (.5*.95 + .25 + .15*k + .10*b));
          const sourceKey = source.keys?.some(k => k.enforced === 'true' && k.scope === 'full' && k.fieldIds.length === mappings.length && k.fieldIds.every(id => mappings.some(m => m.sourceFieldId === id)));
          const rel: Relationship = {
            ...base, id: uid('rel'), snapshotId: snapshot.id, workspaceId: 'ws-local', name: `${source.displayName ?? source.name}引用${target.displayName ?? target.name}`,
            origin: 'inference', reviewStatus: 'suggested', lifecycle: 'current', fingerprint: fp, version: 1,
            createdAt: now, updatedAt: now, description: '由本地结构规则发现，尚未查询数据或确认业务语义。',
            targetsPerSource: { min: 'unknown', max: k === 1 ? '1' : 'unknown', basis: k === 1 ? 'constraint' : 'unknown' },
            sourcesPerTarget: { min: 'unknown', max: sourceKey ? '1' : 'unknown', basis: sourceKey ? 'constraint' : 'unknown' },
            verification: { status: 'not_run', targetUniquenessBasis: k === 1 ? 'constraint' : 'unknown' },
            recommendation: { score, level: 'structure_only', ruleVersion: RULE_VERSION, isCalibratedProbability: false,
              factors: [
                { code: 'name', value: .95, weight: .5, explanation: '字段名称与目标实体标识语义对应。' },
                { code: 'type', value: 1, weight: .25, explanation: '字段类型族兼容，未进行任何数据值强制转换。' },
                { code: 'key', value: k, weight: .15, explanation: k < .4 ? '仅依据名称识别候选键；目标未声明主键或唯一键，唯一性未验证。' : keyFields.length > 1 ? `完整匹配 ${keyFields.length} 列联合键，保留作用域。` : '目标提供单列键结构证据。' },
                { code: 'domain', value: b, weight: .1, explanation: b === 1 ? '两端来自同一数据源。' : '跨数据源关系，需额外验证业务范围。' },
              ], penalties: [], unmetChecks: [...(k < 1 ? ['目标唯一性尚未得到可信约束保证'] : []), '未执行数据验证', '业务含义需要人工确认'] },
          };
          candidates.push(rel);
          if (candidates.length >= 2000) return { candidates, suppressed };
        }
      }
    }
  }
  return { candidates: candidates.sort((a,b) => (b.recommendation?.score ?? 0) - (a.recommendation?.score ?? 0)), suppressed };
}
