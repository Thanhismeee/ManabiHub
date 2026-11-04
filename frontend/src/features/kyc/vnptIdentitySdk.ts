type VnptSdkCallback = (result: unknown) => Promise<void> | void;

const DEFAULT_VNPT_SDK_SCRIPT_URLS = [
  '/web-sdk-version-3.2.1.0.js',
  '/lib/VNPTQRBrowserApp.js',
  '/lib/VNPTBrowserSDKAppV4.1.0.js',
];
const APPROVED_VNPT_BACKEND_ORIGINS = new Set([
  'https://api.idg.vnpt.vn',
  'https://sandbox-idg.vnpt.vn',
]);

const PROVIDER_SESSION_ID_ALIASES = ['providersessionid', 'sessionid', 'clientsession'];
const PROVIDER_TRANSACTION_ID_ALIASES = [
  'providertransactionid',
  'transactionid',
  'txid',
];
const PROVIDER_TRANSACTION_ID_FALLBACK_ALIASES = ['requestid'];
const MAX_SDK_RESULT_DEPTH = 8;
const MAX_SDK_ARRAY_ITEMS = 50;
const MAX_SDK_STRING_LENGTH = 4_096;
const OMITTED = Symbol('omitted');
const VNPT_PROVIDER_ERROR_MESSAGE =
  'VNPT eKYC không thể xử lý phiên xác minh. Vui lòng kiểm tra kết nối, cấu hình VNPT và thử lại.';
const VNPT_TERMINAL_FAILURE_MESSAGE =
  'Phiên xác minh VNPT đã bị hủy, hết thời gian hoặc gặp lỗi. Vui lòng thực hiện lại từ đầu.';

/** Match only the response.object.hash dereference seen in VNPT 3.2.1. */
function isVnptHashDereference(message: string) {
  const normalized = message.toLowerCase();
  return /cannot read propert(?:y|ies) of (?:undefined|null).*['"]hash['"]/.test(normalized)
    || /(?:undefined|null) is not an object.*\.object\.hash/.test(normalized)
    || /\.object is (?:undefined|null)/.test(normalized)
    || /can't access property ['"]hash['"].*\.object is (?:undefined|null)/.test(normalized);
}

declare global {
  interface Window {
    SDK?: {
      launch: (config: Record<string, unknown>) => void;
    };
    __MANABIHUB_LAST_VNPT_CONFIG__?: Record<string, unknown>;
    __MANABIHUB_VNPT_ACTIVE_LAUNCH__?: {
      cleanup: () => void;
    };
    __MANABIHUB_VNPT_DIAGNOSTICS__?: {
      release: 'face-guide-v2';
      launchedAt: number;
      faceGuideDetectedAt?: number;
      faceGuideClickAttempts: number;
      faceGuideClosedAt?: number;
      documentCallbacks: number;
      intermediateCallbacks: number;
      endFlowCallbacks: number;
      lastEndFlowClassification?: EndFlowClassification;
    };
  }
}

let vnptLaunchGeneration = 0;
let vnptScriptSequence: { fingerprint: string; promise: Promise<void> } | null = null;

export interface VnptIdentityResult {
  providerSessionId?: string | null;
  providerTransactionId?: string | null;
  sdkResult: Record<string, unknown>;
}

export interface VnptIdentitySdkOptions {
  /** Receives terminal SDK/callback errors because VNPT does not await async callbacks. */
  onError?: (error: Error) => void;
}

const LEGACY_VNPT_STORAGE_KEYS = ['vnpt_ekyc_last_document_result', 'vnpt_ekyc_last_result'];

/** Remove raw CCCD/provider payloads written by pre-release SDK bridges. */
export function cleanupLegacyVnptIdentityStorage() {
  try {
    LEGACY_VNPT_STORAGE_KEYS.forEach((key) => window.localStorage.removeItem(key));
  } catch {
    // Storage can be unavailable in private browsing; never block KYC startup.
  }
}

export function resetVnptIdentitySdkRuntime() {
  vnptLaunchGeneration += 1;
  if (window.__MANABIHUB_VNPT_ACTIVE_LAUNCH__) {
    window.__MANABIHUB_VNPT_ACTIVE_LAUNCH__.cleanup();
    delete window.__MANABIHUB_VNPT_ACTIVE_LAUNCH__;
  }
  delete window.__MANABIHUB_LAST_VNPT_CONFIG__;
  // Keep the vendor runtime loaded for the lifetime of this browser document.
  // Removing its script tags and deleting window.SDK does not undo TensorFlow's
  // global WebGL registrations; reinjecting the face bundle only registers the
  // same kernels again. A full page reload remains the hard-reset boundary.
  cleanupLegacyVnptIdentityStorage();
}

