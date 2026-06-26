package com.raspel.cardtracker.domain.card;

import com.raspel.cardtracker.domain.audit.AuditAction;
import com.raspel.cardtracker.domain.audit.AuditLogService;
import com.raspel.cardtracker.domain.expense.Expense;
import com.raspel.cardtracker.domain.expense.ExpenseRepository;
import com.raspel.cardtracker.domain.expense.InstallmentEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
public class CardService {

    private final CardRepository cardRepository;
    private final ExpenseRepository expenseRepository;
    private final InstallmentEntryRepository installmentEntryRepository;
    private final AuditLogService auditLogService;

    @Transactional(readOnly = true)
    public List<Card> findAllActive() {
        return cardRepository.findAllByActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<Card> findAll() {
        return cardRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<Card> findById(Long id) {
        return cardRepository.findById(id);
    }

    public Card save(Card card) {
        if (card == null) throw new IllegalArgumentException("Kart bilgisi zorunludur");
        boolean isNew = card.getId() == null;
        Card saved = cardRepository.save(card);
        auditLogService.log(
                isNew ? AuditAction.CREATE : AuditAction.UPDATE,
                "Kart",
                saved.getId(),
                (isNew ? "Yeni kart oluşturuldu: " : "Kart güncellendi: ") + saved.getName()
        );
        return saved;
    }

    public void delete(Long id) {
        cardRepository.findById(id).ifPresent(card -> {
            card.setActive(false);
            cardRepository.save(card);

            List<Expense> expenses = expenseRepository.findByCardId(id);
            if (!expenses.isEmpty()) {
                for (Expense e : expenses) {
                    e.setCard(null);
                }
                expenseRepository.saveAll(expenses);
                auditLogService.log(AuditAction.DELETE, "Kart", id,
                        "Kart silindi (yumuşak): " + card.getName() + " - " + expenses.size() + " harcamanın kart bağlantısı kaldırıldı");
            } else {
                auditLogService.log(AuditAction.DELETE, "Kart", id, "Kart silindi: " + card.getName());
            }
        });
    }

    public void activate(Long id) {
        cardRepository.findById(id).ifPresent(card -> {
            card.setActive(true);
            cardRepository.save(card);
            auditLogService.log(AuditAction.UPDATE, "Kart", id, "Kart aktifleştirildi: " + card.getName());
        });
    }

    public void hardDelete(Long id) {
        List<Expense> expenses = expenseRepository.findByCardId(id);
        for (Expense e : expenses) {
            installmentEntryRepository.deleteByExpenseId(e.getId());
        }
        expenseRepository.deleteAllByCardId(id);
        cardRepository.deleteById(id);
        auditLogService.log(AuditAction.DELETE, "Kart", id, "Kart ve tüm harcamaları kalıcı olarak silindi");
    }

    @Transactional(readOnly = true)
    public List<Card> findByCategory(String category) {
        return cardRepository.findByCategory(category);
    }

    @Transactional(readOnly = true)
    public long count() {
        return cardRepository.count();
    }
}
