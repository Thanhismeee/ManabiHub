import { PolicyBoundary } from '../../components/PolicyBoundary';
import { ArticleLayout } from '../../components/ArticleLayout';
import { getHelpArticle } from '../../content/articleRegistry';
import { formatPolicyCurrency } from '../../utils/policyFormatting';

const article = getHelpArticle('instructor-escrow-payouts');

export const InstructorEscrowPayoutsPage = () => {
  return (
    <ArticleLayout article={article}>
      <PolicyBoundary>
        {(policy) => (
          <>
          <section>
            <h2>Vì sao doanh thu được tạm giữ?</h2>
            <p>
              Phần thu nhập của giảng viên được tạm giữ trong
              {' '}
              <strong>{policy.escrowHoldingDays} ngày theo lịch</strong>
              {' '}
              để bao phủ cửa sổ hoàn tiền và thời gian đối soát.
            </p>
            <p>
              Ví giảng viên phải hiển thị ngày giải ngân dự kiến của từng giao dịch.
              Nếu không có hoàn tiền hoặc chặn đối soát hợp lệ, tiền được chuyển sang
              số dư khả dụng khi đến thời điểm đó.
            </p>
          </section>

          <section>
            <h2>Điều kiện rút tiền</h2>
            <ul>
              <li>
                Số dư khả dụng tối thiểu:
                {' '}
                <strong>{formatPolicyCurrency(policy.payoutThreshold, policy.currency)}</strong>.
              </li>
              <li>
                Phí nền tảng thu từ giảng viên:
                {' '}
                <strong>{formatPolicyCurrency(policy.withdrawalFee, policy.currency)}</strong>.
              </li>
            </ul>
            <p>
              Thời gian ngân hàng ghi có chỉ là ước tính. Mỗi yêu cầu rút tiền phải
              có trạng thái và bằng chứng đối soát riêng.
            </p>
          </section>

          <section>
            <h2>Khi escrow bị chặn</h2>
            <p>
              Giao dịch có thể bị giữ lâu hơn khi đang hoàn tiền, tranh chấp hoặc
              đối soát. Màn hình ví phải nêu lý do và hành động tiếp theo; hệ thống
              không được âm thầm thay đổi số dư.
            </p>
          </section>
          </>
        )}
      </PolicyBoundary>
    </ArticleLayout>
  );
};
