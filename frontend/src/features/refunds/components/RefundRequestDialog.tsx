import { useEffect, useState } from 'react';
import type { AxiosError } from 'axios';
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useCommercialPolicy } from '../../help-center/hooks/useCommercialPolicy';
import type { OrderItemResponse } from '../../checkout/types';
import type { ApiResponse } from '../../../shared/types/api';
import { useCancelStudentRefund, useCreateStudentRefund, useStudentRefundDetail } from '../hooks/useStudentRefunds';
import type { StudentRefundResponse, StudentRefundType } from '../types';

const REFUND_TYPE_OPTIONS: Array<{ value: StudentRefundType; label: string }> = [
  { value: 'STANDARD', label: 'Yêu cầu theo chính sách tiêu chuẩn' },
  { value: 'DISPUTE', label: 'Tranh chấp / yêu cầu xét duyệt thủ công' },
  { value: 'DUPLICATE_CHARGE', label: 'Bị tính phí trùng' },
  { value: 'PAYMENT_ERROR', label: 'Có lỗi thanh toán' },
  { value: 'PLATFORM_ACCESS_FAILURE', label: 'Lỗi nền tảng khiến tôi không thể học' },
];

const STATUS_LABELS: Record<StudentRefundResponse['status'], string> = {
  PENDING: 'Đang chờ Finance xem xét',
  PROCESSING: 'Đang xử lý hoàn tiền',
  RECONCILIATION_REQUIRED: 'Cần đối soát',
  APPROVED: 'Đã chấp thuận',
  REJECTED: 'Đã từ chối',
  CANCELLED: 'Đã hủy',
};

interface RefundRequestDialogProps {
  open: boolean;
  orderItem: OrderItemResponse | null;
  existingRefund?: StudentRefundResponse | null;
  onClose: () => void;
}

