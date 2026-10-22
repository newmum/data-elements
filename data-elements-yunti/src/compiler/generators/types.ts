import type { FlowNode } from '@/domain/flow-dsl';
import type { CompileContext } from '../context';

/** A code generator for a node type: emits 服务脚本 into the context buffer. */
export type NodeGenerator = (node: FlowNode, ctx: CompileContext) => void;

/** Safely read a string field from node data. */
export function str(node: FlowNode, key: string, fallback = ''): string {
  const v = (node.data as Record<string, unknown>)[key];
  return typeof v === 'string' ? v : v == null ? fallback : String(v);
}

export function num(node: FlowNode, key: string, fallback = 0): number {
  const v = (node.data as Record<string, unknown>)[key];
  return typeof v === 'number' ? v : Number(v ?? fallback) || fallback;
}

export function bool(node: FlowNode, key: string, fallback = false): boolean {
  const v = (node.data as Record<string, unknown>)[key];
  return typeof v === 'boolean' ? v : fallback;
}

export function arr<T = unknown>(node: FlowNode, key: string): T[] {
  const v = (node.data as Record<string, unknown>)[key];
  return Array.isArray(v) ? (v as T[]) : [];
}

/** The output variable name for a node, defaulting to a per-node fallback. */
export function outVar(node: FlowNode, fallback = 'result'): string {
  return str(node, 'outputVar', fallback) || fallback;
}

/** Escape a JS string for embedding inside double quotes. */
export function jsString(s: string): string {
  return JSON.stringify(s);
}

/** A short label for a node used in generated comments. */
export function nodeLabel(node: FlowNode): string {
  return `${node.type} [${node.id}]${node.name ? ' ' + node.name : ''}`;
}

