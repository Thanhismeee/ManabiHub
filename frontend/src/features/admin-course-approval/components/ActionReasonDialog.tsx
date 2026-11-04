import React, { useState } from 'react';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogContentText,
  DialogActions,
  Button,
  TextField,
} from '@mui/material';

interface ActionReasonDialogProps {
  open: boolean;
  title: string;
  message: string;
  confirmLabel?: string;
  cancelLabel?: string;
  confirmColor?: 'primary' | 'error' | 'warning' | 'info' | 'success';
  onConfirm: (reason: string) => void;
  onCancel: () => void;
}

export const ActionReasonDialog: React.FC<ActionReasonDialogProps> = ({
  open,
  title,
  message,
  confirmLabel = 'Xác nhận',
  cancelLabel = 'Hủy',
  confirmColor = 'primary',
  onConfirm,
  onCancel,
}) => {
  const [reason, setReason] = useState('');

  const handleConfirm = () => {
    if (!reason.trim()) return;
    onConfirm(reason);
    setReason('');
  };

  const handleCancel = () => {
    setReason('');
    onCancel();
  };

  return (
    <Dialog open={open} onClose={handleCancel} maxWidth="sm" fullWidth>
      <DialogTitle>{title}</DialogTitle>
      <DialogContent>
        <DialogContentText sx={{ mb: 2 }}>{message}</DialogContentText>
        <TextField
          autoFocus
          margin="dense"
          id="reason"
          label="Lý do (Bắt buộc)"
          type="text"
          fullWidth
          variant="outlined"
          multiline
          rows={4}
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          required
        />
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 3 }}>
        <Button onClick={handleCancel} color="inherit">
          {cancelLabel}
        </Button>
        <Button onClick={handleConfirm} color={confirmColor} variant="contained" disableElevation disabled={!reason.trim()}>
          {confirmLabel}
        </Button>
      </DialogActions>
    </Dialog>
  );
};
