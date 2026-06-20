package com.raspel.cardtracker.domain.note;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface NoteRepository extends JpaRepository<Note, Long> {

    List<Note> findByCreatedByOrderByPinnedDescUpdatedAtDesc(String createdBy);

    List<Note> findByCreatedByAndCategoryOrderByPinnedDescUpdatedAtDesc(String createdBy, String category);

    @Query("SELECT DISTINCT n.category FROM Note n WHERE n.createdBy = :username AND n.category IS NOT NULL ORDER BY n.category")
    List<String> findDistinctCategoriesByCreatedBy(@Param("username") String username);

    @Query("SELECT n FROM Note n WHERE n.createdBy = :username AND " +
           "(LOWER(n.title) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(n.content) LIKE LOWER(CONCAT('%', :term, '%')))" +
           "ORDER BY n.pinned DESC, n.updatedAt DESC")
    List<Note> searchByTerm(@Param("username") String username, @Param("term") String term);

    @Query("SELECT n FROM Note n WHERE n.createdBy = :username AND " +
           "(:category IS NULL OR n.category = :category) AND " +
           "(LOWER(n.title) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
           "LOWER(n.content) LIKE LOWER(CONCAT('%', :term, '%'))) " +
           "ORDER BY n.pinned DESC, n.updatedAt DESC")
    List<Note> searchByTermAndCategory(@Param("username") String username, @Param("term") String term, @Param("category") String category);

    @Query("SELECT n FROM Note n WHERE n.createdBy = :username AND n.reminderAt IS NOT NULL AND n.reminderAt <= :now AND n.reminded = false")
    List<Note> findDueReminders(@Param("username") String username, @Param("now") LocalDateTime now);
}
