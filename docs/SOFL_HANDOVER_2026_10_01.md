# Bàn giao phần mềm cho SOFL — cập nhật 01/10/2026

Đã cập nhật theo đợt sửa A01–A14. Chi tiết thay đổi, chính sách quyền học, quota và dữ liệu cũ: [SOFL_FIXES_2026_10_01](D:/manabihub_ljpm/docs/SOFL_FIXES_2026_10_01.md).

## 1. Những gì đã triển khai

| Phần | Cách sử dụng | Nguồn dữ liệu |
|---|---|---|
| Khóa Hàn, Trung, Nhật | Chọn ngôn ngữ, TOPIK/HSK/JLPT hoặc khung nội bộ khi tạo khóa | Các trường riêng trong khóa học; không suy ra từ tên khóa |
| Mục tiêu học tập | Chọn ngôn ngữ lúc đăng ký; chỉnh nhiều mục tiêu trong hồ sơ | `student_learning_goals` |
| Năng lực giáo viên | Nộp minh chứng chuyên môn; quản lý duyệt theo ngôn ngữ/khung | `teacher_language_qualifications`; tài liệu được tải qua API có xác thực |
| Lớp theo đợt | Mở lớp từ khóa đã xuất bản, thêm học viên bằng email, đặt thời hạn | `learning_cohorts`, `cohort_members`, `enrollments` |
| Bài viết / bài nói theo lớp | Giao đề, hạn nộp, hạn nhận bài lại, số lần nộp tối đa | `cohort_assignments` |
| Ghi âm | Micro trình duyệt hoặc tải tệp WebM/OGG/M4A/WAV/MP3; nghe trước khi nộp | Tệp riêng tối đa 8 MB; metadata và SHA-256 trong bài nộp |
| Giáo viên chữa bài | Xem nội dung, nghe ghi âm, nhập điểm 0–100, phản hồi, phút chữa và yêu cầu nộp lại | Mỗi lần nộp là một dòng trong `cohort_submissions`; phản hồi đã gửi được giữ nguyên |
| Học viên cần hỗ trợ | Hiển thị bài chưa nộp khi đến hạn và bài cần sửa; gửi nhắc tối đa một lần/24 giờ | Tính từ bài tập, bài nộp và thành viên thực tế |
| Gói chữa bài | Định mức theo học viên trong lớp; hạn phản hồi theo giờ | Mặc định 12 lượt và 48 giờ; đây là cấu hình sản phẩm, có thể thay đổi |
| Báo cáo học vụ | Theo đơn vị, chi nhánh; xuất CSV; ước tính tiền chữa theo đơn giá nhập | Bài nộp và phút chữa do giáo viên khai báo, không dùng số liệu doanh thu giả |
| Giao diện đa ngôn ngữ | Bộ chọn Tiếng Việt / 한국어 / 简体中文 | Từ điển cho các màn hình lớp, giao bài, ghi âm, chữa bài, báo cáo và điều hướng; đăng nhập, bộ lọc và phần điều khiển học cũng đã chuyển dần |

## 2. Các màn hình để trình bày

### Giáo viên

1. `/teacher/kyc`: xác minh danh tính, nộp năng lực TOPIK/HSK hoặc khung nội bộ; quản lý duyệt tại `/admin/kyc`.
2. `/teacher/courses/new`: tạo khóa theo ngôn ngữ, xây dựng nội dung và hoàn tất quy trình xuất bản đang có.
3. `/teacher/cohorts`: tạo lớp từ khóa đã xuất bản; nhập email **tài khoản học viên đã tồn tại** để thêm vào lớp.
4. Trong lớp, bấm **Giao bài, chữa bài và theo dõi học viên** → `/teacher/cohorts/{cohortId}/workspace`.
5. Tab **Bài tập**: giao bài viết tiếng Hàn hoặc bài nói tiếng Trung; nhập đề bằng ngôn ngữ muốn học; đặt hai hạn nộp và số lần nộp tối đa. Hạn nhận bài phải nằm trước thời điểm lớp hết quyền học.
6. Tab **Bài nộp**: chọn bài chờ chữa, nghe ghi âm hoặc đọc bài viết; nhập nhận xét, điểm và phút thực tế. Chọn nộp lại khi lớp, thời hạn và định mức còn cho phép.
7. Tab **Học viên cần hỗ trợ**: xem người có bài quá hạn / cần sửa, gửi thông báo nhắc.
8. Tab **Gói chữa bài**: thay số lượt và giờ phản hồi. Không thể hạ số lượt thấp hơn mức đã dùng cộng các lượt đang giữ cho bài cần sửa.

