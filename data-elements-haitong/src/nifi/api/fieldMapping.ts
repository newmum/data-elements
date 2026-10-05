import { apiClient } from './client';

let fieldMappingApiUnavailable = false;

export interface FieldMeta {
  name: string;
  path?: string;
  type?: string;
  nullable?: boolean;
  isPk?: boolean;
  hasDefault?: boolean;
  sensitive?: boolean;
}

export interface MappingIssue {
  path: string;
  value?: unknown;
  message: string;
  severity: 'ERROR' | 'WARN';
  code: string;
  suggestions?: string[];
}

export interface ValidationResponse {
  valid: boolean;
  errors: MappingIssue[];
  warnings: MappingIssue[];
  schemaCompare?: { sourceFields: FieldMeta[]; targetFields: FieldMeta[] };
}

export interface PreviewResponse {
  resultRows: Record<string, unknown>[];
  rowResults: { rowIndex: number; success: boolean; errors: string[] }[];
  stats: Record<string, unknown>;
}

export interface RecommendResponse {
  spec: FieldMappingSpec;
  recommendations: Array<{
    id: string;
    from: string;
    to: string;
    fromType?: string;
    toType?: string;
    reason: string;
    confidence: number;
  }>;
}

export interface CompileResponse {
  mode: string;
  query: string;
  nifiComponentConfig: Record<string, unknown>;
}

export interface TransformSpec {
  fn: string;
  args?: unknown[];
  onError?: 'NULL' | 'SKIP_ROW' | 'KEEP_ORIGINAL';
}

export interface LookupSpec {
  sql: string;
  resultColumn?: string;
  dataSource?: string;
  onMissing?: 'NULL' | 'KEEP_SOURCE' | 'FAIL';
}

export type MappingRule = {
  from?: string;
  fromList?: string[];
  constant?: string | number | boolean | null;
  expression?: string;
  lookup?: LookupSpec;
  when?: string;
  then?: MappingRule;
  else?: MappingRule;
  to?: string;
  transform?: TransformSpec;
  comment?: string;
  enabled?: boolean;
};

export interface FieldMappingSpec {
  version: '1.0';
  passthroughUnmapped?: boolean;
  passthroughCaseSensitive?: boolean;
  exclude?: string[];
  onMissingSource?: 'NULL' | 'SKIP_ROW' | 'FAIL';
  onTypeMismatch?: 'CAST' | 'SKIP_ROW' | 'FAIL';
  defaultLocale?: string;
  mappings: MappingRule[];
}

export const emptyFieldMappingSpec = (): FieldMappingSpec => ({
  version: '1.0',
  passthroughUnmapped: false,
  passthroughCaseSensitive: true,
  onMissingSource: 'NULL',
  onTypeMismatch: 'CAST',
  defaultLocale: 'zh-CN',
  mappings: [],
});

export async function migrateFieldMapping(spec: unknown) {
  if (fieldMappingApiUnavailable) return normalizeLocalSpec(spec);
  try {
    const { data } = await apiClient.post<FieldMappingSpec>('/field-mapping/migrate-legacy', { spec });
    return data;
  } catch (error) {
    if (isNotFound(error)) return normalizeLocalSpec(spec);
    throw error;
  }
}

export async function validateFieldMapping(spec: FieldMappingSpec, sourceSchema: FieldMeta[] = [], targetSchema: FieldMeta[] = []) {
  if (fieldMappingApiUnavailable) return validateLocal(spec, sourceSchema, targetSchema);
  try {
    const { data } = await apiClient.post<ValidationResponse>('/field-mapping/validate', { spec, sourceSchema, targetSchema });
    return data;
  } catch (error) {
    if (isNotFound(error)) return validateLocal(spec, sourceSchema, targetSchema);
    throw error;
  }
}

export async function previewFieldMapping(spec: FieldMappingSpec, sampleRows: Record<string, unknown>[]) {
  if (fieldMappingApiUnavailable) return previewLocal(spec, sampleRows);
  try {
    const { data } = await apiClient.post<PreviewResponse>('/field-mapping/preview', { spec, sampleRows });
    return data;
  } catch (error) {
    if (isNotFound(error)) return previewLocal(spec, sampleRows);
    throw error;
  }
}

