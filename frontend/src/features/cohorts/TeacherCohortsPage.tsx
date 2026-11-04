import { useCallback, useEffect, useMemo, useState } from 'react';
import { isAxiosError } from 'axios';
import { Link } from 'react-router-dom';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  IconButton,
  MenuItem,
  Paper,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from '@mui/material';
import AddIcon from '@mui/icons-material/Add';
import DeleteOutlineIcon from '@mui/icons-material/Delete';
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined';
import PersonAddAltOutlinedIcon from '@mui/icons-material/PersonAddAltOutlined';
import { PageHeader } from '../../shared/components/PageHeader/PageHeader';
import { LoadingState } from '../../shared/components/LoadingState/LoadingState';
import { fetchCourseDrafts, type CourseDraftResponse } from '../course-builder/services/courseDraftService';
import { languageLabel, proficiencyLabel } from '../../shared/learning/languageProfiles';
import { useLocale } from '../../shared/i18n/locale';
import {
  addTeacherCohortMember,
  createTeacherCohort,
  listTeacherCohorts,
  removeTeacherCohortMember,
  updateTeacherCohortStatus,
  type CohortStatus,
  type LearningCohort,
} from './cohortService';

const STATUS_LABELS: Record<CohortStatus, string> = {
  PLANNED: 'Sắp khai giảng',
  ACTIVE: 'Đang học',
  COMPLETED: 'Đã kết thúc',
  ARCHIVED: 'Đã lưu trữ',
};

function dateInput(offsetDays: number) {
  const date = new Date();
  date.setDate(date.getDate() + offsetDays);
  return date.toISOString().slice(0, 10);
}