/**
 * Local-only early warning for JWT tokens that appear to have expired based on
 * the `exp` claim. This does NOT authenticate the token — server-side
 * validation is the only source of truth. Opaque tokens (non-JWT) are treated
 * as unverifiable and pass through without error.
 *
 * Supports standard base64 and base64url-encoded payloads.
 */
function isJwtExpired(token: string): boolean {
  try {
    const parts = token.split('.');
    if (parts.length !== 3) return false; // Opaque token — cannot check expiry locally
    const base64Url = parts[1];
    // Convert base64url to standard base64
    const base64 = base64Url
      .replace(/-/g, '+')
      .replace(/_/g, '/')
      .padEnd(base64Url.length + ((4 - (base64Url.length % 4)) % 4), '=');
    const decoded = atob(base64);
    const bytes = Uint8Array.from(decoded, (character) => character.charCodeAt(0));
    const payload = JSON.parse(new TextDecoder().decode(bytes)) as { exp?: unknown };
    // Avoid starting a camera flow with a token that expires during the next five minutes.
    const MINIMUM_REMAINING_LIFETIME_MS = 5 * 60 * 1000;
    return typeof payload.exp === 'number'
      && Number.isFinite(payload.exp)
      && payload.exp * 1000 <= Date.now() + MINIMUM_REMAINING_LIFETIME_MS;
  } catch {
    return false; // Cannot parse — treat as opaque / unverifiable
  }
}