export async function recommendFieldMapping(sourceSchema: FieldMeta[], targetSchema: FieldMeta[]) {
  if (fieldMappingApiUnavailable) return recommendLocal(sourceSchema, targetSchema);
  try {
    const { data } = await apiClient.post<RecommendResponse>('/field-mapping/recommend', { sourceSchema, targetSchema });
    return data;
  } catch (error) {
    if (isNotFound(error)) return recommendLocal(sourceSchema, targetSchema);
    throw error;
  }
}

export async function compileFieldMapping(spec: FieldMappingSpec, sourceSchema: FieldMeta[] = [], targetSchema: FieldMeta[] = []) {
  if (fieldMappingApiUnavailable) return compileLocal(spec, sourceSchema, targetSchema);
  try {
    const { data } = await apiClient.post<CompileResponse>('/field-mapping/compile', { spec, sourceSchema, targetSchema });
    return data;
  } catch (error) {
    if (isNotFound(error)) return compileLocal(spec, sourceSchema, targetSchema);
    throw error;
  }
}

function isNotFound(error: unknown) {
  const notFound = typeof error === 'object'
    && error !== null
    && 'response' in error
    && (error as { response?: { status?: number } }).response?.status === 404;
  if (notFound) fieldMappingApiUnavailable = true;
  return notFound;
}

function normalizeLocalSpec(raw: unknown): FieldMappingSpec {
  if (typeof raw === 'string' && raw.trim()) {
    try {
      return normalizeLocalSpec(JSON.parse(raw));
    } catch {
      return emptyFieldMappingSpec();
    }
  }
  if (raw && typeof raw === 'object' && !Array.isArray(raw)) {
    const object = raw as Record<string, unknown>;
    if (object.version === '1.0' && Array.isArray(object.mappings)) {
      return { ...emptyFieldMappingSpec(), ...object, mappings: object.mappings.map((rule) => normalizeRuleLocal(rule as MappingRule)) };
    }
    const mappings = Object.entries(object)
      .filter(([from, to]) => typeof to === 'string' && normalizePathLocal(from) !== '/source_field')
      .map(([from, to]) => ({ from: normalizePathLocal(from), to: normalizePathLocal(String(to)) }));
    return { ...emptyFieldMappingSpec(), mappings };
  }
  return emptyFieldMappingSpec();
}

function validateLocal(spec: FieldMappingSpec, sourceSchema: FieldMeta[], targetSchema: FieldMeta[]): ValidationResponse {
  const errors: MappingIssue[] = [];
  const warnings: MappingIssue[] = [];
  const sourceSet = new Set(sourceSchema.map((field) => normalizePathLocal(field.path ?? field.name)).filter(Boolean));
  const targetSet = new Set(targetSchema.map((field) => normalizePathLocal(field.path ?? field.name)).filter(Boolean));

  spec.mappings.forEach((mapping, index) => {
    if (mapping.enabled === false) return;
    const path = `mappings[${index}]`;
    if (!mapping.to) errors.push(issue(`${path}.to`, '目标字段不能为空', 'EMPTY_TARGET'));
    if (mapping.to && !isSimpleRecordPath(mapping.to)) errors.push(issue(`${path}.to`, '目标字段只支持数据库字段名或一级 RecordPath', 'INVALID_TARGET'));
    if (mapping.lookup && !mapping.lookup.sql?.trim()) {
      errors.push(issue(`${path}.lookup.sql`, '字典查询映射的 SQL 查询语句不能为空', 'EMPTY_LOOKUP_SQL'));
    }
    if (!mapping.from && !mapping.fromList?.length && !('constant' in mapping) && !mapping.expression && !mapping.lookup && !mapping.when) {
      errors.push(issue(path, '请配置源字段、常量、表达式、字典查询或条件规则', 'EMPTY_SOURCE'));
    }
    pathsFromRuleLocal(mapping).forEach((from) => {
      if (!isSimpleRecordPath(from)) errors.push(issue(`${path}.from`, '源字段只支持数据库字段名或一级 RecordPath', 'INVALID_SOURCE'));
      if (sourceSet.size > 0 && !sourceSet.has(normalizePathLocal(from))) warnings.push(issue(`${path}.from`, `源字段 ${displayPathLocal(from)} 不在当前源字段列表中`, 'SOURCE_NOT_FOUND', 'WARN'));
    });
    if (mapping.to && targetSet.size > 0 && !targetSet.has(normalizePathLocal(mapping.to))) {
      warnings.push(issue(`${path}.to`, `目标字段 ${displayPathLocal(mapping.to)} 不在当前目标字段列表中`, 'TARGET_NOT_FOUND', 'WARN'));
    }
  });

  return { valid: errors.length === 0, errors, warnings, schemaCompare: { sourceFields: sourceSchema, targetFields: targetSchema } };
}

