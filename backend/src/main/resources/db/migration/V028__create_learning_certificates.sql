-- MHB-29: one immutable learning certificate per enrollment.

CREATE TABLE learning_certificates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    enrollment_id UUID NOT NULL,
    certificate_number VARCHAR(64) NOT NULL,
    student_name VARCHAR(255) NOT NULL,
    course_title VARCHAR(255) NOT NULL,
    eligibility_snapshot JSONB NOT NULL,
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_learning_certificates_enrollment
        FOREIGN KEY (enrollment_id) REFERENCES enrollments (id) ON DELETE CASCADE,
    CONSTRAINT uq_learning_certificates_enrollment UNIQUE (enrollment_id),
    CONSTRAINT uq_learning_certificates_number UNIQUE (certificate_number)
);
