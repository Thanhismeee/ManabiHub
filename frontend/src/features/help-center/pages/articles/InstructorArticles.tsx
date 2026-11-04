import { PolicyBoundary } from '../../components/PolicyBoundary';
import { ArticleLayout } from '../../components/ArticleLayout';
import { getHelpArticle } from '../../content/articleRegistry';

const verificationArticle = getHelpArticle('instructor-verification');
const courseReviewArticle = getHelpArticle('instructor-course-review');

export const InstructorVerificationPage = () => (
  <ArticleLayout article={verificationArticle}>
    <PolicyBoundary>
      {(policy) => (
        <>
        <h2>Quy trình xác thực</h2>
        <ol>
          <li>
            CCCD được đọc và xác thực tự động qua nhà cung cấp eKYC. Hệ thống đối
            chiếu họ tên, ngày sinh và kiểm tra danh tính đã được sử dụng trước đó.
          </li>
          <li>
            Chứng chỉ JLPT được OCR để đối chiếu thông tin với CCCD và kiểm tra
            chứng chỉ trùng lặp.
          </li>
          <li>
            Course Manager xác minh tính xác thực của chứng chỉ JLPT bằng nguồn
            nghiệp vụ phù hợp trước khi hoàn tất hồ sơ giảng viên.
          </li>
        </ol>

        <h2>Thời gian xử lý</h2>
        <p>
          Mục tiêu xử lý thủ công là từ
          {' '}
          <strong>{policy.kycTargetDaysMin} đến {policy.kycTargetDaysMax} ngày làm việc</strong>,
          không tính cuối tuần và ngày nghỉ đã cấu hình. Đây là mục tiêu dịch vụ,
          không phải bảo đảm tuyệt đối.
        </p>
        <p>
          Nếu cần bổ sung hoặc sửa tài liệu, hệ thống phải thông báo rõ nội dung cần
          sửa và cách thời hạn xét duyệt được tính lại.
        </p>

        <h2>Quyền trong khi chờ duyệt</h2>
        <p>
          Người dùng có thể sử dụng chức năng biên soạn dành cho giảng viên, nhưng
          khóa học không được hiển thị công khai cho đến khi hồ sơ JLPT được duyệt.
        </p>
        </>
      )}
    </PolicyBoundary>
  </ArticleLayout>
);

export const InstructorCourseReviewPage = () => (
  <ArticleLayout article={courseReviewArticle}>
    <h2>Trước khi xuất bản</h2>
    <p>
      Hệ thống kiểm tra cấu trúc, nội dung bắt buộc, bài kiểm tra cuối khóa và điều
      kiện của giảng viên. Trường hợp ngoại lệ hoặc tín hiệu rủi ro được chuyển cho
      Course Manager thay vì tự động xuất bản.
    </p>

    <h2>Khi khóa học bị gỡ xuất bản</h2>
    <p>
      Khóa học không còn nhận học viên mới. Học viên đã ghi danh vẫn giữ quyền truy
      cập và tiếp tục học phiên bản đã được duyệt gần nhất. Nội dung giảng viên đang
      chỉnh sửa được lưu thành một phiên bản riêng, không ảnh hưởng tới học viên cho
      đến khi Course Manager phê duyệt. Sau khi duyệt, phiên bản mới được cập nhật cho
      học viên; giảng viên có thể xuất bản lại để nhận học viên mới.
    </p>

    <h2>Thông báo và khiếu nại</h2>
    <p>
      Giảng viên phải nhận được lý do, ảnh hưởng, hành động khắc phục và kênh liên
      hệ. Việc gỡ xuất bản không được làm biến mất lịch sử doanh thu hoặc nghĩa vụ
      hoàn tiền.
    </p>
  </ArticleLayout>
);
