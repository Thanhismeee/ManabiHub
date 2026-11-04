import React, { useCallback, useEffect, useMemo } from 'react';
import {
  Box,
  Button,
  Grid,
  Pagination,
  Stack,
  Typography,
  Chip,
  IconButton,
  InputAdornment,
  TextField,
  Tabs,
  Tab,
  MenuItem,
} from '@mui/material';
import SearchOffIcon from '@mui/icons-material/SearchOff';
import RefreshIcon from '@mui/icons-material/Refresh';
import SearchRoundedIcon from '@mui/icons-material/SearchRounded';
import AutoAwesomeRoundedIcon from '@mui/icons-material/AutoAwesomeRounded';
import { useSearchParams } from 'react-router-dom';
import { PageHeader } from '../../../shared/components/PageHeader/PageHeader';
import { DecorativeKanjiWatermark } from '../../../shared/components/DecorativeKanjiWatermark/DecorativeKanjiWatermark';
import { CourseCatalogCard } from '../components/CourseCatalogCard';
import { CourseCatalogFiltersBar } from '../components/CourseCatalogFilters';
import { useCourseCatalog } from '../hooks/useCourseCatalog';
import { useCourseCategories } from '../hooks/useCourseCategories';
import { EmptyState } from '../../../shared/components/EmptyState/EmptyState';
import { ErrorState } from '../../../shared/components/ErrorState/ErrorState';
import type { CourseCatalogFilters } from '../types/catalogTypes';
import type { LearningLanguage, ProficiencyFramework } from '../../../shared/learning/languageProfiles';
import { useLocale } from '../../../shared/i18n/locale';

const DEFAULT_PAGE_SIZE = 12;
const PAGE_SIZE_OPTIONS = [12, 24, 36, 50];
const DEFAULT_SORT = 'enrollmentCount,desc';
const ALL_COURSES_SORT = 'publishedAt,desc';
const ALLOWED_SORTS = new Set([
  DEFAULT_SORT,
  'averageRating,desc',
  ALL_COURSES_SORT,
  'price,asc',
  'price,desc',
  'title,asc',
]);
const ALLOWED_JLPT_LEVELS = new Set(['N1', 'N2', 'N3', 'N4', 'N5']);
const ALLOWED_LANGUAGES = new Set<LearningLanguage>(['JAPANESE', 'KOREAN', 'CHINESE_SIMPLIFIED']);
const ALLOWED_FRAMEWORKS = new Set<ProficiencyFramework>(['JLPT', 'TOPIK', 'HSK', 'SOFL_INTERNAL']);

interface CatalogQuery {
  filters: CourseCatalogFilters;
  page: number;
  pageSize: number;
  sort: string;
}

const CATALOG_TABS = [
  { value: DEFAULT_SORT, label: 'Bán chạy nhất' },
  { value: 'averageRating,desc', label: 'Được đánh giá cao' },
  { value: ALL_COURSES_SORT, label: 'Tất cả khóa học' },
] as const;

function cleanText(value: string | null): string | undefined {
  const cleaned = value?.trim();
  return cleaned || undefined;
}

function parseNonNegativeNumber(value: string | null): number | undefined {
  if (value === null || value.trim() === '') return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : undefined;
}

