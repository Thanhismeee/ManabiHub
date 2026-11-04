import { ArrowRight, SearchX } from 'lucide-react';
import { Helmet } from 'react-helmet-async';
import { Link, useSearchParams } from 'react-router-dom';
import {
  filterHelpArticles,
  getHelpCategory,
  HELP_CATEGORIES,
} from '../content/articleRegistry';
import type { HelpCategory } from '../types';

export const HelpCenterIndexPage = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const query = searchParams.get('q') ?? '';
  const categoryParam = searchParams.get('category');
  const category = HELP_CATEGORIES.some((item) => item.id === categoryParam)
    ? categoryParam as HelpCategory
    : undefined;
  const matchingArticles = filterHelpArticles(query, category);
  const visibleCategories = category
    ? HELP_CATEGORIES.filter((item) => item.id === category)
    : HELP_CATEGORIES;

  const selectCategory = (nextCategory?: HelpCategory) => {
    const next = new URLSearchParams(searchParams);
    if (nextCategory) {
      next.set('category', nextCategory);
    } else {
      next.delete('category');
    }
    setSearchParams(next);
  };

  return (
    <>
      <Helmet title="Trung tâm trợ giúp | ManabiHub">
        <meta
          content="Hướng dẫn dành cho học viên và giảng viên về xác thực, doanh thu, hoàn tiền, an toàn và dữ liệu trên ManabiHub."
          name="description"
        />
      </Helmet>

      <section className="border-b border-gray-200 pb-10">
        <p className="text-sm font-semibold uppercase text-blue-700">ManabiHub Support</p>
        <h1 className="mt-3 max-w-3xl text-3xl font-bold text-gray-950 sm:text-4xl">
          Thông tin rõ ràng cho từng bước học và giảng dạy
        </h1>
        <p className="mt-4 max-w-2xl text-base leading-7 text-gray-600">
          Tìm hiểu quy trình xác thực, doanh thu, hoàn tiền, an toàn và cách dữ liệu
          được sử dụng trên nền tảng.
        </p>
      </section>

      <nav aria-label="Lọc bài viết theo chủ đề" className="flex flex-wrap gap-2 border-b border-gray-200 py-5">
        <button
          type="button"
          aria-pressed={!category}
          className={`px-3 py-2 text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 ${
            !category ? 'bg-gray-950 text-white' : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
          }`}
          onClick={() => selectCategory()}
        >
          Tất cả
        </button>
        {HELP_CATEGORIES.map((item) => (
          <button
            key={item.id}
            type="button"
            aria-pressed={category === item.id}
            className={`px-3 py-2 text-sm font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2 ${
              category === item.id
                ? 'bg-gray-950 text-white'
                : 'bg-gray-100 text-gray-700 hover:bg-gray-200'
            }`}
            onClick={() => selectCategory(item.id)}
          >
            {item.label}
          </button>
        ))}
      </nav>

      {query && (
        <p className="mt-6 text-sm text-gray-600" aria-live="polite">
          {matchingArticles.length} kết quả cho <strong>“{query}”</strong>
        </p>
      )}

      {matchingArticles.length === 0 ? (
        <section className="flex min-h-64 flex-col items-center justify-center border-b border-gray-200 py-12 text-center">
          <SearchX aria-hidden="true" className="h-9 w-9 text-gray-400" />
          <h2 className="mt-4 text-lg font-bold text-gray-950">Không tìm thấy bài viết phù hợp</h2>
          <p className="mt-2 max-w-md text-sm leading-6 text-gray-600">
            Thử một từ khóa ngắn hơn hoặc bỏ bộ lọc chủ đề.
          </p>
          <button
            type="button"
            className="mt-5 text-sm font-semibold text-blue-700 hover:text-blue-900 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2"
            onClick={() => setSearchParams({})}
          >
            Xóa bộ lọc
          </button>
        </section>
      ) : (
        <div className="divide-y divide-gray-200">
          {visibleCategories.map((categoryDefinition) => {
            const categoryArticles = matchingArticles.filter(
              (article) => article.category === categoryDefinition.id,
            );
            if (categoryArticles.length === 0) {
              return null;
            }

            const definition = getHelpCategory(categoryDefinition.id);
            return (
              <section
                key={definition.id}
                className="grid gap-6 py-9 md:grid-cols-[minmax(0,0.8fr)_minmax(0,1.7fr)]"
              >
                <div>
                  <h2 className="text-lg font-bold text-gray-950">{definition.label}</h2>
                  <p className="mt-2 text-sm leading-6 text-gray-600">{definition.description}</p>
                </div>
                <ul className="divide-y divide-gray-200 border-y border-gray-200">
                  {categoryArticles.map((article) => (
                    <li key={article.path}>
                      <Link
                        to={article.path}
                        className="group flex items-start justify-between gap-4 py-4 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-blue-600 focus-visible:ring-offset-2"
                      >
                        <span>
                          <span className="block font-semibold text-gray-900 group-hover:text-blue-700">
                            {article.title}
                          </span>
                          <span className="mt-1 block text-sm leading-6 text-gray-600">
                            {article.summary}
                          </span>
                        </span>
                        <ArrowRight
                          aria-hidden="true"
                          className="mt-1 h-4 w-4 shrink-0 text-gray-400 group-hover:text-blue-700"
                        />
                      </Link>
                    </li>
                  ))}
                </ul>
              </section>
            );
          })}
        </div>
      )}
    </>
  );
};
