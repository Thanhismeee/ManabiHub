# Rà soát sâu ManabiHub dành cho SOFL

**Cập nhật sau rà soát:** đã triển khai sửa mã nguồn cho A01–A14. Xem [bản hoàn tất và hướng dẫn triển khai](D:/manabihub_ljpm/docs/SOFL_FIXES_2026_10_01.md). Nội dung bên dưới ghi nhận trạng thái tại thời điểm phát hiện, trước đợt sửa; chưa phải kết quả kiểm thử sau sửa.

Ngày rà soát: 01/10/2026. Phạm vi: mã nguồn hiện tại trong D:/manabihub_ljpm, gồm phần mới về tiếng Hàn/Trung, hồ sơ giáo viên, lớp học, bài tập, báo cáo và các luồng mua khóa học/hoàn tiền/AI có liên quan.

**Cách đọc kết quả:** đây là rà soát tĩnh bằng cách đọc và đối chiếu code. Các tình huống bên dưới là kịch bản suy ra từ nhánh xử lý, chưa được chạy để tái hiện trên cơ sở dữ liệu. Không chạy kiểm thử, migration hoặc giao dịch thanh toán trong lượt rà soát này. Lượt này chỉ thêm báo cáo, chưa sửa các lỗi được liệt kê. Build thành công ở lượt trước không xác nhận các nghiệp vụ này vận hành đúng.

P1: cần xử lý trước khi chạy thử với dữ liệu, quyền học và thanh toán thật. P2: cần xử lý trước khi mở rộng hoặc cam kết tính năng tương ứng với khách hàng. Mức ưu tiên phản ánh ảnh hưởng nghiệp vụ, không phải điểm đánh giá lỗ hổng bảo mật.

## 1. Kết luận cho người quyết định triển khai

Phần lớp học, nộp bài và chữa bài đã có đường đi trong code. Tuy nhiên, quyền học theo lớp đang được gộp vào cùng bản ghi với quyền mua khóa học. Đây là điểm cần sửa đầu tiên: trung tâm thay đổi lớp có thể làm mất quyền đã thanh toán; ngược lại, còn quyền học theo lớp nhưng người học vẫn có thể bị khóa khi quyền mua hết hạn.

Hai luồng KYC cũ và mới cũng đang cùng ghi trạng thái chung của giáo viên nhưng dùng cách kiểm tra trùng chứng chỉ khác nhau. Báo cáo học vụ hiện thay đổi theo việc đóng bài và danh sách học viên hiện tại; chưa nên dùng nó làm bằng chứng kết quả đào tạo của một kỳ đã chốt.

Không có cơ sở từ lượt đọc code này để tuyên bố hệ thống đã sẵn sàng vận hành thương mại hoàn chỉnh. Cũng chưa có dữ liệu vận hành thật để xác nhận doanh thu, lợi nhuận, thời gian hoàn vốn hoặc mức tiết kiệm cho SOFL.

## 2. Danh sách ưu tiên

| Mã | Mức | Vấn đề | Ảnh hưởng trực tiếp |
| --- | --- | --- | --- |
| A01 | P1 | Mua lại không chuyển nguồn quyền học từ lớp sang giao dịch mua | Đã trả tiền nhưng có thể bị khóa khi lớp đóng/gỡ học viên |
| A02 | P1 | Quyền bị thu hồi do rời lớp làm chặn cả việc mua khóa | Mất cơ hội bán tiếp và học viên không tự khôi phục quyền được |
| A03 | P1 | Quyền mua hết hạn không chuyển sang quyền lớp còn hiệu lực | Trung tâm đã cấp lớp nhưng học viên bị khóa tài liệu |
| A04 | P1 | Đồng bộ lớp ghi đè trạng thái chờ/đã hoàn tiền | Mở lại quyền đang bị khóa hoặc hoàn tiền làm mất quyền lớp hợp lệ |
| A05 | P1 | Chống trùng chứng chỉ không thống nhất giữa hai luồng | Cùng chứng chỉ có thể lọt qua kiểm tra trùng |
| A06 | P1 | Quyết định KYC cũ ghi đè năng lực Hàn/Trung đã duyệt | Giáo viên đủ điều kiện có thể mất quyền hoạt động |
| A07 | P1 | Đóng bài làm mất nghĩa vụ chưa nộp trong báo cáo | Tỷ lệ hoàn thành có thể tăng chỉ vì thao tác quản trị |
| A08 | P2 | Giao diện nộp bài thiếu trạng thái/thời gian lớp | Cho soạn bài rồi mới báo không được nộp |
| A09 | P2 | Không giữ bản nháp bài viết/ghi âm | Đóng hộp thoại hoặc tải lại có thể mất bài chưa nộp |
| A10 | P2 | Backend tin thời lượng âm thanh do client gửi | Giới hạn thời lượng có thể bị vượt qua |
| A11 | P2 | Yêu cầu sửa bài không giữ lại lượt chữa cần thiết | Giáo viên yêu cầu nộp lại nhưng học viên hết lượt |
| A12 | P2 | Giới hạn AI kiểm tra và ghi nhận ở hai thời điểm riêng | Yêu cầu đồng thời có thể vượt định mức và tăng chi phí |
| A13 | P2 | Báo cáo đọc toàn bộ lịch sử, không phân trang | Rủi ro chậm, tốn bộ nhớ khi số lớp và bài tăng |
| A14 | P2 | Mục tiêu học nội bộ không lọc theo ngôn ngữ | Mục tiêu tiếng Hàn có thể lấy khóa tiếng Trung làm khóa hiện tại |

