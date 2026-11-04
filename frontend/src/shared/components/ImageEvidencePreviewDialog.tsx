import CloseOutlinedIcon from '@mui/icons-material/CloseOutlined';
import ZoomInOutlinedIcon from '@mui/icons-material/ZoomInOutlined';
import ZoomOutOutlinedIcon from '@mui/icons-material/ZoomOutOutlined';
import { Box, Dialog, DialogContent, DialogTitle, IconButton, Stack, Tooltip, Typography } from '@mui/material';
import { useEffect, useState } from 'react';

interface ImageEvidencePreviewDialogProps {
  open: boolean;
  src?: string;
  title?: string;
  onClose: () => void;
}

const MIN_ZOOM = 0.5;
const MAX_ZOOM = 3;
const ZOOM_STEP = 0.25;

export function ImageEvidencePreviewDialog({
  open,
  src,
  title = 'Ảnh bằng chứng',
  onClose,
}: ImageEvidencePreviewDialogProps) {
  const [zoom, setZoom] = useState(1);

  useEffect(() => {
    if (open) setZoom(1);
  }, [open, src]);

  return (
    <Dialog open={open && Boolean(src)} onClose={onClose} fullWidth maxWidth="lg">
      <DialogTitle sx={{ py: 1.25 }}>
        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
          <Typography variant="subtitle1" noWrap sx={{ flex: 1, fontWeight: 700 }}>
            {title}
          </Typography>
          <Tooltip title="Thu nhỏ">
            <span>
              <IconButton
                size="small"
                aria-label="Thu nhỏ ảnh"
                onClick={() => setZoom((current) => Math.max(MIN_ZOOM, current - ZOOM_STEP))}
                disabled={zoom <= MIN_ZOOM}
              >
                <ZoomOutOutlinedIcon />
              </IconButton>
            </span>
          </Tooltip>
          <Typography variant="caption" sx={{ minWidth: 42, textAlign: 'center' }}>
            {Math.round(zoom * 100)}%
          </Typography>
          <Tooltip title="Phóng to">
            <span>
              <IconButton
                size="small"
                aria-label="Phóng to ảnh"
                onClick={() => setZoom((current) => Math.min(MAX_ZOOM, current + ZOOM_STEP))}
                disabled={zoom >= MAX_ZOOM}
              >
                <ZoomInOutlinedIcon />
              </IconButton>
            </span>
          </Tooltip>
          <Tooltip title="Đóng">
            <IconButton size="small" aria-label="Đóng ảnh bằng chứng" onClick={onClose}>
              <CloseOutlinedIcon />
            </IconButton>
          </Tooltip>
        </Stack>
      </DialogTitle>
      <DialogContent
        dividers
        sx={{
          p: 0,
          height: '78vh',
          overflow: 'auto',
          bgcolor: '#111827',
        }}
      >
        <Box
          sx={{
            minWidth: '100%',
            minHeight: '100%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            p: 2,
          }}
        >
          {src && (
            <Box
              component="img"
              src={src}
              alt={title}
              onClick={() => setZoom((current) => (current === 1 ? 2 : 1))}
              sx={{
                display: 'block',
                width: zoom === 1 ? 'auto' : `${zoom * 100}%`,
                maxWidth: zoom === 1 ? '100%' : 'none',
                maxHeight: zoom === 1 ? '72vh' : 'none',
                objectFit: 'contain',
                cursor: zoom === 1 ? 'zoom-in' : 'zoom-out',
                transition: 'width 160ms ease',
              }}
            />
          )}
        </Box>
      </DialogContent>
    </Dialog>
  );
}
