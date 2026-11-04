import React, { useEffect, useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button, Box, Typography, Chip, TextField, MenuItem, Select,
  FormControl, Pagination, Paper, Table, TableBody,
  TableCell, TableContainer, TableHead, TableRow
} from '@mui/material';
import { courseApprovalService } from '../services/courseApprovalService';
import type { CourseApproval } from '../types';
import ErrorIcon from '@mui/icons-material/Error';
import FilterListIcon from '@mui/icons-material/FilterList';
import HistoryIcon from '@mui/icons-material/History';
import MenuBookIcon from '@mui/icons-material/MenuBook';
import SearchIcon from '@mui/icons-material/Search';
import {
  formatSubmittedTime,
  getCourseApprovalStatusLabel,
} from '../courseApprovalLocalization';

export const CourseApprovalQueuePage: React.FC = () => {
  const [queue, setQueue] = useState<CourseApproval[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();

  // Search, Filter, Pagination states
  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState('ALL');
  const [tempSearchTerm, setTempSearchTerm] = useState('');
  const [tempStatusFilter, setTempStatusFilter] = useState('ALL');
  const [page, setPage] = useState(1); // MUI Pagination is 1-indexed
  const rowsPerPage = 10;

  useEffect(() => {
    courseApprovalService.getQueue()
      .then((data) => {
        setQueue(data);
        setLoading(false);
      })
      .catch((err: any) => {
        if (err.response?.data?.messageCode === 'ADMIN_PERMISSION_DENIED' || err.response?.data?.messageCode === 'COURSE_MANAGER_REQUIRED') {
          setError('ACCESS_DENIED');
        } else if (err.response?.status === 401) {
          setError('UNAUTHORIZED');
        } else {
          setError('ERROR');
        }
        setLoading(false);
      });
  }, []);

  const filteredAndSearchedQueue = useMemo(() => {
    return queue.filter((course) => {
      const matchesSearch = course.courseName.toLowerCase().includes(searchTerm.toLowerCase());
      const matchesStatus = statusFilter === 'ALL' || course.status === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [queue, searchTerm, statusFilter]);

  const paginatedQueue = useMemo(() => {
    const startIndex = (page - 1) * rowsPerPage;
    return filteredAndSearchedQueue.slice(startIndex, startIndex + rowsPerPage);
  }, [filteredAndSearchedQueue, page]);

  const handlePageChange = (_event: React.ChangeEvent<unknown>, value: number) => {
    setPage(value);
  };

  if (error === 'ACCESS_DENIED') {
    return (
      <Box sx={{ p: 4, textAlign: 'center', bgcolor: '#fef2f2', borderRadius: 2, color: '#991b1b', border: '1px solid #fecaca', mt: 4 }}>
        <ErrorIcon sx={{ fontSize: 48, mb: 2, color: '#dc2626' }} />
        <Typography variant="h5" gutterBottom sx={{ fontWeight: 'bold' }}>Không có quyền truy cập</Typography>
        <Typography>Quản lý tài chính không được phép truy cập chức năng duyệt khóa học.</Typography>
      </Box>
    );
  }

  if (error === 'UNAUTHORIZED') {
    return (
      <Box sx={{ p: 4, textAlign: 'center', bgcolor: '#fffbeb', borderRadius: 2, color: '#b45309', border: '1px solid #fde68a', mt: 4 }}>
        <Typography variant="h5" gutterBottom sx={{ fontWeight: 'bold' }}>Phiên đăng nhập không hợp lệ</Typography>
        <Typography>Vui lòng đăng nhập lại bằng tài khoản Quản lý khóa học.</Typography>
      </Box>
    );
  }

  if (error === 'ERROR') {
    return (
      <Box sx={{ p: 4, textAlign: 'center', bgcolor: '#fef2f2', borderRadius: 2, color: '#991b1b', border: '1px solid #fecaca', mt: 4 }}>
        <Typography>Đã xảy ra lỗi khi tải danh sách chờ duyệt. Vui lòng kiểm tra lại kết nối mạng hoặc liên hệ quản trị viên.</Typography>
      </Box>
    );
  }

  return (
    <Box sx={{ p: { xs: 2, md: 4 }, bgcolor: '#f8fafc', minHeight: '100vh' }}>
      <Typography variant="h5" sx={{ fontWeight: 'bold', mb: 4 }}>
        Danh sách khóa học chờ duyệt
      </Typography>

      <Paper elevation={0} sx={{ p: 2, mb: 4, borderRadius: 3, border: '1px solid #e2e8f0', display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'center', justifyContent: 'space-between', bgcolor: 'white', boxShadow: '0 1px 3px 0 rgb(0 0 0 / 0.1)' }}>
        <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'center', flexGrow: 1 }}>
          <TextField
            placeholder="Tìm kiếm theo tên khóa học..."
            variant="outlined"
            size="small"
            value={tempSearchTerm}
            onChange={(e) => setTempSearchTerm(e.target.value)}
            sx={{ width: { xs: '100%', sm: 280 }, '& .MuiOutlinedInput-root': { borderRadius: 2, bgcolor: '#f8fafc' } }}
            slotProps={{
              input: {
                startAdornment: <SearchIcon sx={{ color: 'text.secondary', mr: 1 }} />
              }
            }}
          />
          <FormControl size="small" sx={{ minWidth: 200, '& .MuiOutlinedInput-root': { borderRadius: 2, bgcolor: '#f8fafc' } }}>
            <Select
              value={tempStatusFilter}
              onChange={(e) => setTempStatusFilter(e.target.value)}
              displayEmpty
            >
              <MenuItem value="ALL">Tất cả</MenuItem>
              <MenuItem value="PENDING">Chờ phê duyệt</MenuItem>
              <MenuItem value="APPROVED">Đã phê duyệt</MenuItem>
              <MenuItem value="REJECTED">Đã từ chối</MenuItem>
              <MenuItem value="DRAFT">Cần chỉnh sửa</MenuItem>
              <MenuItem value="PUBLISHED">Đã xuất bản</MenuItem>
            </Select>
          </FormControl>
        </Box>
        <Button
          variant="contained"
          startIcon={<FilterListIcon />}
          onClick={() => {
            setSearchTerm(tempSearchTerm);
            setStatusFilter(tempStatusFilter);
            setPage(1);
          }}
          sx={{
            bgcolor: 'primary.main',
            color: 'primary.contrastText',
            borderRadius: 2,
            textTransform: 'none',
            px: 4,
            py: 1,
            fontWeight: 'bold',
            boxShadow: '0 4px 10px rgba(196, 30, 58, 0.2)',
            '&:hover': {
              bgcolor: 'primary.dark',
              color: 'primary.contrastText',
              boxShadow: '0 6px 14px rgba(196, 30, 58, 0.28)',
            },
            '&:focus, &:active, &:visited': {
              color: 'primary.contrastText',
            },
            '& .MuiButton-startIcon': {
              color: 'inherit',
            },
          }}
        >
          Áp dụng
        </Button>
      </Paper>

      <Paper
        sx={{
          borderRadius: 4,
          overflow: 'hidden',
          border: '1px solid #e2e8f0',
          boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)'
        }}
        elevation={0}
      >
        <Box sx={{ p: 3, display: 'flex', alignItems: 'center', justifyContent: 'space-between', borderBottom: '1px solid #f1f5f9' }}>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <HistoryIcon sx={{ color: 'text.secondary' }} />
            <Typography variant="h6" sx={{ fontWeight: 'bold' }}>Khóa học đã gửi duyệt</Typography>
          </Box>
        </Box>

        <TableContainer>
          <Table sx={{ minWidth: 800 }}>
            <TableHead>
              <TableRow sx={{ bgcolor: '#f8fafc' }}>
                <TableCell sx={{ fontWeight: 'bold', color: 'text.primary', py: 2 }}>Mã yêu cầu</TableCell>
                <TableCell sx={{ fontWeight: 'bold', color: 'text.primary', py: 2 }}>Loại yêu cầu</TableCell>
                <TableCell sx={{ fontWeight: 'bold', color: 'text.primary', py: 2 }}>Người gửi</TableCell>
                <TableCell sx={{ fontWeight: 'bold', color: 'text.primary', py: 2 }}>Thời gian chờ</TableCell>
                <TableCell sx={{ fontWeight: 'bold', color: 'text.primary', py: 2 }}>Trạng thái</TableCell>
                <TableCell sx={{ fontWeight: 'bold', color: 'text.primary', py: 2 }} align="right">Thao tác</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 4 }}>Đang tải dữ liệu...</TableCell>
                </TableRow>
              ) : paginatedQueue.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                    Không có khóa học nào đang chờ duyệt.
                  </TableCell>
                </TableRow>
              ) : (
                paginatedQueue.map((row) => {
                  const taskId = `TSK-${row.id.substring(0, 8).toUpperCase()}`;
                  let statusColor = '#e2e8f0';
                  let statusTextColor = '#475569';

                  if (row.status === 'PENDING') {
                    statusColor = '#f1f5f9';
                    statusTextColor = '#475569';
                  } else if (row.status === 'APPROVED' || row.status === 'PUBLISHED') {
                    statusColor = '#dcfce7';
                    statusTextColor = '#166534';
                  } else if (row.status === 'REJECTED') {
                    statusColor = '#fee2e2';
                    statusTextColor = '#991b1b';
                  } else if (row.status === 'REQUEST_CORRECTION') {
                    statusColor = '#fef3c7';
                    statusTextColor = '#92400e';
                  }

                  return (
                    <TableRow key={row.id} hover sx={{ '&:last-child td, &:last-child th': { border: 0 } }}>
                      <TableCell sx={{ color: 'text.secondary', fontWeight: 500 }}>{taskId}</TableCell>
                      <TableCell>
                        <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 2 }}>
                          <MenuBookIcon sx={{ color: 'text.secondary', mt: 0.5 }} />
                          <Box>
                            <Typography variant="body2" sx={{ fontWeight: 'bold', color: '#475569' }}>
                              Khóa học: {row.courseName}
                            </Typography>
                            <Typography variant="caption" sx={{ color: '#94a3b8' }}>
                              Phê duyệt khóa học
                            </Typography>
                          </Box>
                        </Box>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" sx={{ fontWeight: 'bold', color: '#334155' }}>
                          Giảng viên: {row.teacherName}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2" sx={{ color: '#64748b', fontWeight: 500 }}>
                          {formatSubmittedTime(row.submittedAt)}
                        </Typography>
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={getCourseApprovalStatusLabel(row.status)}
                          sx={{
                            bgcolor: statusColor,
                            color: statusTextColor,
                            fontWeight: 'bold',
                            borderRadius: '16px',
                            height: '24px',
                            fontSize: '0.75rem',
                            textTransform: 'capitalize'
                          }}
                        />
                      </TableCell>
                      <TableCell align="right">
                        <Button
                          variant="contained"
                          sx={{
                            bgcolor: '#0f172a',
                            color: 'white',
                            borderRadius: 8,
                            textTransform: 'none',
                            px: 3,
                            '&:hover': { bgcolor: '#1e293b' }
                          }}
                          onClick={() => navigate(`/admin/courses/approvals/${row.id}`)}
                        >
                          Xem xét
                        </Button>
                      </TableCell>
                    </TableRow>
                  );
                })
              )}
            </TableBody>
          </Table>
        </TableContainer>

        {!loading && queue.length > 0 && (
          <Box sx={{ display: 'flex', justifyContent: 'center', py: 3, borderTop: '1px solid #f1f5f9' }}>
            <Pagination
              count={Math.ceil(filteredAndSearchedQueue.length / rowsPerPage)}
              page={page}
              onChange={handlePageChange}
              shape="rounded"
              sx={{
                '& .MuiPaginationItem-root': {
                  fontWeight: 'bold',
                  color: 'text.secondary'
                },
                '& .Mui-selected': {
                  bgcolor: 'transparent',
                  color: 'text.primary',
                  border: '1px solid #cbd5e1'
                }
              }}
            />
          </Box>
        )}
      </Paper>
    </Box>
  );
};