export async function launchVnptIdentitySdk(
  onResult: (result: VnptIdentityResult) => Promise<void> | void,
  options: VnptIdentitySdkOptions = {},
) {
  const launchGeneration = ++vnptLaunchGeneration;
  window.__MANABIHUB_VNPT_DIAGNOSTICS__ = {
    release: 'face-guide-v2',
    launchedAt: Date.now(),
    faceGuideClickAttempts: 0,
    documentCallbacks: 0,
    intermediateCallbacks: 0,
    endFlowCallbacks: 0,
  };
  cleanupLegacyVnptIdentityStorage();
  if (window.__MANABIHUB_VNPT_ACTIVE_LAUNCH__) {
    window.__MANABIHUB_VNPT_ACTIVE_LAUNCH__.cleanup();
  }
  const env = getVnptEnv();

  if (!env.enabled) {
    throw new Error('VNPT eKYC SDK đang tắt. Hãy đặt VITE_VNPT_EKYC_ENABLED=true trong môi trường chạy.');
  }

  // Validate all required config BEFORE loading vendor scripts into the page
  const missingConfig = [
    { label: 'VITE_VNPT_EKYC_BACKEND_URL', value: env.backendUrl },
    { label: 'VITE_VNPT_EKYC_TOKEN_ID', value: env.tokenId },
    { label: 'VITE_VNPT_EKYC_TOKEN_KEY', value: env.tokenKey },
    { label: 'VITE_VNPT_EKYC_ACCESS_TOKEN', value: env.accessToken },
  ].filter((item) => !item.value);

  if (missingConfig.length > 0) {
    throw new Error(`Thiếu cấu hình VNPT eKYC: ${missingConfig.map((item) => item.label).join(', ')}`);
  }

  if (isJwtExpired(env.accessToken)) {
    throw new Error('Phiên xác thực VNPT đã hết hạn hoặc cấu hình chưa hợp lệ. Vui lòng cập nhật thông tin xác thực và thử lại.');
  }

  await loadVnptScripts(env.scriptUrls);

  if (launchGeneration !== vnptLaunchGeneration) {
    return;
  }

  if (!window.SDK?.launch) {
    throw new Error('Không tải được VNPT eKYC SDK. Kiểm tra frontend/public/web-sdk-version-3.2.1.0.js và frontend/public/lib/.');
  }

  // --- Per-launch state: fresh for every launch, cleaned up on terminal event ---
  let documentResult: Record<string, unknown> | null = null;
  let callbackResult: Record<string, unknown> | null = null;
  let finalResultHandled = false;
  let errorFired = false;
  let isCleanedUp = false;
  let restorePopupBypass: (() => void) | null = null;
  const configuredScriptSources = resolveConfiguredScriptSources(env.scriptUrls);

  const onWindowError = (event: ErrorEvent) => {
    if (isCleanedUp) return;
    const message = event.error?.message || event.message || '';
    const stack = readStringProperty(event.error, 'stack');
    const source = event.filename
      || readStringProperty(event.error, 'sourceURL')
      || readStringProperty(event.error, 'fileName');
    if (isVnptHashDereference(message)
        && isConfiguredVnptSource(source, stack, configuredScriptSources)) {
      event.preventDefault();
      failLaunch(new Error(VNPT_PROVIDER_ERROR_MESSAGE));
    }
  };

  const onUnhandledRejection = (event: PromiseRejectionEvent) => {
    if (isCleanedUp) return;
    const reason = event.reason;
    const message = reason instanceof Error ? reason.message : readStringProperty(reason, 'message');
    const stack = readStringProperty(reason, 'stack');
    const source = readStringProperty(reason, 'sourceURL') || readStringProperty(reason, 'fileName');
    if (isVnptHashDereference(message)
        && isConfiguredVnptSource(source, stack, configuredScriptSources)) {
      event.preventDefault();
      failLaunch(new Error(VNPT_PROVIDER_ERROR_MESSAGE));
    }
  };

  const cleanupLaunch = () => {
    if (isCleanedUp) return;
    isCleanedUp = true;
    documentResult = null;
    callbackResult = null;
    window.removeEventListener('error', onWindowError);
    window.removeEventListener('unhandledrejection', onUnhandledRejection);
    restorePopupBypass?.();
    restorePopupBypass = null;
    if (window.__MANABIHUB_VNPT_ACTIVE_LAUNCH__?.cleanup === cleanupLaunch) {
      delete window.__MANABIHUB_VNPT_ACTIVE_LAUNCH__;
    }
  };

  const failLaunch = (error: Error) => {
    if (errorFired) return;
    errorFired = true;
    cleanupLaunch();
    options.onError?.(error);
  };

  window.addEventListener('error', onWindowError);
  window.addEventListener('unhandledrejection', onUnhandledRejection);
  // Let VNPT's own SDK consume individual HTTP responses. Treating any
  // intermediate 401/403 or transport failure as a terminal host error tears
  // down the camera while VNPT is still advancing from document capture to
  // face liveness. Startup validation and explicit terminal callbacks remain
  // the fail-closed boundaries for the host application.
  restorePopupBypass = installVnptFaceGuideAutoDismiss();
  window.__MANABIHUB_VNPT_ACTIVE_LAUNCH__ = { cleanup: cleanupLaunch };

  const reportCallbackError = (error: unknown) => {
    failLaunch(toError(error));
  };

  const submitTerminalResult = async (terminalResult: Record<string, unknown>) => {
    if (finalResultHandled) {
      return;
    }

    // Completion must be proven by the terminal callback itself. Intermediate
    // OCR callbacks can contain unrelated `compare` fields and must never make
    // an otherwise empty END_FLOW look complete.
    const terminalClassification = classifyEndFlow({ endFlowResult: terminalResult });
    if (window.__MANABIHUB_VNPT_DIAGNOSTICS__) {
      window.__MANABIHUB_VNPT_DIAGNOSTICS__.endFlowCallbacks += 1;
      window.__MANABIHUB_VNPT_DIAGNOSTICS__.lastEndFlowClassification = terminalClassification;
    }
    if (terminalClassification === 'incomplete') {
      // VNPT 3.2.1 can show its result screen (and emit END_FLOW) when the
      // face processor times out behind the tutorial modal. Empty face and
      // compare envelopes are not a completed verification attempt.
      return;
    }
    if (terminalClassification === 'terminal-failure') {
      failLaunch(new Error(VNPT_TERMINAL_FAILURE_MESSAGE));
      return;
    }

    const compactResult = mergeSdkResults(documentResult, callbackResult, terminalResult);

    const providerSessionId = resolveProviderIdentifier(
      [terminalResult, callbackResult, documentResult],
      PROVIDER_SESSION_ID_ALIASES,
      'mã phiên',
    );
    const providerTransactionId = resolveProviderIdentifier(
      [terminalResult, callbackResult, documentResult],
      PROVIDER_TRANSACTION_ID_ALIASES,
      'mã giao dịch',
      PROVIDER_TRANSACTION_ID_FALLBACK_ALIASES,
    );

    finalResultHandled = true;
    cleanupLaunch();
    await onResult({
      providerSessionId,
      providerTransactionId,
      sdkResult: compactResult,
    });
  };

  const handleDocumentResult: VnptSdkCallback = (sdkResult) => {
    if (isCleanedUp) return;
    if (window.__MANABIHUB_VNPT_DIAGNOSTICS__) {
      window.__MANABIHUB_VNPT_DIAGNOSTICS__.documentCallbacks += 1;
    }
    documentResult = compactSdkResult(sdkResult);
  };

  // CALL_BACK is an intermediate OCR/liveness event. Correlation IDs may already be
  // present there, but they do not prove that the flow is complete. Only END_FLOW is
  // terminal so partial evidence is never submitted.
  const handleCallbackResult: VnptSdkCallback = (sdkResult) => {
    if (isCleanedUp) return;
    if (window.__MANABIHUB_VNPT_DIAGNOSTICS__) {
      window.__MANABIHUB_VNPT_DIAGNOSTICS__.intermediateCallbacks += 1;
    }
    callbackResult = compactSdkResult(sdkResult);
  };

  const handleEndFlowResult: VnptSdkCallback = (sdkResult) => {
    if (isCleanedUp) return;
    void submitTerminalResult(compactSdkResult(sdkResult)).catch(reportCallbackError);
  };

  // Config follows VNPT eKYC Web SDK 3.2.1 docs. Callbacks stay in-process; this
  // flow never reuses the OAuth /auth/callback route.
  const dataConfig: Record<string, unknown> = {
    BACKEND_URL: env.backendUrl,
    TOKEN_KEY: env.tokenKey,
    TOKEN_ID: env.tokenId,
    ACCESS_TOKEN: env.accessToken,
    // SDK 3.2.1 defaults CHALLENGE_CODE to "00000". Omit the property when
    // VNPT has not issued an application-specific code so that default survives
    // the SDK's Object.assign(defaults, suppliedConfig) merge.
    ...(env.challengeCode
      ? { CHALLENGE_CODE: encodeURIComponent(env.challengeCode) }
      : {}),
    CALL_BACK: handleCallbackResult,
    CALL_BACK_END_FLOW: handleEndFlowResult,
    CALL_BACK_DOCUMENT_RESULT: handleDocumentResult,
    HAS_BACKGROUND_IMAGE: true,
    HAS_RESULT_SCREEN: true,
    SHOW_STEP: true,
    // VNPT SDK 3.2.1 accepts this value in MB. Five MB matches the known-good
    // CCCD camera/upload integration and avoids rejecting otherwise readable
    // high-resolution mobile captures before OCR can evaluate them.
    MAX_SIZE_IMAGE: 5,
    DEFAULT_LANGUAGE: 'vi',
    LIST_TYPE_DOCUMENT: [9],
    DOCUMENT_TYPE_START: 9,
    HAS_QR_SCAN: false,
    SDK_FLOW: 'DOCUMENT_TO_FACE',
    FLOW_TAKEN: 'DOCUMENT_TO_FACE',
    USE_METHOD: 'PHOTO_AND_UPLOAD',
    ENABLE_API_UPLOAD_IMAGE: true,
    ENABLE_API_OCR_DOCUMENT: true,
    ENABLE_API_LIVENESS_DOCUMENT: true,
    ENABLE_API_LIVENESS_FACE: true,
    ENABLE_API_MASKED_FACE: true,
    ENABLE_API_COMPARE_FACE: true,
    CHECK_LIVENESS_CARD: true,
    CHECK_LIVENESS_FACE: true,
    CHECK_MASKED_FACE: true,
    COMPARE_FACE: true,
    SHOW_TAB_RESULT_INFORMATION: true,
    SHOW_TAB_RESULT_VALIDATION: true,
    SHOW_TAB_RESULT_QRCODE: true,
  };

  // Deliberately expose only booleans for browser credentials. Raw SDK results,
  // CCCD images and access tokens must never be written to browser storage/debug state.
  window.__MANABIHUB_LAST_VNPT_CONFIG__ = safeDebugConfig(dataConfig);
  try {
    window.SDK.launch(dataConfig);
  } catch {
    cleanupLaunch();
    throw new Error(VNPT_PROVIDER_ERROR_MESSAGE);
  }
}

