import { useEffect, useMemo, useState } from 'react';
import { isAxiosError } from 'axios';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Checkbox,
  Chip,
  FormControlLabel,
  Grid,
  MenuItem,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import UploadFileIcon from '@mui/icons-material/UploadFile';
import WorkspacePremiumOutlinedIcon from '@mui/icons-material/WorkspacePremiumOutlined';
import {
  defaultFramework,
  defaultLevel,
  frameworkOptions,
  languageLabel,
  LEARNING_LANGUAGES,
  levelOptions,
  proficiencyLabel,
  type LearningLanguage,
  type ProficiencyFramework,
} from '../../shared/learning/languageProfiles';
import {
  getTeacherQualifications,
  getTeacherQualificationEvidenceObjectUrl,
  submitTeacherQualification,
  type TeacherCredentialType,
  type TeacherQualificationResponse,
} from './teacherKycApi';

interface TeacherQualificationsPanelProps {
  identityVerified: boolean;
}

const CREDENTIAL_TYPES: Array<{ value: TeacherCredentialType; label: string }> = [
  { value: 'OFFICIAL_CERTIFICATE', label: 'Chứng chỉ chính thức' },
  { value: 'UNIVERSITY_DEGREE', label: 'Bằng cao đẳng/đại học' },
  { value: 'SOFL_INTERNAL_APPROVAL', label: 'Xác nhận năng lực nội bộ SOFL' },
  { value: 'EMPLOYMENT_RECORD', label: 'Hồ sơ công tác/kinh nghiệm' },
];

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Đang duyệt',
  APPROVED: 'Đã duyệt',
  REJECTED: 'Cần bổ sung',
};