### Học viên

1. `/onboarding/student`: chọn tiếng Hàn / Trung / Nhật và mục tiêu.
2. `/student/cohorts`: danh sách lớp được cấp; mở lớp và bấm **Xem bài / nộp bài**.
3. Bài viết: nhập nội dung. Bài nói: cho phép micro, ghi âm rồi nghe lại, hoặc chọn tệp âm thanh.
4. Nộp bài, đợi giáo viên chữa; lịch sử hiển thị từng lần nộp, điểm và nhận xét.
5. Chỉ nộp lại sau khi giáo viên yêu cầu, còn lượt và còn thời gian nhận bài.

### Quản lý trung tâm

- `/admin/cohort-reports`: quyền `COURSE_MANAGER` hoặc `SYSTEM_ADMIN`.
- Lọc đơn vị / cơ sở, đọc số lớp, lượt học viên, người cần hỗ trợ, bài chữa trễ; tải CSV UTF-8 mở được trong Excel.
- Giáo viên có báo cáo các lớp mình tại `/teacher/cohort-reports`.
- Nhập **đơn giá giờ chữa** để ước tính chi phí; không nhập thì tiền để trống. CSV giữ cả đơn giá, phút chữa và thời điểm xuất để đối chiếu.

## 3. Cách hiểu các con số

- **Tỷ lệ nộp:** số cặp học viên–bài tập đã có ít nhất một bài nộp / số cặp đã đến hạn. Chỉ tính học viên đang trong lớp và hạn nộp từ lúc học viên vào lớp. Đóng nhận bài vẫn giữ nghĩa vụ đã đến hạn. Nộp muộn vẫn nằm trong số đã nộp; chỉ số này không phải tỷ lệ nộp đúng hạn.
- **Bài quá hạn:** cặp đã đến hạn nhưng chưa có bài nộp. Bài có nộp và cần sửa được theo dõi riêng.
- **Cần hỗ trợ:** học viên có ít nhất một bài chưa nộp quá hạn hoặc một bài giáo viên yêu cầu sửa.
- **Chờ chữa trễ:** bài chưa có phản hồi và đã qua `review_due_at`.
- **Chữa đúng hạn:** lượt đã chữa trước hoặc bằng `review_due_at` / tổng lượt đã chữa.
- **Điểm trung bình:** điểm ở lần được chữa gần nhất của từng cặp học viên–bài, không cộng các điểm cũ đã bị thay thế.
- **Lượt đã dùng:** mọi lần nộp, gồm lần sửa; một lần nộp sử dụng một lượt và đồng thời đặt trước khối lượng chữa bài.
- **Lượt giữ cho bài cần sửa:** một lượt cho mỗi yêu cầu sửa còn được nhận bài. Số còn lại = định mức − lượt đã dùng − lượt đang giữ. Nộp lại tiêu thụ lượt đã giữ của chính bài đó.
- **Phạm vi báo cáo:** thẻ tổng hợp tính trang hiện tại; CSV lấy toàn bộ lớp theo bộ lọc. Ngày lọc khoảng hoạt động của lớp; các chỉ số là hiện trạng, chưa phải số phát sinh hoặc số chốt trong kỳ.
- **Chi phí chữa ước tính:** tổng phút giáo viên nhập / 60 × đơn giá giờ nhập. Chưa gồm hạ tầng, thuế, bán hàng hay các chi phí vận hành khác; đây không phải báo cáo lợi nhuận kế toán.
- **Lượt học viên:** cộng sĩ số từng lớp; người học nhiều lớp có thể được tính nhiều lần.
- **SLA:** chốt tại thời điểm nộp bài. Thay cấu hình chỉ ảnh hưởng bài nộp mới.