function previewLocal(spec: FieldMappingSpec, sampleRows: Record<string, unknown>[]): PreviewResponse {
  const rows = sampleRows.length ? sampleRows : [{}];
  const resultRows = rows.map((row) => {
    const out: Record<string, unknown> = {};
    spec.mappings.forEach((mapping) => {
      if (mapping.enabled === false || !mapping.to) return;
      const target = displayPathLocal(mapping.to);
      if ('constant' in mapping) out[target] = mapping.constant;
      else if (mapping.expression) out[target] = renderExpressionLocal(mapping.expression, row);
      else if (mapping.lookup) out[target] = previewLookupLocal(mapping.lookup, row);
      else if (mapping.fromList?.length) out[target] = mapping.fromList.map((from) => row[displayPathLocal(from)] ?? '').join(String(mapping.transform?.args?.[0] ?? ''));
      else if (mapping.from) out[target] = row[displayPathLocal(mapping.from)] ?? null;
    });
    return out;
  });
  return { resultRows, rowResults: rows.map((_, rowIndex) => ({ rowIndex, success: true, errors: [] })), stats: { fallback: true, rows: rows.length } };
}

function recommendLocal(sourceSchema: FieldMeta[], targetSchema: FieldMeta[]): RecommendResponse {
  const recommendations = targetSchema.flatMap((target) => {
    const targetName = normalizeNameLocal(target.name);
    const scored = sourceSchema
      .map((source) => ({ source, score: scoreNameLocal(source.name, target.name) }))
      .sort((a, b) => b.score.confidence - a.score.confidence);
    const best = scored[0];
    if (!best || best.score.confidence < 0.55) return [];
    const exact = normalizeNameLocal(best.source.name) === targetName;
    return [{
      id: `${best.source.name}->${target.name}`,
      from: normalizePathLocal(best.source.path ?? best.source.name),
      to: normalizePathLocal(target.path ?? target.name),
      fromType: best.source.type,
      toType: target.type,
      reason: exact ? '字段名完全一致' : best.score.reason,
      confidence: exact ? 0.98 : best.score.confidence,
    }];
  });
  return { spec: { ...emptyFieldMappingSpec(), mappings: recommendations.map((item) => ({ from: item.from, to: item.to })) }, recommendations };
}

function compileLocal(spec: FieldMappingSpec, sourceSchema: FieldMeta[], targetSchema: FieldMeta[]): CompileResponse {
  const columns = spec.mappings
    .filter((mapping) => mapping.enabled !== false && mapping.to)
    .map((mapping) => `${selectExpressionLocal(mapping)} AS ${quoteIdentifierLocal(displayPathLocal(mapping.to))}`);
  const query = columns.length ? `SELECT ${columns.join(', ')} FROM FLOWFILE` : 'SELECT * FROM FLOWFILE';
  return { mode: 'QUERY_RECORD', query, nifiComponentConfig: { 'Record Reader': 'JsonTreeReader', 'Record Writer': 'JsonRecordSetWriter', fallback: true, sourceSchema, targetSchema } };
}

function normalizeRuleLocal(rule: MappingRule): MappingRule {
  return {
    ...rule,
    from: rule.from ? normalizePathLocal(rule.from) : rule.from,
    to: rule.to ? normalizePathLocal(rule.to) : rule.to,
    fromList: rule.fromList?.map(normalizePathLocal).filter(Boolean),
  };
}

function pathsFromRuleLocal(rule: MappingRule) {
  const paths: string[] = [];
  if (rule.from) paths.push(rule.from);
  rule.fromList?.forEach((path) => paths.push(path));
  if (rule.lookup?.sql) lookupFieldRefsLocal(rule.lookup.sql).forEach((path) => paths.push(path));
  return paths;
}

