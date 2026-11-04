import React, { useState } from 'react';
import {
  Alert,
  Autocomplete,
  Box,
  Button,
  Card,
  CardContent,
  Chip,
  CircularProgress,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Grid,
  Pagination,
  Stack,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from '@mui/material';
import RefreshIcon from '@mui/icons-material/Refresh';
import { useQuery } from '@tanstack/react-query';
import { Navigate } from 'react-router-dom';
import type { PageResponse } from '../../../shared/types/api';
import { getAuthSession, hasAnyRole } from '../../../shared/auth/authSession';
import { ROUTES } from '../../../shared/constants/routes';
import { ROLES } from '../../../shared/constants/roles';
import { adminAuditApi, type AuditLogDto, type AuditLogFilterParams } from '../api/adminAuditApi';
import {
  ACTIVE_AUDIT_ROLE_CODES,
  AUDIT_ROLE_LABELS,
  filterAuditRoleOptions,
  normalizeAuditRoleFilter,
} from '../utils/auditRoleFilter';
import { AdminAuditLogDetailPage } from './AdminAuditLogDetailPage';

const DEFAULT_PAGE_SIZE = 10;

export const AdminAuditLogPage: React.FC = () => {
  const session = getAuthSession('admin');
  const isSystemAdmin = session ? hasAnyRole(session, [ROLES.SYSTEM_ADMIN]) : false;

  const [filterParams, setFilterParams] = useState<AuditLogFilterParams>({
    page: 0,
    size: DEFAULT_PAGE_SIZE,
  });
  const [filterDraft, setFilterDraft] = useState<AuditLogFilterParams>({
    page: 0,
    size: DEFAULT_PAGE_SIZE,
  });
  const [selectedAuditId, setSelectedAuditId] = useState<string | null>(null);

  const { data, isLoading, isFetching, error, refetch } = useQuery<PageResponse<AuditLogDto>, Error>({
    queryKey: ['audit-logs', filterParams],
    queryFn: () => adminAuditApi.getAuditLogs(filterParams),
    enabled: isSystemAdmin,
    // Keep the previous page visible while the next page is fetched. This
    // prevents the table from jumping to an empty state on every page click.
    placeholderData: (previous) => previous,
  });

  if (!isSystemAdmin) {
    return <Navigate to={ROUTES.ADMIN.DASHBOARD} replace />;
  }

  const updateDraft = (field: keyof AuditLogFilterParams, value: string) => {
    setFilterDraft((current) => ({ ...current, [field]: value }));
  };

  const handleApplyFilter = () => {
    setFilterParams({
      ...filterDraft,
      role: normalizeAuditRoleFilter(filterDraft.role),
      page: 0,
      size: DEFAULT_PAGE_SIZE,
    });
  };

  const handleClearFilter = () => {
    const emptyFilters: AuditLogFilterParams = {
      page: 0,
      size: filterParams.size ?? DEFAULT_PAGE_SIZE,
    };
    setFilterDraft(emptyFilters);
    setFilterParams(emptyFilters);
  };

  const logs = data?.content ?? [];
  const page = filterParams.page ?? 0;
  const totalPages = Math.max(data?.totalPages ?? 1, 1);

  return (
    <Box sx={{ p: { xs: 1.5, md: 3 }, maxWidth: 1600, mx: 'auto' }}>
      <Stack sx={{ display: 'flex', flexDirection: { xs: 'column', sm: 'row' }, justifyContent: 'space-between', alignItems: { sm: 'center' }, gap: 1, mb: 2 }}>
        <Box>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            Nhật ký hệ thống (Audit Logs)
          </Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Theo dõi các thay đổi quan trọng của hệ thống; dữ liệu được tải theo từng trang.
          </Typography>
        </Box>
        <Button variant="outlined" startIcon={<RefreshIcon />} onClick={() => refetch()} disabled={isFetching}>
          Làm mới
        </Button>
      </Stack>

      <Card sx={{ mb: 2 }}>
        <CardContent>
          <Grid container spacing={1.5} sx={{ alignItems: 'center' }}>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField
                fullWidth
                size="small"
                label="Người thực hiện (Tên/Email/ID)"
                value={filterDraft.actor ?? ''}
                onChange={(event) => updateDraft('actor', event.target.value)}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4, md: 2 }}>
              <Autocomplete
                freeSolo
                fullWidth
                options={ACTIVE_AUDIT_ROLE_CODES}
                inputValue={filterDraft.role ?? ''}
                filterOptions={filterAuditRoleOptions}
                onInputChange={(_, value) => updateDraft('role', value)}
                onChange={(_, value) => updateDraft('role', value ?? '')}
                noOptionsText="Không có vai trò phù hợp"
                renderOption={(props, roleCode) => (
                  <Box component="li" {...props} key={roleCode}>
                    <Box>
                      <Typography variant="body2" sx={{ fontWeight: 700 }}>
                        {AUDIT_ROLE_LABELS[roleCode] ?? roleCode}
                      </Typography>
                      <Typography variant="caption" color="text.secondary">
                        {roleCode}
                      </Typography>
                    </Box>
                  </Box>
                )}
                renderInput={(params) => (
                  <TextField
                    {...params}
                    size="small"
                    label="Vai trò"
                    placeholder="Chọn hoặc nhập vai trò"
                  />
                )}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4, md: 2 }}>
              <TextField
                fullWidth
                size="small"
                label="Loại đối tượng"
                value={filterDraft.targetType ?? ''}
                onChange={(event) => updateDraft('targetType', event.target.value)}
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4, md: 2 }}>
              <TextField
                fullWidth
                size="small"
                label="Hành động"
                value={filterDraft.action ?? ''}
                onChange={(event) => updateDraft('action', event.target.value)}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 3 }}>
              <Stack direction="row" spacing={1}>
                <Button variant="contained" onClick={handleApplyFilter} sx={{ flex: 1 }}>
                  Lọc
                </Button>
                <Button variant="outlined" onClick={handleClearFilter} sx={{ flex: 1 }}>
                  Xóa lọc
                </Button>
              </Stack>
            </Grid>
          </Grid>
        </CardContent>
      </Card>

      {error && (
        <Alert
          severity="error"
          sx={{ mb: 2 }}
          action={
            <Button color="inherit" size="small" onClick={() => refetch()}>
              Thử lại
            </Button>
          }
        >
          Không thể tải nhật ký. {error.message}
        </Alert>
      )}

      <Card>
        <TableContainer sx={{ overflowX: 'auto' }}>
          <Table sx={{ minWidth: 920 }} aria-label="Bảng nhật ký hệ thống">
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 700 }}>Thời gian</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Người thực hiện</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Vai trò</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Hành động</TableCell>
                <TableCell sx={{ fontWeight: 700 }}>Đối tượng</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }}>Chi tiết</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 8 }}>
                    <CircularProgress size={30} aria-label="Đang tải nhật ký" />
                  </TableCell>
                </TableRow>
              ) : logs.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 8 }}>
                    <Typography variant="body1" sx={{ fontWeight: 600 }}>Không có nhật ký phù hợp</Typography>
                    <Typography variant="body2" color="text.secondary">Thử đổi bộ lọc hoặc làm mới dữ liệu.</Typography>
                  </TableCell>
                </TableRow>
              ) : (
                logs.map((log) => (
                  <TableRow hover key={log.id}>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>{new Date(log.createdAt).toLocaleString('vi-VN')}</TableCell>
                    <TableCell>
                      <Typography variant="body2" sx={{ fontWeight: 600 }}>{log.actorDisplayName || 'Hệ thống'}</Typography>
                      {log.actorEmail && <Typography variant="caption" color="text.secondary">{log.actorEmail}</Typography>}
                    </TableCell>
                    <TableCell>{log.actorRoleCode ? <Chip size="small" label={log.actorRoleCode} /> : '—'}</TableCell>
                    <TableCell><Chip size="small" variant="outlined" color="primary" label={log.action} /></TableCell>
                    <TableCell>
                      <Typography variant="body2">{log.targetType}</Typography>
                      {log.targetId && <Typography variant="caption" color="text.secondary">{log.targetId.substring(0, 8)}…</Typography>}
                    </TableCell>
                    <TableCell align="right">
                      <Button size="small" onClick={() => setSelectedAuditId(log.id)}>Xem</Button>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
        <Stack
          direction={{ xs: 'column', sm: 'row' }}
          spacing={2}
          sx={{
            alignItems: 'center',
            borderTop: '1px solid',
            borderColor: 'divider',
            justifyContent: 'space-between',
            px: 3,
            py: 2,
          }}
        >
          <Typography variant="body2" color="text.secondary">
            {data?.totalElements ?? 0} nhật ký · Trang {page + 1}/{totalPages}
          </Typography>
          <Pagination
            color="primary"
            count={totalPages}
            page={page + 1}
            onChange={(_, value) => setFilterParams((current) => ({ ...current, page: value - 1 }))}
            disabled={isFetching}
            size="small"
          />
        </Stack>
      </Card>

      <Dialog open={selectedAuditId !== null} onClose={() => setSelectedAuditId(null)} maxWidth="md" fullWidth>
        <DialogTitle>Chi tiết nhật ký</DialogTitle>
        <DialogContent dividers>
          {selectedAuditId && <AdminAuditLogDetailPage id={selectedAuditId} />}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSelectedAuditId(null)}>Đóng</Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
};
