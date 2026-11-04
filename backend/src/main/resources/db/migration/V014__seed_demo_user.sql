INSERT INTO app_users (
    id,
    email,
    full_name,
    avatar_url,
    phone_number,
    provider,
    provider_user_id,
    user_status,
    last_login_at,
    created_at,
    updated_at
)
VALUES (
           '11111111-1111-1111-1111-111111111111',
           'student.demo@manabihub.com',
           'Demo Student',
           NULL,
           '0987654321',
           'LOCAL',
           'demo-student',
           'ACTIVE',
           NOW(),
           NOW(),
           NOW()
       );
INSERT INTO student_profiles (
    id,
    user_id,
    display_name,
    jlpt_goal,
    created_at,
    updated_at
)
VALUES (
           '22222222-2222-2222-2222-222222222222',
           '11111111-1111-1111-1111-111111111111',
           'Demo Student',
           'N2',
           NOW(),
           NOW()
       );


INSERT INTO app_users (
    id,
    email,
    full_name,
    avatar_url,
    phone_number,
    provider,
    provider_user_id,
    user_status,
    last_login_at,
    created_at,
    updated_at
)
VALUES (
           '33333333-3333-3333-3333-333333333333',
           'teacher.demo@manabihub.com',
           'Demo Teacher',
           NULL,
           '0912345678',
           'LOCAL',
           'demo-teacher',
           'ACTIVE',
           NOW(),
           NOW(),
           NOW()
       );
INSERT INTO teacher_profiles (
    id,
    user_id,
    display_name,
    bio,
    kyc_status,
    can_publish_course,
    created_at,
    updated_at
)
VALUES (
           '44444444-4444-4444-4444-444444444444',
           '33333333-3333-3333-3333-333333333333',
           'Demo Sensei',
           'Japanese Teacher',
           'APPROVED',
           TRUE,
           NOW(),
           NOW()
       );