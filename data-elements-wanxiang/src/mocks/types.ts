import type { Entity, Evidence, Relationship } from '../features/er/types/domain';
export type MockRow = Record<string, any>;
export interface LocalWorkspace {
  id: string; name: string;
  sources: MockRow[]; catalog: Record<string, Entity[]>; entities: Entity[];
  records: Record<string, MockRow[]>; manifest: Record<string, string>;
  models: MockRow[]; relationships: Relationship[]; evidence: Evidence[];
  elements: MockRow[]; reviews: MockRow[]; codes: MockRow[]; encoding: MockRow[];
  mappings: MockRow[]; rules: MockRow[]; plans: MockRow[]; collections: MockRow[];
  profiles: MockRow[]; results: MockRow[]; issues: MockRow[]; orders: MockRow[];
  orderEvents: MockRow[]; lineage: MockRow[]; jobs: MockRow[]; jobEvents: MockRow[];
  schedules: MockRow[]; members: MockRow[]; grants: MockRow[]; audit: MockRow[];
  imports: MockRow[];
}
export interface LocalDatabase {
  format: 'wanxiang-local'; schemaVersion: 1; revision: number;
  principalId: string; authenticated: boolean; workspaces: LocalWorkspace[];
}
export interface StoragePort { getItem(key: string): string | null; setItem(key: string, value: string): void; }
export interface MockRequest { method?: string; body?: unknown; version?: string | number; signal?: AbortSignal; idempotencyKey?: string; }
export class MockError extends Error {
  constructor(public status: number, public code: string, message: string) { super(message); this.name = 'MockError'; }
}
export const STORAGE_KEY = 'wanxiang:local-workspace:3.1';
export const copy = <T>(value: T): T => structuredClone(value);
export function stableId(value: string): string {
  let h = 2166136261; const words: string[] = [];
  for (let n = 0; n < 4; n++) { for (let i = 0; i < value.length; i++) h = Math.imul(h ^ (value.charCodeAt(i) + n * 17), 16777619); words.push((h >>> 0).toString(16).padStart(8, '0')); }
  const s = words.join(''); return `${s.slice(0,8)}-${s.slice(8,12)}-4${s.slice(13,16)}-a${s.slice(17,20)}-${s.slice(20)}`;
}
export const OWNER = stableId('owner');
export const REVIEWER = stableId('reviewer');
export const STEWARD = stableId('steward');
export const WORKSPACE = stableId('wanxiang-workspace');
