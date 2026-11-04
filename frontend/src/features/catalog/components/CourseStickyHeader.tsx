import { useState, useEffect } from 'react';
import type { PublicCourseDetail } from '../types/courseDetailTypes';
import { hasCourseAccessPeriodEnded } from '../utils/courseAccess';

interface CourseStickyHeaderProps {
  course: PublicCourseDetail;
  onPurchase?: () => void;
  onContinueLearning?: () => void;
}

export const CourseStickyHeader = ({ course, onPurchase, onContinueLearning }: CourseStickyHeaderProps) => {
  const [isVisible, setIsVisible] = useState(false);
  const accessPeriodEnded = hasCourseAccessPeriodEnded(course.accessExpiresAt);

  useEffect(() => {
    const handleScroll = () => {
      // Show header after scrolling past the hero section (approx 400px)
      if (window.scrollY > 400) {
        setIsVisible(true);
      } else {
        setIsVisible(false);
      }
    };

    window.addEventListener('scroll', handleScroll);
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return (
    <div
      className={`fixed top-0 left-0 right-0 z-50 bg-slate-900/95 backdrop-blur-md border-b border-slate-700 shadow-lg transform transition-transform duration-300 ease-in-out ${
        isVisible ? 'translate-y-0' : '-translate-y-full'
      }`}
    >
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-3 flex items-center justify-between">
        <div className="flex flex-col overflow-hidden mr-4">
          <h3 className="text-white font-bold text-sm sm:text-base truncate">{course.title}</h3>
        </div>

        <div className="flex items-center flex-shrink-0 gap-4">
          <div className="hidden sm:block text-right">
            {course.price === 0 ? (
              <div className="text-emerald-400 font-bold text-lg">Miễn phí</div>
            ) : (
              <div className="text-white font-bold text-lg">{course.price.toLocaleString('vi-VN')} {course.currency}</div>
            )}
          </div>
          {accessPeriodEnded ? (
            <button
              type="button"
              disabled
              className="cursor-not-allowed rounded-lg bg-slate-700 px-6 py-2 text-sm font-bold text-slate-300"
            >
              Đã hết hạn
            </button>
          ) : course.isEnrolled ? (
            <button
              onClick={onContinueLearning}
              className="bg-white hover:bg-slate-100 text-slate-900 font-bold py-2 px-6 rounded-lg transition-colors text-sm"
            >
              Tiếp tục học
            </button>
          ) : (
            <button
              onClick={onPurchase}
              className="bg-gradient-to-r from-indigo-500 to-purple-500 hover:from-indigo-400 hover:to-purple-400 text-white font-bold py-2 px-6 rounded-lg transition-all shadow-md shadow-indigo-500/20 text-sm"
            >
              {course.hasExpiredEnrollment
                ? course.price === 0 ? 'Gia hạn miễn phí' : 'Gia hạn khóa học'
                : course.price === 0 ? 'Ghi danh ngay' : 'Mua ngay'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