## 3. Quyền học, thanh toán và hoàn tiền

### A01 — P1: quyền mua vẫn mang nguồn của lớp học

**Vị trí:** [resetForRepurchase](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/learning/service/impl/EnrollmentProgressResetServiceImpl.java:27), [cấp quyền sau thanh toán](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/payment/service/impl/PaymentServiceImpl.java:420), [đồng bộ quyền lớp](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/LearningCohortService.java:261).

Khi mua lại, resetForRepurchase đặt lại trạng thái, tiến độ và hạn học nhưng không đổi accessSource sang PURCHASE, không xóa sourceCohortId. Vì vậy bản ghi trước đó mang SOFL_COHORT tiếp tục được đồng bộ như quyền cấp bởi lớp.

**Tình huống:** quyền lớp hết hạn tự nhiên nhưng bản ghi vẫn ACTIVE; học viên mua lại khóa còn được bán; thanh toán thành công; giáo viên chuyển lớp cũ sang ARCHIVED. Đồng bộ lớp không tìm được lớp cấp quyền và đặt bản ghi thành REVOKED, dù lần mua mới còn hạn.

Một nhánh khác cũng có vấn đề: học viên tạo đơn trước, được thêm vào lớp trong lúc đơn đang chờ thanh toán, sau đó thanh toán thành công. createEnrollments gặp quyền SOFL_COHORT đang ACTIVE và còn hạn thì không ghi nhận quyền mua độc lập.

**Hướng sửa:** ghi nhận mỗi nguồn cấp quyền độc lập, gắn quyền mua với order item và quyền lớp với membership. Hạn truy cập hiệu lực là hợp của các quyền còn hợp lệ. Nếu sửa tạm trên mô hình hiện tại, phải xử lý cả mua lại lẫn đơn đang chờ rồi mới được cấp lớp; chỉ sửa resetForRepurchase sẽ bỏ sót nhánh thứ hai.

### A02 — P1: rời lớp làm chặn cả việc mua lại

**Vị trí:** [thu hồi quyền khi không còn lớp](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/LearningCohortService.java:273), [kiểm tra đã sở hữu khi tạo đơn](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/order/service/impl/OrderServiceImpl.java:92).

Đồng bộ lớp đặt SOFL_COHORT thành REVOKED khi không còn lớp cấp quyền. createOrder coi mọi REVOKED là đã sở hữu và từ chối đơn với ORDER_ALREADY_ENROLLED, không phân biệt lý do thu hồi hay nguồn quyền.

**Tình huống:** học viên chỉ có quyền theo lớp, được gỡ khỏi lớp, muốn tự mua khóa để tiếp tục. Hệ thống trả thông báo đã sở hữu khóa, trong khi API học cũng từ chối quyền REVOKED.

**Hướng sửa:** phân biệt kết thúc quyền lớp với thu hồi do vi phạm/hoàn tiền. Quyền lớp đã kết thúc không được chặn một giao dịch mua hợp lệ. Đừng mở mua lại cho tất cả REVOKED vì có thể bỏ qua chính sách thu hồi do vi phạm.

### A03 — P1: mua còn hạn lúc vào lớp, nhưng mất quyền khi lần mua hết hạn

