import { axiosClient } from '../../shared/api/axiosClient';
import type { ApiResponse, PageResponse } from '../../shared/types/api';
import type {
  ExpenseDetail,
  ExpenseFilters,
  ExpenseOverview,
  ExpensePayload,
  ExpenseSummary,
  RevenueDashboard,
  RevenueGranularity,
} from './types';

const REVENUE_BASE = '/v1/admin/finance/revenue';
const EXPENSE_BASE = '/v1/admin/finance/expenses';

export const adminFinanceApi = {
  getRevenueDashboard: async (params: {
    from?: string;
    to?: string;
    granularity?: RevenueGranularity;
  }): Promise<RevenueDashboard> => {
    const response = await axiosClient.get<ApiResponse<RevenueDashboard>>(`${REVENUE_BASE}/dashboard`, { params });
    return response.data.data;
  },

  searchExpenses: async (filters: ExpenseFilters): Promise<PageResponse<ExpenseSummary>> => {
    const response = await axiosClient.get<ApiResponse<PageResponse<ExpenseSummary>>>(EXPENSE_BASE, {
      params: { ...filters, sort: 'incurredAt,desc' },
    });
    return response.data.data;
  },

  getExpenseOverview: async (from?: string, to?: string): Promise<ExpenseOverview> => {
    const response = await axiosClient.get<ApiResponse<ExpenseOverview>>(`${EXPENSE_BASE}/overview`, {
      params: { from, to },
    });
    return response.data.data;
  },

  getExpense: async (id: string): Promise<ExpenseDetail> => {
    const response = await axiosClient.get<ApiResponse<ExpenseDetail>>(`${EXPENSE_BASE}/${id}`);
    return response.data.data;
  },

  createExpense: async (payload: ExpensePayload): Promise<ExpenseDetail> => {
    const response = await axiosClient.post<ApiResponse<ExpenseDetail>>(EXPENSE_BASE, payload);
    return response.data.data;
  },

  updateExpense: async (id: string, payload: ExpensePayload): Promise<ExpenseDetail> => {
    const response = await axiosClient.put<ApiResponse<ExpenseDetail>>(`${EXPENSE_BASE}/${id}`, payload);
    return response.data.data;
  },

  confirmExpense: async (id: string): Promise<ExpenseDetail> => {
    const response = await axiosClient.post<ApiResponse<ExpenseDetail>>(`${EXPENSE_BASE}/${id}/confirm`);
    return response.data.data;
  },

  markExpensePaid: async (id: string): Promise<ExpenseDetail> => {
    const response = await axiosClient.post<ApiResponse<ExpenseDetail>>(`${EXPENSE_BASE}/${id}/paid`);
    return response.data.data;
  },

  voidExpense: async (id: string, reason: string): Promise<ExpenseDetail> => {
    const response = await axiosClient.post<ApiResponse<ExpenseDetail>>(`${EXPENSE_BASE}/${id}/void`, { reason });
    return response.data.data;
  },
};
