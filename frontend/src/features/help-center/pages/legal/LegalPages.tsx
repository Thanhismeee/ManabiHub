import { Box } from '@mui/material';
import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import { ArticleLayout } from '../../components/ArticleLayout';
import { PolicyBoundary } from '../../components/PolicyBoundary';
import { getHelpArticle } from '../../content/articleRegistry';
import {
  formatPolicyCurrency,
  formatPolicyEffectiveDate,
  formatPolicyPercent,
} from '../../utils/policyFormatting';
import { ROUTES } from '../../../../shared/constants/routes';

const DraftScopeNotice = ({ children }: { children: ReactNode }) => (
  <Box
    role="note"
    sx={{
      borderLeft: 4,
      borderColor: 'warning.main',
      bgcolor: 'warning.50',
      color: 'warning.900',
      p: 2,
      mb: 3,
    }}
  >
    <p><strong>Phạm vi bản MVP.</strong></p>
    <p>{children}</p>
  </Box>
);

export const TermsPage = () => (
  <ArticleLayout article={getHelpArticle('terms')}>
    <DraftScopeNotice>
      Đây là bản điều khoản vận hành phục vụ đồ án ManabiHub. Chủ dự án vẫn phải rà
      soát pháp lý trước khi dùng cho hoạt động thương mại thực tế.
    </DraftScopeNotice>

    <h2>1. Tài khoản và quyền truy cập</h2>
    <p>
      Người dùng chịu trách nhiệm bảo vệ phiên đăng nhập của mình và không được chia sẻ
      quyền truy cập trái phép. Tài khoản học viên/giảng viên sử dụng luồng đăng nhập công
      khai; tài khoản quản trị nội bộ sử dụng cơ chế xác thực và phân quyền riêng.
    </p>

    <h2>2. Khóa học và nội dung</h2>
    <p>
      Giảng viên chịu trách nhiệm về quyền sử dụng và tính chính xác của nội dung đã gửi.
      Khóa học chỉ được công khai sau các bước xác thực, xét duyệt và xuất bản tương ứng.
      ManabiHub có thể tạm ngừng bán hoặc gỡ xuất bản khi có quyết định kiểm duyệt có lưu
      vết; việc đó không tự động xóa quyền học đã mua.
    </p>

    <h2>3. Thanh toán và hoàn tiền</h2>
    <p>
      Đơn hàng chỉ được coi là đã thanh toán khi hệ thống xác nhận kết quả hợp lệ từ cổng
      thanh toán. Điều kiện và giới hạn hiện hành được mô tả tại{' '}
      <Link to={ROUTES.PUBLIC.REFUND_POLICY}>Chính sách hoàn tiền</Link>. Việc đủ điều kiện gửi yêu
      cầu không đồng nghĩa cổng thanh toán đã hoàn tiền thành công.
    </p>

    <h2>4. Hành vi không được phép</h2>
    <ul>
      <li>Truy cập trái phép, phá hoại, dò quét hoặc né tránh kiểm soát bảo mật.</li>
      <li>Đăng nội dung vi phạm pháp luật, quyền riêng tư hoặc quyền sở hữu trí tuệ.</li>
      <li>Chia sẻ dữ liệu định danh, thanh toán hoặc tài liệu bảo vệ của người khác.</li>
      <li>Lạm dụng tính năng AI như nguồn chấm điểm hay quyết định chính thức.</li>
    </ul>

    <h2>5. Liên hệ và thay đổi</h2>
    <p>
      Thay đổi chính sách phải được công bố bằng phiên bản và ngày hiệu lực rõ ràng; giao
      dịch tài chính đã xác nhận giữ nguyên snapshot tại thời điểm mua. Câu hỏi hoặc khiếu
      nại được gửi qua <Link to={ROUTES.PUBLIC.ABOUT}>trang liên hệ của ManabiHub</Link>.
    </p>
  </ArticleLayout>
);

