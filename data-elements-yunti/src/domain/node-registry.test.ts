import { describe, it, expect } from 'vitest';
import {
  getNodeDef,
  requireNodeDef,
  listNodeDefs,
  groupedNodeDefs,
} from './node-registry';

const ALL_TYPES = [
  'trigger',
  'response',
  'sql-query',
  'sql-exec',
  'kafka-consume',
  'kafka-produce',
  'es-query',
  'http-call',
  'redis',
  'script',
  'transform',
  'filter',
  'aggregate',
  'sort',
  'paginate',
  'join',
  'condition',
  'switch',
  'foreach',
  'parallel',
  'try-catch',
  'subflow',
  'delay',
  'set-var',
  'const',
  'log',
  'note',
  'validate',
];

describe('node-registry', () => {
  it('registers every SRS §6 node type (28)', () => {
    for (const type of ALL_TYPES) {
      expect(getNodeDef(type), `missing node type ${type}`).toBeDefined();
    }
    expect(listNodeDefs()).toHaveLength(ALL_TYPES.length);
  });

  it('every def has a unique type, a color, an icon and a generatorKey', () => {
    const seen = new Set<string>();
    for (const def of listNodeDefs()) {
      expect(seen.has(def.type)).toBe(false);
      seen.add(def.type);
      expect(def.color).toMatch(/^#/);
      expect(def.icon.length).toBeGreaterThan(0);
      expect(def.generatorKey).toBe(def.type);
    }
  });

  it('requireNodeDef throws on unknown type', () => {
    expect(() => requireNodeDef('nope')).toThrow();
  });

  it('groups defs by the five categories in order', () => {
    const groups = groupedNodeDefs();
    expect(groups.map((g) => g.category)).toEqual([
      'io',
      'datasource',
      'processing',
      'logic',
      'tools',
    ]);
    expect(groups.every((g) => g.defs.length > 0)).toBe(true);
  });

  it('port/flag invariants hold', () => {
    for (const def of listNodeDefs()) {
      const ins = def.ports.filter((p) => p.group === 'in');
      const outs = def.ports.filter((p) => p.group === 'out');
      if (def.type !== 'trigger' && !def.nonExecutable) {
        expect(ins.length, `${def.type} needs an in port`).toBeGreaterThanOrEqual(1);
      }
      if (!def.terminal && !def.nonExecutable) {
        expect(outs.length, `${def.type} needs an out port`).toBeGreaterThanOrEqual(1);
      }
    }
    expect(requireNodeDef('trigger').singleton).toBe(true);
    expect(requireNodeDef('response').terminal).toBe(true);
    expect(requireNodeDef('note').nonExecutable).toBe(true);
    // control-flow branch ports
    expect(requireNodeDef('condition').ports.map((p) => p.id)).toEqual(
      expect.arrayContaining(['true', 'false']),
    );
    expect(requireNodeDef('try-catch').ports.map((p) => p.id)).toContain('error');
  });
});
