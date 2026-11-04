package com.manabihub.cohort.service;

import com.manabihub.cohort.dto.CohortWorkspaceDto.*;
import com.manabihub.cohort.domain.CohortStatus;
import com.manabihub.course.enums.LearningLanguage;
import com.manabihub.common.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.sql.ResultSet;
import java.time.LocalDate;
import java.util.*;

/** Aggregate metadata in PostgreSQL; report requests never load submission bodies or audio. */
@Service @RequiredArgsConstructor @Transactional(readOnly = true)
public class CohortReportQueryService {
    private final NamedParameterJdbcTemplate jdbc;
    private static final String SCOPE = """
        FROM learning_cohorts c JOIN teacher_profiles tp ON tp.id=c.owner_teacher_id
        WHERE (CAST(:owner AS uuid) IS NULL OR tp.user_id=CAST(:owner AS uuid))
        """;
    private static final String FILTER = """
        AND (CAST(:organization AS text) IS NULL OR c.organization_name=:organization)
        AND (CAST(:branch AS text) IS NULL OR c.branch_name=:branch)
        AND (CAST(:fromDate AS date) IS NULL OR c.end_date>=CAST(:fromDate AS date))
        AND (CAST(:toDate AS date) IS NULL OR c.start_date<=CAST(:toDate AS date))
        """;