export function RefundRequestDialog({
  open,
  orderItem,
  existingRefund,
  onClose,
}: RefundRequestDialogProps) {
  const [refundType, setRefundType] = useState<StudentRefundType>('STANDARD');
  const [reason, setReason] = useState('');
  const [confirmed, setConfirmed] = useState(false);
  const [submitted, setSubmitted] = useState(false);
  const policyQuery = useCommercialPolicy();
  const createMutation = useCreateStudentRefund();
  const cancelMutation = useCancelStudentRefund();
  const detailQuery = useStudentRefundDetail(existingRefund?.id ?? createMutation.data?.id, open);
  const currentRefund = detailQuery.data ?? existingRefund ?? createMutation.data;

  useEffect(() => {
    if (open) {
      setRefundType('STANDARD');
      setReason('');
      setConfirmed(false);
      setSubmitted(false);
      createMutation.reset();
      cancelMutation.reset();
    }
  // Mutations are intentionally reset only when a new dialog session starts.
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open]);

  const error = createMutation.error ?? cancelMutation.error;
  const errorCode = getMessageCode(error);
  const errorMessage = error
    ? refundErrorMessage(errorCode, getApiMessage(error))
    : null;

  const submit = async () => {
    if (!orderItem || !reason.trim() || !confirmed) return;
    try {
      await createMutation.mutateAsync({
        orderItemId: orderItem.id,
        refundType,
        reason: reason.trim(),
      });
      setSubmitted(true);
    } catch {
      // React Query exposes the server error below using its machine-readable message code.
    }
  };

  const cancel = async () => {
    if (!existingRefund?.cancellable) return;
    try {
      await cancelMutation.mutateAsync(existingRefund.id);
      setSubmitted(true);
    } catch {
      // React Query exposes the cancellation error below.
    }
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>
        {existingRefund ? 'Chi tiết yêu cầu hoàn tiền' : 'Yêu cầu hoàn tiền khóa học'}
      </DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2.25}>
          <Box>
            <Typography sx={{ fontWeight: 800 }}>{orderItem?.courseTitle}</Typography>
            <Typography variant="body2" color="text.secondary">
              Mỗi yêu cầu chỉ áp dụng cho khóa học này trong đơn hàng.
            </Typography>
          </Box>

          {currentRefund ? (
            <RefundDetail refund={currentRefund} />
          ) : (
            <>
              {policyQuery.isLoading && <Alert severity="info">Đang tải chính sách hiện hành…</Alert>}
              {policyQuery.isError && (
                <Alert
                  severity="error"
                  action={<Button onClick={() => void policyQuery.refetch()}>Thử lại</Button>}
                >
                  Không thể tải chính sách hoàn tiền. Vui lòng thử lại trước khi gửi.
                </Alert>
              )}
              {policyQuery.data && (
                <Alert severity="info">
                  Điều kiện tiêu chuẩn: gửi trong vòng{' '}
                  <strong>{policyQuery.data.refundWindowDays} ngày theo lịch</strong> và tiến độ{' '}
                  <strong>không vượt quá {policyQuery.data.refundProgressLimitPercent}%</strong> và chưa tải{' '}
                  <strong>toàn bộ tài liệu học tập được bảo vệ</strong>. Nếu đáp ứng đủ, tiền sẽ được tự động
                  hoàn toàn bộ vào ví.
                </Alert>
              )}

              <FormControl fullWidth>
                <InputLabel id="refund-type-label">Loại yêu cầu</InputLabel>
                <Select
                  labelId="refund-type-label"
                  label="Loại yêu cầu"
                  value={refundType}
                  onChange={(event) => setRefundType(event.target.value as StudentRefundType)}
                >
                  {REFUND_TYPE_OPTIONS.map((option) => (
                    <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                  ))}
                </Select>
              </FormControl>

              {refundType !== 'STANDARD' && (
                <Alert severity="warning">
                  Trường hợp ngoại lệ sẽ được chuyển tới Finance để xem xét thủ công và không được tự động chấp thuận.
                </Alert>
              )}

              <TextField
                label="Lý do và thông tin cần kiểm tra"
                value={reason}
                onChange={(event) => setReason(event.target.value)}
                multiline
                minRows={4}
                slotProps={{ htmlInput: { maxLength: 2000 } }}
                helperText={`${reason.length}/2000`}
                required
                fullWidth
              />
              <FormControlLabel
                control={(
                  <Checkbox
                    checked={confirmed}
                    onChange={(event) => setConfirmed(event.target.checked)}
                  />
                )}
                label="Tôi xác nhận thông tin trên là đúng và đồng ý áp dụng chính sách hoàn tiền."
              />
            </>
          )}

          {errorMessage && <Alert severity="error">{errorMessage}</Alert>}
          {submitted && !error && (
            <Alert severity="success">
              {existingRefund
                ? 'Yêu cầu đã được hủy.'
                : currentRefund?.status === 'APPROVED'
                  ? 'Yêu cầu đủ điều kiện và đã được tự động hoàn toàn bộ tiền vào ví.'
                  : currentRefund?.automaticRefundPending
                    ? 'Đã ghi nhận yêu cầu. Hệ thống đang tự động hoàn tiền vào ví và sẽ cập nhật kết quả tại đây.'
                    : currentRefund?.status === 'RECONCILIATION_REQUIRED'
                      ? 'Yêu cầu cần đối soát trước khi có thể hoàn tiền. Finance sẽ kiểm tra và xử lý.'
                    : 'Yêu cầu đã được chuyển sang tranh chấp/xét duyệt thủ công.'}
            </Alert>
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>Đóng</Button>
        {currentRefund?.cancellable && existingRefund && !submitted && (
          <Button
            color="error"
            onClick={() => void cancel()}
            disabled={cancelMutation.isPending}
          >
            {cancelMutation.isPending ? 'Đang hủy…' : 'Hủy yêu cầu đang chờ'}
          </Button>
        )}
        {!existingRefund && !submitted && (
          <Button
            variant="contained"
            onClick={() => void submit()}
            disabled={
              createMutation.isPending
              || policyQuery.isLoading
              || policyQuery.isError
              || !reason.trim()
              || !confirmed
            }
          >
            {createMutation.isPending ? 'Đang gửi…' : 'Gửi yêu cầu'}
          </Button>
        )}
      </DialogActions>
    </Dialog>
  );
}

function RefundDetail({ refund }: { refund: StudentRefundResponse }) {
  const snapshot = refund.eligibilitySnapshot;
  return (
    <Stack spacing={1.5}>
      <Chip label={refund.automaticRefundPending ? 'Đang tự động hoàn tiền vào ví' : STATUS_LABELS[refund.status]} color={refund.status === 'REJECTED' ? 'error' : 'primary'} />
      <Typography variant="body2"><strong>Loại:</strong> {refund.refundType}</Typography>
      <Typography variant="body2"><strong>Lý do:</strong> {refund.reason}</Typography>
      <Typography variant="body2">
        <strong>Thời điểm gửi:</strong> {new Date(refund.createdAt).toLocaleString('vi-VN')}
      </Typography>
      {snapshot && (
        <Alert severity="info">
          Snapshot khi gửi: ngày thứ {snapshot.elapsedCalendarDays}/{snapshot.refundWindowDays},{' '}
          tiến độ {snapshot.measuredProgressPercent}% (ngưỡng không vượt quá{' '}
          {snapshot.progressThresholdPercent}%), tài liệu bảo vệ{' '}
          {snapshot.protectedMaterialsFullyDownloaded ? 'đã tải toàn bộ' : 'chưa tải toàn bộ'}, số tiền đã trả{' '}
          {new Intl.NumberFormat('vi-VN', { style: 'currency', currency: snapshot.currency })
            .format(snapshot.actuallyPaidAmount)}.
        </Alert>
      )}
      {refund.decisionNote && (
        <Typography variant="body2"><strong>Kết quả xử lý:</strong> {refund.decisionNote}</Typography>
      )}
      {refund.status === 'RECONCILIATION_REQUIRED' && (
        <Alert severity="warning">
          Cần Finance kiểm tra trước khi hoàn tiền. Mã đối soát: {refund.reconciliationReasonCode ?? 'LEGACY_REASON_NOT_RECORDED'}.
        </Alert>
      )}
    </Stack>
  );
}

function getMessageCode(error: unknown): string | undefined {
  return (error as AxiosError<ApiResponse<unknown>> | undefined)?.response?.data?.messageCode;
}

function getApiMessage(error: unknown): string | undefined {
  const message = (error as AxiosError<ApiResponse<unknown>> | undefined)?.response?.data?.message;
  return typeof message === 'string' && message.trim() ? message : undefined;
}

function refundErrorMessage(code?: string, serverMessage?: string): string {
  const messages: Record<string, string> = {
    REFUND_WINDOW_EXPIRED: 'Đã quá thời hạn hoàn tiền tiêu chuẩn. Nếu có lỗi thanh toán hoặc lỗi nền tảng, hãy chọn đúng loại ngoại lệ để Finance xem xét.',
    REFUND_PROGRESS_LIMIT_REACHED: 'Tiến độ đã đạt ngưỡng nên không đủ điều kiện tiêu chuẩn. Chỉ chọn ngoại lệ khi thực sự có lỗi thanh toán hoặc lỗi nền tảng.',
    REFUND_PROTECTED_MATERIALS_DOWNLOADED: 'Bạn đã tải toàn bộ tài liệu được bảo vệ nên yêu cầu cần được chuyển sang tranh chấp/xét duyệt thủ công.',
    REFUND_ENROLLMENT_MISSING: 'Hệ thống không tìm thấy quyền học. Hãy chọn “Lỗi nền tảng khiến tôi không thể học” để Finance xem xét thủ công.',
    REFUND_ACTIVE_REQUEST_EXISTS: 'Khóa học này đã có yêu cầu hoàn tiền đang hoạt động hoặc đã được chấp thuận.',
    REFUND_CANCELLATION_NOT_ALLOWED: 'Finance đã bắt đầu xử lý hoặc yêu cầu không còn ở trạng thái có thể hủy.',
  };
  return (code ? messages[code] : undefined)
    ?? serverMessage
    ?? 'Không thể xử lý yêu cầu hoàn tiền. Vui lòng kiểm tra thông tin và thử lại.';
}
