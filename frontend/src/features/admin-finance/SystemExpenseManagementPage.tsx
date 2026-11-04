import { useCallback, useEffect, useMemo, useState, type Dispatch, type SetStateAction } from 'react';
import { isAxiosError } from 'axios';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  IconButton,
  MenuItem,
  Paper,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TablePagination,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import RefreshIcon from '@mui/icons-material/Refresh';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import { useParams } from 'react-router-dom';
import { adminFinanceApi } from './adminFinanceApi';
import {
  EXPENSE_CATEGORY_OPTIONS,
  EXPENSE_SOURCE_LABELS,
  EXPENSE_STATUS_LABELS,
  expenseCategoryLabel,
} from './expenseCatalog';
import {
  FINANCE_TIME_ZONE,
  formatDateRange,
  formatFinanceDate,
  formatFinanceDateTime,
  formatMoney,
  quickDateRange,
  reportingDateInputValue,
  reportingMonthRange,
} from './financeDisplay';
import type {
  ExpenseCategory,
  ExpenseDetail,
  ExpenseFilters,
  ExpenseLinePayload,
  ExpenseOverview,
  ExpensePayload,
  ExpenseSourceType,
  ExpenseStatus,
  ExpenseSummary,
} from './types';

interface ExpenseLineDraft extends Omit<ExpenseLinePayload, 'categoryCode'> {
  categoryCode: ExpenseCategory | '';
}

interface ExpenseDraft extends Omit<ExpensePayload, 'lines'> {
  lines: ExpenseLineDraft[];
}

type ExpenseAction = 'confirm' | 'paid';

const defaultPeriod = reportingMonthRange(new Date());
const initialFilters: ExpenseFilters = {
  page: 0,
  size: 10,
  status: '',
  category: '',
  keyword: '',
  vendor: '',
  providerCode: '',
  invoiceNumber: '',
  createdBy: '',
  minAmountVnd: '',
  maxAmountVnd: '',
  incurredFrom: defaultPeriod.from,
  incurredTo: defaultPeriod.to,
};

function emptyDraft(): ExpenseDraft {
  return {
    vendorName: '',
    providerCode: '',
    invoiceNumber: '',
    description: '',
    currency: 'VND',
    exchangeRate: 1,
    incurredAt: reportingDateInputValue(new Date()),
    dueDate: '',
    exchangeRateDate: '',
    exchangeRateSource: '',
    billingPeriodFrom: '',
    billingPeriodTo: '',
    evidenceReference: '',
    sourceType: 'MANUAL_INVOICE',
    lines: [{ categoryCode: '', description: '', originalAmount: '' }],
  };
}

