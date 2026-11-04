import GavelIcon from '@mui/icons-material/Gavel';
import ShieldOutlinedIcon from '@mui/icons-material/ShieldOutlined';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  CircularProgress,
  Divider,
  FormControl,
  FormControlLabel,
  FormGroup,
  FormHelperText,
  InputLabel,
  MenuItem,
  Select,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import type { FormEventHandler } from 'react';
import type {
  FieldErrors,
  UseFormRegister,
  UseFormSetValue,
} from 'react-hook-form';
import type { ResolveViolationFormValues } from '../schemas/resolveViolationSchema';
import type {
  EvidenceRequestedFrom,
  ModerationActionType,
  ModerationDecisionType,
} from '../types/violation.types';
import {
  moderationActionContent,
  type EnforcementAction,
} from './moderationActionContent';

interface ModerationDecisionPanelProps {
  isResolved: boolean;
  isPending: boolean;
  decision: ModerationDecisionType;
  selectedActions: ModerationActionType[];
  evidenceRequestedFrom?: EvidenceRequestedFrom;
  availableActions: EnforcementAction[];
  targetType: string;
  severeActionAllowed: boolean;
  errors: FieldErrors<ResolveViolationFormValues>;
  register: UseFormRegister<ResolveViolationFormValues>;
  setValue: UseFormSetValue<ResolveViolationFormValues>;
  onToggleAction: (action: EnforcementAction) => void;
  onSubmit: FormEventHandler<HTMLFormElement>;
}

export function ModerationDecisionPanel({
  isResolved,
  isPending,
  decision,
  selectedActions,
  evidenceRequestedFrom,
  availableActions,
  targetType,
  severeActionAllowed,
  errors,
  register,
  setValue,
  onToggleAction,
  onSubmit,
}: ModerationDecisionPanelProps) {
  return (
    <Card variant="outlined" sx={{ position: { lg: 'sticky' }, top: 24 }}>
      <CardHeader
        title="Quyết định kiểm duyệt"
        avatar={<GavelIcon color="primary" />}
        sx={{ bgcolor: '#f8fafc' }}
      />
      <Divider />
      <CardContent>
        {isResolved ? (
          <Alert severity="info">
            Báo cáo đã đóng. Mọi quyết định và hành động được giữ trong lịch sử
            audit.
          </Alert>
        ) : (
          <Box component="form" onSubmit={onSubmit} noValidate>
            <Stack spacing={2.5}>
              <FormControl fullWidth size="small">
                <InputLabel id="moderation-decision-label">
                  Quyết định
                </InputLabel>
                <Select
                  labelId="moderation-decision-label"
                  label="Quyết định"
                  value={decision}
                  onChange={(event) => {
                    const next = event.target.value as ModerationDecisionType;
                    setValue('decision', next, { shouldDirty: true });
                    if (next !== 'UPHELD') {
                      setValue('actions', []);
                    }
                  }}
                >
                  <MenuItem value="UPHELD">Xác nhận vi phạm</MenuItem>
                  <MenuItem value="DISMISSED">Bác bỏ báo cáo</MenuItem>
                  <MenuItem value="PENDING_EVIDENCE">
                    Yêu cầu thêm bằng chứng
                  </MenuItem>
                  <MenuItem value="CORRECTION_REQUIRED">
                    Yêu cầu chỉnh sửa
                  </MenuItem>
                </Select>
              </FormControl>

              {decision === 'UPHELD' && (
                <FormControl error={Boolean(errors.actions)}>
                  <Typography variant="subtitle2" sx={{ fontWeight: 800, mb: 1 }}>
                    Biện pháp xử lý được phép
                  </Typography>
                  {availableActions.length === 0 ? (
                    <Alert severity="warning">
                      Vai trò hiện tại không có biện pháp phù hợp với đối tượng
                      này.
                    </Alert>
                  ) : (
                    <FormGroup>
                      {availableActions.map((action) => (
                        <FormControlLabel
                          key={action}
                          control={
                            <Switch
                              checked={selectedActions.includes(action)}
                              onChange={() => onToggleAction(action)}
                              color={
                                moderationActionContent[action].severe
                                  ? 'error'
                                  : 'primary'
                              }
                            />
                          }
                          label={
                            <Box>
                              <Typography
                                variant="body2"
                                sx={{ fontWeight: 700 }}
                              >
                                {moderationActionContent[action].label}
                              </Typography>
                              <Typography
                                variant="caption"
                                color="text.secondary"
                              >
                                {moderationActionContent[action].consequence}
                              </Typography>
                            </Box>
                          }
                          sx={{ alignItems: 'flex-start', mb: 1 }}
                        />
                      ))}
                    </FormGroup>
                  )}
                  {errors.actions?.message && (
                    <FormHelperText>{errors.actions.message}</FormHelperText>
                  )}
                </FormControl>
              )}

              {decision === 'UPHELD' &&
                selectedActions.includes('REMOVE_CONTENT') &&
                targetType === 'COURSE' && (
                  <TextField
                    label="ID lesson/review cần ẩn"
                    placeholder="UUID, UUID"
                    helperText={
                      errors.targetIdsText?.message ??
                      'Nhập một hoặc nhiều UUID, phân tách bằng dấu phẩy.'
                    }
                    error={Boolean(errors.targetIdsText)}
                    {...register('targetIdsText')}
                  />
                )}

              {decision === 'PENDING_EVIDENCE' && (
                <FormControl
                  fullWidth
                  size="small"
                  error={Boolean(errors.evidenceRequestedFrom)}
                >
                  <InputLabel id="evidence-provider-label">
                    Người cần bổ sung
                  </InputLabel>
                  <Select
                    labelId="evidence-provider-label"
                    label="Người cần bổ sung"
                    value={evidenceRequestedFrom ?? ''}
                    onChange={(event) =>
                      setValue(
                        'evidenceRequestedFrom',
                        event.target.value as EvidenceRequestedFrom,
                        { shouldDirty: true, shouldValidate: true },
                      )
                    }
                  >
                    <MenuItem value="REPORTER">Người báo cáo</MenuItem>
                    <MenuItem value="CREATOR">
                      Giáo viên sở hữu nội dung
                    </MenuItem>
                    <MenuItem value="BOTH">Cả hai bên</MenuItem>
                  </Select>
                  {errors.evidenceRequestedFrom?.message && (
                    <FormHelperText>
                      {errors.evidenceRequestedFrom.message}
                    </FormHelperText>
                  )}
                </FormControl>
              )}

              <TextField
                label="Ghi chú quyết định"
                multiline
                minRows={4}
                slotProps={{ htmlInput: { maxLength: 2000 } }}
                error={Boolean(errors.decisionNote)}
                helperText={
                  errors.decisionNote?.message ??
                  'Nêu rõ căn cứ và hậu quả để lưu audit, tối đa 2.000 ký tự.'
                }
                {...register('decisionNote')}
              />

              {severeActionAllowed && (
                <Alert severity="warning" icon={<ShieldOutlinedIcon />}>
                  Bạn đang có quyền xử lý nghiêm trọng. Chỉ dùng Ban/Freeze khi
                  bằng chứng đã được xác minh.
                </Alert>
              )}

              <Button
                type="submit"
                variant="contained"
                size="large"
                fullWidth
                disabled={
                  isPending ||
                  (availableActions.length === 0 && decision === 'UPHELD')
                }
              >
                {isPending ? (
                  <CircularProgress size={22} color="inherit" />
                ) : (
                  'Áp dụng quyết định'
                )}
              </Button>
            </Stack>
          </Box>
        )}
      </CardContent>
    </Card>
  );
}
