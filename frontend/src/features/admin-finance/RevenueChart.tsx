import { Alert, Box, Stack, Typography } from '@mui/material';
import { formatMoney, formatRevenueBucket } from './financeDisplay';
import type { RevenueGranularity, RevenuePoint } from './types';

const SERIES = [
  { key: 'grossSales' as const, label: 'Doanh số', color: '#2563eb' },
  { key: 'refundAmount' as const, label: 'Hoàn tiền', color: '#d97706' },
  { key: 'platformRevenue' as const, label: 'Doanh thu nền tảng', color: '#16a34a' },
  { key: 'expenses' as const, label: 'Tổng chi phí', color: '#dc2626' },
  { key: 'net' as const, label: 'Lợi nhuận vận hành', color: '#7c3aed' },
];

export function RevenueChart({ points, granularity }: {
  points: RevenuePoint[];
  granularity: RevenueGranularity;
}) {
  const width = 1120;
  const height = 360;
  const pad = 58;
  const valueFor = (point: RevenuePoint, key: typeof SERIES[number]['key']) => {
    if (key === 'expenses') return Number(point.paymentFees) + Number(point.operatingExpenses);
    if (key === 'net') return Number(point.platformRevenue) - Number(point.paymentFees) - Number(point.operatingExpenses);
    return Number(point[key]);
  };
  const values = points.flatMap((point) => SERIES.map((item) => valueFor(point, item.key)));
  const chartMin = Math.min(...values, 0);
  const chartMax = Math.max(...values, 1);
  const chartSpan = Math.max(chartMax - chartMin, 1);
  const x = (index: number) => pad + (points.length <= 1
    ? (width - pad * 2) / 2
    : index * (width - pad * 2) / (points.length - 1));
  const y = (value: number) => pad + (chartMax - value) / chartSpan * (height - pad * 2);

  if (points.length === 0) {
    return <Alert severity="info">Không có bản ghi trong khoảng thời gian đã lọc.</Alert>;
  }

  return (
    <Box>
      <Box sx={{ overflowX: 'auto' }}>
        <svg viewBox={`0 0 ${width} ${height}`} style={{ minWidth: 760, width: '100%', display: 'block' }} role="img" aria-label="Biểu đồ doanh thu, hoàn tiền và chi phí">
          {[0, 0.25, 0.5, 0.75, 1].map((ratio) => {
            const tickValue = chartMin + chartSpan * ratio;
            return (
              <g key={ratio}>
                <line x1={pad} x2={width - pad} y1={y(tickValue)} y2={y(tickValue)} stroke={Math.abs(tickValue) < 0.01 ? '#94a3b8' : '#e5e7eb'} />
                <text x={pad - 8} y={y(tickValue) + 4} textAnchor="end" fontSize="11" fill="#6b7280">
                  {new Intl.NumberFormat('vi-VN', { notation: 'compact' }).format(tickValue)}
                </text>
              </g>
            );
          })}
          {SERIES.map((item) => {
            const linePoints = points.map((point, index) => `${x(index)},${y(valueFor(point, item.key))}`).join(' ');
            return (
              <g key={item.key}>
                <polyline points={linePoints} fill="none" stroke={item.color} strokeWidth="2.5" strokeLinejoin="round" strokeLinecap="round" />
                {points.map((point, index) => {
                  const value = valueFor(point, item.key);
                  return (
                    <circle key={`${item.key}-${point.bucket}`} cx={x(index)} cy={y(value)} r="5" fill={item.color} tabIndex={0}>
                      <title>{`${item.label} · ${formatRevenueBucket(point.bucket, granularity)}: ${formatMoney(value)}`}</title>
                    </circle>
                  );
                })}
              </g>
            );
          })}
          {points.map((point, index) => (
            (index === 0 || index === points.length - 1 || index % Math.max(1, Math.ceil(points.length / 6)) === 0)
              ? <text key={point.bucket} x={x(index)} y={height - 14} textAnchor="middle" fontSize="11" fill="#6b7280">{formatRevenueBucket(point.bucket, granularity)}</text>
              : null
          ))}
        </svg>
      </Box>
      <Stack direction="row" sx={{ gap: 3, flexWrap: 'wrap', justifyContent: 'center' }}>
        {SERIES.map((item) => (
          <Stack direction="row" sx={{ gap: 1, alignItems: 'center' }} key={item.key}>
            <Box sx={{ width: 12, height: 4, bgcolor: item.color }} />
            <Typography variant="caption">{item.label}</Typography>
          </Stack>
        ))}
      </Stack>
    </Box>
  );
}