export function SystemExpenseManagementPage() {
  const { id: routeExpenseId } = useParams<{ id: string }>();
  const [filters, setFilters] = useState<ExpenseFilters>(initialFilters);
  const [draftFilters, setDraftFilters] = useState<ExpenseFilters>(initialFilters);
  const [items, setItems] = useState<ExpenseSummary[]>([]);
  const [overview, setOverview] = useState<ExpenseOverview | null>(null);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(true);
  const [overviewLoading, setOverviewLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [overviewError, setOverviewError] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [detail, setDetail] = useState<ExpenseDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [form, setForm] = useState<ExpenseDraft>(emptyDraft());
  const [formBaseline, setFormBaseline] = useState('');
  const [formError, setFormError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [discardConfirmOpen, setDiscardConfirmOpen] = useState(false);
  const [pendingAction, setPendingAction] = useState<ExpenseAction | null>(null);
  const [actionError, setActionError] = useState<string | null>(null);
  const [voidDialogOpen, setVoidDialogOpen] = useState(false);
  const [voidReason, setVoidReason] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    setOverviewLoading(true);
    setError(null);
    setOverviewError(false);
    const [pageResult, overviewResult] = await Promise.allSettled([
      adminFinanceApi.searchExpenses(filters),
      adminFinanceApi.getExpenseOverview(filters.incurredFrom, filters.incurredTo),
    ]);
    if (pageResult.status === 'fulfilled') {
      setItems(pageResult.value.content);
      setTotal(pageResult.value.totalElements);
    } else {
      setError('Không thể tải danh sách chứng từ. Dữ liệu đang hiển thị, nếu có, là lần tải gần nhất.');
    }
    if (overviewResult.status === 'fulfilled') {
      setOverview(overviewResult.value);
    } else {
      setOverviewError(true);
    }
    setLoading(false);
    setOverviewLoading(false);
  }, [filters]);

  useEffect(() => { void load(); }, [load]);

  const openDetail = useCallback(async (id: string) => {
    setDetailLoading(true);
    setError(null);
    try {
      setDetail(await adminFinanceApi.getExpense(id));
    } catch (requestError) {
      setError(apiErrorMessage(requestError, 'Không thể tải chi tiết chứng từ chi phí.'));
    } finally {
      setDetailLoading(false);
    }
  }, []);

  useEffect(() => {
    if (routeExpenseId) void openDetail(routeExpenseId);
  }, [openDetail, routeExpenseId]);

  const beginForm = (next: ExpenseDraft) => {
    setForm(next);
    setFormBaseline(JSON.stringify(next));
    setFormError(null);
    setFormOpen(true);
  };

  const openCreate = () => {
    setDetail(null);
    beginForm(emptyDraft());
  };

  const openEdit = (expense: ExpenseDetail) => {
    beginForm({
      version: expense.version,
      vendorName: expense.vendorName,
      providerCode: expense.providerCode ?? '',
      invoiceNumber: expense.invoiceNumber ?? '',
      description: expense.description ?? '',
      currency: expense.currency,
      exchangeRate: expense.exchangeRate,
      incurredAt: expense.incurredAt,
      dueDate: expense.dueDate ?? '',
      exchangeRateDate: expense.exchangeRateDate ?? '',
      exchangeRateSource: expense.exchangeRateSource ?? '',
      billingPeriodFrom: expense.billingPeriodFrom ?? '',
      billingPeriodTo: expense.billingPeriodTo ?? '',
      evidenceReference: expense.evidenceReference ?? '',
      sourceType: expense.sourceType,
      lines: expense.lines.map((line) => ({
        categoryCode: line.categoryCode,
        description: line.description,
        originalAmount: line.originalAmount,
      })),
    });
  };

  const closeForm = () => {
    if (JSON.stringify(form) !== formBaseline) {
      setDiscardConfirmOpen(true);
      return;
    }
    setFormOpen(false);
  };

  const duplicateOnCurrentPage = useMemo(() => {
    const provider = form.providerCode?.trim().toLocaleLowerCase('vi-VN');
    const invoice = form.invoiceNumber?.trim().toLocaleLowerCase('vi-VN');
    if (!provider || !invoice || !form.incurredAt) return null;
    return items.find((item) => item.id !== detail?.id
      && item.providerCode?.trim().toLocaleLowerCase('vi-VN') === provider
      && item.invoiceNumber?.trim().toLocaleLowerCase('vi-VN') === invoice
      && item.incurredAt === form.incurredAt) ?? null;
  }, [detail?.id, form.incurredAt, form.invoiceNumber, form.providerCode, items]);
  const filterError = useMemo(() => validateExpenseFilters(draftFilters), [draftFilters]);

  const saveDraft = async (confirmAfterSave: boolean) => {
    const validationError = validateExpenseDraft(form);
    if (validationError) {
      setFormError(validationError);
      return;
    }
    setSaving(true);
    setFormError(null);
    let saved: ExpenseDetail | null = null;
    try {
      const payload = toExpensePayload(form);
      saved = detail?.status === 'DRAFT'
        ? await adminFinanceApi.updateExpense(detail.id, payload)
        : await adminFinanceApi.createExpense(payload);
      if (confirmAfterSave) {
        saved = await adminFinanceApi.confirmExpense(saved.id);
      }
      setDetail(saved);
      setFormOpen(false);
      setMessage(confirmAfterSave
        ? 'Đã lưu và gửi chứng từ sang trạng thái đã duyệt, chờ thanh toán.'
        : 'Đã lưu chứng từ ở trạng thái nháp.');
      await load();
    } catch (requestError) {
      if (saved && confirmAfterSave) {
        setDetail(saved);
        setFormOpen(false);
        setError('Chứng từ đã được lưu nháp nhưng chưa thể gửi duyệt. Hãy mở lại chi tiết và thử xác nhận.');
        await load();
      } else {
        setFormError(apiErrorMessage(
          requestError,
          'Không thể lưu chứng từ. Hãy kiểm tra dữ liệu, phiên bản và bộ ba nhà cung cấp / số hóa đơn / ngày hóa đơn.',
        ));
      }
    } finally {
      setSaving(false);
    }
  };

  const runAction = async (action: ExpenseAction) => {
    if (!detail) return;
    setSaving(true);
    setActionError(null);
    try {
      const next = action === 'confirm'
        ? await adminFinanceApi.confirmExpense(detail.id)
        : await adminFinanceApi.markExpensePaid(detail.id);
      setDetail(next);
      setPendingAction(null);
      setMessage(action === 'confirm'
        ? 'Đã duyệt chứng từ và ghi người duyệt vào audit.'
        : 'Đã ghi nhận chứng từ được thanh toán.');
      await load();
    } catch (requestError) {
      setActionError(apiErrorMessage(requestError, 'Không thể chuyển trạng thái. Dữ liệu có thể đã được người khác cập nhật.'));
    } finally {
      setSaving(false);
    }
  };

  const voidExpense = async () => {
    if (!detail || voidReason.trim().length < 5) return;
    setSaving(true);
    setActionError(null);
    try {
      const next = await adminFinanceApi.voidExpense(detail.id, voidReason.trim());
      setDetail(next);
      setVoidDialogOpen(false);
      setVoidReason('');
      setMessage('Đã vô hiệu chứng từ; dữ liệu và lý do vẫn được giữ phục vụ kiểm toán.');
      await load();
    } catch (requestError) {
      setActionError(apiErrorMessage(requestError, 'Không thể vô hiệu chứng từ.'));
    } finally {
      setSaving(false);
    }
  };

  const hasSpecificFilters = Boolean(
    filters.status || filters.category || filters.keyword || filters.vendor || filters.providerCode
    || filters.invoiceNumber || filters.createdBy || filters.minAmountVnd || filters.maxAmountVnd
    || filters.incurredFrom !== initialFilters.incurredFrom
    || filters.incurredTo !== initialFilters.incurredTo,
  );

  return (
    <Box sx={{ p: { xs: 1, md: 2 } }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', gap: 2, mb: 3 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 800 }}>Chứng từ chi phí</Typography>
          <Typography color="text.secondary">
            Một hóa đơn tương ứng một chứng từ và có thể gồm nhiều dòng chi phí thành phần.
          </Typography>
        </Box>
        <Stack direction="row" sx={{ gap: 1 }}>
          <Button variant="outlined" startIcon={<RefreshIcon />} onClick={() => void load()} disabled={loading}>Làm mới</Button>
          <Button variant="contained" startIcon={<AddIcon />} onClick={openCreate}>Thêm chứng từ</Button>
        </Stack>
      </Stack>

      {error && <Alert severity={items.length ? 'warning' : 'error'} onClose={() => setError(null)} sx={{ mb: 2 }}>{error}</Alert>}
      {message && <Alert severity="success" onClose={() => setMessage(null)} sx={{ mb: 2 }}>{message}</Alert>}

      <ExpenseOverviewCards overview={overview} loading={overviewLoading} unavailable={overviewError} />

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Stack sx={{ gap: 2 }}>
          <Stack direction="row" sx={{ gap: 1, flexWrap: 'wrap' }}>
            <Button size="small" onClick={() => applyExpenseQuickRange('TODAY', setDraftFilters)}>Hôm nay</Button>
            <Button size="small" onClick={() => applyExpenseQuickRange('WEEK', setDraftFilters)}>Tuần này</Button>
            <Button size="small" onClick={() => applyExpenseQuickRange('MONTH', setDraftFilters)}>Tháng này</Button>
            <Button size="small" onClick={() => applyExpenseQuickRange('QUARTER', setDraftFilters)}>Quý này</Button>
            <Typography variant="caption" color="text.secondary" sx={{ alignSelf: 'center', ml: 1 }}>
              Múi giờ: {FINANCE_TIME_ZONE} · Cập nhật: {overview ? formatFinanceDateTime(overview.generatedAt) : 'Chưa đồng bộ'}
            </Typography>
          </Stack>
          <Grid container spacing={1.5}>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField fullWidth size="small" label="Mã chứng từ / từ khóa" value={draftFilters.keyword} onChange={(event) => setDraftFilters({ ...draftFilters, keyword: event.target.value })} />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField fullWidth size="small" label="Nhà cung cấp" value={draftFilters.vendor} onChange={(event) => setDraftFilters({ ...draftFilters, vendor: event.target.value })} />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField fullWidth size="small" label="Mã nhà cung cấp" value={draftFilters.providerCode} onChange={(event) => setDraftFilters({ ...draftFilters, providerCode: event.target.value })} />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField fullWidth size="small" label="Số hóa đơn" value={draftFilters.invoiceNumber} onChange={(event) => setDraftFilters({ ...draftFilters, invoiceNumber: event.target.value })} />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField select fullWidth size="small" label="Trạng thái chứng từ" value={draftFilters.status} onChange={(event) => setDraftFilters({ ...draftFilters, status: event.target.value as ExpenseFilters['status'] })}>
                <MenuItem value="">Tất cả trạng thái</MenuItem>
                {Object.entries(EXPENSE_STATUS_LABELS).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <Autocomplete
                size="small"
                options={EXPENSE_CATEGORY_OPTIONS}
                groupBy={(option) => option.group}
                getOptionLabel={(option) => option.label}
                value={EXPENSE_CATEGORY_OPTIONS.find((option) => option.value === draftFilters.category) ?? null}
                onChange={(_, option) => setDraftFilters({ ...draftFilters, category: option?.value ?? '' })}
                renderInput={(params) => <TextField {...params} label="Nhóm chi phí" />}
              />
            </Grid>
            <Grid size={{ xs: 6, md: 1.5 }}>
              <TextField fullWidth size="small" type="date" label="Từ ngày" value={draftFilters.incurredFrom} onChange={(event) => setDraftFilters({ ...draftFilters, incurredFrom: event.target.value })} slotProps={{ inputLabel: { shrink: true } }} error={Boolean(filterError?.includes('ngày'))} />
            </Grid>
            <Grid size={{ xs: 6, md: 1.5 }}>
              <TextField fullWidth size="small" type="date" label="Đến ngày" value={draftFilters.incurredTo} onChange={(event) => setDraftFilters({ ...draftFilters, incurredTo: event.target.value })} slotProps={{ inputLabel: { shrink: true } }} error={Boolean(filterError?.includes('ngày'))} />
            </Grid>
            <Grid size={{ xs: 6, md: 3 }}>
              <TextField fullWidth size="small" type="number" label="Tổng VND từ" value={draftFilters.minAmountVnd} onChange={(event) => setDraftFilters({ ...draftFilters, minAmountVnd: event.target.value })} slotProps={{ htmlInput: { min: 0 } }} />
            </Grid>
            <Grid size={{ xs: 6, md: 3 }}>
              <TextField fullWidth size="small" type="number" label="Tổng VND đến" value={draftFilters.maxAmountVnd} onChange={(event) => setDraftFilters({ ...draftFilters, maxAmountVnd: event.target.value })} slotProps={{ htmlInput: { min: 0 } }} />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField fullWidth size="small" label="Mã người tạo" value={draftFilters.createdBy} onChange={(event) => setDraftFilters({ ...draftFilters, createdBy: event.target.value })} />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <Stack direction="row" sx={{ gap: 1, justifyContent: { md: 'flex-end' } }}>
                <Button onClick={() => { setDraftFilters(initialFilters); setFilters(initialFilters); }}>Xóa lọc</Button>
                <Button variant="contained" disabled={Boolean(filterError)} onClick={() => setFilters({ ...draftFilters, page: 0 })}>Áp dụng</Button>
              </Stack>
            </Grid>
          </Grid>
          {filterError && <Alert severity="warning">{filterError}</Alert>}
        </Stack>
      </Paper>

      <TableContainer component={Paper} variant="outlined">
        <Table sx={{ minWidth: 1180 }}>
          <TableHead>
            <TableRow sx={{ bgcolor: '#f8fafc' }}>
              <TableCell>Mã / ngày chứng từ</TableCell>
              <TableCell>Nhà cung cấp / hóa đơn</TableCell>
              <TableCell align="right">Nguyên tệ / tỷ giá</TableCell>
              <TableCell align="right">Tổng VND</TableCell>
              <TableCell>Hạn / thanh toán</TableCell>
              <TableCell>Trạng thái chứng từ</TableCell>
              <TableCell>Người tạo / cập nhật</TableCell>
              <TableCell align="right">Thao tác</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading && items.length === 0
              ? Array.from({ length: 5 }, (_, index) => (
                  <TableRow key={index}>{Array.from({ length: 8 }, (__, cell) => <TableCell key={cell}><Skeleton /></TableCell>)}</TableRow>
                ))
              : items.map((item) => (
                  <TableRow key={item.id} hover>
                    <TableCell>
                      <Typography sx={{ fontWeight: 700 }}>{item.expenseCode}</Typography>
                      <Typography variant="caption" color="text.secondary">{formatFinanceDate(item.incurredAt)} · {item.lineCount} dòng</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 650 }}>{item.vendorName}</Typography>
                      <Typography variant="caption" color="text.secondary">{item.invoiceNumber || 'Chưa có số hóa đơn'}{item.providerCode ? ` · ${item.providerCode}` : ''}</Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Typography variant="body2">{formatMoney(item.originalTotal, item.currency)}</Typography>
                      <Typography variant="caption" color="text.secondary">{item.currency}</Typography>
                    </TableCell>
                    <TableCell align="right"><Typography sx={{ fontWeight: 750 }}>{formatMoney(item.totalAmountVnd)}</Typography></TableCell>
                    <TableCell>
                      <Typography variant="body2">{item.dueDate ? formatFinanceDate(item.dueDate) : 'Chưa ghi nhận hạn'}</Typography>
                      <Typography variant="caption" color={item.status === 'PAID' ? 'success.main' : 'text.secondary'}>{paymentStatusLabel(item.status)}</Typography>
                    </TableCell>
                    <TableCell><ExpenseStatusChip status={item.status} /></TableCell>
                    <TableCell>
                      <Typography variant="caption" sx={{ display: 'block' }}>{shortId(item.createdBy)}</Typography>
                      <Typography variant="caption" color="text.secondary">{formatFinanceDateTime(item.updatedAt)}</Typography>
                    </TableCell>
                    <TableCell align="right">
                      <Button size="small" startIcon={<VisibilityOutlinedIcon />} onClick={() => void openDetail(item.id)}>Chi tiết</Button>
                    </TableCell>
                  </TableRow>
                ))}
            {!loading && items.length === 0 && (
              <TableRow>
                <TableCell colSpan={8}>
                  <Box sx={{ py: 7, textAlign: 'center' }}>
                    <Typography sx={{ fontWeight: 750 }}>
                      {hasSpecificFilters ? 'Không có chứng từ khớp bộ lọc' : 'Chưa có chứng từ trong kỳ đang xem'}
                    </Typography>
                    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                      {hasSpecificFilters ? 'Hãy thay đổi hoặc xóa bớt điều kiện lọc.' : `Khoảng ${formatDateRange(filters.incurredFrom ?? defaultPeriod.from, filters.incurredTo ?? defaultPeriod.to)}.`}
                    </Typography>
                    {!hasSpecificFilters && <Button sx={{ mt: 2 }} variant="contained" startIcon={<AddIcon />} onClick={openCreate}>Thêm chứng từ đầu tiên</Button>}
                  </Box>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
        <TablePagination
          component="div"
          count={total}
          page={filters.page}
          rowsPerPage={filters.size}
          onPageChange={(_, page) => setFilters({ ...filters, page })}
          onRowsPerPageChange={(event) => setFilters({ ...filters, page: 0, size: Number(event.target.value) })}
          rowsPerPageOptions={[10, 20, 50]}
          labelRowsPerPage="Số dòng"
        />
      </TableContainer>

      {detailLoading && <Dialog open><DialogContent><Stack direction="row" sx={{ gap: 2, alignItems: 'center' }}><CircularProgress size={24} />Đang tải chi tiết…</Stack></DialogContent></Dialog>}
      <ExpenseFormDialog
        open={formOpen}
        form={form}
        setForm={setForm}
        error={formError}
        duplicate={duplicateOnCurrentPage}
        onClose={closeForm}
        onSave={saveDraft}
        saving={saving}
        editing={detail?.status === 'DRAFT'}
      />
      <ExpenseDetailDialog
        detail={detail}
        saving={saving}
        onClose={() => setDetail(null)}
        onEdit={openEdit}
        onAction={(action) => { setActionError(null); setPendingAction(action); }}
        onVoid={() => { setActionError(null); setVoidReason(''); setVoidDialogOpen(true); }}
      />

      <Dialog open={discardConfirmOpen} onClose={() => setDiscardConfirmOpen(false)}>
        <DialogTitle>Bỏ các thay đổi chưa lưu?</DialogTitle>
        <DialogContent><Typography color="text.secondary">Dữ liệu vừa nhập sẽ không thể khôi phục.</Typography></DialogContent>
        <DialogActions>
          <Button onClick={() => setDiscardConfirmOpen(false)}>Tiếp tục chỉnh sửa</Button>
          <Button color="error" variant="contained" onClick={() => { setDiscardConfirmOpen(false); setFormOpen(false); }}>Bỏ thay đổi</Button>
        </DialogActions>
      </Dialog>

      <Dialog open={Boolean(pendingAction)} onClose={() => !saving && setPendingAction(null)} maxWidth="sm" fullWidth>
        <DialogTitle>{pendingAction === 'confirm' ? 'Duyệt chứng từ chi phí?' : 'Xác nhận đã thanh toán?'}</DialogTitle>
        <DialogContent>
          {actionError && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
          <Typography>
            {pendingAction === 'confirm'
              ? 'Sau khi duyệt, số tiền này được đưa vào báo cáo chi phí và chứng từ không thể chỉnh sửa.'
              : 'Chỉ xác nhận khi tiền đã thực sự được chuyển; thao tác sẽ được ghi audit.'}
          </Typography>
          {detail && <Alert severity="warning" sx={{ mt: 2 }}>Giá trị quy đổi: <b>{formatMoney(detail.totalAmountVnd)}</b> · Nhà cung cấp: <b>{detail.vendorName}</b></Alert>}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPendingAction(null)} disabled={saving}>Quay lại</Button>
          <Button variant="contained" color={pendingAction === 'paid' ? 'success' : 'primary'} disabled={saving} onClick={() => pendingAction && void runAction(pendingAction)}>
            {saving ? 'Đang xử lý…' : 'Xác nhận'}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={voidDialogOpen} onClose={() => !saving && setVoidDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>Vô hiệu chứng từ?</DialogTitle>
        <DialogContent>
          {actionError && <Alert severity="error" sx={{ mb: 2 }}>{actionError}</Alert>}
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={3}
            label="Lý do vô hiệu"
            value={voidReason}
            onChange={(event) => setVoidReason(event.target.value)}
            error={voidReason.length > 0 && voidReason.trim().length < 5}
            helperText="Bắt buộc, tối thiểu 5 ký tự; lý do được lưu trong audit."
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setVoidDialogOpen(false)} disabled={saving}>Quay lại</Button>
          <Button color="error" variant="contained" disabled={saving || voidReason.trim().length < 5} onClick={() => void voidExpense()}>{saving ? 'Đang xử lý…' : 'Vô hiệu chứng từ'}</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

function ExpenseOverviewCards({ overview, loading, unavailable }: { overview: ExpenseOverview | null; loading: boolean; unavailable: boolean }) {
  const cards = [
    { label: 'Tổng chứng từ', value: overview?.totalDocuments, helper: 'Không gồm chứng từ đã vô hiệu' },
    { label: 'Tổng nguyên tệ', value: null, helper: 'API chưa nhóm tổng theo từng tiền tệ' },
    { label: 'Tổng VND đã duyệt', value: overview ? formatMoney(overview.totalConfirmedVnd) : null, helper: 'Chứng từ đã xác nhận và đã thanh toán trong kỳ' },
    { label: 'Chứng từ nháp', value: overview?.draftCount, helper: 'Chưa đưa vào báo cáo chi phí' },
    { label: 'Đã duyệt, chờ thanh toán', value: overview?.confirmedCount, helper: 'Đã đưa vào báo cáo chi phí' },
    { label: 'Đã thanh toán', value: overview?.paidCount, helper: 'Đã ghi nhận thời điểm thanh toán' },
    { label: 'Quá hạn thanh toán', value: overview?.overdueCount, helper: 'Đã duyệt, quá hạn và chưa thanh toán' },
  ];
  return (
    <Grid container spacing={2} sx={{ mb: 2 }}>
      {cards.map(({ label, value, helper }) => (
        <Grid key={label} size={{ xs: 12, sm: 6, lg: 3 }}>
          <Card variant="outlined" sx={{ height: '100%' }}>
            <CardContent>
              <Typography variant="body2" color="text.secondary">{label}</Typography>
              {loading ? <Skeleton width="70%" height={38} /> : (
                <Typography variant="h6" sx={{ mt: 1, fontWeight: 800 }}>
                  {unavailable || value === null || value === undefined ? 'Chưa đồng bộ' : value}
                </Typography>
              )}
              <Typography variant="caption" color="text.secondary">{helper}</Typography>
            </CardContent>
          </Card>
        </Grid>
      ))}
    </Grid>
  );
}

function ExpenseFormDialog({ open, form, setForm, error, duplicate, onClose, onSave, saving, editing }: {
  open: boolean;
  form: ExpenseDraft;
  setForm: Dispatch<SetStateAction<ExpenseDraft>>;
  error: string | null;
  duplicate: ExpenseSummary | null;
  onClose: () => void;
  onSave: (confirmAfterSave: boolean) => Promise<void>;
  saving: boolean;
  editing: boolean;
}) {
  const updateLine = (index: number, field: keyof ExpenseLineDraft, value: string) => {
    setForm((current) => ({
      ...current,
      lines: current.lines.map((line, lineIndex) => lineIndex === index ? { ...line, [field]: value } : line),
    }));
  };
  const originalTotal = form.lines.reduce((sum, line) => sum + Number(line.originalAmount || 0), 0);
  const convertedTotal = originalTotal * Number(form.exchangeRate || 0);

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="lg">
      <Box component="form" onSubmit={(event) => {
        event.preventDefault();
        const submitter = (event.nativeEvent as SubmitEvent).submitter as HTMLButtonElement | null;
        void onSave(submitter?.value === 'confirm');
      }}>
        <DialogTitle>{editing ? 'Sửa chứng từ nháp' : 'Thêm chứng từ chi phí'}</DialogTitle>
        <DialogContent dividers>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
          {duplicate && <Alert severity="warning" sx={{ mb: 2 }}>Có thể trùng với {duplicate.expenseCode}. Backend sẽ kiểm tra toàn bộ dữ liệu trước khi ghi.</Alert>}
          <Alert severity="info" sx={{ mb: 2 }}>Một hóa đơn chỉ tạo một chứng từ. Tách từng dịch vụ trên hóa đơn thành các dòng chi phí bên dưới.</Alert>
          <Stack sx={{ gap: 2 }}>
            <Grid container spacing={2}>
              <Grid size={{ xs: 12, md: 4 }}><TextField required fullWidth label="Nhà cung cấp" value={form.vendorName} onChange={(event) => setForm((current) => ({ ...current, vendorName: event.target.value }))} /></Grid>
              <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Mã nhà cung cấp" value={form.providerCode} onChange={(event) => setForm((current) => ({ ...current, providerCode: event.target.value }))} helperText="Dùng cùng mã cho cùng một provider để kiểm tra trùng." /></Grid>
              <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label="Số hóa đơn" value={form.invoiceNumber} onChange={(event) => setForm((current) => ({ ...current, invoiceNumber: event.target.value }))} /></Grid>
              <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField required fullWidth type="date" label="Ngày hóa đơn / phát sinh" value={form.incurredAt} onChange={(event) => setForm((current) => ({ ...current, incurredAt: event.target.value }))} slotProps={{ inputLabel: { shrink: true } }} /></Grid>
              <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField fullWidth type="date" label="Hạn thanh toán" value={form.dueDate} onChange={(event) => setForm((current) => ({ ...current, dueDate: event.target.value }))} slotProps={{ inputLabel: { shrink: true } }} /></Grid>
              <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField required fullWidth label="Tiền tệ" value={form.currency} onChange={(event) => {
                const currency = event.target.value.toUpperCase();
                setForm((current) => ({
                  ...current,
                  currency,
                  exchangeRate: currency === 'VND' ? 1 : current.currency === 'VND' ? '' : current.exchangeRate,
                  exchangeRateDate: currency === 'VND' ? '' : current.exchangeRateDate,
                  exchangeRateSource: currency === 'VND' ? '' : current.exchangeRateSource,
                }));
              }} slotProps={{ htmlInput: { maxLength: 10 } }} /></Grid>
              <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField required fullWidth type="number" label="Tỷ giá sang VND" value={form.exchangeRate} disabled={form.currency === 'VND'} onChange={(event) => setForm((current) => ({ ...current, exchangeRate: event.target.value }))} slotProps={{ htmlInput: { min: 0.000001, step: 'any' } }} helperText={form.currency === 'VND' ? 'VND cố định bằng 1.' : 'Không tự mặc định bằng 1.'} /></Grid>
              {form.currency !== 'VND' && <>
                <Grid size={{ xs: 12, md: 3 }}><TextField required fullWidth type="date" label="Ngày tỷ giá" value={form.exchangeRateDate} onChange={(event) => setForm((current) => ({ ...current, exchangeRateDate: event.target.value }))} slotProps={{ inputLabel: { shrink: true } }} /></Grid>
                <Grid size={{ xs: 12, md: 5 }}><TextField required fullWidth label="Nguồn tỷ giá" placeholder="Ví dụ: Vietcombank" value={form.exchangeRateSource} onChange={(event) => setForm((current) => ({ ...current, exchangeRateSource: event.target.value }))} /></Grid>
              </>}
              <Grid size={{ xs: 12, md: 4 }}><TextField select fullWidth label="Nguồn chứng từ" value={form.sourceType} onChange={(event) => setForm((current) => ({ ...current, sourceType: event.target.value as ExpenseSourceType }))}>{Object.entries(EXPENSE_SOURCE_LABELS).map(([value, label]) => <MenuItem key={value} value={value}>{label}</MenuItem>)}</TextField></Grid>
              <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField fullWidth type="date" label="Kỳ dịch vụ từ" value={form.billingPeriodFrom} onChange={(event) => setForm((current) => ({ ...current, billingPeriodFrom: event.target.value }))} slotProps={{ inputLabel: { shrink: true } }} /></Grid>
              <Grid size={{ xs: 12, sm: 6, md: 3 }}><TextField fullWidth type="date" label="Kỳ dịch vụ đến" value={form.billingPeriodTo} onChange={(event) => setForm((current) => ({ ...current, billingPeriodTo: event.target.value }))} slotProps={{ inputLabel: { shrink: true } }} /></Grid>
              <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label="Tham chiếu chứng từ gốc" value={form.evidenceReference} onChange={(event) => setForm((current) => ({ ...current, evidenceReference: event.target.value }))} helperText="URL hoặc mã tham chiếu; backend hiện chưa hỗ trợ tải file trực tiếp." /></Grid>
              <Grid size={{ xs: 12 }}><TextField fullWidth multiline minRows={2} label="Ghi chú" value={form.description} onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} /></Grid>
            </Grid>

            <Typography variant="subtitle1" sx={{ fontWeight: 750 }}>Các dòng chi phí</Typography>
            {form.lines.map((line, index) => (
              <Stack direction={{ xs: 'column', md: 'row' }} sx={{ gap: 1 }} key={index}>
                <Autocomplete
                  options={EXPENSE_CATEGORY_OPTIONS}
                  groupBy={(option) => option.group}
                  getOptionLabel={(option) => option.label}
                  value={EXPENSE_CATEGORY_OPTIONS.find((option) => option.value === line.categoryCode) ?? null}
                  onChange={(_, option) => updateLine(index, 'categoryCode', option?.value ?? '')}
                  renderInput={(params) => <TextField {...params} required label="Loại chi phí" placeholder="Tìm loại chi phí" />}
                  sx={{ minWidth: { md: 280 } }}
                />
                <TextField required label="Mô tả thành phần" value={line.description} onChange={(event) => updateLine(index, 'description', event.target.value)} fullWidth />
                <TextField required label={`Số tiền ${form.currency || 'nguyên tệ'}`} type="number" value={line.originalAmount} onChange={(event) => updateLine(index, 'originalAmount', event.target.value)} sx={{ minWidth: 190 }} slotProps={{ htmlInput: { min: 0.01, step: 'any' } }} />
                <IconButton aria-label={`Xóa dòng ${index + 1}`} disabled={form.lines.length === 1} onClick={() => setForm((current) => ({ ...current, lines: current.lines.filter((_, lineIndex) => lineIndex !== index) }))}><DeleteOutlineIcon /></IconButton>
              </Stack>
            ))}
            <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1 }}>
              <Button disabled={form.lines.length >= 100} startIcon={<AddIcon />} onClick={() => setForm((current) => ({ ...current, lines: [...current.lines, { categoryCode: '', description: '', originalAmount: '' }] }))}>Thêm dòng{form.lines.length >= 100 ? ' (đã đạt giới hạn)' : ''}</Button>
              <Box sx={{ textAlign: { sm: 'right' } }}>
                <Typography>Tổng nguyên tệ: <b>{formatMoney(originalTotal, form.currency || 'VND')}</b></Typography>
                <Typography>Tổng quy đổi VND: <b>{Number(form.exchangeRate) > 0 ? formatMoney(convertedTotal) : 'Chưa xác định tỷ giá'}</b></Typography>
              </Box>
            </Stack>
          </Stack>
        </DialogContent>
        <DialogActions sx={{ flexWrap: 'wrap' }}>
          <Button onClick={onClose} disabled={saving}>Hủy</Button>
          <Button type="submit" value="draft" variant="outlined" disabled={saving}>{saving ? 'Đang lưu…' : 'Lưu nháp'}</Button>
          <Button type="submit" value="confirm" variant="contained" disabled={saving}>{saving ? 'Đang xử lý…' : 'Lưu và gửi duyệt'}</Button>
        </DialogActions>
      </Box>
    </Dialog>
  );
}

