# Hoàn tất sửa lỗi sau rà soát SOFL — 01/10/2026

## 1. Trạng thái hiện tại

Đã triển khai thay đổi trong mã nguồn cho 14 phát hiện A01–A14 của báo cáo rà soát. Backend biên dịch thành công bằng `mvnw.cmd -DskipTests compile`; frontend đóng gói thành công bằng `npm run build`. Bộ FFmpeg đã được tải, kiểm tra checksum và cài tại máy phát triển này.

Chưa chạy kiểm thử tự động hoặc xác nhận đầy đủ nghiệp vụ bằng tài khoản thật. Backend đã triển khai vào môi trường AWS hiện có, bản `manabihub-sofl-20261001-f8375031`, Health Ok và endpoint health UP; trạng thái frontend ghi trong [biên bản AWS](D:/manabihub_ljpm/docs/SOFL_AWS_RELEASE_2026_10_01.md). Kết quả build và health chưa xác nhận toàn bộ tình huống nghiệp vụ bên dưới.

## 2. Các phát hiện đã có thay đổi trong code

| Mã | Thay đổi đã thực hiện | Vị trí chính |
|---|---|---|
| A01 | Mỗi dòng đơn hàng cấp một quyền mua riêng; không còn dùng nguồn lớp cho lần mua mới. Gia hạn giữ tiến độ và chứng nhận đã đạt. | [EnrollmentAccessService](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/learning/service/EnrollmentAccessService.java) |
| A02 | Quyền lớp bị gỡ hoặc hết hạn không tự chặn học viên mua khóa. Kiểm tra quyền mua còn hiệu lực và các trạng thái mua đang bị khóa. | EnrollmentAccessService.purchaseBlocked |
| A03 | Quyền lớp và quyền mua có hạn riêng; bản ghi enrollment tổng hợp các quyền đang có hiệu lực. | EnrollmentAccessService.refresh |
| A04 | Hoàn tiền thay đổi đúng quyền của dòng đơn hàng; gỡ hoặc đóng lớp chỉ thay đổi quyền của lớp. Hủy/từ chối hoàn tiền không khôi phục quyền đã hoàn tiền. | [StudentRefundServiceImpl](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/refund/service/impl/StudentRefundServiceImpl.java), [RefundDecisionTransactionService](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/refund/service/impl/RefundDecisionTransactionService.java) |
| A05 | Registry dùng chung giữa chứng chỉ JLPT cũ và hồ sơ đa ngôn ngữ; khóa duy nhất chống hai tài khoản giữ cùng mã. Từ chối hồ sơ mới giải phóng mã nếu không còn hồ sơ đang chờ/đã duyệt hoặc claim JLPT cũ giữ mã đó. | [TeacherCredentialRegistry](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/kyc/service/TeacherCredentialRegistry.java) |
| A06 | Quyết định từ luồng KYC cũ giữ năng lực đa ngôn ngữ đã được duyệt. Khóa hồ sơ giáo viên khi cập nhật; thu hồi quyền toàn bộ vẫn chặn giảng dạy. Quyền JLPT cũ được đối chiếu hồ sơ đã duyệt. | [KycServiceImpl](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/kyc/service/impl/KycServiceImpl.java), [TeacherQualificationService](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/kyc/service/TeacherQualificationService.java) |
| A07 | Đóng nhận bài không xóa nghĩa vụ đã đến hạn khỏi số bài chưa nộp và số học viên cần hỗ trợ. | [CohortWorkspaceService](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/CohortWorkspaceService.java), [CohortReportQueryService](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/CohortReportQueryService.java) |
| A08 | API trả trạng thái, ngày bắt đầu/kết thúc và lý do lớp không nhận bài; giao diện báo lý do và khóa thao tác nộp tương ứng. | [StudentCohortsPage](D:/manabihub_ljpm/frontend/src/features/cohorts/StudentCohortsPage.tsx) |
| A09 | Tự lưu bài viết và tệp ghi âm đã chấp nhận vào IndexedDB, khôi phục khi mở bài, xóa sau nộp thành công hoặc đăng xuất/đổi tài khoản. Bản nháp có hạn 7 ngày. | [privateDrafts](D:/manabihub_ljpm/frontend/src/shared/storage/privateDrafts.ts), StudentCohortsPage |
| A10 | Server giải mã âm thanh và lưu `verifiedAudioSeconds`; không lấy số giây thiết bị báo làm bằng chứng thời lượng. Tệp có video, không giải mã được hoặc vượt giới hạn bị từ chối. | [CohortAudioValidator](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/CohortAudioValidator.java) |
| A11 | Yêu cầu sửa giữ trước một lượt chữa; bài khác không được dùng lượt đã giữ. Không hạ quota dưới số đã dùng cộng số đang giữ. Bài nộp lại dùng đúng lượt của mình. | CohortWorkspaceService.reservedRevisions/submit/review/updatePackage |
| A12 | Chat và chữa viết giữ quota chung theo tài khoản trước khi gọi AI. Khóa dòng người dùng, ghi PROCESSING trong giao dịch riêng, kết thúc thành SUCCESS/FAILED. | [AiQuotaService](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/ai/service/AiQuotaService.java) |
| A13 | Báo cáo tổng hợp trong SQL, phân trang và lọc trên server; không tải nội dung bài nộp hay âm thanh để lập báo cáo. Danh sách lớp học viên chỉ trả thông tin tóm tắt; workspace học viên chỉ tải bài của chính mình. | CohortReportQueryService, [CohortReportsPage](D:/manabihub_ljpm/frontend/src/features/cohorts/CohortReportsPage.tsx) |
| A14 | Chọn khóa hiện tại theo ngôn ngữ, khung và phiên bản khung của mục tiêu; bỏ gợi ý lịch học cố định gắn với Kanji N5. | [StudentDashboardPage](D:/manabihub_ljpm/frontend/src/features/student/pages/StudentDashboardPage.tsx), [StudyGoalsWidget](D:/manabihub_ljpm/frontend/src/features/student/components/StudyGoalsWidget.tsx) |

