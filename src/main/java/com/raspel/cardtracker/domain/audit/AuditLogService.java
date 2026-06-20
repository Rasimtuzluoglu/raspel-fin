package com.raspel.cardtracker.domain.audit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Mevcut oturumdaki kullanıcı adıyla audit log kaydı oluşturur.
     */
    public void log(AuditAction action, String entityType, Long entityId, String description) {
        String username = getCurrentUsername();
        AuditLog entry = AuditLog.builder()
                .username(username)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build();
        auditLogRepository.save(entry);
    }

    /**
     * Belirtilen kullanıcı adıyla audit log kaydı oluşturur (login/logout gibi durumlar için).
     */
    public void logWithUsername(String username, AuditAction action, String entityType, Long entityId, String description) {
        AuditLog entry = AuditLog.builder()
                .username(username)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .description(description)
                .createdAt(LocalDateTime.now())
                .build();
        auditLogRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findAll() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findFiltered(String username, AuditAction action, String entityType,
                                       LocalDateTime startDate, LocalDateTime endDate) {
        String actionStr = action != null ? action.name() : null;
        return auditLogRepository.findFiltered(username, actionStr, entityType, startDate, endDate);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findByUsername(String username) {
        return auditLogRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    @Transactional(readOnly = true)
    public List<AuditLog> findByEntityTypeAndEntityId(String entityType, Long entityId) {
        return auditLogRepository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId);
    }

    /**
     * 1 yıldan eski audit log kayıtlarını siler.
     * Her gün gece yarısı 02:00'de çalışır.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldLogs() {
        LocalDateTime cutoffDate = LocalDateTime.now().minusYears(1);
        int deletedCount = auditLogRepository.deleteOlderThan(cutoffDate);
        log.info("{} adet eski audit log kaydı silindi (1 yıldan eski)", deletedCount);
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }
}