type EndFlowClassification = 'incomplete' | 'terminal-failure' | 'complete';

interface ScalarResultEntry {
  path: string;
  value: string | number | boolean;
}

const TERMINAL_OUTCOME_PATHS = new Set([
  'endflowresultstatus',
  'endflowresultresult',
  'endflowresultterminalstatus',
  'endflowresultterminalresult',
  'endflowresultflowstatus',
  'endflowresultobjectstatus',
  'endflowresultobjectresult',
  'endflowresultobjectterminalstatus',
  'endflowresultobjectterminalresult',
  'endflowresultobjectflowstatus',
  'endflowresultdatastatus',
  'endflowresultdataresult',
  'endflowresultdataterminalstatus',
  'endflowresultdataterminalresult',
  'endflowresultdataflowstatus',
  'endflowresultresultstatus',
  'endflowresultresultterminalstatus',
  'endflowresultresultterminalresult',
  'endflowresultresultflowstatus',
  'terminalstatus',
  'terminalresult',
]);

function classifyEndFlow(compactResult: Record<string, unknown>): EndFlowClassification {
  const entries = flattenScalarResultEntries(compactResult);
  const terminalFailure = entries.some((entry) => (
    TERMINAL_OUTCOME_PATHS.has(normalizeKey(entry.path))
      && typeof entry.value === 'string'
      && isExplicitTerminalFailure(entry.value)
  ));
  if (terminalFailure) return 'terminal-failure';

  const hasFaceLivenessOutcome = entries.some((entry) => (
    isFaceLivenessOutcomePath(entry.path) && isKnownLivenessOutcome(entry.value)
  ));
  const hasFaceCompareOutcome = entries.some((entry) => (
    isFaceCompareOutcomePath(entry.path) && isKnownCompareOutcome(entry.value)
  ));

  return hasFaceLivenessOutcome && hasFaceCompareOutcome ? 'complete' : 'incomplete';
}