function ExpenseDetailDialog({ detail, saving, onClose, onEdit, onAction, onVoid }: {
  detail: ExpenseDetail | null;
  saving: boolean;
  onClose: () => void;
  onEdit: (detail: ExpenseDetail) => void;
  onAction: (action: ExpenseAction) => void;
  onVoid: () => void;
}) {
  return (
    <Dialog open={Boolean(detail)} onClose={onClose} fullWidth maxWidth="lg">
      {detail && <>
        <DialogTitle>
          <Stack direction={{ xs: 'column', sm: 'row' }} sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1 }}>
            <span>{detail.expenseCode} · {detail.vendorName}</span>
            <ExpenseStatusChip status={detail.status} />
          </Stack>
        </DialogTitle>
        <DialogContent dividers>
          <Grid container spacing={2} sx={{ mb: 2 }}>
            <Grid size={{ xs: 12, md: 4 }}><InfoBlock label="Hóa đơn" value={detail.invoiceNumber || 'Chưa ghi nhận'} helper={detail.providerCode || 'Chưa có mã nhà cung cấp'} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><InfoBlock label="Ngày / hạn thanh toán" value={formatFinanceDate(detail.incurredAt)} helper={detail.dueDate ? `Hạn ${formatFinanceDate(detail.dueDate)}` : 'Chưa ghi nhận hạn'} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><InfoBlock label="Trạng thái thanh toán" value={paymentStatusLabel(detail.status)} helper={detail.paidAt ? formatFinanceDateTime(detail.paidAt) : undefined} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><InfoBlock label="Tổng nguyên tệ" value={formatMoney(detail.originalTotal, detail.currency)} helper={`Tỷ giá ${Number(detail.exchangeRate).toLocaleString('vi-VN')}`} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><InfoBlock label="Tổng quy đổi VND" value={formatMoney(detail.totalAmountVnd)} helper="Do backend tính từ từng dòng" /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><InfoBlock label="Nguồn chứng từ" value={EXPENSE_SOURCE_LABELS[detail.sourceType]} helper={detail.evidenceReference || 'Chưa có tham chiếu tệp'} /></Grid>
          </Grid>
          {detail.currency !== 'VND' && <Alert severity="info" sx={{ mb: 2 }}>Tỷ giá ngày {formatFinanceDate(detail.exchangeRateDate)} · Nguồn: {detail.exchangeRateSource || 'Chưa ghi nhận'}</Alert>}
          {detail.billingPeriodFrom && detail.billingPeriodTo && <Typography sx={{ mb: 2 }}>Kỳ dịch vụ: <b>{formatDateRange(detail.billingPeriodFrom, detail.billingPeriodTo)}</b></Typography>}
          {detail.description && <Typography sx={{ mb: 2 }}>Ghi chú: {detail.description}</Typography>}
          {detail.voidReason && <Alert severity="warning" sx={{ mb: 2 }}>Lý do vô hiệu: {detail.voidReason}</Alert>}
          <Table size="small">
            <TableHead><TableRow><TableCell>Nhóm / loại chi phí</TableCell><TableCell>Mô tả</TableCell><TableCell align="right">Nguyên tệ</TableCell><TableCell align="right">VND</TableCell></TableRow></TableHead>
            <TableBody>{detail.lines.map((line) => <TableRow key={line.id}><TableCell>{expenseCategoryLabel(line.categoryCode)}</TableCell><TableCell>{line.description}</TableCell><TableCell align="right">{formatMoney(line.originalAmount, detail.currency)}</TableCell><TableCell align="right">{formatMoney(line.amountVnd)}</TableCell></TableRow>)}</TableBody>
          </Table>
          <Box component="details" sx={{ mt: 2 }}>
            <Typography component="summary" sx={{ cursor: 'pointer', fontWeight: 700 }}>Dấu vết xử lý</Typography>
            <Stack sx={{ mt: 1, gap: 0.5 }}>
              <Typography variant="body2">Tạo bởi {shortId(detail.createdBy)} lúc {formatFinanceDateTime(detail.createdAt)}</Typography>
              <Typography variant="body2">Cập nhật cuối: {formatFinanceDateTime(detail.updatedAt)}</Typography>
              {detail.confirmedAt && <Typography variant="body2">Duyệt bởi {shortId(detail.confirmedBy)} lúc {formatFinanceDateTime(detail.confirmedAt)}</Typography>}
              {detail.paidAt && <Typography variant="body2">Ghi nhận thanh toán lúc {formatFinanceDateTime(detail.paidAt)}</Typography>}
              {detail.voidedAt && <Typography variant="body2">Vô hiệu bởi {shortId(detail.voidedBy)} lúc {formatFinanceDateTime(detail.voidedAt)}</Typography>}
            </Stack>
          </Box>
        </DialogContent>
        <DialogActions sx={{ flexWrap: 'wrap' }}>
          <Button onClick={onClose}>Đóng</Button>
          {detail.status === 'DRAFT' && <Button startIcon={<EditOutlinedIcon />} onClick={() => onEdit(detail)}>Sửa nháp</Button>}
          {detail.status === 'DRAFT' && <Button variant="contained" onClick={() => onAction('confirm')} disabled={saving}>Duyệt chứng từ</Button>}
          {detail.status === 'CONFIRMED' && <Button variant="contained" color="success" onClick={() => onAction('paid')} disabled={saving}>Xác nhận đã thanh toán</Button>}
          {detail.status !== 'VOID' && <Button color="error" onClick={onVoid} disabled={saving}>Vô hiệu</Button>}
        </DialogActions>
      </>}
    </Dialog>
  );
}

