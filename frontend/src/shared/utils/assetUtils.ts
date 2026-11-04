export function resolvePublicAssetUrl(url?: string | null): string | undefined {
  if (!url) return undefined;

  if (
    url.startsWith('http://') ||
    url.startsWith('https://') ||
    url.startsWith('blob:') ||
    url.startsWith('data:')
  ) {
    return url;
  }

  let baseUrl = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8081/api';
  if (baseUrl.endsWith('/api')) {
    baseUrl = baseUrl.substring(0, baseUrl.length - 4);
  } else if (baseUrl.endsWith('/')) {
    baseUrl = baseUrl.substring(0, baseUrl.length - 1);
  }

  if (!url.startsWith('/')) {
    url = '/' + url;
  }

  return baseUrl + url;
}