**Vị trí:** [nhánh giữ nguyên quyền mua](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/LearningCohortService.java:264), [API học kiểm tra hết hạn](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/learning/service/impl/LearningServiceImpl.java:371).

Khi thêm học viên có quyền PURCHASE còn hạn, synchronizeCohortAccess trả về ngay. Nó không lưu thêm quyền lớp và không kéo dài hạn. Các lời gọi đồng bộ hiện nằm ở thêm/gỡ học viên hoặc đổi trạng thái lớp; đường đọc bài học không đối chiếu lại membership.

**Tình huống:** quyền mua còn 5 ngày, lớp SOFL kéo dài thêm 60 ngày. Thêm học viên vào lớp không đổi hạn mua. Đến ngày thứ 6, học viên vẫn có membership ACTIVE nhưng API học báo hết hạn. Các mốc này chỉ là dữ liệu giả định để mô tả lỗi.

**Hướng sửa:** giải quyết cùng A01 bằng các quyền độc lập. Kiểm tra quyền hiệu lực ở đường truy cập nội dung, không phụ thuộc việc giáo viên phải đổi trạng thái lớp để kích hoạt đồng bộ.

### A04 — P1: trạng thái hoàn tiền và quyền lớp ghi đè nhau

**Vị trí:** [đồng bộ lớp](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/LearningCohortService.java:261), [khóa quyền khi yêu cầu hoàn tiền](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/refund/service/impl/StudentRefundServiceImpl.java:224), [duyệt hoàn tiền](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/refund/service/impl/RefundDecisionTransactionService.java:180).

Nhánh trả về giữ quyền mua chỉ áp dụng ACTIVE/COMPLETED. Với REFUND_PENDING, REFUNDED hoặc REVOKED, nếu còn lớp hợp lệ, đồng bộ lớp đổi nguồn thành SOFL_COHORT rồi đặt ACTIVE/COMPLETED. Cơ chế khóa trong lúc chờ hoàn tiền hiện có thể bị mở lại bởi thao tác lớp. Ngược lại, quyết định hoàn tiền đặt bản ghi chung thành REFUNDED dù học viên có thể còn quyền lớp độc lập.

**Tình huống:** một yêu cầu hoàn tiền cần xét duyệt thủ công đang PENDING; học viên thuộc lớp còn hạn; giáo viên cập nhật trạng thái lớp, kể cả đặt lại cùng trạng thái. Quyền học chung được mở lại. Khi hoàn tiền được duyệt, bản ghi chung bị khóa lại mà không xét quyền còn lại của lớp.

Ngoài ra, đồng bộ lớp đọc enrollment bằng truy vấn không khóa, trong khi đường thanh toán dùng findByStudentIdAndCourseIdForUpdate; Enrollment không có trường phiên bản để phát hiện ghi đè. Đây là rủi ro cạnh tranh cần xác minh bằng kiểm thử đồng thời, chưa có kết quả tái hiện.

**Hướng sửa:** hoàn tiền chỉ tác động quyền của giao dịch liên quan; quản lý trạng thái giao dịch tách khỏi quyền lớp. Chốt rõ chính sách quyền nào bị tạm khóa trong lúc xử lý tranh chấp. Dùng một quy tắc khóa nhất quán cho mọi đường ghi cùng bản ghi và tính lại quyền hiệu lực sau commit.

## 4. Hồ sơ giáo viên và năng lực giảng dạy

### A05 — P1: một chứng chỉ có thể lọt qua hai cơ chế kiểm tra trùng

**Vị trí:** [kiểm tra mã của luồng mới](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/kyc/service/TeacherQualificationService.java:132), [truy vấn mã mới](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/kyc/repository/TeacherLanguageQualificationRepository.java:41), [chuẩn hóa/giữ mã JLPT cũ](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/kyc/service/TeacherCertificateClaimService.java:37), [schema mới](D:/manabihub_ljpm/backend/src/main/resources/db/migration/V080__add_teacher_language_qualifications.sql:1).

Có ba nguyên nhân:

1. Luồng mới chỉ trim mã và so sánh không phân biệt hoa/thường. Luồng JLPT cũ còn loại dấu phân cách. Các chuỗi minh họa AB-1234 và AB1234 không được luồng mới coi là một mã.
2. Luồng cũ dùng teacher_certificate_claims; luồng mới chỉ tra teacher_language_qualifications. Chứng chỉ đã giữ chỗ trong kho cũ không được đối chiếu khi nộp hồ sơ mới. Migration các giáo viên cũ đã duyệt cũng không đưa credential_code vào bản ghi năng lực.
3. Luồng mới khóa theo từng giáo viên rồi tra và insert, nhưng không có ràng buộc duy nhất ở database cho mã chứng chỉ. Hai tài khoản khác nhau có thể cùng vượt qua bước tra cứu trước khi một trong hai commit.