function InfoBlock({ label, value, helper }: { label: string; value: string; helper?: string }) {
  return <Card variant="outlined" sx={{ height: '100%' }}><CardContent><Typography variant="caption" color="text.secondary">{label}</Typography><Typography sx={{ fontWeight: 750, mt: 0.5 }}>{value}</Typography>{helper && <Typography variant="caption" color="text.secondary">{helper}</Typography>}</CardContent></Card>;
}

function ExpenseStatusChip({ status }: { status: ExpenseStatus }) {
  return <Chip size="small" label={EXPENSE_STATUS_LABELS[status]} color={status === 'PAID' ? 'success' : status === 'VOID' ? 'default' : status === 'CONFIRMED' ? 'primary' : 'warning'} />;
}

function paymentStatusLabel(status: ExpenseStatus) {
  if (status === 'PAID') return 'Đã thanh toán';
  if (status === 'CONFIRMED') return 'Chờ thanh toán';
  if (status === 'VOID') return 'Không áp dụng';
  return 'Chưa gửi duyệt';
}

function validateExpenseDraft(form: ExpenseDraft) {
  if (!form.vendorName.trim()) return 'Vui lòng nhập nhà cung cấp.';
  if (!form.incurredAt) return 'Vui lòng nhập ngày hóa đơn / phát sinh.';
  if (form.incurredAt > reportingDateInputValue(new Date())) return 'Ngày hóa đơn / phát sinh không được ở tương lai.';
  if (form.dueDate && form.dueDate < form.incurredAt) return 'Hạn thanh toán không được trước ngày hóa đơn.';
  if ((form.billingPeriodFrom && !form.billingPeriodTo) || (!form.billingPeriodFrom && form.billingPeriodTo)) return 'Kỳ dịch vụ phải có đủ ngày bắt đầu và kết thúc.';
  if (form.billingPeriodFrom && form.billingPeriodTo && form.billingPeriodFrom > form.billingPeriodTo) return 'Kỳ dịch vụ không hợp lệ.';
  if (!/^[A-Z]{3,10}$/.test(form.currency)) return 'Mã tiền tệ phải gồm 3–10 chữ cái.';
  if (form.currency === 'VND' && Number(form.exchangeRate) !== 1) return 'Tỷ giá VND phải bằng 1.';
  if (form.currency !== 'VND' && (!form.exchangeRateDate || !form.exchangeRateSource?.trim())) return 'Ngoại tệ bắt buộc có ngày và nguồn tỷ giá.';
  if (!Number.isFinite(Number(form.exchangeRate)) || Number(form.exchangeRate) <= 0) return 'Tỷ giá phải lớn hơn 0.';
  if (form.lines.length === 0 || form.lines.some((line) => !line.categoryCode || !line.description.trim() || Number(line.originalAmount) <= 0)) return 'Mỗi dòng phải có loại chi phí, mô tả và số tiền lớn hơn 0.';
  return null;
}

