#!/usr/bin/env node
'use strict';
/**
 * check-inventory-rows.cjs — independent AC-01 corroboration.
 * Does NOT trust generate-audit.cjs internals: independently counts SQL files
 * in the migration dir and asserts the TSV has exactly that many data rows
 * (plus one header) with no blank file cells.
 * Usage: node check-inventory-rows.cjs <migration-dir> <inventory-tsv>
 */
const fs = require('node:fs');
const path = require('node:path');

const [migDir, tsvPath] = process.argv.slice(2);
if (!migDir || !tsvPath) {
  process.stderr.write('usage: node check-inventory-rows.cjs <migration-dir> <tsv>\n');
  process.exit(64);
}

const files = fs.readdirSync(migDir).filter((f) => f.endsWith('.sql'));
const lines = fs.readFileSync(tsvPath, 'utf8').split('\n').filter((l) => l.length > 0);
const problems = [];

if (lines[0].split('\t')[0] !== 'file') problems.push('missing TSV header');
const rows = lines.slice(1);
if (rows.length !== files.length) {
  problems.push(`row count ${rows.length} != file count ${files.length}`);
}
rows.forEach((r, i) => {
  const fileCell = r.split('\t')[0];
  if (!fileCell) problems.push(`row ${i + 1}: empty file cell`);
  else if (!files.includes(fileCell)) problems.push(`row "${fileCell}" is not a migration file`);
});
for (const f of files) {
  if (!rows.some((r) => r.startsWith(`${f}\t`))) problems.push(`file missing from inventory: ${f}`);
}

process.stdout.write(problems.length ? `INVENTORY-CHECK FAIL\n${problems.map((p) => `- ${p}`).join('\n')}\n` : `INVENTORY-CHECK PASS — ${rows.length} data rows == ${files.length} migration files, bijective\n`);
process.exit(problems.length ? 1 : 0);
