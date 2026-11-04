import { useEffect, useRef, useState } from 'react';
import { Alert, Box, Button } from '@mui/material';
import { fetchAudio } from './workspaceApi';
import { useLocale } from '../../shared/i18n/locale';

export function PrivateAudioPlayer({ submissionId, teacher }: { submissionId: string; teacher: boolean }) {
  const { t } = useLocale();
  const [url, setUrl] = useState(''); const [busy, setBusy] = useState(false); const [error, setError] = useState(false);
  const active = useRef(true); const objectUrl = useRef('');
  const generation = useRef(0);
  useEffect(() => {
    active.current = true; generation.current++; setUrl(''); setError(false); setBusy(false);
    return () => { active.current = false; if (objectUrl.current) URL.revokeObjectURL(objectUrl.current); objectUrl.current = ''; };
  }, [submissionId, teacher]);
  async function load() {
    const requestGeneration = generation.current;
    setBusy(true); setError(false);
    try {
      const blob = await fetchAudio(submissionId, teacher);
      if (!active.current || requestGeneration !== generation.current) return;
      objectUrl.current = URL.createObjectURL(blob); setUrl(objectUrl.current);
    } catch { if (active.current && requestGeneration === generation.current) setError(true); }
    finally { if (active.current && requestGeneration === generation.current) setBusy(false); }
  }
  return <Box>{url ? <Box component="audio" controls src={url} sx={{ width: '100%' }} />
    : <Button disabled={busy} onClick={() => void load()}>{t(busy ? 'Đang tải...' : 'Tải để nghe')}</Button>}
    {error && <Alert severity="error">{t('Không thể nghe bản ghi.')}</Alert>}
  </Box>;
}
