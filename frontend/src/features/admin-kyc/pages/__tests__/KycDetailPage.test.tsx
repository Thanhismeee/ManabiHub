import { describe, it, expect, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { KycDetailPage } from '../KycDetailPage';
import { adminKycService } from '../../services/adminKycService';

// Mock the service
vi.mock('../../services/adminKycService', () => ({
  adminKycService: {
    getKycDetail: vi.fn(),
    getDocumentObjectUrl: vi.fn(),
    reviewKyc: vi.fn(),
  },
  KYC_STATUS_LABELS: {
    APPROVED: 'Đã phê duyệt',
    PENDING: 'Chờ duyệt',
    REJECTED: 'Bị từ chối',
    CORRECTION_REQUIRED: 'Yêu cầu cập nhật',
    REVOKED: 'Đã thu hồi',
  },
}));

describe('KycDetailPage Edge Cases', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  const renderComponent = (id: string = '123') => {
    return render(
      <MemoryRouter initialEntries={[`/admin/kyc/${id}`]}>
        <Routes>
          <Route path="/admin/kyc/:id" element={<KycDetailPage />} />
        </Routes>
      </MemoryRouter>
    );
  };

  it('shows UNAUTHORIZED error when 401 is encountered', async () => {
    vi.mocked(adminKycService.getKycDetail).mockRejectedValue({
      response: { status: 401 }
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Không tìm thấy hồ sơ')).toBeInTheDocument();
      expect(screen.getByText('Không thể tải hồ sơ KYC. Vui lòng kiểm tra phiên đăng nhập và backend.')).toBeInTheDocument();
    });
  });

  it('shows error state when 403 Forbidden is encountered', async () => {
    vi.mocked(adminKycService.getKycDetail).mockRejectedValue({
      response: { status: 403 }
    });

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText('Không tìm thấy hồ sơ')).toBeInTheDocument();
      expect(screen.getByText('Không thể tải hồ sơ KYC. Vui lòng kiểm tra phiên đăng nhập và backend.')).toBeInTheDocument();
    });
  });

  it('shows information banner for terminal state (APPROVED)', async () => {
    vi.mocked(adminKycService.getKycDetail).mockResolvedValue({
      id: '123',
      teacherId: 't1',
      teacherFullName: 'Test User',
      teacherEmail: 'test@example.com',
      status: 'APPROVED',
      processedByEmail: 'admin@manabi.com',
      decisionNote: 'Looks good',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    } as any);

    renderComponent();

    await waitFor(() => {
      expect(screen.getByText(/Hồ sơ đã được xử lý bởi/)).toBeInTheDocument();
      expect(screen.getByText(/admin@manabi.com/)).toBeInTheDocument();
      expect(screen.getByText(/Ghi chú: Looks good/)).toBeInTheDocument();
      expect(screen.getByText(/Thu hồi sau duyệt chỉ được thực hiện từ trust case đã xác minh/)).toBeInTheDocument();
    });
  });

  it('shows error on duplicate certificate (409) during review', async () => {
    vi.mocked(adminKycService.getKycDetail).mockResolvedValue({
      id: '123',
      teacherId: 't1',
      teacherFullName: 'Test User',
      teacherEmail: 'test@example.com',
      status: 'PENDING',
      vnptVerificationStatus: 'SDK_VERIFIED',
      exceptionStage: 'CERTIFICATE',
      exceptionType: 'JLPT_AUTHENTICITY_CHECK',
      certificateUrl: 'file-123',
      certificateHolderName: 'TEST USER',
      certificateDateOfBirth: '1990-01-01',
      certificateLevel: 'N1',
      certificateCode: 'JLPT-123',
      certificateOcrText: 'N1 1990-01-01 TEST USER',
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
    } as any);

    vi.mocked(adminKycService.getDocumentObjectUrl).mockResolvedValue('blob:url');
    
    // Mock review to fail with 409
    vi.mocked(adminKycService.reviewKyc).mockRejectedValue({
      response: { status: 409, data: { messageCode: 'DUPLICATE_CERTIFICATE' } }
    });

    renderComponent();

    // Wait for the page and image to load
    await waitFor(() => {
      const button = screen.getByText('Xác nhận chứng chỉ thật').closest('button');
      expect(button).not.toBeDisabled();
    });

    const approveButton = screen.getByText('Xác nhận chứng chỉ thật');
    approveButton.click();

    // Wait for confirmation dialog
    await waitFor(() => {
      expect(screen.getByText('Xác nhận quyết định')).toBeInTheDocument();
    });

    const confirmButton = screen.getByRole('button', { name: 'Xác nhận' });
    confirmButton.click();

    // Verify error message
    await waitFor(() => {
      expect(screen.getByText('Không thể lưu quyết định. Hồ sơ có thể đã được người khác xử lý hoặc chưa đủ điều kiện.')).toBeInTheDocument();
    });
  });
});
