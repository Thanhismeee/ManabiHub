import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadEnv } from 'vite';

const REQUIRED_WHEN_ENABLED = [
  'VITE_VNPT_EKYC_BACKEND_URL',
  'VITE_VNPT_EKYC_TOKEN_ID',
  'VITE_VNPT_EKYC_TOKEN_KEY',
  'VITE_VNPT_EKYC_ACCESS_TOKEN',
];

const OPTIONAL_WHEN_ENABLED = [
  'VITE_VNPT_EKYC_CHALLENGE_CODE',
];

const PLACEHOLDER_VALUE = /^(?:<[^>]+>|change[-_ ]?me|replace[-_ ]?me|todo|your[-_ ])/i;
const APPROVED_VNPT_BACKEND_ORIGINS = new Set([
  'https://api.idg.vnpt.vn',
  'https://sandbox-idg.vnpt.vn',
]);

function valueOf(env, name) {
  return String(env[name] ?? '').trim();
}

function validateHttpsUrl(name, rawValue, errors, options = {}) {
  let parsed;
  try {
    parsed = new URL(rawValue);
  } catch {
    errors.push(`${name} must be an absolute HTTPS URL.`);
    return null;
  }

  if (parsed.protocol !== 'https:') {
    errors.push(`${name} must use HTTPS.`);
  }
  if (parsed.username || parsed.password) {
    errors.push(`${name} must not contain URL credentials.`);
  }
  if (parsed.hash) {
    errors.push(`${name} must not contain a URL fragment.`);
  }
  if (!options.allowSearch && parsed.search) {
    errors.push(`${name} must not contain query parameters.`);
  }
  return parsed;
}

function validateScriptUrls(rawValue, errors) {
  const urls = rawValue
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);

  for (const url of urls) {
    if (url.startsWith('/') && !url.startsWith('//') && !url.includes('\\')) {
      continue;
    }
    errors.push('VITE_VNPT_EKYC_SDK_SCRIPT_URLS entries must be root-relative same-origin paths.');
  }
}

function validateVnptEnv(env, requireEnabled) {
  const errors = [];
  const enabledValue = valueOf(env, 'VITE_VNPT_EKYC_ENABLED').toLowerCase();

  if (enabledValue && enabledValue !== 'true' && enabledValue !== 'false') {
    errors.push('VITE_VNPT_EKYC_ENABLED must be exactly true or false.');
    return errors;
  }

  if (requireEnabled && enabledValue !== 'true') {
    errors.push('VITE_VNPT_EKYC_ENABLED must be true for a protected VNPT release build.');
    return errors;
  }
  if (enabledValue !== 'true') {
    return errors;
  }

  for (const name of REQUIRED_WHEN_ENABLED) {
    const value = valueOf(env, name);
    if (!value) {
      errors.push(`${name} is required when VITE_VNPT_EKYC_ENABLED=true.`);
    } else if (PLACEHOLDER_VALUE.test(value) || /[\r\n]/.test(value)) {
      errors.push(`${name} still contains a placeholder or invalid control characters.`);
    }
  }

  for (const name of OPTIONAL_WHEN_ENABLED) {
    const value = valueOf(env, name);
    if (value && (PLACEHOLDER_VALUE.test(value) || /[\r\n]/.test(value))) {
      errors.push(`${name} still contains a placeholder or invalid control characters.`);
    }
  }

  const backendUrl = valueOf(env, 'VITE_VNPT_EKYC_BACKEND_URL');
  if (backendUrl && !PLACEHOLDER_VALUE.test(backendUrl)) {
    const parsedBackend = validateHttpsUrl('VITE_VNPT_EKYC_BACKEND_URL', backendUrl, errors);
    if (parsedBackend && (
      !APPROVED_VNPT_BACKEND_ORIGINS.has(parsedBackend.origin)
      || parsedBackend.pathname !== '/'
      || parsedBackend.search
      || parsedBackend.hash
    )) {
      errors.push('VITE_VNPT_EKYC_BACKEND_URL must use an exact approved VNPT production or sandbox origin.');
    }
  }

  validateScriptUrls(valueOf(env, 'VITE_VNPT_EKYC_SDK_SCRIPT_URLS'), errors);
  return errors;
}

function requestedMode() {
  const modeIndex = process.argv.indexOf('--mode');
  return modeIndex >= 0 && process.argv[modeIndex + 1]
    ? process.argv[modeIndex + 1]
    : 'production';
}

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const fileEnv = loadEnv(requestedMode(), frontendRoot, 'VITE_');
const processViteEnv = Object.fromEntries(
  Object.entries(process.env).filter(([name, value]) => name.startsWith('VITE_') && value !== undefined),
);
const errors = validateVnptEnv(
  { ...fileEnv, ...processViteEnv },
  process.argv.includes('--require-enabled'),
);

if (errors.length > 0) {
  console.error('VNPT eKYC browser build configuration is invalid:');
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  console.error('Only VNPT-approved browser-scoped values may use the VITE_ prefix.');
  process.exitCode = 1;
} else {
  const enabled = valueOf({ ...fileEnv, ...processViteEnv }, 'VITE_VNPT_EKYC_ENABLED').toLowerCase() === 'true';
  console.log(`VNPT eKYC browser build configuration valid (enabled=${enabled}).`);
}
