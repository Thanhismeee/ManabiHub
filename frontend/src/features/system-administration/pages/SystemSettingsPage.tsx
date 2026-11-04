import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Divider,
  FormControlLabel,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import SaveOutlinedIcon from '@mui/icons-material/SaveOutlined';
import SettingsApplicationsOutlinedIcon from '@mui/icons-material/SettingsApplicationsOutlined';
import { systemAdministrationService } from '../services/systemAdministrationService';
import type { SystemSetting } from '../types/systemAdministrationTypes';

const LABELS: Record<string, string> = {
  COMMISSION_RATE: 'Tỷ lệ hoa hồng nền tảng (0–1)',
  COURSE_PRICE_FLOOR: 'Giá khóa học tối thiểu (VND)',
  AI_SUPPORT_PRICE_FLOOR: 'Giá tối thiểu để dùng AI (VND)',
  REFUND_WINDOW_DAYS: 'Thời hạn hoàn tiền (ngày)',
  REFUND_PROGRESS_LIMIT_PERCENT: 'Ngưỡng tiến độ hoàn tiền — không vượt quá (%)',
  ESCROW_HOLDING_DAYS: 'Thời gian giữ escrow (ngày)',
  PAYOUT_THRESHOLD: 'Số dư tối thiểu để rút (VND)',
  AI_ENABLED: 'Bật toàn bộ tính năng AI',
  AI_WRITING_ENABLED: 'Bật AI hỗ trợ bài viết',
  AI_CHATBOT_ENABLED: 'Bật AI chatbot',
  ADMIN_LOCKOUT_MAX_ATTEMPTS: 'Số lần đăng nhập sai tối đa',
  ADMIN_LOCKOUT_DURATION_MINUTES: 'Thời gian khóa admin (phút)',
  COURSE_MIN_LEARNING_GOALS: 'Số mục tiêu học tập tối thiểu',
  COURSE_MAX_LEARNING_GOAL_LENGTH: 'Độ dài tối đa mỗi mục tiêu',
};

const GROUPS = [
  {
    title: 'Giá và vận hành tài chính',
    keys: [
      'COMMISSION_RATE',
      'COURSE_PRICE_FLOOR',
      'PAYOUT_THRESHOLD',
      'REFUND_WINDOW_DAYS',
      'REFUND_PROGRESS_LIMIT_PERCENT',
      'ESCROW_HOLDING_DAYS',
    ],
  },
  {
    title: 'AI',
    keys: [
      'AI_ENABLED',
      'AI_WRITING_ENABLED',
      'AI_CHATBOT_ENABLED',
      'AI_SUPPORT_PRICE_FLOOR',
    ],
  },
  {
    title: 'Kiểm tra nội dung và bảo mật',
    keys: [
      'COURSE_MIN_LEARNING_GOALS',
      'COURSE_MAX_LEARNING_GOAL_LENGTH',
      'ADMIN_LOCKOUT_MAX_ATTEMPTS',
      'ADMIN_LOCKOUT_DURATION_MINUTES',
    ],
  },
];