Bổ sung: khóa dòng người dùng trước khi cấp phiên học đầu tiên, tránh hai thiết bị đồng thời tạo cùng một lease. Vị trí: [LearningSessionLeaseServiceImpl](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/identity/service/impl/LearningSessionLeaseServiceImpl.java).

## 3. Chính sách vận hành cần hiểu

- **Quyền học:** mỗi lần mua và mỗi thành viên lớp là một grant riêng trong `enrollment_access_grants`. `enrollments` vẫn lưu tiến độ và trạng thái quyền tổng hợp để tương thích các màn hình hiện có. Quyền lớp có thể cho phép xem nội dung chuẩn bị trước ngày bắt đầu; nhận bài chỉ mở trong khoảng ngày của lớp.
- **Hoàn tiền:** trong lúc một quyền mua đang REFUND_PENDING, quyền học tổng hợp tạm khóa theo chính sách đang có. Khi hoàn tất, chỉ quyền mua tương ứng chuyển REFUNDED; quyền lớp khác còn hiệu lực có thể tiếp tục cho phép học. Tiến độ/chứng nhận lịch sử được giữ.
- **Quota chữa:** số còn lại là quota trừ mọi lần nộp đã dùng và lượt đã giữ cho yêu cầu sửa còn nhận được bài. Đóng bài hoặc hết hạn nhận bài giải phóng lượt giữ, không xóa bài đã nộp hay lượt đã dùng.
- **Quota AI:** tính các yêu cầu SUCCESS, PROCESSING và FAILED trong 60 giây/24 giờ gần nhất, gộp chat và chữa viết. Một lần gọi lỗi vẫn dùng quota để tránh gửi lại liên tục gây tốn phí. BLOCKED trước khi gọi không dùng quota. PROCESSING còn lại sau khi backend dừng đột ngột vẫn nằm trong cửa sổ quota; chưa có tác vụ tự khôi phục trạng thái bài đang xử lý.
- **Bản nháp:** lưu trên thiết bị, tách theo tài khoản/lớp/bài, không đồng bộ giữa thiết bị. Chỉ tệp đã ghi xong hoặc được chấp nhận mới được lưu; phần đang thu âm chưa hoàn tất không phải bản nháp. Nếu trình duyệt chặn hoặc hết dung lượng IndexedDB, giao diện báo lỗi lưu.
- **Âm thanh:** đo luồng âm thanh được chọn bằng FFmpeg; giới hạn 8 MB vẫn giữ. Sai số kỹ thuật chấp nhận tối đa 50 ms khi so với hạn; số giây xác minh làm tròn lên và không vượt hạn bài. Mỗi tiến trình giải mã có hạn chờ 20 giây; tối đa 2 tác vụ đồng thời trên mỗi backend. Bản ghi cũ chưa xác minh có trường `verifiedAudioSeconds` trống.
- **Báo cáo:** số người học và nghĩa vụ tính trên thành viên đang ACTIVE; thời gian chữa giữ cả lịch sử người đã rời lớp. Bộ lọc ngày chọn lớp diễn ra trong khoảng đó, số liệu là hiện trạng toàn bộ lớp. Đây chưa phải ảnh chụp kết quả chốt kỳ. CSV lấy từng trang theo bộ lọc, tối đa 20.000 lớp; dữ liệu có thể thay đổi nếu vận hành đang ghi thêm trong lúc xuất.
- **Chi phí:** tiền chữa = phút giáo viên tự nhập / 60 × đơn giá giờ người dùng chọn. Đây là ước tính có nguồn nhập liệu rõ ràng, chưa phải bảng lương phê duyệt hoặc báo cáo lợi nhuận kế toán.

