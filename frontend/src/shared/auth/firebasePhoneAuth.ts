import { getApp, getApps, initializeApp, type FirebaseOptions } from 'firebase/app';
import {
  getAuth,
  inMemoryPersistence,
  RecaptchaVerifier,
  setPersistence,
  signInWithPhoneNumber,
  signOut,
  type Auth,
  type ConfirmationResult,
} from 'firebase/auth';

const FIREBASE_APP_NAME = 'manabihub-phone-auth';

export type FirebasePhoneProof = {
  phoneAuthChallengeId: string;
  firebaseIdToken: string;
};

export type PhoneOtpChallengeResponse = {
  verificationMethod: 'SMS' | 'EMAIL' | 'FIREBASE';
  challengeId: string;
  phoneNumberE164: string | null;
  maskedDestination: string | null;
  expiresAt: string;
};

export type FirebasePhoneOtpSession = {
  confirm(code: string): Promise<FirebasePhoneProof>;
  cancel(): Promise<void>;
};

type FirebaseChallenge = {
  challengeId: string;
  phoneNumberE164: string;
};

let authPromise: Promise<Auth> | null = null;

export async function startFirebasePhoneOtp(
  challenge: FirebaseChallenge,
): Promise<FirebasePhoneOtpSession> {
  if (!/^\+84\d{9}$/.test(challenge.phoneNumberE164)) {
    throw new Error('Số điện thoại Firebase phải ở định dạng +84XXXXXXXXX.');
  }
  if (!challenge.challengeId) {
    throw new Error('Challenge xác thực số điện thoại không hợp lệ.');
  }

  const auth = await firebaseAuth();
  const container = document.createElement('div');
  container.id = `firebase-recaptcha-${crypto.randomUUID()}`;
  document.body.appendChild(container);

  let confirmation: ConfirmationResult;
  let verifier: RecaptchaVerifier | null = null;
  try {
    verifier = new RecaptchaVerifier(auth, container, { size: 'invisible' });
    confirmation = await signInWithPhoneNumber(
      auth,
      challenge.phoneNumberE164,
      verifier,
    );
  } finally {
    verifier?.clear();
    container.remove();
  }

  let resolvedProof: FirebasePhoneProof | null = null;
  let cancelled = false;
  return {
    async confirm(code: string) {
      if (resolvedProof) {
        return resolvedProof;
      }
      if (cancelled) {
        throw new Error('Phiên OTP này đã được sử dụng. Hãy yêu cầu mã mới.');
      }
      const credential = await confirmation.confirm(code);
      const firebaseIdToken = await credential.user.getIdToken(true);
      await signOut(auth).catch(() => undefined);
      resolvedProof = {
        phoneAuthChallengeId: challenge.challengeId,
        firebaseIdToken,
      };
      return resolvedProof;
    },
    async cancel() {
      cancelled = true;
      resolvedProof = null;
      await signOut(auth).catch(() => undefined);
    },
  };
}

export function firebasePhoneErrorMessage(error: unknown): string {
  const code = typeof error === 'object' && error !== null && 'code' in error
    ? String((error as { code?: unknown }).code ?? '')
    : '';
  const messages: Record<string, string> = {
    'auth/invalid-verification-code': 'Mã SMS không đúng hoặc đã hết hạn.',
    'auth/code-expired': 'Mã SMS đã hết hạn. Hãy yêu cầu mã mới.',
    'auth/session-expired': 'Phiên xác thực SMS đã hết hạn. Hãy yêu cầu mã mới.',
    'auth/invalid-verification-id': 'Phiên xác thực SMS không hợp lệ. Hãy yêu cầu mã mới.',
    'auth/too-many-requests': 'Bạn yêu cầu quá nhiều lần. Vui lòng thử lại sau.',
    'auth/quota-exceeded': 'Hạn mức SMS Firebase đã hết. Vui lòng liên hệ quản trị viên.',
    'auth/billing-not-enabled': 'Firebase chưa bật thanh toán cho SMS thật.',
    'auth/captcha-check-failed': 'reCAPTCHA không hợp lệ. Vui lòng tải lại trang.',
    'auth/invalid-app-credential': 'reCAPTCHA hoặc cấu hình ứng dụng Firebase không hợp lệ.',
    'auth/missing-app-credential': 'Không thể khởi tạo reCAPTCHA Firebase.',
    'auth/missing-phone-number': 'Thiếu số điện thoại cần xác thực.',
    'auth/invalid-phone-number': 'Số điện thoại không hợp lệ.',
    'auth/operation-not-allowed': 'Firebase Phone Auth chưa được bật.',
    'auth/unauthorized-domain': 'Tên miền hiện tại chưa được thêm vào Firebase Authorized domains.',
    'auth/app-not-authorized': 'Ứng dụng hoặc API key chưa được phép dùng Firebase Authentication.',
    'auth/network-request-failed': 'Không thể kết nối Firebase. Hãy kiểm tra mạng và thử lại.',
  };
  if (messages[code]) {
    return messages[code];
  }
  return error instanceof Error && error.message
    ? error.message
    : 'Không thể xác thực SMS qua Firebase.';
}

async function firebaseAuth(): Promise<Auth> {
  if (!authPromise) {
    authPromise = (async () => {
      const options = firebaseOptions();
      const app = getApps().some((candidate) => candidate.name === FIREBASE_APP_NAME)
        ? getApp(FIREBASE_APP_NAME)
        : initializeApp(options, FIREBASE_APP_NAME);
      const auth = getAuth(app);
      await setPersistence(auth, inMemoryPersistence);
      auth.useDeviceLanguage();
      return auth;
    })().catch((error) => {
      authPromise = null;
      throw error;
    });
  }
  return authPromise;
}

function firebaseOptions(): FirebaseOptions {
  if (String(import.meta.env.VITE_FIREBASE_PHONE_AUTH_ENABLED ?? '').trim().toLowerCase() !== 'true') {
    throw new Error('VITE_FIREBASE_PHONE_AUTH_ENABLED chưa được bật cho bản frontend.');
  }
  const options: FirebaseOptions = {
    apiKey: value('VITE_FIREBASE_API_KEY', import.meta.env.VITE_FIREBASE_API_KEY),
    authDomain: value('VITE_FIREBASE_AUTH_DOMAIN', import.meta.env.VITE_FIREBASE_AUTH_DOMAIN),
    projectId: value('VITE_FIREBASE_PROJECT_ID', import.meta.env.VITE_FIREBASE_PROJECT_ID),
    appId: value('VITE_FIREBASE_APP_ID', import.meta.env.VITE_FIREBASE_APP_ID),
    messagingSenderId: String(import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID ?? '').trim() || undefined,
  };
  return options;
}

function value(name: string, raw: unknown): string {
  const resolved = String(raw ?? '').trim();
  if (!resolved) {
    throw new Error(`${name} chưa được cấu hình cho bản frontend.`);
  }
  return resolved;
}