    public ReportPage reports(UUID owner, String organization, String branch, LocalDate from, LocalDate to, int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100 || (from != null && to != null && from.isAfter(to)))
            throw new BusinessException("COHORT_REPORT_FILTER", "Bộ lọc hoặc trang báo cáo không hợp lệ.", HttpStatus.BAD_REQUEST);
        MapSqlParameterSource params = new MapSqlParameterSource().addValue("owner", owner).addValue("organization", clean(organization))
                .addValue("branch", clean(branch)).addValue("fromDate", from).addValue("toDate", to).addValue("limit", size).addValue("offset", page * size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + SCOPE + FILTER, params, Long.class);
        List<String> organizations = jdbc.queryForList("SELECT DISTINCT c.organization_name " + SCOPE + " ORDER BY c.organization_name", params, String.class);
        List<String> branches = jdbc.queryForList("SELECT DISTINCT c.branch_name " + SCOPE
                + " AND c.branch_name IS NOT NULL AND (CAST(:organization AS text) IS NULL OR c.organization_name=:organization) ORDER BY c.branch_name", params, String.class);
        String sql = "WITH selected AS (SELECT c.*,tp.display_name AS teacher_name " + SCOPE + FILTER
                + " ORDER BY c.start_date DESC,c.id LIMIT :limit OFFSET :offset), " + """
        latest AS (
            SELECT DISTINCT ON(s.member_id,s.assignment_id) s.member_id,s.assignment_id,s.reviewed_at,s.revision_requested
            FROM cohort_submissions s JOIN cohort_assignments a ON a.id=s.assignment_id JOIN selected c ON c.id=a.cohort_id
            ORDER BY s.member_id,s.assignment_id,s.attempt DESC
        ), obligations AS (
            SELECT c.id AS cohort_id,m.id AS member_id,
                COUNT(a.id) FILTER(WHERE a.due_at<=NOW() AND a.due_at>=m.joined_at) AS expected,
                COUNT(a.id) FILTER(WHERE a.due_at<=NOW() AND a.due_at>=m.joined_at AND l.member_id IS NOT NULL) AS submitted,
                COUNT(a.id) FILTER(WHERE a.due_at<=NOW() AND a.due_at>=m.joined_at AND l.member_id IS NULL) AS missing,
                COUNT(a.id) FILTER(WHERE NOT a.closed AND l.reviewed_at IS NOT NULL AND l.revision_requested) AS revisions
            FROM selected c JOIN cohort_members m ON m.cohort_id=c.id AND m.status='ACTIVE'
            LEFT JOIN cohort_assignments a ON a.cohort_id=c.id
            LEFT JOIN latest l ON l.member_id=m.id AND l.assignment_id=a.id
            GROUP BY c.id,m.id
        ), learner_counts AS (
            SELECT cohort_id,COUNT(*) AS students,SUM(expected) AS expected,SUM(submitted) AS submitted,
                SUM(missing) AS missing,COUNT(*) FILTER(WHERE missing+revisions>0) AS risk
            FROM obligations GROUP BY cohort_id
        ), work AS (
            SELECT c.id AS cohort_id,COUNT(s.id) AS used,
                COUNT(s.id) FILTER(WHERE s.reviewed_at IS NULL) AS pending,
                COUNT(s.id) FILTER(WHERE s.reviewed_at IS NULL AND s.review_due_at<NOW()) AS overdue,
                COUNT(s.id) FILTER(WHERE s.reviewed_at IS NOT NULL) AS reviewed,
                COUNT(s.id) FILTER(WHERE s.reviewed_at<=s.review_due_at) AS ontime,
                COALESCE(SUM(s.review_minutes),0) AS minutes
            FROM selected c LEFT JOIN cohort_assignments a ON a.cohort_id=c.id LEFT JOIN cohort_submissions s ON s.assignment_id=a.id GROUP BY c.id
        ), grades AS (
            SELECT cohort_id,AVG(score)::double precision AS average FROM (
                SELECT DISTINCT ON(s.member_id,s.assignment_id) a.cohort_id,s.score
                FROM cohort_submissions s JOIN cohort_assignments a ON a.id=s.assignment_id JOIN selected c ON c.id=a.cohort_id
                WHERE s.score IS NOT NULL ORDER BY s.member_id,s.assignment_id,s.attempt DESC
            ) scores GROUP BY cohort_id
        )
        SELECT c.*,co.learning_language,
            (SELECT COUNT(*) FROM cohort_assignments a WHERE a.cohort_id=c.id) AS assignments,
            COALESCE(lc.students,0) AS students,COALESCE(lc.expected,0) AS expected,COALESCE(lc.submitted,0) AS submitted,
            COALESCE(lc.missing,0) AS missing,COALESCE(lc.risk,0) AS risk,w.used,w.pending,w.overdue,w.reviewed,w.ontime,w.minutes,g.average
        FROM selected c JOIN courses co ON co.id=c.course_id LEFT JOIN learner_counts lc ON lc.cohort_id=c.id
        JOIN work w ON w.cohort_id=c.id LEFT JOIN grades g ON g.cohort_id=c.id ORDER BY c.start_date DESC,c.id
        """;
        List<Report> items = jdbc.query(sql, params, (r, row) -> map(r));
        return new ReportPage(items, page, size, total == null ? 0 : total, organizations, branches);
    }

    public List<ClassSummary> studentClasses(UUID userId) {
        return jdbc.query("""
            SELECT c.*,m.id AS member_id,
                (SELECT COUNT(*) FROM cohort_assignments a WHERE a.cohort_id=c.id) AS assignments,
                (SELECT COUNT(*) FROM cohort_submissions s WHERE s.member_id=m.id) AS used,
                CASE WHEN c.status IN ('ACTIVE','PLANNED') AND (NOW() AT TIME ZONE 'Asia/Ho_Chi_Minh')::date BETWEEN c.start_date AND c.end_date THEN (
                    SELECT COUNT(*) FROM (
                        SELECT DISTINCT ON(s.assignment_id) s.assignment_id,s.reviewed_at,s.revision_requested
                        FROM cohort_submissions s WHERE s.member_id=m.id ORDER BY s.assignment_id,s.attempt DESC
                    ) latest JOIN cohort_assignments a ON a.id=latest.assignment_id
                    WHERE latest.reviewed_at IS NOT NULL AND latest.revision_requested AND NOT a.closed AND a.accept_until>=NOW()
                ) ELSE 0 END AS reserved
            FROM learning_cohorts c JOIN cohort_members m ON m.cohort_id=c.id JOIN student_profiles sp ON sp.id=m.student_id
            WHERE sp.user_id=:userId AND m.status='ACTIVE' ORDER BY c.start_date DESC,c.id
            """, new MapSqlParameterSource("userId", userId), (r, row) -> new ClassSummary(r.getObject("id", UUID.class),r.getString("name"),r.getString("code"),
                r.getObject("course_id", UUID.class),CohortStatus.valueOf(r.getString("status")),r.getObject("start_date", LocalDate.class),r.getObject("end_date", LocalDate.class),
                r.getInt("assignments"),Math.max(0,r.getInt("feedback_quota_per_student")-r.getInt("used")-r.getInt("reserved"))));
    }
    private Report map(ResultSet r) throws java.sql.SQLException {
        return new Report(r.getObject("id",UUID.class),r.getString("name"),r.getString("code"),r.getString("organization_name"),r.getString("branch_name"),
            r.getString("teacher_name"),LearningLanguage.valueOf(r.getString("learning_language")),r.getInt("students"),r.getInt("assignments"),r.getInt("expected"),r.getInt("submitted"),
            r.getInt("missing"),r.getInt("risk"),r.getInt("pending"),r.getInt("overdue"),r.getInt("reviewed"),r.getInt("ontime"),r.getInt("minutes"),r.getInt("used"),
            r.getInt("feedback_quota_per_student"),r.getObject("average", Double.class));
    }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
