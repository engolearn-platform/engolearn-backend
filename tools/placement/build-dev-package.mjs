#!/usr/bin/env node
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const tools = path.dirname(fileURLToPath(import.meta.url));
const backend = path.resolve(tools, '../..');
const args = process.argv.slice(2);
if (args.includes('--help')) {
  console.log('Usage: node tools/placement/build-dev-package.mjs [--check] [--audio-base-url http://127.0.0.1:8892]');
  process.exit(0);
}
const javaArgs = [];
for (let i = 0; i < args.length; i++) {
  if (args[i] === '--check') javaArgs.push('--check');
  else if (args[i] === '--audio-base-url' && args[i + 1]) javaArgs.push(args[i], args[++i]);
  else throw new Error(`Unknown/incomplete argument: ${args[i]}`);
}
function run(command, commandArgs) {
  const result = spawnSync(command, commandArgs, { cwd: backend, stdio: 'inherit' });
  if (result.error) throw result.error;
  if (result.status !== 0) throw new Error(`${command} failed with exit ${result.status}`);
}
const temporaryRoot = path.join(os.tmpdir(), 'opencode');
if (!fs.statSync(os.tmpdir()).isDirectory()) throw new Error('Missing OS temporary directory');
fs.mkdirSync(temporaryRoot, { recursive: true });
const temporary = fs.mkdtempSync(path.join(temporaryRoot, 'placement-dev-package-'));
try {
  run(process.execPath, [path.join(tools, 'build-mongo-seed.mjs'), '--check']);
  const cpFile = path.join(temporary, 'classpath.txt');
  if (process.platform === 'win32') {
    const mvn = path.join(backend, 'mvnw.cmd');
    const quote = value => `'${value.replaceAll("'", "''")}'`;
    const command = `& ${quote(mvn)} -q '-DskipTests' compile dependency:build-classpath ${quote(`-Dmdep.outputFile=${cpFile}`)}; exit $LASTEXITCODE`;
    run('powershell.exe', ['-NoProfile', '-NonInteractive', '-EncodedCommand', Buffer.from(command, 'utf16le').toString('base64')]);
  } else {
    run(path.join(backend, 'mvnw'), ['-q', '-DskipTests', 'compile', 'dependency:build-classpath', `-Dmdep.outputFile=${cpFile}`]);
  }
  const cp = [path.join(backend, 'target/classes'), fs.readFileSync(cpFile, 'utf8').trim()].join(path.delimiter);
  run('java', ['--class-path', cp, path.join(tools, 'BuildPlacementDevPackage.java'), ...javaArgs]);
  run('mongosh', ['--nodb', '--norc', '--quiet', '--file', path.join(tools, 'validate-dev-package.mongosh.js')]);
} finally {
  fs.rmSync(temporary, { recursive: true, force: true });
}
