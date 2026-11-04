import { ROUTES } from '../constants/routes';
import SpaceDashboardIcon from '@mui/icons-material/SpaceDashboard';
import LibraryBooksIcon from '@mui/icons-material/LibraryBooks';
import ReceiptLongIcon from '@mui/icons-material/ReceiptLong';
import AccountCircleIcon from "@mui/icons-material/AccountCircle";
import FavoriteBorderIcon from '@mui/icons-material/FavoriteBorder';
import GroupsOutlinedIcon from '@mui/icons-material/GroupsOutlined';

export const STUDENT_MENU = [
  { title: 'Lớp học & bài tập', path: ROUTES.STUDENT.COHORTS, icon: GroupsOutlinedIcon },
  {
    title: 'Khóa học của tôi',
    path: ROUTES.STUDENT.DASHBOARD,
    icon: SpaceDashboardIcon,
  },
  {
    title: 'Khám phá khóa học',
    path: ROUTES.STUDENT.BROWSE_COURSES,
    icon: LibraryBooksIcon,
  },
  {
    title: 'Danh sách yêu thích',
    path: ROUTES.STUDENT.WISHLIST,
    icon: FavoriteBorderIcon,
  },
  {
    title: 'Ví & Thanh toán',
    path: ROUTES.STUDENT.PAYMENTS,
    icon: ReceiptLongIcon,
  },
  {
    title: 'Hồ sơ cá nhân',
    path: ROUTES.STUDENT.PROFILE,
    icon: AccountCircleIcon,
  },
];
