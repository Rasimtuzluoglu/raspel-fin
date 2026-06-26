package com.raspel.cardtracker.domain.audit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditLogServiceTest {

    @Mock private AuditLogRepository auditLogRepository;

    @InjectMocks
    private AuditLogService auditLogService;

    private void setSecurityContextUser(String username) {
        Authentication auth = new UsernamePasswordAuthenticationToken(username, null, List.of());
        SecurityContext context = mock(SecurityContext.class);
        when(context.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(context);
    }

    @Test
    void log_shouldSaveAuditLogWithCurrentUser() {
        setSecurityContextUser("testadmin");

        auditLogService.log(AuditAction.CREATE, "Harcama", 1L, "Test açıklama");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("testadmin");
        assertThat(saved.getAction()).isEqualTo(AuditAction.CREATE);
        assertThat(saved.getEntityType()).isEqualTo("Harcama");
        assertThat(saved.getEntityId()).isEqualTo(1L);
        assertThat(saved.getDescription()).isEqualTo("Test açıklama");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    void log_shouldFallbackToSystemWhenNoSecurityContext() {
        SecurityContextHolder.clearContext();

        auditLogService.log(AuditAction.DELETE, "Kart", 99L, "Otomatik silme");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("system");
    }

    @Test
    void logWithUsername_shouldSaveWithGivenUsername() {
        auditLogService.logWithUsername("belirli_kullanici", AuditAction.UPDATE,
                "Profil", 5L, "Profil güncellendi");

        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog saved = captor.getValue();
        assertThat(saved.getUsername()).isEqualTo("belirli_kullanici");
        assertThat(saved.getAction()).isEqualTo(AuditAction.UPDATE);
        assertThat(saved.getEntityType()).isEqualTo("Profil");
    }

    @Test
    void cleanupOldLogs_shouldDeleteLogsOlderThanOneYear() {
        when(auditLogRepository.deleteOlderThan(any(LocalDateTime.class))).thenReturn(42);

        auditLogService.cleanupOldLogs();

        ArgumentCaptor<LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(auditLogRepository).deleteOlderThan(cutoffCaptor.capture());

        // Cutoff tarihi bugünden yaklaşık 1 yıl önce olmalı
        LocalDateTime cutoff = cutoffCaptor.getValue();
        LocalDateTime expectedApprox = LocalDateTime.now().minusYears(1);
        assertThat(cutoff).isBetween(expectedApprox.minusSeconds(5), expectedApprox.plusSeconds(5));
    }

    @Test
    void findAll_shouldReturnAllLogs() {
        AuditLog log1 = new AuditLog();
        AuditLog log2 = new AuditLog();
        when(auditLogRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(log1, log2));

        List<AuditLog> result = auditLogService.findAll();

        assertThat(result).hasSize(2);
        verify(auditLogRepository).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void findByUsername_shouldDelegateToRepository() {
        when(auditLogRepository.findByUsernameOrderByCreatedAtDesc("admin"))
                .thenReturn(List.of(new AuditLog()));

        List<AuditLog> result = auditLogService.findByUsername("admin");

        assertThat(result).hasSize(1);
    }
}