export const PrivacyPage = () => (
  <ArticleLayout article={getHelpArticle('privacy')}>
    <DraftScopeNotice>
      Nội dung dưới đây mô tả dữ liệu và luồng tích hợp đang có trong bản MVP; không tuyên
      bố thời hạn lưu trữ hoặc cam kết pháp lý chưa được hệ thống thực thi.
    </DraftScopeNotice>

    <h2>1. Dữ liệu được xử lý</h2>
    <ul>
      <li>Thông tin tài khoản và hồ sơ nhận từ luồng đăng nhập Google.</li>
      <li>Hồ sơ KYC, ảnh/chứng từ và kết quả kiểm tra định danh hoặc chứng chỉ.</li>
      <li>Đơn hàng, mã tham chiếu thanh toán, enrollment, tiến độ và kết quả học tập.</li>
      <li>Nội dung người dùng chủ động gửi cho tính năng AI và ngữ cảnh bài học cần thiết.</li>
      <li>Nhật ký bảo mật, kiểm toán và quyết định quản trị.</li>
    </ul>

    <h2>2. Mục đích sử dụng</h2>
    <p>
      Dữ liệu được dùng để xác thực người dùng, cung cấp khóa học, xử lý thanh toán và đối
      soát, xác minh giảng viên, vận hành tính năng AI, ngăn lạm dụng và lưu bằng chứng cho
      các quyết định nhạy cảm.
    </p>

    <h2>3. Bên xử lý bên ngoài</h2>
    <p>
      Tùy tính năng người dùng chọn, dữ liệu tối thiểu cần thiết có thể được trao đổi với
      Google OAuth, VNPT eKYC/registry, VNPay, nhà cung cấp AI được cấu hình và hạ tầng AWS.
      ManabiHub không gửi khóa truy cập hay payload thanh toán thô vào lời nhắc AI.
    </p>

    <h2>4. Lưu trữ, bảo vệ và yêu cầu của người dùng</h2>
    <p>
      Dữ liệu được giữ trong phạm vi cần thiết cho vận hành, an toàn, kiểm toán và tính toàn
      vẹn tài chính. Một số lịch sử giao dịch hoặc quyết định không thể bị sửa âm thầm. Người
      dùng có thể yêu cầu kiểm tra hoặc sửa thông tin hồ sơ qua{' '}
      <Link to={ROUTES.PUBLIC.ABOUT}>kênh liên hệ</Link>; yêu cầu sẽ được đánh giá theo phạm vi kỹ thuật và
      nghĩa vụ lưu vết hiện hành.
    </p>
  </ArticleLayout>
);

export const InstructorTermsPage = () => (
  <ArticleLayout article={getHelpArticle('instructor-terms')}>
    <DraftScopeNotice>
      Điều khoản thương mại này lấy tỷ lệ trực tiếp từ cấu hình công khai hiện hành và
      không thay thế hợp đồng hoặc phê duyệt pháp lý trước khi vận hành thương mại.
    </DraftScopeNotice>

    <h2>1. Điều kiện giảng dạy</h2>
    <p>
      Giảng viên phải hoàn tất xác thực theo yêu cầu và chỉ được xuất bản khóa học đã qua
      xét duyệt. Phê duyệt nội dung và thao tác xuất bản là hai bước riêng; vi phạm có thể
      dẫn đến chặn tạo nội dung, bán mới, rút tiền hoặc gỡ xuất bản có lý do và audit.
    </p>

    <PolicyBoundary>
      {(policy) => (
        <>
          <h2>2. Doanh thu và hoa hồng</h2>
          <p>
            Với giao dịch được xác nhận, hoa hồng nền tảng là{' '}
            <strong>{formatPolicyPercent(policy.commissionRate)}</strong> và phần thu nhập
            giảng viên trước hoàn tiền là{' '}
            <strong>{formatPolicyPercent(1 - policy.commissionRate)}</strong> của số tiền thực
            tế được phân bổ cho khóa học sau giảm giá. Tỷ lệ của giao dịch được lưu tại thời
            điểm thanh toán và không bị tính lại khi cấu hình thay đổi.
          </p>

          <h2>3. Escrow, hoàn tiền và rút tiền</h2>
          <ul>
            <li>
              Thu nhập giảng viên được tạm giữ tối thiểu{' '}
              <strong>{policy.escrowHoldingDays} ngày theo lịch</strong>, trừ khi có refund,
              dispute, freeze hoặc quyết định chính sách đang chặn.
            </li>
            <li>
              Ngưỡng yêu cầu rút tiền hiện hành là{' '}
              <strong>{formatPolicyCurrency(policy.payoutThreshold, policy.currency)}</strong>;
              phí rút tiền nền tảng là{' '}
              <strong>{formatPolicyCurrency(policy.withdrawalFee, policy.currency)}</strong>.
            </li>
            <li>
              Hoàn tiền hợp lệ đảo phần teacher-net và hoa hồng đã lưu đúng một lần. Nếu tiền
              đã giải ngân, hệ thống chuyển sang đối soát thay vì tạo số dư âm hoặc giả vờ đã
              hoàn tất.
            </li>
          </ul>
          <p>
            Chính sách {policy.policyVersion}, hiệu lực từ{' '}
            {formatPolicyEffectiveDate(policy.effectiveAt)}.
          </p>
        </>
      )}
    </PolicyBoundary>
  </ArticleLayout>
);

