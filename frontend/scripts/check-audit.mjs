import { spawnSync } from 'node:child_process';

const ACCEPTED_ADVISORIES = new Set(['GHSA-QWWW-VCR4-C8H2']);
const ACCEPTANCE_EXPIRES_AT = new Date('2027-08-15T23:59:59+07:00');
const npmExecutable = process.platform === 'win32' ? 'npm.cmd' : 'npm';
const npmCli = process.env.npm_execpath;

const audit = spawnSync(npmCli ? process.execPath : npmExecutable, [
  ...(npmCli ? [npmCli] : []),
  'audit',
  '--json',
], {
  encoding: 'utf8',
  shell: false,
});

let report;
try {
  report = JSON.parse(audit.stdout);
} catch {
  console.error(
    audit.error?.message
      || audit.stderr
      || audit.stdout
      || 'npm audit did not return JSON.',
  );
  process.exit(1);
}

if (report.error) {
  console.error(`npm audit failed: ${report.error.summary ?? 'unknown error'}`);
  process.exit(1);
}

const vulnerabilities = report.vulnerabilities ?? {};

function advisoryIdsFor(packageName, visited = new Set()) {
  if (visited.has(packageName)) {
    return new Set();
  }
  visited.add(packageName);

  const vulnerability = vulnerabilities[packageName];
  const advisoryIds = new Set();
  for (const source of vulnerability?.via ?? []) {
    if (typeof source === 'string') {
      for (const advisoryId of advisoryIdsFor(source, visited)) {
        advisoryIds.add(advisoryId);
      }
      continue;
    }

    const advisoryId = source.url?.match(/GHSA-[\w-]+/i)?.[0]?.toUpperCase();
    advisoryIds.add(advisoryId ?? `SOURCE-${source.source ?? 'UNKNOWN'}`);
  }
  return advisoryIds;
}

const rejected = [];
for (const packageName of Object.keys(vulnerabilities)) {
  const advisoryIds = advisoryIdsFor(packageName);
  if (
    advisoryIds.size === 0
    || [...advisoryIds].some((advisoryId) => !ACCEPTED_ADVISORIES.has(advisoryId))
  ) {
    rejected.push(`${packageName}: ${[...advisoryIds].join(', ') || 'unresolved advisory'}`);
  }
}

if (rejected.length > 0) {
  console.error(`Unaccepted dependency vulnerabilities:\n- ${rejected.join('\n- ')}`);
  process.exit(1);
}

if (Object.keys(vulnerabilities).length > 0 && Date.now() > ACCEPTANCE_EXPIRES_AT.getTime()) {
  console.error(
    `Dependency risk acceptance expired at ${ACCEPTANCE_EXPIRES_AT.toISOString()}. `
      + 'Upgrade the Node/React Router baseline or renew the acceptance with review.',
  );
  process.exit(1);
}

const totals = report.metadata?.vulnerabilities ?? {};
console.log(
  `Dependency audit accepted: ${totals.total ?? 0} package finding(s); `
    + `${ACCEPTED_ADVISORIES.size} scoped RSC-only advisory accepted until `
    + `${ACCEPTANCE_EXPIRES_AT.toISOString()}.`,
);
