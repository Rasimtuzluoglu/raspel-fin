package com.raspel.cardtracker.domain.cheque;

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
        });
        chequeRepository.deleteById(id);
    }
}
