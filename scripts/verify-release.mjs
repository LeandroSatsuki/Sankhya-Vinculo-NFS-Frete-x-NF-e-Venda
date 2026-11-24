import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../', import.meta.url));
const json = name => JSON.parse(readFileSync(path.join(root, name), 'utf8'));
const within = (base, name) => {
  assert(!path.isAbsolute(name), `Absolute path forbidden: ${name}`);
  const result = path.resolve(base, name);
  const relative = path.relative(base, result);
  assert(relative !== '..' && !relative.startsWith(`..${path.sep}`) && !path.isAbsolute(relative), `Path escape: ${name}`);
  return result;
};
const walk = directory => readdirSync(directory, { withFileTypes: true }).flatMap(entry => {
  const item = path.join(directory, entry.name);
  return entry.isDirectory() ? walk(item) : [item];
});
const index = json('releases/index.json');
assert.equal(index.schemaVersion, 1);
for (const component of index.components) {
  const base = within(root, component.directory);
  const gradle = readFileSync(path.join(base, 'build.gradle'), 'utf8');
  const version = gradle.match(/^\s*version\s*=\s*["']([^"']+)["']/m)?.[1];
  assert.equal(version, component.version, `Version drift: ${component.name}`);
  if (!component.manifest) continue;
  const manifest = json(component.manifest);
  assert.equal(manifest.schemaVersion, 1);
  assert.equal(manifest.version, component.version);
  assert.equal(manifest.sourceDirectory, component.directory);
  assert.equal(manifest.context, component.context);
  assert.equal(manifest.servicePrefix, component.servicePrefix);
  for (const field of ['sourceArchiveSha256', 'packageSha256']) {
    assert.match(manifest[field], /^[a-f0-9]{64}$/, `Invalid ${field}`);
  }
  assert(Object.keys(manifest.files).length > 0, 'Empty source manifest');
  for (const [name, expected] of Object.entries(manifest.files)) {
    const digest = createHash('sha256').update(readFileSync(within(base, name))).digest('hex');
    assert.equal(digest, expected, `Delivery source changed: ${name}`);
  }
  // New production files must also belong to the declared release snapshot.
  for (const folder of ['model/src', 'vc/src', 'datadictionary', 'dbscripts']) {
    for (const file of walk(path.join(base, folder))) {
      const relative = path.relative(base, file).split(path.sep).join('/');
      assert(Object.hasOwn(manifest.files, relative), `Unrecorded delivery file: ${relative}`);
    }
  }
  assert.match(gradle, /autoDDL\s*=\s*false/, 'LOGISTICA must preserve shared TFV definitions');
  const settings = readFileSync(path.join(base, 'settings.gradle'), 'utf8');
  assert.match(settings, /rootProject\.name\s*=\s*'addon-logistica'/);
}
console.log('Release index, versions and delivery source hashes verified. No ERP access.');
