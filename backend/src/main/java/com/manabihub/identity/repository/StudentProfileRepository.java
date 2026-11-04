package com.manabihub.identity.repository;

import com.manabihub.identity.entity.StudentProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {
    Optional<StudentProfile> findByUser_Id(UUID userId);

    Optional<StudentProfile> findByUser_EmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StudentProfile s where s.id = :id")
    Optional<StudentProfile> findForUpdate(@Param("id") UUID id);

    boolean existsByUserId(UUID userId);

    @Query("select distinct student from StudentProfile student left join fetch student.user "
            + "where student.id in :ids")
    List<StudentProfile> findAllWithUserByIdIn(@Param("ids") Collection<UUID> ids);
}
