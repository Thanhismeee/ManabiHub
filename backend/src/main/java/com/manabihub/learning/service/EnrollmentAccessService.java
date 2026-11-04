package com.manabihub.learning.service;

import com.manabihub.cohort.domain.*;
import com.manabihub.course.entity.Course;
import com.manabihub.identity.entity.StudentProfile;
import com.manabihub.identity.repository.StudentProfileRepository;
import com.manabihub.learning.entity.*;
import com.manabihub.learning.enums.*;
import com.manabihub.learning.repository.*;
import com.manabihub.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class EnrollmentAccessService {
    private final StudentProfileRepository students;
    private final EnrollmentRepository enrollments;
    private final EnrollmentAccessGrantRepository grants;
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    public boolean purchaseBlocked(StudentProfile student, Course course) {
        students.findForUpdate(student.getId()).orElseThrow();
        Enrollment enrollment = enrollments.findByStudentIdAndCourseIdForUpdate(student.getId(), course.getId()).orElse(null);
        if (enrollment == null) return false;
        Instant now = Instant.now();
        return grants.findByEnrollment_Id(enrollment.getId()).stream()
                .anyMatch(g -> g.getAccessSource() == EnrollmentAccessSource.PURCHASE
                        && (g.activeAt(now) || g.getStatus() == EnrollmentStatus.REFUND_PENDING || g.getStatus() == EnrollmentStatus.REVOKED));
    }

    public void completePurchase(StudentProfile student, Course course, UUID orderItemId) {
        Enrollment enrollment = locked(student, course);
        EnrollmentAccessGrant grant = grants.findByAccessSourceAndSourceReferenceId(EnrollmentAccessSource.PURCHASE, orderItemId).orElse(null);
        if (grant != null && !grant.getEnrollment().getId().equals(enrollment.getId())) {
            throw new IllegalStateException("Order item access belongs to a different enrollment");
        }
        if (grant == null) {
            grant = new EnrollmentAccessGrant();
            grant.setEnrollment(enrollment); grant.setAccessSource(EnrollmentAccessSource.PURCHASE);
            grant.setSourceReferenceId(orderItemId); grant.setGrantedAt(Instant.now());
            grant.setExpiresAt(course.resolveEnrollmentExpiry(grant.getGrantedAt()));
            grant.setStatus(EnrollmentStatus.ACTIVE); grants.saveAndFlush(grant);
        }
        // A renewal changes access, not the learner's earned certificates or historical progress.
        refresh(enrollment);
    }

    public void synchronizeCohorts(StudentProfile student, Course course, List<CohortMember> memberships) {
        Enrollment enrollment = locked(student, course);
        List<EnrollmentAccessGrant> existing = grants.findByEnrollment_Id(enrollment.getId());
        Map<UUID, CohortMember> allowed = new HashMap<>();
        for (CohortMember member : memberships) {
            LearningCohort cohort = member.getCohort();
            if (member.getStatus() == CohortMemberStatus.ACTIVE
                    && (cohort.getStatus() == CohortStatus.ACTIVE || cohort.getStatus() == CohortStatus.PLANNED)) allowed.put(member.getId(), member);
        }
        for (EnrollmentAccessGrant grant : existing) {
            if (grant.getAccessSource() == EnrollmentAccessSource.SOFL_COHORT && !allowed.containsKey(grant.getSourceReferenceId())) {
                grant.setStatus(EnrollmentStatus.REVOKED); grants.save(grant);
            }
        }
        for (CohortMember member : allowed.values()) {
            EnrollmentAccessGrant grant = existing.stream().filter(g -> g.getAccessSource() == EnrollmentAccessSource.SOFL_COHORT
                    && g.getSourceReferenceId().equals(member.getId())).findFirst().orElseGet(EnrollmentAccessGrant::new);
            grant.setEnrollment(enrollment); grant.setAccessSource(EnrollmentAccessSource.SOFL_COHORT);
            grant.setSourceReferenceId(member.getId()); grant.setCohortId(member.getCohort().getId());
            grant.setGrantedAt(member.getJoinedAt()); grant.setStatus(EnrollmentStatus.ACTIVE);
            grant.setExpiresAt(member.getCohort().getEndDate().plusDays(1).atStartOfDay(VN).toInstant());
            grants.save(grant);
        }
        grants.flush(); refresh(enrollment);
    }

    public void updatePurchaseStatus(UUID studentId, UUID courseId, UUID orderItemId, EnrollmentStatus status) {
        students.findForUpdate(studentId).orElseThrow();
        Enrollment enrollment = enrollments.findByStudentIdAndCourseIdForUpdate(studentId, courseId).orElseThrow();
        EnrollmentAccessGrant grant = grants.findByAccessSourceAndSourceReferenceId(EnrollmentAccessSource.PURCHASE, orderItemId)
                .filter(g -> g.getEnrollment().getId().equals(enrollment.getId()))
                .orElseThrow(() -> new BusinessException("ENROLLMENT_ACCESS_RECONCILIATION",
                        "Quyền học của đơn hàng cũ chưa được đối soát. Liên hệ quản trị trước khi xử lý hoàn tiền.", HttpStatus.CONFLICT));
        // Cancelling/rejecting a refund must not resurrect an already refunded purchase.
        if (status != EnrollmentStatus.ACTIVE || grant.getStatus() == EnrollmentStatus.REFUND_PENDING) {
            grant.setStatus(status); grants.saveAndFlush(grant);
        }
        refresh(enrollment);
    }

    private Enrollment locked(StudentProfile student, Course course) {
        StudentProfile current = students.findForUpdate(student.getId()).orElseThrow();
        return enrollments.findByStudentIdAndCourseIdForUpdate(current.getId(), course.getId())
                .orElseGet(() -> enrollments.saveAndFlush(Enrollment.builder().student(current).course(course).build()));
    }

    private void refresh(Enrollment enrollment) {
        Instant now = Instant.now();
        List<EnrollmentAccessGrant> all = grants.findByEnrollment_Id(enrollment.getId());
        List<EnrollmentAccessGrant> active = all.stream().filter(g -> g.activeAt(now)).toList();
        boolean pending = all.stream().anyMatch(g -> g.getAccessSource() == EnrollmentAccessSource.PURCHASE && g.getStatus() == EnrollmentStatus.REFUND_PENDING);
        boolean revoked = all.stream().anyMatch(g -> g.getAccessSource() == EnrollmentAccessSource.PURCHASE && g.getStatus() == EnrollmentStatus.REVOKED);
        if (revoked) enrollment.setStatus(EnrollmentStatus.REVOKED);
        else if (pending) enrollment.setStatus(EnrollmentStatus.REFUND_PENDING);
        else if (!active.isEmpty()) enrollment.setStatus(enrollment.getCompletedAt() == null ? EnrollmentStatus.ACTIVE : EnrollmentStatus.COMPLETED);
        else enrollment.setStatus(all.stream().anyMatch(g -> g.getAccessSource() == EnrollmentAccessSource.PURCHASE && g.getStatus() == EnrollmentStatus.REFUNDED)
                ? EnrollmentStatus.REFUNDED : EnrollmentStatus.EXPIRED);
        if (active.isEmpty()) { enrollment.setExpiresAt(now); enrollment.setSourceCohortId(null); }
        else {
            enrollment.setExpiresAt(active.stream().anyMatch(g -> g.getExpiresAt() == null) ? null
                    : active.stream().map(EnrollmentAccessGrant::getExpiresAt).max(Comparator.naturalOrder()).orElseThrow());
            EnrollmentAccessGrant preferred = active.stream().filter(g -> g.getAccessSource() == EnrollmentAccessSource.PURCHASE).findFirst()
                    .orElseGet(() -> active.stream().max(Comparator.comparing(g -> g.getExpiresAt() == null ? Instant.MAX : g.getExpiresAt())).orElseThrow());
            enrollment.setAccessSource(preferred.getAccessSource()); enrollment.setSourceCohortId(preferred.getCohortId());
        }
        enrollments.save(enrollment);
    }
}
