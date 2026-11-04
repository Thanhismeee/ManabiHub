import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  CardHeader,
  Chip,
  CircularProgress,
  Divider,
  Grid,
  Stack,
  Typography,
} from '@mui/material';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { toast } from 'react-hot-toast';
import { useNavigate, useParams } from 'react-router-dom';
import { PageHeader } from '../../../shared/components/PageHeader/PageHeader';
import { EvidencePanel } from '../components/EvidencePanel';
import { ModerationConfirmationDialog } from '../components/ModerationConfirmationDialog';
import { ModerationDecisionPanel } from '../components/ModerationDecisionPanel';
import { ModerationHistory } from '../components/ModerationHistory';
import type { EnforcementAction } from '../components/moderationActionContent';
import { ViolationStatusBadge } from '../components/ViolationStatusBadge';
import { useResolveViolation } from '../hooks/useResolveViolation';
import { useViolationDetail } from '../hooks/useViolationDetail';
import {
  createResolveViolationSchema,
  parseTargetIds,
  type ResolveViolationFormValues,
} from '../schemas/resolveViolationSchema';
import type { ResolveViolationRequest } from '../types/violation.types';
import {
  getViolationErrorMessage,
  isViolationConflict,
} from '../utils/violationMessages';

const terminalStatuses = [
  'RESOLVED_UPHELD',
  'RESOLVED_NO_VIOLATION',
  'INVALID',
  'CANCELLED',
];

function formatDate(value: string) {
  return new Intl.DateTimeFormat('vi-VN', {
    dateStyle: 'long',
    timeStyle: 'short',
  }).format(new Date(value));
}

