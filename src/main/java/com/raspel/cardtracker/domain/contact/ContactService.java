package com.raspel.cardtracker.domain.contact;

import com.raspel.cardtracker.domain.audit.AuditAction;
import com.raspel.cardtracker.domain.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ContactService {

    private final ContactRepository contactRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<Contact> findAll() {
        return contactRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Contact> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return findAll();
        }
        return contactRepository.findByNameContainingIgnoreCase(query.trim());
    }

    @Transactional(readOnly = true)
    public Optional<Contact> findById(Long id) {
        return contactRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Contact> findByName(String name) {
        return contactRepository.findByName(name);
    }

    public Contact save(Contact contact) {
        if (contact == null) throw new IllegalArgumentException("Cari bilgisi zorunludur");
        boolean isNew = contact.getId() == null;
        Contact saved = contactRepository.save(contact);
        auditLogService.log(
                isNew ? AuditAction.CREATE : AuditAction.UPDATE,
                "Cari",
                saved.getId(),
                (isNew ? "Yeni cari oluşturuldu: " : "Cari güncellendi: ") + saved.getName()
        );
        return saved;
    }

    public void delete(Long id) {
        contactRepository.findById(id).ifPresent(contact -> {
            auditLogService.log(AuditAction.DELETE, "Cari", id, "Cari silindi: " + contact.getName());
        });
        contactRepository.deleteById(id);
    }
}
