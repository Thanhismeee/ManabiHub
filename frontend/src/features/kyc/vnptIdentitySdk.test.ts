import { afterEach, beforeAll, beforeEach, describe, expect, it, vi } from 'vitest';
import { launchVnptIdentitySdk, resetVnptIdentitySdkRuntime } from './vnptIdentitySdk';

type SdkConfig = Record<string, unknown>;

let errorListeners: EventListener[] = [];
let rejectionListeners: EventListener[] = [];
let originalAddEventListener: typeof window.addEventListener;
let originalRemoveEventListener: typeof window.removeEventListener;
let originalFetch: typeof window.fetch;

beforeAll(() => {
  originalAddEventListener = window.addEventListener;
  originalRemoveEventListener = window.removeEventListener;
  originalFetch = window.fetch;
});

beforeEach(() => {
  errorListeners = [];
  rejectionListeners = [];
  vi.spyOn(window, 'addEventListener').mockImplementation(function (this: any, type: string, listener: EventListenerOrEventListenerObject, options?: boolean | AddEventListenerOptions) {
    if (type === 'error') {
      errorListeners.push(listener as EventListener);
    } else if (type === 'unhandledrejection') {
      rejectionListeners.push(listener as EventListener);
    }
    return originalAddEventListener.call(this, type, listener, options);
  });
  vi.spyOn(window, 'removeEventListener').mockImplementation(function (this: any, type: string, listener: EventListenerOrEventListenerObject, options?: boolean | EventListenerOptions) {
    if (type === 'error') {
      errorListeners = errorListeners.filter((l) => l !== listener);
    } else if (type === 'unhandledrejection') {
      rejectionListeners = rejectionListeners.filter((l) => l !== listener);
    }
    return originalRemoveEventListener.call(this, type, listener, options);
  });
});

/**
 * Helper: dispatch a vendor crash ErrorEvent that our handler will catch.
 * We manually invoke the listeners instead of window.dispatchEvent to avoid
 * Vitest's uncaught exception tracking.
 */
function simulateVnptVendorCrash(
  message = "Cannot read properties of undefined (reading 'hash')",
  stack = '',
  filename = `${window.location.origin}/web-sdk-version-3.2.1.0.js`,
) {
  const event = new ErrorEvent('error', {
    message: message,
    filename,
    cancelable: true,
  });
  Object.defineProperty(event, 'error', {
    value: { message: message, stack: stack },
  });
  errorListeners.forEach((listener) => listener(event));
  return event;
}

function simulateVnptUnhandledRejection(reason: unknown) {
  const event = new Event('unhandledrejection', { cancelable: true });
  Object.defineProperty(event, 'reason', { value: reason });
  rejectionListeners.forEach((listener) => listener(event));
  return event;
}

function simulateUnrelatedError() {
  const event = new ErrorEvent('error', {
    message: "Cannot read properties of undefined (reading 'hashCode')",
    cancelable: true,
  });
  Object.defineProperty(event, 'error', {
    value: { message: "Cannot read properties of undefined (reading 'hashCode')" },
  });
  errorListeners.forEach((listener) => listener(event));
}

/**
 * Helper: dispatch a Chrome-style hash error that is NOT from VNPT vendor code.
 * After the isVnptVendorCrash fix, this must NOT be caught.
 */
function simulateNonVnptHashError() {
  const event = new ErrorEvent('error', {
    message: "Cannot read properties of undefined (reading 'hash')",
    cancelable: true,
  });
  Object.defineProperty(event, 'error', {
    value: {
      message: "Cannot read properties of undefined (reading 'hash')",
      stack: "app-router.js:42\n    at handleNavigation (app-router.js:38)",
    },
  });
  errorListeners.forEach((listener) => listener(event));
}

