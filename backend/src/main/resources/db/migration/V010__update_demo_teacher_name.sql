-- V010__update_demo_teacher_name.sql
-- Update the demo teacher's name to match the CCCD for UC-22 KYC testing

UPDATE app_users 
SET full_name = 'THÂN VĂN THÀNH' 
WHERE id = 'd0000000-0000-0000-0000-000000000003';
