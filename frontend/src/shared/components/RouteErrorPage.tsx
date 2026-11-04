import { useRouteError } from 'react-router-dom';

export function RouteErrorPage() {
  const error = useRouteError();
  const message = error instanceof Error ? error.message : String(error ?? '');
  const isChunkError = /(Failed to fetch dynamically imported module|Importing a module script failed|Loading chunk .* failed|ChunkLoadError)/i
    .test(message);

  return (
    <main style={{ minHeight: '100vh', display: 'grid', placeItems: 'center', padding: 24, background: '#f8fafc' }}>
      <section style={{ width: 'min(520px, 100%)', padding: 28, borderRadius: 16, background: '#fff', boxShadow: '0 16px 40px rgba(15, 23, 42, 0.12)', textAlign: 'center' }}>
        <div style={{ fontSize: 36 }} aria-hidden="true">{isChunkError ? '↻' : '!'}</div>
        <h1 style={{ margin: '12px 0 8px', fontSize: 24, color: '#101828' }}>
          {isChunkError ? 'ManabiHub vừa được cập nhật' : 'Trang chưa thể hiển thị'}
        </h1>
        <p style={{ margin: '0 0 22px', lineHeight: 1.6, color: '#667085' }}>
          {isChunkError
            ? 'Hãy tải lại để dùng phiên bản mới nhất. Tiến độ học đã lưu của bạn không bị mất.'
            : 'Đã có lỗi tạm thời. Bạn có thể tải lại trang hoặc quay về trang chủ.'}
        </p>
        <div style={{ display: 'flex', justifyContent: 'center', gap: 12, flexWrap: 'wrap' }}>
          <button type="button" onClick={() => window.location.reload()} style={{ border: 0, borderRadius: 10, padding: '11px 18px', background: '#c41e3a', color: '#fff', fontWeight: 700, cursor: 'pointer' }}>
            Tải lại trang
          </button>
          <button type="button" onClick={() => window.location.assign('/')} style={{ border: '1px solid #d0d5dd', borderRadius: 10, padding: '11px 18px', background: '#fff', color: '#344054', fontWeight: 700, cursor: 'pointer' }}>
            Về trang chủ
          </button>
        </div>
      </section>
    </main>
  );
}
