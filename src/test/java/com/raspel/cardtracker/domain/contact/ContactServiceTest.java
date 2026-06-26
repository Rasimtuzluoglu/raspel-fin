package com.raspel.cardtracker.domain.contact;

import com.raspel.cardtracker.domain.audit.AuditLogService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ContactServiceTest {

    @Mock private ContactRepository contactRepository;
    @Mock private AuditLogService auditLogService;

    @InjectMocks
    private ContactService contactService;

    @Test
    void save_shouldThrowWhenContactIsNull() {
        assertThatThrownBy(() -> contactService.save(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Cari bilgisi zorunludur");
    }

    @Test
    void save_shouldCreateNewContactWithAuditLog() {
        Contact contact = new Contact();
        contact.setName("Test Firma");

        when(contactRepository.save(any(Contact.class))).thenAnswer(inv -> {
            Contact c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        Contact saved = contactService.save(contact);

        assertThat(saved.getId()).isEqualTo(1L);
        verify(auditLogService).log(any(), eq("Cari"), any(), contains("Yeni cari oluşturuldu"));
    }

    @Test
    void save_shouldUpdateExistingContactWithAuditLog() {
        Contact contact = new Contact();
        contact.setId(5L);
        contact.setName("Güncellenmiş Firma");

        when(contactRepository.save(any(Contact.class))).thenReturn(contact);

        contactService.save(contact);

        verify(auditLogService).log(any(), eq("Cari"), any(), contains("Cari güncellendi"));
    }

    @Test
    void delete_shouldDeleteWithAuditLog() {
        Contact contact = new Contact();
        contact.setId(1L);
        contact.setName("Silinecek Firma");

        when(contactRepository.findById(1L)).thenReturn(Optional.of(contact));

        contactService.delete(1L);

        verify(contactRepository).deleteById(1L);
        verify(auditLogService).log(any(), eq("Cari"), eq(1L), contains("Cari silindi"));
    }

    @Test
    void search_shouldReturnAllContactsWhenQueryIsEmpty() {
        Contact c1 = new Contact();
        when(contactRepository.findAll()).thenReturn(List.of(c1));

        List<Contact> result = contactService.search("");

        assertThat(result).hasSize(1);
        verify(contactRepository).findAll();
        verify(contactRepository, never()).findByNameContainingIgnoreCase(any());
    }

    @Test
    void search_shouldReturnAllContactsWhenQueryIsNull() {
        Contact c1 = new Contact();
        when(contactRepository.findAll()).thenReturn(List.of(c1));

        List<Contact> result = contactService.search(null);

        assertThat(result).hasSize(1);
        verify(contactRepository).findAll();
    }

    @Test
    void search_shouldDelegateToRepositoryWhenQueryNotEmpty() {
        Contact c1 = new Contact();
        when(contactRepository.findByNameContainingIgnoreCase("test")).thenReturn(List.of(c1));

        List<Contact> result = contactService.search("test");

        assertThat(result).hasSize(1);
        verify(contactRepository).findByNameContainingIgnoreCase("test");
    }

    @Test
    void findById_shouldReturnEmptyWhenNotFound() {
        when(contactRepository.findById(999L)).thenReturn(Optional.empty());

        Optional<Contact> result = contactService.findById(999L);

        assertThat(result).isEmpty();
    }
}
