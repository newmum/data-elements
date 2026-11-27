import test from 'node:test';
import assert from 'node:assert/strict';
import { cloneConfiguration } from '../../utils/browserCompat.ts';
import { reportRandomId } from '../../report/core/browserId.ts';
import { previewCell } from './governancePreview.ts';
import { setConditional } from './unifiedRuleModel.ts';

test('governance rule cloning and conditional branches work without structuredClone', () => {
  const original = globalThis.structuredClone;
  try {
    globalThis.structuredClone = undefined;
    const rule = { ruleId:'one', target:{name:'id'}, input:{kind:'FIELD',name:'id'}, steps:[], enabled:true };
    const cloned = cloneConfiguration(rule);
    cloned.input.name = 'other';
    assert.equal(rule.input.name, 'id');
    const conditional = setConditional(rule, true);
    assert.notEqual(conditional.then.input, rule.input);
    assert.equal(conditional.then.input.name, 'id');
    assert.equal(previewCell({ id: 4 }, 'id'), 4);
    assert.equal(previewCell({ id: 4 }, 'toString'), undefined);
  } finally { globalThis.structuredClone = original; }
});

test('UUID uses secure random bytes without randomUUID', () => {
  const random = { getRandomValues(bytes) { bytes.fill(1); return bytes; } };
  assert.match(reportRandomId(random), /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});
