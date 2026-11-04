# Bản phát hành SOFL trên AWS — 01/10/2026

## Bản mã và gói triển khai

- Nhánh đã đẩy: `codex/sofl-multilingual-platform`.
- Commit mã: `f8375031c73d` (tích hợp `origin/develop` tại `6f68d0f7`).
- Backend: Java 21, Maven `clean package -DskipTests` thành công; không chạy test.
- Frontend: `npm run build` thành công, 2.092 module. Có cảnh báo dash.js và kích thước chunk.
- ZIP: `backend/target/manabihub-elastic-beanstalk.zip`, 173.998.512 byte.
- SHA-256 ZIP: `140f8716efa0b37aafaf801732fc1b928fa8436cf4ef19cf44c1aff33d1ef406`.
- Nhãn phát hành: `manabihub-sofl-20261001-f8375031`.
- Gói gồm JAR, Procfile, cấu hình nginx, hook lưu âm thanh, FFmpeg Linux LGPL và giấy phép. Binary không đưa vào Git.
- Migration SOFL: V085–V091. Các migration V079–V084 của develop được giữ nguyên.

## Sao lưu trước triển khai

- RDS: `manabihub-db`, khu vực `us-east-1`.
- Snapshot: `manabihub-pre-sofl-20261001`, tạo lúc 12:39 (UTC+07:00).
- AWS Console xác nhận **Available / Completed**, mã hóa bật, kích thước đầy đủ 3,4 GiB.
- Snapshot chỉ sao lưu dữ liệu PostgreSQL, không chứa các tệp lưu trên EC2.

## Đích triển khai và trạng thái

- Elastic Beanstalk: `Manabihub-backend-env`, ID `e-ubbrifdkj2`, khu vực `ap-southeast-2`.
- Bản trước cập nhật: `manabihub-v1-19`, Health **Ok**.
- Frontend: Amplify app `d1sbjmyazduh3v`, nhánh `develop`, deployment trước cập nhật **217**, commit `6f68d0f7`.
- Người dùng đã tải và triển khai ZIP vào môi trường hiện có. AWS Console xác nhận **Environment update completed successfully** lúc 12:54:30 (UTC+07:00), bản đang chạy `manabihub-sofl-20261001-f8375031`, Health **Ok**.
- Endpoint `/actuator/health` trả `status: UP` sau triển khai.
- Đã đẩy `f8375031` vào nhánh `develop` bằng fast-forward. Amplify deployment **218** xác nhận **Deployed / SUCCEED**, cùng commit `f8375031c73d735a4bf8f85c68e34aa0e610dc99`; bắt đầu lúc 12:58 (UTC+07:00), tổng thời gian 2 phút 44 giây (build 2 phút 35 giây, deploy 8 giây).
- Frontend đang phục vụ tại [ManabiHub](https://develop.d1sbjmyazduh3v.amplifyapp.com/).
- Commit bổ sung biên bản chỉ sửa tài liệu, dùng `[skip-cd]` để không build lại phần mềm đang chạy. Cơ chế này được mô tả trong [hướng dẫn AWS Amplify](https://docs.aws.amazon.com/amplify/latest/userguide/edit-build-settings.html#skip-build-for-commit).

## Lưu ý vận hành

- Âm thanh production mặc định tại `/var/app/manabihub-storage/cohort-audio`, giữ qua cập nhật ứng dụng trên cùng EC2; thay EC2 cần kho bền vững và bản sao lưu tệp.
- Khi backend khởi động, Flyway tự áp migration còn thiếu. Chỉ ghi nhận hoàn tất khi AWS hiển thị bản mới, Health Ok và không có lỗi khởi động/migration.
- Build thành công chưa xác nhận toàn bộ nghiệp vụ bằng tài khoản thật. Không thực hiện giao dịch tiền thật trong lượt này.