export function SystemSettingsPage() {
  const [settings, setSettings] = useState<SystemSetting[]>([]);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [savingKey, setSavingKey] = useState<string | null>(null);
  const [confirmKey, setConfirmKey] = useState<string | null>(null);
  const [reason, setReason] = useState('');
  const [success, setSuccess] = useState<string | null>(null);

  const loadSettings = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await systemAdministrationService.listSettings();
      setSettings(data);
      setDrafts(Object.fromEntries(data.map((setting) => [setting.key, setting.value])));
    } catch {
      setError('Không thể tải cấu hình hệ thống. Vui lòng thử lại.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadSettings();
  }, [loadSettings]);

  const settingsByKey = useMemo(
    () => new Map(settings.map((setting) => [setting.key, setting])),
    [settings],
  );

  const saveSetting = async () => {
    if (!confirmKey || !reason.trim()) {
      return;
    }
    setSavingKey(confirmKey);
    setError(null);
    setSuccess(null);
    try {
      const updated = await systemAdministrationService.updateSetting(confirmKey, {
        value: drafts[confirmKey] ?? '',
        reason: reason.trim(),
      });
      setSettings((current) =>
        current.map((setting) => setting.key === updated.key ? updated : setting),
      );
      setDrafts((current) => ({ ...current, [updated.key]: updated.value }));
      setSuccess(`Đã cập nhật ${LABELS[updated.key] ?? updated.key}.`);
      setConfirmKey(null);
      setReason('');
    } catch {
      setError('Giá trị không hợp lệ hoặc cấu hình đã thay đổi. Hãy kiểm tra và thử lại.');
    } finally {
      setSavingKey(null);
    }
  };

  if (loading) {
    return (
      <Box sx={{ minHeight: 320, display: 'grid', placeItems: 'center' }}>
        <CircularProgress aria-label="Đang tải cấu hình" />
      </Box>
    );
  }

  return (
    <Stack spacing={3}>
      <Box>
        <Stack direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
          <SettingsApplicationsOutlinedIcon color="primary" />
          <Typography variant="h4" sx={{ fontWeight: 800 }}>Cấu hình hệ thống</Typography>
        </Stack>
        <Typography color="text.secondary" sx={{ mt: 1 }}>
          Giá trị được lưu tập trung và mọi thay đổi đều có nhật ký trước/sau.
        </Typography>
      </Box>

      <Alert severity="info">
        Thay đổi chỉ áp dụng cho quyết định mới; không tính lại giao dịch, enrollment hay
        kết quả đã phát sinh.
      </Alert>
      {error && (
        <Alert
          severity="error"
          action={<Button color="inherit" onClick={loadSettings}>Thử lại</Button>}
        >
          {error}
        </Alert>
      )}
      {success && <Alert severity="success" onClose={() => setSuccess(null)}>{success}</Alert>}

      {settings.length === 0 && (
        <Alert severity="warning">Chưa có cấu hình được hỗ trợ trong phiên bản này.</Alert>
      )}

      {GROUPS.map((group) => {
        const groupSettings = group.keys
          .map((key) => settingsByKey.get(key))
          .filter((setting): setting is SystemSetting => Boolean(setting));
        if (groupSettings.length === 0) return null;

        return (
          <Card key={group.title} variant="outlined">
            <CardContent>
              <Typography variant="h6" sx={{ fontWeight: 750 }}>{group.title}</Typography>
              <Divider sx={{ my: 2 }} />
              <Stack spacing={2.5}>
                {groupSettings.map((setting) => {
                  const draft = drafts[setting.key] ?? '';
                  const changed = draft !== setting.value;
                  return (
                    <Stack
                      key={setting.key}
                      direction={{ xs: 'column', md: 'row' }}
                      spacing={2}
                      sx={{ alignItems: { md: 'center' } }}
                    >
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Stack
                          direction="row"
                          spacing={1}
                          sx={{ alignItems: 'center', flexWrap: 'wrap' }}
                        >
                          <Typography sx={{ fontWeight: 700 }}>
                            {LABELS[setting.key] ?? setting.key}
                          </Typography>
                          <Chip label={setting.key} size="small" variant="outlined" />
                        </Stack>
                        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                          {setting.description}
                        </Typography>
                      </Box>

                      {setting.valueType === 'BOOLEAN' ? (
                        <FormControlLabel
                          sx={{ minWidth: 180 }}
                          control={(
                            <Switch
                              checked={draft === 'true'}
                              disabled={!setting.editable}
                              onChange={(_, checked) =>
                                setDrafts((current) => ({
                                  ...current,
                                  [setting.key]: String(checked),
                                }))}
                              slotProps={{
                                input: {
                                  'aria-label': LABELS[setting.key] ?? setting.key,
                                },
                              }}
                            />
                          )}
                          label={draft === 'true' ? 'Đang bật' : 'Đang tắt'}
                        />
                      ) : (
                        <TextField
                          label={LABELS[setting.key] ?? setting.key}
                          type={setting.valueType === 'NUMBER' ? 'number' : 'text'}
                          value={draft}
                          disabled={!setting.editable}
                          onChange={(event) =>
                            setDrafts((current) => ({
                              ...current,
                              [setting.key]: event.target.value,
                            }))}
                          slotProps={{ htmlInput: { step: 'any' } }}
                          sx={{ width: { xs: '100%', md: 250 } }}
                        />
                      )}

                      <Button
                        variant="contained"
                        startIcon={<SaveOutlinedIcon />}
                        disabled={!setting.editable || !changed || savingKey !== null}
                        onClick={() => {
                          setConfirmKey(setting.key);
                          setReason('');
                        }}
                      >
                        Lưu
                      </Button>
                    </Stack>
                  );
                })}
              </Stack>
            </CardContent>
          </Card>
        );
      })}

      <Dialog
        open={Boolean(confirmKey)}
        onClose={() => savingKey === null && setConfirmKey(null)}
        fullWidth
        maxWidth="sm"
      >
        <DialogTitle>Xác nhận thay đổi cấu hình</DialogTitle>
        <DialogContent>
          <Typography color="text.secondary" sx={{ mb: 2 }}>
            {confirmKey ? LABELS[confirmKey] ?? confirmKey : ''}
          </Typography>
          <TextField
            autoFocus
            fullWidth
            label="Lý do thay đổi"
            multiline
            minRows={3}
            value={reason}
            onChange={(event) => setReason(event.target.value)}
            slotProps={{ htmlInput: { maxLength: 500 } }}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmKey(null)} disabled={savingKey !== null}>Hủy</Button>
          <Button
            variant="contained"
            onClick={saveSetting}
            disabled={!reason.trim() || savingKey !== null}
          >
            {savingKey ? 'Đang lưu…' : 'Xác nhận'}
          </Button>
        </DialogActions>
      </Dialog>
    </Stack>
  );
}