export const RefundPolicyPage = () => (
  <ArticleLayout article={getHelpArticle('refund-policy')}>
    <DraftScopeNotice>
      Học viên gửi yêu cầu cho từng khóa học tại{' '}
      <Link to={ROUTES.STUDENT.PAYMENTS}>Lịch sử thanh toán</Link>. Yêu cầu đáp ứng đầy đủ
      BR-REF-01 được tự động phê duyệt và hoàn vào ví; các trường hợp khác chuyển thành tranh chấp
      hoặc chờ Finance xét duyệt thủ công.
    </DraftScopeNotice>

    <PolicyBoundary>
      {(policy) => (
        <>
          <h2>1. Điều kiện tiêu chuẩn</h2>
          <p>Một yêu cầu tiêu chuẩn chỉ đủ điều kiện khi đồng thời thỏa mãn:</p>
          <ul>
            <li>Giao dịch thuộc học viên đang đăng nhập và thanh toán đã thành công.</li>
            <li>
              Yêu cầu được gửi không muộn hơn{' '}
              <strong>{policy.refundWindowDays} ngày theo lịch</strong> sau thời điểm thanh toán
              thành công; đúng ngày cuối cùng vẫn nằm trong cửa sổ.
            </li>
            <li>
              Tiến độ học phải{' '}
              <strong>không vượt quá {policy.refundProgressLimitPercent}%</strong>; đúng{' '}
              <strong>{policy.refundProgressLimitPercent}% vẫn đủ điều kiện tiêu chuẩn</strong>.
            </li>
            <li>Học viên chưa tải toàn bộ tài liệu học tập được bảo vệ.</li>
            <li>Không có yêu cầu hoàn tiền đang hoạt động cho cùng đơn hàng.</li>
          </ul>

          <h2>2. Số tiền và ngoại lệ</h2>
          <p>
            {policy.refundProgressLimitPercent}% là ngưỡng tiến độ, không phải tỷ lệ tiền hoàn.
            Khi đủ điều kiện theo quy tắc chuẩn, toàn bộ số tiền thực tế đã thanh toán được tự động
            hoàn vào ví học viên, không phải giá niêm yết.
          </p>
          <p>
            Thu trùng, lỗi thanh toán đã xác nhận hoặc lỗi nền tảng khiến học viên không truy
            cập được nội dung có thể đi theo luồng ngoại lệ và cần Finance Manager xem xét với
            lý do có thể kiểm toán.
          </p>

          <h2>3. Trạng thái xử lý</h2>
          <p>
            Yêu cầu đủ điều kiện được tự động phê duyệt và chỉ hiển thị hoàn tất sau khi tiền đã được
            ghi vào ví cùng các cập nhật enrollment, escrow và sổ cái liên quan. Yêu cầu không đủ
            điều kiện tự động được chuyển sang tranh chấp/xét duyệt thủ công. Lỗi hoàn tất kế toán
            phải chuyển sang đối soát, không báo thành công giả.
          </p>
          <p>
            Chính sách {policy.policyVersion}, hiệu lực từ{' '}
            {formatPolicyEffectiveDate(policy.effectiveAt)}.
          </p>
        </>
      )}
    </PolicyBoundary>
  </ArticleLayout>
);

export const AiNoticePage = () => (
  <ArticleLayout article={getHelpArticle('ai-notice')}>
    <DraftScopeNotice>
      Thông báo này phản ánh phạm vi AI đang có trong bản MVP và phải được cập nhật nếu nhà
      cung cấp, loại dữ liệu hoặc chính sách lưu trữ thay đổi.
    </DraftScopeNotice>

    <h2>1. Vai trò của AI</h2>
    <p>
      AI chỉ cung cấp gợi ý học tập, phản hồi sơ bộ cho bài viết và hội thoại trong ngữ cảnh
      bài học. AI không quyết định điểm cuối khóa, hoàn thành khóa học, KYC, xuất bản, hoàn tiền
      hoặc thanh toán.
    </p>

    <h2>2. Dữ liệu được gửi</h2>
    <p>
      Khi người dùng chủ động dùng tính năng AI, yêu cầu có thể gồm nội dung họ nhập và phần
      ngữ cảnh bài học cần thiết. Không nhập CCCD, thông tin ngân hàng, khóa truy cập hoặc dữ
      liệu thanh toán vào lời nhắc.
    </p>

    <h2>3. Độ chính xác và khả dụng</h2>
    <p>
      Kết quả có thể thiếu chính xác và cần được người học hoặc giảng viên kiểm chứng. Khi nhà
      cung cấp không khả dụng hoặc hạn mức đã hết, ManabiHub phải báo lỗi rõ ràng thay vì tạo
      dữ liệu giả. Xem thêm <Link to={`${ROUTES.PUBLIC.HELP}/ai-and-data`}>hướng dẫn AI và dữ liệu</Link>.
    </p>
  </ArticleLayout>
);
