import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogContent,
  DialogTitle,
  Divider,
  Skeleton,
  Stack,
  Typography,
} from '@mui/material';
import type { ReactNode } from 'react';
import { formatCurrency } from '../../../shared/utils/formatCurrency';
import {
  formatWalletDateTime,
  referenceTypeLabel,
  transactionTypeLabel,
} from '../../wallet/constants/transactionLabels';
import { useTeacherWalletTransactionDetail } from '../hooks/useTeacherWalletTransactions';

interface WalletTransactionDetailDialogProps {
  transactionId: string | null;
  onClose: () => void;
}

function Row({ label, value }: { label: string; value: ReactNode }) {
  return (
    <Stack direction="row" spacing={2} sx={{ justifyContent: 'space-between', py: 1 }}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Typography variant="body2" sx={{ fontWeight: 700, textAlign: 'right', wordBreak: 'break-all' }}>
        {value}
      </Typography>
    </Stack>
  );
}

/**
 * UC-17 alternative flow 6a: one revenue-wallet transaction plus its related escrow or
 * withdrawal record, when the caller is permitted to see it.
 */
export function WalletTransactionDetailDialog({
  transactionId,
  onClose,
}: WalletTransactionDetailDialogProps) {
  const { data, isLoading, isError, refetch } = useTeacherWalletTransactionDetail(transactionId);

  return (
    <Dialog open={Boolean(transactionId)} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle sx={{ fontWeight: 800 }}>Chi tiết giao dịch</DialogTitle>
      <DialogContent dividers>
        {isLoading && (
          <Box>
            {[0, 1, 2, 3, 4].map((row) => (
              <Skeleton key={row} height={34} />
            ))}
          </Box>
        )}

        {isError && (
          <Alert
            severity="error"
            action={(
              <Button color="inherit" onClick={() => void refetch()}>
                Thử lại
              </Button>
            )}
          >
            Không tải được chi tiết giao dịch.
          </Alert>
        )}

        {data && (
          <>
            <Box sx={{ bgcolor: 'action.hover', borderRadius: 2, mb: 2, p: 2 }}>
              <Typography variant="caption" color="text.secondary">
                {transactionTypeLabel(data.transactionType)}
              </Typography>
              <Typography
                variant="h5"
                sx={{
                  color: data.direction === 'IN' ? 'success.main' : 'text.primary',
                  fontWeight: 800,
                }}
              >
                {data.direction === 'IN' ? '+' : '−'}
                {formatCurrency(data.amount)}
              </Typography>
            </Box>

            <Row label="Mã giao dịch" value={data.id} />
            <Row label="Thời gian" value={formatWalletDateTime(data.createdAt)} />
            <Row label="Chiều tiền" value={data.direction === 'IN' ? 'Tiền vào' : 'Tiền ra'} />
            <Row label="Loại tham chiếu" value={referenceTypeLabel(data.referenceType)} />
            <Row label="Mã tham chiếu" value={data.referenceCode ?? '—'} />
            {data.note && <Row label="Ghi chú" value={data.note} />}

            {data.relatedRecord && (
              <>
                <Divider sx={{ my: 2 }} />
                <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>
                  {referenceTypeLabel(data.relatedRecord.kind)} liên quan
                </Typography>
                <Row label="Mã" value={data.relatedRecord.code ?? '—'} />
                {data.relatedRecord.title && (
                  <Row label="Khóa học" value={data.relatedRecord.title} />
                )}
                <Row label="Trạng thái" value={data.relatedRecord.status ?? '—'} />
                <Row
                  label="Giá trị"
                  value={
                    data.relatedRecord.amount != null
                      ? formatCurrency(data.relatedRecord.amount)
                      : '—'
                  }
                />
                <Row label="Ngày tạo" value={formatWalletDateTime(data.relatedRecord.occurredAt)} />
              </>
            )}
          </>
        )}
      </DialogContent>
    </Dialog>
  );
}

export default WalletTransactionDetailDialog;
