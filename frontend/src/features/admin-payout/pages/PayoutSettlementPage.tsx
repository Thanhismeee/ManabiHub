import { useState } from 'react';
import { useForm } from 'react-hook-form';
import {
  AlertTriangle,
  ArrowLeft,
  Building2,
  CheckCircle2,
  CreditCard,
  Download,
  FileCheck2,
  History,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  UserRound,
} from 'lucide-react';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Paper,
  Skeleton,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useNavigate, useParams } from 'react-router-dom';
import { toast } from 'react-hot-toast';
import { PageHeader } from '../../../shared/components/PageHeader/PageHeader';
import { ManualTransferDialog } from '../components/ManualTransferDialog';
import { PayoutStatusBadge } from '../components/PayoutStatusBadge';
import { useApprovePayout } from '../hooks/useApprovePayout';
import { usePayoutDetail } from '../hooks/usePayoutDetail';
import { useRejectPayout } from '../hooks/useRejectPayout';
import { useRetryPayout } from '../hooks/useRetryPayout';
import { useReviewReconciliation } from '../hooks/useReviewReconciliation';
import {
  rejectPayoutSchema,
  type RejectPayoutFormValues,
} from '../schemas/rejectPayoutSchema';
import {
  getPayoutErrorMessage,
  getPayoutMessageByCode,
} from '../services/payoutError';
import { adminPayoutService } from '../services/adminPayoutService';
import type {
  MockPayoutScenario,
  PayoutDetail,
  ReconciliationAlert,
} from '../types/payout.types';
import { formatFinanceDateTime, formatMoney } from '../../admin-finance/financeDisplay';

