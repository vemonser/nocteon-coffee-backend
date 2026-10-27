#!/usr/bin/env node
'use strict';
/**
 * check-discovery-artifact.cjs — AC-04 checker.
 * Validates the recorded verify-discovery output:
 *   - the resolver JSON artifact parses and contains a genuine RESOLVED command
 *   - the command is non-empty and not a forbidden placeholder
 *   - the referenced on-disk discovery artifacts (resolver JSON + stderr log) exist
 * Reads: <resolver-result.json> and <resolver-stderr.log>
 * Usage: node check-discovery-artifact.cjs <resolver-result.json> <resolver-stderr.log>
 */
const fs = require('node:fs');

const [file, stderrLog] = process.argv.slice(2);
if (!file || !stderrLog) {
  process.stderr.write('usage: node check-discovery-artifact.cjs <resolver-result.json> <resolver-stderr.log>\n');
  process.exit(64);
}

const FORBIDDEN = [/^\s*true\s*$/, /^\s*false\s*$/, /^\s*$/, /^\s*echo\s+\S*(pass|ok|success)\S*\s*$/, /^\s*exit\s+0\s*$/];
const problems = [];
let d;
try {
  d = JSON.parse(fs.readFileSync(file, 'utf8'));
} catch (e) {
  process.stderr.write(`unreadable: ${e.message}\n`);
  process.exit(1);
}
if (d.status !== 'RESOLVED') problems.push(`status is ${d.status} (expected RESOLVED)`);
if (!d.resolved) problems.push('no resolved command recorded');
else {
  for (const re of FORBIDDEN) if (re.test(d.resolved)) problems.push(`resolved command is a forbidden placeholder: "${d.resolved}"`);
  if (/\btrue\b/.test(d.resolved) && d.resolved.trim() === 'true') problems.push('resolved command is the literal no-op `true`');
}
for (const p of [file, stderrLog]) {
  if (!fs.existsSync(p)) problems.push(`recorded discovery artifact missing on disk: ${p}`);
}

process.stdout.write(problems.length ? `DISCOVERY-CHECK FAIL\n${problems.map((p) => `- ${p}`).join('\n')}\n` : `DISCOVERY-CHECK PASS — status=${d.status} resolved="${d.resolved}" source=${d.source}; JSON + stderr artifacts exist on disk (command NOT executed here, no pass claim made)\n`);
process.exit(problems.length ? 1 : 0);
