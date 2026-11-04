import HistoryIcon from '@mui/icons-material/History';
import {
  Box,
  Card,
  CardContent,
  CardHeader,
  Chip,
  Divider,
  Stack,
  Typography,
} from '@mui/material';
import type { ModerationHistoryItem } from '../types/violation.types';

interface ModerationHistoryProps {
  items: ModerationHistoryItem[];
}

function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'long',
    timeStyle: 'short',
  }).format(new Date(value));
}

export function ModerationHistory({ items }: ModerationHistoryProps) {
  return (
    <Card variant="outlined">
      <CardHeader
        title="Lịch sử kiểm duyệt"
        avatar={<HistoryIcon color="action" />}
      />
      <Divider />
      <CardContent>
        {items.length === 0 ? (
          <Typography color="text.secondary">
            Chưa có quyết định kiểm duyệt trước đó.
          </Typography>
        ) : (
          <Stack spacing={2}>
            {items.map((history) => (
              <Box
                key={history.decisionId}
                sx={{ bgcolor: '#f8fafc', borderRadius: 2, p: 2 }}
              >
                <Stack
                  direction={{ xs: 'column', sm: 'row' }}
                  sx={{ justifyContent: 'space-between', gap: 1 }}
                >
                  <Typography variant="subtitle2" sx={{ fontWeight: 800 }}>
                    {history.decisionType}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {formatDate(history.decidedAt)} · {history.decidedBy}
                  </Typography>
                </Stack>
                <Typography variant="body2" sx={{ my: 1 }}>
                  {history.decisionNote}
                </Typography>
                {history.evidenceRequestedFrom && (
                  <Typography
                    component="p"
                    variant="caption"
                    color="text.secondary"
                    sx={{ mb: 1 }}
                  >
                    Yêu cầu bằng chứng từ: {history.evidenceRequestedFrom}
                  </Typography>
                )}
                <Stack
                  direction="row"
                  spacing={1}
                  useFlexGap
                  sx={{ flexWrap: 'wrap' }}
                >
                  {history.actions.map((action) => (
                    <Chip
                      key={action}
                      label={action}
                      size="small"
                      variant="outlined"
                    />
                  ))}
                </Stack>
              </Box>
            ))}
          </Stack>
        )}
      </CardContent>
    </Card>
  );
}
