package com.raspel.cardtracker.domain.audit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findAllByOrderByCreatedAtDesc();

    List<AuditLog> findByUsernameOrderByCreatedAtDesc(String username);

    List<AuditLog> findByEntityTypeOrderByCreatedAtDesc(String entityType);

    List<AuditLog> findByActionOrderByCreatedAtDesc(AuditAction action);

    List<AuditLog> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime start, LocalDateTime end);

    List<AuditLog> findByEntityTypeAndEntityIdOrderByCreatedAtDesc(String entityType, Long entityId);

    @Query(value = "SELECT * FROM audit_log a WHERE " +
           "(CAST(:username AS text) IS NULL OR a.username = CAST(:username AS text)) AND " +
           "(CAST(:action AS text) IS NULL OR a.action = CAST(:action AS text)) AND " +
           "(CAST(:entityType AS text) IS NULL OR a.entity_type = CAST(:entityType AS text)) AND " +
           "(CAST(:startDate AS timestamp) IS NULL OR a.created_at >= CAST(:startDate AS timestamp)) AND " +
           "(CAST(:endDate AS timestamp) IS NULL OR a.created_at <= CAST(:endDate AS timestamp)) AND " +
           "(CAST(:term AS text) IS NULL OR :term = '' OR LOWER(a.username) LIKE LOWER(CONCAT('%', CAST(:term AS text), '%')) OR LOWER(a.description) LIKE LOWER(CONCAT('%', CAST(:term AS text), '%'))) " +
           "ORDER BY a.created_at DESC",
           countQuery = "SELECT COUNT(*) FROM audit_log a WHERE " +
           "(CAST(:username AS text) IS NULL OR a.username = CAST(:username AS text)) AND " +
           "(CAST(:action AS text) IS NULL OR a.action = CAST(:action AS text)) AND " +
           "(CAST(:entityType AS text) IS NULL OR a.entity_type = CAST(:entityType AS text)) AND " +
           "(CAST(:startDate AS timestamp) IS NULL OR a.created_at >= CAST(:startDate AS timestamp)) AND " +
           "(CAST(:endDate AS timestamp) IS NULL OR a.created_at <= CAST(:endDate AS timestamp)) AND " +
           "(CAST(:term AS text) IS NULL OR :term = '' OR LOWER(a.username) LIKE LOWER(CONCAT('%', CAST(:term AS text), '%')) OR LOWER(a.description) LIKE LOWER(CONCAT('%', CAST(:term AS text), '%')))",
           nativeQuery = true)
    Page<AuditLog> findFilteredPaged(
            @Param("username") String username,
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("term") String term,
            Pageable pageable);

    @Query(value = "SELECT * FROM audit_log a WHERE " +
           "(CAST(:username AS text) IS NULL OR a.username = CAST(:username AS text)) AND " +
           "(CAST(:action AS text) IS NULL OR a.action = CAST(:action AS text)) AND " +
           "(CAST(:entityType AS text) IS NULL OR a.entity_type = CAST(:entityType AS text)) AND " +
           "(CAST(:startDate AS timestamp) IS NULL OR a.created_at >= CAST(:startDate AS timestamp)) AND " +
           "(CAST(:endDate AS timestamp) IS NULL OR a.created_at <= CAST(:endDate AS timestamp)) " +
           "ORDER BY a.created_at DESC", nativeQuery = true)
    List<AuditLog> findFiltered(
            @Param("username") String username,
            @Param("action") String action,
            @Param("entityType") String entityType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.createdAt < :cutoffDate")
    int deleteOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);

    @Modifying
    @Query("DELETE FROM AuditLog a WHERE a.entityType = :entityType AND a.entityId = :entityId")
    void deleteByEntityTypeAndId(@Param("entityType") String entityType, @Param("entityId") Long entityId);
}