**Ảnh hưởng:** kiểm tra tự động không bảo đảm một chứng chỉ thuộc một giáo viên. Việc duyệt vẫn do quản lý quyết định; chưa có bằng chứng rằng hồ sơ giả đã được duyệt trong thực tế.

**Hướng sửa:** thống nhất kho giữ chỗ chứng chỉ giữa hai luồng; chuẩn hóa theo từng loại chứng chỉ; xác định rõ phạm vi mã theo đơn vị cấp/loại chứng chỉ thay vì áp một quy tắc cho mọi giấy tờ. Dùng ràng buộc duy nhất và xử lý xung đột ở database; đối chiếu và xử lý dữ liệu cũ trước khi bổ sung ràng buộc.

### A06 — P1: từ chối hồ sơ JLPT cũ có thể khóa giáo viên đã được duyệt Hàn/Trung

**Vị trí:** [luồng mới chỉ chặn REVOKED khi nộp](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/kyc/service/TeacherQualificationService.java:88), [duyệt năng lực mới](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/kyc/service/TeacherQualificationService.java:253), [quyết định KYC cũ](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/kyc/service/impl/KycServiceImpl.java:268), [chặn giáo viên mất role](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/security/config/TeacherEligibilityFilter.java:88).

Hồ sơ mới có thể được gửi trong khi hồ sơ JLPT cũ đang chờ. Duyệt năng lực mới đặt trạng thái chung APPROVED và cấp role TEACHER. Sau đó luồng cũ REJECTED/CORRECTION_REQUIRED đặt trạng thái chung xuống và xóa role, không xét năng lực mới đang APPROVED.

**Tình huống:** cùng tài khoản có JLPT chờ duyệt và hồ sơ TOPIK mới; quản lý duyệt TOPIK rồi từ chối JLPT. Giáo viên vẫn có bản ghi TOPIK APPROVED nhưng bộ lọc quyền giáo viên từ chối API hoạt động, còn xuất bản bị chặn bởi trạng thái chung.

**Hướng sửa:** tách trạng thái danh tính khỏi trạng thái từng năng lực; tính quyền hoạt động từ danh tính hợp lệ, các năng lực được duyệt và quyết định đình chỉ riêng. Từ chối một năng lực không được vô hiệu hóa năng lực khác. Hai luồng duyệt phải dùng cùng một dịch vụ cập nhật quyền và quy tắc khóa.

## 5. Báo cáo và trải nghiệm học viên

### A07 — P1: đóng bài làm báo cáo tốt lên dù không có bài nộp mới

**Vị trí:** [tính mẫu số báo cáo](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/CohortWorkspaceService.java:197), [đếm bài quá hạn](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/CohortWorkspaceService.java:240), [định nghĩa bài đến hạn](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/CohortWorkspaceService.java:251).

isDueFor yêu cầu bài chưa closed. Một thao tác đóng nhận bài vì vậy xóa luôn các nghĩa vụ nộp đã đến hạn khỏi mẫu số, số bài thiếu và số học viên cần hỗ trợ.

**Ví dụ giả định:** 10 học viên, hai bài cùng đã đến hạn. Bài A có 10 lượt nộp, bài B có 3 lượt nộp. Tỷ lệ là 13/20 = 65%. Chỉ đóng bài B thì tỷ lệ trở thành 10/10 = 100%, bảy bài thiếu biến mất, không có ai nộp thêm. Phép tính này minh họa code; không phải số liệu của SOFL.

**Hướng sửa:** closed chỉ ngừng nhận bài. Báo cáo kết quả giữ nghĩa vụ đã giao và kết quả thiếu bài. Hủy yêu cầu nộp phải là thao tác khác có lý do, audit và công thức riêng. Nếu gửi báo cáo một kỳ cho khách, cần lưu phiên bản/chốt kỳ; thay đổi roster hiện tại cũng đang làm số liệu lịch sử thay đổi.

### A08 — P2: màn hình cho nộp bài khi lớp chưa bắt đầu/đã kết thúc

