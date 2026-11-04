import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminViolationService } from '../services/adminViolationService';
import type { ViolationEvidence } from '../types/violation.types';
import { EvidencePanel } from './EvidencePanel';

vi.mock('../services/adminViolationService', () => ({
  adminViolationService: {
    loadEvidencePreview: vi.fn(),
    downloadEvidence: vi.fn(),
  },
}));

vi.mock('react-hot-toast', () => ({ toast: { error: vi.fn() } }));

const imageEvidence: ViolationEvidence = {
  evidenceId: 'evidence-1',
  evidenceType: 'IMAGE',
  displayName: 'anh-bang-chung.png',
  accessUrl: '/v1/admin/violations/report-1/evidence/evidence-1',
  contentType: 'image/png',
  submittedAt: '2026-08-10T07:00:00Z',
};

describe('EvidencePanel', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(adminViolationService.loadEvidencePreview).mockResolvedValue({
      url: 'blob:admin-evidence-preview',
      shouldRevoke: false,
    });
  });

  afterEach(cleanup);

  it('tải ảnh bằng quyền admin, hiển thị thumbnail và mở chế độ phóng to', async () => {
    render(<EvidencePanel evidence={[imageEvidence]} />);

    await waitFor(() => {
      expect(adminViolationService.loadEvidencePreview).toHaveBeenCalledWith(imageEvidence.accessUrl);
    });
    const thumbnail = await screen.findByAltText('Bằng chứng anh-bang-chung.png');
    expect(thumbnail).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Xem ảnh anh-bang-chung.png' }));
    expect(await screen.findByAltText('anh-bang-chung.png')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Phóng to ảnh' }));
    expect(screen.getByText('125%')).toBeInTheDocument();
  });
});
