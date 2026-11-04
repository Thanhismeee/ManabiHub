import AttachFileOutlinedIcon from '@mui/icons-material/AttachFileOutlined';
import ImageOutlinedIcon from '@mui/icons-material/ImageOutlined';
import {
  Alert,
  Box,
  Button,
  ButtonBase,
  Card,
  CardContent,
  CardHeader,
  CircularProgress,
  Divider,
  Paper,
  Stack,
  Typography,
} from '@mui/material';
import { useEffect, useState } from 'react';
import { toast } from 'react-hot-toast';
import { ImageEvidencePreviewDialog } from '../../../shared/components/ImageEvidencePreviewDialog';
import { adminViolationService } from '../services/adminViolationService';
import type { ViolationEvidence } from '../types/violation.types';

interface PreviewImage {
  src: string;
  title: string;
}

const isImageEvidence = (item: ViolationEvidence) =>
  item.evidenceType === 'IMAGE' || item.contentType?.startsWith('image/');

function EvidenceItem({
  item,
  onPreview,
}: {
  item: ViolationEvidence;
  onPreview: (preview: PreviewImage) => void;
}) {
  const [previewUrl, setPreviewUrl] = useState<string>();
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewFailed, setPreviewFailed] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const imageEvidence = isImageEvidence(item);

  useEffect(() => {
    if (!imageEvidence) return;

    let active = true;
    let ownedObjectUrl: string | undefined;
    setPreviewLoading(true);
    setPreviewFailed(false);
    setPreviewUrl(undefined);

    void adminViolationService.loadEvidencePreview(item.accessUrl)
      .then(({ url, shouldRevoke }) => {
        if (!active) {
          if (shouldRevoke) URL.revokeObjectURL(url);
          return;
        }
        if (shouldRevoke) ownedObjectUrl = url;
        setPreviewUrl(url);
      })
      .catch(() => {
        if (active) setPreviewFailed(true);
      })
      .finally(() => {
        if (active) setPreviewLoading(false);
      });

    return () => {
      active = false;
      if (ownedObjectUrl) URL.revokeObjectURL(ownedObjectUrl);
    };
  }, [imageEvidence, item.accessUrl]);

  const downloadEvidence = async () => {
    try {
      setDownloading(true);
      await adminViolationService.downloadEvidence(item.accessUrl, item.displayName);
    } catch {
      toast.error('Không thể tải bằng chứng. Vui lòng thử lại.');
    } finally {
      setDownloading(false);
    }
  };

  return (
    <Paper variant="outlined" sx={{ p: 1.5 }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: { sm: 'center' } }}>
        {imageEvidence && (
          <ButtonBase
            aria-label={`Xem ảnh ${item.displayName}`}
            disabled={!previewUrl}
            onClick={() => previewUrl && onPreview({ src: previewUrl, title: item.displayName })}
            sx={{
              width: { xs: '100%', sm: 144 },
              height: 92,
              borderRadius: 1.5,
              overflow: 'hidden',
              bgcolor: 'action.hover',
              flexShrink: 0,
            }}
          >
            {previewLoading ? (
              <CircularProgress size={24} />
            ) : previewUrl ? (
              <Box
                component="img"
                src={previewUrl}
                alt={`Bằng chứng ${item.displayName}`}
                sx={{ width: '100%', height: '100%', objectFit: 'cover' }}
              />
            ) : (
              <Stack spacing={0.5} sx={{ alignItems: 'center', color: 'text.secondary' }}>
                <ImageOutlinedIcon />
                <Typography variant="caption">
                  {previewFailed ? 'Không thể xem trước' : 'Ảnh bằng chứng'}
                </Typography>
              </Stack>
            )}
          </ButtonBase>
        )}

        <Box sx={{ minWidth: 0, flex: 1 }}>
          <Typography variant="body2" sx={{ fontWeight: 700 }} noWrap>
            {item.displayName}
          </Typography>
          <Typography variant="caption" color="text.secondary">
            {item.evidenceType} · {new Date(item.submittedAt).toLocaleString('vi-VN')}
          </Typography>
          {imageEvidence && previewUrl && (
            <Typography variant="caption" color="success.main" sx={{ display: 'block', mt: 0.25 }}>
              Bấm vào ảnh để xem lớn và phóng to.
            </Typography>
          )}
        </Box>

        <Stack direction="row" spacing={1} sx={{ flexShrink: 0 }}>
          {imageEvidence && previewUrl && (
            <Button
              size="small"
              variant="contained"
              onClick={() => onPreview({ src: previewUrl, title: item.displayName })}
            >
              Xem ảnh
            </Button>
          )}
          <Button size="small" variant="outlined" onClick={downloadEvidence} disabled={downloading}>
            {downloading ? 'Đang tải...' : imageEvidence ? 'Tải ảnh' : 'Tải bằng chứng'}
          </Button>
        </Stack>
      </Stack>
    </Paper>
  );
}

export function EvidencePanel({ evidence }: { evidence: ViolationEvidence[] }) {
  const [previewImage, setPreviewImage] = useState<PreviewImage | null>(null);

  return (
    <>
      <Card variant="outlined">
        <CardHeader title="Bằng chứng" avatar={<AttachFileOutlinedIcon color="action" />} />
        <Divider />
        <CardContent>
          {evidence.length === 0 ? (
            <Alert severity="warning">
              Báo cáo chưa có bằng chứng đính kèm. Không nên áp dụng biện pháp nghiêm trọng
              khi chưa đủ căn cứ.
            </Alert>
          ) : (
            <Stack spacing={1.5}>
              {evidence.map((item) => (
                <EvidenceItem key={item.evidenceId} item={item} onPreview={setPreviewImage} />
              ))}
            </Stack>
          )}
        </CardContent>
      </Card>
      <ImageEvidencePreviewDialog
        open={Boolean(previewImage)}
        src={previewImage?.src}
        title={previewImage?.title}
        onClose={() => setPreviewImage(null)}
      />
    </>
  );
}
