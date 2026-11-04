package com.manabihub.learning.service.impl;

import com.manabihub.learning.enums.LessonProgressStatus;
import com.manabihub.course.repository.LessonBlockRepository;
import com.manabihub.learning.repository.LessonBlockProgressRepository;
import com.manabihub.learning.service.LearningProgressDomainService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LearningProgressDomainServiceImpl implements LearningProgressDomainService {

    private final LessonBlockRepository lessonBlockRepository;
    private final LessonBlockProgressRepository lessonBlockProgressRepository;

    @Override
    public ProgressResult calculateProgress(UUID courseId, UUID enrollmentId) {
        int total = lessonBlockRepository.countByCourse_Id(courseId);
        int completed = lessonBlockProgressRepository.countByEnrollmentIdAndStatus(enrollmentId, LessonProgressStatus.COMPLETED);
        
        double percent = 0.0;
        if (total > 0) {
            percent = Math.min(100.0, Math.round((completed * 10_000.0) / total) / 100.0);
        }
        
        return new ProgressResult(completed, total, percent);
    }
}
