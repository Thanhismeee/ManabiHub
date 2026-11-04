import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import '@testing-library/jest-dom/vitest';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { TeacherKycPage } from './TeacherKycPage';

const apiMocks = vi.hoisted(() => ({
  getTeacherKycStatus: vi.fn(),
  restartTeacherVerification: vi.fn(),
  submitTeacherCertificate: vi.fn(),
  verifyTeacherIdentity: vi.fn(),
}));

const ocrMocks = vi.hoisted(() => ({
  recognizeJlptCertificate: vi.fn(),
}));

vi.mock('./teacherKycApi', () => apiMocks);
vi.mock('./certificateOcr', () => ocrMocks);
vi.mock('./vnptIdentitySdk', () => ({
  launchVnptIdentitySdk: vi.fn(),
  resetVnptIdentitySdkRuntime: vi.fn(),
}));

beforeEach(() => {
  apiMocks.getTeacherKycStatus.mockResolvedValue({
    teacherId: 'teacher-1',
    userId: 'user-1',
    teacherKycStatus: 'IDENTITY_VERIFIED',
    teacherKycStatusLabel: 'Đã xác minh danh tính',
    canPublishCourse: false,
    identityVerification: {
      status: 'VERIFIED',
      statusLabel: 'Đã xác minh',
      canInteract: false,
    },
    certificateVerification: {
      status: 'NOT_STARTED',
      statusLabel: 'Chưa nộp',
      canInteract: true,
    },
    latestRequest: {
      requestId: 'req-1',
      status: 'DRAFT',
      statusLabel: 'Nháp',
      submittedAt: '2026-08-21T00:00:00Z',
      identityStatus: 'VERIFIED',
      identityStatusLabel: 'Đã xác minh',
      certificateStatus: 'NOT_SUBMITTED',
      certificateStatusLabel: 'Chưa nộp',
      copyrightAgreed: false,
      verificationPayload: {
        identityOcr: {
          fullName: 'PHẠM ĐỨC LONG',
          dateOfBirth: '20/05/2000',
          idNumber: '001200000001',
        },
      },
      documents: [],
    },
    srsTrace: {},
  });
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

describe('TeacherKycPage certificate submission and validation', () => {
  it('displays Vietnamese mismatch message when KYC_CERTIFICATE_OCR_MISMATCH code is returned', async () => {
    ocrMocks.recognizeJlptCertificate.mockResolvedValue({
      rawText: 'Name THAN VAN THANH\nDate of Birth 2004-08-12\nLevel N3\nCertificate No 25B2080102-33745',
      holderName: 'THAN VAN THANH',
      dateOfBirth: '2004-08-12',
      level: 'N3',
      certificateCode: '25B2080102-33745',
    });

    apiMocks.submitTeacherCertificate.mockRejectedValue({
      response: {
        status: 400,
        data: {
          success: false,
          messageCode: 'KYC_CERTIFICATE_OCR_MISMATCH',
          message: 'The name read from the JLPT certificate does not match the VNPT-verified CCCD',
        },
      },
    });

    render(
      <MemoryRouter>
        <TeacherKycPage />
      </MemoryRouter>,
    );

    // Wait for page to finish loading
    await screen.findByText('Cung cấp chứng chỉ chuyên môn');

    // Upload certificate file
    const file = new File(['fake-image'], 'jlpt_cert.png', { type: 'image/png' });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await act(async () => {
      fireEvent.change(input, { target: { files: [file] } });
    });

    // Check that OCR fields populated
    expect(await screen.findByDisplayValue('THAN VAN THANH')).toBeInTheDocument();
    expect(screen.getByDisplayValue('2004-08-12')).toBeInTheDocument();
    expect(screen.getByDisplayValue('N3')).toBeInTheDocument();
    expect(screen.getByDisplayValue('25B2080102-33745')).toBeInTheDocument();

    // Check copyright agreement checkbox
    const agreementCheckbox = screen.getByRole('checkbox', {
      name: /Tôi đã đọc Điều khoản dành cho giảng viên/i,
    });
    fireEvent.click(agreementCheckbox);

    // Submit certificate
    const submitButton = screen.getByRole('button', { name: 'Nộp chứng chỉ' });
    await act(async () => {
      fireEvent.click(submitButton);
    });

    // Verify localized message displayed
    expect(await screen.findByText('Họ tên hoặc ngày sinh trên chứng chỉ không khớp với thông tin CCCD đã xác minh.')).toBeInTheDocument();

    // Verify fields remain populated after rejection
    expect(screen.getByDisplayValue('THAN VAN THANH')).toBeInTheDocument();
    expect(screen.getByDisplayValue('2004-08-12')).toBeInTheDocument();
  });

  it('displays generic server error message on 500 error', async () => {
    ocrMocks.recognizeJlptCertificate.mockResolvedValue({
      rawText: 'Name PHAM DUC LONG\nDate of Birth 2000-05-20\nLevel N2\nCertificate No 25B2080102-33745',
      holderName: 'PHAM DUC LONG',
      dateOfBirth: '2000-05-20',
      level: 'N2',
      certificateCode: '25B2080102-33745',
    });

    apiMocks.submitTeacherCertificate.mockRejectedValue({
      response: {
        status: 500,
        data: {
          success: false,
          messageCode: 'COMMON_INTERNAL_ERROR',
          message: 'An unexpected error occurred. Please contact the administrator.',
        },
      },
    });

    render(
      <MemoryRouter>
        <TeacherKycPage />
      </MemoryRouter>,
    );

    await screen.findByText('Cung cấp chứng chỉ chuyên môn');

    const file = new File(['fake-image'], 'jlpt_cert.png', { type: 'image/png' });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await act(async () => {
      fireEvent.change(input, { target: { files: [file] } });
    });

    const agreementCheckbox = screen.getByRole('checkbox', {
      name: /Tôi đã đọc Điều khoản dành cho giảng viên/i,
    });
    fireEvent.click(agreementCheckbox);

    const submitButton = await screen.findByRole('button', { name: 'Nộp chứng chỉ' });
    await act(async () => {
      fireEvent.click(submitButton);
    });

    expect(await screen.findByText('An unexpected error occurred. Please contact the administrator.')).toBeInTheDocument();
  });

  it('prevents duplicate submissions when button is triggered while submitting', async () => {
    ocrMocks.recognizeJlptCertificate.mockResolvedValue({
      rawText: 'Name PHAM DUC LONG\nDate of Birth 2000-05-20\nLevel N2\nCertificate No 25B2080102-33745',
      holderName: 'PHAM DUC LONG',
      dateOfBirth: '2000-05-20',
      level: 'N2',
      certificateCode: '25B2080102-33745',
    });

    let resolveSubmission: (value: unknown) => void;
    apiMocks.submitTeacherCertificate.mockReturnValue(
      new Promise((resolve) => {
        resolveSubmission = resolve;
      }),
    );

    render(
      <MemoryRouter>
        <TeacherKycPage />
      </MemoryRouter>,
    );

    await screen.findByText('Cung cấp chứng chỉ chuyên môn');

    const file = new File(['fake-image'], 'jlpt_cert.png', { type: 'image/png' });
    const input = document.querySelector('input[type="file"]') as HTMLInputElement;
    await act(async () => {
      fireEvent.change(input, { target: { files: [file] } });
    });

    const agreementCheckbox = screen.getByRole('checkbox', {
      name: /Tôi đã đọc Điều khoản dành cho giảng viên/i,
    });
    fireEvent.click(agreementCheckbox);

    const submitButton = await screen.findByRole('button', { name: 'Nộp chứng chỉ' });

    // First click
    await act(async () => {
      fireEvent.click(submitButton);
    });

    expect(apiMocks.submitTeacherCertificate).toHaveBeenCalledTimes(1);

    // Second click while in-flight
    await act(async () => {
      fireEvent.click(submitButton);
    });

    // Should still only be called once
    expect(apiMocks.submitTeacherCertificate).toHaveBeenCalledTimes(1);

    // Finish submission
    await act(async () => {
      resolveSubmission?.({
        data: {
          sessionToken: 'token-1',
        },
      });
    });
  });
});