## 4. Chỗ sửa code

| Muốn sửa | File chính |
|---|---|
| Ngôn ngữ, khung, cấp độ hợp lệ | `backend/src/main/java/com/manabihub/course/domain/CourseLanguageProfile.java`, `frontend/src/shared/learning/languageProfiles.ts` |
| Phê duyệt năng lực giáo viên | `backend/src/main/java/com/manabihub/kyc/service/TeacherQualificationService.java` |
| Thêm / gỡ người học, quyền khóa theo nhiều lớp | `backend/src/main/java/com/manabihub/cohort/service/LearningCohortService.java` |
| Quyền mua / quyền lớp độc lập và hoàn tiền | `backend/src/main/java/com/manabihub/learning/service/EnrollmentAccessService.java` |
| Đo thời lượng âm thanh tại server | `backend/src/main/java/com/manabihub/cohort/service/CohortAudioValidator.java` |
| Registry chứng chỉ dùng chung | `backend/src/main/java/com/manabihub/kyc/service/TeacherCredentialRegistry.java` |
| Giữ quota AI chung trước khi gọi | `backend/src/main/java/com/manabihub/ai/service/AiQuotaService.java` |
| Báo cáo tổng hợp SQL và phân trang | `backend/src/main/java/com/manabihub/cohort/service/CohortReportQueryService.java` |
| Lưu bản nháp theo tài khoản | `frontend/src/shared/storage/privateDrafts.ts` |
| Hạn nộp, quota, SLA, ghi âm riêng, phản hồi, báo cáo | `backend/src/main/java/com/manabihub/cohort/service/CohortWorkspaceService.java` |
| API giáo viên | `backend/src/main/java/com/manabihub/cohort/controller/TeacherCohortWorkspaceController.java` |
| API học viên | `backend/src/main/java/com/manabihub/cohort/controller/StudentCohortController.java` |
| API báo cáo quản lý | `backend/src/main/java/com/manabihub/cohort/controller/AdminCohortReportController.java` |
| Màn hình giao và chữa bài | `frontend/src/features/cohorts/TeacherCohortWorkspacePage.tsx` |
| Màn hình học viên | `frontend/src/features/cohorts/StudentCohortsPage.tsx` |
| Thu âm, định dạng, giới hạn trên trình duyệt | `frontend/src/features/cohorts/AudioRecorder.tsx` — dùng `MediaRecorder`, `getUserMedia`; đọc thời lượng tệp bằng `AudioContext` |
| Nghe bản ghi đã nộp | `frontend/src/features/cohorts/PrivateAudioPlayer.tsx` — tải blob qua Axios có token, tạo URL tạm và giải phóng khi rời màn hình |
| Báo cáo, xuất CSV, đơn giá | `frontend/src/features/cohorts/CohortReportsPage.tsx` |
| Kết nối API | `frontend/src/features/cohorts/workspaceApi.ts` |
| Bản dịch | `frontend/src/shared/i18n/translations.ts`, `locale.ts`, `LocaleSelect.tsx` |
| Các bảng mới | Flyway `V085`–`V091` trong `backend/src/main/resources/db/migration` |

## 5. Quyền truy cập và vận hành

