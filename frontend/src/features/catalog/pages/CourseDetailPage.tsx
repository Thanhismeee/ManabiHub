import { useNavigate, useParams } from 'react-router-dom';
import { useRef } from 'react';
import { useCourseDetail } from '../hooks/useCourseDetail';
import { CourseHero } from '../components/CourseHero';
import { CourseStickyCard, type CourseStickyCardHandle } from '../components/CourseStickyCard';
import { CurriculumAccordion } from '../components/CurriculumAccordion';
import { TeacherProfile } from '../components/TeacherProfile';
import { CourseStickyHeader } from '../components/CourseStickyHeader';
import { Helmet } from 'react-helmet-async';
import { RefreshCw, Target, CheckCircle2 } from 'lucide-react';
import { CourseReviewsSection } from '../../course-reviews/components/CourseReviewsSection';
import { RichTextContent } from '../../../shared/components/RichTextContent/RichTextContent';
import { resolvePublicAssetUrl } from '../../../shared/utils/assetUtils';
import { RelatedCoursesSection } from '../components/RelatedCoursesSection';

export const CourseDetailPage = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const stickyCardRef = useRef<CourseStickyCardHandle>(null);
  const {
    data: course,
    isLoading,
    isError,
    isFetching,
    refetch,
  } = useCourseDetail(id || '');

  if (isLoading) {
    return (
      <div className="bg-white min-h-screen">
        <div className="bg-slate-900 pt-20 pb-24">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
            <div className="w-24 h-6 bg-slate-800 rounded-full mb-6 animate-pulse" />
            <div className="w-3/4 h-12 bg-slate-800 rounded-lg mb-6 animate-pulse" />
            <div className="w-1/2 h-6 bg-slate-800 rounded-lg mb-8 animate-pulse" />
            <div className="w-full h-14 max-w-2xl bg-slate-800 rounded-2xl animate-pulse" />
          </div>
        </div>
        <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-12">
            <div className="lg:col-span-2 space-y-12">
              <div className="w-full h-48 bg-slate-100 rounded-2xl animate-pulse" />
              <div className="w-full h-64 bg-slate-100 rounded-2xl animate-pulse" />
              <div className="w-full h-32 bg-slate-100 rounded-2xl animate-pulse" />
            </div>
            <div className="lg:col-span-1">
              <div className="w-full h-96 bg-slate-100 rounded-2xl animate-pulse" />
            </div>
          </div>
        </div>
      </div>
    );
  }

  if (isError || !course) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center bg-slate-50 text-slate-800">
        <Helmet>
          <title>Không tìm thấy khóa học | ManabiHub</title>
        </Helmet>
        <h2 className="text-2xl font-bold mb-2">Không tìm thấy khóa học</h2>
        <p className="text-slate-500">Khóa học không tồn tại hoặc chưa được xuất bản.</p>
      </div>
    );
  }

  const hasOutcomes = Boolean(course.outcomes?.trim());
  const hasPrerequisites = Boolean(course.prerequisites?.trim());
  const thumbnailUrl = resolvePublicAssetUrl(course.thumbnailUrl);

  // Plain text fallback for meta description
  const metaDescription = course.introduction
    ? course.introduction.replace(/<[^>]+>/g, '').substring(0, 160)
    : course.description
      ? course.description.replace(/<[^>]+>/g, '').substring(0, 160)
      : 'Khóa học tiếng Nhật chất lượng cao trên ManabiHub';

  return (
    <div className="bg-white min-h-screen relative">
      <Helmet>
        <title>{course.title} | ManabiHub</title>
        <meta name="description" content={metaDescription} />
        <meta property="og:title" content={course.title} />
        <meta property="og:description" content={metaDescription} />
        <meta property="og:type" content="website" />
        {thumbnailUrl && <meta property="og:image" content={thumbnailUrl} />}
        <meta name="twitter:card" content="summary_large_image" />
        <meta name="twitter:title" content={course.title} />
        <meta name="twitter:description" content={metaDescription} />
        {thumbnailUrl && <meta name="twitter:image" content={thumbnailUrl} />}
      </Helmet>

      <CourseStickyHeader
        course={course}
        onPurchase={() => stickyCardRef.current?.openPurchaseOptions()}
        onContinueLearning={() => navigate(`/student/courses/${course.id}/learn`)}
      />
      <CourseHero course={course} />

      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
        <div className="flex justify-end mb-4">
          <button
            type="button"
            onClick={() => void refetch()}
            disabled={isFetching}
            className="inline-flex items-center gap-2 rounded-xl border border-slate-200 bg-white px-4 py-2 text-sm font-semibold text-slate-700 shadow-sm transition hover:border-red-200 hover:text-red-700 disabled:cursor-not-allowed disabled:opacity-60"
          >
            <RefreshCw className={`h-4 w-4 ${isFetching ? 'animate-spin' : ''}`} />
            {isFetching ? 'Đang tải...' : 'Tải lại khóa học'}
          </button>
        </div>
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-12 relative">

          {/* Left Column: Main Content */}
          <div className="lg:col-span-2">

            {/* What you will learn */}
            {hasOutcomes && (
              <div className="bg-rose-50/50 border border-rose-100 p-6 rounded-2xl mb-12">
                <h2 className="text-lg font-bold text-slate-900 mb-4 flex items-center">
                  <Target className="w-5 h-5 text-red-600 mr-2" />
                  Bạn sẽ học được gì trong khóa học này?
                </h2>
                <div className="flex items-start text-sm text-slate-700 leading-relaxed font-medium gap-2">
                  <CheckCircle2 className="w-4 h-4 text-emerald-600 flex-shrink-0 stroke-[2.5] mt-0.5" />
                  <RichTextContent value={course.outcomes} className="min-w-0 flex-1" />
                </div>
              </div>
            )}

            {/* Curriculum */}
            <div className="mb-12">
              <CurriculumAccordion
                modules={course.modules}
              />
            </div>

            {/* Prerequisites */}
            {hasPrerequisites && (
              <div className="mb-12">
                <h2 className="text-2xl font-bold text-slate-900 mb-4">Yêu cầu đầu vào</h2>
                <RichTextContent value={course.prerequisites} className="text-slate-700 text-sm" />
              </div>
            )}

            {/* Target Students */}
            {course.targetStudents && (
              <div className="mb-12">
                <h2 className="text-2xl font-bold text-slate-900 mb-4">Đối tượng phù hợp</h2>
                <RichTextContent value={course.targetStudents} className="text-sm text-slate-700" />
              </div>
            )}

            {/* Teacher Profile */}
            <TeacherProfile teacher={course.teacher} />

            <RelatedCoursesSection course={course} />

            <CourseReviewsSection
              courseId={course.id}
              courseIdentifier={course.slug || course.id}
              isEnrolled={course.isEnrolled}
              canTeacherReply={Boolean(course.isTeacherOwner)}
              averageRating={course.averageRating}
              reviewCount={course.reviewCount}
              onReviewChanged={refetch}
            />
          </div>

          {/* Right Column: Sticky Card */}
          <div className="lg:col-span-1">
            <div className="sticky top-24">
              <CourseStickyCard ref={stickyCardRef} course={course} />
            </div>
          </div>

        </div>
      </div>
    </div>
  );
};
