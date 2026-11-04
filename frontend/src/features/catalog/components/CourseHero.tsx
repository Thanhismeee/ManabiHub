import type { PublicCourseDetail } from '../types/courseDetailTypes';
import LanguageIcon from '@mui/icons-material/Language';
import NewReleasesIcon from '@mui/icons-material/NewReleases';
import StarRoundedIcon from '@mui/icons-material/StarRounded';

import { RichTextContent } from '../../../shared/components/RichTextContent/RichTextContent';
import { useState, useEffect } from 'react';
import { getAuthSession, type AuthSession } from '../../../shared/auth/authSession';
import { ROLES } from '../../../shared/constants/roles';
import ReportProblemIcon from '@mui/icons-material/ReportProblem';
import { ReportViolationModal } from '../../violation/components/ReportViolationModal';
import { Link } from 'react-router-dom';
import { ROUTES } from '../../../shared/constants/routes';
import { languageLabel, proficiencyLabel } from '../../../shared/learning/languageProfiles';

interface CourseHeroProps {
  course: PublicCourseDetail;
}

export const CourseHero = ({ course }: CourseHeroProps) => {
  const [session, setSession] = useState<AuthSession | null>(null);
  const [reportModalOpen, setReportModalOpen] = useState(false);

  useEffect(() => {
    setSession(getAuthSession('public'));
  }, []);

  const canReport = session && (session.roles.includes(ROLES.STUDENT) || session.roles.includes(ROLES.TEACHER));
  const courseLanguage = course.learningLanguage ?? 'JAPANESE';
  const levelLabel = proficiencyLabel(
    course.proficiencyFramework ?? (course.jlptLevel ? 'JLPT' : undefined),
    course.proficiencyLevel ?? course.jlptLevel,
  );
  const watermark = courseLanguage === 'KOREAN' ? '한' : courseLanguage === 'CHINESE_SIMPLIFIED' ? '汉' : '文章';

  return (
    <div className="relative bg-slate-900 text-white pt-12 pb-12 sm:pt-16 sm:pb-16 overflow-hidden rounded-t-3xl rounded-b-[2.5rem] shadow-xl mt-4">
      {/* Background Gradient Meshes */}
      <div className="absolute inset-0 bg-gradient-to-br from-[#4C0519] via-[#881337] to-[#BE123C] opacity-90" />
      <div className="absolute top-0 right-0 -mr-20 -mt-20 w-96 h-96 rounded-full bg-red-600/20 blur-3xl" />
      <div className="absolute bottom-0 left-0 -ml-20 -mb-20 w-80 h-80 rounded-full bg-rose-600/20 blur-3xl" />
      
      {/* Kanji Watermark */}
      <div className="absolute -top-10 right-10 text-[15rem] font-black text-white/5 select-none pointer-events-none" style={{ writingMode: 'vertical-rl' }}>
        {watermark}
      </div>

      <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 grid grid-cols-1 lg:grid-cols-3 gap-8">
        <div className="lg:col-span-2 flex flex-col items-start">
          {/* Breadcrumb */}
          <div className="flex items-center text-xs font-medium text-white/50 mb-3 tracking-wide">
            <Link
              to={ROUTES.PUBLIC.COURSE_BROWSE}
              className="hover:text-white/80 transition-colors"
            >
              Khám phá khóa học
            </Link>
            <span className="mx-2">/</span>
            <span className="hover:text-white/80 transition-colors">{levelLabel}</span>
            <span className="mx-2">/</span>
            <span className="text-white/80 truncate max-w-[200px] sm:max-w-xs">{course.title.replace('--', '|')}</span>
          </div>

          {/* Badges */}
          <div className="flex flex-wrap items-center gap-3 mb-4">
            {course.category && (
              <span className="px-3 py-1 bg-white/10 border border-white/20 rounded-full text-red-100 text-xs font-semibold tracking-wide uppercase">
                {course.category}
              </span>
            )}
            {(course.proficiencyLevel || course.jlptLevel) && (
              <span className="px-3 py-1 bg-red-500/20 border border-red-400/30 rounded-full text-white text-xs font-semibold tracking-wide uppercase">
                {levelLabel}
              </span>
            )}
          </div>

          {/* Title & Subtitle */}
          <h1 className="text-3xl sm:text-4xl lg:text-5xl font-bold tracking-tight mb-2 leading-tight drop-shadow-md">
            {course.title.replace('--', '|')}
          </h1>
          {/* Description */}
          <RichTextContent
            value={course.description || course.introduction}
            className="text-base sm:text-lg text-slate-300 my-6 max-w-3xl leading-relaxed"
          />

          {/* Meta Info */}
          <div className="flex flex-wrap items-center gap-x-6 gap-y-3 text-sm text-slate-400">
            {(course.reviewCount ?? 0) > 0 && course.averageRating != null && (
              <span className="flex items-center text-amber-300 font-semibold">
                <StarRoundedIcon fontSize="small" className="mr-1" />
                {course.averageRating.toFixed(1)}
                <span className="ml-1 text-slate-300 font-normal">
                  ({course.reviewCount} đánh giá đã xác minh)
                </span>
              </span>
            )}
            <span className="flex items-center">
              Tạo bởi&nbsp;<span className="text-white font-medium">{course.teacher.name}</span>
            </span>
            {course.publishedAt && (
              <span className="flex items-center">
                <NewReleasesIcon fontSize="small" className="mr-1.5 opacity-70" />
                Xuất bản {new Date(course.publishedAt).toLocaleDateString('vi-VN')}
              </span>
            )}
            <span className="flex items-center">
              <LanguageIcon fontSize="small" className="mr-1.5 opacity-70" />
              {languageLabel(courseLanguage)}
            </span>
          </div>

          {canReport && (
            <button
              onClick={() => setReportModalOpen(true)}
              className="flex items-center gap-1 mt-6 px-4 py-2 bg-red-500/10 text-red-300 hover:bg-red-500/20 hover:text-red-200 rounded-lg text-sm font-medium transition-colors border border-red-500/20 shadow-sm"
              title="Báo cáo nội dung vi phạm"
            >
              <ReportProblemIcon fontSize="small" /> Báo cáo vi phạm
            </button>
          )}

          {reportModalOpen && (
            <ReportViolationModal
              open={reportModalOpen}
              onClose={() => setReportModalOpen(false)}
              targetType="COURSE"
              targetId={course.id}
            />
          )}
        </div>
      </div>
    </div>
  );
};