## 4. Migration và dữ liệu cũ

Áp qua Flyway theo thứ tự; không chạy riêng lẻ bỏ qua migration trước đó:

Khi tích hợp `origin/develop`, dải V079–V084 đã được dùng cho tài chính, Firebase và hoàn tiền. Các migration SOFL chưa triển khai được chuyển thành V085–V091; giữ nguyên nội dung/checksum các migration đã có trên `develop`.

| Migration | Nội dung |
|---|---|
| V085–V088 | Hồ sơ ngôn ngữ, năng lực giáo viên, lớp, bài tập và phản hồi của đợt phát triển trước |
| [V089](D:/manabihub_ljpm/backend/src/main/resources/db/migration/V089__separate_enrollment_access_grants.sql) | Bảng grant độc lập, chuyển quyền cũ và tính lại enrollment |
| [V090](D:/manabihub_ljpm/backend/src/main/resources/db/migration/V090__unify_teacher_credential_claims.sql) | Registry mã chứng chỉ dùng chung, phát hiện xung đột chủ mã cũ |
| [V091](D:/manabihub_ljpm/backend/src/main/resources/db/migration/V091__cohort_audio_verification_and_ai_reservations.sql) | Thời lượng đã xác minh, trạng thái PROCESSING và index quota AI |

V089 **dừng** nếu phát hiện dòng mua PAID chưa hoàn tiền nhưng enrollment đang mang nguồn SOFL_COHORT. Lỗi cũ có thể đã ghi đè hạn mua bằng hạn lớp; mã không tự suy ra hạn mua lịch sử từ cấu hình khóa hôm nay. Cần đối chiếu đơn hàng, thời điểm thanh toán, chính sách/hạn mua hoặc bản sao dữ liệu trước đó rồi khôi phục nguồn PURCHASE và trạng thái/hạn mua đúng trước khi chạy lại. Grant lớp sẽ được chuyển độc lập từ danh sách thành viên.

V089 liên kết enrollment PURCHASE với dòng đơn hàng đã thanh toán/hoàn tiền gần nhất, giữ trạng thái và hạn đang lưu. Quyền mua không có đơn hàng đối ứng dùng ID enrollment làm mã nguồn. Các yêu cầu hoàn tiền gắn với dòng đơn hàng cũ hơn hoặc không có grant đối ứng cần đối soát riêng; API trả lỗi nghiệp vụ 409 thay vì tự thay đổi quyền của đơn khác.

V090 dừng nếu hồ sơ PENDING/APPROVED và claim JLPT cũ có hai chủ khác nhau cho cùng khóa mã. Giải quyết bằng minh chứng trước khi chạy lại; không tự chuyển chủ mã.

Không sửa file migration đã được áp vào một cơ sở dữ liệu. Nếu phiên bản nháp của V089–V091 đã được áp ở môi trường khác, cần migration tiến tới cho môi trường đó thay vì sửa checksum Flyway.

