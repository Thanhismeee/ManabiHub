# MHB-72 - Frontend dependency security

## Tóm tắt dễ bàn giao

Trước thay đổi này, frontend có 4 finding từ `npm audit`: 3 high và 1 low.
Hai high đến từ SheetJS `xlsx`, một high đến từ React Router và low đến từ
Quill. Sau thay đổi:

- SheetJS bị gỡ hoàn toàn. Import câu hỏi hàng loạt dùng CSV UTF-8 do chính ứng
  dụng parse, không chạy công thức hay thư viện spreadsheet.
- Quill được pin về `2.0.2`, ngoài range `=2.0.3` của advisory, và HTML được
  sanitize ở cả lúc nạp vào editor, lúc lấy ra và lúc render cho học viên.
- React Router lên `7.18.1`, là bản Node 20-compatible mới nhất đang dùng. Audit
  chỉ còn advisory RSC-only được chấp nhận có thời hạn bên dưới.

Bundle JavaScript chính giảm khoảng 446 kB sau khi bỏ SheetJS.

## Luồng CSV thay cho XLSX

Giáo viên tải `Template_Cau_Hoi.csv`, mở/chỉnh bằng Excel hoặc Google Sheets,
sau đó lưu lại CSV UTF-8 và import.

Các giới hạn phòng lỗi/tấn công:

- chỉ nhận đuôi `.csv`;
- tối đa 1 MB, 500 câu hỏi, 7 cột và 2.000 ký tự mỗi ô;
- parser hỗ trợ dấu phẩy, xuống dòng và dấu ngoặc kép theo CSV;
- từ chối CSV malformed, NUL byte, đáp án ngoài 1-4 hoặc thiếu lựa chọn;
- chuẩn hóa Unicode/khoảng trắng để loại câu trùng;
- xem trước rồi mới nối dữ liệu vào đề thi.

Code:

- `frontend/src/features/course-builder/utils/finalTestCsv.ts`
- `frontend/src/features/course-builder/pages/FinalTestQuestionsEditor.tsx`

## Chính sách rich text

`frontend/src/shared/security/sanitizeRichText.ts` là policy duy nhất cho HTML
do giáo viên nhập. Chỉ formatting cần cho mô tả/bài học được giữ lại; script,
iframe, object, form, style và event handler bị loại. Editor sanitize trước khi
nạp HTML cũ, trước khi đưa HTML vào form state và các màn public/student sanitize
ngay trước `dangerouslySetInnerHTML`.

Không được thêm một `dangerouslySetInnerHTML` mới mà bỏ qua hàm này.

## Risk acceptance có thời hạn: React Router RSC

Advisory còn lại:
[`GHSA-qwww-vcr4-c8h2`](https://github.com/advisories/GHSA-qwww-vcr4-c8h2).
Advisory ghi rõ chỉ ảnh hưởng ứng dụng dùng unstable React Server Components.

ManabiHub không dùng RSC, SSR, React Router actions hay server framework mode.
Đây là SPA Vite tĩnh gọi Spring Boot API bằng bearer token. Vì vậy code path bị
lỗi không nằm trong artifact triển khai hiện tại.

- Owner: Phạm Đức Long / Project Leader.
- Phạm vi chấp nhận: chỉ `GHSA-qwww-vcr4-c8h2`; không chấp nhận advisory khác.
- Hết hạn: cuối ngày 2026-08-15 (Asia/Saigon).
- Điều kiện hủy ngay: thêm RSC/SSR/framework mode hoặc server action.
- Cách xử lý dứt điểm: nâng CI/runtime từ Node 20 lên tối thiểu Node 22.22,
  chuyển import khỏi package đã bị bỏ `react-router-dom`, rồi nâng React Router
  lên `8.3.0+`.

`npm run audit:ci` fail nếu xuất hiện advisory khác hoặc acceptance hết hạn.
Hai package entries `react-router` và `react-router-dom` trong output là cùng một
advisory, không phải hai lỗ hổng độc lập. GitHub Actions chạy audit gate, lint,
test và build ở mọi PR.

## Kiểm thử

```text
npm test -- --run
npm run lint
npm run build
npm run audit:ci
npm audit --json
```

Test bắt buộc bao phủ CSV UTF-8/Japanese, quoted values, duplicate handling,
malformed input và XSS sanitizer. Không có migration, secret hay biến môi trường
mới.

## Rollback

Rollback commit sẽ khôi phục SheetJS/Quill/Router cũ và đồng thời mở lại các
finding bảo mật; không rollback riêng lockfile. Nếu CSV gây lỗi nghiệp vụ, tắt
nút import và cho giáo viên nhập tay trong lúc sửa, không tái bật `xlsx@0.18.5`.
