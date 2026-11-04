INSERT INTO student_profiles (
    id,
    user_id,
    display_name,
    jlpt_goal,
    created_at
)
VALUES (
           gen_random_uuid(),
           '33333333-3333-3333-3333-333333333333',
           'Demo Sensei',
           'N1',
           now()
       );