function selectExpressionLocal(rule: MappingRule) {
  if ('constant' in rule) return literalLocal(rule.constant);
  if (rule.lookup?.sql) return lookupExpressionLocal(rule.lookup, rule.from);
  if (rule.fromList?.length) return `CONCAT(${rule.fromList.map((path) => quoteIdentifierLocal(displayPathLocal(path))).join(', ')})`;
  if (rule.expression) return literalLocal(rule.expression);
  if (rule.from) return quoteIdentifierLocal(displayPathLocal(rule.from));
  return 'NULL';
}

function lookupFieldRefsLocal(sql: string) {
  const paths: string[] = [];
  const re = /\$\{field:([^}]+)}/g;
  let match: RegExpExecArray | null;
  while ((match = re.exec(sql))) paths.push(normalizePathLocal(match[1]));
  return paths;
}

function lookupExpressionLocal(lookup: LookupSpec, sourceField?: string) {
  const sourceIdentifier = sourceField ? quoteIdentifierLocal(displayPathLocal(sourceField)) : '?';
  const resolved = (lookup.sql ?? '')
    .replace(/\$\{field:([^}]+)}/g, (_, field) => quoteIdentifierLocal(displayPathLocal(field)))
    .replace(/\?/g, sourceIdentifier);
  const trimmed = resolved.trim().replace(/;+\s*$/, '');
  return trimmed ? `(${trimmed})` : 'NULL';
}

function previewLookupLocal(lookup: LookupSpec, row: Record<string, unknown>) {
  const refs = lookupFieldRefsLocal(lookup.sql ?? '').map(displayPathLocal);
  const bound = refs.map((field) => `${field}=${row[field] ?? ''}`).join(', ');
  const column = lookup.resultColumn ? `.${lookup.resultColumn}` : '';
  return `「字典查询${column}${bound ? `（${bound}）` : ''}」`;
}

function renderExpressionLocal(expression: string, row: Record<string, unknown>) {
  return expression.replace(/\$\{field:([^}]+)}/g, (_, field) => String(row[displayPathLocal(field)] ?? ''));
}

function normalizePathLocal(value: string) {
  const raw = (value ?? '').trim();
  if (!raw) return '';
  if (raw.startsWith('/')) return raw;
  return `/${raw.replaceAll('.', '/')}`;
}

function displayPathLocal(value?: string) {
  const raw = (value ?? '').trim();
  return raw.startsWith('/') ? raw.slice(1) : raw;
}

function isSimpleRecordPath(value: string) {
  return /^\/?[A-Za-z_][A-Za-z0-9_]*$/.test(value.trim());
}

function normalizeNameLocal(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9\u4e00-\u9fa5]/g, '');
}

function scoreNameLocal(source: string, target: string) {
  const a = normalizeNameLocal(source);
  const b = normalizeNameLocal(target);
  if (!a || !b) return { reason: '字段名相似', confidence: 0 };
  if (a === b) return { reason: /[\u4e00-\u9fa5]/.test(a) ? '中文名完全匹配' : '字段名完全一致', confidence: 1 };
  if (a.includes(b) || b.includes(a)) return { reason: /[\u4e00-\u9fa5]/.test(a + b) ? '中文名相似' : '字段名相似', confidence: 0.82 };
  const distance = levenshteinLocal(a, b);
  return { reason: /[\u4e00-\u9fa5]/.test(a + b) ? '中文名相似' : '字段名相似', confidence: Math.max(0, 1 - distance / Math.max(a.length, b.length)) };
}

function levenshteinLocal(a: string, b: string) {
  const costs = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    let previous = i - 1;
    costs[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const current = costs[j];
      costs[j] = Math.min(costs[j] + 1, costs[j - 1] + 1, previous + (a[i - 1] === b[j - 1] ? 0 : 1));
      previous = current;
    }
  }
  return costs[b.length];
}

function quoteIdentifierLocal(value: string) {
  return `"${value.replaceAll('"', '""')}"`;
}

function literalLocal(value: unknown) {
  if (value === null || value === undefined) return 'NULL';
  if (typeof value === 'number') return String(value);
  if (typeof value === 'boolean') return value ? 'TRUE' : 'FALSE';
  return `'${String(value).replaceAll("'", "''")}'`;
}

function issue(path: string, message: string, code: string, severity: 'ERROR' | 'WARN' = 'ERROR'): MappingIssue {
  return { path, message, code, severity };
}