- Giáo viên thao tác trên lớp mình sở hữu. Học viên chỉ thấy bài nộp của mình và lớp mình đang tham gia.
- File ghi âm không có URL công khai: API kiểm tra chủ bài hoặc chủ lớp trước khi trả file; phản hồi có `Cache-Control: no-store`.
- Gỡ khỏi một lớp không thu hồi quyền do lớp khác cùng khóa còn hiệu lực cấp. Quyền mua độc lập đang hiệu lực được giữ; khóa lớp được đồng bộ lại khi đổi trạng thái lớp.
- Khóa bản ghi lớp khi ghi dữ liệu để tránh vượt sĩ số, nộp vượt định mức hoặc trùng lần nộp do hai thao tác cùng lúc.
- Phiên bản này phục vụ vận hành trung tâm qua các vai trò nội bộ đang có. Tên đơn vị / chi nhánh là chiều báo cáo; chưa phải cơ chế phân tách tenant cho nhiều công ty độc lập dùng chung một hệ thống. Tài khoản quản lý nội bộ có thể xem báo cáo toàn hệ thống.
- Ghi âm cần HTTPS khi triển khai; `localhost` được trình duyệt cho phép để phát triển.
- Cấu hình kho âm thanh: `MANABIHUB_COHORT_AUDIO_STORAGE_ROOT` / `manabihub.cohort.audio-storage-root`; local mặc định `storage/cohort-audio`, profile production mặc định `/var/app/manabihub-storage/cohort-audio`. Gói AWS tạo thư mục riêng ngoài bản phát hành để giữ tệp khi cập nhật ứng dụng trên cùng EC2. Thay EC2 vẫn cần khôi phục tệp từ kho bền vững/bản sao lưu; snapshot RDS chỉ sao lưu cơ sở dữ liệu.
- Giới hạn multipart toàn ứng dụng 16 MB/tệp, 28 MB/request; API ghi âm vẫn giới hạn 8 MB. Reverse proxy cần cho phép dung lượng tương ứng.
- Server kiểm tra MIME, dấu nhận dạng container, dung lượng, SHA-256 và giải mã bằng FFmpeg để xác minh thời lượng. Cấu hình decoder: `MANABIHUB_COHORT_FFMPEG_EXECUTABLE`; xem hướng dẫn cài trong bản hoàn tất. Thiếu decoder thì API không nhận bài ghi âm mới. Giáo viên đánh giá bài nói; không có chấm phát âm AI tự động trong luồng này.
- Bài kiểm tra cuối khóa hiện là trắc nghiệm COURSE/PBT/IBT. Bài nói được giao tại lớp học; không gắn nhãn bài nói cho bộ trắc nghiệm.
- Nội dung khóa, đề bài và phản hồi do giáo viên nhập được giữ nguyên ngôn ngữ. Một số màn hình cũ và thông báo API vẫn còn tiếng Việt; không coi toàn hệ thống đã bản địa hóa 100%.

## 6. Trạng thái bàn giao

- Đã biên dịch backend bằng `mvnw.cmd -DskipTests compile` và đóng gói frontend bằng `npm run build` trong quá trình làm việc.
- Chưa chạy bộ test tự động hoặc xác nhận toàn bộ luồng bằng tài khoản thật trên trình duyệt. Backend đã triển khai vào môi trường AWS hiện có, bản `manabihub-sofl-20261001-f8375031`, Health Ok và `/actuator/health` UP; frontend Amplify deployment 218 đã Deployed / SUCCEED cùng commit f8375031. Kết quả phát hành được ghi tại [biên bản AWS](D:/manabihub_ljpm/docs/SOFL_AWS_RELEASE_2026_10_01.md).
- Flyway được bật trong profile production và chạy migration còn thiếu khi khởi động backend. Đã tạo snapshot RDS `manabihub-pre-sofl-20261001` trước triển khai. Biên bản ghi nhận trạng thái triển khai/health, chưa kèm trích xuất bảng lịch sử Flyway; dùng tài khoản giáo viên/học viên hiện có để xác nhận nghiệp vụ theo mục 2.
- Không tạo học viên, doanh thu hoặc điểm số giả để đưa vào báo cáo.
