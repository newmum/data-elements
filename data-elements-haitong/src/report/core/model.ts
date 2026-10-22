export type Value = string | number | boolean | null;
export type DataType = 'text' | 'integer' | 'decimal' | 'date' | 'datetime' | 'boolean';
export type Nullable = 'unset' | 'allow' | 'forbid';
export type SyncState = 'idle' | 'sending' | 'waiting' | 'success' | 'failed' | 'unknown';
export type TimeFormat = '' | 'iso' | 'ymd' | 'dmy' | 'mdy' | 'unix-s' | 'unix-ms';
export interface TimeRule {
    format: TimeFormat;
    timezone: string;
}
export interface DictionaryLink {
    id: string;
    version: number;
    match: 'code' | 'label' | 'manual';
    output: 'preserve' | 'code';
    confirmed: boolean;
    mappings: Record<string, string>;
}
export interface Field {
    id: string;
    column: number;
    headers: string[];
    en: string;
    zh: string;
    type: DataType;
    nullable: Nullable;
    confirmed: boolean;
    ambiguous?: boolean;
    pk: boolean;
    time: TimeRule;
    dictionary?: DictionaryLink;
}
export interface Schema {
    name: string;
    /** Business table identifier, independent of the source file and field names. */
    englishName: string;
    description: string;
    fields: Field[];
    timestamp: string;
    eventTime: string;
    sameTimeConfirmed: boolean;
    reviewed: boolean;
}
export interface DataRow {
    id: string;
    sourceRow: number;
    values: Record<string, Value>;
    formulaFields: string[];
    precisionFields: string[];
}
export interface Issue {
    code: string;
    message: string;
    hint: string;
    fieldId?: string;
    rowId?: string;
    sourceRow?: number;
    scope: 'schema' | 'resource' | 'row' | 'warning';
}
export interface Validation {
    fingerprint: string;
    time: string;
    global: Issue[];
    issues: Issue[];
    byRow: Record<string, Issue[]>;
    prepared: Record<string, Record<string, Value>>;
    eligible: string[];
    duplicateGroups: number;
}
export interface SourceInfo {
    fileId: string;
    filename: string;
    sheet: string;
    range: string;
    importedAt: string;
    demo: boolean;
}
export interface Resource {
    id: string;
    source: SourceInfo;
    dataVersion: number;
    schemaVersion: number;
    schema: Schema;
    savedSchema: Schema;
    dirty: boolean;
    locked: boolean;
    rows: DataRow[];
    validation?: Validation;
    sync: Record<string, SyncState>;
    notes: string[];
}
export interface Dictionary {
    id: string;
    version: number;
    name: string;
    source: 'demo' | 'upload';
    fileId?: string;
    filename?: string;
    sheet?: string;
    entries: {
        code: string;
        label: string;
    }[];
}
export interface SourceCell {
    v: Value;
    formula?: boolean;
    precision?: boolean;
}
export interface SourceSheet {
    name: string;
    cells: SourceCell[][];
    hidden: boolean;
    hiddenRows: number[];
    hiddenCols: number[];
    merges: {
        r1: number;
        c1: number;
        r2: number;
        c2: number;
    }[];
    rowCount: number;
    colCount: number;
    notes: string[];
}
export interface SourceBook {
    id: string;
    filename: string;
    size: number;
    sheets: SourceSheet[];
    importedAt: string;
}
export interface Region {
    id: string;
    sheet: number;
    kind: 'business' | 'dictionary';
    startRow: number;
    endRow: number;
    startCol: number;
    endCol: number;
    header: 'single' | 'bilingual' | 'none';
    dataStart: number;
    name: string;
    codeCol: number;
    labelCol: number;
    hiddenConfirmed: boolean;
}
export type Scenario = 'success' | 'partial' | 'failure' | 'unknown';
export interface TaskItem {
    rowId: string;
    sourceRow: number;
    values: Record<string, Value>;
    state: SyncState;
    reason: string;
}
/** A table selected from the tenant-scoped five-step registration result. */
export interface RegisteredTableTarget {
    id: string;
    datasourceId: string;
    datasourceName: string;
    name: string;
    displayName: string;
    businessType: string;
    fieldCount: number;
}
export interface SyncTask {
    id: string;
    parentId?: string;
    resourceId: string;
    source: SourceInfo;
    schema: Schema;
    dataVersion: number;
    schemaVersion: number;
    dictionaries: Dictionary[];
    createdAt: string;
    endedAt?: string;
    stage: 'sending' | 'waiting' | 'done';
    scenario: Scenario;
    target: string;
    /** The immutable registered destination used for this import, never inferred from a file name. */
    targetTable?: RegisteredTableTarget;
    items: TaskItem[];
}
export const LIMITS = { files: 5, bytes: 10 * 1024 * 1024, rows: 10000, cols: 100, cells: 1000000, parseMs: 30000 };
export const TYPE_OPTIONS = [{ value: 'text', label: '文本' }, { value: 'integer', label: '整数' }, { value: 'decimal', label: '小数' }, { value: 'date', label: '日期' }, { value: 'datetime', label: '日期时间' }, { value: 'boolean', label: '布尔' }];
export const SYNC_LABEL: Record<SyncState, string> = { idle: '未同步', sending: '同步中', waiting: '等待回执', success: '同步成功', failed: '同步失败', unknown: '结果待确认' };
let sequence = 0;
export function uid(prefix = 'id'): string { return `${prefix}_${Date.now().toString(36)}_${(++sequence).toString(36)}`; }
/**
 * The report workbench is also used from older Chromium-based government
 * desktop browsers.  `structuredClone` is unavailable in some of those
 * runtimes, so do not let a local-file import fail just because the browser
 * cannot provide the newer platform API.
 */
export function clone<T>(x: T): T {
    if (typeof globalThis.structuredClone === 'function') return globalThis.structuredClone(x);
    // Report-domain values are JSON data (plain records, arrays and primitive
    // cell values).  Keep the fallback deliberately scoped to that model
    // instead of adding a broad, dependency-heavy clone polyfill.
    return JSON.parse(JSON.stringify(x)) as T;
}
export function display(v: Value | undefined): string { return v === null || v === undefined || v === '' ? '—' : String(v); }
export function isBlank(v: Value | undefined): boolean { return v === null || v === undefined || (typeof v === 'string' && !v.trim()); }
export function colName(index: number): string { let x = index + 1, s = ''; while (x) {
    x--;
    s = String.fromCharCode(65 + x % 26) + s;
    x = Math.floor(x / 26);
} return s; }
