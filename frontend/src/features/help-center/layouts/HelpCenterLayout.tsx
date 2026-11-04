import type { FormEvent } from 'react';
import { Search } from 'lucide-react';
import { Link, Outlet, useLocation, useNavigate } from 'react-router-dom';

export const HelpCenterLayout = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const currentQuery = new URLSearchParams(location.search).get('q') ?? '';

  const handleSearch = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const formData = new FormData(event.currentTarget);
    const query = String(formData.get('q') ?? '').trim();
    navigate(query ? `/help?q=${encodeURIComponent(query)}` : '/help');
  };

  return (
    <div className="flex min-h-screen flex-col bg-white text-gray-950">
      <header className="sticky top-0 z-10 border-b border-gray-200 bg-white">
        <div className="mx-auto flex w-full max-w-6xl flex-col gap-4 px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <div className="flex min-w-0 items-center gap-3">
            <Link
              to="/"
              aria-label="Về trang chủ ManabiHub"
              className="flex shrink-0 items-center rounded-md focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-red-600 focus-visible:ring-offset-2"
            >
              <img
                src="/manabihub-header-logo.png"
                alt="ManabiHub"
                className="h-12 w-auto transition-transform duration-200 hover:scale-105"
              />
            </Link>
            <span aria-hidden="true" className="text-gray-300">/</span>
            <Link
              to="/help"
              className="truncate text-sm font-semibold text-gray-700 hover:text-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 sm:text-base"
            >
              Trung tâm trợ giúp
            </Link>
          </div>

          <form className="relative w-full sm:max-w-md" role="search" onSubmit={handleSearch}>
            <label className="sr-only" htmlFor="help-search">Tìm kiếm bài viết trợ giúp</label>
            <Search
              aria-hidden="true"
              className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-500"
            />
            <input
              key={currentQuery}
              id="help-search"
              name="q"
              type="search"
              defaultValue={currentQuery}
              placeholder="Tìm kiếm theo chủ đề..."
              className="h-11 w-full border border-gray-300 bg-gray-50 pl-10 pr-12 text-sm outline-none transition focus:border-blue-600 focus:bg-white focus:ring-2 focus:ring-blue-100"
            />
            <button
              type="submit"
              aria-label="Tìm kiếm"
              title="Tìm kiếm"
              className="absolute right-1 top-1 flex h-9 w-9 items-center justify-center text-gray-600 transition hover:bg-gray-200 hover:text-blue-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600"
            >
              <Search aria-hidden="true" className="h-4 w-4" />
            </button>
          </form>
        </div>
      </header>

      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8 sm:px-6 sm:py-12">
        <Outlet />
      </main>

      <footer className="border-t border-gray-200 bg-gray-50">
        <div className="mx-auto flex w-full max-w-6xl flex-col gap-2 px-4 py-6 text-sm text-gray-600 sm:flex-row sm:items-center sm:justify-between sm:px-6">
          <span>© {new Date().getFullYear()} ManabiHub</span>
          <Link className="font-medium hover:text-blue-700" to="/">
            Về trang chủ
          </Link>
        </div>
      </footer>
    </div>
  );
};
