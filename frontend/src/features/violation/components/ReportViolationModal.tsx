import ArrowBackOutlinedIcon from '@mui/icons-material/ArrowBackOutlined';
import AttachFileOutlinedIcon from '@mui/icons-material/AttachFileOutlined';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';
import {
  Alert,
  Box,
  Button,
  ButtonBase,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControl,
  FormControlLabel,
  FormHelperText,
  FormLabel,
  IconButton,
  LinearProgress,
  Paper,
  Radio,
  RadioGroup,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { Controller, useForm } from 'react-hook-form';
import { useMutation } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import type { AxiosError } from 'axios';
import { toast } from 'react-hot-toast';
import { ImageEvidencePreviewDialog } from '../../../shared/components/ImageEvidencePreviewDialog';
import { submitViolationReport, type ViolationReportRequest } from '../api/violationApi';

const REPORT_REASONS = [
  { value: 'MISLEADING_CONTENT', label: 'Nội dung không đúng với mô tả' },
  { value: 'INAPPROPRIATE_CONTENT', label: 'Nội dung không phù hợp hoặc phản cảm' },
  { value: 'COPYRIGHT_VIOLATION', label: 'Nội dung có dấu hiệu vi phạm bản quyền' },
  { value: 'FRAUD_OR_DECEPTION', label: 'Nội dung có dấu hiệu lừa đảo hoặc gây hiểu nhầm' },
  { value: 'TECHNICAL_ISSUE', label: 'Nội dung bị lỗi, không thể xem hoặc sử dụng' },
  { value: 'OTHER', label: 'Lý do khác' },
] as const;

const MAX_FILES = 3;
const MAX_FILE_SIZE = 5 * 1024 * 1024;
const ACCEPTED_FILE_TYPES = new Set(['application/pdf', 'image/png', 'image/jpeg']);

type ReportReasonValue = (typeof REPORT_REASONS)[number]['value'];

interface ReportFormValues {
  reasonOption: ReportReasonValue | '';
  description: string;
}

interface ApiErrorResponse {
  messageCode?: string;
  message?: string;
}

interface ReportViolationModalProps {
  open: boolean;
  onClose: () => void;
  targetType: 'COURSE' | 'LESSON' | 'LESSON_BLOCK' | 'REVIEW' | 'USER';
  targetId: string;
}

interface MutationPayload {
  request: ViolationReportRequest;
  evidenceFiles: File[];
}

interface SelectedEvidence {
  file: File;
  previewUrl?: string;
}

const formatFileSize = (size: number) => {
  if (size < 1024 * 1024) return `${Math.ceil(size / 1024)} KB`;
  return `${(size / (1024 * 1024)).toFixed(1)} MB`;
};

export const ReportViolationModal = ({ open, onClose, targetType, targetId }: ReportViolationModalProps) => {
  const [step, setStep] = useState<1 | 2>(1);
  const [evidenceFiles, setEvidenceFiles] = useState<SelectedEvidence[]>([]);
  const [previewEvidence, setPreviewEvidence] = useState<SelectedEvidence | null>(null);
  const [fileError, setFileError] = useState('');
  const fileInputRef = useRef<HTMLInputElement>(null);
  const {
    control,
    register,
    handleSubmit,
    reset,
    trigger,
    watch,
    formState: { errors },
  } = useForm<ReportFormValues>({
    defaultValues: {
      reasonOption: '',
      description: '',
    },
  });

  const selectedReason = watch('reasonOption');
  const selectedReasonLabel = REPORT_REASONS.find((option) => option.value === selectedReason)?.label;

  const mutation = useMutation({
    mutationFn: ({ request, evidenceFiles: files }: MutationPayload) =>
      submitViolationReport(request, files),
    onSuccess: () => {
      toast.success('Báo cáo vi phạm đã được gửi.');
      resetDialog();
      onClose();
    },
    onError: (error: AxiosError<ApiErrorResponse>) => {
      const messageCode = error.response?.data?.messageCode;
      if (messageCode === 'MSG-REP-002') {
        toast.error('Bạn đã gửi báo cáo tương tự trong thời gian gần đây.');
      } else {
        toast.error(error.response?.data?.message ?? 'Không thể gửi báo cáo. Vui lòng thử lại.');
      }
    },
  });

  const resetDialog = () => {
    evidenceFiles.forEach((item) => {
      if (item.previewUrl) URL.revokeObjectURL(item.previewUrl);
    });
    reset();
    setStep(1);
    setEvidenceFiles([]);
    setPreviewEvidence(null);
    setFileError('');
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const closeDialog = () => {
    if (mutation.isPending) return;
    resetDialog();
    onClose();
  };

  const continueToDetails = async () => {
    if (await trigger('reasonOption')) setStep(2);
  };

  const addEvidenceFiles = (files: FileList | null) => {
    if (!files) return;
    setFileError('');
    const candidates = Array.from(files);
    if (evidenceFiles.length + candidates.length > MAX_FILES) {
      setFileError(`Chỉ được đính kèm tối đa ${MAX_FILES} tệp.`);
      return;
    }

    const invalidType = candidates.find((file) => !ACCEPTED_FILE_TYPES.has(file.type));
    if (invalidType) {
      setFileError(`Tệp “${invalidType.name}” không đúng định dạng PDF, PNG hoặc JPEG.`);
      return;
    }
    const oversized = candidates.find((file) => file.size > MAX_FILE_SIZE);
    if (oversized) {
      setFileError(`Tệp “${oversized.name}” vượt quá giới hạn 5 MB.`);
      return;
    }

    setEvidenceFiles((current) => {
      const uniqueFiles = candidates.filter(
        (candidate) =>
          !current.some(
            (existing) =>
              existing.file.name === candidate.name &&
              existing.file.size === candidate.size &&
              existing.file.lastModified === candidate.lastModified,
          ),
      );
      return [
        ...current,
        ...uniqueFiles.map((file) => ({
          file,
          previewUrl: file.type.startsWith('image/') ? URL.createObjectURL(file) : undefined,
        })),
      ];
    });
    if (fileInputRef.current) fileInputRef.current.value = '';
  };

  const removeEvidenceFile = (index: number) => {
    const removed = evidenceFiles[index];
    if (removed?.previewUrl) URL.revokeObjectURL(removed.previewUrl);
    if (previewEvidence === removed) setPreviewEvidence(null);
    setEvidenceFiles((current) => current.filter((_, fileIndex) => fileIndex !== index));
    setFileError('');
  };

  const onSubmit = (data: ReportFormValues) => {
    if (!selectedReasonLabel) return;
    mutation.mutate({
      request: {
        targetType,
        targetId,
        reason: selectedReasonLabel,
        description: data.description.trim(),
      },
      evidenceFiles: evidenceFiles.map((item) => item.file),
    });
  };

  return (
    <>
    <Dialog open={open} onClose={closeDialog} fullWidth maxWidth="sm">
      {mutation.isPending && <LinearProgress />}
      <DialogTitle>
        <Typography variant="h6" component="span" sx={{ fontWeight: 700 }}>
          Báo cáo vi phạm
        </Typography>
        <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.25 }}>
          Bước {step}/2 · {step === 1 ? 'Chọn lý do' : 'Cung cấp thông tin'}
        </Typography>
      </DialogTitle>
      <form
        onSubmit={step === 2 ? handleSubmit(onSubmit) : (event) => event.preventDefault()}
        noValidate
      >
        <DialogContent dividers>
          {step === 1 ? (
            <>
              <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
                Chọn lý do phù hợp nhất với vấn đề bạn phát hiện. Bạn sẽ bổ sung mô tả và bằng chứng ở bước tiếp theo.
              </Typography>
              <Controller
                name="reasonOption"
                control={control}
                rules={{ required: 'Vui lòng chọn một lý do báo cáo.' }}
                render={({ field }) => (
                  <FormControl fullWidth error={Boolean(errors.reasonOption)} disabled={mutation.isPending}>
                    <FormLabel id="report-reason-label" sx={{ fontWeight: 600, color: 'text.primary' }}>
                      Lý do báo cáo
                    </FormLabel>
                    <RadioGroup {...field} aria-labelledby="report-reason-label" sx={{ mt: 1 }}>
                      {REPORT_REASONS.map((option) => (
                        <FormControlLabel
                          key={option.value}
                          value={option.value}
                          control={<Radio size="small" />}
                          label={option.label}
                          sx={{
                            mx: 0,
                            mb: 0.75,
                            border: '1px solid',
                            borderColor: field.value === option.value ? 'error.main' : 'divider',
                            borderRadius: 1.5,
                            px: 1,
                            py: 0.25,
                            bgcolor: field.value === option.value ? 'rgba(211, 47, 47, 0.05)' : 'transparent',
                          }}
                        />
                      ))}
                    </RadioGroup>
                    {errors.reasonOption && <FormHelperText>{errors.reasonOption.message}</FormHelperText>}
                  </FormControl>
                )}
              />
            </>
          ) : (
            <Stack spacing={2.5}>
              <Alert severity="info" icon={false}>
                <Typography variant="caption" color="text.secondary">Lý do đã chọn</Typography>
                <Typography variant="body2" sx={{ fontWeight: 700 }}>{selectedReasonLabel}</Typography>
              </Alert>

              <TextField
                {...register('description', {
                  validate: (value) => {
                    const description = value.trim();
                    if (!description) return 'Vui lòng nhập mô tả chi tiết.';
                    if (description.length < 10) return 'Mô tả cần có ít nhất 10 ký tự.';
                    if (description.length > 2000) return 'Mô tả không được vượt quá 2.000 ký tự.';
                    return true;
                  },
                })}
                label="Mô tả chi tiết"
                placeholder="Nêu rõ nội dung vi phạm, vị trí xảy ra và thông tin giúp quản trị viên kiểm tra..."
                multiline
                rows={5}
                fullWidth
                required
                error={Boolean(errors.description)}
                helperText={errors.description?.message ?? 'Tối đa 2.000 ký tự.'}
                disabled={mutation.isPending}
                slotProps={{ htmlInput: { maxLength: 2000 } }}
              />

              <Box>
                <Typography variant="subtitle2" sx={{ mb: 0.75 }}>
                  Bằng chứng đính kèm <Typography component="span" variant="caption" color="text.secondary">(không bắt buộc)</Typography>
                </Typography>
                <Paper
                  variant="outlined"
                  sx={{
                    p: 2,
                    textAlign: 'center',
                    borderStyle: 'dashed',
                    bgcolor: 'action.hover',
                  }}
                >
                  <input
                    ref={fileInputRef}
                    type="file"
                    hidden
                    multiple
                    accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg"
                    onChange={(event) => addEvidenceFiles(event.target.files)}
                    data-testid="evidence-input"
                  />
                  <AttachFileOutlinedIcon color="action" />
                  <Typography variant="body2" sx={{ mt: 0.5, mb: 1 }}>
                    PDF, PNG hoặc JPEG · tối đa 3 tệp · 5 MB mỗi tệp
                  </Typography>
                  <Button
                    variant="outlined"
                    size="small"
                    onClick={() => fileInputRef.current?.click()}
                    disabled={mutation.isPending || evidenceFiles.length >= MAX_FILES}
                  >
                    Chọn tệp bằng chứng
                  </Button>
                </Paper>
                {fileError && <FormHelperText error sx={{ mx: 1.75 }}>{fileError}</FormHelperText>}

                {evidenceFiles.length > 0 && (
                  <Stack spacing={1} sx={{ mt: 1.5 }}>
                    {evidenceFiles.map((item, index) => (
                      <Paper key={`${item.file.name}-${item.file.lastModified}`} variant="outlined" sx={{ px: 1.5, py: 1 }}>
                        <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                          {item.previewUrl ? (
                            <ButtonBase
                              aria-label={`Xem ảnh ${item.file.name}`}
                              onClick={() => setPreviewEvidence(item)}
                              sx={{ borderRadius: 1, overflow: 'hidden', flexShrink: 0 }}
                            >
                              <Box
                                component="img"
                                src={item.previewUrl}
                                alt={`Xem trước ${item.file.name}`}
                                sx={{ width: 72, height: 52, objectFit: 'cover' }}
                              />
                            </ButtonBase>
                          ) : (
                            <AttachFileOutlinedIcon fontSize="small" color="action" />
                          )}
                          <Box sx={{ minWidth: 0, flex: 1 }}>
                            <Typography variant="body2" noWrap>{item.file.name}</Typography>
                            <Typography variant="caption" color="text.secondary">
                              {formatFileSize(item.file.size)}{item.previewUrl ? ' · Bấm vào ảnh để phóng to' : ''}
                            </Typography>
                          </Box>
                          <IconButton
                            size="small"
                            aria-label={`Xóa ${item.file.name}`}
                            onClick={() => removeEvidenceFile(index)}
                            disabled={mutation.isPending}
                          >
                            <DeleteOutlineIcon fontSize="small" />
                          </IconButton>
                        </Stack>
                      </Paper>
                    ))}
                  </Stack>
                )}
              </Box>
            </Stack>
          )}
        </DialogContent>
        <DialogActions sx={{ px: 3, py: 2 }}>
          {step === 1 ? (
            <>
              <Button onClick={closeDialog} color="inherit" disabled={mutation.isPending}>Hủy</Button>
              <Button type="button" variant="contained" color="error" onClick={continueToDetails}>
                Tiếp tục
              </Button>
            </>
          ) : (
            <>
              <Button
                type="button"
                startIcon={<ArrowBackOutlinedIcon />}
                onClick={() => setStep(1)}
                color="inherit"
                disabled={mutation.isPending}
              >
                Quay lại
              </Button>
              <Button type="submit" variant="contained" color="error" disabled={mutation.isPending}>
                {mutation.isPending ? 'Đang gửi...' : 'Gửi báo cáo'}
              </Button>
            </>
          )}
        </DialogActions>
      </form>
    </Dialog>
    <ImageEvidencePreviewDialog
      open={Boolean(previewEvidence?.previewUrl)}
      src={previewEvidence?.previewUrl}
      title={previewEvidence?.file.name}
      onClose={() => setPreviewEvidence(null)}
    />
    </>
  );
};
