import type {
  HelpArticleMetadata,
  HelpCategory,
  HelpCategoryDefinition,
} from '../types';

export const HELP_CATEGORIES: HelpCategoryDefinition[] = [
  {
    id: 'instructors',
    label: 'Dành cho giảng viên',
    description: 'Xác thực, xuất bản khóa học, doanh thu và thanh toán.',
  },
  {
    id: 'learners',
    label: 'Dành cho học viên',
    description: 'Thanh toán, hoàn tiền và quyền truy cập khóa học.',
  },
  {
    id: 'trust-safety',
    label: 'Niềm tin và an toàn',
    description: 'Báo cáo vi phạm, biện pháp xử lý và quyền khiếu nại.',
  },
  {
    id: 'ai-and-data',
    label: 'AI và dữ liệu',
    description: 'Cách AI được sử dụng và những giới hạn cần lưu ý.',
  },
  {
    id: 'legal',
    label: 'Pháp lý',
    description: 'Điều khoản và chính sách vận hành minh bạch của bản MVP.',
  },
];

export const HELP_ARTICLES: HelpArticleMetadata[] = [
  {
    id: 'instructor-verification',
    path: '/help/instructors/verification',
    title: 'Xác thực danh tính và chứng chỉ JLPT',
    summary: 'Các bước xác thực CCCD tự động và kiểm tra chứng chỉ JLPT thủ công.',
    category: 'instructors',
    audience: 'instructor',
    keywords: ['KYC', 'CCCD', 'VNPT', 'JLPT', 'xác thực', 'chứng chỉ'],
    relatedPaths: [
      '/help/instructors/course-review-and-unpublishing',
      '/legal/instructor-terms',
    ],
    lastReviewedAt: '2026-07-28',
    policyVersion: 'provisional-2026-07-28',
    status: 'provisional',
    discoverable: true,
  },
  {
    id: 'instructor-revenue-share',
    path: '/help/instructors/revenue-share',
    title: 'Chia sẻ doanh thu và hoa hồng',
    summary: 'Công thức tính doanh thu gộp, hoa hồng nền tảng và thu nhập giảng viên.',
    category: 'instructors',
    audience: 'instructor',
    keywords: ['doanh thu', 'hoa hồng', 'commission', 'thu nhập', 'giảng viên'],
    relatedPaths: [
      '/help/instructors/escrow-and-payouts',
      '/legal/instructor-terms',
    ],
    lastReviewedAt: '2026-07-28',
    policyVersion: 'provisional-2026-07-28',
    status: 'provisional',
    discoverable: true,
  },
  {
    id: 'instructor-escrow-payouts',
    path: '/help/instructors/escrow-and-payouts',
    title: 'Escrow và rút tiền',
    summary: 'Thời gian tạm giữ, ngày giải ngân dự kiến và điều kiện rút tiền.',
    category: 'instructors',
    audience: 'instructor',
    keywords: ['escrow', 'ví', 'rút tiền', 'payout', 'số dư', 'giải ngân'],
    relatedPaths: [
      '/help/instructors/revenue-share',
      '/help/learners/payments-refunds-access',
    ],
    lastReviewedAt: '2026-07-28',
    policyVersion: 'provisional-2026-07-28',
    status: 'provisional',
    discoverable: true,
  },
  {
    id: 'instructor-course-review',
    path: '/help/instructors/course-review-and-unpublishing',
    title: 'Xét duyệt và gỡ xuất bản khóa học',
    summary: 'Điều kiện xuất bản, trường hợp bị gỡ và ảnh hưởng đến học viên hiện tại.',
    category: 'instructors',
    audience: 'instructor',
    keywords: ['xét duyệt', 'xuất bản', 'unpublish', 'khóa học', 'khiếu nại'],
    relatedPaths: [
      '/help/instructors/verification',
      '/help/trust-safety/reporting-and-actions',
    ],
    lastReviewedAt: '2026-07-28',
    policyVersion: 'provisional-2026-07-28',
    status: 'provisional',
    discoverable: true,
  },
  {
    id: 'learner-payments-refunds',
    path: '/help/learners/payments-refunds-access',
    title: 'Thanh toán, hoàn tiền và quyền truy cập',
    summary: 'Điều kiện hoàn tiền và cách hoàn tiền ảnh hưởng đến quyền học.',
    category: 'learners',
    audience: 'learner',
    keywords: ['thanh toán', 'hoàn tiền', 'refund', 'quyền truy cập', 'tiến độ'],
    relatedPaths: ['/legal/refund-policy'],
    lastReviewedAt: '2026-07-28',
    policyVersion: 'provisional-2026-07-28',
    status: 'provisional',
    discoverable: true,
  },
  {
    id: 'trust-safety',
    path: '/help/trust-safety/reporting-and-actions',
    title: 'Báo cáo vi phạm và biện pháp xử lý',
    summary: 'Kênh báo cáo, phạm vi xử lý và ảnh hưởng đến khóa học đã mua.',
    category: 'trust-safety',
    audience: 'all',
    keywords: ['báo cáo', 'vi phạm', 'trust', 'safety', 'gỡ khóa học', 'khiếu nại'],
    relatedPaths: ['/help/instructors/course-review-and-unpublishing'],
    lastReviewedAt: '2026-07-28',
    policyVersion: 'provisional-2026-07-28',
    status: 'provisional',
    discoverable: true,
  },
  {
    id: 'ai-and-data',
    path: '/help/ai-and-data',
    title: 'Trí tuệ nhân tạo và dữ liệu',
    summary: 'Dữ liệu nào được gửi cho AI, khi nào AI chạy và giới hạn của kết quả.',
    category: 'ai-and-data',
    audience: 'all',
    keywords: ['AI', 'Gemini', 'dữ liệu', 'quyền riêng tư', 'gợi ý', 'chat'],
    relatedPaths: ['/legal/ai-notice', '/legal/privacy'],
    lastReviewedAt: '2026-07-28',
    policyVersion: 'provisional-2026-07-28',
    status: 'provisional',
    discoverable: true,
  },
  {
    id: 'terms',
    path: '/legal/terms',
    title: 'Điều khoản sử dụng',
    summary: 'Quy tắc tài khoản, nội dung, thanh toán và sử dụng ManabiHub trong bản MVP.',
    category: 'legal',
    audience: 'all',
    keywords: ['điều khoản', 'terms', 'pháp lý'],
    relatedPaths: ['/legal/privacy'],
    lastReviewedAt: '2026-08-01',
    policyVersion: 'draft-mvp-2026-08-01',
    status: 'draft',
    discoverable: true,
  },
  {
    id: 'privacy',
    path: '/legal/privacy',
    title: 'Chính sách bảo mật',
    summary: 'Dữ liệu bản MVP xử lý, mục đích sử dụng và các ranh giới tích hợp bên ngoài.',
    category: 'legal',
    audience: 'all',
    keywords: ['bảo mật', 'privacy', 'dữ liệu cá nhân'],
    relatedPaths: ['/legal/terms', '/legal/ai-notice'],
    lastReviewedAt: '2026-08-01',
    policyVersion: 'draft-mvp-2026-08-01',
    status: 'draft',
    discoverable: true,
  },
  {
    id: 'instructor-terms',
    path: '/legal/instructor-terms',
    title: 'Điều khoản dành cho giảng viên',
    summary: 'Xác thực, xuất bản, tỷ lệ doanh thu, escrow, hoàn tiền và rút tiền của giảng viên.',
    category: 'legal',
    audience: 'instructor',
    keywords: ['giảng viên', 'doanh thu', 'điều khoản', 'instructor'],
    relatedPaths: [
      '/help/instructors/revenue-share',
      '/help/instructors/escrow-and-payouts',
    ],
    lastReviewedAt: '2026-08-01',
    policyVersion: 'draft-mvp-2026-08-01',
    status: 'draft',
    discoverable: true,
  },
  {
    id: 'refund-policy',
    path: '/legal/refund-policy',
    title: 'Chính sách hoàn tiền',
    summary: 'Điều kiện thời gian, ngưỡng tiến độ, số tiền hoàn và trạng thái đối soát.',
    category: 'legal',
    audience: 'learner',
    keywords: ['hoàn tiền', 'refund', 'học viên'],
    relatedPaths: ['/help/learners/payments-refunds-access'],
    lastReviewedAt: '2026-08-01',
    policyVersion: 'draft-mvp-2026-08-01',
    status: 'draft',
    discoverable: true,
  },
  {
    id: 'ai-notice',
    path: '/legal/ai-notice',
    title: 'Thông báo về trí tuệ nhân tạo',
    summary: 'Vai trò gợi ý của AI, dữ liệu được gửi và giới hạn của kết quả.',
    category: 'legal',
    audience: 'all',
    keywords: ['AI', 'Gemini', 'thông báo', 'dữ liệu'],
    relatedPaths: ['/help/ai-and-data', '/legal/privacy'],
    lastReviewedAt: '2026-08-01',
    policyVersion: 'draft-mvp-2026-08-01',
    status: 'draft',
    discoverable: true,
  },
];

export function getHelpArticle(id: string): HelpArticleMetadata {
  const article = HELP_ARTICLES.find((item) => item.id === id);
  if (!article) {
    throw new Error(`Unknown help article: ${id}`);
  }
  return article;
}

export function getHelpCategory(category: HelpCategory): HelpCategoryDefinition {
  const definition = HELP_CATEGORIES.find((item) => item.id === category);
  if (!definition) {
    throw new Error(`Unknown help category: ${category}`);
  }
  return definition;
}

export function filterHelpArticles(query: string, category?: HelpCategory) {
  const normalizedQuery = query.trim().toLocaleLowerCase('vi-VN');

  return HELP_ARTICLES.filter((article) => {
    if (!article.discoverable || (category && article.category !== category)) {
      return false;
    }

    if (!normalizedQuery) {
      return true;
    }

    const searchableText = [
      article.title,
      article.summary,
      ...article.keywords,
    ].join(' ').toLocaleLowerCase('vi-VN');

    return searchableText.includes(normalizedQuery);
  });
}
