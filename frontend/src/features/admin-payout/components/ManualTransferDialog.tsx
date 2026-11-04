import CloseIcon from '@mui/icons-material/Close';
import UploadFileOutlinedIcon from '@mui/icons-material/UploadFileOutlined';
import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  IconButton,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useState, type FormEvent } from 'react';
import { useConfirmManualTransfer } from '../hooks/useConfirmManualTransfer';
import type { PayoutDetail } from '../types/payout.types';

interface ManualTransferDialogProps {
  detail: PayoutDetail;
  onClose: () => void;
}

const MAX_PROOF_SIZE = 5 * 1024 * 1024;
const ALLOWED_PROOF_TYPES = ['application/pdf', 'image/png', 'image/jpeg'];

export function ManualTransferDialog({
  detail,
  onClose,
}: ManualTransferDialogProps) {
  const mutation = useConfirmManualTransfer();
  const [transactionReference, setTransactionReference] = useState('');
  const [transferredAt, setTransferredAt] = useState(() =>
    toLocalDateTimeInput(new Date()),
  );
  const [note, setNote] = useState('');
  const [proof, setProof] = useState<File | null>(null);
  const [validationError, setValidationError] = useState<string | null>(null);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    const reference = transactionReference.trim();
    if (!reference || reference.length > 100) {
      setValidationError('Mã giao dịch là bắt buộc và không vượt quá 100 ký tự.');
      return;
    }
    if (!proof) {
      setValidationError('Vui lòng chọn chứng từ chuyển khoản.');
      return;
    }
    if (proof.size > MAX_PROOF_SIZE || !ALLOWED_PROOF_TYPES.includes(proof.type)) {
      setValidationError('Chứng từ phải là PDF, PNG hoặc JPEG và không vượt quá 5 MB.');
      return;
    }
    if (!transferredAt || new Date(transferredAt).getTime() > Date.now()) {
      setValidationError('Thời điểm chuyển tiền không hợp lệ.');
      return;
    }
    if (note.trim().length > 500) {
      setValidationError('Ghi chú không được vượt quá 500 ký tự.');
      return;
    }

    setValidationError(null);
    mutation.mutate(
      {
        withdrawalRequestId: detail.withdrawalRequestId,
        payload: {
          transactionReference: reference,
          transferredAmount: detail.requestedAmount,
          transferredAt: new Date(transferredAt).toISOString(),
          ...(note.trim() && { note: note.trim() }),
        },
        proof,
      },
      { onSuccess: onClose },
    );
  };

  return (
    <Dialog
      open
      onClose={mutation.isPending ? undefined : onClose}
      maxWidth="sm"
      fullWidth
      component="form"
      onSubmit={submit}
    >
      <DialogTitle component="div" sx={{ borderBottom: '1px solid', borderColor: 'divider' }}>
        <Stack direction="row" spacing={2} sx={{ alignItems: 'flex-start' }}>
          <Box sx={{ flex: 1 }}>
            <Typography variant="h6" sx={{ fontWeight: 800 }}>
              Xác nhận chuyển khoản thủ công
            </Typography>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              Chỉ xác nhận khi tiền đã được chuyển thực tế và có chứng từ hợp lệ.
            </Typography>
          </Box>
          <IconButton aria-label="Đóng" disabled={mutation.isPending} onClick={onClose}>
            <CloseIcon />
          </IconButton>
        </Stack>
      </DialogTitle>

      <DialogContent sx={{ pt: '24px !important' }}>
        <Alert severity="warning" sx={{ mb: 2.5 }}>
          Thao tác này sẽ ghi sổ thanh toán <strong>{formatVnd(detail.requestedAmount)}</strong>.
          Chứng từ chỉ Finance Manager có quyền tải.
        </Alert>

        <Stack spacing={2}>
          <TextField
            label="Mã giao dịch ngân hàng"
            value={transactionReference}
            onChange={(event) => setTransactionReference(event.target.value)}
            placeholder="VD: VCB-20260726-001234"
            required
            autoFocus
            slotProps={{ htmlInput: { maxLength: 100 } }}
          />
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
            <TextField
              label="Số tiền đã chuyển"
              value={formatVnd(detail.requestedAmount)}
              slotProps={{ input: { readOnly: true } }}
              sx={{ flex: 1 }}
            />
            <TextField
              type="datetime-local"
              label="Thời điểm chuyển"
              value={transferredAt}
              onChange={(event) => setTransferredAt(event.target.value)}
              required
              slotProps={{
                htmlInput: { max: toLocalDateTimeInput(new Date()) },
                inputLabel: { shrink: true },
              }}
              sx={{ flex: 1 }}
            />
          </Stack>

          <Box
            component="label"
            sx={{
              alignItems: 'center',
              bgcolor: proof ? '#f0fdf4' : '#f8fafc',
              border: '1px dashed',
              borderColor: proof ? '#86efac' : 'divider',
              borderRadius: 2,
              cursor: 'pointer',
              display: 'flex',
              gap: 1.5,
              justifyContent: 'center',
              px: 2,
              py: 3,
              transition: 'border-color 160ms ease',
              '&:hover': { borderColor: 'primary.main' },
            }}
          >
            <UploadFileOutlinedIcon color={proof ? 'success' : 'action'} />
            <Box>
              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                {proof ? proof.name : 'Chọn chứng từ chuyển khoản'}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                PDF, PNG hoặc JPEG · tối đa 5 MB
              </Typography>
            </Box>
            <input
              type="file"
              accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
              hidden
              onChange={(event) => {
                setProof(event.target.files?.[0] ?? null);
                setValidationError(null);
              }}
            />
          </Box>

          <TextField
            label="Ghi chú nội bộ"
            value={note}
            onChange={(event) => setNote(event.target.value)}
            multiline
            rows={3}
            helperText={`${note.length}/500 ký tự`}
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
        </Stack>

        {validationError && (
          <Alert severity="error" sx={{ mt: 2 }}>{validationError}</Alert>
        )}
      </DialogContent>

      <DialogActions sx={{ borderTop: '1px solid', borderColor: 'divider', px: 3, py: 2 }}>
        <Button color="inherit" disabled={mutation.isPending} onClick={onClose} sx={{ textTransform: 'none' }}>
          Quay lại
        </Button>
        <Button
          type="submit"
          variant="contained"
          disabled={mutation.isPending}
          sx={{ fontWeight: 700, textTransform: 'none' }}
        >
          {mutation.isPending ? 'Đang xác nhận...' : 'Xác nhận đã chuyển tiền'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}

function toLocalDateTimeInput(date: Date) {
  const offset = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offset).toISOString().slice(0, 16);
}

function formatVnd(value: number) {
  return new Intl.NumberFormat('vi-VN', {
    currency: 'VND',
    maximumFractionDigits: 0,
    style: 'currency',
  }).format(value);
}
