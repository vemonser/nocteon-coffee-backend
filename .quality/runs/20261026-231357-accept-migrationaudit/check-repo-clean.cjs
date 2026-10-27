#!/usr/bin/env node
'use strict';
/**
 * check-repo-clean.cjs — checkpoint-9 fidelity for an analysis-only run.
 * Compares `git status --porcelain` and `git rev-parse HEAD` against the
 * recorded baseline artifacts.
 *
 * PASS (0) iff:
 *   - HEAD is identical to baseline-head.txt
 *   - no tracked file is modified/added/deleted/renamed (any marker other than `??`)
 *   - new untracked entries are a subset of exactly ONE allowed run dir
 *   - baseline entries are preserved
 * Usage: node check-repo-clean.cjs <baseline-status.txt> <baseline-head.txt> <allowed-run-dir>
 */
const { execFileSync } = require('node:child_process');
const fs = require('node:fs');

const [baselinePath, baselineHeadPath, allowedDir] = process.argv.slice(2);
if (!baselinePath || !baselineHeadPath || !allowedDir) {
  process.stderr.write('usage: node check-repo-clean.cjs <baseline-status.txt> <baseline-head.txt> <allowed-run-dir>\n');
  process.exit(64);
}

const baseline = fs.readFileSync(baselinePath, 'utf8').split('\n').filter(Boolean);
const baselineHead = fs.readFileSync(baselineHeadPath, 'utf8').trim();
const currentHead = execFileSync('git', ['rev-parse', 'HEAD'], { encoding: 'utf8' }).trim();
const current = execFileSync('git', ['status', '--porcelain'], { encoding: 'utf8' }).split('\n').filter(Boolean);

const problems = [];

if (currentHead !== baselineHead) {
  problems.push(`HEAD moved: baseline=${baselineHead} current=${currentHead}`);
}

for (const line of current) {
  // Explicit tracked-change markers: any status other than untracked `??`.
  if (!line.startsWith('??')) {
    problems.push(`tracked-change: ${line}`);
  } else {
    const entry = line.slice(3);
    const isBaseline = baseline.includes(line);
    const isAllowedRun = entry === allowedDir || entry === `${allowedDir}/`;
    if (!isBaseline && !isAllowedRun) problems.push(`unexpected untracked: ${line}`);
  }
}

// Conversely: baseline entries must not have vanished.
const currentEntries = new Set(current.map((l) => (l.startsWith('??') ? l.slice(3) : null)).filter(Boolean));
for (const b of baseline) {
  if (b.startsWith('??')) {
    const e = b.slice(3);
    const stillListed = currentEntries.has(e) || current.some((l) => l.startsWith('??') && l.slice(3).startsWith(e.replace(/\/$/, '') + '/'));
    if (!stillListed) problems.push(`baseline entry disappeared: ${b}`);
  }
}

process.stdout.write(problems.length ? `CLEAN FAIL\n${problems.map((p) => `- ${p}`).join('\n')}\n` : `CLEAN PASS — HEAD ${currentHead} matches baseline; no tracked changes; untracked delta ⊆ {run dir}; baseline entries preserved\n`);
process.exit(problems.length ? 1 : 0);
