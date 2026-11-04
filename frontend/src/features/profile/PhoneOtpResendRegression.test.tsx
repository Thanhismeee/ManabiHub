import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  getMyStudentProfile,
  getMyTeacherProfile,
  requestStudentPhoneVerification,
  requestTeacherPhoneVerification,
} from './profileApi';
import { getStudentIdentityVerificationStatus } from '../wallet/services/studentIdentityVerificationService';
import StudentProfilePage from './StudentProfilePage';
import TeacherProfilePage from './TeacherProfilePage';

vi.mock('./profileApi', () => ({
  avatarUploadErrorMessage: vi.fn(),
  confirmStudentPhoneVerification: vi.fn(),
  confirmTeacherPhoneVerification: vi.fn(),
  getMyStudentProfile: vi.fn(),
  getMyTeacherProfile: vi.fn(),
  requestStudentPhoneVerification: vi.fn(),
  requestTeacherPhoneVerification: vi.fn(),
  updateMyStudentProfile: vi.fn(),
  updateMyTeacherProfile: vi.fn(),
  uploadAvatar: vi.fn(),
}));

vi.mock('../wallet/services/studentIdentityVerificationService', () => ({
  getStudentIdentityVerificationStatus: vi.fn(),
}));

vi.mock('../../shared/auth/firebasePhoneAuth', () => ({
  firebasePhoneErrorMessage: () => 'Không thể xác thực SMS qua Firebase.',
  startFirebasePhoneOtp: vi.fn(),
}));

const studentProfile = {
  id: 'student-1',
  email: 'student@example.com',
  fullName: 'Student Test',
  phoneNumber: '0987654326',
  phoneVerified: false,
  phoneVerifiedAt: null,
  avatarUrl: null,
  displayName: 'Student',
  jlptGoal: 'N3',
};

const teacherProfile = {
  ...studentProfile,
  id: 'teacher-1',
  email: 'teacher@example.com',
  fullName: 'Teacher Test',
  displayName: 'Teacher',
  bio: 'Teacher bio',
};

const smsChallenge = {
  phoneNumber: '0987654326',
  verified: false,
  verifiedAt: null,
  verificationMethod: 'SMS' as const,
  challengeId: 'challenge-1',
  phoneNumberE164: '+84987654326',
  expiresAt: '2026-09-27T03:00:00Z',
};

const rateLimitError = {
  response: {
    data: {
      messageCode: 'PHONE_VERIFICATION_RATE_LIMITED',
      message: 'Please wait before requesting another verification code',
    },
  },
};

async function finishCooldown() {
  for (let second = 0; second < 60; second += 1) {
    await act(async () => {
      await vi.advanceTimersByTimeAsync(1_000);
    });
  }
}

afterEach(() => {
  cleanup();
  vi.useRealTimers();
});

describe('phone OTP resend regression', () => {
  beforeEach(() => {
    vi.mocked(getMyStudentProfile).mockResolvedValue(studentProfile);
    vi.mocked(getMyTeacherProfile).mockResolvedValue(teacherProfile);
    vi.mocked(getStudentIdentityVerificationStatus).mockResolvedValue({
      verified: false,
      status: 'NOT_VERIFIED',
    });
  });

  it('keeps the student OTP input visible when a resend is rejected', async () => {
    vi.mocked(requestStudentPhoneVerification)
      .mockResolvedValueOnce(smsChallenge)
      .mockRejectedValueOnce(rateLimitError);
    render(
      <MemoryRouter>
        <StudentProfilePage />
      </MemoryRouter>,
    );

    const sendButton = await screen.findByRole('button', { name: 'Gửi mã SMS' });
    vi.useFakeTimers();
    await act(async () => fireEvent.click(sendButton));

    expect(screen.getByLabelText('Mã SMS 6 số')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Gửi lại sau 60s' })).toBeDisabled();

    await finishCooldown();
    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Gửi lại mã SMS' })));

    expect(requestStudentPhoneVerification).toHaveBeenCalledTimes(2);
    expect(screen.getByLabelText('Mã SMS 6 số')).toBeInTheDocument();
    expect(screen.getByText('Please wait before requesting another verification code')).toBeInTheDocument();
  }, 10_000);

  it('keeps the teacher OTP input visible when a resend is rejected', async () => {
    vi.mocked(requestTeacherPhoneVerification)
      .mockResolvedValueOnce(smsChallenge)
      .mockRejectedValueOnce(rateLimitError);
    render(<TeacherProfilePage />);

    const sendButton = await screen.findByRole('button', { name: 'Gửi mã SMS' });
    vi.useFakeTimers();
    await act(async () => fireEvent.click(sendButton));

    expect(screen.getByLabelText('Mã SMS 6 số')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Gửi lại sau 60s' })).toBeDisabled();

    await finishCooldown();
    await act(async () => fireEvent.click(screen.getByRole('button', { name: 'Gửi lại mã SMS' })));

    expect(requestTeacherPhoneVerification).toHaveBeenCalledTimes(2);
    expect(screen.getByLabelText('Mã SMS 6 số')).toBeInTheDocument();
    expect(screen.getByText('Please wait before requesting another verification code')).toBeInTheDocument();
  }, 10_000);
});
