import {
  Alert,
  Box,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  Typography,
} from '@mui/material';
import type { ResolveViolationRequest } from '../types/violation.types';
import { moderationActionContent } from './moderationActionContent';

interface ModerationConfirmationDialogProps {
  payload: ResolveViolationRequest | null;
  isPending: boolean;
  onClose: () => void;
  onConfirm: (payload: ResolveViolationRequest) => void;
}

export function ModerationConfirmationDialog({
  payload,
  isPending,
  onClose,
  onConfirm,
}: ModerationConfirmationDialogProps) {
  const isSevere = Boolean(
    payload?.actions?.some(
      (action) =>
        action !== 'NONE' && moderationActionContent[action].severe,
    ),
  );

  return (
    <Dialog
      open={payload !== null}
      onClose={() => {
        if (!isPending) {
          onClose();
        }
      }}
      maxWidth="sm"
      fullWidth
    >
      <DialogTitle>Xác nhận hậu quả kiểm duyệt</DialogTitle>
      <DialogContent>
        <Alert severity={isSevere ? 'error' : 'warning'} sx={{ mb: 2 }}>
          Hành động được áp dụng nguyên tử và được ghi audit. Không thể hoàn tác
          bằng nút “Quay lại”.
        </Alert>
        <Stack spacing={1.5}>
          {payload?.actions?.map((action) =>
            action === 'NONE' ? null : (
              <Box key={action}>
                <Typography sx={{ fontWeight: 800 }}>
                  {moderationActionContent[action].label}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {moderationActionContent[action].consequence}
                </Typography>
              </Box>
            ),
          )}
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={isPending}>
          Kiểm tra lại
        </Button>
        <Button
          color={isSevere ? 'error' : 'primary'}
          variant="contained"
          disabled={isPending || payload === null}
          onClick={() => {
            if (payload) {
              onConfirm(payload);
            }
          }}
        >
          {isPending ? 'Đang xử lý…' : 'Xác nhận và áp dụng'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
