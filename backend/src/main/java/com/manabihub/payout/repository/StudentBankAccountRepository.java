package com.manabihub.payout.repository;

import com.manabihub.payout.entity.StudentBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface StudentBankAccountRepository extends JpaRepository<StudentBankAccount, UUID> {
    Optional<StudentBankAccount> findByIdAndStudentId(UUID id, UUID studentId);
    Optional<StudentBankAccount> findByStudentIdAndAccountFingerprint(
            UUID studentId,
            String accountFingerprint
    );
    List<StudentBankAccount> findByStudentIdAndOwnershipVerifiedTrueOrderByCreatedAtDesc(UUID studentId);
}