export function PayoutSettlementPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const detailQuery = usePayoutDetail(id);
  const approve = useApprovePayout();
  const reject = useRejectPayout();
  const retry = useRetryPayout();
  const reviewReconciliation = useReviewReconciliation();
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [approveDialogOpen, setApproveDialogOpen] = useState(false);
  const [manualDialogOpen, setManualDialogOpen] = useState(false);
  const [mockScenario, setMockScenario] = useState<MockPayoutScenario>('SUCCESS');
  const rejectForm = useForm<RejectPayoutFormValues>({
    defaultValues: { reason: '' },
  });

  if (detailQuery.isLoading) {
    return (
      <Box>
        <Skeleton variant="text" width={320} height={52} />
        <Skeleton variant="text" width={420} height={24} sx={{ mb: 3 }} />
        <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
          {[0, 1, 2].map((item) => (
            <Skeleton key={item} variant="rounded" height={220} sx={{ flex: 1 }} />
          ))}
        </Stack>
      </Box>
    );
  }

  if (detailQuery.isError || !detailQuery.data || !id) {
    return (
      <Alert
        severity="error"
        action={(
          <Button color="inherit" onClick={() => void detailQuery.refetch()} sx={{ fontWeight: 700 }}>
            Thử lại
          </Button>
        )}
      >
        {getPayoutErrorMessage(detailQuery.error)}
      </Alert>
    );
  }

  const detail = detailQuery.data;
  const criticalMismatch = detail.reconciliationStatus === 'CRITICAL_MISMATCH';
  const processing = detail.settlementStatus === 'PROCESSING';
  const processingStale = processing && isProcessingStale(detail.processingStartedAt);
  const completed = detail.status === 'EXECUTED' || detail.settlementStatus === 'SUCCEEDED';
  const rejected = detail.status === 'REJECTED' || detail.settlementStatus === 'REJECTED';
  const accountBlocked = (detail.ownerAccountStatus ?? detail.teacherAccountStatus) !== 'ACTIVE';
  const mutationPending = approve.isPending
    || reject.isPending
    || retry.isPending
    || reviewReconciliation.isPending;
  const approvableStatus = ['PENDING', 'APPROVED'].includes(detail.status);
  const canApprove = approvableStatus
    && !criticalMismatch
    && !detail.walletFrozen
    && !accountBlocked
    && !processing
    && !completed
    && !rejected
    && !mutationPending;
  const canRetry = !criticalMismatch
    && !detail.walletFrozen
    && !accountBlocked
    && !completed
    && !rejected
    && detail.transferMethod !== 'MANUAL'
    && (
      detail.settlementStatus === 'FAILED'
      || detail.settlementStatus === 'PENDING_RETRY'
      || processingStale
    )
    && !mutationPending;
  const canManualTransfer = !criticalMismatch
    && !detail.walletFrozen
    && !accountBlocked
    && !processing
    && !completed
    && !rejected
    && !detail.gatewayReference
    && ['PENDING', 'APPROVED', 'FAILED'].includes(detail.status)
    && !mutationPending;
  const canReject = !processing && !completed && !rejected && !mutationPending;

  const handleApprove = () => {
    approve.mutate(
      {
        withdrawalRequestId: id,
        mockScenario: import.meta.env.DEV ? mockScenario : undefined,
      },
      { onSuccess: () => setApproveDialogOpen(false) },
    );
  };

  const submitReject = rejectForm.handleSubmit((values) => {
    const parsed = rejectPayoutSchema.safeParse(values);
    if (!parsed.success) {
      const issue = parsed.error.issues[0];
      rejectForm.setError('reason', { message: issue?.message ?? 'Lý do không hợp lệ.' });
      return;
    }

    reject.mutate(
      { withdrawalRequestId: id, payload: parsed.data },
      {
        onSuccess: () => {
          rejectForm.reset();
          setRejectDialogOpen(false);
        },
      },
    );
  });

  const downloadProof = async () => {
    try {
      const blob = await adminPayoutService.downloadManualProof(id);
      const objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = detail.manualProofOriginalName || 'payout-transfer-proof';
      anchor.click();
      URL.revokeObjectURL(objectUrl);
    } catch (error) {
      toast.error(getPayoutErrorMessage(error));
    }
  };

  const downloadBankQr = async () => {
    try {
      const blob = await adminPayoutService.downloadBankQr(id);
      const objectUrl = URL.createObjectURL(blob);
      const anchor = document.createElement('a');
      anchor.href = objectUrl;
      anchor.download = 'bank-account-qr';
      anchor.click();
      URL.revokeObjectURL(objectUrl);
    } catch (error) {
      toast.error(getPayoutErrorMessage(error));
    }
  };

  return (
    <div className="space-y-5">
      <PageHeader
        title="Chi tiết chi trả"
        subtitle={`Mã yêu cầu ${shortId(detail.withdrawalRequestId)}`}
        breadcrumbs={[
          { label: 'Tài chính', href: '/admin/payouts' },
          { label: 'Chi trả' },
        ]}
        action={(
          <Button
            variant="outlined"
            startIcon={<ArrowLeft className="h-4 w-4" />}
            onClick={() => navigate('/admin/payouts')}
            sx={{ fontWeight: 700, textTransform: 'none' }}
          >
            Hàng đợi
          </Button>
        )}
      />

      <Paper
        elevation={0}
        sx={{
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 2,
          p: { xs: 2, md: 2.5 },
        }}
      >
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          spacing={2}
          sx={{ alignItems: { xs: 'flex-start', md: 'center' }, justifyContent: 'space-between' }}
        >
          <Box>
            <Typography variant="body2" color="text.secondary">Số tiền yêu cầu chi trả</Typography>
            <Typography variant="h5" sx={{ fontWeight: 800, letterSpacing: '-0.02em', mt: 0.25 }}>
              {formatMoney(detail.requestedAmount)}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Tạo lúc {formatFinanceDateTime(detail.requestedAt)} · {detail.ownerName ?? detail.teacherName}
            </Typography>
          </Box>
          <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', gap: 1 }}>
            <LabeledStatus label="Nội bộ" status={detail.status} />
            <LabeledStatus label="Provider" status={detail.settlementStatus} />
            <LabeledStatus label="Đối soát" status={detail.reconciliationStatus} />
          </Stack>
        </Stack>
      </Paper>

      {detail.reconciliationAlerts.length > 0 && (
        <section className={`rounded-xl border p-4 ${
          criticalMismatch
            ? 'border-red-200 bg-red-50'
            : 'border-amber-200 bg-amber-50'
        }`}>
          <div className="flex gap-3">
            <AlertTriangle className={`mt-0.5 h-5 w-5 shrink-0 ${criticalMismatch ? 'text-red-600' : 'text-amber-600'}`} />
            <div>
              <h2 className={`font-bold ${criticalMismatch ? 'text-red-900' : 'text-amber-900'}`}>
                {criticalMismatch ? 'Phát hiện sai lệch nghiêm trọng' : 'Có cảnh báo cần lưu ý'}
              </h2>
              <ul className="mt-2 space-y-1.5 text-sm">
                {detail.reconciliationAlerts.map((alert) => (
                  <li key={alert.code} className="flex items-start gap-2">
                    <span className="mt-1.5 h-1.5 w-1.5 shrink-0 rounded-full bg-current" />
                    <span>{reconciliationAlertText(alert)}</span>
                  </li>
                ))}
              </ul>
              <details className="mt-3 text-xs opacity-80">
                <summary className="cursor-pointer font-semibold">Mã kỹ thuật đối soát</summary>
                <code className="mt-1 block break-all">{detail.reconciliationAlerts.map((alert) => alert.code).join(', ')}</code>
              </details>
            </div>
          </div>
        </section>
      )}

      <div className="grid gap-5 lg:grid-cols-3">
        <InfoCard title={detail.ownerType === 'STUDENT' ? 'Học viên' : 'Giáo viên'} icon={UserRound}>
          <InfoRow label="Tên hiển thị" value={detail.ownerName ?? detail.teacherName} />
          <InfoRow label="Mã chủ ví" value={detail.ownerId ?? detail.teacherId} breakAll />
          <InfoRow label="Trạng thái tài khoản" value={accountStatusLabel(detail.ownerAccountStatus ?? detail.teacherAccountStatus)} />
        </InfoCard>

        <InfoCard title="Số dư ví chi trả" icon={CreditCard}>
          <InfoRow label="Số tiền yêu cầu" value={formatMoney(detail.requestedAmount)} emphasize />
          <InfoRow label="Số dư khả dụng" value={formatMoney(detail.availableBalance)} />
          <InfoRow label="Số dư đang giữ" value={formatMoney(detail.reservedBalance)} />
          <InfoRow label="Đang chờ clearing" value={formatMoney(detail.pendingClearing)} />
        </InfoCard>

        <InfoCard title="Đích nhận tiền" icon={Building2}>
          <InfoRow label="Ngân hàng" value={detail.bankName || 'Chưa có'} />
          <InfoRow label="Chi nhánh" value={detail.bankBranch || 'Không cung cấp'} />
          <InfoRow label="Chủ tài khoản" value={detail.accountHolderName || 'Chưa có'} />
          <InfoRow label="Số tài khoản" value={detail.accountNumberMasked} />
        </InfoCard>
      </div>

      <div className="grid gap-5 lg:grid-cols-2">
        <InfoCard title="Đối soát" icon={ShieldCheck}>
          <InfoRow label="Kết quả đối soát" value={<PayoutStatusBadge status={detail.reconciliationStatus} />} />
          <InfoRow label="Escrow" value={escrowLabel(detail.escrowStatus)} />
          <InfoRow label="Ví bị khóa" value={detail.walletFrozen ? 'Có' : 'Không'} />
          <InfoRow label="Tài khoản bị chặn" value={accountBlocked ? 'Có' : 'Không'} />
          <InfoRow label="Số lần đã lưu" value={String(detail.reconciliationHistory.length)} />
          <button
            type="button"
            onClick={() => reviewReconciliation.mutate(id)}
            disabled={mutationPending}
            className="mt-2 inline-flex w-full items-center justify-center gap-2 rounded-lg border border-amber-300 bg-amber-50 px-3 py-2 text-sm font-bold text-amber-800 hover:bg-amber-100 disabled:opacity-50"
          >
            <RefreshCw className={`h-4 w-4 ${reviewReconciliation.isPending ? 'animate-spin' : ''}`} />
            Kiểm tra và lưu đối soát
          </button>
        </InfoCard>

        <InfoCard title="Lần xử lý hiện tại" icon={History}>
          <InfoRow label="Số lần thử lại" value={String(detail.retryCount)} />
          <InfoRow label="Bắt đầu xử lý" value={formatFinanceDateTime(detail.processingStartedAt)} />
          <InfoRow label="Hoàn tất" value={formatFinanceDateTime(detail.settledAt)} />
          <InfoRow label="Nhà cung cấp" value={detail.gatewayProvider || 'Chưa đồng bộ'} />
          <InfoRow
            label="Phương thức"
            value={detail.transferMethod === 'MANUAL'
              ? 'Chuyển khoản thủ công'
              : detail.transferMethod === 'GATEWAY'
                ? 'Payment Gateway'
                : 'Chưa chọn'}
          />
          <InfoRow label="Mã đối soát" value={detail.gatewayReference || 'Chưa có'} breakAll />
          <InfoRow
            label="Thông báo người nhận"
            value={notificationLabel(detail.notificationStatus, detail.notificationAttempts)}
          />
          {detail.failureCode && (
            <InfoRow
              label="Kết quả lỗi"
              value={getPayoutMessageByCode(detail.failureCode) ?? 'Lỗi provider chưa được ánh xạ'}
            />
          )}
          {detail.decisionReason && <InfoRow label="Lý do quyết định" value={detail.decisionReason} />}
          {detail.manualTransferredAt && (
            <InfoRow label="Thời điểm chuyển tay" value={formatFinanceDateTime(detail.manualTransferredAt)} />
          )}
          {detail.manualProofAvailable && (
            <button
              type="button"
              onClick={() => void downloadProof()}
              className="mt-2 inline-flex w-full items-center justify-center gap-2 rounded-lg border border-emerald-300 bg-emerald-50 px-3 py-2 text-sm font-bold text-emerald-800 hover:bg-emerald-100"
            >
              <Download className="h-4 w-4" />
              Tải chứng từ {detail.manualProofOriginalName || ''}
            </button>
          )}
          {detail.bankQrAvailable && (
            <button
              type="button"
              onClick={() => void downloadBankQr()}
              className="mt-2 inline-flex w-full items-center justify-center gap-2 rounded-lg border border-blue-300 bg-blue-50 px-3 py-2 text-sm font-bold text-blue-800 hover:bg-blue-100"
            >
              <Download className="h-4 w-4" />
              Tải mã QR ngân hàng
            </button>
          )}
          {(detail.failureCode || detail.failureMessage) && (
            <details className="mt-3 rounded-lg border border-gray-200 bg-gray-50 p-2 text-xs text-gray-600">
              <summary className="cursor-pointer font-semibold">Chi tiết kỹ thuật provider</summary>
              {detail.failureCode && <code className="mt-2 block break-all">Mã: {detail.failureCode}</code>}
              {detail.failureMessage && <p className="mt-1 break-words">Thông điệp: {detail.failureMessage}</p>}
            </details>
          )}
        </InfoCard>
      </div>

      <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
        <h2 className="flex items-center gap-2 font-bold text-gray-900">
          <FileCheck2 className="h-5 w-5 text-red-600" />
          Lịch sử đối soát bất biến
        </h2>
        {detail.reconciliationHistory.length === 0 ? (
          <p className="mt-3 text-sm text-gray-500">
            Chưa có lần đối soát nào được Finance Manager lưu lại.
          </p>
        ) : (
          <ol className="mt-4 space-y-3">
            {detail.reconciliationHistory.map((entry) => (
              <li key={entry.id} className="rounded-lg border border-gray-100 bg-gray-50 p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <PayoutStatusBadge status={entry.status} />
                    <span className="text-sm font-semibold text-gray-800">
                      {reconciliationTriggerLabel(entry.triggerType)}
                    </span>
                  </div>
                  <time className="text-xs text-gray-500">{formatFinanceDateTime(entry.createdAt)}</time>
                </div>
                {entry.alerts.length > 0 && (
                  <ul className="mt-2 space-y-1 text-sm text-gray-600">
                    {entry.alerts.map((alert) => (
                      <li key={`${entry.id}-${alert.code}`}>• {reconciliationAlertText(alert)}</li>
                    ))}
                  </ul>
                )}
                <p className="mt-2 break-all text-xs text-gray-400">
                  Finance actor: {entry.checkedBy}
                </p>
              </li>
            ))}
          </ol>
        )}
      </section>

      <section className="sticky bottom-0 z-20 flex flex-wrap items-center justify-between gap-4 rounded-xl border border-gray-200 bg-white/95 p-4 shadow-[0_-8px_28px_rgba(15,23,42,0.08)] backdrop-blur">
        <p className="max-w-2xl text-sm text-gray-600">
          {approveBlockReason(detail, mutationPending)}
        </p>
        <div className="flex w-full flex-wrap gap-2 sm:w-auto sm:justify-end">
          {import.meta.env.DEV && canApprove && (
            <label className="flex min-w-64 flex-col gap-1 rounded-lg border border-amber-200 bg-amber-50 px-3 py-2 text-xs font-bold text-amber-900">
              Kết quả payout giả lập
              <select
                value={mockScenario}
                onChange={(event) => setMockScenario(event.target.value as MockPayoutScenario)}
                className="rounded-md border border-amber-300 bg-white px-2 py-1.5 text-sm text-gray-900"
              >
                <option value="SUCCESS">Thành công</option>
                <option value="RETRYABLE_FAILURE">Lỗi tạm thời, có thể thử lại</option>
                <option value="PERMANENT_FAILURE">Thất bại vĩnh viễn</option>
              </select>
              <span className="font-normal text-amber-700">Chỉ hiển thị khi chạy frontend development.</span>
            </label>
          )}
          <button
            type="button"
            onClick={() => setRejectDialogOpen(true)}
            disabled={!canReject}
            className="rounded-lg border border-red-300 bg-white px-4 py-2.5 text-sm font-bold text-red-700 hover:bg-red-50 disabled:cursor-not-allowed disabled:opacity-40"
          >
            Từ chối
          </button>
          <button
            type="button"
            onClick={() => setManualDialogOpen(true)}
            disabled={!canManualTransfer}
            className="rounded-lg border border-emerald-300 bg-white px-4 py-2.5 text-sm font-bold text-emerald-700 hover:bg-emerald-50 disabled:cursor-not-allowed disabled:opacity-40"
          >
            Xác nhận chuyển tay
          </button>
          {canRetry && (
            <button
              type="button"
              onClick={() => retry.mutate(id)}
              disabled={mutationPending}
              className="inline-flex items-center gap-2 rounded-lg bg-amber-600 px-4 py-2.5 text-sm font-bold text-white hover:bg-amber-700 disabled:opacity-50"
            >
              <RotateCcw className="h-4 w-4" />
              {retry.isPending ? 'Đang thử lại...' : 'Thử lại an toàn'}
            </button>
          )}
          <button
            type="button"
            onClick={() => setApproveDialogOpen(true)}
            disabled={!canApprove}
            className="inline-flex items-center gap-2 rounded-lg bg-red-600 px-5 py-2.5 text-sm font-bold text-white hover:bg-red-700 disabled:cursor-not-allowed disabled:opacity-40"
          >
            <CheckCircle2 className="h-4 w-4" />
            {approve.isPending
              ? 'Đang chuyển tiền...'
              : import.meta.env.DEV
                ? 'Chạy payout giả lập'
                : 'Duyệt qua Gateway'}
          </button>
        </div>
      </section>

      {manualDialogOpen && (
        <ManualTransferDialog
          detail={detail}
          onClose={() => setManualDialogOpen(false)}
        />
      )}

      <Dialog
        open={approveDialogOpen}
        onClose={() => {
          if (!approve.isPending) setApproveDialogOpen(false);
        }}
        maxWidth="sm"
        fullWidth
      >
        <DialogTitle sx={{ fontWeight: 800 }}>Xác nhận thực hiện chi trả?</DialogTitle>
        <DialogContent>
          <Alert severity="warning" sx={{ mb: 2 }}>
            Hệ thống sẽ gọi provider để chuyển tiền. Backend sử dụng khóa idempotency nhằm ngăn một yêu cầu bị chuyển trùng.
          </Alert>
          <Stack spacing={1.25}>
            <InfoRow label="Người nhận" value={detail.ownerName ?? detail.teacherName} />
            <InfoRow label="Số tiền" value={formatMoney(detail.requestedAmount)} emphasize />
            <InfoRow label="Ngân hàng" value={detail.bankName || 'Chưa ghi nhận'} />
            <InfoRow label="Tài khoản" value={detail.accountNumberMasked || 'Chưa ghi nhận'} />
            <InfoRow label="Đối soát" value={<PayoutStatusBadge status={detail.reconciliationStatus} />} />
            {import.meta.env.DEV && (
              <InfoRow label="Kết quả giả lập" value={mockScenarioLabel(mockScenario)} />
            )}
          </Stack>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 3 }}>
          <Button
            color="inherit"
            disabled={approve.isPending}
            onClick={() => setApproveDialogOpen(false)}
            sx={{ textTransform: 'none' }}
          >
            Quay lại kiểm tra
          </Button>
          <Button
            variant="contained"
            disabled={approve.isPending}
            onClick={handleApprove}
            sx={{ fontWeight: 700, textTransform: 'none' }}
          >
            {approve.isPending ? 'Đang thực hiện...' : 'Xác nhận chi trả'}
          </Button>
        </DialogActions>
      </Dialog>

      {rejectDialogOpen && (
        <Dialog open onClose={() => setRejectDialogOpen(false)} maxWidth="sm" fullWidth>
          <Box component="form" onSubmit={submitReject}>
            <DialogTitle sx={{ fontWeight: 800 }}>Từ chối yêu cầu rút tiền?</DialogTitle>
            <DialogContent>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Số dư đang giữ sẽ được trả lại ví khả dụng. Lý do được gửi cho chủ ví và lưu vào audit.
              </Typography>
              <TextField
                {...rejectForm.register('reason')}
                label="Lý do từ chối"
                placeholder="Ví dụ: Thông tin tài khoản ngân hàng chưa khớp hồ sơ đã xác minh."
                multiline
                rows={4}
                error={Boolean(rejectForm.formState.errors.reason)}
                helperText={rejectForm.formState.errors.reason?.message}
                autoFocus
                slotProps={{ htmlInput: { maxLength: 500 } }}
              />
            </DialogContent>
            <DialogActions sx={{ px: 3, pb: 3 }}>
              <Button color="inherit" onClick={() => setRejectDialogOpen(false)} sx={{ textTransform: 'none' }}>
                Quay lại
              </Button>
              <Button
                type="submit"
                color="error"
                variant="contained"
                disabled={reject.isPending}
                sx={{ fontWeight: 700, textTransform: 'none' }}
              >
                {reject.isPending ? 'Đang xử lý...' : 'Xác nhận từ chối'}
              </Button>
            </DialogActions>
          </Box>
        </Dialog>
      )}
    </div>
  );
}

