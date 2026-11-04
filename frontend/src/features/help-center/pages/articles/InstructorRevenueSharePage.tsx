import { PolicyBoundary } from '../../components/PolicyBoundary';
import { ArticleLayout } from '../../components/ArticleLayout';
import { Box } from '@mui/material';
import { getHelpArticle } from '../../content/articleRegistry';
import {
  formatPolicyCurrency,
  formatPolicyEffectiveDate,
  formatPolicyPercent,
} from '../../utils/policyFormatting';

const article = getHelpArticle('instructor-revenue-share');
const exampleGrossAmount = 1_000_000;

export const InstructorRevenueSharePage = () => {
  return (
    <ArticleLayout article={article}>
      <PolicyBoundary>
        {(policy) => {
          const commissionAmount = Math.round(exampleGrossAmount * policy.commissionRate);
          const instructorNetAmount = exampleGrossAmount - commissionAmount;

          return (
            <>
              <section>
            <h2>Công thức áp dụng</h2>
            <p>
              Doanh thu gộp của một khóa học là số tiền học viên thực tế thanh toán
              được phân bổ cho khóa học đó sau giảm giá. ManabiHub áp dụng tỷ lệ được
              ghi nhận tại thời điểm thanh toán thành công:
            </p>
            <ul>
              <li>
                Hoa hồng nền tảng: <strong>{formatPolicyPercent(policy.commissionRate)}</strong>.
              </li>
              <li>
                Thu nhập giảng viên trước hoàn tiền:
                {' '}
                <strong>{formatPolicyPercent(1 - policy.commissionRate)}</strong>.
              </li>
            </ul>
            <Box sx={{ borderLeft: 4, borderColor: 'primary.main', bgcolor: 'primary.50', p: 2, mt: 2 }}>
              <p><strong>Ví dụ minh họa</strong></p>
              <p>
                Với doanh thu gộp {formatPolicyCurrency(exampleGrossAmount, policy.currency)},
                hoa hồng nền tảng là
                {' '}
                {formatPolicyCurrency(commissionAmount, policy.currency)}
                {' '}
                và phần của giảng viên là
                {' '}
                {formatPolicyCurrency(instructorNetAmount, policy.currency)}.
              </p>
            </Box>
              </section>

              <section>
            <h2>Hoàn tiền và thay đổi chính sách</h2>
            <p>
              Khi một giao dịch được hoàn tiền hợp lệ, cả phần của giảng viên và hoa
              hồng nền tảng của giao dịch đó đều được đảo ngược. Việc thay đổi tỷ lệ
              trong tương lai không được tính lại cho giao dịch đã thanh toán.
            </p>
            <p>
              Phiên bản chính sách {policy.policyVersion} có hiệu lực từ
              {' '}
              {formatPolicyEffectiveDate(policy.effectiveAt)}.
            </p>
              </section>
            </>
          );
        }}
      </PolicyBoundary>
    </ArticleLayout>
  );
};
