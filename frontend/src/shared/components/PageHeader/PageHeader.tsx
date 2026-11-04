import React from 'react';
import { Box, Typography, Breadcrumbs, Link, Stack } from '@mui/material';
import NavigateNextIcon from '@mui/icons-material/NavigateNext';

interface PageHeaderProps {
  title: React.ReactNode;
  subtitle?: string;
  watermark?: string;
  breadcrumbs?: Array<{ label: string; href?: string }>;
  action?: React.ReactNode;
}

export const PageHeader: React.FC<PageHeaderProps> = ({ title, subtitle, breadcrumbs, action }) => {
  return (
    <Box
      sx={{
        mb: { xs: 3, md: 4 },
        position: 'relative',
      }}
    >
      {/* Breadcrumb on top — small, muted */}
      {breadcrumbs && breadcrumbs.length > 0 && (
        <Breadcrumbs
          separator={<NavigateNextIcon sx={{ fontSize: 14, color: '#94a3b8' }} />}
          aria-label="breadcrumb"
          sx={{
            mb: 1.5,
            '& ol': { listStyle: 'none', listStyleType: 'none', pl: 0, m: 0, display: 'flex', alignItems: 'center' },
            '& li': { listStyle: 'none', listStyleType: 'none', pl: 0, m: 0, '&::before': { content: 'none' }, '&::marker': { content: 'none' } },
          }}
        >
          {breadcrumbs.map((bc, index) => {
            const isLast = index === breadcrumbs.length - 1;
            if (isLast || !bc.href) {
              return (
                <Typography key={index} variant="caption" sx={{ color: isLast ? '#64748b' : '#94a3b8', fontWeight: isLast ? 600 : 400 }}>
                  {bc.label}
                </Typography>
              );
            }
            return (
              <Link key={index} underline="hover" href={bc.href} variant="caption" sx={{ color: '#94a3b8', '&:hover': { color: '#C41E3A' } }}>
                {bc.label}
              </Link>
            );
          })}
        </Breadcrumbs>
      )}

      {/* Title row: Main title + JP subtitle inline + action on the right */}
      <Stack direction={{ xs: 'column', sm: 'row' }} spacing={{ xs: 1.5, sm: 0 }} sx={{ justifyContent: 'space-between', alignItems: { xs: 'stretch', sm: 'flex-start' }, minWidth: 0 }}>
        <Box sx={{ display: 'flex', alignItems: 'baseline', flexWrap: 'wrap', minWidth: 0, gap: { xs: 0.5, md: 1.5 } }}>
          <Typography variant="h4" component="h1" sx={{ minWidth: 0, fontWeight: 800, color: '#0f172a', lineHeight: 1.3, overflowWrap: 'anywhere' }}>
            {title}
          </Typography>
          {subtitle && (
            <Typography component="span" sx={{ fontSize: '0.9rem', color: '#94a3b8', fontWeight: 500, whiteSpace: { xs: 'normal', sm: 'nowrap' }, overflowWrap: 'anywhere' }}>
              {subtitle}
            </Typography>
          )}
        </Box>

        {action && (
          <Box sx={{ display: 'flex', gap: 2, flexShrink: 0, ml: { xs: 0, sm: 2 }, flexWrap: 'wrap' }}>
            {action}
          </Box>
        )}
      </Stack>
    </Box>
  );
};
