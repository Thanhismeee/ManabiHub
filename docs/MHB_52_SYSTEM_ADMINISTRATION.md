# MHB-52 — Cấu hình hệ thống và phân quyền nội bộ

## Mục đích nghiệp vụ

MHB-52 cho phép `SYSTEM_ADMIN` thay đổi cấu hình vận hành và gán đúng một vai
trò cho mỗi tài khoản quản trị nội bộ. `COURSE_MANAGER` và
`FINANCE_MANAGER` không được đọc hoặc sửa hai khu vực này.

Đây không phải màn hình quản lý secret. API không nhận hoặc trả API key, mật
khẩu, `JWT_SECRET`, `PAYOUT_SECURITY_SECRET` hay credential của nhà cung cấp.
Các secret vẫn phải được quản lý bằng biến môi trường/deployment secret store.

## Luồng chính

### Cập nhật cấu hình

1. Frontend đọc danh sách key được backend hỗ trợ.
2. System Admin sửa một giá trị và bắt buộc nhập lý do.
3. Backend đọc lại vai trò hiện tại từ database.
4. Backend khóa dòng cấu hình, chuẩn hóa và kiểm tra range/type.
5. Nếu giá trị không đổi, request trả thành công nhưng không tạo audit rác.
6. Nếu có thay đổi, backend lưu `updated_by` và một audit log có before/after.

### Đổi vai trò nội bộ

1. System Admin chọn một tài khoản `ACTIVE` khác và vai trò mới.
2. Backend khóa tài khoản đích.
3. Chỉ `SYSTEM_ADMIN`, `COURSE_MANAGER`, `FINANCE_MANAGER` được chấp nhận.
4. Không cho tự đổi vai trò và không cho loại bỏ System Admin cuối cùng.
5. Database bảo đảm một tài khoản chỉ có một dòng trong
   `internal_admin_roles`.
6. JWT cũ của tài khoản đích bị từ chối ở request admin tiếp theo; người dùng
   phải đăng nhập lại.

## API

| Method | Endpoint | Mục đích |
| --- | --- | --- |
| `GET` | `/api/v1/admin/system-settings` | Danh sách cấu hình hỗ trợ |
| `PUT` | `/api/v1/admin/system-settings/{key}` | Cập nhật một cấu hình |
| `GET` | `/api/v1/admin/internal-accounts` | Danh sách tài khoản nội bộ |
| `PATCH` | `/api/v1/admin/internal-accounts/{id}/role` | Đổi vai trò |

Hai API ghi đều yêu cầu `reason` dài tối đa 500 ký tự. Mọi endpoint đều yêu
cầu JWT có `ROLE_SYSTEM_ADMIN` và vai trò live trong database cũng phải là
`SYSTEM_ADMIN`.

## Cấu hình và giới hạn

| Key | Validation | Consumer hiện tại |
| --- | --- | --- |
| `COMMISSION_RATE` | `0..1` | Canonical config cho finance stories |
| `COURSE_PRICE_FLOOR` | VND nguyên, `0..10 tỷ` | Tạo course draft |
| `AI_SUPPORT_PRICE_FLOOR` | VND nguyên, `0..10 tỷ` | AI eligibility |
| `REFUND_WINDOW_DAYS` | `0..365` | Refund story |
| `REFUND_PROGRESS_LIMIT_PERCENT` | `0..100` | Refund story |
| `ESCROW_HOLDING_DAYS` | `0..365` | Escrow clearing story |
| `PAYOUT_THRESHOLD` | VND nguyên, `0..10 tỷ` | Teacher withdrawal |
| `AI_ENABLED` | boolean | AI eligibility |
| `AI_WRITING_ENABLED` | boolean | AI writing |
| `AI_CHATBOT_ENABLED` | boolean | AI chat |
| `ADMIN_LOCKOUT_MAX_ATTEMPTS` | `3..20` | Admin login |
| `ADMIN_LOCKOUT_DURATION_MINUTES` | `1..1440` | Admin login |
| `COURSE_MIN_LEARNING_GOALS` | `1..20` | Tạo course draft |
| `COURSE_MAX_LEARNING_GOAL_LENGTH` | `20..1000` | Tạo course draft |

Các story refund/commission/escrow phải đọc các key canonical này khi được
merge. Thay đổi cấu hình chỉ tác động quyết định mới; không tính lại dữ liệu
đã phát sinh.

## Kiến trúc và vị trí code

- Controller:
  `backend/src/main/java/com/manabihub/systemconfig/controller/SystemAdministrationController.java`
- Nghiệp vụ và audit:
  `backend/src/main/java/com/manabihub/systemconfig/service/impl/SystemAdministrationServiceImpl.java`
- Validation:
  `backend/src/main/java/com/manabihub/systemconfig/service/SystemSettingValidator.java`
- Typed reader cho module khác:
  `backend/src/main/java/com/manabihub/systemconfig/service/SystemSettingValueService.java`
- Chặn JWT admin cũ:
  `backend/src/main/java/com/manabihub/security/config/InternalAdminRoleFilter.java`
- UI:
  `frontend/src/features/system-administration`

Migration `V042__add_system_administration_controls.sql` seed các key còn thiếu
và tạo unique index cho một-role-mỗi-admin. Thứ tự triển khai bắt buộc là
`V038` (certificate claims), `V039` (escrow), `V040` (payout),
`V041` (review), rồi `V042` (system administration).

## Test và bằng chứng

- Unit validation cho number/boolean/range và key ngoài allowlist.
- Service test cho audit before/after, invalid input, self-role và live role.
- Web MVC test cho `SYSTEM_ADMIN`, cross-role và anonymous access.
- Filter test cho JWT khớp/sai role live.
- PostgreSQL integration chạy Flyway, cập nhật setting, đổi role thật và kiểm
  tra hai audit log.
- Frontend test cho loading/success/error, lý do audit và privacy.
- Chạy full backend regression, frontend test/lint/production build trước khi
  chuyển Code Review.

## Deploy và rollback

Không có biến môi trường mới. Deploy backend trước frontend để route mới không
gọi API chưa tồn tại.

Nếu cần rollback:

1. rollback frontend để ẩn hai route;
2. rollback backend;
3. giữ nguyên setting/audit/role data và unique index.

Không xóa audit log hoặc giá trị cấu hình khi rollback vì đây là dữ liệu kiểm
soát vận hành.
