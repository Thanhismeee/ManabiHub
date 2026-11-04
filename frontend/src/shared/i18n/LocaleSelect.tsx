import { MenuItem, TextField } from '@mui/material';
import { useLocale, type UiLocale } from './locale';

export function LocaleSelect() {
  const { locale, setLocale, t } = useLocale();
  return <TextField select size="small" value={locale} label={t('Ngôn ngữ giao diện')}
    onChange={event => setLocale(event.target.value as UiLocale)} sx={{ minWidth: 115 }}>
    <MenuItem value="vi">Tiếng Việt</MenuItem><MenuItem value="ko">한국어</MenuItem><MenuItem value="zh-CN">简体中文</MenuItem>
  </TextField>;
}