function flattenScalarResultEntries(root: Record<string, unknown>) {
  const entries: ScalarResultEntry[] = [];
  const seen = new WeakSet<object>();

  const visit = (value: unknown, path: string, depth: number) => {
    if (depth > MAX_SDK_RESULT_DEPTH || value == null) return;
    if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
      entries.push({ path, value });
      return;
    }
    if (typeof value !== 'object' || seen.has(value)) return;
    seen.add(value);
    if (Array.isArray(value)) {
      value.slice(0, MAX_SDK_ARRAY_ITEMS).forEach((item, index) => visit(item, `${path}.${index}`, depth + 1));
      return;
    }
    Object.entries(value as Record<string, unknown>).forEach(([key, nestedValue]) => {
      visit(nestedValue, path ? `${path}.${key}` : key, depth + 1);
    });
  };

  visit(root, '', 0);
  return entries;
}

function isExplicitTerminalFailure(value: string) {
  const compact = normalizeKey(value);
  if (compact === 'noerror' || compact === 'withouterror') return false;
  return compact.includes('cancel')
    || compact.includes('abort')
    || compact.includes('timeout')
    || compact.includes('timedout')
    || compact === 'failed'
    || compact === 'failure'
    || compact === 'error'
    || compact.startsWith('error')
    || compact.endsWith('error');
}

function isFaceLivenessOutcomePath(path: string) {
  const normalizedPath = normalizeKey(path);
  const leaf = normalizeKey(path.split('.').at(-1) ?? '');
  return normalizedPath.startsWith('endflowresult')
    && (normalizedPath.includes('livenessface') || normalizedPath.includes('faceliveness'))
    && (normalizedPath.endsWith('livenessface')
      || normalizedPath.endsWith('faceliveness')
      || ['liveness', 'status', 'result', 'msg'].includes(leaf));
}

function isFaceCompareOutcomePath(path: string) {
  const normalizedPath = normalizeKey(path);
  const leaf = normalizeKey(path.split('.').at(-1) ?? '');
  return normalizedPath.startsWith('endflowresult')
    && normalizedPath.includes('compare')
    && (normalizedPath.endsWith('compare')
      || normalizedPath.endsWith('facecompare')
      || ['msg', 'status', 'result'].includes(leaf));
}

function isKnownLivenessOutcome(value: string | number | boolean) {
  if (typeof value === 'boolean') return true;
  if (typeof value !== 'string') return false;
  return [
    'success', 'successful', 'ok', 'valid', 'verified', 'pass', 'passed', 'live',
    'failure', 'failed', 'error', 'invalid', 'notlive', 'noliveness', 'spoof',
  ].includes(normalizeKey(value));
}

function isKnownCompareOutcome(value: string | number | boolean) {
  if (typeof value === 'boolean') return true;
  if (typeof value !== 'string') return false;
  return [
    'match', 'matched', 'same', 'identical', 'success', 'successful', 'pass', 'passed',
    'nomatch', 'mismatch', 'different', 'failure', 'failed', 'error', 'invalid',
  ].includes(normalizeKey(value));
}

/**
 * VNPT 3.2.1 starts its face-processing timeout while the tutorial modal is
 * still covering the camera on mobile WebKit. The tutorial action is a div,
 * not a button. Dismiss only the known VNPT tutorial modal, once; never match
 * arbitrary application videos or buttons.
 */
function installVnptFaceGuideAutoDismiss() {
  if (typeof document === 'undefined' || typeof MutationObserver === 'undefined') {
    return () => undefined;
  }

  let retryTimer: number | undefined;
  let stopped = false;

  const stopWatching = () => {
    stopped = true;
    observer.disconnect();
    if (retryTimer !== undefined) {
      window.clearInterval(retryTimer);
      retryTimer = undefined;
    }
  };

  const findTutorialAction = () => {
    const tutorialMedia = Array.from(document.querySelectorAll<HTMLElement>('video, video source'))
      .find((element) => {
        const rawSource = element instanceof HTMLVideoElement
          ? element.currentSrc || element.getAttribute('src') || ''
          : element.getAttribute('src') || '';
        try {
          const path = new URL(rawSource, document.baseURI).pathname;
          return path.endsWith('/lib/vietnamese-tutorial.mp4')
            || path.endsWith('/lib/english-tutorial.mp4');
        } catch {
          return false;
        }
      });

    // The SDK uses Ant Design today, but the class names are not part of VNPT's
    // public contract. Walk ancestors from the known tutorial video and anchor
    // the action to that subtree instead of depending on `.ant-modal-*`.
    let ancestor = tutorialMedia?.parentElement ?? null;
    while (ancestor && ancestor !== document.body) {
      const action = ancestor.querySelector<HTMLElement>('.vnpt-cursor-pointer.vnpt-bg-primary');
      if (action) return { tutorialMedia, action };
      ancestor = ancestor.parentElement;
    }
    return null;
  };

  const dismissKnownGuide = () => {
    if (stopped) return;
    const match = findTutorialAction();
    if (!match) {
      if (window.__MANABIHUB_VNPT_DIAGNOSTICS__?.faceGuideDetectedAt) {
        window.__MANABIHUB_VNPT_DIAGNOSTICS__.faceGuideClosedAt = Date.now();
        stopWatching();
      }
      return;
    }

    const diagnostics = window.__MANABIHUB_VNPT_DIAGNOSTICS__;
    if (diagnostics) {
      diagnostics.faceGuideDetectedAt ??= Date.now();
      diagnostics.faceGuideClickAttempts += 1;
    }
    match.action.click();
  };

  const observer = new MutationObserver(dismissKnownGuide);
  observer.observe(document.body, { childList: true, subtree: true });
  // MutationObserver can fire while React is still committing the vendor modal.
  // Retry until the known tutorial subtree is actually gone; a one-shot click
  // can be lost and leaves VNPT's face timer running behind the video.
  retryTimer = window.setInterval(dismissKnownGuide, 100);
  queueMicrotask(dismissKnownGuide);
  return stopWatching;
}

