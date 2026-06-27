package com.raspel.cardtracker.domain.department;

import com.raspel.cardtracker.domain.audit.AuditAction;
import com.raspel.cardtracker.domain.audit.AuditLogService;
import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.domain.card.CardRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
public class DepartmentService {

    private final DepartmentRepository departmentRepository;
    private final CardRepository cardRepository;
    private final AuditLogService auditLogService;

    @Cacheable("departments")
    public List<Department> findAllActive() {
        return departmentRepository.findAll().stream()
                .filter(d -> d.getIsActive() != null && d.getIsActive())
                .collect(java.util.stream.Collectors.toList());
    }

    public List<Department> findAll() {
        return departmentRepository.findAll();
    }

    @CacheEvict(value = "departments", allEntries = true)
    public Department save(Department department) {
        if (department == null) throw new IllegalArgumentException("Departman bilgisi zorunludur");
        boolean isNew = department.getId() == null;
        Department saved = departmentRepository.save(department);
        auditLogService.log(isNew ? AuditAction.CREATE : AuditAction.UPDATE, "Departman", saved.getId(),
                "Departman " + (isNew ? "oluşturuldu" : "güncellendi") + ": " + saved.getName());
        return saved;
    }

    public Optional<Department> findById(Long id) {
        return departmentRepository.findById(id);
    }

    public Optional<Department> findByName(String name) {
        return departmentRepository.findByNameIgnoreCase(name);
    }

    @CacheEvict(value = "departments", allEntries = true)
    public void delete(Long id) {
        departmentRepository.findById(id).ifPresent(dept -> {
            List<Card> cards = cardRepository.findByDepartment(dept);
            for (Card card : cards) {
                card.setDepartment(null);
                cardRepository.save(card);
            }
            dept.setIsActive(false);
            departmentRepository.save(dept);
            auditLogService.log(AuditAction.DELETE, "Departman", id, "Departman silindi: " + dept.getName());
        });
    }
}