export function ViolationDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const detailQuery = useViolationDetail(id ?? '');
  const resolveMutation = useResolveViolation();
  const [confirmation, setConfirmation] = useState<ResolveViolationRequest | null>(null);

  const {
    register,
    handleSubmit,
    watch,
    setValue,
    setError,
    formState: { errors },
  } = useForm<ResolveViolationFormValues>({
    defaultValues: {
      decision: 'UPHELD',
      decisionNote: '',
      actions: [],
      evidenceRequestedFrom: 'REPORTER',
      targetIdsText: '',
    },
  });

  const decision = watch('decision');
  const selectedActions = watch('actions');
  const evidenceRequestedFrom = watch('evidenceRequestedFrom');
  const detail = detailQuery.data;

  if (detailQuery.isLoading) {
    return (
      <Box sx={{ display: 'flex', justifyContent: 'center', p: 8 }}>
        <CircularProgress aria-label="Đang tải báo cáo" />
      </Box>
    );
  }

  if (detailQuery.isError || !detail) {
    return (
      <Alert
        severity="error"
        action={
          <Button color="inherit" onClick={() => void detailQuery.refetch()}>
            Thử lại
          </Button>
        }
      >
        Không thể tải thông tin báo cáo.
      </Alert>
    );
  }

  const isResolved = terminalStatuses.includes(detail.status);
  const availableActions = detail.availableActions.filter(
    (action): action is EnforcementAction => action !== 'NONE',
  );

  const toggleAction = (action: EnforcementAction) => {
    const next = selectedActions.includes(action)
      ? selectedActions.filter((item) => item !== action)
      : [...selectedActions, action];
    setValue('actions', next, { shouldDirty: true, shouldValidate: true });
  };

  const submitForm = (rawValues: ResolveViolationFormValues) => {
    if (resolveMutation.isPending) {
      return;
    }
    const result = createResolveViolationSchema(
      detail.target.targetType,
    ).safeParse(rawValues);
    if (!result.success) {
      for (const issue of result.error.issues) {
        const field = issue.path[0];
        if (
          field === 'decisionNote' ||
          field === 'actions' ||
          field === 'targetIdsText'
        ) {
          setError(field, { message: issue.message });
        }
      }
      return;
    }

    const payload: ResolveViolationRequest = {
      decision: result.data.decision,
      decisionNote: result.data.decisionNote,
    };
    if (result.data.decision === 'PENDING_EVIDENCE') {
      payload.evidenceRequestedFrom = result.data.evidenceRequestedFrom;
    }
    if (result.data.decision === 'UPHELD') {
      payload.actions = result.data.actions;
      payload.targetIds = parseTargetIds(result.data.targetIdsText);
      setConfirmation(payload);
      return;
    }
    executeResolve(payload);
  };

  const executeResolve = (payload: ResolveViolationRequest) => {
    resolveMutation.mutate(
      { id: detail.reportId, data: payload },
      {
        onSuccess: () => {
          setConfirmation(null);
          toast.success('Quyết định kiểm duyệt đã được áp dụng.');
        },
        onError: (error: unknown) => {
          setConfirmation(null);
          toast.error(getViolationErrorMessage(error));
          if (isViolationConflict(error)) {
            void detailQuery.refetch();
          }
        },
      },
    );
  };

  return (
    <Box>
      <PageHeader
        title="Chi tiết báo cáo vi phạm"
        subtitle={`Mã báo cáo ${detail.reportId}`}
        breadcrumbs={[
          { label: 'Admin' },
          { label: 'Vi phạm', href: '/admin/violations' },
          { label: 'Chi tiết' },
        ]}
      />

      <Button
        startIcon={<ArrowBackIcon />}
        onClick={() => navigate('/admin/violations')}
        sx={{ mb: 3 }}
      >
        Quay lại hàng đợi
      </Button>

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, lg: 8 }}>
          <Stack spacing={3}>
            <Card variant="outlined">
              <CardHeader
                title="Thông tin báo cáo"
                action={<ViolationStatusBadge status={detail.status} />}
              />
              <Divider />
              <CardContent>
                <Grid container spacing={2.5}>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <Typography variant="caption" color="text.secondary">
                      Người báo cáo
                    </Typography>
                    <Typography sx={{ fontWeight: 700 }}>
                      {detail.reporter?.displayName ?? 'Ẩn danh'}
                    </Typography>
                    {detail.reporter && (
                      <Typography variant="caption" color="text.secondary">
                        {detail.reporter.role ?? 'Người dùng'} · Tuổi tài khoản{' '}
                        {detail.reporter.accountAge ?? 'chưa xác định'}
                      </Typography>
                    )}
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <Typography variant="caption" color="text.secondary">
                      Thời điểm gửi
                    </Typography>
                    <Typography sx={{ fontWeight: 600 }}>
                      {formatDate(detail.submittedAt)}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <Typography variant="caption" color="text.secondary">
                      Lý do báo cáo
                    </Typography>
                    <Typography sx={{ mt: 0.5, whiteSpace: 'pre-wrap', fontWeight: 600 }}>
                      {detail.reason}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 12 }}>
                    <Typography variant="caption" color="text.secondary">
                      Mô tả chi tiết
                    </Typography>
                    <Typography sx={{ mt: 0.5, whiteSpace: 'pre-wrap' }}>
                      {detail.description?.trim() || 'Không có mô tả bổ sung.'}
                    </Typography>
                  </Grid>
                </Grid>
              </CardContent>
            </Card>

            <Card variant="outlined">
              <CardHeader title="Đối tượng bị báo cáo" />
              <Divider />
              <CardContent>
                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <Typography variant="caption" color="text.secondary">
                      Đối tượng
                    </Typography>
                    <Typography sx={{ fontWeight: 700 }}>
                      {detail.target.contentTitle ??
                        detail.target.courseTitle ??
                        detail.target.targetType}
                    </Typography>
                    <Typography variant="caption" color="text.secondary">
                      {detail.target.targetType} · {detail.target.targetId}
                    </Typography>
                  </Grid>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <Typography variant="caption" color="text.secondary">
                      Trạng thái hiện tại
                    </Typography>
                    <Box sx={{ mt: 0.5 }}>
                      <Chip
                        size="small"
                        label={detail.target.currentStatus ?? 'Không xác định'}
                        variant="outlined"
                      />
                    </Box>
                  </Grid>
                  {detail.target.affectedUserName && (
                    <Grid size={{ xs: 12 }}>
                      <Typography variant="caption" color="text.secondary">
                        Người dùng chịu ảnh hưởng
                      </Typography>
                      <Typography sx={{ fontWeight: 600 }}>
                        {detail.target.affectedUserName}
                      </Typography>
                    </Grid>
                  )}
                </Grid>
              </CardContent>
            </Card>

            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Alert severity={detail.previousWarnings > 0 ? 'warning' : 'info'}>
                  <strong>{detail.previousWarnings}</strong> vi phạm trước đây trên đối tượng này.
                </Alert>
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <Alert severity={detail.paidEnrollmentCount > 0 ? 'warning' : 'info'}>
                  <strong>{detail.paidEnrollmentCount}</strong> người mua trả phí cần được bảo toàn
                  quyền lợi.
                </Alert>
              </Grid>
            </Grid>

            <EvidencePanel evidence={detail.evidence} />

            <ModerationHistory items={detail.moderationHistory} />
          </Stack>
        </Grid>

        <Grid size={{ xs: 12, lg: 4 }}>
          <ModerationDecisionPanel
            isResolved={isResolved}
            isPending={resolveMutation.isPending}
            decision={decision}
            selectedActions={selectedActions}
            evidenceRequestedFrom={evidenceRequestedFrom}
            availableActions={availableActions}
            targetType={detail.target.targetType}
            severeActionAllowed={detail.severeActionAllowed}
            errors={errors}
            register={register}
            setValue={setValue}
            onToggleAction={toggleAction}
            onSubmit={handleSubmit(submitForm)}
          />
        </Grid>
      </Grid>

      <ModerationConfirmationDialog
        payload={confirmation}
        isPending={resolveMutation.isPending}
        onClose={() => setConfirmation(null)}
        onConfirm={executeResolve}
      />
    </Box>
  );
}