function getVnptEnv() {
  return {
    enabled: String(import.meta.env.VITE_VNPT_EKYC_ENABLED ?? '').trim().toLowerCase() === 'true',
    scriptUrls: resolveScriptUrls(import.meta.env.VITE_VNPT_EKYC_SDK_SCRIPT_URLS),
    backendUrl: normalizeBackendUrl(import.meta.env.VITE_VNPT_EKYC_BACKEND_URL),
    tokenId: (import.meta.env.VITE_VNPT_EKYC_TOKEN_ID ?? '').trim(),
    tokenKey: (import.meta.env.VITE_VNPT_EKYC_TOKEN_KEY ?? '').trim(),
    accessToken: sanitizeAccessToken(import.meta.env.VITE_VNPT_EKYC_ACCESS_TOKEN),
    challengeCode: (import.meta.env.VITE_VNPT_EKYC_CHALLENGE_CODE ?? '').trim(),
  };
}

async function loadVnptScripts(scriptUrls: string[]) {
  if (window.SDK?.launch) {
    return;
  }

  const fingerprint = scriptUrls.join('\u0000');
  if (vnptScriptSequence && vnptScriptSequence.fingerprint !== fingerprint) {
    throw new Error('Danh sách VNPT SDK đã thay đổi trong cùng một phiên trang. Vui lòng tải lại trang và thử lại.');
  }

  if (!vnptScriptSequence) {
    vnptScriptSequence = {
      fingerprint,
      promise: (async () => {
        for (const url of scriptUrls) {
          await loadScript(url);
        }
      })(),
    };
  }

  const sequence = vnptScriptSequence;
  try {
    await sequence.promise;
  } catch (error) {
    if (vnptScriptSequence === sequence) {
      vnptScriptSequence = null;
    }
    throw error;
  }
}

function loadScript(src: string) {
  return new Promise<void>((resolve, reject) => {
    const existingScript = findLoadedScript(src);

    if (existingScript?.dataset.loaded === 'true') {
      resolve();
      return;
    }

    const script = existingScript ?? document.createElement('script');
    const handleLoad = () => {
      script.removeEventListener('error', handleError);
      script.dataset.loaded = 'true';
      resolve();
    };
    const handleError = () => {
      script.removeEventListener('load', handleLoad);
      script.remove();
      reject(new Error(`Không tải được VNPT SDK script: ${src}`));
    };
    script.addEventListener('load', handleLoad, { once: true });
    script.addEventListener('error', handleError, { once: true });

    if (!existingScript) {
      script.dataset.vnptSdk = src;
      script.async = true;
      script.src = src;
      document.head.appendChild(script);
    }
  });
}

function findLoadedScript(src: string) {
  return Array.from(document.querySelectorAll<HTMLScriptElement>('script[data-vnpt-sdk]')).find(
    (script) => script.dataset.vnptSdk === src,
  );
}

function compactSdkResult(value: unknown): Record<string, unknown> {
  const compacted = compactSdkValue(value, 0, new WeakSet<object>());

  if (compacted && compacted !== OMITTED && typeof compacted === 'object' && !Array.isArray(compacted)) {
    return compacted as Record<string, unknown>;
  }

  return compacted === OMITTED ? {} : { rawResult: compacted };
}

