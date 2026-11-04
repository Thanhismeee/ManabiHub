package com.manabihub.audit.service.impl;

import com.manabihub.audit.dto.AuditLogDetailDto;
import com.manabihub.audit.dto.AuditLogDto;
import com.manabihub.audit.dto.AuditLogFilterDto;
import com.manabihub.audit.entity.AuditLog;
import com.manabihub.audit.repository.AuditLogRepository;
import com.manabihub.audit.repository.AuditLogSpecification;
import com.manabihub.audit.service.AuditLogService;
import com.manabihub.common.exception.BusinessException;
import com.manabihub.common.response.PageResponse;
import com.manabihub.identity.entity.AppUser;
import com.manabihub.identity.entity.InternalAdminAccount;
import com.manabihub.identity.repository.AppUserRepository;
import com.manabihub.identity.repository.InternalAdminAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository auditLogRepository;
    private final InternalAdminAccountRepository internalAdminAccountRepository;
    private final AppUserRepository appUserRepository;

    @Override
    @Transactional
    public void logUserAction(
            UUID userId,
            String roleCode,
            String action,
            String targetType,
            UUID targetId,
            Map<String, Object> beforeValue,
            Map<String, Object> afterValue,
            Map<String, Object> metadata
    ) {

        AuditLog auditLog = AuditLog.builder()
                .actorType("USER")
                .actorUserId(userId)
                .actorRoleCode(roleCode)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .metadata(metadata)
                .build();

        auditLogRepository.save(auditLog);
    }

    @Override
    @Transactional
    public void logAdminAction(
            UUID adminId,
            String roleCode,
            String action,
            String targetType,
            UUID targetId,
            Map<String, Object> beforeValue,
            Map<String, Object> afterValue,
            Map<String, Object> metadata
    ) {

        AuditLog auditLog = AuditLog.builder()
                .actorType("INTERNAL_ADMIN")
                .actorAdminId(adminId)
                .actorRoleCode(roleCode)
                .action(action)
                .targetType(targetType)
                .targetId(targetId)
                .beforeValue(beforeValue)
                .afterValue(afterValue)
                .metadata(metadata)
                .build();

        auditLogRepository.save(auditLog);
    }

    @Override
    public PageResponse<AuditLogDto> getAuditLogs(AuditLogFilterDto filter, Pageable pageable) {
        if (filter.getFromDate() != null && filter.getToDate() != null && filter.getFromDate().isAfter(filter.getToDate())) {
            throw new BusinessException("MSG-COM-002", "fromDate cannot be after toDate");
        }

        // Whitelist sorts and ensure ID is the tie-breaker
        List<org.springframework.data.domain.Sort.Order> validOrders = new ArrayList<>();
        List<String> allowedSorts = List.of("createdAt", "id", "action", "targetType", "targetId");
        for (org.springframework.data.domain.Sort.Order order : pageable.getSort()) {
            if (allowedSorts.contains(order.getProperty())) {
                validOrders.add(order);
            }
        }
        // MHB-024: neu moi khoa sap xep deu bi loai, phai quay ve mac dinh cua
        // endpoint (createdAt DESC) chu khong de moi khoa phu id DESC ben duoi.
        // id la UUID ngau nhien (AuditLog: GenerationType.UUID) nen sap theo id
        // khong con lien quan gi toi thoi gian: danh sach im lang mat tinh
        // moi-nhat-truoc ma van tra ve 200.
        if (validOrders.isEmpty()) {
            validOrders.add(org.springframework.data.domain.Sort.Order.desc("createdAt"));
        }
        
        // Ensure ID is stable tie breaker
        if (validOrders.stream().noneMatch(o -> o.getProperty().equals("id"))) {
            validOrders.add(org.springframework.data.domain.Sort.Order.desc("id"));
        }
        
        int safePage = Math.max(pageable.getPageNumber(), 0);
        int safeSize = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        Pageable safePageable = PageRequest.of(safePage, safeSize, org.springframework.data.domain.Sort.by(validOrders));

        List<UUID> actorIds = null;
        if (filter.getActor() != null && !filter.getActor().isBlank()) {
            actorIds = resolveActorIds(filter.getActor());
            if (actorIds.isEmpty()) {
                // If actor filter yields no results, return empty page immediately
                return PageResponse.from(Page.empty(safePageable));
            }
        }

        Specification<AuditLog> spec = AuditLogSpecification.filter(
                actorIds,
                filter.getRole(),
                filter.getTargetType(),
                filter.getTargetId(),
                filter.getAction(),
                filter.getFromDate(),
                filter.getToDate()
        );

        Page<AuditLog> page = auditLogRepository.findAll(spec, safePageable);

        if (page.isEmpty()) {
            return PageResponse.from(Page.empty(safePageable));
        }

        List<AuditLogDto> dtoList = mapToDtoList(page.getContent());

        return PageResponse.from(new PageImpl<>(dtoList, safePageable, page.getTotalElements()));
    }

    @Override
    public AuditLogDetailDto getAuditLogDetail(UUID id) {
        AuditLog auditLog = auditLogRepository.findById(id)
                .orElseThrow(() -> new BusinessException("MSG-COM-001", "Audit log not found"));

        Map<UUID, InternalAdminAccount> adminMap = new HashMap<>();
        Map<UUID, AppUser> userMap = new HashMap<>();
        
        if (auditLog.getActorAdminId() != null) {
            internalAdminAccountRepository.findById(auditLog.getActorAdminId())
                    .ifPresent(admin -> adminMap.put(admin.getId(), admin));
        } else if (auditLog.getActorUserId() != null) {
            appUserRepository.findById(auditLog.getActorUserId())
                    .ifPresent(user -> userMap.put(user.getId(), user));
        }

        return mapToDetailDto(auditLog, adminMap, userMap);
    }

    private List<UUID> resolveActorIds(String search) {
        List<UUID> matchedIds = new ArrayList<>();
        
        try {
            UUID id = UUID.fromString(search);
            matchedIds.add(id);
        } catch (IllegalArgumentException e) {
            // Not a UUID, search by email/name
            PageRequest limit = PageRequest.of(0, 101);
            List<UUID> adminIds = internalAdminAccountRepository.searchAdminIds(search, limit);
            List<UUID> userIds = appUserRepository.searchUserIds(search, limit);
                    
            matchedIds.addAll(adminIds);
            matchedIds.addAll(userIds);
            
            if (matchedIds.size() > 100) {
                throw new BusinessException("MSG-COM-002", "Too many actor search results. Please provide a more specific keyword.");
            }
        }
        
        return matchedIds;
    }

    private List<AuditLogDto> mapToDtoList(List<AuditLog> auditLogs) {
        Set<UUID> adminIds = auditLogs.stream()
                .map(AuditLog::getActorAdminId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<UUID> userIds = auditLogs.stream()
                .map(AuditLog::getActorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, InternalAdminAccount> adminMap = new HashMap<>();
        if (!adminIds.isEmpty()) {
            internalAdminAccountRepository.findAllById(adminIds)
                    .forEach(admin -> adminMap.put(admin.getId(), admin));
        }

        Map<UUID, AppUser> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            appUserRepository.findAllById(userIds)
                    .forEach(user -> userMap.put(user.getId(), user));
        }

        return auditLogs.stream().map(log -> {
            AuditLogDto dto = new AuditLogDto();
            dto.setId(log.getId());
            dto.setActorType(log.getActorType());
            dto.setActorUserId(log.getActorUserId());
            dto.setActorAdminId(log.getActorAdminId());
            dto.setActorRoleCode(log.getActorRoleCode());
            dto.setAction(log.getAction());
            dto.setTargetType(log.getTargetType());
            dto.setTargetId(log.getTargetId());
            dto.setCreatedAt(log.getCreatedAt());

            if (log.getActorAdminId() != null && adminMap.containsKey(log.getActorAdminId())) {
                InternalAdminAccount admin = adminMap.get(log.getActorAdminId());
                dto.setActorDisplayName(admin.getFullName());
                dto.setActorEmail(admin.getEmail());
            } else if (log.getActorUserId() != null && userMap.containsKey(log.getActorUserId())) {
                AppUser user = userMap.get(log.getActorUserId());
                dto.setActorDisplayName(user.getFullName());
                dto.setActorEmail(user.getEmail());
            }

            return dto;
        }).collect(Collectors.toList());
    }

    private AuditLogDetailDto mapToDetailDto(AuditLog log, Map<UUID, InternalAdminAccount> adminMap, Map<UUID, AppUser> userMap) {
        AuditLogDetailDto dto = new AuditLogDetailDto();
        dto.setId(log.getId());
        dto.setActorType(log.getActorType());
        dto.setActorUserId(log.getActorUserId());
        dto.setActorAdminId(log.getActorAdminId());
        dto.setActorRoleCode(log.getActorRoleCode());
        dto.setAction(log.getAction());
        dto.setTargetType(log.getTargetType());
        dto.setTargetId(log.getTargetId());
        dto.setCreatedAt(log.getCreatedAt());
        dto.setIpAddress(log.getIpAddress());
        dto.setUserAgent(log.getUserAgent());

        if (log.getActorAdminId() != null && adminMap.containsKey(log.getActorAdminId())) {
            InternalAdminAccount admin = adminMap.get(log.getActorAdminId());
            dto.setActorDisplayName(admin.getFullName());
            dto.setActorEmail(admin.getEmail());
        } else if (log.getActorUserId() != null && userMap.containsKey(log.getActorUserId())) {
            AppUser user = userMap.get(log.getActorUserId());
            dto.setActorDisplayName(user.getFullName());
            dto.setActorEmail(user.getEmail());
        }

        dto.setBeforeValue(redactSensitiveMap(log.getBeforeValue()));
        dto.setAfterValue(redactSensitiveMap(log.getAfterValue()));
        dto.setMetadata(redactSensitiveMap(log.getMetadata()));

        return dto;
    }

    private Map<String, Object> redactSensitiveMap(Map<String, Object> map) {
        if (map == null) return null;
        Map<String, Object> redacted = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = entry.getKey();
            if (isSensitiveKey(key)) {
                redacted.put(key, "***REDACTED***");
            } else if (entry.getValue() instanceof Map) {
                // Ignore unchecked cast for safe local recursion
                @SuppressWarnings("unchecked")
                Map<String, Object> childMap = (Map<String, Object>) entry.getValue();
                redacted.put(key, redactSensitiveMap(childMap));
            } else if (entry.getValue() instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> childList = (List<Object>) entry.getValue();
                redacted.put(key, redactSensitiveList(childList));
            } else {
                redacted.put(key, entry.getValue());
            }
        }
        return redacted;
    }

    private List<Object> redactSensitiveList(List<Object> list) {
        if (list == null) return null;
        List<Object> redacted = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> childMap = (Map<String, Object>) item;
                redacted.add(redactSensitiveMap(childMap));
            } else if (item instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> childList = (List<Object>) item;
                redacted.add(redactSensitiveList(childList));
            } else {
                redacted.add(item);
            }
        }
        return redacted;
    }

    private boolean isSensitiveKey(String key) {
        String lower = key.toLowerCase();
        return lower.contains("password") ||
               lower.contains("token") ||
               lower.contains("secret") ||
               lower.contains("key") ||
               lower.contains("hash") ||
               lower.contains("card") ||
               lower.contains("ccv") ||
               lower.contains("cvv") ||
               lower.contains("ssn") ||
               lower.contains("identitydocument");
    }
}
