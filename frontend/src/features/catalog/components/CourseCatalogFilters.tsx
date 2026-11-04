import React, { useCallback, useEffect, useState } from 'react';
import {
  Box,
  Button,
  Divider,
  Drawer,
  FormControl,
  IconButton,
  MenuItem,
  Select,
  Stack,
  TextField,
  Typography,
  useMediaQuery,
  useTheme,
} from '@mui/material';
import InputAdornment from '@mui/material/InputAdornment';
import CloseIcon from '@mui/icons-material/Close';
import FilterListIcon from '@mui/icons-material/FilterList';
import RestartAltIcon from '@mui/icons-material/RestartAlt';
import type { CourseCatalogFilters, CourseCategory } from '../types/catalogTypes';
import { useLocale } from '../../../shared/i18n/locale';
import {
  LEARNING_LANGUAGES,
  frameworkOptions,
  levelOptions,
  type LearningLanguage,
  type ProficiencyFramework,
} from '../../../shared/learning/languageProfiles';

interface CourseCatalogFiltersBarProps {
  filters: CourseCatalogFilters;
  onFiltersChange: (filters: CourseCatalogFilters) => void;
  categories: CourseCategory[];
  categoriesLoading?: boolean;
}

function formatPriceInput(value?: number): string {
  return value === undefined ? '' : value.toLocaleString('en-US');
}

function parsePrice(value: string): number | undefined {
  const numericStr = value.replace(/\D/g, '');
  if (!numericStr) return undefined;
  const parsed = Number(numericStr);
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : undefined;
}