function readCatalogQuery(params: URLSearchParams): CatalogQuery {
  const keyword = cleanText(params.get('keyword'));
  const category = cleanText(params.get('category'));
  const requestedLanguage = cleanText(params.get('learningLanguage'))?.toUpperCase() as LearningLanguage | undefined;
  const learningLanguage = requestedLanguage && ALLOWED_LANGUAGES.has(requestedLanguage)
    ? requestedLanguage
    : undefined;
  const requestedFramework = cleanText(params.get('proficiencyFramework'))?.toUpperCase() as ProficiencyFramework | undefined;
  const proficiencyFramework = requestedFramework && ALLOWED_FRAMEWORKS.has(requestedFramework)
    ? requestedFramework
    : undefined;
  const proficiencyLevel = cleanText(params.get('proficiencyLevel'))?.toUpperCase();
  const requestedJlpt = cleanText(params.get('jlptLevel'))?.toUpperCase();
  const jlptLevel =
    requestedJlpt && ALLOWED_JLPT_LEVELS.has(requestedJlpt) ? requestedJlpt : undefined;
  const minPrice = parseNonNegativeNumber(params.get('minPrice'));
  const requestedMaxPrice = parseNonNegativeNumber(params.get('maxPrice'));
  const maxPrice =
    requestedMaxPrice !== undefined &&
    (minPrice === undefined || requestedMaxPrice >= minPrice)
      ? requestedMaxPrice
      : undefined;

  const requestedPage = Number(params.get('page'));
  const page =
    Number.isInteger(requestedPage) && requestedPage >= 1 ? requestedPage - 1 : 0;
  const requestedPageSize = Number(params.get('size'));
  const pageSize = PAGE_SIZE_OPTIONS.includes(requestedPageSize)
    ? requestedPageSize
    : DEFAULT_PAGE_SIZE;
  const requestedSort = params.get('sort');
  const sort = requestedSort && ALLOWED_SORTS.has(requestedSort) ? requestedSort : DEFAULT_SORT;

  return {
    filters: {
      keyword,
      category,
      learningLanguage,
      proficiencyFramework,
      proficiencyLevel,
      jlptLevel,
      minPrice,
      maxPrice,
    },
    page,
    pageSize,
    sort,
  };
}

function buildCatalogParams(query: CatalogQuery): URLSearchParams {
  const params = new URLSearchParams();
  const { filters, page, sort } = query;

  if (filters.keyword) params.set('keyword', filters.keyword);
  if (filters.category) params.set('category', filters.category);
  if (filters.learningLanguage) params.set('learningLanguage', filters.learningLanguage);
  if (filters.proficiencyFramework) params.set('proficiencyFramework', filters.proficiencyFramework);
  if (filters.proficiencyLevel) params.set('proficiencyLevel', filters.proficiencyLevel);
  if (filters.jlptLevel) params.set('jlptLevel', filters.jlptLevel);
  if (filters.minPrice !== undefined) params.set('minPrice', String(filters.minPrice));
  if (filters.maxPrice !== undefined) params.set('maxPrice', String(filters.maxPrice));
  if (page > 0) params.set('page', String(page + 1));
  if (query.pageSize !== DEFAULT_PAGE_SIZE) params.set('size', String(query.pageSize));
  if (sort !== DEFAULT_SORT) params.set('sort', sort);

  return params;
}