function compactSdkValue(value: unknown, depth: number, seen: WeakSet<object>): unknown | typeof OMITTED {
  if (value == null || typeof value === 'boolean' || typeof value === 'number') {
    return value;
  }

  if (typeof value === 'string') {
    const trimmed = value.trim();
    if (looksLikeEmbeddedMedia(trimmed) || trimmed.length > MAX_SDK_STRING_LENGTH) {
      return OMITTED;
    }
    return trimmed;
  }

  if (typeof value !== 'object' || depth > MAX_SDK_RESULT_DEPTH || seen.has(value)) {
    return OMITTED;
  }

  seen.add(value);

  if (Array.isArray(value)) {
    return value
      .slice(0, MAX_SDK_ARRAY_ITEMS)
      .map((item) => compactSdkValue(item, depth + 1, seen))
      .filter((item) => item !== OMITTED);
  }

  const result: Record<string, unknown> = {};
  Object.entries(value as Record<string, unknown>).forEach(([key, nestedValue]) => {
    if (isSensitiveOrBinaryKey(key)) {
      return;
    }

    const compacted = compactSdkValue(nestedValue, depth + 1, seen);
    if (compacted !== OMITTED) {
      result[key] = compacted;
    }
  });
  return result;
}

function mergeSdkResults(
  documentResult: Record<string, unknown> | null,
  callbackResult: Record<string, unknown> | null,
  endFlowResult: Record<string, unknown> | null,
) {
  const result: Record<string, unknown> = {};
  if (documentResult && Object.keys(documentResult).length > 0) result.documentResult = documentResult;
  if (callbackResult && Object.keys(callbackResult).length > 0) result.callbackResult = callbackResult;
  if (endFlowResult && Object.keys(endFlowResult).length > 0) result.endFlowResult = endFlowResult;
  return result;
}

function resolveProviderIdentifier(
  sourcesInPriorityOrder: Array<Record<string, unknown> | null>,
  aliases: string[],
  label: string,
  fallbackAliases: string[] = [],
) {
  const values = sourcesInPriorityOrder.flatMap((source) => source ? findExactValues(source, aliases) : []);
  const distinctValues = Array.from(new Set(values));
  if (distinctValues.length > 1) {
    throw new Error(`VNPT trả về ${label} không nhất quán. Vui lòng thực hiện lại phiên xác minh.`);
  }
  if (distinctValues[0]) return distinctValues[0];

  const fallbackValues = Array.from(new Set(
    sourcesInPriorityOrder.flatMap((source) => source ? findExactValues(source, fallbackAliases) : []),
  ));
  if (fallbackValues.length > 1) {
    throw new Error(`VNPT trả về ${label} không nhất quán. Vui lòng thực hiện lại phiên xác minh.`);
  }
  return fallbackValues[0] ?? null;
}

function findExactValues(source: Record<string, unknown>, aliases: string[]) {
  const aliasSet = new Set(aliases);
  const values: string[] = [];
  const candidateEnvelopes: Record<string, unknown>[] = [source];
  for (const envelopeKey of ['object', 'data', 'result']) {
    const envelope = findObjectProperty(source, envelopeKey);
    if (envelope) candidateEnvelopes.push(envelope);
  }
  for (const envelope of candidateEnvelopes) {
    for (const [key, value] of Object.entries(envelope)) {
      if (aliasSet.has(normalizeKey(key)) && isScalarValue(value)) {
        const normalized = String(value).trim();
        if (normalized) values.push(normalized);
      }
    }
  }
  return values;
}

function findObjectProperty(source: Record<string, unknown>, expectedKey: string) {
  const entry = Object.entries(source).find(([key]) => normalizeKey(key) === expectedKey);
  return entry?.[1] && typeof entry[1] === 'object' && !Array.isArray(entry[1])
    ? entry[1] as Record<string, unknown>
    : null;
}

function isScalarValue(value: unknown) {
  return (typeof value === 'string' || typeof value === 'number') && String(value).trim().length > 0;
}

function isSensitiveOrBinaryKey(key: string) {
  const normalized = normalizeKey(key);
  if (
    normalized.includes('authorization')
    || normalized.includes('accesstoken')
    || normalized.includes('accesskey')
    || normalized.includes('tokenkey')
    || normalized.includes('tokenid')
    || normalized.includes('secret')
    || normalized.includes('apikey')
    || normalized.includes('password')
    || normalized.includes('cookie')
    || normalized === 'header'
    || normalized === 'headers'
    || normalized.includes('headersrequest')
    || normalized.includes('signature')
  ) {
    return true;
  }

  return normalized.includes('base64')
    || normalized.includes('blob')
    || normalized.includes('filecontent')
    || normalized.includes('filedata')
    || normalized.includes('videodata');
}

function looksLikeEmbeddedMedia(value: string) {
  return /^data:(image|video|application)\//i.test(value) || /^[A-Za-z0-9+/]{8_000,}={0,2}$/.test(value);
}

function normalizeKey(value: string) {
  return value.replace(/[^a-zA-Z0-9]/g, '').toLowerCase();
}