function mockScenarioLabel(scenario: MockPayoutScenario) {
  const labels: Record<MockPayoutScenario, string> = {
    SUCCESS: 'Thành công',
    RETRYABLE_FAILURE: 'Lỗi tạm thời, có thể thử lại',
    PERMANENT_FAILURE: 'Thất bại vĩnh viễn',
  };
  return labels[scenario];
}

function LabeledStatus({
  label,
  status,
}: {
  label: string;
  status: PayoutDetail['status'] | PayoutDetail['settlementStatus'] | PayoutDetail['reconciliationStatus'];
}) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
        {label}
      </Typography>
      {status
        ? <PayoutStatusBadge status={status} />
        : <Typography variant="body2" color="text.secondary">Chưa đồng bộ</Typography>}
    </Box>
  );
}

function InfoCard({
  title,
  icon: Icon,
  children,
}: {
  title: string;
  icon: typeof CreditCard;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-xl border border-gray-200 bg-white p-5 shadow-sm">
      <h2 className="mb-4 flex items-center gap-2 border-b border-gray-100 pb-3 font-bold text-gray-900">
        <Icon className="h-5 w-5 text-red-600" />
        {title}
      </h2>
      <dl className="space-y-3">{children}</dl>
    </section>
  );
}

function InfoRow({
  label,
  value,
  emphasize = false,
  breakAll = false,
}: {
  label: string;
  value: React.ReactNode;
  emphasize?: boolean;
  breakAll?: boolean;
}) {
  return (
    <div className="flex items-start justify-between gap-4 text-sm">
      <dt className="shrink-0 text-gray-500">{label}</dt>
      <dd className={`text-right text-gray-900 ${emphasize ? 'text-base font-bold' : 'font-semibold'} ${breakAll ? 'break-all' : ''}`}>
        {value}
      </dd>
    </div>
  );
}

