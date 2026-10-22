import { describe, expect, it } from 'vitest';
import { resolveReturnTarget } from './returnNavigation';

describe('resolveReturnTarget', () => {
  it('resolves a platform hash route on the same origin', () => {
    const search = `?returnTo=${encodeURIComponent(
      'http://localhost:3000/#/deal/data-server/list?group=mine',
    )}`;

    expect(resolveReturnTarget(search, 'http://localhost:3000/yunti/editor/flow-1')).toBe(
      'http://localhost:3000/#/deal/data-server/list?group=mine',
    );
  });

  it('allows sibling local development ports', () => {
    const search = `?returnTo=${encodeURIComponent(
      'http://localhost:3000/#/deal/data-server/list',
    )}`;

    expect(resolveReturnTarget(search, 'http://localhost:3030/editor/flow-1')).toBe(
      'http://localhost:3000/#/deal/data-server/list',
    );
  });

  it('rejects an external return target', () => {
    const search = `?returnTo=${encodeURIComponent('https://example.com/phishing')}`;

    expect(resolveReturnTarget(search, 'https://platform.example/yunti/editor/flow-1')).toBeNull();
  });
});
