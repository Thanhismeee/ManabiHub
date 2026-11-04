package com.manabihub.finaltest.repository;

import com.manabihub.finaltest.entity.FinalTest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FinalTestRepository extends JpaRepository<FinalTest, UUID> {
    Optional<FinalTest> findByCourseId(UUID courseId);
}