function shortId(value: string) {
  return `${value.slice(0, 8).toUpperCase()}…`;
}

function accountStatusLabel(status: string) {
  if (status === 'ACTIVE') return 'Đang hoạt động';
  if (status === 'LOCKED') return 'Đã khóa';
  if (status === 'DELETED') return 'Đã xóa';
  return 'Không xác định';
}

function escrowLabel(status: string) {
  return status === 'CLEARED' ? 'Đã clearing' : 'Còn khoản đang chờ clearing';
}

function notificationLabel(status: PayoutDetail['notificationStatus'], attempts: number) {
  const labels = {
    NOT_REQUIRED: 'Chưa cần gửi',
    PENDING: 'Đang chờ gửi',
    SENT: 'Đã gửi',
    FAILED: 'Gửi thất bại',
  };
  return `${labels[status]}${attempts > 0 ? ` (${attempts} lần)` : ''}`;
}

function reconciliationTriggerLabel(trigger: PayoutDetail['reconciliationHistory'][number]['triggerType']) {
  const labels = {
    DETAIL_REVIEW: 'Finance kiểm tra thủ công',
    APPROVAL: 'Trước khi gọi Gateway',
    FINALIZATION: 'Trước khi ghi sổ hoàn tất',
    MANUAL_TRANSFER: 'Xác nhận chuyển khoản thủ công',
  };
  return labels[trigger];
}

