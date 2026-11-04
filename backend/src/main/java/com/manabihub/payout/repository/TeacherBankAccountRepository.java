package com.manabihub.payout.repository;

import com.manabihub.payout.entity.TeacherBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TeacherBankAccountRepository extends JpaRepository<TeacherBankAccount, UUID> {
    
    List<TeacherBankAccount> findByTeacherIdOrderByCreatedAtDesc(UUID teacherId);
    
    Optional<TeacherBankAccount> findByTeacherIdAndAccountFingerprint(
            UUID teacherId,
            String accountFingerprint
    );

    Optional<TeacherBankAccount> findByIdAndTeacherId(UUID id, UUID teacherId);
}
