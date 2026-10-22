import type { FlowNode } from '@/domain/flow-dsl';
import type { NodeDef, PortDef } from '@/domain/node-registry';

const inPortAttrs = {
  circle: {
    r: 5,
    magnet: true,
    stroke: '#1677ff',
    strokeWidth: 1.5,
    fill: '#fff',
  },
};

const outPortAttrs = {
  circle: {
    r: 5,
    magnet: true,
    stroke: '#13c2c2',
    strokeWidth: 1.5,
    fill: '#fff',
  },
};

function dynamicPorts(def: NodeDef, node?: FlowNode): PortDef[] {
  if (def.type !== 'switch' || !node) return def.ports;
  const base = [...def.ports];
  const seen = new Set(base.map((port) => port.id));
  const cases = Array.isArray(node.data.cases) ? node.data.cases : [];
  for (const item of cases) {
    if (!item || typeof item !== 'object') continue;
    const record = item as Record<string, unknown>;
    const id = typeof record.port === 'string' ? record.port : '';
    if (!id || seen.has(id)) continue;
    seen.add(id);
    base.push({
      id,
      group: 'out',
      label: typeof record.label === 'string' ? record.label : String(record.value ?? id),
    });
  }
  return base;
}

export function toX6Ports(def: NodeDef, node?: FlowNode) {
  const ports = dynamicPorts(def, node);
  return {
    groups: {
      in: {
        position: 'left',
        attrs: inPortAttrs,
      },
      out: {
        position: 'right',
        attrs: outPortAttrs,
      },
    },
    items: ports.map((port: PortDef) => ({
      id: port.id,
      group: port.group,
      attrs: {
        text: {
          text: port.label ?? '',
          fontSize: 10,
          fill: '#6b7280',
        },
      },
    })),
  };
}