function isProcessingStale(processingStartedAt: string | null) {
  if (!processingStartedAt) return true;
  return Date.now() - new Date(processingStartedAt).getTime() >= 5 * 60 * 1000;
}

function reconciliationAlertText(alert: ReconciliationAlert) {
  const messages: Record<string, string> = {
    PAYOUT_TEACHER_ACCOUNT_BLOCKED: 'Tài khoản giáo viên không ở trạng thái hoạt động.',
    PAYOUT_WALLET_OWNER_MISMATCH: 'Ví không thuộc về giáo viên của yêu cầu rút tiền.',
    PAYOUT_INVALID_REQUEST_AMOUNT: 'Số tiền yêu cầu không hợp lệ.',
    PAYOUT_RESERVED_BALANCE_MISMATCH: 'Số dư đang giữ thấp hơn số tiền yêu cầu.',
    PAYOUT_CURRENCY_MISMATCH: 'Đơn vị tiền tệ của ví không phù hợp.',
    PAYOUT_BANK_DESTINATION_MISSING: 'Thiếu thông tin tài khoản ngân hàng đích đã xác minh.',
    PAYOUT_RESERVATION_LEDGER_MISSING: 'Không tìm thấy bút toán giữ tiền của yêu cầu.',
    PAYOUT_RESERVATION_LEDGER_MISMATCH: 'Bút toán giữ tiền không khớp ví hoặc số tiền.',
    PAYOUT_REJECTED_STATE_MISMATCH: 'Trạng thái từ chối giữa yêu cầu và giao dịch chi trả không đồng nhất.',
    PAYOUT_REJECTION_LEDGER_MISSING: 'Không tìm thấy bút toán giải phóng tiền của yêu cầu bị từ chối.',
    PAYOUT_REJECTION_LEDGER_MISMATCH: 'Bút toán giải phóng tiền không khớp ví hoặc số tiền.',
    PAYOUT_REJECTION_REASON_MISSING: 'Quyết định từ chối chưa có lý do.',
    PAYOUT_REJECTED_TRANSFER_CONFLICT: 'Yêu cầu bị từ chối nhưng đã có mã giao dịch từ nhà cung cấp.',
    PAYOUT_REJECTED_COMPLETION_CONFLICT: 'Yêu cầu bị từ chối nhưng đã tồn tại bút toán chi trả hoàn tất.',
    PAYOUT_COMPLETED_STATE_MISMATCH: 'Trạng thái yêu cầu rút tiền và giao dịch chi trả không đồng nhất.',
    PAYOUT_REQUEST_WALLET_MISMATCH: 'Yêu cầu rút tiền tham chiếu tới ví khác.',
    PAYOUT_SETTLEMENT_WALLET_MISMATCH: 'Giao dịch chi trả tham chiếu tới ví khác.',
    PAYOUT_SETTLEMENT_OWNER_MISMATCH: 'Loại chủ ví của giao dịch chi trả không khớp yêu cầu.',
    PAYOUT_SETTLEMENT_AMOUNT_MISMATCH: 'Số tiền chi trả không khớp số tiền yêu cầu.',
    PAYOUT_SETTLEMENT_CURRENCY_MISMATCH: 'Đơn vị tiền tệ của giao dịch chi trả không khớp với ví.',
    PAYOUT_PROVIDER_REFERENCE_MISSING: 'Giao dịch hoàn tất chưa có mã tham chiếu từ nhà cung cấp.',
    PAYOUT_COMPLETION_LEDGER_MISSING: 'Không tìm thấy bút toán hoàn tất rút tiền.',
    PAYOUT_COMPLETION_LEDGER_MISMATCH: 'Bút toán hoàn tất không khớp ví hoặc số tiền.',
    PAYOUT_WALLET_BALANCE_INVALID: 'Ví có số dư thiếu hoặc âm bất thường.',
    PAYOUT_PENDING_ESCROW_PRESENT: 'Giáo viên còn doanh thu khác đang chờ clearing.',
    PAYOUT_WALLET_FROZEN: 'Ví doanh thu của giáo viên đang bị khóa.',
  };
  return messages[alert.code] ?? 'Cảnh báo đối soát chưa được ánh xạ.';
}