**Vị trí:** [canSubmit trên giao diện](D:/manabihub_ljpm/frontend/src/features/cohorts/StudentCohortsPage.tsx:32), [DTO workspace](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/dto/CohortWorkspaceDto.java:34), [backend chặn thời gian/trạng thái lớp](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/CohortWorkspaceService.java:100).

Workspace không trả trạng thái lớp, ngày bắt đầu/kết thúc hoặc lý do không được nộp. Giao diện chỉ kiểm tra bài, số lượt và hạn nhận bài. Membership ACTIVE vẫn làm lớp hiện trong danh sách khi giáo viên đặt COMPLETED/ARCHIVED.

**Tình huống:** giáo viên kết thúc lớp sớm nhưng acceptUntil của bài còn ở tương lai, hoặc lớp chưa đến ngày bắt đầu. Học viên mở bài, soạn nội dung/ghi âm, bấm gửi rồi mới nhận lỗi. Backend đã chặn nên đây là lỗi trải nghiệm, không phải bằng chứng vượt quyền.

**Hướng sửa:** API trả quyền nộp hiệu lực và lý do khóa, cùng trạng thái và thời gian lớp. Giao diện hiển thị lý do trước khi học viên làm bài; vẫn kiểm tra lại ở backend khi gửi.

### A09 — P2: bài chưa nộp không có cơ chế giữ bản nháp

**Vị trí:** [state bài làm](D:/manabihub_ljpm/frontend/src/features/cohorts/StudentCohortsPage.tsx:14), [mở bài xóa nội dung cũ](D:/manabihub_ljpm/frontend/src/features/cohorts/StudentCohortsPage.tsx:60), [đóng hộp thoại](D:/manabihub_ljpm/frontend/src/features/cohorts/StudentCohortsPage.tsx:65).

Nội dung và File ghi âm chỉ nằm trong state. Đóng rồi mở bài lại sẽ xóa nội dung/file; tải lại hoặc rời trang cũng mất state. Không có autosave hay cảnh báo bỏ bài chưa nộp. Khi API gửi lỗi, state vẫn được giữ nếu học viên còn ở hộp thoại; không nên mô tả mọi lỗi gửi là mất bài ngay.

**Hướng sửa:** giữ bản nháp theo người dùng/lớp/bài; văn bản có autosave, âm thanh lưu tạm với giới hạn và thời hạn rõ ràng. Cảnh báo trước khi bỏ bản nháp. Xử lý xóa dữ liệu nháp khi đổi tài khoản để tránh lộ bài trên máy dùng chung.

### A10 — P2: giới hạn thời lượng bài nói chưa được xác minh ở server

**Vị trí:** [backend nhận reportedSeconds](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/CohortWorkspaceService.java:125), [kiểm tra chữ ký đầu tệp](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/CohortWorkspaceService.java:294), [trình duyệt đo/clamp thời lượng](D:/manabihub_ljpm/frontend/src/features/cohorts/AudioRecorder.tsx:67).

Backend kiểm tra số do client khai báo, MIME, kích thước và vài byte đầu; không giải mã để đọc thời lượng thực hay xác nhận luồng chỉ có âm thanh. Gửi trực tiếp multipart có thể khai thời lượng ngắn cho tệp dài nhưng nhỏ hơn 8 MB. Ở trình duyệt, thời lượng ghi trực tiếp còn được chặn trên số hiển thị bằng Math.min(maxSeconds, ...); timer bị trì hoãn cũng không chứng minh tệp thực ngắn hơn giới hạn.

**Ảnh hưởng:** vượt định mức thời gian giáo viên phải nghe; có thể lưu tệp sai/hỏng container. Chưa có bằng chứng thực thi mã hay tải tệp vượt giới hạn dung lượng 8 MB.

**Hướng sửa:** xử lý media với giới hạn CPU/bộ nhớ/thời gian, đo thời lượng và loại luồng ở server; lưu thời lượng đã xác minh. Giữ giới hạn 8 MB và cơ chế tải riêng tư hiện có.

### A11 — P2: giáo viên có thể yêu cầu nhiều lần sửa nhưng chỉ còn một lượt

**Vị trí:** [kiểm tra khi yêu cầu sửa](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/CohortWorkspaceService.java:145), [trừ định mức theo số lần đã nộp](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/CohortWorkspaceService.java:115).

