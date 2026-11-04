import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadEnv } from 'vite';

const REQUIRED_WHEN_ENABLED = [
  'VITE_FIREBASE_API_KEY',
  'VITE_FIREBASE_AUTH_DOMAIN',
  'VITE_FIREBASE_PROJECT_ID',
  'VITE_FIREBASE_APP_ID',
  'VITE_FIREBASE_MESSAGING_SENDER_ID',
];

const PLACEHOLDER_VALUE = /^(?:<[^>]+>|change[-_ ]?me|replace[-_ ]?me|todo|your[-_ ])/i;

function valueOf(env, name) {
  return String(env[name] ?? '').trim();
}

function requestedMode() {
  const modeIndex = process.argv.indexOf('--mode');
  return modeIndex >= 0 && process.argv[modeIndex + 1]
    ? process.argv[modeIndex + 1]
    : 'production';
}

function validateFirebasePhoneEnv(env) {
  const errors = [];
  const enabled = valueOf(env, 'VITE_FIREBASE_PHONE_AUTH_ENABLED').toLowerCase();
  if (enabled && enabled !== 'true' && enabled !== 'false') {
    errors.push('VITE_FIREBASE_PHONE_AUTH_ENABLED must be exactly true or false.');
    return errors;
  }
  if (enabled !== 'true') {
    return errors;
  }

  for (const name of REQUIRED_WHEN_ENABLED) {
    const value = valueOf(env, name);
    if (!value) {
      errors.push(`${name} is required when VITE_FIREBASE_PHONE_AUTH_ENABLED=true.`);
    } else if (PLACEHOLDER_VALUE.test(value) || /[\r\n]/.test(value)) {
      errors.push(`${name} still contains a placeholder or control character.`);
    }
  }

  const apiKey = valueOf(env, 'VITE_FIREBASE_API_KEY');
  if (apiKey && !PLACEHOLDER_VALUE.test(apiKey) && !/^AIza[0-9A-Za-z_-]{30,}$/.test(apiKey)) {
    errors.push('VITE_FIREBASE_API_KEY does not look like a Firebase Web API key.');
  }
  const authDomain = valueOf(env, 'VITE_FIREBASE_AUTH_DOMAIN');
  if (authDomain && !PLACEHOLDER_VALUE.test(authDomain)
      && (!/^[a-z0-9.-]+$/i.test(authDomain) || authDomain.includes('..'))) {
    errors.push('VITE_FIREBASE_AUTH_DOMAIN must be a hostname without scheme, path, or port.');
  }
  const projectId = valueOf(env, 'VITE_FIREBASE_PROJECT_ID');
  if (projectId && !PLACEHOLDER_VALUE.test(projectId) && !/^[a-z][a-z0-9-]{4,28}[a-z0-9]$/.test(projectId)) {
    errors.push('VITE_FIREBASE_PROJECT_ID is not a valid Google Cloud project ID.');
  }
  const senderId = valueOf(env, 'VITE_FIREBASE_MESSAGING_SENDER_ID');
  if (senderId && !PLACEHOLDER_VALUE.test(senderId) && !/^\d{6,20}$/.test(senderId)) {
    errors.push('VITE_FIREBASE_MESSAGING_SENDER_ID must contain digits only.');
  }
  return errors;
}

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const fileEnv = loadEnv(requestedMode(), frontendRoot, 'VITE_');
const processViteEnv = Object.fromEntries(
  Object.entries(process.env).filter(([name, value]) => name.startsWith('VITE_') && value !== undefined),
);
const env = { ...fileEnv, ...processViteEnv };
const errors = validateFirebasePhoneEnv(env);

if (errors.length > 0) {
  console.error('Firebase Phone Auth browser build configuration is invalid:');
  for (const error of errors) {
    console.error(`- ${error}`);
  }
  console.error('Only public Firebase Web configuration may use the VITE_ prefix.');
  process.exitCode = 1;
} else {
  const enabled = valueOf(env, 'VITE_FIREBASE_PHONE_AUTH_ENABLED').toLowerCase() === 'true';
  console.log(`Firebase Phone Auth browser build configuration valid (enabled=${enabled}).`);
}
