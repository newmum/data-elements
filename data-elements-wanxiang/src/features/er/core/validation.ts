import type { MetadataSnapshot } from '../types/domain';
import { validateRelation } from './relations';
import { metadataShapeErrors } from './schemaValidation';
export class ImportError extends Error {
  constructor(public issues: string[]) { super(issues.slice(0,12).join('\n')); this.name = 'ImportError'; }
}
const families = ['integer','decimal','float','string','boolean','date','timestamp','uuid','objectid','binary','object','array','unknown'];
const kinds = ['table','view','collection','index','keyspace','embedded'];
const bools = ['true','false','unknown'];
function isObject(x: unknown): x is Record<string,unknown> { return !!x && typeof x === 'object' && !Array.isArray(x); }
export function validateSnapshot(input: unknown): MetadataSnapshot {
  const shapeErrors=metadataShapeErrors(input);
  if(shapeErrors.length)throw new ImportError(shapeErrors);
  const errors: string[] = [];
  if (!isObject(input)) throw new ImportError(['根对象必须为 JSON 对象。']);
  if (input.schemaVersion !== '1.0') errors.push('不支持的 schemaVersion；只接受 1.0。');
  if (typeof input.id !== 'string' || !input.id) errors.push('快照 id 不能为空。');
  if (typeof input.capturedAt !== 'string' || !Number.isFinite(Date.parse(input.capturedAt))) errors.push('capturedAt 必须是有效日期。');
  if (!Array.isArray(input.sources) || !Array.isArray(input.entities)) throw new ImportError([...errors, 'sources 和 entities 必须是数组。']);
  if (input.entities.length > 5000) errors.push('最多支持 5,000 个实体，请缩小导入范围。');
  const sourceIds = new Set<string>(); const entityIds = new Set<string>(); const fieldIds = new Set<string>();
  for (const [i,s] of input.sources.entries()) {
    if (!isObject(s) || typeof s.id !== 'string' || typeof s.name !== 'string' || typeof s.engine !== 'string' || !isObject(s.capabilities)) { errors.push(`sources[${i}]：缺少有效的 id / name / engine / capabilities。`); continue; }
    if (sourceIds.has(s.id)) errors.push(`数据源 ID 重复：${s.id}`);
    sourceIds.add(s.id);
    for (const [k,v] of Object.entries(s.capabilities)) if (typeof v !== 'boolean') errors.push(`${s.id}.capabilities.${k} 必须为布尔值。`);
  }
  for (const [i,e] of input.entities.entries()) {
    if (!isObject(e) || typeof e.id !== 'string' || typeof e.name !== 'string' || !Array.isArray(e.fields) || typeof e.sourceId !== 'string') { errors.push(`entities[${i}]：缺少有效的 id / name / fields / sourceId。`); continue; }
    if (!kinds.includes(String(e.kind))) errors.push(`${e.id}：不支持的实体 kind。`);
    if (!sourceIds.has(e.sourceId)) errors.push(`${e.id}：引用了不存在的数据源。`);
    if (entityIds.has(e.id)) errors.push(`实体 ID 重复：${e.id}`);
    entityIds.add(e.id);
    const ownIds = new Set<string>();
    for (const [j,f] of e.fields.entries()) {
      if (!isObject(f) || typeof f.id !== 'string' || typeof f.name !== 'string' || typeof f.path !== 'string' || typeof f.nativeType !== 'string' || !Number.isInteger(f.ordinal)) { errors.push(`${e.id}.fields[${j}]：缺少有效的字段标识、路径、类型或序号。`); continue; }
      if (!families.includes(String(f.typeFamily))) errors.push(`${f.id}：不支持的 typeFamily。`);
      if (f.nullable !== undefined && !bools.includes(String(f.nullable))) errors.push(`${f.id}：nullable 必须为 true / false / unknown 字符串。`);
      if (fieldIds.has(f.id)) errors.push(`字段 ID 重复：${f.id}`);
      fieldIds.add(f.id); ownIds.add(f.id);
    }
    if (e.keys !== undefined && !Array.isArray(e.keys)) errors.push(`${e.id}.keys 必须为数组。`);
    if (Array.isArray(e.keys)) for (const k of e.keys) {
      if (!isObject(k) || typeof k.id !== 'string' || !['primary','unique','unique_index'].includes(String(k.kind)) || !Array.isArray(k.fieldIds) || !k.fieldIds.length) { errors.push(`${e.id}：键约束格式不正确。`); continue; }
      if (new Set(k.fieldIds).size !== k.fieldIds.length) errors.push(`${k.id}：键约束包含重复字段。`);
      if (k.fieldIds.some(id => typeof id !== 'string' || !ownIds.has(id))) errors.push(`${k.id}：键成员不属于对应实体。`);
    }
  }
  if (fieldIds.size > 200000) errors.push('字段数超过 200,000 的导入上限。');
  if (errors.length) throw new ImportError(errors);
  if (input.catalogRelationships !== undefined && !Array.isArray(input.catalogRelationships)) throw new ImportError(['catalogRelationships 必须为数组。']);
  const snapshot = structuredClone(input) as unknown as MetadataSnapshot;
  const relationIds = new Set<string>();
  for (const r of snapshot.catalogRelationships ?? []) {
    if (!r || typeof r.id !== 'string' || typeof r.constraintName !== 'string' || !Array.isArray(r.mappings) || r.mappings.some(m => !m || typeof m.sourceFieldId !== 'string' || typeof m.targetFieldId !== 'string')) { errors.push('目录关系缺少 id / constraintName / 有效 mappings。'); continue; }
    if (relationIds.has(r.id)) errors.push(`关系 ID 重复：${r.id}`);
    relationIds.add(r.id);
    // Catalog type equivalence is reported by source systems; do not apply inference type restrictions.
    errors.push(...validateRelation({ ...r, snapshotId: snapshot.id, name: r.constraintName, semanticType: 'join', description: '导入的目录约束' }, snapshot).map(x => `${r.id}：${x}`));
  }
  const allIds=[snapshot.id,...sourceIds,...entityIds,...fieldIds,...relationIds];
  if(allIds.some(id=>['__proto__','constructor','prototype'].includes(id)))errors.push('ID 使用了保留标识，请更换稳定标识。');
  if (errors.length) throw new ImportError(errors);
  return snapshot;
}
export function parseImport(text: string): unknown {
  if (new TextEncoder().encode(text).length > 50 * 1024 * 1024) throw new ImportError(['文件不能超过 50 MB。']);
  let data: unknown;
  try { data = JSON.parse(text); } catch { throw new ImportError(['JSON 格式错误，请检查引号、逗号与括号。']); }
  function inspect(x: unknown, depth: number): void {
    if (depth > 16) throw new ImportError(['JSON 嵌套深度不能超过 16 层。']);
    if (x && typeof x === 'object') for (const [k,v] of Object.entries(x)) {
      if (k === '__proto__' || k === 'constructor' || k === 'prototype') throw new ImportError(['导入包含不允许的对象属性。']);
      inspect(v, depth + 1);
    }
  }
  inspect(data,0); return data;
}
