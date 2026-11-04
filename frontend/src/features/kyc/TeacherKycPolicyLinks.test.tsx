import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ROUTES } from '../../shared/constants/routes';
import { TeacherKycPage } from './TeacherKycPage';

const apiMocks = vi.hoisted(() => ({
  getTeacherKycStatus: vi.fn(),
  restartTeacherVerification: vi.fn(),
  submitTeacherCertificate: vi.fn(),
  verifyTeacherIdentity: vi.fn(),
}));
const sdkMocks = vi.hoisted(() => ({
  launchVnptIdentitySdk: vi.fn(),
  resetVnptIdentitySdkRuntime: vi.fn(),
}));

vi.mock('./teacherKycApi', () => apiMocks);
vi.mock('./certificateOcr', () => ({ recognizeJlptCertificate: vi.fn() }));
vi.mock('./vnptIdentitySdk', () => sdkMocks);

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
    latestRequest: null,
    srsTrace: {},
  });
});

afterEach(() => {
  cleanup();
  localStorage.clear();
});

describe('TeacherKycPage policy links', () => {
  it('links KYC guidance and the copyright confirmation to the matching terms', async () => {
    render(
      <MemoryRouter>
        <TeacherKycPage />
      </MemoryRouter>,
    );

    expect(screen.getByRole('link', {
      name: 'Tìm hiểu thêm về chính sách KYC',
    }).getAttribute('href')).toBe(`${ROUTES.PUBLIC.HELP}/instructors/verification`);
    expect((await screen.findByRole('link', {
      name: 'Điều khoản dành cho giảng viên',
    })).getAttribute('href')).toBe(ROUTES.PUBLIC.INSTRUCTOR_TERMS);
    expect(screen.getByRole('link', { name: 'Điều khoản sử dụng' }).getAttribute('href'))
      .toBe(ROUTES.PUBLIC.TERMS);
    expect(screen.getByRole('checkbox', {
      name: 'Tôi đã đọc Điều khoản dành cho giảng viên và chấp nhận cam kết trách nhiệm bản quyền: tôi có quyền sử dụng hợp lệ đối với nội dung số mình gửi lên ManabiHub.',
    }).hasAttribute('disabled')).toBe(false);
  });

  it('keeps terminal VNPT callback errors visible and allows an immediate retry', async () => {
    apiMocks.getTeacherKycStatus.mockResolvedValue({
      teacherId: 'teacher-1',
      userId: 'user-1',
      teacherKycStatus: 'NOT_STARTED',
      teacherKycStatusLabel: 'Chưa xác minh',
      canPublishCourse: false,
      identityVerification: {
        status: 'NOT_STARTED',
        statusLabel: 'Chưa xác minh',
        canInteract: true,
      },
      certificateVerification: {
        status: 'LOCKED',
        statusLabel: 'Chưa mở khóa',
        canInteract: false,
      },
      latestRequest: null,
      srsTrace: {},
    });
    sdkMocks.launchVnptIdentitySdk.mockImplementation(async (_onResult, options) => {
      options?.onError?.(new Error('VNPT chưa trả về đủ mã phiên và mã giao dịch.'));
    });

    render(
      <MemoryRouter>
        <TeacherKycPage />
      </MemoryRouter>,
    );

    const startButton = await screen.findByRole('button', { name: 'Bắt đầu xác minh danh tính' });
    fireEvent.click(startButton);

    expect(await screen.findByText('VNPT chưa trả về đủ mã phiên và mã giao dịch.')).toBeInTheDocument();
    await waitFor(() => expect(startButton).not.toBeDisabled());
    expect(localStorage.getItem('manabihub_kyc_identity_launch_cooldown_until')).toBeNull();
  });

  it('locks the SDK dialog while the terminal result is being recorded', async () => {
    apiMocks.getTeacherKycStatus.mockResolvedValue({
      teacherId: 'teacher-1',
      userId: 'user-1',
      teacherKycStatus: 'NOT_STARTED',
      teacherKycStatusLabel: 'Chưa xác minh',
      canPublishCourse: false,
      identityVerification: { status: 'NOT_STARTED', statusLabel: 'Chưa xác minh', canInteract: true },
      certificateVerification: { status: 'LOCKED', statusLabel: 'Chưa mở khóa', canInteract: false },
      latestRequest: null,
      srsTrace: {},
    });
    let terminalCallback: ((result: Record<string, unknown>) => Promise<void>) | undefined;
    sdkMocks.launchVnptIdentitySdk.mockImplementation(async (onResult) => {
      terminalCallback = onResult;
    });
    apiMocks.verifyTeacherIdentity.mockReturnValue(new Promise(() => undefined));

    render(
      <MemoryRouter>
        <TeacherKycPage />
      </MemoryRouter>,
    );
    fireEvent.click(await screen.findByRole('button', { name: 'Bắt đầu xác minh danh tính' }));
    await waitFor(() => expect(terminalCallback).toBeDefined());

    await act(async () => {
      void terminalCallback?.({ sdkResult: { status: 'SUCCESS' } });
    });

    expect(await screen.findByText('Đang ghi nhận kết quả xác minh. Không đóng cửa sổ này.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Đóng xác minh VNPT' })).toBeDisabled();
  });
});