function toError(error: unknown) {
  return error instanceof Error ? error : new Error('VNPT eKYC không trả về kết quả hợp lệ. Vui lòng thử lại.');
}

function safeDebugConfig(config: Record<string, unknown>) {
  return {
    BACKEND_URL: config.BACKEND_URL,
    TOKEN_ID_EXISTS: Boolean(config.TOKEN_ID),
    TOKEN_KEY_EXISTS: Boolean(config.TOKEN_KEY),
    ACCESS_TOKEN_EXISTS: Boolean(config.ACCESS_TOKEN),
    ACCESS_TOKEN_HAS_BEARER_PREFIX: String(config.ACCESS_TOKEN ?? '').toLowerCase().startsWith('bearer '),
    CHALLENGE_CODE_EXISTS: Boolean(config.CHALLENGE_CODE),
    SDK_FLOW: config.SDK_FLOW,
    FLOW_TAKEN: config.FLOW_TAKEN,
    USE_METHOD: config.USE_METHOD,
    HAS_QR_SCAN: config.HAS_QR_SCAN,
    LIST_TYPE_DOCUMENT: config.LIST_TYPE_DOCUMENT,
    CHECK_LIVENESS_CARD: config.CHECK_LIVENESS_CARD,
    CHECK_LIVENESS_FACE: config.CHECK_LIVENESS_FACE,
    CHECK_MASKED_FACE: config.CHECK_MASKED_FACE,
    COMPARE_FACE: config.COMPARE_FACE,
    ENABLE_API_UPLOAD_IMAGE: config.ENABLE_API_UPLOAD_IMAGE,
    ENABLE_API_OCR_DOCUMENT: config.ENABLE_API_OCR_DOCUMENT,
    ENABLE_API_LIVENESS_DOCUMENT: config.ENABLE_API_LIVENESS_DOCUMENT,
    ENABLE_API_LIVENESS_FACE: config.ENABLE_API_LIVENESS_FACE,
    ENABLE_API_MASKED_FACE: config.ENABLE_API_MASKED_FACE,
    ENABLE_API_COMPARE_FACE: config.ENABLE_API_COMPARE_FACE,
  };
}

function splitCsv(value: string | undefined) {
  return (value ?? '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
}

function resolveScriptUrls(value: string | undefined) {
  const configuredUrls = splitCsv(value);
  const urls = configuredUrls.length > 0 ? configuredUrls : DEFAULT_VNPT_SDK_SCRIPT_URLS;
  urls.forEach((url) => {
    if (url.startsWith('/') && !url.startsWith('//') && !url.includes('\\')) return;
    throw new Error(`URL VNPT SDK phải là đường dẫn same-origin bắt đầu bằng '/': ${url}`);
  });
  return urls;
}

function normalizeBackendUrl(value: string | undefined) {
  const normalized = (value ?? '').trim().replace(/\/+$/, '');
  if (!normalized) return '';
  let parsed: URL;
  try {
    parsed = new URL(normalized);
  } catch {
    throw new Error('VITE_VNPT_EKYC_BACKEND_URL không phải URL hợp lệ.');
  }
  if (!APPROVED_VNPT_BACKEND_ORIGINS.has(parsed.origin)
      || parsed.username
      || parsed.password
      || parsed.pathname !== '/'
      || parsed.search
      || parsed.hash) {
    throw new Error('VITE_VNPT_EKYC_BACKEND_URL phải dùng đúng VNPT production hoặc sandbox origin đã phê duyệt.');
  }
  return parsed.origin;
}

function sanitizeAccessToken(value: string | undefined) {
  return (value ?? '').trim().replace(/^bearer\s+/i, '');
}

interface ConfiguredScriptSource {
  href: string;
  originAndPath: string;
}

function resolveConfiguredScriptSources(scriptUrls: string[]): ConfiguredScriptSource[] {
  return scriptUrls.map((url) => {
    const resolved = new URL(url, document.baseURI);
    return {
      href: resolved.href,
      originAndPath: `${resolved.origin}${resolved.pathname}`,
    };
  });
}

function isConfiguredVnptSource(
  source: string,
  stack: string,
  configuredSources: ConfiguredScriptSource[],
) {
  if (source) {
    try {
      const resolved = new URL(source, document.baseURI);
      const originAndPath = `${resolved.origin}${resolved.pathname}`;
      if (configuredSources.some((configured) => configured.originAndPath === originAndPath)) return true;
    } catch {
      // A malformed source is not trustworthy enough to attribute to VNPT.
    }
  }
  return Boolean(stack && configuredSources.some((configured) =>
    stack.includes(configured.href) || stack.includes(configured.originAndPath)));
}

function readStringProperty(value: unknown, property: string) {
  if (!value || typeof value !== 'object') return '';
  const candidate = (value as Record<string, unknown>)[property];
  return typeof candidate === 'string' ? candidate : '';
}
