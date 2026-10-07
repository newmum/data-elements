/** Compile canonical low-code with the same compiler used by the online editor. */
import fs from 'node:fs/promises';
import path from 'node:path';
import vm from 'node:vm';
import {fileURLToPath} from 'node:url';
import {createHash} from 'node:crypto';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const file = process.argv[2];
if (!file) throw new Error('Usage: node tools/compile-lowcode.mjs lowcode/<group>/<name>.vue');
const canonical = path.join(root, 'lowcode');
const input = path.resolve(root, file);
const relative = path.relative(canonical, input);
if (relative.startsWith('..') || path.isAbsolute(relative) || !input.endsWith('.vue')) {
  throw new Error('Compile only canonical lowcode/*.vue source');
}
const realRoot = await fs.realpath(root);
const realCanonical = await fs.realpath(canonical);
const realInput = await fs.realpath(input);
const inside = (base, target) => {
  const rel = path.relative(base, target);
  return rel !== '..' && !rel.startsWith('..' + path.sep) && !path.isAbsolute(rel);
};
if (!inside(realRoot, realCanonical) || !inside(realCanonical, realInput)) {
  throw new Error('Canonical low-code path cannot escape through a symlink or junction');
}
const manifest = JSON.parse(await fs.readFile(path.join(root, 'lowcode/.manifest.json'), 'utf8'));
const matches = manifest.resources.filter(e => e.path === relative.split(path.sep).join('/'));
const entry = matches.length === 1 ? matches[0] : null;
if (!entry || entry.type === '0' || typeof entry.id !== 'string' || !entry.id) {
  throw new Error('Register one non-directory component identity in the manifest before compilation');
}
const source = await fs.readFile(input, 'utf8');
const sandbox = {};
vm.runInNewContext(await fs.readFile(path.join(root, 'data-elements-chengtian/public/babel.min.js'), 'utf8'), sandbox);
globalThis.window = {Babel: sandbox.Babel};
const {compileCode} = await import('../data-elements-chengtian/src/plugins/compiler/sfc-compiler.js');
const compiled = await compileCode(source);
if (!compiled.compileJs.includes('return __sfc__')) throw new Error('Missing low-code runtime factory');
// Parse generated JavaScript without running component code.
new Function(compiled.compileJs);
// Identity is data, not a local path. A hash avoids Windows reserved names and traversal.
const outputDirectory = path.join(root, 'logs/lowcode/compiled');
let ancestor = outputDirectory;
while (true) {
  try { await fs.access(ancestor); break; }
  catch (error) {
    if (error.code !== 'ENOENT') throw error;
    const parent = path.dirname(ancestor);
    if (parent === ancestor) throw new Error('No repository ancestor for compiled output');
    ancestor = parent;
  }
}
if (!inside(realRoot, await fs.realpath(ancestor))) throw new Error('Compiled output escapes repository');
await fs.mkdir(outputDirectory, {recursive: true});
if (!inside(realRoot, await fs.realpath(outputDirectory))) throw new Error('Compiled output escapes repository');
const output = path.join(outputDirectory, createHash('sha256').update(entry.id).digest('hex') + '.json');
try {
  if (!inside(realRoot, await fs.realpath(output))) throw new Error('Compiled output escapes repository');
} catch (error) { if (error.code !== 'ENOENT') throw error; }
await fs.writeFile(output, JSON.stringify({
  componentId: entry.id,
  sourceSha256: createHash('sha256').update(source.replace(/\r\n?/g, '\n')).digest('hex'),
  ...compiled,
}, null, 2) + '\n');
console.log(JSON.stringify({source: file, compiled: path.relative(root, output), syntaxVerified: true}));
