import VerifiedIcon from '@mui/icons-material/Verified';
import { Link } from 'react-router-dom';
import { ROUTES } from '../../../shared/constants/routes';
import type { PublicTeacherProfile } from '../types/courseDetailTypes';

interface TeacherProfileProps {
  teacher: PublicTeacherProfile;
}

export const TeacherProfile = ({ teacher }: TeacherProfileProps) => {
  return (
    <div className="mb-12">
      <h2 className="text-2xl font-extrabold text-slate-900 mb-6">Giảng viên của bạn</h2>
      <div className="bg-slate-50/80 rounded-2xl border border-slate-100 p-6 flex flex-col sm:flex-row items-center sm:items-start gap-6 hover:shadow-md transition-shadow">
        <div className="flex-shrink-0 relative">
          <div className="relative w-16 h-16 rounded-full bg-red-50 text-red-600 font-bold border-2 border-red-100 flex items-center justify-center text-xl shadow-sm overflow-hidden">
            {teacher.avatarUrl ? (
              <img src={teacher.avatarUrl} alt={teacher.name} className="w-full h-full object-cover" />
            ) : (
              <span>{teacher.name.charAt(0).toUpperCase()}</span>
            )}
          </div>
        </div>
        <div className="text-center sm:text-left flex-1">
          <div className="flex flex-wrap items-center justify-center sm:justify-start gap-3 mb-3">
            <Link
              to={ROUTES.PUBLIC.TEACHER_PROFILE(teacher.id)}
              className="text-xl font-bold text-slate-900 hover:text-red-700 transition-colors"
            >
              {teacher.name}
            </Link>
            {teacher.verified && (
              <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded text-xs font-semibold bg-emerald-100 text-emerald-700">
                <VerifiedIcon sx={{ fontSize: 14 }} />
                Đã xác minh
              </span>
            )}
          </div>
          <div className="text-slate-600 text-sm leading-relaxed whitespace-pre-line mb-4">
            {teacher.bio || 'Giảng viên chưa cập nhật thông tin giới thiệu.'}
          </div>
          <Link
            to={ROUTES.PUBLIC.TEACHER_PROFILE(teacher.id)}
            className="inline-flex text-sm font-bold text-red-700 hover:text-red-900"
          >
            Xem hồ sơ và các khóa học
          </Link>
        </div>
      </div>
    </div>
  );
};