export function TeacherCohortsPage() {
  const { t, locale } = useLocale();
  const [cohorts, setCohorts] = useState<LearningCohort[]>([]);
  const [courses, setCourses] = useState<CourseDraftResponse[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [createOpen, setCreateOpen] = useState(false);
  const [memberEmail, setMemberEmail] = useState('');
  const [feedback, setFeedback] = useState<{ severity: 'success' | 'error'; message: string } | null>(null);
  const [form, setForm] = useState({
    courseId: '',
    name: '',
    code: '',
    organizationName: 'SOFL',
    branchName: '',
    startDate: dateInput(7),
    endDate: dateInput(97),
    maxStudents: 30,
  });

  const load = useCallback(async () => {
    try {
      setLoading(true);
      const [cohortItems, courseItems] = await Promise.all([
        listTeacherCohorts(),
        fetchCourseDrafts(),
      ]);
      setCohorts(cohortItems);
      setCourses(courseItems.filter((course) => course.status === 'PUBLISHED'));
      setSelectedId((current) => current && cohortItems.some((item) => item.id === current)
        ? current
        : cohortItems[0]?.id ?? null);
    } catch (error) {
      setFeedback({ severity: 'error', message: readError(error, 'Không thể tải danh sách lớp học.') });
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void load();
  }, [load]);

  const selected = useMemo(
    () => cohorts.find((cohort) => cohort.id === selectedId) ?? null,
    [cohorts, selectedId],
  );

  async function createCohort() {
    if (!form.courseId || !form.name.trim() || !form.startDate || !form.endDate) {
      setFeedback({ severity: 'error', message: 'Vui lòng chọn khóa học và nhập đủ thông tin lớp.' });
      return;
    }
    try {
      setSaving(true);
      const created = await createTeacherCohort({
        ...form,
        code: form.code.trim() || undefined,
        name: form.name.trim(),
        organizationName: form.organizationName.trim() || 'SOFL',
        branchName: form.branchName.trim() || undefined,
      });
      setCreateOpen(false);
      setFeedback({ severity: 'success', message: `Đã tạo lớp ${created.name} (${created.code}).` });
      await load();
      setSelectedId(created.id);
    } catch (error) {
      setFeedback({ severity: 'error', message: readError(error, 'Không thể tạo lớp học.') });
    } finally {
      setSaving(false);
    }
  }

  async function addMember() {
    if (!selected || !memberEmail.trim()) return;
    try {
      setSaving(true);
      const updated = await addTeacherCohortMember(selected.id, memberEmail.trim());
      replaceCohort(updated);
      setMemberEmail('');
      setFeedback({ severity: 'success', message: 'Đã thêm học viên và cấp quyền học theo thời hạn lớp.' });
    } catch (error) {
      setFeedback({ severity: 'error', message: readError(error, 'Không thể thêm học viên.') });
    } finally {
      setSaving(false);
    }
  }

  async function removeMember(memberId: string, fullName: string) {
    if (!selected || !window.confirm(`Gỡ ${fullName} khỏi lớp và thu hồi quyền học do lớp này cấp?`)) return;
    try {
      setSaving(true);
      replaceCohort(await removeTeacherCohortMember(selected.id, memberId));
      setFeedback({ severity: 'success', message: 'Đã gỡ học viên khỏi lớp.' });
    } catch (error) {
      setFeedback({ severity: 'error', message: readError(error, 'Không thể gỡ học viên.') });
    } finally {
      setSaving(false);
    }
  }

  async function changeStatus(status: CohortStatus) {
    if (!selected) return;
    try {
      setSaving(true);
      replaceCohort(await updateTeacherCohortStatus(selected.id, status));
    } catch (error) {
      setFeedback({ severity: 'error', message: readError(error, 'Không thể đổi trạng thái lớp.') });
    } finally {
      setSaving(false);
    }
  }

  function replaceCohort(updated: LearningCohort) {
    setCohorts((current) => current.map((item) => item.id === updated.id ? updated : item));
  }

  if (loading) return <LoadingState fullHeight message={t('Đang tải...')} />;

  return (
    <Box sx={{ p: { xs: 2, md: 3 } }}>
      <PageHeader
        title={t('Lớp học SOFL')}
        subtitle={t('Cấp quyền học theo đợt khai giảng, khóa học và thời hạn cụ thể')}
        breadcrumbs={[{ label: t('Giảng viên') }, { label: t('Lớp học') }]}
      />

      {feedback && <Alert severity={feedback.severity} onClose={() => setFeedback(null)} sx={{ mb: 2 }}>{t(feedback.message)}</Alert>}

      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ alignItems: { md: 'center' }, justifyContent: 'space-between', mb: 3 }}>
        <Typography color="text.secondary">
          {t('Một khóa học có thể dùng cho nhiều lớp. Mỗi học viên được cấp quyền đến ngày kết thúc lớp.')}
        </Typography>
        <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreateOpen(true)}>
          {t('Tạo lớp mới')}
        </Button>
      </Stack>

      {courses.length === 0 && (
        <Alert severity="info" sx={{ mb: 3 }}>
          {t('Cần xuất bản ít nhất một khóa học trước khi mở lớp.')}
        </Alert>
      )}

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, lg: 4 }}>
          <Stack spacing={1.5}>
            {cohorts.length === 0 ? (
              <Paper variant="outlined" sx={{ p: 4, textAlign: 'center' }}>
                <GroupsOutlinedIcon color="disabled" sx={{ fontSize: 48 }} />
                <Typography sx={{ fontWeight: 700, mt: 1 }}>{t('Chưa có lớp học')}</Typography>
                <Typography variant="body2" color="text.secondary">{t('Tạo lớp đầu tiên từ một khóa đã xuất bản.')}</Typography>
              </Paper>
            ) : cohorts.map((cohort) => (
              <Card
                key={cohort.id}
                variant="outlined"
                onClick={() => setSelectedId(cohort.id)}
                sx={{ cursor: 'pointer', borderColor: selectedId === cohort.id ? 'primary.main' : 'divider' }}
              >
                <CardContent>
                  <Stack direction="row" sx={{ justifyContent: 'space-between', gap: 1 }}>
                    <Box>
                      <Typography sx={{ fontWeight: 800 }}>{cohort.name}</Typography>
                      <Typography variant="body2" color="text.secondary">{cohort.code} • {cohort.branchName || cohort.organizationName}</Typography>
                    </Box>
                    <Chip size="small" label={t(STATUS_LABELS[cohort.status])} color={cohort.status === 'ACTIVE' ? 'success' : 'default'} />
                  </Stack>
                  <Typography variant="body2" sx={{ mt: 1.5 }}>{cohort.courseTitle}</Typography>
                  <Typography variant="caption" color="text.secondary">
                    {cohort.activeStudentCount}/{cohort.maxStudents} {t('Học viên')} • {cohort.startDate} → {cohort.endDate}
                  </Typography>
                </CardContent>
              </Card>
            ))}
          </Stack>
        </Grid>

        <Grid size={{ xs: 12, lg: 8 }}>
          {selected ? (
            <Paper variant="outlined" sx={{ p: { xs: 2, md: 3 } }}>
              <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} sx={{ justifyContent: 'space-between', mb: 3 }}>
                <Box>
                  <Typography variant="h6" sx={{ fontWeight: 800 }}>{selected.name}</Typography>
                  <Typography color="text.secondary">
                    {t(languageLabel(selected.learningLanguage))} • {proficiencyLabel(selected.proficiencyFramework, selected.proficiencyLevel)} • {selected.courseTitle}
                  </Typography>
                </Box>
                <TextField
                  select
                  size="small"
                  label={t('Trạng thái lớp')}
                  value={selected.status}
                  disabled={saving}
                  onChange={(event) => { void changeStatus(event.target.value as CohortStatus); }}
                  sx={{ minWidth: 170 }}
                >
                  {(Object.keys(STATUS_LABELS) as CohortStatus[]).map((status) => (
                    <MenuItem key={status} value={status}>{t(STATUS_LABELS[status])}</MenuItem>
                  ))}
                </TextField>
              </Stack>

              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ mb: 2 }}>
                <TextField
                  fullWidth
                  size="small"
                  type="email"
                  label={t('Email tài khoản học viên')}
                  value={memberEmail}
                  disabled={saving || selected.status === 'ARCHIVED' || selected.status === 'COMPLETED'}
                  onChange={(event) => setMemberEmail(event.target.value)}
                  onKeyDown={(event) => { if (event.key === 'Enter') void addMember(); }}
                />
                <Button
                  variant="contained"
                  startIcon={<PersonAddAltOutlinedIcon />}
                  disabled={saving || !memberEmail.trim() || selected.activeStudentCount >= selected.maxStudents}
                  onClick={() => { void addMember(); }}
                  sx={{ whiteSpace: 'nowrap' }}
                >
                  {t('Thêm học viên')}
                </Button>
              </Stack>

              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>{t('Học viên')}</TableCell>
                      <TableCell>{t('Quyền học')}</TableCell>
                      <TableCell>{t('Hạn truy cập')}</TableCell>
                      <TableCell align="right">{t('Thao tác')}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {selected.members.filter((member) => member.status === 'ACTIVE').map((member) => (
                      <TableRow key={member.id}>
                        <TableCell>
                          <Typography variant="body2" sx={{ fontWeight: 700 }}>{member.fullName}</Typography>
                          <Typography variant="caption" color="text.secondary">{member.email}</Typography>
                        </TableCell>
                        <TableCell><Chip size="small" label={t(member.enrollmentStatus ?? 'Chưa cấp')} /></TableCell>
                        <TableCell>{member.accessExpiresAt ? new Date(member.accessExpiresAt).toLocaleDateString(locale === 'vi' ? 'vi-VN' : locale === 'ko' ? 'ko-KR' : 'zh-CN') : t('Không giới hạn')}</TableCell>
                        <TableCell align="right">
                          <Tooltip title={t('Gỡ khỏi lớp')}>
                            <IconButton color="error" size="small" onClick={() => { void removeMember(member.id, member.fullName); }}>
                              <DeleteOutlineIcon fontSize="small" />
                            </IconButton>
                          </Tooltip>
                        </TableCell>
                      </TableRow>
                    ))}
                    {selected.members.every((member) => member.status !== 'ACTIVE') && (
                      <TableRow><TableCell colSpan={4} align="center">{t('Chưa có học viên trong lớp.')}</TableCell></TableRow>
                    )}
                  </TableBody>
                </Table>
              </TableContainer>
              <Button component={Link} to={`/teacher/cohorts/${selected.id}/workspace`} variant="outlined" sx={{ mt: 2 }}>
                {t('Giao bài, chữa bài và theo dõi học viên')}
              </Button>
            </Paper>
          ) : (
            <Paper variant="outlined" sx={{ p: 5, textAlign: 'center' }}>
              <Typography color="text.secondary">{t('Chọn một lớp để quản lý học viên.')}</Typography>
            </Paper>
          )}
        </Grid>
      </Grid>

      <Dialog open={createOpen} onClose={() => !saving && setCreateOpen(false)} maxWidth="md" fullWidth>
        <DialogTitle>{t('Tạo lớp học theo đợt khai giảng')}</DialogTitle>
        <DialogContent>
          <Grid container spacing={2} sx={{ mt: 0.5 }}>
            <Grid size={{ xs: 12 }}>
              <TextField select fullWidth required label={t('Khóa học')} value={form.courseId} onChange={(event) => setForm({ ...form, courseId: event.target.value })}>
                {courses.map((course) => <MenuItem key={course.id} value={course.id}>{course.title} • {proficiencyLabel(course.proficiencyFramework, course.proficiencyLevel)}</MenuItem>)}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, md: 8 }}><TextField fullWidth required label={t('Tên lớp')} value={form.name} onChange={(event) => setForm({ ...form, name: event.target.value })} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth label={t('Mã lớp (tự tạo nếu để trống)')} value={form.code} onChange={(event) => setForm({ ...form, code: event.target.value.toUpperCase() })} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label={t('Đơn vị')} value={form.organizationName} onChange={(event) => setForm({ ...form, organizationName: event.target.value })} /></Grid>
            <Grid size={{ xs: 12, md: 6 }}><TextField fullWidth label={t('Cơ sở/chi nhánh')} value={form.branchName} onChange={(event) => setForm({ ...form, branchName: event.target.value })} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth type="date" label={t('Ngày bắt đầu')} slotProps={{ inputLabel: { shrink: true } }} value={form.startDate} onChange={(event) => setForm({ ...form, startDate: event.target.value })} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth type="date" label={t('Ngày kết thúc')} slotProps={{ inputLabel: { shrink: true } }} value={form.endDate} onChange={(event) => setForm({ ...form, endDate: event.target.value })} /></Grid>
            <Grid size={{ xs: 12, md: 4 }}><TextField fullWidth type="number" label={t('Sĩ số tối đa')} value={form.maxStudents} onChange={(event) => setForm({ ...form, maxStudents: Number(event.target.value) })} /></Grid>
          </Grid>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCreateOpen(false)} disabled={saving}>{t('Hủy')}</Button>
          <Button variant="contained" onClick={() => { void createCohort(); }} disabled={saving || courses.length === 0}>{t(saving ? 'Đang tạo...' : 'Tạo lớp')}</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

function readError(error: unknown, fallback: string) {
  return isAxiosError(error) ? error.response?.data?.message ?? fallback : fallback;
}
