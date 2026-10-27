#!/usr/bin/env node
'use strict';
/**
 * generate-audit.cjs — read-only inventory + integrity check for Flyway migrations.
 * Reads src/main/resources/db/migration ONLY; writes two artifacts to cwd's run dir.
 *
 * Usage: node generate-audit.cjs <output-tsv> <report-md>
 * Exit codes: 0 = all integrity checks pass (no duplicate versions, all names conform)
 *             1 = at least one integrity failure (details on stdout)
 */
const fs = require('node:fs');
const path = require('node:path');

const MIG_DIR = path.join('src', 'main', 'resources', 'db', 'migration');
const [outTsv, outReport] = process.argv.slice(2);
if (!outTsv || !outReport) {
  process.stderr.write('usage: node generate-audit.cjs <tsv> <report>\n');
  process.exit(64);
}

const NAME_RE = /^V([0-9]+(_[0-9]+)*)__([A-Za-z0-9_]+)\.sql$/;

const files = fs
  .readdirSync(MIG_DIR)
  .filter((f) => f.endsWith('.sql'))
  .sort();

const rows = files.map((f) => {
  const m = NAME_RE.exec(f);
  return {
    file: f,
    versionLabel: m ? m[1] : null,
    segments: m ? m[1].split('_').map(Number) : null,
    description: m ? m[3] : null,
    conforms: !!m,
  };
});

// Duplicate Flyway version = identical segments tuple.
// Display order: by parsed Flyway version (segment tuple), non-conforming last.
const byVersion = new Map();
for (const r of rows) {
  if (!r.conforms) continue;
  const key = r.segments.join('.');
  byVersion.set(key, (byVersion.get(key) || []).concat([r.file]));
}
const duplicateGroups = [...byVersion.entries()].filter(([, fs]) => fs.length > 1);

const ordered = [...rows].sort((a, b) => {
  if (a.conforms !== b.conforms) return a.conforms ? -1 : 1;
  if (!a.conforms) return 0;
  for (let i = 0; i < Math.max(a.segments.length, b.segments.length); i++) {
    const da = a.segments[i] ?? -1;
    const db = b.segments[i] ?? -1;
    if (da !== db) return da - db;
  }
  return 0;
});

const conforming = rows.filter((r) => r.conforms);
const nonConforming = rows.filter((r) => !r.conforms);

// ── Write inventory (works even when checks fail — the inventory is the artifact) ──
fs.writeFileSync(
  outTsv,
  ['file\tversion\tsegments\tdescription\tconforms']
    .concat(ordered.map((r) => `${r.file}\t${r.versionLabel ?? ''}\t${r.segments ? r.segments.join('.') : ''}\t${r.description ?? ''}\t${r.conforms}`))
    .join('\n') + '\n'
);

const lines = [];
lines.push('# Flyway Migration Inventory & Integrity Report');
lines.push('');
lines.push(`Migration files scanned: ${files.length}`);
lines.push(`Conforming filenames: ${conforming.length}`);
lines.push(`Non-conforming filenames: ${nonConforming.length}`);
lines.push(`Duplicate Flyway versions: ${duplicateGroups.length}`);
lines.push('');
if (duplicateGroups.length) {
  lines.push('## DUPLICATES (checker FAIL)');
  for (const [version, dupFiles] of duplicateGroups) lines.push(`- version ${version}: ${dupFiles.join(', ')}`);
  lines.push('');
}
if (nonConforming.length) {
  lines.push('## NON-CONFORMING FILENAMES (checker FAIL)');
  for (const r of nonConforming) lines.push(`- \`${r.file}\` — does not match V<version>__<name>.sql`);
  lines.push('');
}
lines.push('## Migrations sorted by Flyway version (segment tuple; e.g. 106, 106.1, 106.5)');
for (const r of ordered) lines.push(`| \`${r.file}\` | ${r.segments ? r.segments.join('.') : '(non-conforming)'} |`);
fs.writeFileSync(outReport, lines.join('\n') + '\n');

const failed = duplicateGroups.length > 0 || nonConforming.length > 0;
process.stdout.write(
  `files=${files.length} conforming=${conforming.length} nonConforming=${nonConforming.length} duplicateVersionGroups=${duplicateGroups.length} → ${failed ? 'FAIL' : 'PASS'}\n`
);
process.exit(failed ? 1 : 0);
