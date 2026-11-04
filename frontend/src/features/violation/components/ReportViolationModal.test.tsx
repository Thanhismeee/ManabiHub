import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { submitViolationReport } from '../api/violationApi';
import { ReportViolationModal } from './ReportViolationModal';

vi.mock('../api/violationApi', () => ({ submitViolationReport: vi.fn() }));
vi.mock('react-hot-toast', () => ({ toast: { success: vi.fn(), error: vi.fn() } }));

const renderDialog = () => {
  const queryClient = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  return render(
    <QueryClientProvider client={queryClient}>
      <ReportViolationModal
        open
        onClose={vi.fn()}
        targetType="COURSE"
        targetId="3d7deffc-8782-40cd-9263-419f6e074983"
      />
    </QueryClientProvider>,
  );
};

const goToDetails = async (reason = 'Nội dung không đúng với mô tả') => {
  fireEvent.click(screen.getByLabelText(reason));
  fireEvent.click(screen.getByRole('button', { name: 'Tiếp tục' }));
  expect(await screen.findByLabelText(/Mô tả chi tiết/)).toBeInTheDocument();
};

describe('ReportViolationModal', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(submitViolationReport).mockResolvedValue({});
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:local-evidence-preview');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
  });

  afterEach(() => {
    cleanup();
    vi.restoreAllMocks();
  });

  it('chuyển từ bước chọn lý do sang bước nhập mô tả và bằng chứng', async () => {
    renderDialog();
    expect(screen.getByText('Bước 1/2 · Chọn lý do')).toBeInTheDocument();

    await goToDetails();

    expect(screen.getByText('Bước 2/2 · Cung cấp thông tin')).toBeInTheDocument();
    expect(screen.getByText('Chọn tệp bằng chứng')).toBeInTheDocument();
  });

  it('gửi lý do, mô tả và tệp bằng chứng đã chọn', async () => {
    renderDialog();
    await goToDetails('Nội dung có dấu hiệu vi phạm bản quyền');

    fireEvent.change(screen.getByLabelText(/Mô tả chi tiết/), {
      target: { value: 'Video trong bài học sử dụng nội dung có bản quyền ở phút thứ ba.' },
    });
    const evidence = new File(['%PDF-test'], 'bang-chung.pdf', { type: 'application/pdf' });
    fireEvent.change(screen.getByTestId('evidence-input'), { target: { files: [evidence] } });
    expect(await screen.findByText('bang-chung.pdf')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Gửi báo cáo' }));

    await waitFor(() => {
      expect(submitViolationReport).toHaveBeenCalledWith(
        {
          targetType: 'COURSE',
          targetId: '3d7deffc-8782-40cd-9263-419f6e074983',
          reason: 'Nội dung có dấu hiệu vi phạm bản quyền',
          description: 'Video trong bài học sử dụng nội dung có bản quyền ở phút thứ ba.',
        },
        [evidence],
      );
    });
  });

  it('yêu cầu mô tả chi tiết tối thiểu 10 ký tự', async () => {
    renderDialog();
    await goToDetails('Lý do khác');

    fireEvent.change(screen.getByLabelText(/Mô tả chi tiết/), { target: { value: 'Quá ngắn' } });
    fireEvent.click(screen.getByRole('button', { name: 'Gửi báo cáo' }));

    expect(await screen.findByText('Mô tả cần có ít nhất 10 ký tự.')).toBeInTheDocument();
    expect(submitViolationReport).not.toHaveBeenCalled();
  });

  it('từ chối tệp không đúng định dạng trước khi gửi', async () => {
    renderDialog();
    await goToDetails();

    const invalidFile = new File(['plain text'], 'ghi-chu.txt', { type: 'text/plain' });
    fireEvent.change(screen.getByTestId('evidence-input'), { target: { files: [invalidFile] } });

    expect(await screen.findByText(/không đúng định dạng PDF, PNG hoặc JPEG/)).toBeInTheDocument();
    expect(screen.queryByText('ghi-chu.txt', { selector: 'p' })).not.toBeInTheDocument();
  });

  it('cho phép xem trước và phóng to ảnh bằng chứng trước khi gửi', async () => {
    renderDialog();
    await goToDetails();

    const image = new File(['image-content'], 'anh-bang-chung.png', { type: 'image/png' });
    fireEvent.change(screen.getByTestId('evidence-input'), { target: { files: [image] } });

    fireEvent.click(await screen.findByRole('button', { name: 'Xem ảnh anh-bang-chung.png' }));
    expect(await screen.findByAltText('anh-bang-chung.png')).toBeInTheDocument();
    expect(screen.getByText('100%')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Phóng to ảnh' }));
    expect(screen.getByText('125%')).toBeInTheDocument();
  });
});