Mỗi phản hồi kiểm tra số lần đã nộp có nhỏ hơn quota hay không, không tính các yêu cầu sửa đang tồn tại. Bài chưa nộp lại cũng không giữ chỗ một lượt.

**Ví dụ giả định:** quota 3; học viên đã nộp hai bài khác nhau, mỗi bài cho phép nộp lại. Giáo viên yêu cầu sửa cả hai vì số đã dùng vẫn là 2. Học viên nộp lại bài thứ nhất thì hết quota, bài thứ hai vẫn hiện cần nộp lại nhưng API từ chối. Các con số chỉ mô tả tình huống.

Nhánh review cũng không kiểm tra membership còn ACTIVE trước khi yêu cầu sửa; học viên đã bị gỡ khỏi lớp có thể được gửi thông báo yêu cầu nộp lại dù không vào lớp được.

**Hướng sửa:** giữ chỗ lượt cho revision, hoặc thiết kế lượt sửa không tiêu hao thêm quota theo chính sách gói. Kiểm tra membership và toàn bộ khả năng nộp trước khi phát hành yêu cầu sửa; hiển thị rõ quota đã dùng/đã giữ/còn khả dụng.

## 6. Chi phí AI, tải dữ liệu và ngôn ngữ

### A12 — P2: kiểm soát lượt AI chưa chịu được yêu cầu đồng thời

**Vị trí:** [kiểm tra trước khi gọi provider](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/ai/service/impl/AiChatServiceImpl.java:107), [đếm chỉ SUCCESS](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/ai/service/impl/AiChatServiceImpl.java:210), [giới hạn AI chữa bài](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/learning/service/impl/LearningServiceImpl.java:813).

Các yêu cầu đọc số SUCCESS rồi gọi provider, ghi SUCCESS sau khi có kết quả. Không có bước đặt trước một lượt dùng theo người dùng. Nhiều yêu cầu đang bay cùng lúc đều thấy số đếm cũ và có thể vượt cả hạn mức phút/ngày. Khóa trên một writing submission giúp tránh xử lý lặp cùng bài, nhưng không chặn nhiều bài khác nhau hoặc nhiều câu chat.

**Hướng sửa:** tiêu thụ/giữ chỗ quota nguyên tử trước khi gọi provider; xác định cách hoàn lại quota khi lỗi và giới hạn số yêu cầu đang xử lý. Chat và writing cần chính sách ngân sách chung nếu chúng dùng cùng một gói chi phí. Log token là dữ liệu sử dụng, không tự tạo thành bằng chứng chi phí tiền đã thanh toán.

### A13 — P2: tải báo cáo tăng nhanh theo toàn bộ dữ liệu

**Vị trí:** [lấy mọi lớp để báo cáo](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/CohortWorkspaceService.java:191), [đọc workspace rồi đọc lại lịch sử](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/service/CohortWorkspaceService.java:197), [truy vấn toàn bộ bài của lớp](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/cohort/repository/CohortSubmissionRepository.java:12).

Không phân trang hoặc giới hạn kỳ ở backend. report gọi workspace tải toàn bộ bài/attempt rồi truy vấn lại; với mỗi thành viên và bài lại quét attempts. workspace cũng lọc lịch sử riêng cho từng người. API danh sách lớp học viên còn đọc lịch sử cả lớp trước khi lọc ra bài của chính học viên ở server.

**Ảnh hưởng dự kiến:** thời gian và bộ nhớ tăng mạnh khi có nhiều lớp, học viên và lượt sửa. Không có số đo tải hay kết quả benchmark trong lượt này, nên không kết luận hệ thống hiện đã chậm hoặc có ngưỡng chịu tải cụ thể. Việc đọc rồi lọc ở server không đồng nghĩa API đang trả bài của người khác.

**Hướng sửa:** tổng hợp trong database hoặc dùng projection không mang toàn bộ nội dung bài; truy vấn lịch sử học viên theo member ngay từ DB. Phân trang, lọc thời gian/đơn vị ở backend; dùng map/index để tránh quét lặp và bỏ đọc trùng.

### A14 — P2: mục tiêu nội bộ có thể lấy khóa khác ngôn ngữ

**Vị trí:** [chọn currentCourse](D:/manabihub_ljpm/frontend/src/features/student/pages/StudentDashboardPage.tsx:120), [khung SOFL_INTERNAL dùng chung](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/course/domain/CourseLanguageProfile.java:109).

