import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
import { compiler } from '../../../scripts/compiler.mjs';

test('actual status/error query functions mark background reads quiet and retain rejection and cancellation signals', async () => {
  const ts = compiler();
  const source = fs.readFileSync(new URL('../api/pipelines.ts', import.meta.url), 'utf8').replaceAll('import.meta.env', '({})');
  const compiled = ts.transpileModule(source, { compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.CommonJS } }).outputText;
  const queries = [], calls = [], backendError = new Error('backend unavailable');
  const api = { get: async (path, config) => { calls.push({ path, config }); throw backendError; } };
  const exported = {};
  const require = id => {
    if (id === '@tanstack/react-query') return { useQuery: options => { queries.push(options); return {}; } };
    if (id === './client') return { apiClient: api, BACKGROUND_REQUEST_OPTIONS: { nifiSilentGlobalErrors: true } };
    if (id === './bridgeSession') return { useTokenReady: () => true, useSessionRevision: () => 7 };
    if (id === './response') return { requireListResponse: payload => payload };
    return {};
  };
  vm.runInNewContext(compiled, { exports: exported, require });
  const abort = new AbortController();
  exported.useFlowStatus('allowed-pipeline');
  await assert.rejects(queries.at(-1).queryFn({ signal: abort.signal }), error => error === backendError);
  exported.useFlowErrors('allowed-pipeline');
  await assert.rejects(queries.at(-1).queryFn({ signal: abort.signal }), error => error === backendError);
  assert.deepEqual(calls.map(call => call.path), ['/pipelines/allowed-pipeline/status', '/pipelines/allowed-pipeline/errors']);
  for (const call of calls) {
    assert.equal(call.config.nifiSilentGlobalErrors, true);
    assert.equal(call.config.signal, abort.signal);
  }
  assert.equal(queries.every(query => query.queryKey.includes('allowed-pipeline') && query.queryKey.includes(7)), true);
});