## 5. Bộ xử lý âm thanh và triển khai

Tại máy này đã có [ffmpeg.exe](D:/manabihub_ljpm/backend/tools/ffmpeg/ffmpeg.exe), `ffprobe.exe`, `LICENSE` và `README.txt`. Binary được bỏ qua bởi Git; không tự có trên máy triển khai khi chỉ sao chép mã nguồn.

Windows: chạy [setup-cohort-audio.ps1](D:/manabihub_ljpm/scripts/setup-cohort-audio.ps1) từ repo. Script chỉ cài trong `backend/tools/ffmpeg`, tải bản 9.0.2 từ [Gyan FFmpeg builds](https://www.gyan.dev/ffmpeg/builds/), so SHA-256 của ZIP với `60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba` trước khi giải nén. Có hạn tải 300 giây.

Backend tìm decoder theo thứ tự: `MANABIHUB_COHORT_FFMPEG_EXECUTABLE` → các đường dẫn Windows trong `tools/ffmpeg` → `tools/ffmpeg/ffmpeg` dành cho gói Linux → lệnh `ffmpeg` trên PATH. Không có decoder thì bài ghi âm mới trả 503 và không được lưu thành bài nộp hợp lệ.

Gói Elastic Beanstalk dùng [setup-cohort-audio-linux.ps1](D:/manabihub_ljpm/scripts/setup-cohort-audio-linux.ps1) và [build-eb-bundle.ps1](D:/manabihub_ljpm/scripts/build-eb-bundle.ps1). Bản Linux LGPL từ BtbN được kiểm tra SHA-256 trước khi đóng gói, kèm giấy phép; không tải binary trong lúc khởi động AWS. Hook tạo thư mục `/var/app/manabihub-storage/cohort-audio`, chủ sở hữu `webapp`, quyền 0700. Thư mục giữ được tệp qua lần triển khai trên cùng EC2, chưa giữ được tệp khi thay EC2; cần kho bền vững và sao lưu tệp trước khi vận hành thương mại.

Trước triển khai đã tạo snapshot RDS `manabihub-pre-sofl-20261001`. Flyway được bật trong profile production để chạy migration còn thiếu; biên bản hiện ghi nhận AWS deployment/health, chưa kèm bản trích xuất bảng lịch sử Flyway. Cần kho tệp bền vững cho `MANABIHUB_COHORT_AUDIO_STORAGE_ROOT` trước khi vận hành thương mại; snapshot RDS không sao lưu tệp âm thanh trên EC2. Giữ HTTPS, quyền ghi/đọc cho backend và giới hạn multipart tương ứng.

## 6. Giới hạn phạm vi sản phẩm

Các thay đổi này hoàn thiện đợt sửa lỗi của các luồng đã xây dựng. Những hạng mục thương mại mở rộng vẫn cần phạm vi triển khai riêng: phân quyền theo tổ chức/chi nhánh độc lập, nhiều giáo viên cho một lớp, bảng lương và thu học phí trực tiếp của trung tâm, ảnh chụp báo cáo chốt kỳ, chính sách cấp độ/hạn hiệu lực và thu hồi riêng từng năng lực giáo viên, bản địa hóa toàn bộ màn hình cũ, chấm phát âm AI tự động. Không đưa các hạng mục này vào danh sách chức năng đã hoàn thành khi gửi cho SOFL.

## 7. Bằng chứng build

- Backend sau tích hợp `develop`: Java 21, 776 tệp nguồn; `mvnw.cmd -DskipTests package` hoàn tất lúc 12:42 ngày 01/10/2026. Bước này có biên dịch mã test, không chạy test. Cảnh báo còn lại về API deprecated trong OAuth và unchecked trong cấu hình bảo mật.
- Frontend: TypeScript và Vite hoàn tất. Cảnh báo về gói dash.js ESM/CommonJS và chunk lớn vẫn còn; build thành công không xác nhận phát DASH hay hiệu năng trình duyệt.
- `git diff --check`: không phát hiện lỗi khoảng trắng trong phần thay đổi đã được Git theo dõi.
- Không chạy tests, không thực hiện giao dịch tiền thật và không tạo dữ liệu doanh thu/học viên giả.