Dashboard chọn khóa ACTIVE cùng proficiencyFramework, không đối chiếu learningLanguage. SOFL_INTERNAL được phép dùng cho Nhật, Hàn và Trung. Nếu mục tiêu chính là Hàn nội bộ nhưng khóa Trung nội bộ xuất hiện trước trong danh sách, cấp độ và lộ trình được suy ra từ khóa Trung.

**Hướng sửa:** lọc đồng thời ngôn ngữ, khung và phiên bản; xác định quy tắc chọn khi nhiều khóa cùng hợp lệ. Không gắn nhãn năng lực thực của học viên từ cấp độ của khóa đang học.

## 7. Khoảng trống sản phẩm cần nói rõ với SOFL

Đây là phạm vi chưa đủ cho vận hành trung tâm, không tự động là lỗi code:

- **Quản lý trung tâm/chi nhánh:** organizationName và branchName hiện là chuỗi nhập tự do; quyền lớp theo chủ giáo viên. Bộ lọc báo cáo theo tên không tạo thành cách ly dữ liệu hoặc phân quyền chi nhánh. Chưa có mô hình tổ chức, quản lý cơ sở, phân công nhiều giáo viên, giáo viên thay thế hoặc chuyển chủ lớp trong API mới.
- **Phân quyền gói có chi phí:** giáo viên chủ lớp có thể tăng quota/SLA; chưa có đường phê duyệt chi phí gói của trung tâm. Nếu các lượt chữa được bán có giá, cần chốt ai được thay đổi định mức và audit đơn giá/phiên bản gói.
- **Thu hồi/cập nhật năng lực:** luồng mới chỉ có PENDING/APPROVED/REJECTED và chỉ quyết định hồ sơ đang PENDING. Chưa có thu hồi riêng năng lực đã duyệt hoặc sửa một quyết định duyệt nhầm; gửi lại cùng ngôn ngữ/khung đã APPROVED bị chặn. Việc khóa toàn bộ tài khoản bằng luồng kiểm duyệt khác không thay thế quản lý từng năng lực.
- **Phạm vi giảng dạy theo cấp:** requireApprovedForCourse kiểm tra ngôn ngữ/khung, không kiểm tra mức khóa học so với credentialLevel hay hạn chứng chỉ. Cần SOFL xác định quyền giảng dạy theo cấp/phạm vi; chưa thể tuyên bố code đang thực thi chính sách này.
- **Đa ngôn ngữ chưa phủ toàn bộ:** phần mới có nhãn Hàn/Trung; phần cũ vẫn có nội dung cố định như Kanji & Từ vựng và gợi ý khóa Kanji N5 trong StudyGoalsWidget. Nội dung khóa không được dịch tự động. Xem [widget](D:/manabihub_ljpm/frontend/src/features/student/components/StudyGoalsWidget.tsx:621).
- **Báo cáo học vụ khác báo cáo tài chính:** đơn giá giờ chữa được người xem nhập trên trình duyệt; chi phí là phút giáo viên tự nhập nhân đơn giá đó. Chưa phải bảng lương được duyệt, hóa đơn, doanh thu lớp hay báo cáo lãi/lỗ. API cohort mới không ghi order/học phí khi thêm học viên. Doanh thu khóa hiện đi theo giao dịch/escrow, không chứng minh doanh thu học phí thu ngoài nền tảng.
- **Mốc kỳ và dân số thống kê:** chỉ số active learner lấy roster hiện tại, một số chỉ số chữa bài lấy cả lịch sử học viên đã rời lớp. Cần công bố định nghĩa, thêm kỳ báo cáo và lưu snapshot nếu dùng để đối chiếu cam kết hoặc thanh toán.
- **Lưu trữ media:** code lưu audio và minh chứng trên filesystem của backend, có đường dẫn tuyệt đối trong database. Cần volume bền vững, sao lưu DB và media nhất quán, kế hoạch chuyển máy và khôi phục. Chưa xác nhận các cấu hình này tồn tại trên môi trường triển khai.
- **Hai thiết bị:** có khóa user khi đăng ký thiết bị; learning lease chỉ được áp qua interceptor cho đường /api/v1/learning/**. Không nên quảng cáo mọi hoạt động trên toàn nền tảng đều chỉ chạy đồng thời ở một thiết bị. Khi chưa có lease, thao tác khóa hàng lease không khóa được một hàng chưa tồn tại; cần xác minh cuộc đua khởi tạo lease trên hai phiên. Xem [lease](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/identity/service/impl/LearningSessionLeaseServiceImpl.java:28) và [phạm vi interceptor](D:/manabihub_ljpm/backend/src/main/java/com/manabihub/learning/config/LearningWebMvcConfig.java:18). Chưa kết luận có thể học đồng thời trái phép.

## 8. Thứ tự xử lý và tiêu chí nghiệm thu đề xuất

### Đợt 1 — bảo toàn quyền và trạng thái

1. Thiết kế các nguồn quyền học độc lập, sửa A01–A04 và migration tương thích dữ liệu cũ. Bảo toàn tiến độ, chứng chỉ và bằng chứng hoàn tiền theo chính sách được thống nhất; không xóa tiến độ chỉ vì đổi nguồn cấp quyền.
2. Thống nhất giữ chỗ chứng chỉ và cập nhật quyền giáo viên, sửa A05–A06. Giữ tách biệt đình chỉ toàn bộ với từ chối một năng lực.
3. Sửa A07; tách đóng nhận bài khỏi hủy nghĩa vụ và bổ sung cách chốt báo cáo kỳ.

### Đợt 2 — bảo vệ bài làm và chi phí

4. Đồng bộ quyền nộp từ server và giữ bản nháp, sửa A08–A09.
5. Xác minh media ở server và quy tắc lượt sửa, sửa A10–A11.
6. Hạn mức AI nguyên tử và truy vấn báo cáo có giới hạn, sửa A12–A13.
7. Hoàn thiện chọn mục tiêu theo ngôn ngữ và rà nhãn cũ, sửa A14.

### Các ca cần kiểm chứng sau khi sửa

Danh sách này là yêu cầu nghiệm thu đề xuất, chưa được chạy trong lượt rà soát:

- Mua trước/sau khi vào lớp; đơn chờ rồi được cấp lớp; quyền lớp hết hạn rồi mua lại; đóng/gỡ một trong nhiều lớp; còn quyền mua nhưng rời lớp.
- Quyền mua hết hạn khi lớp còn hạn; chờ/duyệt/từ chối/hủy hoàn tiền khi đồng thời còn membership hợp lệ; hai thao tác cập nhật quyền đồng thời.
- Trùng mã giữa luồng JLPT cũ và mới; khác dạng viết của cùng mã; hai giáo viên gửi cùng lúc; xử lý hồ sơ cũ sau khi năng lực ngôn ngữ khác được duyệt.
- Đóng bài không thay đổi kết quả nộp đã chốt; thay roster không thay báo cáo kỳ đã chốt; số thiếu bài và mẫu số vẫn đối chiếu được.
- Soạn bài trước ngày bắt đầu/sau khi lớp đóng; đóng/mở hộp thoại, tải lại, đổi tài khoản và gửi lỗi khi có bản nháp.
- Tệp đúng/sai MIME, tệp hỏng, tệp có thời lượng khai khác thực, tệp vượt thời gian hoặc dung lượng; không để xử lý media treo hệ thống.
- Hai yêu cầu sửa khi chỉ còn một lượt; giáo viên yêu cầu sửa sau khi gỡ học viên; người học không bị yêu cầu thực hiện việc API cấm.
- Nhiều yêu cầu AI đồng thời không vượt quota đã định; lỗi provider có cách ghi nhận quota/chi phí rõ ràng.
- Báo cáo với dữ liệu đại diện quy mô dự kiến của SOFL; đo thời gian, số query và bộ nhớ trước khi cam kết khả năng phục vụ.
- Mục tiêu Hàn nội bộ không lấy khóa Trung nội bộ; người học nhìn thấy đúng nhãn ngôn ngữ.

## 9. Cách mô tả hiện trạng khi trao đổi với khách

Có thể trình bày phần đang có code: hồ sơ năng lực theo ngôn ngữ, lớp theo đợt, bài viết/bài nói, chữa bài có lịch sử, nhắc học và bảng theo dõi SLA. Nên demo bằng dữ liệu minh họa có nhãn rõ ràng.

Trước khi ký cam kết vận hành, cần hoàn tất các điểm P1, kiểm chứng trên môi trường thử và có kịch bản khôi phục. Các lợi ích tài chính phải đo trong pilot: thời gian chữa bài, tỷ lệ nộp, tỷ lệ quay lại học, học phí thực thu và chi phí thực trả. Code và dự báo không thay thế những số liệu đó.
