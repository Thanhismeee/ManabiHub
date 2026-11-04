import AccountBalanceOutlinedIcon from '@mui/icons-material/AccountBalanceOutlined';
import HourglassTopOutlinedIcon from '@mui/icons-material/HourglassTopOutlined';
import ReceiptLongOutlinedIcon from '@mui/icons-material/ReceiptLongOutlined';
import SavingsOutlinedIcon from '@mui/icons-material/SavingsOutlined';
import { Box, Grid, Paper, Stack, Typography } from '@mui/material';
import { formatCurrency } from '../../../shared/utils/formatCurrency';
import type { TeacherRevenueSummary as TeacherRevenueSummaryData } from '../types/wallet.types';

type Props = { summary: TeacherRevenueSummaryData };

const metrics = [
  { key: 'totalGrossRevenue', label: 'Tổng doanh thu gộp', icon: ReceiptLongOutlinedIcon, color: '#1d4ed8', bg: '#eff6ff' },
  { key: 'settledRevenue', label: 'Đã settle vào ví', icon: SavingsOutlinedIcon, color: '#15803d', bg: '#f0fdf4' },
  { key: 'heldInEscrow', label: 'Đang hold trong sàn', icon: HourglassTopOutlinedIcon, color: '#c2410c', bg: '#fff7ed' },
  { key: 'availableInWallet', label: 'Có thể rút hiện tại', icon: AccountBalanceOutlinedIcon, color: '#be123c', bg: '#fff1f2' },
] as const;

export function TeacherRevenueSummary({ summary }: Props) {
  return (
    <Paper elevation={0} sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 2, mb: 2.5, p: { xs: 2, md: 3 } }}>
      <Typography variant="h6" sx={{ fontWeight: 800 }}>Tổng quan doanh thu</Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Tiền đang hold không thể rút; chỉ phần đã settle và còn trong ví mới được yêu cầu rút.
      </Typography>
      <Grid container spacing={1.5} sx={{ mb: 2 }}>
        {metrics.map(({ key, label, icon: Icon, color, bg }) => (
          <Grid key={key} size={{ xs: 12, sm: 6, lg: 3 }}>
            <Box sx={{ bgcolor: bg, borderRadius: 2, p: 2 }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
                <Icon sx={{ color }} />
                <Typography variant="body2" sx={{ fontWeight: 700 }}>{label}</Typography>
              </Stack>
              <Typography sx={{ color, fontSize: '1.25rem', fontWeight: 900 }}>
                {formatCurrency(summary[key])}
              </Typography>
            </Box>
          </Grid>
        ))}
      </Grid>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3} sx={{ mb: 2 }}>
        <Typography variant="body2">Lượt mua: <strong>{summary.totalSales}</strong></Typography>
        <Typography variant="body2">Lượt hoàn tiền: <strong>{summary.totalRefundedSales}</strong></Typography>
        <Typography variant="body2">Đã rút: <strong>{formatCurrency(summary.totalWithdrawn)}</strong></Typography>
        <Typography variant="body2">Đang chờ rút: <strong>{formatCurrency(summary.reservedForWithdrawal)}</strong></Typography>
      </Stack>
      <Box sx={{ overflowX: 'auto' }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>Thu nhập theo khóa học</Typography>
        <Box component="table" sx={{ borderCollapse: 'collapse', minWidth: 760, width: '100%', '& th, & td': { borderBottom: '1px solid', borderColor: 'divider', px: 1, py: 1, textAlign: 'left' }, '& th': { color: 'text.secondary', fontSize: '0.75rem' } }}>
          <thead><tr><th>Khóa học</th><th>Lượt mua</th><th>Hoàn</th><th>Doanh thu gộp</th><th>Đã settle</th><th>Đang hold</th><th>Hoàn tiền</th></tr></thead>
          <tbody>
            {summary.courseRevenue.map((course) => (
              <tr key={course.courseId}>
                <td>{course.courseTitle}</td>
                <td>{course.purchaseCount}</td>
                <td>{course.refundedCount}</td>
                <td>{formatCurrency(course.grossRevenue)}</td>
                <td>{formatCurrency(course.releasedAmount)}</td>
                <td>{formatCurrency(course.heldAmount)}</td>
                <td>{formatCurrency(course.refundedAmount)}</td>
              </tr>
            ))}
            {summary.courseRevenue.length === 0 && <tr><td colSpan={7}>Chưa có dữ liệu doanh thu.</td></tr>}
          </tbody>
        </Box>
      </Box>
    </Paper>
  );
}
