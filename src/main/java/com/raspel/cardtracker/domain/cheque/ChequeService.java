package com.raspel.cardtracker.domain.cheque;

import com.raspel.cardtracker.domain.audit.AuditAction;
import com.raspel.cardtracker.domain.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class ChequeService {

    private final ChequeRepository chequeRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<Cheque> findAll() {
        return chequeRepository.findAllByOrderByMaturityDateAsc();
    }

    @Transactional(readOnly = true)
    public List<Cheque> searchByTerm(String term) {
        return chequeRepository.searchByTerm(term);
    }

    @Transactional(readOnly = true)
    public Optional<Cheque> findById(Long id) {
        return chequeRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public Optional<Cheque> findByChequeNumber(String chequeNumber) {
        return chequeRepository.findByChequeNumber(chequeNumber);
    }

    @Transactional(readOnly = true)
    public Page<Cheque> findFilteredPaged(String term, ChequeType type, ChequeStatus status,
                                          LocalDate startDate, LocalDate endDate, Pageable pageable) {
        String t = term != null && !term.trim().isEmpty() ? term.trim() : null;
        return chequeRepository.findFilteredPaged(t, type, status, startDate, endDate, pageable);
    }

    @Transactional(readOnly = true)
    public BigDecimal sumAmountByTypeAndStatus(ChequeType type, ChequeStatus status) {
        return chequeRepository.sumAmountByTypeAndStatus(type, status);
    }

    public Cheque save(Cheque cheque) {
        if (cheque == null) throw new IllegalArgumentException("Çek bilgisi zorunludur");
        boolean isNew = cheque.getId() == null;
        if (cheque.getAmount() == null || cheque.getAmount().compareTo(java.math.BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Çek tutarı sıfırdan büyük olmalıdır");
        }
        if (cheque.getMaturityDate() == null) {
            throw new IllegalArgumentException("Vade tarihi zorunludur");
        }
        Cheque saved = chequeRepository.save(cheque);
        auditLogService.log(
                isNew ? AuditAction.CREATE : AuditAction.UPDATE,
                "Çek",
                saved.getId(),
                (isNew ? "Yeni çek oluşturuldu: " : "Çek güncellendi: ") + saved.getChequeNumber()
        );
        return saved;
    }

    public void delete(Long id) {
        chequeRepository.findById(id).ifPresent(cheque -> {
            auditLogService.log(AuditAction.DELETE, "Çek", id, "Çek silindi: " + cheque.getChequeNumber());
            auditLogService.deleteByEntityTypeAndId("Çek", id);
        });
        chequeRepository.deleteById(id);
    }
}
