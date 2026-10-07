import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../../magic/', import.meta.url));
const manifest = JSON.parse(fs.readFileSync(path.join(root, '.manifest.json'), 'utf8'));

// Resolve by the editor metadata ID so renaming a directory does not break contracts.
export function magicResource(metadataId) {
  const matches = manifest.resources.filter(resource => resource.metadataId === metadataId);
  assert.equal(matches.length, 1, `Expected one canonical Magic resource: ${metadataId}`);
  const entry = matches[0];
  const filename = path.resolve(root, entry.path);
  assert.ok(filename.startsWith(root), `Magic path escapes the canonical directory: ${entry.path}`);
  const content = fs.readFileSync(filename, 'utf8');
  const separator = content.indexOf('================================');
  assert.ok(separator >= 0, `Missing Magic metadata separator: ${metadataId}`);
  const metadata = JSON.parse(content.slice(0, separator));
  assert.equal(metadata.id, metadataId, `Magic metadata ID differs from the manifest: ${entry.path}`);
  return { entry, metadata, content, script: content.slice(separator + 32).trim() };
}

export function magicScript(metadataId) {
  return magicResource(metadataId).script;
}
