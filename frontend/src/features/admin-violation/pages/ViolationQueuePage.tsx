import RefreshIcon from '@mui/icons-material/Refresh';
import ReportProblemOutlinedIcon from '@mui/icons-material/ReportProblemOutlined';
import VisibilityOutlinedIcon from '@mui/icons-material/VisibilityOutlined';
import {
  Alert,
  Box,
  Button,
  Chip,
  CircularProgress,
  MenuItem,
  Pagination,
  Paper,
  Skeleton,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import { useMemo, useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { PageHeader } from '../../../shared/components/PageHeader/PageHeader';
import { useViolationQueue } from '../hooks/useViolationQueue';
import { ViolationStatusBadge } from '../components/ViolationStatusBadge';
import type { ViolationReportStatus } from '../types/violation.types';

const PAGE_SIZE = 10;

const headerCellSx = {
  color: 'text.secondary',
  fontSize: 12,
  fontWeight: 800,
  letterSpacing: '0.04em',
  textTransform: 'uppercase',
};

function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'short',
    timeStyle: 'short',
  }).format(new Date(value));
}

const statusOptions: Array<{ value: ViolationReportStatus; label: string }> = [
  { value: 'PENDING_REVIEW', label: 'Chờ duyệt' },
  { value: 'IN_REVIEW', label: 'Đang xem xét' },
  { value: 'PENDING_EVIDENCE', label: 'Chờ bằng chứng' },
  { value: 'CORRECTION_REQUIRED', label: 'Yêu cầu chỉnh sửa' },
  { value: 'RESOLVED_UPHELD', label: 'Đã xác nhận vi phạm' },
  { value: 'RESOLVED_NO_VIOLATION', label: 'Không vi phạm' },
  { value: 'INVALID', label: 'Không hợp lệ' },
  { value: 'CANCELLED', label: 'Đã hủy' },
];