export const CourseCatalogFiltersBar: React.FC<CourseCatalogFiltersBarProps> = ({
  filters,
  onFiltersChange,
  categories,
  categoriesLoading,
}) => {
  const theme = useTheme();
  const { t } = useLocale();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [minPrice, setMinPrice] = useState(formatPriceInput(filters.minPrice));
  const [maxPrice, setMaxPrice] = useState(formatPriceInput(filters.maxPrice));
  const [priceError, setPriceError] = useState('');

  useEffect(() => {
    setMinPrice(formatPriceInput(filters.minPrice));
    setMaxPrice(formatPriceInput(filters.maxPrice));
  }, [filters.minPrice, filters.maxPrice]);

  const updateFilter = useCallback((field: keyof CourseCatalogFilters, value?: string) => {
    onFiltersChange({
      ...filters,
      [field]: value || undefined,
    });
  }, [filters, onFiltersChange]);

  const handlePriceChange = (setter: React.Dispatch<React.SetStateAction<string>>) => (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
    const numericStr = e.target.value.replace(/\D/g, '');
    setter(numericStr ? Number(numericStr).toLocaleString('en-US') : '');
  };

  const applyPrice = () => {
    const parsedMin = parsePrice(minPrice);
    const parsedMax = parsePrice(maxPrice);
    const hasInvalidValue =
      (minPrice.trim() !== '' && parsedMin === undefined) ||
      (maxPrice.trim() !== '' && parsedMax === undefined);

    if (hasInvalidValue) {
      setPriceError('Giá phải là số không âm.');
      return;
    }

    if (parsedMin !== undefined && parsedMax !== undefined && parsedMin > parsedMax) {
      setPriceError('Giá tối thiểu không được lớn hơn giá tối đa.');
      return;
    }

    setPriceError('');
    onFiltersChange({
      ...filters,
      minPrice: parsedMin,
      maxPrice: parsedMax,
    });
    setDrawerOpen(false);
  };

  const clearFilters = () => {
    setMinPrice('');
    setMaxPrice('');
    setPriceError('');
    onFiltersChange({});
    setDrawerOpen(false);
  };

  const filterFields = (
    <Box
      sx={{
        display: 'grid',
        gridTemplateColumns: isMobile ? '1fr' : 'repeat(4, minmax(0, 1fr))',
        gap: 2,
        alignItems: 'center',
      }}
    >
      <FormControl fullWidth size="small">
        <Select
          displayEmpty
          value={filters.category ?? ''}
          disabled={categoriesLoading}
          onChange={(event) => updateFilter('category', event.target.value)}
          sx={{ '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: '#C41E3A' } }}
        >
          <MenuItem value=""><Typography color="text.secondary">{t('Danh mục')}</Typography></MenuItem>
          {categories.map((category) => (
            <MenuItem key={category.id} value={category.code}>
              {category.name}
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      <FormControl fullWidth size="small">
        <Select
          displayEmpty
          value={filters.learningLanguage ?? ''}
          onChange={(event) => {
            const learningLanguage = (event.target.value || undefined) as LearningLanguage | undefined;
            onFiltersChange({
              ...filters,
              learningLanguage,
              proficiencyFramework: undefined,
              proficiencyLevel: undefined,
              jlptLevel: undefined,
            });
          }}
          sx={{ '&.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: '#C41E3A' } }}
        >
          <MenuItem value=""><Typography color="text.secondary">{t('Ngôn ngữ')}</Typography></MenuItem>
          {LEARNING_LANGUAGES.map((language) => (
            <MenuItem key={language.value} value={language.value}>
              {t(language.label)}
            </MenuItem>
          ))}
        </Select>
      </FormControl>

      <FormControl fullWidth size="small" disabled={!filters.learningLanguage}>
        <Select
          displayEmpty
          value={filters.proficiencyFramework ?? ''}
          onChange={(event) => {
            const proficiencyFramework = (event.target.value || undefined) as ProficiencyFramework | undefined;
            onFiltersChange({ ...filters, proficiencyFramework, proficiencyLevel: undefined, jlptLevel: undefined });
          }}
        >
          <MenuItem value=""><Typography color="text.secondary">{t('Khung trình độ')}</Typography></MenuItem>
          {(filters.learningLanguage ? frameworkOptions(filters.learningLanguage) : []).map((framework) => (
            <MenuItem key={framework.value} value={framework.value}>{t(framework.label)}</MenuItem>
          ))}
        </Select>
      </FormControl>

      {filters.proficiencyFramework === 'SOFL_INTERNAL' ? (
        <TextField
          size="small"
          placeholder={t('Mã cấp độ SOFL')}
          value={filters.proficiencyLevel ?? ''}
          onChange={(event) => updateFilter('proficiencyLevel', event.target.value.toUpperCase())}
        />
      ) : (
        <FormControl fullWidth size="small" disabled={!filters.proficiencyFramework}>
          <Select
            displayEmpty
            value={filters.proficiencyLevel ?? ''}
            onChange={(event) => updateFilter('proficiencyLevel', event.target.value)}
          >
            <MenuItem value=""><Typography color="text.secondary">{t('Cấp độ')}</Typography></MenuItem>
            {(filters.proficiencyFramework ? levelOptions(filters.proficiencyFramework) : []).map((level) => (
              <MenuItem key={level.value} value={level.value}>{level.label}</MenuItem>
            ))}
          </Select>
        </FormControl>
      )}

      <Stack direction="column" sx={{ gridColumn: isMobile ? 'auto' : 'span 2' }}>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={1.5}
        >
          <TextField
            placeholder={t('Giá từ')}
            type="text"
            value={minPrice}
            onChange={handlePriceChange(setMinPrice)}
            onBlur={applyPrice}
            onKeyDown={(e) => { if (e.key === 'Enter') applyPrice(); }}
            size="small"
            fullWidth
            slotProps={{
              input: { endAdornment: <InputAdornment position="end" sx={{ mr: 1 }}>đ</InputAdornment> },
              htmlInput: { inputMode: 'numeric' }
            }}
            sx={{ '& .MuiOutlinedInput-root.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: '#C41E3A' } }}
          />
          <TextField
            placeholder={t('Giá đến')}
            type="text"
            value={maxPrice}
            onChange={handlePriceChange(setMaxPrice)}
            onBlur={applyPrice}
            onKeyDown={(e) => { if (e.key === 'Enter') applyPrice(); }}
            size="small"
            fullWidth
            slotProps={{
              input: { endAdornment: <InputAdornment position="end" sx={{ mr: 1 }}>đ</InputAdornment> },
              htmlInput: { inputMode: 'numeric' }
            }}
            sx={{ '& .MuiOutlinedInput-root.Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: '#C41E3A' } }}
          />
        </Stack>
        {priceError && (
          <Typography variant="caption" color="error" sx={{ mt: 0.5, px: 0.5 }}>
            {t(priceError)}
          </Typography>
        )}
      </Stack>

      <Button
        variant="text"
        onClick={clearFilters}
        startIcon={<RestartAltIcon />}
        sx={{
          color: '#475569',
          fontWeight: 600,
          bgcolor: '#f1f5f9',
          borderRadius: 2,
          px: 2, py: 1,
          transition: 'all 0.2s',
          '&:hover': { color: '#C41E3A', bgcolor: '#fef2f2' }
        }}
      >
        {t('Xóa bộ lọc')}
      </Button>
    </Box>
  );

  return (
    <Box>
      {isMobile ? (
        <Stack direction="row" sx={{ mb: 2, justifyContent: 'space-between', alignItems: 'center' }}>
          <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>{t('Bộ lọc')}</Typography>
          <Button
            variant="outlined"
            startIcon={<FilterListIcon />}
            onClick={() => setDrawerOpen(true)}
            sx={{
              borderColor: '#e2e8f0', color: '#475569',
              '&:hover': { borderColor: '#C41E3A', color: '#C41E3A' }
            }}
          >
            {t('Mở bộ lọc')}
          </Button>
        </Stack>
      ) : (
        <Box
          sx={{
            p: 2,
            border: '1px solid',
            borderColor: 'divider',
            bgcolor: 'background.paper',
            borderRadius: 3,
            boxShadow: '0 4px 12px rgba(0,0,0,0.02)',
          }}
        >
          {filterFields}
        </Box>
      )}

      <Drawer
        anchor="bottom"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        slotProps={{ paper: { sx: { maxHeight: '88vh', borderRadius: '8px 8px 0 0' } } }}
      >
        <Box sx={{ p: 2.5 }}>
          <Stack
            direction="row"
            sx={{ alignItems: 'center', justifyContent: 'space-between' }}
          >
            <Typography variant="h6">{t('Bộ lọc khóa học')}</Typography>
            <IconButton
              aria-label={t('Đóng bộ lọc')}
              title={t('Đóng')}
              onClick={() => setDrawerOpen(false)}
            >
              <CloseIcon />
            </IconButton>
          </Stack>
          <Divider sx={{ my: 2 }} />
          {filterFields}
        </Box>
      </Drawer>
    </Box>
  );
};
