import { useSyncExternalStore } from 'react';
import { translations } from './translations';

export type UiLocale = 'vi' | 'ko' | 'zh-CN';
const KEY = 'manabihub.uiLocale';
function readLocale(): UiLocale {
  try { const value = localStorage.getItem(KEY); return value === 'ko' || value === 'zh-CN' ? value : 'vi'; }
  catch { return 'vi'; }
}
let current = readLocale();
const listeners = new Set<() => void>();
const subscribe = (listener: () => void) => { listeners.add(listener); return () => { listeners.delete(listener); }; };
export function setUiLocale(locale: UiLocale) {
  current = locale;
  try { localStorage.setItem(KEY, locale); } catch { /* Locale still applies for this session. */ }
  document.documentElement.lang = locale;
  listeners.forEach(listener => listener());
}
document.documentElement.lang = current;
export function useLocale() {
  const locale = useSyncExternalStore(subscribe, () => current);
  const t = (vietnamese: string) => locale === 'vi' ? vietnamese : translations[vietnamese]?.[locale] ?? vietnamese;
  const date = (value: string) => new Date(value).toLocaleString(locale === 'vi' ? 'vi-VN' : locale === 'ko' ? 'ko-KR' : 'zh-CN');
  return { locale, t, date, setLocale: setUiLocale };
}
