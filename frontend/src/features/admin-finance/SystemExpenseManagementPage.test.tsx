import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { adminFinanceApi } from './adminFinanceApi';
import { SystemExpenseManagementPage } from './SystemExpenseManagementPage';
import type { ExpenseOverview, ExpenseSummary } from './types';

vi.mock('./adminFinanceApi', () => ({
  adminFinanceApi: {
    searchExpenses: vi.fn(),
    getExpenseOverview: vi.fn(),
    getExpense: vi.fn(),
    createExpense: vi.fn(),
    updateExpense: vi.fn(),
    confirmExpense: vi.fn(),
    markExpensePaid: vi.fn(),
    voidExpense: vi.fn(),
  },
}));

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

beforeEach(() => {
  vi.mocked(adminFinanceApi.searchExpenses).mockResolvedValue(page([]));
  vi.mocked(adminFinanceApi.getExpenseOverview).mockResolvedValue(overview());
});

describe('SystemExpenseManagementPage', () => {
  it('distinguishes a true empty state and starts a foreign-currency invoice without unsafe defaults', async () => {
    render(
      <MemoryRouter>
        <SystemExpenseManagementPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText('Chưa có chứng từ trong kỳ đang xem')).toBeInTheDocument();
    fireEvent.click(screen.getByText('Thêm chứng từ đầu tiên'));

    expect(screen.getByText('Thêm chứng từ chi phí')).toBeInTheDocument();
    expect(screen.getByLabelText(/Loại chi phí/)).toHaveValue('');
    expect(screen.queryByText('OTHER_OPERATIONAL')).not.toBeInTheDocument();

    fireEvent.change(screen.getByLabelText(/Tiền tệ/), { target: { value: 'USD' } });

    expect(screen.getByLabelText(/Tỷ giá sang VND/)).toHaveValue(null);
    expect(screen.getByLabelText(/Ngày tỷ giá/)).toBeRequired();
    expect(screen.getByLabelText(/Nguồn tỷ giá/)).toBeRequired();
    expect(screen.getByText('Không tự mặc định bằng 1.')).toBeInTheDocument();
    expect(screen.getByText('Tổng quy đổi VND:').parentElement).toHaveTextContent('Chưa xác định tỷ giá');
  }, 20_000);

  it('retains the last successful page and labels it stale when refresh fails', async () => {
    vi.mocked(adminFinanceApi.searchExpenses)
      .mockResolvedValueOnce(page([expense()]))
      .mockRejectedValueOnce(new Error('offline'));

    render(
      <MemoryRouter>
        <SystemExpenseManagementPage />
      </MemoryRouter>,
    );

    expect(await screen.findByText('EXP-202608-001')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Làm mới' }));

    expect(await screen.findByText(/Dữ liệu đang hiển thị, nếu có, là lần tải gần nhất/)).toBeInTheDocument();
    expect(screen.getByText('EXP-202608-001')).toBeInTheDocument();
  });
});

function page(content: ExpenseSummary[]) {
  return {
    content,
    page: 0,
    size: 10,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    first: true,
    last: true,
  };
}

function overview(): ExpenseOverview {
  return {
    from: '2026-08-01',
    to: '2026-08-26',
    totalDocuments: 0,
    totalConfirmedVnd: 0,
    draftCount: 0,
    confirmedCount: 0,
    paidCount: 0,
    overdueCount: 0,
    generatedAt: '2026-08-26T02:30:00Z',
  };
}

function expense(): ExpenseSummary {
  return {
    id: 'expense-1',
    expenseCode: 'EXP-202608-001',
    vendorName: 'Amazon Web Services',
    providerCode: 'AWS',
    invoiceNumber: 'INV-001',
    currency: 'USD',
    originalTotal: 10,
    totalAmountVnd: 250_000,
    incurredAt: '2026-08-20',
    dueDate: '2026-09-01',
    status: 'DRAFT',
    sourceType: 'MANUAL_INVOICE',
    lineCount: 1,
    createdBy: 'admin-1',
    createdAt: '2026-08-20T03:00:00Z',
    updatedAt: '2026-08-20T03:00:00Z',
  };
}
