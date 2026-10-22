import { describe, expect, it } from 'vitest';
import { createEmptyFlow } from '@/domain/flow-dsl';
import { computeLayeredLayout } from './layout';

describe('computeLayeredLayout', () => {
  it('places target nodes to the right of source nodes', () => {
    const flow = createEmptyFlow('layout');
    const positions = computeLayeredLayout(flow);
    const edge = flow.edges[0];
    expect(positions[edge.target].x).toBeGreaterThan(positions[edge.source].x);
  });
});
