import { afterEach, describe, expect, it, vi } from 'vitest';
import packageJson from '../package.json';
import lock from '../package-lock.json';
import config from '../vite.config';
import type { UserConfig } from 'vite';
import type { TableDef } from '../src/data';
import { executeSQL } from '../src/sqlEngine';
import { clearRangeContents, rangeAsTsv } from '../src/gridRangeActions';

describe('standalone Data Elements frontend', () => {
  afterEach(() => vi.unstubAllGlobals());

  it('uses the repository project name and an independent lockfile', () => {
    expect(packageJson.name).toBe('data-elements-fengchi');
    expect(lock.name).toBe(packageJson.name);
    expect(lock.packages[''].name).toBe(packageJson.name);
    expect(lock.packages[''].dependencies).toEqual(packageJson.dependencies);
    expect(packageJson.scripts.dev).toBe('vite');
    expect(packageJson.scripts.preview).toBe('vite preview');
  });

  it('pins dev and preview to the same unused frontend port without fallback', () => {
    const resolved = config as UserConfig;
    const expected = { host: 'localhost', port: 3020, strictPort: true };
    expect(resolved.server).toEqual({...expected, proxy: {'/api/ai': {target: 'http://127.0.0.1:3021'}}});
    expect(resolved.preview).toEqual(expected);
    expect([3000, 3002, 3005, 3010, 3030, 4173]).not.toContain(expected.port);
    expect(resolved.base).toBe('/');
  });

  it.each([20, 100])('keeps %i-row read and bulk-edit operations entirely local', (size) => {
    const network = vi.fn(() => { throw new Error('No backend belongs to this frontend'); });
    vi.stubGlobal('fetch', network);
    vi.stubGlobal('XMLHttpRequest', network);
    vi.stubGlobal('WebSocket', network);
    const table: TableDef = {
      name: 'validation_rows', comment: '本地批量操作验证',
      columns: [
        { name: 'id', type: 'int', primary: true, nullable: false },
        { name: 'name', type: 'varchar(50)', nullable: false },
      ],
      rows: Array.from({ length: size }, (_, i) => ({ id: i + 1, name: `validation-${i + 1}` })),
    };
    const result = executeSQL(`SELECT * FROM validation_rows ORDER BY id DESC LIMIT ${size}`, { validation_rows: table });
    expect(result.rows).toHaveLength(size);
    const range = { rowIds: result.rows.map(row => row.id), columns: ['name'], count: size };
    expect(rangeAsTsv(table, range).split('\r\n')).toHaveLength(size);
    const cleared = clearRangeContents(table, range);
    expect(cleared.rows.every(row => row.name === '')).toBe(true);
    expect(table.rows[0].name).toBe('validation-1');
    expect(network).not.toHaveBeenCalled();
  });
});
