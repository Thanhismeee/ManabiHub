import type { ReactNode } from 'react';
import { Helmet } from 'react-helmet-async';
import { Link } from 'react-router-dom';
import {
  getHelpCategory,
  HELP_ARTICLES,
} from '../content/articleRegistry';
import type { HelpArticleMetadata } from '../types';
import { formatReviewedDate } from '../utils/policyFormatting';
import { Box, Stack, Typography, Chip, List, ListItem, ListItemButton, ListItemText, Divider, Paper } from '@mui/material';
import { PageHeader } from '../../../shared/components/PageHeader/PageHeader';
import ArrowForwardIosIcon from '@mui/icons-material/ArrowForwardIos';

interface ArticleLayoutProps {
  article: HelpArticleMetadata;
  children: ReactNode;
}

export const ArticleLayout = ({ article, children }: ArticleLayoutProps) => {
  const category = getHelpCategory(article.category);
  const relatedArticles = article.relatedPaths
    .map((path) => HELP_ARTICLES.find((item) => item.path === path))
    .filter((item): item is HelpArticleMetadata => Boolean(item?.discoverable));

  return (
    <>
      <Helmet title={`${article.title} | ManabiHub`}>
        <meta content={article.summary} name="description" />
        {article.status === 'draft' && <meta content="noindex, nofollow" name="robots" />}
      </Helmet>

      <Box sx={{ maxWidth: 800, mx: 'auto', pb: 10 }}>
        <PageHeader
          title={article.title}
          breadcrumbs={[
            { label: 'Trung tâm trợ giúp', href: '/help' },
            { label: category.label, href: `/help?category=${article.category}` },
            { label: article.title }
          ]}
        />

        <Paper elevation={0} sx={{ p: { xs: 2, md: 4 }, border: '1px solid', borderColor: 'divider', borderRadius: 2 }}>
          <Stack spacing={2} sx={{ mb: 4, pb: 4, borderBottom: '1px solid', borderColor: 'divider' }}>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Chip
                label={article.status === 'draft' ? 'Bản dự thảo chưa có hiệu lực' : 'Nội dung tạm thời'}
                size="small"
                color={article.status === 'draft' ? 'warning' : 'default'}
                sx={{ fontWeight: 600, borderRadius: 1 }}
              />
              <Typography variant="caption" color="text.secondary">
                Phiên bản {article.policyVersion}
              </Typography>
            </Stack>
            <Typography variant="body1" color="text.secondary" sx={{ fontSize: '1.1rem', lineHeight: 1.6 }}>
              {article.summary}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              Rà soát lần cuối: {formatReviewedDate(article.lastReviewedAt)}
            </Typography>
          </Stack>

          <Box
            sx={{
              '& h2': { fontSize: '1.5rem', fontWeight: 700, mt: 4, mb: 2, color: 'text.primary' },
              '& h3': { fontSize: '1.25rem', fontWeight: 700, mt: 3, mb: 2, color: 'text.primary' },
              '& p': { mb: 2, lineHeight: 1.7, color: 'text.secondary' },
              '& ul': { mb: 2, pl: 3, color: 'text.secondary', listStyleType: 'disc' },
              '& li': { mb: 1 },
              '& strong': { color: 'text.primary' },
              '& a': { color: 'primary.main', textDecoration: 'none', '&:hover': { textDecoration: 'underline' } },
            }}
          >
            {children}
          </Box>

          {relatedArticles.length > 0 && (
            <Box sx={{ mt: 6, pt: 4, borderTop: '1px solid', borderColor: 'divider' }}>
              <Typography variant="h6" sx={{ fontWeight: 700, mb: 2 }}>
                Bài viết liên quan
              </Typography>
              <List disablePadding sx={{ border: '1px solid', borderColor: 'divider', borderRadius: 1 }}>
                {relatedArticles.map((related, index) => (
                  <Box key={related.path}>
                    <ListItem disablePadding>
                      <ListItemButton component={Link} to={related.path} sx={{ py: 2 }}>
                        <ListItemText 
                          primary={
                            <Typography sx={{ fontWeight: 600 }}>
                              {related.title}
                            </Typography>
                          }
                        />
                        <ArrowForwardIosIcon sx={{ fontSize: 14, color: 'text.secondary' }} />
                      </ListItemButton>
                    </ListItem>
                    {index < relatedArticles.length - 1 && <Divider />}
                  </Box>
                ))}
              </List>
            </Box>
          )}
        </Paper>
      </Box>
    </>
  );
};
