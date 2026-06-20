package com.raspel.cardtracker.domain.note;

import com.raspel.cardtracker.domain.audit.AuditAction;
import com.raspel.cardtracker.domain.audit.AuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@Transactional
public class NoteService {

    private final NoteRepository noteRepository;
    private final AuditLogService auditLogService;

    public NoteService(NoteRepository noteRepository, AuditLogService auditLogService) {
        this.noteRepository = noteRepository;
        this.auditLogService = auditLogService;
    }

    public List<Note> findAllByUser(String username) {
        return noteRepository.findByCreatedByOrderByPinnedDescUpdatedAtDesc(username);
    }

    public List<Note> findByCategory(String username, String category) {
        return noteRepository.findByCreatedByAndCategoryOrderByPinnedDescUpdatedAtDesc(username, category);
    }

    public List<Note> search(String username, String term) {
        return noteRepository.searchByTerm(username, term);
    }

    public List<Note> searchByTermAndCategory(String username, String term, String category) {
        return noteRepository.searchByTermAndCategory(username, term, category);
    }

    public List<String> getCategories(String username) {
        return noteRepository.findDistinctCategoriesByCreatedBy(username);
    }

    public Note save(Note note) {
        boolean isNew = note.getId() == null;
        note.setUpdatedAt(LocalDateTime.now());
        Note saved = noteRepository.save(note);
        auditLogService.log(isNew ? AuditAction.CREATE : AuditAction.UPDATE, "Not", saved.getId(),
                "Not " + (isNew ? "oluşturuldu" : "güncellendi") + ": " + saved.getTitle());
        return saved;
    }

    public Optional<Note> findById(Long id) {
        return noteRepository.findById(id);
    }

    public void delete(Long id) {
        noteRepository.findById(id).ifPresent(note -> {
            auditLogService.log(AuditAction.DELETE, "Not", id, "Not silindi: " + note.getTitle());
        });
        noteRepository.deleteById(id);
    }

    public void togglePin(Long id) {
        noteRepository.findById(id).ifPresent(note -> {
            note.setPinned(!note.getPinned());
            note.setUpdatedAt(LocalDateTime.now());
            noteRepository.save(note);
        });
    }

    public List<Note> getDueReminders(String username) {
        return noteRepository.findDueReminders(username, LocalDateTime.now());
    }

    public void markReminded(Long id) {
        noteRepository.findById(id).ifPresent(note -> {
            note.setReminded(true);
            noteRepository.save(note);
        });
    }
}
