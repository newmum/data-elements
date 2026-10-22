import { describe, expect, it } from 'vitest';
import { createEmptyFlow } from '@/domain/flow-dsl';
import { generateOpenApi } from './openapi';

describe('generateOpenApi', () => {
  it('uses the trigger method and path', () => {
    const flow = createEmptyFlow('doc');
    const api = generateOpenApi(flow);
    expect(api.openapi).toBe('3.1.0');
    expect(api.paths['/'].get.summary).toBe('doc');
  });
});
