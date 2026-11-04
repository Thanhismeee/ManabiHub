import { useState, type MouseEvent } from 'react';
import FavoriteIcon from '@mui/icons-material/Favorite';
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';
import {
  Alert,
  Button,
  IconButton,
  Snackbar,
  Tooltip,
} from '@mui/material';
import { useLocation, useNavigate } from 'react-router-dom';
import { rememberPostLoginRoute } from '../../../shared/auth/authSession';
import { useWishlist } from '../hooks/useWishlist';

interface WishlistToggleButtonProps {
  courseId: string;
  variant: 'icon' | 'button';
}

export function WishlistToggleButton({
  courseId,
  variant,
}: WishlistToggleButtonProps) {
  const navigate = useNavigate();
  const location = useLocation();
  const {
    isStudent,
    courseIds,
    addCourse,
    removeCourse,
    isUpdating,
  } = useWishlist();
  const [errorOpen, setErrorOpen] = useState(false);
  const isSaved = courseIds.has(courseId);

  const handleToggle = async (event: MouseEvent<HTMLElement>) => {
    event.preventDefault();
    event.stopPropagation();

    if (!isStudent) {
      rememberPostLoginRoute('public', `${location.pathname}${location.search}`);
      navigate('/login');
      return;
    }

    try {
      if (isSaved) {
        await removeCourse(courseId);
      } else {
        await addCourse(courseId);
      }
    } catch {
      setErrorOpen(true);
    }
  };

  const label = isSaved ? 'Bỏ lưu' : 'Lưu khóa học';

  return (
    <>
      {variant === 'icon' ? (
        <Tooltip title={label}>
          <IconButton
            aria-label={label}
            disabled={isUpdating}
            onClick={handleToggle}
            sx={{
              position: 'absolute',
              zIndex: 2,
              top: 12,
              right: 12,
              width: 40,
              height: 40,
              bgcolor: 'rgba(255, 255, 255, 0.9)',
              backdropFilter: 'blur(4px)',
              boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
              transition: 'transform 0.2s',
              '&:hover': { bgcolor: 'white', transform: 'scale(1.1)' },
            }}
          >
            {isSaved ? <FavoriteIcon sx={{ color: '#DC2626' }} /> : <FavoriteBorderIcon sx={{ color: 'text.secondary' }} />}
          </IconButton>
        </Tooltip>
      ) : (
        <Button
          fullWidth
          variant="outlined"
          color={isSaved ? 'error' : 'primary'}
          startIcon={isSaved ? <FavoriteIcon /> : <FavoriteBorderIcon />}
          disabled={isUpdating}
          onClick={handleToggle}
          sx={{ mb: 3 }}
        >
          {label}
        </Button>
      )}
      <Snackbar
        open={errorOpen}
        autoHideDuration={4000}
        onClose={() => setErrorOpen(false)}
      >
        <Alert severity="error" onClose={() => setErrorOpen(false)}>
          Không thể cập nhật danh sách yêu thích. Vui lòng thử lại.
        </Alert>
      </Snackbar>
    </>
  );
}