export function ViolationQueuePage() {
  const [page, setPage] = useState(0);
  const [statusFilter, setStatusFilter] = useState('');

  const params = useMemo(() => ({
    page,
    size: PAGE_SIZE,
    ...(statusFilter && { status: statusFilter }),
  }), [statusFilter, page]);

  const queue = useViolationQueue(params);

  return (
    <Box>
      <PageHeader
        title="Quản lý báo cáo vi phạm"
        subtitle="Xem xét và xử lý các báo cáo vi phạm khóa học và nội dung"
        breadcrumbs={[
          { label: 'Admin' },
          { label: 'Vi phạm' },
        ]}
      />

      {queue.isError && (
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={() => void queue.refetch()}>
              Thử lại
            </Button>
          }
          sx={{ mb: 2 }}
        >
          Không thể tải hàng đợi báo cáo vi phạm.
        </Alert>
      )}

      <Paper
        elevation={0}
        sx={{
          border: '1px solid',
          borderColor: 'divider',
          borderRadius: 2,
          overflow: 'hidden',
        }}
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
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <ReportProblemOutlinedIcon color="error" />
              <Typography variant="h6" sx={{ fontWeight: 800 }}>
                Hàng đợi báo cáo
              </Typography>
              {!queue.isLoading && (
                <Chip
                  size="small"
                  label={`${queue.data?.totalElements ?? 0} báo cáo`}
                  sx={{ bgcolor: '#fee2e2', color: 'error.main', fontWeight: 700 }}
                />
              )}
            </Stack>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
              Xử lý các nội dung vi phạm bản quyền hoặc tiêu chuẩn cộng đồng.
            </Typography>
          </Box>
          <Button
            variant="outlined"
            startIcon={queue.isFetching ? <CircularProgress size={16} color="inherit" /> : <RefreshIcon />}
            disabled={queue.isFetching}
            onClick={() => void queue.refetch()}
            sx={{ fontWeight: 700, textTransform: 'none' }}
          >
            Tải lại
          </Button>
        </Stack>

        <Box sx={{ borderBottom: '1px solid', borderColor: 'divider', p: { xs: 2, md: 3 } }}>
          <Stack direction={{ xs: 'column', lg: 'row' }} spacing={1.5}>
            <TextField
              select
              label="Trạng thái"
              size="small"
              value={statusFilter}
              onChange={(event) => {
                setStatusFilter(event.target.value);
                setPage(0);
              }}
              sx={{ minWidth: 200 }}
            >
              <MenuItem value="">Tất cả trạng thái</MenuItem>
              {statusOptions.map((option) => (
                <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
              ))}
            </TextField>
          </Stack>
        </Box>

        <TableContainer>
          <Table sx={{ minWidth: 980 }}>
            <TableHead>
              <TableRow sx={{ bgcolor: '#f8fafc' }}>
                <TableCell sx={headerCellSx}>Báo cáo bởi</TableCell>
                <TableCell sx={headerCellSx}>Đối tượng</TableCell>
                <TableCell sx={headerCellSx}>Lý do</TableCell>
                <TableCell sx={headerCellSx}>Trạng thái</TableCell>
                <TableCell sx={headerCellSx}>Ngày gửi</TableCell>
                <TableCell align="right" sx={headerCellSx}>Thao tác</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {queue.isLoading
                ? Array.from({ length: 5 }, (_, index) => (
                    <TableRow key={index}>
                      {Array.from({ length: 6 }, (__, cellIndex) => (
                        <TableCell key={cellIndex}><Skeleton /></TableCell>
                      ))}
                    </TableRow>
                  ))
                : queue.data?.content.length === 0
                  ? (
                      <TableRow>
                        <TableCell colSpan={6}>
                          <Box sx={{ py: 7, textAlign: 'center' }}>
                            <ReportProblemOutlinedIcon sx={{ color: 'text.disabled', fontSize: 42 }} />
                            <Typography sx={{ fontWeight: 700, mt: 1 }}>
                              Không có báo cáo nào
                            </Typography>
                          </Box>
                        </TableCell>
                      </TableRow>
                    )
                  : queue.data?.content.map((item) => (
                      <TableRow key={item.reportId} hover>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>
                            {item.reporterName}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>
                            {item.targetType === 'COURSE' ? 'Khóa học' : item.targetType}
                          </Typography>
                          <Typography variant="caption" color="text.secondary" noWrap sx={{ display: 'block', maxWidth: 150 }}>
                            {item.targetId}
                          </Typography>
                        </TableCell>
                        <TableCell>
                          <Typography variant="body2" noWrap sx={{ maxWidth: 300 }}>
                            {item.reason}
                          </Typography>
                        </TableCell>
                        <TableCell><ViolationStatusBadge status={item.status} /></TableCell>
                        <TableCell>
                          <Typography variant="body2">{formatDate(item.submittedAt)}</Typography>
                        </TableCell>
                        <TableCell align="right">
                          <Button
                            component={RouterLink}
                            to={`/admin/violations/${item.reportId}`}
                            variant="outlined"
                            size="small"
                            startIcon={<VisibilityOutlinedIcon />}
                            sx={{ fontWeight: 700, textTransform: 'none' }}
                          >
                            Chi tiết
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
            </TableBody>
          </Table>
        </TableContainer>

        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          sx={{
            alignItems: 'center',
            borderTop: '1px solid',
            borderColor: 'divider',
            justifyContent: 'space-between',
            px: 3,
            py: 2,
          }}
        >
          <Typography variant="body2" color="text.secondary">
            {queue.data?.totalElements ?? 0} báo cáo · Trang {page + 1}/{Math.max(queue.data?.totalPages ?? 1, 1)}
          </Typography>
          <Pagination
            color="primary"
            count={Math.max(queue.data?.totalPages ?? 1, 1)}
            page={page + 1}
            onChange={(_, value) => setPage(value - 1)}
            disabled={queue.isFetching}
            size="small"
          />
        </Stack>
      </Paper>
    </Box>
  );
}
