export interface RecordLookup {
  multiValue?: boolean;
  multiValueSeparator?: string;
  query?: string;
  values?: Record<string, string | null>;
  onMissing?: 'NULL' | 'KEEP_SOURCE' | 'FAIL';
}

export function isRecordLookup(lookup?: RecordLookup): boolean {
  return lookup?.multiValue === true && (!!lookup.query || !!lookup.values);
}

/** The same token semantics as the backend and native NiFi record translator. */
export function previewInlineRecordLookup(raw: unknown, lookup: RecordLookup): unknown {
  const separator = lookup.multiValueSeparator ?? ',';
  const codes = raw == null ? [] : String(raw).split(separator).map((code) => code.trim()).filter(Boolean);
  const values = lookup.values ?? {};
  const label = (code: string) => Object.prototype.hasOwnProperty.call(values, code) ? values[code] : null;
  if (codes.some((code) => label(code) != null && label(code) !== '')) {
    return codes.map((code) => label(code) == null || label(code) === '' ? code : label(code)).join(separator);
  }
  if (lookup.onMissing === 'KEEP_SOURCE') return raw;
  if (lookup.onMissing === 'FAIL') throw new Error('多值翻译没有匹配到编码');
  return null;
}