export const CourseCatalogPage: React.FC = () => {
  const { t, locale } = useLocale();
  const [searchParams, setSearchParams] = useSearchParams();
  const rawQueryString = searchParams.toString();
  const query = useMemo(
    () => readCatalogQuery(new URLSearchParams(rawQueryString)),
    [rawQueryString],
  );

  const replaceQuery = useCallback(
    (nextQuery: CatalogQuery) => {
      setSearchParams(buildCatalogParams(nextQuery), { replace: true });
    },
    [setSearchParams],
  );

  useEffect(() => {
    const canonicalQueryString = buildCatalogParams(query).toString();
    if (canonicalQueryString !== rawQueryString) {
      setSearchParams(new URLSearchParams(canonicalQueryString), { replace: true });
    }
  }, [query, rawQueryString, setSearchParams]);

  const {
    data: categoriesData,
    isLoading: categoriesLoading,
    isSuccess: categoriesLoaded,
  } = useCourseCategories();
  const categories = categoriesData ?? [];
  const {
    data,
    isLoading,
    isError,
    isFetching,
    refetch,
  } = useCourseCatalog({
    ...query.filters,
    page: query.page,
    size: query.pageSize,
    sort: query.sort,
  });

  useEffect(() => {
    if (data && data.totalPages > 0 && query.page >= data.totalPages) {
      replaceQuery({ ...query, page: data.totalPages - 1 });
    }
  }, [data, query, replaceQuery]);

  useEffect(() => {
    const selectedCategory = query.filters.category;
    if (
      categoriesLoaded &&
      selectedCategory &&
      !categoriesData?.some((category) => category.code === selectedCategory)
    ) {
      replaceQuery({
        ...query,
        filters: { ...query.filters, category: undefined },
        page: 0,
      });
    }
  }, [categoriesData, categoriesLoaded, query, replaceQuery]);

  const handleFiltersChange = useCallback(
    (filters: CourseCatalogFilters) => {
      replaceQuery({ filters, page: 0, pageSize: query.pageSize, sort: query.sort });
    },
    [query.pageSize, query.sort, replaceQuery],
  );

  const handleSortChange = useCallback(
    (sort: string) => {
      replaceQuery({ filters: query.filters, page: 0, pageSize: query.pageSize, sort });
    },
    [query.filters, query.pageSize, replaceQuery],
  );

  const handlePageChange = (_event: React.ChangeEvent<unknown>, page: number) => {
    replaceQuery({ ...query, page: page - 1 });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const handlePageSizeChange = (event: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const nextPageSize = Number(event.target.value);
    if (!PAGE_SIZE_OPTIONS.includes(nextPageSize)) return;
    replaceQuery({ ...query, page: 0, pageSize: nextPageSize });
  };

  const selectedCatalogTab = CATALOG_TABS.some((tab) => tab.value === query.sort)
    ? query.sort
    : ALL_COURSES_SORT;

  const updateKeyword = (keyword: string) => {
    handleFiltersChange({ ...query.filters, keyword: keyword.trim() || undefined });
  };

  const [heroKeyword, setHeroKeyword] = React.useState(query.filters.keyword ?? '');
  useEffect(() => setHeroKeyword(query.filters.keyword ?? ''), [query.filters.keyword]);

  return (
    <Box component="main" sx={{ minHeight: '100vh', minWidth: 0, overflowX: 'hidden', flexGrow: 1, display: 'flex', flexDirection: 'column', bgcolor: '#FAF9F6', py: { xs: 3, md: 5 }, px: { xs: 2, sm: 3 } }}>
      <Box sx={{ maxWidth: '1280px', mx: 'auto', width: '100%', minWidth: 0, position: 'relative', flexGrow: 1, display: 'flex', flexDirection: 'column', minHeight: '50vh' }}>
        <DecorativeKanjiWatermark text={locale === 'ko' ? '배움' : '学习'} />
        <PageHeader
          title={t('Khám phá khóa học')}
          breadcrumbs={[{ label: t('Học viên') }, { label: t('Khám phá khóa học') }]}
        />

        <Box sx={{ position: 'relative', zIndex: 1, flexGrow: 1, display: 'flex', flexDirection: 'column' }}>
          <Box sx={{ position: 'sticky', top: { xs: 72, sm: 64 }, zIndex: 20, bgcolor: 'rgba(250, 249, 246, 0.92)', backdropFilter: 'blur(12px)', py: 2, mx: { xs: -2, sm: -3 }, px: { xs: 2, sm: 3 }, borderRadius: 2, transition: 'all 0.3s' }}>
            <CourseCatalogFiltersBar
              filters={query.filters}
              onFiltersChange={handleFiltersChange}
              categories={categories}
              categoriesLoading={categoriesLoading}
            />
            <Tabs
              value={selectedCatalogTab}
              onChange={(_event, value: string) => handleSortChange(value)}
              variant="scrollable"
              scrollButtons="auto"
              allowScrollButtonsMobile
              aria-label="Cách sắp xếp khóa học"
              sx={{
                mt: 1.5,
                minHeight: 42,
                '& .MuiTabs-indicator': { bgcolor: '#C41E3A', height: 3, borderRadius: 3 },
                '& .MuiTab-root': {
                  minHeight: 42,
                  textTransform: 'none',
                  fontWeight: 700,
                  color: '#64748B',
                  '&.Mui-selected': { color: '#C41E3A' },
                },
              }}
            >
              {CATALOG_TABS.map((tab) => (
                <Tab key={tab.value} value={tab.value} label={t(tab.label)} />
              ))}
            </Tabs>
          </Box>

          <Box
            component="section"
            aria-labelledby="course-discovery-search"
            sx={{ mb: 3, p: { xs: 2.5, md: 3.5 }, borderRadius: 3, color: '#fff', position: 'relative', overflow: 'hidden', background: 'linear-gradient(120deg, #14284B 0%, #243B64 65%, #7F1734 100%)', boxShadow: '0 14px 30px rgba(20,40,75,0.16)' }}
          >
            <Box sx={{ position: 'absolute', inset: 0, opacity: 0.1, backgroundImage: 'repeating-radial-gradient(ellipse at 20% 20%, transparent 0, transparent 12px, #fff 13px, transparent 14px)' }} />
            <Box sx={{ position: 'relative' }}>
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', mb: 1 }}>
                <AutoAwesomeRoundedIcon sx={{ color: '#F9C74F' }} />
                <Typography variant="overline" sx={{ letterSpacing: 1.5, fontWeight: 800, color: '#FDE68A' }}>
                  {t('Học ngôn ngữ cùng ManabiHub')}
                </Typography>
              </Stack>
              <Typography id="course-discovery-search" variant="h5" sx={{ fontWeight: 800, mb: 0.75 }}>
                {t('Chọn khóa học phù hợp với mục tiêu của bạn')}
              </Typography>
              <Typography variant="body2" sx={{ color: 'rgba(255,255,255,0.78)', mb: 2, maxWidth: 720 }}>
                {t('Chọn tiếng Hàn, tiếng Trung hoặc tiếng Nhật; lọc theo trình độ, kỹ năng và học phí.')}
              </Typography>
              <TextField
                fullWidth
                value={heroKeyword}
                onChange={(event) => setHeroKeyword(event.target.value)}
                onKeyDown={(event) => { if (event.key === 'Enter') updateKeyword(heroKeyword); }}
                placeholder={t('Tìm TOPIK, HSK, giao tiếp...')}
                aria-label={t('Tìm khóa học')}
                slotProps={{
                  input: {
                    endAdornment: (
                      <InputAdornment position="end">
                        <IconButton
                          aria-label={t('Tìm kiếm')}
                          edge="end"
                          onClick={() => updateKeyword(heroKeyword)}
                          sx={{
                            color: '#C41E3A',
                            bgcolor: 'transparent',
                            '&:hover': {
                              color: '#9F1239',
                              bgcolor: 'transparent',
                            },
                          }}
                        >
                          <SearchRoundedIcon />
                        </IconButton>
                      </InputAdornment>
                    ),
                  },
                }}
                sx={{ maxWidth: 760, bgcolor: '#fff', borderRadius: 1.5, '& .MuiOutlinedInput-root': { borderRadius: 1.5 } }}
              />
              <Stack direction="row" spacing={1} useFlexGap sx={{ mt: 1.5, flexWrap: 'wrap' }}>
                {['TOPIK', 'HSK', 'JLPT'].map((tag) => (
                  <Chip key={tag} label={`#${tag}`} onClick={() => { setHeroKeyword(tag); updateKeyword(tag); }} size="small" sx={{ color: '#fff', borderColor: 'rgba(255,255,255,0.4)', bgcolor: 'rgba(255,255,255,0.08)' }} variant="outlined" />
                ))}
              </Stack>
            </Box>
          </Box>

        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          sx={{ my: 3, minHeight: 28, justifyContent: 'space-between', alignItems: { xs: 'flex-start', sm: 'center' }, gap: 2 }}
        >
          <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 1.5 }}>
            <Typography variant="subtitle1" sx={{ fontWeight: 700 }}>
              {data ? `${data.totalElements} ${t('Khóa học')}` : t('Danh sách khóa học')}
            </Typography>
            {/* Active Filter Badges */}
            <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap' }}>
              {query.filters.category && (
                <Chip size="small" label={`Danh mục: ${categories.find(c => c.code === query.filters.category)?.name || query.filters.category}`} onDelete={() => handleFiltersChange({ ...query.filters, category: undefined })} sx={{ bgcolor: '#fee2e2', color: '#C41E3A', fontWeight: 700, '& .MuiChip-deleteIcon': { color: '#C41E3A', '&:hover': { color: '#9f1239' } } }} />
              )}
              {query.filters.jlptLevel && (
                <Chip size="small" label={`Trình độ: ${query.filters.jlptLevel}`} onDelete={() => handleFiltersChange({ ...query.filters, jlptLevel: undefined })} sx={{ bgcolor: '#fee2e2', color: '#C41E3A', fontWeight: 700, '& .MuiChip-deleteIcon': { color: '#C41E3A', '&:hover': { color: '#9f1239' } } }} />
              )}
              {query.filters.learningLanguage && (
                <Chip size="small" label={`Ngôn ngữ: ${query.filters.learningLanguage}`} onDelete={() => handleFiltersChange({ ...query.filters, learningLanguage: undefined, proficiencyFramework: undefined, proficiencyLevel: undefined })} sx={{ bgcolor: '#fee2e2', color: '#C41E3A', fontWeight: 700, '& .MuiChip-deleteIcon': { color: '#C41E3A' } }} />
              )}
              {query.filters.proficiencyFramework && (
                <Chip size="small" label={`Khung: ${query.filters.proficiencyFramework}`} onDelete={() => handleFiltersChange({ ...query.filters, proficiencyFramework: undefined, proficiencyLevel: undefined })} sx={{ bgcolor: '#fee2e2', color: '#C41E3A', fontWeight: 700, '& .MuiChip-deleteIcon': { color: '#C41E3A' } }} />
              )}
              {query.filters.proficiencyLevel && (
                <Chip size="small" label={`Cấp độ: ${query.filters.proficiencyLevel}`} onDelete={() => handleFiltersChange({ ...query.filters, proficiencyLevel: undefined })} sx={{ bgcolor: '#fee2e2', color: '#C41E3A', fontWeight: 700, '& .MuiChip-deleteIcon': { color: '#C41E3A' } }} />
              )}
              {query.filters.minPrice !== undefined && (
                <Chip size="small" label={`Giá từ: ${query.filters.minPrice.toLocaleString()}đ`} onDelete={() => handleFiltersChange({ ...query.filters, minPrice: undefined })} sx={{ bgcolor: '#fee2e2', color: '#C41E3A', fontWeight: 700, '& .MuiChip-deleteIcon': { color: '#C41E3A', '&:hover': { color: '#9f1239' } } }} />
              )}
              {query.filters.maxPrice !== undefined && (
                <Chip size="small" label={`Giá đến: ${query.filters.maxPrice.toLocaleString()}đ`} onDelete={() => handleFiltersChange({ ...query.filters, maxPrice: undefined })} sx={{ bgcolor: '#fee2e2', color: '#C41E3A', fontWeight: 700, '& .MuiChip-deleteIcon': { color: '#C41E3A', '&:hover': { color: '#9f1239' } } }} />
              )}
            </Stack>
          </Box>
          {isFetching && !isLoading && (
            <Typography variant="caption" color="text.secondary">
              {t('Đang cập nhật...')}
            </Typography>
          )}
          <Button
            variant="outlined"
            size="small"
            startIcon={<RefreshIcon />}
            disabled={isFetching}
            onClick={() => void refetch()}
            sx={{ textTransform: 'none', fontWeight: 700, whiteSpace: 'nowrap' }}
          >
            {t('Tải lại danh sách')}
          </Button>
        </Stack>

        {isLoading && (
          <Grid container spacing={3} sx={{ mt: 1 }}>
                {Array.from(new Array(Math.min(query.pageSize, 12))).map((_, index) => (
              <Grid key={index} size={{ xs: 12, md: 6, lg: 4 }}>
                <Box sx={{ bgcolor: 'white', borderRadius: 4, overflow: 'hidden', border: '1px solid', borderColor: 'grey.100', height: 340 }}>
                  <Box sx={{ width: '100%', aspectRatio: '16/9', bgcolor: 'grey.200', animation: 'pulse 1.5s infinite ease-in-out' }} />
                  <Box sx={{ p: 2 }}>
                    <Box sx={{ width: '30%', height: 16, bgcolor: 'grey.200', borderRadius: 1, mb: 1.5, animation: 'pulse 1.5s infinite ease-in-out' }} />
                    <Box sx={{ width: '90%', height: 24, bgcolor: 'grey.200', borderRadius: 1, mb: 1, animation: 'pulse 1.5s infinite ease-in-out' }} />
                    <Box sx={{ width: '60%', height: 24, bgcolor: 'grey.200', borderRadius: 1, mb: 3, animation: 'pulse 1.5s infinite ease-in-out' }} />
                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mt: 'auto' }}>
                      <Box sx={{ width: '40%', height: 24, bgcolor: 'grey.200', borderRadius: 1, animation: 'pulse 1.5s infinite ease-in-out' }} />
                      <Box sx={{ width: '20%', height: 24, bgcolor: 'grey.200', borderRadius: 1, animation: 'pulse 1.5s infinite ease-in-out' }} />
                    </Box>
                  </Box>
                </Box>
              </Grid>
            ))}
          </Grid>
        )}

        {isError && (
          <ErrorState
            title={t('Không thể tải danh sách khóa học')}
            message={t('Vui lòng kiểm tra kết nối và thử lại.')}
            onRetry={() => refetch()}
          />
        )}

        {!isLoading && !isError && data?.content.length === 0 && (
          <Box sx={{ py: 6, bgcolor: 'background.paper', border: '1px solid', borderColor: 'divider', borderRadius: 3 }}>
            <EmptyState
              title={t('Không tìm thấy khóa học phù hợp')}
              description={t('Hãy thử từ khóa hoặc khoảng giá khác.')}
              icon={<SearchOffIcon sx={{ fontSize: 56, color: 'text.secondary' }} />}
              actionLabel={t('Xóa bộ lọc')}
              onAction={() => {
                replaceQuery({ filters: {}, page: 0, pageSize: query.pageSize, sort: DEFAULT_SORT });
              }}
            />
          </Box>
        )}

        {!isLoading && !isError && data && data.content.length > 0 && (
          <Box sx={{ flexGrow: 1 }}>
            <Grid
              container
              spacing={3}
              sx={{ opacity: isFetching ? 0.6 : 1, transition: 'opacity 160ms ease', alignContent: 'flex-start' }}
            >
              {data.content.map((course) => (
                <Grid key={course.id} size={{ xs: 12, md: 6, lg: 4 }}>
                  <CourseCatalogCard course={course} />
                </Grid>
              ))}
            </Grid>
          </Box>
        )}

        {!isLoading && !isError && data && data.totalElements > 0 && (
          <Stack
            direction={{ xs: 'column', sm: 'row' }}
            spacing={2}
            sx={{ mt: 5, alignItems: 'center', justifyContent: 'center' }}
          >
            {data.totalPages > 1 && (
              <Pagination
                count={data.totalPages}
                page={query.page + 1}
                onChange={handlePageChange}
                color="primary"
                shape="rounded"
              />
            )}
            <TextField
              select
              size="small"
              label={t('Khóa học/trang')}
              value={query.pageSize}
              onChange={handlePageSizeChange}
              sx={{ minWidth: 150 }}
              slotProps={{ select: { MenuProps: { disableScrollLock: true } } }}
            >
              {PAGE_SIZE_OPTIONS.map((option) => (
                <MenuItem key={option} value={option}>{option}</MenuItem>
              ))}
            </TextField>
          </Stack>
        )}
        </Box>
      </Box>
    </Box>
  );
};
