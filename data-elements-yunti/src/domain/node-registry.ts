import type { ComponentType } from 'react';
import type { NodeCategory, NodeDef, NodeFormProps } from './node-def';
import { allNodeDefs } from './node-defs';

export type { NodeCategory, NodeDef, NodeFormProps, PortDef } from './node-def';
export { IN_PORT, OUT_PORT, IN_OUT, defineNode } from './node-def';

const registry = new Map<string, NodeDef>();
for (const def of allNodeDefs) {
  if (registry.has(def.type)) {
    throw new Error(`Duplicate node type registered: ${def.type}`);
  }
  registry.set(def.type, def);
}

export function getNodeDef(type: string): NodeDef | undefined {
  return registry.get(type);
}

export function requireNodeDef(type: string): NodeDef {
  const def = registry.get(type);
  if (!def) throw new Error(`Unknown node type: ${type}`);
  return def;
}

export function listNodeDefs(): NodeDef[] {
  return [...registry.values()];
}

const categoryOrder: NodeCategory[] = ['io', 'datasource', 'processing', 'logic', 'tools'];

export const categoryLabel: Record<NodeCategory, string> = {
  io: '入口 / 出口',
  datasource: '数据源',
  processing: '数据处理',
  logic: '逻辑控制',
  tools: '工具',
};

/** Node defs grouped by category in display order. */
export function groupedNodeDefs(): { category: NodeCategory; label: string; defs: NodeDef[] }[] {
  return categoryOrder.map((category) => ({
    category,
    label: categoryLabel[category],
    defs: listNodeDefs().filter((d) => d.category === category),
  }));
}

/** Register the config form component for a node type (called from Phase 5 forms). */
export function registerNodeForm(type: string, form: ComponentType<NodeFormProps>): void {
  const def = registry.get(type);
  if (def) def.form = form;
}
