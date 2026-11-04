package com.manabihub.audit.service.impl;

import com.manabihub.audit.dto.AuditLogFilterDto;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MHB-024: danh sach trang thai cho phep sap xep la dung, cai sai la thu quay ve
 * khi moi khoa deu bi loai. Kiem tra o muc Pageable that su duoc chuyen xuong
 * repository, vi do la thu quyet dinh thu tu hang tra ve.
 */
@ExtendWith(MockitoExtension.class)
class AuditLogSortingTest {

    @Mock private AuditLogRepository auditLogRepository;
    @Mock private InternalAdminAccountRepository internalAdminAccountRepository;
    @Mock private AppUserRepository appUserRepository;

    @InjectMocks private AuditLogServiceImpl auditLogService;

    @Captor private ArgumentCaptor<Pageable> pageableCaptor;

    @SuppressWarnings("unchecked")
    private List<Sort.Order> sortOrdersSentToRepository(Sort requestedSort) {
        when(auditLogRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(Page.empty());

        auditLogService.getAuditLogs(
                AuditLogFilterDto.builder().build(),
                PageRequest.of(0, 20, requestedSort));

        verify(auditLogRepository).findAll(any(Specification.class), pageableCaptor.capture());
        return pageableCaptor.getValue().getSort().stream().toList();
    }

    @Test
    void unknownSortKeyFallsBackToNewestFirst() {
        List<Sort.Order> orders = sortOrdersSentToRepository(Sort.by(Sort.Order.desc("metadata")));

        assertEquals(2, orders.size());
        assertEquals("createdAt", orders.get(0).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(0).getDirection());
        assertEquals("id", orders.get(1).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(1).getDirection());
    }

    @Test
    void allowedSortKeyIsKeptWithIdAsTieBreaker() {
        List<Sort.Order> orders = sortOrdersSentToRepository(Sort.by(Sort.Order.asc("action")));

        assertEquals(2, orders.size());
        assertEquals("action", orders.get(0).getProperty());
        assertEquals(Sort.Direction.ASC, orders.get(0).getDirection());
        assertEquals("id", orders.get(1).getProperty());
        assertEquals(Sort.Direction.DESC, orders.get(1).getDirection());
    }
}