describe('VNPT identity SDK bridge', () => {
  const launch = vi.fn();

  beforeEach(() => {
    vi.stubEnv('VITE_VNPT_EKYC_ENABLED', 'true');
    vi.stubEnv('VITE_VNPT_EKYC_BACKEND_URL', 'https://api.idg.vnpt.vn/');
    vi.stubEnv('VITE_VNPT_EKYC_TOKEN_ID', 'browser-token-id');
    vi.stubEnv('VITE_VNPT_EKYC_TOKEN_KEY', 'browser-token-key');
    vi.stubEnv('VITE_VNPT_EKYC_ACCESS_TOKEN', 'Bearer browser-access-token');
    vi.stubEnv('VITE_VNPT_EKYC_CHALLENGE_CODE', 'VALID_CHALLENGE');
    window.SDK = { launch };
    launch.mockReset();
    localStorage.clear();
  });

  afterEach(() => {
    resetVnptIdentitySdkRuntime();
    window.fetch = originalFetch;
    vi.unstubAllEnvs();
    localStorage.clear();
  });

  it('waits for a terminal callback, strips binary/auth data and preserves identity/risk fields', async () => {
    const onResult = vi.fn();
    const onError = vi.fn();
    await launchVnptIdentitySdk(onResult, { onError });

    const config = launch.mock.calls[0][0] as SdkConfig;
    await callback(config, 'CALL_BACK', { status: 'OCR_IN_PROGRESS' });
    expect(onResult).not.toHaveBeenCalled();

    await callback(config, 'CALL_BACK_DOCUMENT_RESULT', {
      data: {
        idNumber: '012345678901',
        fullName: 'Nguyen Van A',
        dateOfBirth: '01/01/2000',
        fake_print_photo: true,
        fake_print_photo_prob: 0.95,
        frontImage: `data:image/jpeg;base64,${'a'.repeat(10_000)}`,
        ACCESS_TOKEN: 'must-not-leak',
        clientSecret: 'server-secret-must-not-leak',
        headers: { Authorization: 'Bearer must-not-leak' },
      },
    });
    await callback(config, 'CALL_BACK_END_FLOW', {
      ...successfulVendorFaceResult(),
      session_id: 'session-1',
      transactionId: 'transaction-1',
      tokenKey: 'must-not-leak-either',
    });

    await vi.waitFor(() => expect(onResult).toHaveBeenCalledTimes(1));
    const result = onResult.mock.calls[0][0];
    expect(result.providerSessionId).toBe('session-1');
    expect(result.providerTransactionId).toBe('transaction-1');
    expect(result.sdkResult.documentResult.data).toMatchObject({
      idNumber: '012345678901',
      fullName: 'Nguyen Van A',
      dateOfBirth: '01/01/2000',
      fake_print_photo: true,
      fake_print_photo_prob: 0.95,
    });
    const serialized = JSON.stringify(result.sdkResult);
    expect(serialized).not.toContain('data:image');
    expect(serialized).not.toContain('must-not-leak');
    expect(serialized).not.toContain('server-secret');
    expect(onError).not.toHaveBeenCalled();
  });

  it('allows a direct-sdk demo result without provider identifiers', async () => {
    const onResult = vi.fn();
    const onError = vi.fn();
    await launchVnptIdentitySdk(onResult, { onError });

    const config = launch.mock.calls[0][0] as SdkConfig;
    await callback(config, 'CALL_BACK_DOCUMENT_RESULT', { id: '012345678901', fullName: 'Nguyen Van A' });
    await callback(config, 'CALL_BACK_END_FLOW', successfulVendorFaceResult({ status: 'SUCCESS' }));

    await vi.waitFor(() => expect(onResult).toHaveBeenCalledTimes(1));
    expect(onResult.mock.calls[0][0].providerSessionId).toBeNull();
    expect(onResult.mock.calls[0][0].providerTransactionId).toBeNull();
    expect(onError).not.toHaveBeenCalled();
  });

  it('submits a terminal result once when SDK emits both callback variants', async () => {
    const onResult = vi.fn();
    await launchVnptIdentitySdk(onResult);

    const config = launch.mock.calls[0][0] as SdkConfig;
    await callback(config, 'CALL_BACK', { sessionId: 'session-1', requestId: 'transaction-1' });
    await new Promise((resolve) => setTimeout(resolve, 0));
    expect(onResult).not.toHaveBeenCalled();
    await callback(config, 'CALL_BACK_END_FLOW', successfulVendorFaceResult({
      sessionId: 'session-1',
      requestId: 'transaction-1',
    }));
    await vi.waitFor(() => expect(onResult).toHaveBeenCalledTimes(1));

    expect(window.__MANABIHUB_LAST_VNPT_CONFIG__).toMatchObject({
      BACKEND_URL: 'https://api.idg.vnpt.vn',
      TOKEN_ID_EXISTS: true,
      TOKEN_KEY_EXISTS: true,
      ACCESS_TOKEN_EXISTS: true,
      CHALLENGE_CODE_EXISTS: true,
    });
    expect(JSON.stringify(window.__MANABIHUB_LAST_VNPT_CONFIG__)).not.toContain('browser-access-token');
  });

  it('uses the documented CCCD chip configuration with a five-megabyte image ceiling', async () => {
    await launchVnptIdentitySdk(vi.fn());

    const config = launch.mock.calls[0][0] as SdkConfig;
    expect(config).toMatchObject({
      MAX_SIZE_IMAGE: 5,
      LIST_TYPE_DOCUMENT: [9],
      DOCUMENT_TYPE_START: 9,
      SDK_FLOW: 'DOCUMENT_TO_FACE',
    });
  });

  it('removes legacy persisted KYC payloads when runtime is reset', () => {
    localStorage.setItem('vnpt_ekyc_last_document_result', 'legacy-document');
    localStorage.setItem('vnpt_ekyc_last_result', 'legacy-result');
    resetVnptIdentitySdkRuntime();
    expect(localStorage.getItem('vnpt_ekyc_last_document_result')).toBeNull();
    expect(localStorage.getItem('vnpt_ekyc_last_result')).toBeNull();
  });

  it('fails fast if required config is missing', async () => {
    vi.stubEnv('VITE_VNPT_EKYC_TOKEN_ID', '');
    const onResult = vi.fn();
    await expect(launchVnptIdentitySdk(onResult)).rejects.toThrow(/Thiếu cấu hình VNPT eKYC/);
    expect(launch).not.toHaveBeenCalled();
  });

  it('fails fast if access token is an expired JWT (standard base64)', async () => {
    // Generate a dummy expired JWT with standard base64
    const header = btoa(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
    const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) - 360 }));
    const signature = 'dummy-signature';
    vi.stubEnv('VITE_VNPT_EKYC_ACCESS_TOKEN', `Bearer ${header}.${payload}.${signature}`);

    const onResult = vi.fn();
    await expect(launchVnptIdentitySdk(onResult)).rejects.toThrow(/Phiên xác thực VNPT đã hết hạn/);
    expect(launch).not.toHaveBeenCalled();
  });

  // ──────────────────────────────────────────────────────────────────
  // New required tests
  // ──────────────────────────────────────────────────────────────────

  it('fails fast if access token is an expired JWT encoded with base64url', async () => {
    const headerObj = { alg: 'HS256', typ: 'JWT' };
    const payloadObj = {
      exp: Math.floor(Date.now() / 1000) - 360,
      sub: 'test-user',
      nonce: 'CCCD Việt Nam \uffff',
    };
    const payloadB64Url = toBase64Url(payloadObj);
    expect(payloadB64Url).toMatch(/[-_]/);
    const token = `${toBase64Url(headerObj)}.${payloadB64Url}.dummysig`;
    vi.stubEnv('VITE_VNPT_EKYC_ACCESS_TOKEN', token);

    await expect(launchVnptIdentitySdk(vi.fn())).rejects.toThrow(/Phiên xác thực VNPT đã hết hạn/);
    expect(launch).not.toHaveBeenCalled();
  });

  it('accepts a JWT with more than five minutes remaining', async () => {
    const headerObj = { alg: 'HS256', typ: 'JWT' };
    const payloadObj = { exp: Math.floor(Date.now() / 1000) + 360, sub: 'test-user' };
    const token = `${toBase64Url(headerObj)}.${toBase64Url(payloadObj)}.dummysig`;
    vi.stubEnv('VITE_VNPT_EKYC_ACCESS_TOKEN', token);

    const onResult = vi.fn();
    await launchVnptIdentitySdk(onResult);
    expect(launch).toHaveBeenCalledTimes(1);
  });

  it('does not reject opaque (non-JWT) access tokens', async () => {
    vi.stubEnv('VITE_VNPT_EKYC_ACCESS_TOKEN', 'opaque-server-issued-token-no-dots');

    const onResult = vi.fn();
    await launchVnptIdentitySdk(onResult);
    expect(launch).toHaveBeenCalledTimes(1);
  });

  it('uses the bundled SDK default when VNPT has not issued a challenge code', async () => {
    vi.stubEnv('VITE_VNPT_EKYC_CHALLENGE_CODE', '');

    await launchVnptIdentitySdk(vi.fn());

    const config = launch.mock.calls[0][0] as SdkConfig;
    expect(launch).toHaveBeenCalledTimes(1);
    expect(config).not.toHaveProperty('CHALLENGE_CODE');
    expect(window.__MANABIHUB_LAST_VNPT_CONFIG__).toMatchObject({
      CHALLENGE_CODE_EXISTS: false,
    });
  });

  it('rejects a lookalike VNPT backend and remote SDK scripts', async () => {
    vi.stubEnv('VITE_VNPT_EKYC_BACKEND_URL', 'https://api.idg.vnpt.vn.evil.example');
    await expect(launchVnptIdentitySdk(vi.fn())).rejects.toThrow(/origin đã phê duyệt/);

    vi.stubEnv('VITE_VNPT_EKYC_BACKEND_URL', 'https://api.idg.vnpt.vn');
    vi.stubEnv('VITE_VNPT_EKYC_SDK_SCRIPT_URLS', 'https://cdn.example.test/sdk.js');
    await expect(launchVnptIdentitySdk(vi.fn())).rejects.toThrow(/same-origin/);
    expect(launch).not.toHaveBeenCalled();
  });

  it('rejects an approved VNPT origin with an unexpected path', async () => {
    vi.stubEnv('VITE_VNPT_EKYC_BACKEND_URL', 'https://api.idg.vnpt.vn/unexpected-base');

    await expect(launchVnptIdentitySdk(vi.fn())).rejects.toThrow(/origin/);
    expect(launch).not.toHaveBeenCalled();
  });

  it('does not catch an unrelated app error containing "hash" in its message', async () => {
    const onResult = vi.fn();
    const onError = vi.fn();
    await launchVnptIdentitySdk(onResult, { onError });

    // Simulate an unrelated error with "hash" in the message
    simulateUnrelatedError();

    // VNPT handler should NOT have fired
    expect(onError).not.toHaveBeenCalled();
    // SDK should still be present (not reset)
    expect(window.SDK).toBeDefined();
  });

  it('does not catch a Chrome hash error that lacks VNPT stack origin', async () => {
    const onResult = vi.fn();
    const onError = vi.fn();
    await launchVnptIdentitySdk(onResult, { onError });

    // This error has the EXACT Chrome message but its stack is from app code, not VNPT
    simulateNonVnptHashError();

    expect(onError).not.toHaveBeenCalled();
    expect(window.SDK).toBeDefined();
  });

  it('handles a Safari hash dereference from the configured VNPT SDK', async () => {
    const onError = vi.fn();
    await launchVnptIdentitySdk(vi.fn(), { onError });

    simulateVnptVendorCrash("undefined is not an object (evaluating 't.object.hash')");

    expect(onError).toHaveBeenCalledTimes(1);
  });

  it('two consecutive launches call their own onError callbacks separately', async () => {
    const onError1 = vi.fn();
    const onError2 = vi.fn();

    // Launch 1
    await launchVnptIdentitySdk(vi.fn(), { onError: onError1 });

    // Launch 2 — should clean up launch 1's listener
    await launchVnptIdentitySdk(vi.fn(), { onError: onError2 });

    // Simulate VNPT crash
    simulateVnptVendorCrash();

    // Only the second launch's callback should fire
    expect(onError1).not.toHaveBeenCalled();
    expect(onError2).toHaveBeenCalledTimes(1);
  });

  it('handles vendor hash crash during active launch exactly once', async () => {
    const onError = vi.fn();
    await launchVnptIdentitySdk(vi.fn(), { onError });

    // First crash
    simulateVnptVendorCrash();
    // Second crash (should be ignored — already fired once)
    simulateVnptVendorCrash();

    expect(onError).toHaveBeenCalledTimes(1);
    expect(onError.mock.calls[0][0].message).toMatch(/không thể xử lý phiên xác minh/);
  });

  it('happy path END_FLOW submits result exactly once and cleans up listener', async () => {
    const onResult = vi.fn();
    const onError = vi.fn();
    await launchVnptIdentitySdk(onResult, { onError });

    const config = launch.mock.calls[0][0] as SdkConfig;

    // Terminal event
    await callback(config, 'CALL_BACK_END_FLOW', successfulVendorFaceResult({
      status: 'SUCCESS',
      transactionId: 'tx-1',
    }));

    await vi.waitFor(() => expect(onResult).toHaveBeenCalledTimes(1));

    // A second END_FLOW should be ignored
    await callback(config, 'CALL_BACK_END_FLOW', successfulVendorFaceResult({
      status: 'SUCCESS',
      transactionId: 'tx-1',
    }));
    expect(onResult).toHaveBeenCalledTimes(1);

    // Listener should have been cleaned up — vendor crash should not trigger onError
    simulateVnptVendorCrash();
    expect(onError).not.toHaveBeenCalled();
  });

  it('cleans up legacy localStorage PII at bootstrap', () => {
    localStorage.setItem('vnpt_ekyc_last_document_result', '{"cccd":"PII_DATA"}');
    localStorage.setItem('vnpt_ekyc_last_result', '{"photo":"base64..."}');

    // Import-time cleanup is called in main.tsx; we test the exported function directly.
    resetVnptIdentitySdkRuntime();

    expect(localStorage.getItem('vnpt_ekyc_last_document_result')).toBeNull();
    expect(localStorage.getItem('vnpt_ekyc_last_result')).toBeNull();
  });

  it('passes CHALLENGE_CODE correctly in dataConfig', async () => {
    vi.stubEnv('VITE_VNPT_EKYC_CHALLENGE_CODE', 'MY_REAL_CHALLENGE');
    const onResult = vi.fn();
    await launchVnptIdentitySdk(onResult);

    const config = launch.mock.calls[0][0] as SdkConfig;
    expect(config.CHALLENGE_CODE).toBe('MY_REAL_CHALLENGE');
  });

  it('encodes CHALLENGE_CODE because the vendor concatenates it into a query string', async () => {
    vi.stubEnv('VITE_VNPT_EKYC_CHALLENGE_CODE', 'challenge +/?&');
    await launchVnptIdentitySdk(vi.fn());

    const config = launch.mock.calls[0][0] as SdkConfig;
    expect(config.CHALLENGE_CODE).toBe('challenge%20%2B%2F%3F%26');
  });

  it('attributes an error by the exact custom same-origin SDK filename without a stack', async () => {
    vi.stubEnv('VITE_VNPT_EKYC_SDK_SCRIPT_URLS', '/assets/sdk-4f19.js');
    const onError = vi.fn();
    await launchVnptIdentitySdk(vi.fn(), { onError });

    const event = simulateVnptVendorCrash(
      "Cannot read properties of undefined (reading 'hash')",
      '',
      `${window.location.origin}/assets/sdk-4f19.js?release=1`,
    );

    expect(event.defaultPrevented).toBe(true);
    expect(onError).toHaveBeenCalledTimes(1);
  });

  it('does not swallow an unrelated undefined error from the vendor script', async () => {
    const onError = vi.fn();
    await launchVnptIdentitySdk(vi.fn(), { onError });

    const event = simulateVnptVendorCrash(
      "Cannot read properties of undefined (reading 'camera')",
    );

    expect(event.defaultPrevented).toBe(false);
    expect(onError).not.toHaveBeenCalled();
  });

  it('handles an attributed unhandled rejection and ignores source-less reasons', async () => {
    const onError = vi.fn();
    await launchVnptIdentitySdk(vi.fn(), { onError });

    const sourceLess = simulateVnptUnhandledRejection(
      "Cannot read properties of undefined (reading 'hash')",
    );
    expect(sourceLess.defaultPrevented).toBe(false);
    expect(onError).not.toHaveBeenCalled();

    const attributed = simulateVnptUnhandledRejection({
      message: "Cannot read properties of undefined (reading 'hash')",
      stack: `at upload (${window.location.origin}/web-sdk-version-3.2.1.0.js:1:1)`,
    });
    expect(attributed.defaultPrevented).toBe(true);
    expect(onError).toHaveBeenCalledTimes(1);
  });

  it('cleans listeners when SDK.launch throws synchronously', async () => {
    window.SDK = { launch: vi.fn(() => { throw new Error('React render failed'); }) };

    await expect(launchVnptIdentitySdk(vi.fn())).rejects.toThrow(/không thể xử lý phiên xác minh/);
    expect(errorListeners).toHaveLength(0);
    expect(rejectionListeners).toHaveLength(0);
    expect(window.__MANABIHUB_VNPT_ACTIVE_LAUNCH__).toBeUndefined();
  });

  it('makes every callback from an older launch inert after relaunch', async () => {
    const firstResult = vi.fn();
    await launchVnptIdentitySdk(firstResult);
    const firstConfig = launch.mock.calls[0][0] as SdkConfig;

    const secondResult = vi.fn();
    await launchVnptIdentitySdk(secondResult);
    const secondConfig = launch.mock.calls[1][0] as SdkConfig;

    await callback(firstConfig, 'CALL_BACK_DOCUMENT_RESULT', { idNumber: 'old-id' });
    await callback(firstConfig, 'CALL_BACK', { sessionId: 'old-session' });
    await callback(firstConfig, 'CALL_BACK_END_FLOW', { transactionId: 'old-transaction' });
    expect(firstResult).not.toHaveBeenCalled();

    await callback(secondConfig, 'CALL_BACK_END_FLOW', {
      ...successfulVendorFaceResult(),
      sessionId: 'new-session',
      transactionId: 'new-transaction',
    });
    await vi.waitFor(() => expect(secondResult).toHaveBeenCalledTimes(1));
    expect(JSON.stringify(secondResult.mock.calls[0][0])).not.toContain('old-');
  });

  it('fails closed when callback stages disagree on provider identifiers', async () => {
    const onResult = vi.fn();
    const onError = vi.fn();
    await launchVnptIdentitySdk(onResult, { onError });
    const config = launch.mock.calls[0][0] as SdkConfig;

    await callback(config, 'CALL_BACK', { sessionId: 'session-old' });
    await callback(config, 'CALL_BACK_END_FLOW', successfulVendorFaceResult({
      sessionId: 'session-terminal',
    }));

    await vi.waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
    expect(onResult).not.toHaveBeenCalled();
    expect(onError.mock.calls[0][0].message).toMatch(/không nhất quán/);
  });

  it('uses the terminal transaction ID instead of an OCR request ID', async () => {
    const onResult = vi.fn();
    await launchVnptIdentitySdk(onResult);
    const config = launch.mock.calls[0][0] as SdkConfig;

    await callback(config, 'CALL_BACK_DOCUMENT_RESULT', { requestId: 'ocr-upload-request' });
    await callback(config, 'CALL_BACK_END_FLOW', successfulVendorFaceResult({
      transactionId: 'terminal-transaction',
    }));

    await vi.waitFor(() => expect(onResult).toHaveBeenCalledTimes(1));
    expect(onResult.mock.calls[0][0].providerTransactionId).toBe('terminal-transaction');
  });

  it('fails closed when requestId fallbacks disagree across callback stages', async () => {
    const onResult = vi.fn();
    const onError = vi.fn();
    await launchVnptIdentitySdk(onResult, { onError });
    const config = launch.mock.calls[0][0] as SdkConfig;

    await callback(config, 'CALL_BACK_DOCUMENT_RESULT', { requestId: 'ocr-request' });
    await callback(config, 'CALL_BACK_END_FLOW', successfulVendorFaceResult({
      requestId: 'terminal-request',
    }));

    await vi.waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
    expect(onResult).not.toHaveBeenCalled();
  });

  it('leaves an intermediate VNPT 401 for the vendor SDK without ending the host launch', async () => {
    const response = new Response('provider unauthorized', { status: 401 });
    const originalFetch = vi.fn().mockResolvedValue(response);
    window.fetch = originalFetch as typeof window.fetch;
    const onResult = vi.fn();
    const onError = vi.fn();
    await launchVnptIdentitySdk(onResult, { onError });
    const config = launch.mock.calls[0][0] as SdkConfig;

    const vendorResponse = await window.fetch('https://api.idg.vnpt.vn/file-service/v1/addFile');

    expect(await vendorResponse.text()).toBe('provider unauthorized');
    expect(onError).not.toHaveBeenCalled();
    expect(window.__MANABIHUB_VNPT_ACTIVE_LAUNCH__).toBeDefined();
    expect(window.fetch).toBe(originalFetch);

    await callback(config, 'CALL_BACK_END_FLOW', successfulVendorFaceResult());
    await vi.waitFor(() => expect(onResult).toHaveBeenCalledTimes(1));
    expect(onError).not.toHaveBeenCalled();
  });

  it('does not intercept authentication failures from another backend', async () => {
    const response = new Response('app unauthorized', { status: 401 });
    const originalFetch = vi.fn().mockResolvedValue(response);
    window.fetch = originalFetch as typeof window.fetch;
    const onError = vi.fn();
    await launchVnptIdentitySdk(vi.fn(), { onError });

    const appResponse = await window.fetch('https://app.example.test/api/private');

    expect(appResponse.status).toBe(401);
    expect(onError).not.toHaveBeenCalled();
  });

  it('leaves an intermediate VNPT network failure for the vendor SDK to handle', async () => {
    const networkError = new TypeError('Failed to fetch');
    window.fetch = vi.fn().mockRejectedValue(networkError) as typeof window.fetch;
    const onError = vi.fn();
    await launchVnptIdentitySdk(vi.fn(), { onError });

    await expect(window.fetch('https://api.idg.vnpt.vn/file-service/v1/addFile')).rejects.toBe(networkError);
    expect(onError).not.toHaveBeenCalled();
    expect(window.__MANABIHUB_VNPT_ACTIVE_LAUNCH__).toBeDefined();
  });

  it('deduplicates concurrent script loads and removes a failed script so it can be retried', async () => {
    delete window.SDK;
    vi.stubEnv('VITE_VNPT_EKYC_SDK_SCRIPT_URLS', '/retryable-vnpt-sdk.js');

    const firstLaunch = launchVnptIdentitySdk(vi.fn());
    const secondLaunch = launchVnptIdentitySdk(vi.fn());
    const firstFailure = expect(firstLaunch).rejects.toThrow(/retryable-vnpt-sdk\.js/);
    const secondFailure = expect(secondLaunch).rejects.toThrow(/retryable-vnpt-sdk\.js/);
    const firstScript = await vi.waitFor(() => {
      const scripts = document.querySelectorAll<HTMLScriptElement>(
        'script[data-vnpt-sdk="/retryable-vnpt-sdk.js"]',
      );
      expect(scripts).toHaveLength(1);
      return scripts[0];
    });

    firstScript.dispatchEvent(new Event('error'));
    await Promise.all([firstFailure, secondFailure]);
    expect(firstScript.isConnected).toBe(false);

    const retryLaunch = launchVnptIdentitySdk(vi.fn());
    const retryFailure = expect(retryLaunch).rejects.toThrow(/retryable-vnpt-sdk\.js/);
    const retryScript = await vi.waitFor(() => {
      const candidate = document.querySelector<HTMLScriptElement>(
        'script[data-vnpt-sdk="/retryable-vnpt-sdk.js"]',
      );
      expect(candidate).not.toBeNull();
      return candidate as HTMLScriptElement;
    });

    expect(retryScript).not.toBe(firstScript);
    retryScript.dispatchEvent(new Event('error'));
    await retryFailure;
    expect(retryScript.isConnected).toBe(false);
  });

  it('cancels a pending launch while preserving the successfully loaded runtime for relaunch', async () => {
    delete window.SDK;
    vi.stubEnv('VITE_VNPT_EKYC_SDK_SCRIPT_URLS', '/slow-vnpt-sdk.js');

    const launchPromise = launchVnptIdentitySdk(vi.fn());
    const script = await vi.waitFor(() => {
      const candidate = document.querySelector<HTMLScriptElement>('script[data-vnpt-sdk="/slow-vnpt-sdk.js"]');
      expect(candidate).not.toBeNull();
      return candidate as HTMLScriptElement;
    });

    resetVnptIdentitySdkRuntime();
    window.SDK = { launch };
    script.dispatchEvent(new Event('load'));
    await launchPromise;

    expect(launch).not.toHaveBeenCalled();
    expect(script.dataset.loaded).toBe('true');
    expect(script.isConnected).toBe(true);

    resetVnptIdentitySdkRuntime();
    expect(window.SDK?.launch).toBe(launch);
    expect(script.isConnected).toBe(true);

    await launchVnptIdentitySdk(vi.fn());
    expect(launch).toHaveBeenCalledTimes(1);
    expect(document.querySelectorAll('script[data-vnpt-sdk="/slow-vnpt-sdk.js"]')).toHaveLength(1);

    script.remove();
  });

  it('keeps the launch active when END_FLOW arrives before face liveness and compare complete', async () => {
    const onResult = vi.fn();
    const onError = vi.fn();
    await launchVnptIdentitySdk(onResult, { onError });
    const config = launch.mock.calls[0][0] as SdkConfig;

    await callback(config, 'CALL_BACK_DOCUMENT_RESULT', {
      ocr: { object: { id: '012345678901', name: 'NGUYEN VAN A', birth_day: '01/01/2000' } },
    });
    const prematureVendorEndFlow = {
      client_session: 'WEB-SDK_mobile_session',
      liveness_face: { object: { liveness: '' } },
      compare: { object: { msg: '', prob: null } },
      masked: { object: { masked: '' } },
    };

    await callback(config, 'CALL_BACK_END_FLOW', prematureVendorEndFlow);
    await callback(config, 'CALL_BACK_END_FLOW', prematureVendorEndFlow);
    await new Promise((resolve) => setTimeout(resolve, 0));

    expect(onResult).not.toHaveBeenCalled();
    expect(onError).not.toHaveBeenCalled();
    expect(window.__MANABIHUB_VNPT_ACTIVE_LAUNCH__).toBeDefined();

    await callback(config, 'CALL_BACK_END_FLOW', successfulVendorFaceResult({
      client_session: 'WEB-SDK_mobile_session',
    }));
    await vi.waitFor(() => expect(onResult).toHaveBeenCalledTimes(1));
    expect(window.__MANABIHUB_VNPT_ACTIVE_LAUNCH__).toBeUndefined();
  });

  it('reports an explicit terminal cancellation once without submitting it to the backend callback', async () => {
    const onResult = vi.fn();
    const onError = vi.fn();
    await launchVnptIdentitySdk(onResult, { onError });
    const config = launch.mock.calls[0][0] as SdkConfig;

    await callback(config, 'CALL_BACK_END_FLOW', { status: 'USER_CANCELLED' });
    await callback(config, 'CALL_BACK_END_FLOW', { status: 'USER_CANCELLED' });

    await vi.waitFor(() => expect(onError).toHaveBeenCalledTimes(1));
    expect(onResult).not.toHaveBeenCalled();
    expect(window.__MANABIHUB_VNPT_ACTIVE_LAUNCH__).toBeUndefined();
  });

  it('dismisses the known VNPT face tutorial without relying on Ant modal classes', async () => {
    const unrelatedClick = vi.fn();
    const guideClick = vi.fn();
    await launchVnptIdentitySdk(vi.fn());

    const unrelatedVideo = document.createElement('video');
    const unrelatedButton = document.createElement('button');
    unrelatedButton.addEventListener('click', unrelatedClick);
    document.body.append(unrelatedVideo, unrelatedButton);

    const vendorDialog = document.createElement('div');
    const tutorialVideo = document.createElement('video');
    const tutorialSource = document.createElement('source');
    tutorialSource.type = 'video/mp4';
    tutorialSource.src = '/lib/vietnamese-tutorial.mp4';
    tutorialVideo.append(tutorialSource);
    const guideAction = document.createElement('div');
    guideAction.className = 'vnpt-cursor-pointer vnpt-bg-primary';
    guideAction.addEventListener('click', () => {
      guideClick();
      vendorDialog.remove();
    });
    vendorDialog.append(tutorialVideo, guideAction);
    document.body.append(vendorDialog);

    await vi.waitFor(() => expect(guideClick).toHaveBeenCalledTimes(1));
    expect(unrelatedClick).not.toHaveBeenCalled();
    expect(window.__MANABIHUB_VNPT_DIAGNOSTICS__).toMatchObject({
      release: 'face-guide-v2',
      faceGuideClickAttempts: 1,
    });
    unrelatedVideo.remove();
    unrelatedButton.remove();
  });

  it('retries the known tutorial action until the vendor modal actually closes', async () => {
    const guideClick = vi.fn();
    await launchVnptIdentitySdk(vi.fn());

    const vendorWrapper = document.createElement('section');
    const tutorialVideo = document.createElement('video');
    const tutorialSource = document.createElement('source');
    tutorialSource.src = '/lib/vietnamese-tutorial.mp4';
    tutorialVideo.append(tutorialSource);
    const guideAction = document.createElement('div');
    guideAction.className = 'vnpt-cursor-pointer vnpt-bg-primary';
    guideAction.addEventListener('click', () => {
      guideClick();
      if (guideClick.mock.calls.length === 2) vendorWrapper.remove();
    });
    vendorWrapper.append(tutorialVideo, guideAction);
    document.body.append(vendorWrapper);

    await vi.waitFor(() => expect(guideClick).toHaveBeenCalledTimes(2));
    expect(window.__MANABIHUB_VNPT_DIAGNOSTICS__).toMatchObject({
      faceGuideClickAttempts: 2,
    });
  });
});

function toBase64Url(value: unknown) {
  const bytes = new TextEncoder().encode(JSON.stringify(value));
  let binary = '';
  bytes.forEach((byte) => { binary += String.fromCharCode(byte); });
  return btoa(binary)
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
}

async function callback(config: SdkConfig, name: string, result: unknown) {
  const handler = config[name] as ((value: unknown) => Promise<void> | void);
  await handler(result);
}

function successfulVendorFaceResult(extra: Record<string, unknown> = {}) {
  return {
    liveness_face: { object: { liveness: 'success' } },
    compare: { object: { msg: 'MATCH', prob: 98 } },
    masked: { object: { masked: 'no' } },
    ...extra,
  };
}
