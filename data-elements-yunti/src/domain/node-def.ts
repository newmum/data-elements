import type { ComponentType } from 'react';
import { categoryColor } from '@/theme/tokens';

export type NodeCategory = 'io' | 'datasource' | 'processing' | 'logic' | 'tools';

export type PortGroup = 'in' | 'out';

export interface PortDef {
  /** Port id, unique within a node (e.g. 'in', 'out', 'true', 'false', 'error'). */
  id: string;
  group: PortGroup;
  /** Visible label for branch ports (e.g. 'true' / 'false'). */
  label?: string;
}

/** Props passed to a per-node config form component. */
export interface NodeFormProps {
  nodeId: string;
}

export interface NodeDef {
  type: string;
  category: NodeCategory;
  /** Display name (zh). */
  label: string;
  description: string;
  /** Icon name (resolved to a component in the canvas/palette icon map). */
  icon: string;
  /** Category accent color. */
  color: string;
  /** Default ports. Control nodes declare branch ports here. */
  ports: PortDef[];
  /** Default node `data` for newly created nodes. */
  defaultData: Record<string, unknown>;
  /** Key into the compiler generator registry (defaults to `type`). */
  generatorKey: string;
  /** Only one allowed per flow (trigger). */
  singleton?: boolean;
  /** Terminal node — produces the response, no out port (response). */
  terminal?: boolean;
  /** Not compiled / pure annotation (note). */
  nonExecutable?: boolean;
  /** Lazy config form component, registered separately (Phase 5). */
  form?: ComponentType<NodeFormProps>;
}

export const IN_PORT: PortDef = { id: 'in', group: 'in' };
export const OUT_PORT: PortDef = { id: 'out', group: 'out' };
export const IN_OUT: PortDef[] = [IN_PORT, OUT_PORT];

/** Build a NodeDef, filling in color (from category) and generatorKey (from type). */
export function defineNode(
  def: Omit<NodeDef, 'color' | 'generatorKey'> & {
    color?: string;
    generatorKey?: string;
  },
): NodeDef {
  return {
    ...def,
    color: def.color ?? categoryColor[def.category],
    generatorKey: def.generatorKey ?? def.type,
  };
}
