import { useEffect, useRef, useState } from 'react';
import { Alert, Box, Button, Stack, Typography } from '@mui/material';
import MicIcon from '@mui/icons-material/Mic';
import StopIcon from '@mui/icons-material/Stop';
import { useLocale } from '../../shared/i18n/locale';

export interface Recording { file: File; seconds: number }
export function AudioRecorder({ maxSeconds, disabled, value, onChange, onBusyChange }: {
  maxSeconds: number; disabled?: boolean; value?: Recording; onChange: (recording?: Recording) => void; onBusyChange?: (busy: boolean) => void;
}) {
  const { t } = useLocale();
  const recorder = useRef<MediaRecorder | null>(null);
  const stream = useRef<MediaStream | null>(null);
  const timer = useRef<ReturnType<typeof setInterval> | null>(null);
  const alive = useRef(true);
  const started = useRef(0);
  const [recording, setRecording] = useState(false);
  const [opening, setOpening] = useState(false);
  const [seconds, setSeconds] = useState(0);
  const [file, setFile] = useState<File>();
  const [url, setUrl] = useState('');
  const [error, setError] = useState('');
  const supported = typeof MediaRecorder !== 'undefined' && Boolean(navigator.mediaDevices?.getUserMedia);
  useEffect(() => { onBusyChange?.(recording || opening); }, [recording, opening, onBusyChange]);
  useEffect(() => {
    if (!recording && !opening) { setFile(value?.file); setSeconds(value?.seconds ?? 0); }
  }, [value?.file, value?.seconds, recording, opening]);

  useEffect(() => {
    alive.current = true;
    return () => {
      alive.current = false;
      if (recorder.current && recorder.current.state !== 'inactive') recorder.current.stop();
      stream.current?.getTracks().forEach(track => track.stop());
      if (timer.current) clearInterval(timer.current);
    };
  }, []);
  useEffect(() => {
    if (!file) { setUrl(''); return; }
    const next = URL.createObjectURL(file); setUrl(next);
    return () => URL.revokeObjectURL(next);
  }, [file]);

  function accept(next: File, duration: number) {
    if (next.size === 0 || next.size > 8 * 1024 * 1024 || !Number.isFinite(duration) || duration < 1 || duration > maxSeconds) {
      setError(t('Bản ghi tối đa 8 MB và không được vượt thời lượng bài tập.')); return;
    }
    setFile(next); setSeconds(duration); onChange({ file: next, seconds: duration });
  }
  async function start() {
    setError(''); setOpening(true);
    try {
      const source = await navigator.mediaDevices.getUserMedia({ audio: true });
      if (!alive.current) { source.getTracks().forEach(track => track.stop()); return; }
      stream.current = source;
      const mime = ['audio/webm;codecs=opus', 'audio/ogg;codecs=opus', 'audio/mp4'].find(value => MediaRecorder.isTypeSupported(value));
      if (!mime) { source.getTracks().forEach(track => track.stop()); throw new Error('unsupported'); }
      const next = new MediaRecorder(source, { mimeType: mime }); recorder.current = next;
      const chunks: Blob[] = []; let bytes = 0; let failed = false;
      next.ondataavailable = event => {
        if (event.data.size) { chunks.push(event.data); bytes += event.data.size; }
        if (bytes > 8 * 1024 * 1024 && next.state !== 'inactive') next.stop();
      };
      next.onerror = () => {
        failed = true;
        source.getTracks().forEach(track => track.stop());
        if (timer.current) clearInterval(timer.current);
        if (alive.current) { setRecording(false); setError(t('Không mở được micro. Kiểm tra quyền truy cập và dùng HTTPS.')); }
      };
      next.onstop = () => {
        source.getTracks().forEach(track => track.stop());
        if (timer.current) clearInterval(timer.current);
        if (!alive.current) return;
        setRecording(false);
        if (failed) return;
        const duration = Math.max(1, Math.round((performance.now() - started.current) / 1000));
        const extension = mime.startsWith('audio/mp4') ? 'm4a' : mime.startsWith('audio/ogg') ? 'ogg' : 'webm';
        accept(new File(chunks, `speaking.${extension}`, { type: mime }), duration);
      };
      started.current = performance.now(); setSeconds(0); setRecording(true);
      next.start(500);
      timer.current = setInterval(() => {
        const elapsed = Math.floor((performance.now() - started.current) / 1000); setSeconds(Math.min(elapsed, maxSeconds));
        if (elapsed >= maxSeconds && next.state !== 'inactive') next.stop();
      }, 250);
    } catch {
      stream.current?.getTracks().forEach(track => track.stop());
      if (timer.current) clearInterval(timer.current);
      if (alive.current) { setRecording(false); setError(t('Không mở được micro. Kiểm tra quyền truy cập và dùng HTTPS.')); }
    } finally { if (alive.current) setOpening(false); }
  }
  async function pick(next?: File) {
    if (!next) return;
    setError('');
    if (next.size > 8 * 1024 * 1024) { setError(t('Bản ghi tối đa 8 MB và không được vượt thời lượng bài tập.')); return; }
    setOpening(true);
    let context: AudioContext | undefined;
    try {
      context = new AudioContext();
      const decoded = await context.decodeAudioData(await next.arrayBuffer());
      const knownMime: Record<string, string> = { m4a: 'audio/mp4', mp3: 'audio/mpeg', wav: 'audio/wav', ogg: 'audio/ogg', webm: 'audio/webm' };
      const extension = next.name.split('.').pop()?.toLowerCase() ?? '';
      const typed = next.type ? next : new File([next], next.name, { type: knownMime[extension] ?? '', lastModified: next.lastModified });
      if (alive.current) accept(typed, Math.max(1, Math.round(decoded.duration)));
    } catch { if (alive.current) setError(t('Không đọc được thời lượng. Hãy ghi âm trực tiếp hoặc chọn tệp khác.')); }
    finally { await context?.close().catch(() => undefined); if (alive.current) setOpening(false); }
  }
  return <Stack spacing={1.5}>
    {!supported && <Alert severity="info">{t('Trình duyệt chưa hỗ trợ ghi âm; bạn có thể chọn tệp âm thanh.')}</Alert>}
    <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }} useFlexGap>
      <Button variant="outlined" startIcon={recording ? <StopIcon /> : <MicIcon />} disabled={disabled || opening || !supported}
        onClick={() => recording ? recorder.current?.stop() : void start()}>{t(recording ? 'Dừng ghi âm' : 'Ghi âm')}</Button>
      <Button component="label" disabled={disabled || opening || recording}>{t('Chọn tệp âm thanh')}
        <input hidden type="file" accept="audio/webm,audio/ogg,audio/mp4,audio/x-m4a,audio/wav,audio/x-wav,audio/mpeg,.m4a" onChange={event => { void pick(event.target.files?.[0]); event.target.value = ''; }} />
      </Button>
      {file && <Button disabled={disabled || recording || opening} onClick={() => { setFile(undefined); setSeconds(0); onChange(undefined); }}>{t('Xóa bản ghi')}</Button>}
    </Stack>
    <Typography variant="body2">{seconds}s / {maxSeconds}s · 8 MB</Typography>
    {url && <Box component="audio" controls src={url} sx={{ width: '100%' }} />}
    {error && <Alert severity="error">{error}</Alert>}
  </Stack>;
}
