package com.raspel.cardtracker.domain.note;

import com.raspel.cardtracker.domain.audit.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock private NoteRepository noteRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private NoteService noteService;

    @Test
    void save_shouldCreateNewNoteWithAuditLog() {
        Note note = new Note();
        note.setTitle("Test Not");
        note.setCreatedBy("testuser");

        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            n.setId(1L);
            return n;
        });

        Note saved = noteService.save(note);

        assertThat(saved.getId()).isEqualTo(1L);
        verify(noteRepository).save(note);
        verify(auditLogService).log(any(), eq("Not"), any(), contains("oluşturuldu"));
    }

    @Test
    void save_shouldUpdateExistingNoteWithAuditLog() {
        Note note = new Note();
        note.setId(10L);
        note.setTitle("Güncellenmiş Not");

        when(noteRepository.save(any(Note.class))).thenReturn(note);

        noteService.save(note);

        verify(auditLogService).log(any(), eq("Not"), any(), contains("güncellendi"));
    }

    @Test
    void save_shouldSetUpdatedAtBeforeSaving() {
        Note note = new Note();
        note.setTitle("Zaman Testi");
        note.setUpdatedAt(null);

        when(noteRepository.save(any(Note.class))).thenAnswer(inv -> {
            Note n = inv.getArgument(0);
            n.setId(2L);
            return n;
        });

        noteService.save(note);

        assertThat(note.getUpdatedAt()).isNotNull();
    }

    @Test
    void delete_shouldDeleteNoteWithAuditLog() {
        Note note = new Note();
        note.setId(1L);
        note.setTitle("Silinecek Not");

        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));

        noteService.delete(1L);

        verify(noteRepository).deleteById(1L);
        verify(auditLogService).log(any(), eq("Not"), eq(1L), contains("Not silindi"));
    }

    @Test
    void togglePin_shouldSetPinnedToTrueWhenCurrentlyFalse() {
        Note note = new Note();
        note.setId(1L);
        note.setPinned(false);

        when(noteRepository.findById(1L)).thenReturn(Optional.of(note));
        when(noteRepository.save(any())).thenReturn(note);

        noteService.togglePin(1L);

        assertThat(note.getPinned()).isTrue();
    }

    @Test
    void togglePin_shouldSetPinnedToFalseWhenCurrentlyTrue() {
        Note note = new Note();
        note.setId(2L);
        note.setPinned(true);

        when(noteRepository.findById(2L)).thenReturn(Optional.of(note));
        when(noteRepository.save(any())).thenReturn(note);

        noteService.togglePin(2L);

        assertThat(note.getPinned()).isFalse();
    }

    @Test
    void markReminded_shouldSetRemindedTrue() {
        Note note = new Note();
        note.setId(3L);
        note.setReminded(false);

        when(noteRepository.findById(3L)).thenReturn(Optional.of(note));
        when(noteRepository.save(any())).thenReturn(note);

        noteService.markReminded(3L);

        assertThat(note.getReminded()).isTrue();
        verify(noteRepository).save(note);
    }

    @Test
    void getDueReminders_shouldCallRepositoryWithCurrentTime() {
        Note note = new Note();
        when(noteRepository.findDueReminders(eq("user"), any(LocalDateTime.class)))
                .thenReturn(List.of(note));

        List<Note> result = noteService.getDueReminders("user");

        assertThat(result).hasSize(1);
        verify(noteRepository).findDueReminders(eq("user"), any(LocalDateTime.class));
    }

    @Test
    void findAllByUser_shouldReturnUserNotes() {
        when(noteRepository.findByCreatedByOrderByPinnedDescUpdatedAtDesc("user1"))
                .thenReturn(List.of(new Note(), new Note()));

        List<Note> result = noteService.findAllByUser("user1");

        assertThat(result).hasSize(2);
    }
}