export function TeacherQualificationsPanel({ identityVerified }: TeacherQualificationsPanelProps) {
  const [items, setItems] = useState<TeacherQualificationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState<{ severity: 'success' | 'error'; text: string } | null>(null);
  const [language, setLanguage] = useState<LearningLanguage>('KOREAN');
  const [framework, setFramework] = useState<ProficiencyFramework>('TOPIK');
  const [level, setLevel] = useState('1');
  const [credentialType, setCredentialType] = useState<TeacherCredentialType>('OFFICIAL_CERTIFICATE');
  const [credentialCode, setCredentialCode] = useState('');
  const [issuer, setIssuer] = useState('');
  const [evidence, setEvidence] = useState<File | null>(null);
  const [agreementAccepted, setAgreementAccepted] = useState(false);

  async function load() {
    try {
      setLoading(true);
      setItems(await getTeacherQualifications());
    } catch {
      setMessage({ severity: 'error', text: 'Không thể tải danh sách hồ sơ năng lực.' });
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, []);

  const hasOpenApplication = useMemo(
    () => items.some((item) => item.learningLanguage === language
      && item.proficiencyFramework === framework
      && (item.status === 'PENDING' || item.status === 'APPROVED')),
    [framework, items, language],
  );

  async function handleSubmit() {
    if (!identityVerified) {
      setMessage({ severity: 'error', text: 'Hãy hoàn tất xác minh danh tính ở Bước 1 trước.' });
      return;
    }
    if (!level.trim() || !issuer.trim() || !evidence || !agreementAccepted) {
      setMessage({ severity: 'error', text: 'Vui lòng nhập đủ cấp độ, đơn vị xác nhận, tài liệu và cam kết.' });
      return;
    }
    if (credentialType === 'OFFICIAL_CERTIFICATE' && !credentialCode.trim()) {
      setMessage({ severity: 'error', text: 'Chứng chỉ chính thức cần có mã chứng chỉ.' });
      return;
    }
    if (evidence.size > 8 * 1024 * 1024) {
      setMessage({ severity: 'error', text: 'Tài liệu không được vượt quá 8MB.' });
      return;
    }
    try {
      setSubmitting(true);
      await submitTeacherQualification({
        learningLanguage: language,
        proficiencyFramework: framework,
        frameworkVersion: framework === 'SOFL_INTERNAL' ? 'SOFL' : 'CURRENT',
        proficiencyLevel: level,
        credentialType,
        credentialCode,
        issuer,
        evidence,
        copyrightAgreementAccepted: agreementAccepted,
      });
      setEvidence(null);
      setCredentialCode('');
      setIssuer('');
      setAgreementAccepted(false);
      setMessage({ severity: 'success', text: 'Đã gửi hồ sơ. Course Manager sẽ kiểm tra tài liệu và phạm vi được phép giảng dạy.' });
      await load();
    } catch (error) {
      const text = isAxiosError(error)
        ? error.response?.data?.message ?? 'Không thể gửi hồ sơ năng lực.'
        : 'Không thể gửi hồ sơ năng lực.';
      setMessage({ severity: 'error', text });
    } finally {
      setSubmitting(false);
    }
  }

  async function openEvidence(path: string) {
    try {
      const objectUrl = await getTeacherQualificationEvidenceObjectUrl(path);
      window.open(objectUrl, '_blank', 'noopener,noreferrer');
      window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60_000);
    } catch {
      setMessage({ severity: 'error', text: 'Không thể mở tài liệu xác minh.' });
    }
  }

  return (
    <Card variant="outlined" sx={{ borderRadius: 2 }}>
      <CardContent sx={{ p: { xs: 2, md: 3 } }}>
        <Stack spacing={2.5}>
          <Stack direction="row" spacing={1.5} sx={{ alignItems: 'flex-start' }}>
            <WorkspacePremiumOutlinedIcon color="primary" />
            <Box>
              <Typography component="h3" sx={{ fontSize: 20, fontWeight: 700 }}>
                Năng lực giảng dạy theo ngôn ngữ
              </Typography>
              <Typography color="text.secondary" sx={{ mt: 0.5, fontSize: 14, lineHeight: 1.6 }}>
                Nộp riêng hồ sơ tiếng Hàn, tiếng Trung hoặc tiếng Nhật. SOFL có thể dùng chứng chỉ
                chính thức, bằng chuyên ngành hoặc quyết định đánh giá năng lực nội bộ có người chịu trách nhiệm xác nhận.
              </Typography>
            </Box>
          </Stack>

          {!identityVerified && (
            <Alert severity="info">Mục này sẽ mở sau khi danh tính được xác minh thành công.</Alert>
          )}
          {message && <Alert severity={message.severity} onClose={() => setMessage(null)}>{message.text}</Alert>}

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                select
                fullWidth
                label="Ngôn ngữ giảng dạy"
                value={language}
                disabled={!identityVerified || submitting}
                onChange={(event) => {
                  const nextLanguage = event.target.value as LearningLanguage;
                  const nextFramework = defaultFramework(nextLanguage);
                  setLanguage(nextLanguage);
                  setFramework(nextFramework);
                  setLevel(defaultLevel(nextFramework));
                }}
              >
                {LEARNING_LANGUAGES.map((option) => (
                  <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                select
                fullWidth
                label="Khung năng lực"
                value={framework}
                disabled={!identityVerified || submitting}
                onChange={(event) => {
                  const nextFramework = event.target.value as ProficiencyFramework;
                  setFramework(nextFramework);
                  setLevel(defaultLevel(nextFramework));
                  setCredentialType(nextFramework === 'SOFL_INTERNAL'
                    ? 'SOFL_INTERNAL_APPROVAL'
                    : 'OFFICIAL_CERTIFICATE');
                }}
              >
                {frameworkOptions(language).map((option) => (
                  <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              {framework === 'SOFL_INTERNAL' ? (
                <TextField
                  fullWidth
                  label="Mã cấp độ nội bộ"
                  value={level}
                  disabled={!identityVerified || submitting}
                  onChange={(event) => setLevel(event.target.value.toUpperCase())}
                />
              ) : (
                <TextField
                  select
                  fullWidth
                  label="Cấp độ chứng minh"
                  value={level}
                  disabled={!identityVerified || submitting}
                  onChange={(event) => setLevel(event.target.value)}
                >
                  {levelOptions(framework).map((option) => (
                    <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                  ))}
                </TextField>
              )}
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                select
                fullWidth
                label="Loại hồ sơ"
                value={credentialType}
                disabled={!identityVerified || submitting}
                onChange={(event) => setCredentialType(event.target.value as TeacherCredentialType)}
              >
                {CREDENTIAL_TYPES
                  .filter((option) => framework === 'SOFL_INTERNAL'
                    ? true
                    : option.value !== 'SOFL_INTERNAL_APPROVAL')
                  .map((option) => (
                    <MenuItem key={option.value} value={option.value}>{option.label}</MenuItem>
                  ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                label="Đơn vị cấp/xác nhận"
                placeholder="VD: NIIED, Chinese Testing International, SOFL"
                value={issuer}
                disabled={!identityVerified || submitting}
                onChange={(event) => setIssuer(event.target.value)}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 4 }}>
              <TextField
                fullWidth
                label="Mã chứng chỉ (nếu có)"
                required={credentialType === 'OFFICIAL_CERTIFICATE'}
                value={credentialCode}
                disabled={!identityVerified || submitting}
                onChange={(event) => setCredentialCode(event.target.value)}
              />
            </Grid>
          </Grid>

          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: { sm: 'center' } }}>
            <Button
              component="label"
              variant="outlined"
              startIcon={<UploadFileIcon />}
              disabled={!identityVerified || submitting}
            >
              Chọn tài liệu PDF/JPG/PNG
              <input
                hidden
                type="file"
                accept="application/pdf,image/jpeg,image/png"
                onChange={(event) => setEvidence(event.target.files?.[0] ?? null)}
              />
            </Button>
            <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
              {evidence?.name ?? 'Tối đa 8MB'}
            </Typography>
          </Stack>

          <FormControlLabel
            control={<Checkbox checked={agreementAccepted} onChange={(event) => setAgreementAccepted(event.target.checked)} />}
            label="Tôi cam kết tài liệu là hợp pháp, thông tin khai báo là đúng và tôi có quyền sử dụng tài liệu này để xác minh chuyên môn."
          />
          <Button
            variant="contained"
            onClick={() => { void handleSubmit(); }}
            disabled={!identityVerified || submitting || hasOpenApplication}
            sx={{ alignSelf: { sm: 'flex-start' } }}
          >
            {submitting ? 'Đang gửi...' : hasOpenApplication ? 'Đã có hồ sơ đang xử lý' : 'Gửi hồ sơ năng lực'}
          </Button>

          <Box>
            <Typography sx={{ fontWeight: 700, mb: 1 }}>Hồ sơ đã nộp</Typography>
            {loading ? (
              <Typography color="text.secondary">Đang tải...</Typography>
            ) : items.length === 0 ? (
              <Typography color="text.secondary">Chưa có hồ sơ năng lực nào.</Typography>
            ) : (
              <Stack spacing={1}>
                {items.map((item) => (
                  <Box key={item.id} sx={{ p: 1.5, border: '1px solid', borderColor: 'divider', borderRadius: 1.5 }}>
                    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ justifyContent: 'space-between' }}>
                      <Box>
                        <Typography sx={{ fontWeight: 700 }}>
                          {languageLabel(item.learningLanguage)} • {proficiencyLabel(item.proficiencyFramework, item.credentialLevel)}
                        </Typography>
                        <Typography variant="body2" color="text.secondary">
                          {item.issuer} • {item.evidenceFileName}
                        </Typography>
                        {item.reviewNote && (
                          <Typography variant="body2" color="error.main" sx={{ mt: 0.5 }}>
                            Phản hồi: {item.reviewNote}
                          </Typography>
                        )}
                      </Box>
                      <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
                        {item.evidenceUrl && (
                          <Button size="small" onClick={() => { void openEvidence(item.evidenceUrl as string); }}>
                            Xem tài liệu
                          </Button>
                        )}
                        <Chip
                          size="small"
                          label={STATUS_LABELS[item.status] ?? item.status}
                          color={item.status === 'APPROVED' ? 'success' : item.status === 'REJECTED' ? 'error' : 'warning'}
                        />
                      </Stack>
                    </Stack>
                  </Box>
                ))}
              </Stack>
            )}
          </Box>
        </Stack>
      </CardContent>
    </Card>
  );
}
