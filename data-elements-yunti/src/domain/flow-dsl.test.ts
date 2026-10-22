import { describe, it, expect } from 'vitest';
import {
  parseFlow,
  serializeFlow,
  createEmptyFlow,
  FlowSchema,
  TriggerDataSchema,
} from './flow-dsl';

describe('flow-dsl', () => {
  it('createEmptyFlow produces a trigger + response wired together', () => {
    const f = createEmptyFlow('demo');
    expect(f.name).toBe('demo');
    expect(f.nodes.filter((n) => n.type === 'trigger')).toHaveLength(1);
    expect(f.nodes.filter((n) => n.type === 'response')).toHaveLength(1);
    expect(f.edges).toHaveLength(1);
    const [edge] = f.edges;
    const trigger = f.nodes.find((n) => n.type === 'trigger')!;
    const response = f.nodes.find((n) => n.type === 'response')!;
    expect(edge.source).toBe(trigger.id);
    expect(edge.target).toBe(response.id);
  });

  it('round-trips through serialize/parse', () => {
    const f = createEmptyFlow('demo');
    expect(parseFlow(JSON.parse(serializeFlow(f)))).toEqual(f);
  });

  it('rejects a flow missing required top-level fields', () => {
    expect(() => FlowSchema.parse({ name: 'n', nodes: [], edges: [] })).toThrow();
  });

  it('applies defaults for trigger data', () => {
    const t = TriggerDataSchema.parse({});
    expect(t.method).toBe('GET');
    expect(t.request.query).toEqual([]);
    expect(t.auth.required).toBe(false);
  });
});
