import { PolicyBoundary } from '../../components/PolicyBoundary';
import { ArticleLayout } from '../../components/ArticleLayout';
import { getHelpArticle } from '../../content/articleRegistry';
import { Link } from 'react-router-dom';
import { ROUTES } from '../../../../shared/constants/routes';

const learnerPaymentsArticle = getHelpArticle('learner-payments-refunds');
const trustSafetyArticle = getHelpArticle('trust-safety');
const aiAndDataArticle = getHelpArticle('ai-and-data');

export const LearnerPaymentsRefundsPage = () => (
  <ArticleLayout article={learnerPaymentsArticle}>
    <p>
      Học viên có thể mở <Link to={ROUTES.STUDENT.PAYMENTS}>Lịch sử thanh toán</Link>,
      chọn đúng khóa học trong đơn hàng và gửi yêu cầu hoàn tiền. Yêu cầu đủ BR-REF-01 được tự động
      hoàn vào ví; yêu cầu ngoài điều kiện được chuyển thành tranh chấp hoặc chờ Finance xét duyệt.
    </p>
    <PolicyBoundary>
      {(policy) => (
        <>
        <h2>Điều kiện gửi yêu cầu</h2>
        <p>
          Yêu cầu hoàn tiền phải được gửi trong vòng
          {' '}
          <strong>{policy.refundWindowDays} ngày theo lịch</strong>
          {' '}
          kể từ khi thanh toán thành công và tiến độ học phải không vượt quá
          {' '}
          <strong>{policy.refundProgressLimitPercent}%</strong>.
        </p>
        <p>
          Tiến độ đúng
          {' '}
          <strong>{policy.refundProgressLimitPercent}% vẫn đủ điều kiện tiêu chuẩn</strong>, miễn là học viên
          chưa tải toàn bộ tài liệu được bảo vệ. Khi đủ điều kiện, toàn bộ số tiền thực tế đã thanh toán
          được tự động hoàn vào ví.
        </p>
        <p>
          Yêu cầu chỉ hiển thị đã hoàn khi giao dịch ghi có vào ví và các bản ghi tài chính liên quan
          đã hoàn tất nhất quán.
        </p>

        <h2>Trường hợp ngoại lệ</h2>
        <p>
          Nếu bị tính phí trùng, gặp lỗi thanh toán hoặc lỗi nền tảng khiến không thể truy
          cập nội dung đã mua, hãy chọn đúng loại ngoại lệ và mô tả rõ sự việc. Finance sẽ
          xem xét thủ công; hệ thống không tự động chấp thuận.
        </p>

        <h2>Quyền truy cập khóa học</h2>
        <p>
          Khi hoàn tiền thành công, quyền truy cập của giao dịch đó bị thu hồi. Nếu
          khóa học chỉ bị ngừng nhận học viên mới, học viên đã ghi danh vẫn được học
          trừ khi có quyết định Trust & Safety riêng.
        </p>
        </>
      )}
    </PolicyBoundary>
  </ArticleLayout>
);

export const TrustSafetyPage = () => (
  <ArticleLayout article={trustSafetyArticle}>
    <h2>Nội dung cần báo cáo</h2>
    <p>
      Báo cáo nên nêu khóa học hoặc tài khoản liên quan, loại vi phạm, thời điểm và
      bằng chứng có thể kiểm chứng. Không đăng công khai CCCD, chứng chỉ hoặc thông
      tin thanh toán của người khác.
    </p>

    <h2>Biện pháp xử lý</h2>
    <p>
      Tùy mức độ, ManabiHub có thể yêu cầu sửa nội dung, tạm ngừng nhận học viên
      mới, gỡ xuất bản hoặc hạn chế tài khoản. Quyết định phải có lý do và nhật ký
      kiểm toán.
    </p>

    <h2>Học viên hiện tại và khiếu nại</h2>
    <p>
      Việc gỡ xuất bản thông thường không xóa quyền học đã mua. Trường hợp phải thu
      hồi quyền truy cập vì an toàn cần có thông báo và phương án khắc phục. Người
      bị tác động phải được cung cấp kênh liên hệ hoặc khiếu nại.
    </p>
  </ArticleLayout>
);

export const AiAndDataPage = () => (
  <ArticleLayout article={aiAndDataArticle}>
    <h2>Khi nào AI được sử dụng?</h2>
    <p>
      AI chỉ chạy khi người dùng gửi câu hỏi trong bài học hoặc chủ động yêu cầu góp
      ý cho bài viết. AI không tự động chấm điểm cuối khóa hoặc đưa ra quyết định
      KYC, xuất bản hay thanh toán.
    </p>

    <h2>Dữ liệu được gửi</h2>
    <p>
      Yêu cầu có thể gồm nội dung người dùng nhập và phần ngữ cảnh bài học cần thiết
      để tạo phản hồi. Luồng hiện tại không bật Google Search grounding và không gửi
      khóa truy cập hoặc thông tin thanh toán cho mô hình.
    </p>

    <h2>Giới hạn</h2>
    <p>
      Phản hồi AI có thể thiếu chính xác, không thay thế giáo viên và cần được kiểm
      chứng. Khi nhà cung cấp không khả dụng hoặc giới hạn sử dụng đã đạt, hệ thống
      phải báo lỗi rõ ràng thay vì tạo dữ liệu giả.
    </p>
  </ArticleLayout>
);
