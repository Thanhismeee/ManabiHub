import RefreshIcon from '@mui/icons-material/Refresh';
import {
  Alert,
  Box,
  Button,
  CircularProgress,
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
import { useState } from 'react';
import { EmptyState } from '../../../shared/components/EmptyState/EmptyState';
import { formatCurrency } from '../../../shared/utils/formatCurrency';
import {
  TEACHER_TRANSACTION_TYPES,
  TRANSACTION_TYPE_LABELS,
  formatWalletDateTime,
  transactionTypeLabel,
} from '../../wallet/constants/transactionLabels';
import type {
  WalletDirection,
  WalletTransaction,
  WalletTransactionFilter,
  WalletTransactionType,
} from '../../wallet/types';
import { useTeacherWalletTransactions } from '../hooks/useTeacherWalletTransactions';
import { WalletTransactionDetailDialog } from './WalletTransactionDetailDialog';

const PAGE_SIZE_OPTIONS = [10, 25, 50];

/**
 * Revenue-wallet transaction history with filters (UC-17 steps 3, 6, 7) and a
 * per-transaction detail dialog (flow 6a).
 */
export function WalletTransactionHistory() {
  const [filter, setFilter] = useState<WalletTransactionFilter>({ page: 0, size: 10 });
  const [referenceCode, setReferenceCode] = useState('');
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const query = useTeacherWalletTransactions(filter);
  const history = query.data;

  const patch = (next: Partial<WalletTransactionFilter>) =>
    setFilter((prev) => ({ ...prev, ...next, page: 0 }));

  const dateRangeInvalid = Boolean(
    filter.fromDate && filter.toDate && filter.fromDate > filter.toDate,
  );

  return (
    <Paper
      elevation={0}
      sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, mb: 4, overflow: 'hidden' }}
    >
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{
          alignItems: { xs: 'stretch', sm: 'center' },
          borderBottom: '1px solid',
          borderColor: 'divider',
          justifyContent: 'space-between',
          p: { xs: 2, md: 3 },
        }}
      >
        <Box>
          <Typography variant="h6" sx={{ fontWeight: 800 }}>
            Lịch sử giao dịch ví
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Toàn bộ biến động số dư: tạm giữ, đối soát, giữ tiền chờ rút và chi trả.
          </Typography>
        </Box>
        <Button
          variant="outlined"
          startIcon={query.isFetching ? <CircularProgress size={16} color="inherit" /> : <RefreshIcon />}
          disabled={query.isFetching}
          onClick={() => void query.refetch()}
          sx={{ fontWeight: 700, textTransform: 'none' }}
        >
          Tải lại
        </Button>
      </Stack>

      {/* Filters */}
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={2}
        sx={{ borderBottom: '1px solid', borderColor: 'divider', p: { xs: 2, md: 3 } }}
      >
        <TextField
          select
          size="small"
          label="Loại giao dịch"
          value={filter.types?.[0] ?? ''}
          onChange={(e) =>
            patch({ types: e.target.value ? [e.target.value as WalletTransactionType] : undefined })
          }
          sx={{ minWidth: 210 }}
        >
          <MenuItem value="">Tất cả</MenuItem>
          {TEACHER_TRANSACTION_TYPES.map((type) => (
            <MenuItem key={type} value={type}>
              {TRANSACTION_TYPE_LABELS[type]}
            </MenuItem>
          ))}
        </TextField>

        <TextField
          select
          size="small"
          label="Chiều tiền"
          value={filter.direction ?? ''}
          onChange={(e) =>
            patch({ direction: (e.target.value || undefined) as WalletDirection | undefined })
          }
          sx={{ minWidth: 150 }}
        >
          <MenuItem value="">Tất cả</MenuItem>
          <MenuItem value="IN">Tiền vào</MenuItem>
          <MenuItem value="OUT">Tiền ra</MenuItem>
        </TextField>

        <TextField
          size="small"
          type="date"
          label="Từ ngày"
          slotProps={{ inputLabel: { shrink: true } }}
          value={filter.fromDate ?? ''}
          onChange={(e) => patch({ fromDate: e.target.value || undefined })}
          error={dateRangeInvalid}
        />
        <TextField
          size="small"
          type="date"
          label="Đến ngày"
          slotProps={{ inputLabel: { shrink: true } }}
          value={filter.toDate ?? ''}
          onChange={(e) => patch({ toDate: e.target.value || undefined })}
          error={dateRangeInvalid}
          helperText={dateRangeInvalid ? 'Ngày kết thúc phải sau ngày bắt đầu' : undefined}
        />

        <TextField
          size="small"
          label="Mã tham chiếu"
          placeholder="Mã đơn hàng…"
          value={referenceCode}
          onChange={(e) => setReferenceCode(e.target.value)}
          onBlur={() => patch({ referenceCode: referenceCode || undefined })}
          onKeyDown={(e) => {
            if (e.key === 'Enter') patch({ referenceCode: referenceCode || undefined });
          }}
        />
      </Stack>

      {query.isError ? (
        <Alert
          severity="error"
          action={(
            <Button color="inherit" onClick={() => void query.refetch()}>
              Thử lại
            </Button>
          )}
          sx={{ m: 3 }}
        >
          Không thể tải lịch sử giao dịch ví.
        </Alert>
      ) : query.isLoading ? (
        <Box sx={{ p: 3 }}>
          {[0, 1, 2, 3].map((row) => (
            <Skeleton key={row} height={58} sx={{ mb: 1 }} />
          ))}
        </Box>
      ) : !history?.content.length ? (
        <EmptyState
          title="Chưa có giao dịch nào"
          description="Thay đổi bộ lọc hoặc quay lại sau khi ví phát sinh biến động."
        />
      ) : (
        <>
          <TableContainer>
            <Table sx={{ minWidth: 760 }}>
              <TableHead>
                <TableRow>
                  <TableCell>Thời gian</TableCell>
                  <TableCell>Loại giao dịch</TableCell>
                  <TableCell>Mã tham chiếu</TableCell>
                  <TableCell align="right">Số tiền</TableCell>
                  <TableCell align="right">Chi tiết</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {history.content.map((transaction: WalletTransaction) => (
                  <TableRow
                    key={transaction.id}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => setSelectedId(transaction.id)}
                  >
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>
                      {formatWalletDateTime(transaction.createdAt)}
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>
                        {transactionTypeLabel(transaction.transactionType)}
                      </Typography>
                      {transaction.note && (
                        <Typography variant="caption" color="text.secondary">
                          {transaction.note}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>{transaction.referenceCode ?? '—'}</TableCell>
                    <TableCell
                      align="right"
                      sx={{
                        color: transaction.direction === 'IN' ? 'success.main' : 'text.primary',
                        fontWeight: 800,
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {transaction.direction === 'IN' ? '+' : '−'}
                      {formatCurrency(transaction.amount)}
                    </TableCell>
                    <TableCell align="right">
                      <Button
                        size="small"
                        onClick={(event) => {
                          event.stopPropagation();
                          setSelectedId(transaction.id);
                        }}
                        sx={{ fontWeight: 700, textTransform: 'none' }}
                      >
                        Xem
                      </Button>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>

          <TablePagination
            component="div"
            count={history.totalElements}
            page={history.page}
            rowsPerPage={history.size}
            rowsPerPageOptions={PAGE_SIZE_OPTIONS}
            labelRowsPerPage="Số dòng mỗi trang"
            onPageChange={(_, page) => setFilter((prev) => ({ ...prev, page }))}
            onRowsPerPageChange={(e) =>
              setFilter((prev) => ({ ...prev, page: 0, size: Number(e.target.value) }))
            }
          />
        </>
      )}

      <WalletTransactionDetailDialog
        transactionId={selectedId}
        onClose={() => setSelectedId(null)}
      />
    </Paper>
  );
}

export default WalletTransactionHistory;