function validateExpenseFilters(filters: ExpenseFilters) {
  if (filters.incurredFrom && filters.incurredTo && filters.incurredFrom > filters.incurredTo) {
    return 'Khoảng ngày không hợp lệ: ngày kết thúc phải từ ngày bắt đầu trở đi.';
  }
  const minimum = filters.minAmountVnd === '' || filters.minAmountVnd === undefined
    ? null
    : Number(filters.minAmountVnd);
  const maximum = filters.maxAmountVnd === '' || filters.maxAmountVnd === undefined
    ? null
    : Number(filters.maxAmountVnd);
  if ((minimum !== null && (!Number.isFinite(minimum) || minimum < 0))
      || (maximum !== null && (!Number.isFinite(maximum) || maximum < 0))) {
    return 'Khoảng tiền phải là số không âm.';
  }
  if (minimum !== null && maximum !== null && minimum > maximum) {
    return 'Tổng VND đến phải lớn hơn hoặc bằng tổng VND từ.';
  }
  return null;
}

function toExpensePayload(form: ExpenseDraft): ExpensePayload {
  return {
    ...form,
    providerCode: form.providerCode?.trim() || undefined,
    invoiceNumber: form.invoiceNumber?.trim() || undefined,
    description: form.description?.trim() || undefined,
    dueDate: form.dueDate || undefined,
    exchangeRateDate: form.currency === 'VND' ? undefined : form.exchangeRateDate || undefined,
    exchangeRateSource: form.currency === 'VND' ? undefined : form.exchangeRateSource?.trim() || undefined,
    billingPeriodFrom: form.billingPeriodFrom || undefined,
    billingPeriodTo: form.billingPeriodTo || undefined,
    evidenceReference: form.evidenceReference?.trim() || undefined,
    lines: form.lines.map((line) => ({
      categoryCode: line.categoryCode as ExpenseCategory,
      description: line.description.trim(),
      originalAmount: line.originalAmount,
    })),
  };
}

function applyExpenseQuickRange(kind: 'TODAY' | 'WEEK' | 'MONTH' | 'QUARTER', setFilters: Dispatch<SetStateAction<ExpenseFilters>>) {
  const range = quickDateRange(kind, new Date());
  setFilters((current) => ({ ...current, incurredFrom: range.from, incurredTo: range.to }));
}

function apiErrorMessage(error: unknown, fallback: string) {
  if (isAxiosError(error)) {
    if (error.response?.status === 409) return 'Xung đột dữ liệu: chứng từ có thể bị trùng hoặc đã được phiên khác cập nhật. Hãy tải lại và kiểm tra.';
    if (error.response?.status === 403) return 'Tài khoản không có quyền thực hiện thao tác này.';
    const message = (error.response?.data as { message?: unknown } | undefined)?.message;
    if (typeof message === 'string' && message.trim()) return `${fallback} (${message})`;
  }
  return fallback;
}

function shortId(value?: string | null) {
  return value ? `${value.slice(0, 8)}…` : 'Chưa ghi nhận';
}