function approveBlockReason(detail: PayoutDetail, mutationPending: boolean) {
  if (mutationPending) return 'Một quyết định đang được xử lý. Vui lòng không gửi thêm thao tác.';
  if (detail.status === 'EXECUTED' || detail.settlementStatus === 'SUCCEEDED') {
    return 'Yêu cầu này đã được thanh toán.';
  }
  if (detail.status === 'REJECTED' || detail.settlementStatus === 'REJECTED') {
    return 'Yêu cầu này đã bị từ chối.';
  }
  if (detail.settlementStatus === 'PROCESSING') {
    if (isProcessingStale(detail.processingStartedAt)) {
      return 'Phiên xử lý đã quá 5 phút. Hãy dùng “Thử lại an toàn”; hệ thống vẫn giữ nguyên khóa idempotency.';
    }
    return 'Giao dịch đang được xử lý; nút quyết định tạm khóa để chống chuyển trùng.';
  }
  if (detail.reconciliationStatus === 'CRITICAL_MISMATCH') {
    return 'Không thể duyệt khi còn sai lệch đối soát nghiêm trọng.';
  }
  if (detail.walletFrozen || (detail.ownerAccountStatus ?? detail.teacherAccountStatus) !== 'ACTIVE') {
    return 'Không thể duyệt do ví hoặc tài khoản người nhận đang bị khóa.';
  }
  return 'Cảnh báo mức WARNING không chặn chi trả; hãy kiểm tra kỹ trước khi duyệt.';
}
