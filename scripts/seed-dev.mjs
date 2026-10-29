#!/usr/bin/env node
'use strict';

import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';

const DB = process.env.DB_NAME || 'nocteon_db';
const USER = process.env.DB_USER || 'postgres';
const CONTAINER = process.env.DB_CONTAINER || 'nocteon-db';

const PRESERVE = new Set(['flyway_schema_history','roles','role_permissions','shipping_zones','shipping_zone_cities','store_settings']);
function getDemoTables() {
  const out = q("SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;");
  return out.split('\n').map(s=>s.trim()).filter(Boolean).filter(t=>!PRESERVE.has(t));
}
const DEMO_TABLES = getDemoTables();

const args = new Set(process.argv.slice(2));
const yes = args.has('--yes');
const verify = args.has('--verify');

function psql(sql, { stdinFile, tuples = false, quiet = true } = {}) {
  const argv = ['exec', '-i', CONTAINER, 'psql', '-U', USER, '-d', DB, '-v', 'ON_ERROR_STOP=1'];
  if (quiet) argv.push('-q');
  if (tuples) argv.push('-t', '-A');
  const opts = { encoding: 'utf8', maxBuffer: 1024 * 1024 * 50 };
  if (stdinFile) {
    const content = fs.readFileSync(stdinFile, 'utf8');
    opts.input = content;
  } else if (sql != null) {
    opts.input = sql;
  }
  const r = spawnSync('docker', argv, opts);
  if (r.status !== 0) {
    process.stderr.write(r.stderr || r.stdout || '');
    process.exit(r.status == null ? 1 : r.status);
  }
  return (r.stdout || '').trim();
}

function q(sql) {
  return psql(sql, { tuples: true });
}

const info = q("SELECT current_database() || ' / schema=' || current_schema();");
console.log('Target database/schema:', info);

if (verify) {
  const lines = [];
  for (const t of DEMO_TABLES) {
    const dt = q(`SELECT data_type FROM information_schema.columns WHERE table_schema='public' AND table_name='${t}' AND column_name='id';`).trim();
    let out;
    if (dt === 'integer' || dt === 'bigint' || dt === 'smallint') {
      out = q(`SELECT count(*) || '|' || COALESCE(sum(id),0) FROM public.${t};`);
    } else {
      out = q(`SELECT count(*) || '|0' FROM public.${t};`);
    }
    const [c, s] = out.split('|');
    lines.push(`${t}: count=${c} sum_id=${s}`);
  }
  const body = lines.join('\n');
  console.log('--- VERIFY ---');
  console.log(body);
  const hash = crypto.createHash('sha256').update(body).digest('hex');
  console.log('CHECKSUM:', hash);
  process.exit(0);
}

if (!yes) {
  console.error('Refusing to run without --yes. This seed DESTROYS dev data in', DB);
  console.error('Re-run with: node scripts/seed-dev.mjs --yes');
  process.exit(2);
}

console.log('--- row counts BEFORE delete ---');
for (const t of DEMO_TABLES) {
  const out = q(`SELECT count(*) FROM public.${t};`);
  console.log(`${t}: ${out}`);
}

const seedFile = path.join(path.dirname(new URL(import.meta.url).pathname), 'seed-dev.sql');
if (!fs.existsSync(seedFile)) {
  console.error('Missing', seedFile);
  process.exit(1);
}
console.log('--- applying seed ---');
psql(null, { stdinFile: seedFile });
console.log('Seed applied successfully (exit 0).');

console.log('--- row counts AFTER ---');
for (const t of DEMO_TABLES) {
  const out = q(`SELECT count(*) FROM public.${t};`);
  console.log(`${t}: ${out}`);
}
