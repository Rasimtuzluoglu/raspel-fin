package com.raspel.cardtracker.domain.expense;

import com.raspel.cardtracker.domain.audit.AuditAction;
import com.raspel.cardtracker.domain.audit.AuditLogService;
import com.raspel.cardtracker.domain.card.Card;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final InstallmentEntryRepository installmentEntryRepository;
    private final TcmbCurrencyService tcmbCurrencyService;
    private final AuditLogService auditLogService;

    /**
     * Yeni harcama kaydeder ve otomatik olarak taksit satırlarını oluşturur.
     * Örnek: 1200 TL, 12 taksit, Ocak 2025 → her ay 100 TL x 12 kayıt
     */
    public Expense createExpense(Expense expense) {
        if (expense.getExpenseDate() == null) throw new IllegalArgumentException("Harcama tarihi zorunludur");
        if (expense.getCard() == null) throw new IllegalArgumentException("Kart zorunludur");
        if (expense.getOriginalAmount() != null && expense.getOriginalAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Tutar 0'dan büyük olmalıdır");

        if (expense.getOriginalAmount() == null && expense.getTotalAmount() == null)
            throw new IllegalArgumentException("Tutar belirtilmelidir");

        // Para birimi ve orijinal tutar doğrulamaları
        if (expense.getCurrency() == null || expense.getCurrency().trim().isEmpty()) {
            expense.setCurrency("TRY");
        }
        if (expense.getOriginalAmount() == null) {
            expense.setOriginalAmount(expense.getTotalAmount());
        }

        // Döviz çevrimi
        if (!"TRY".equalsIgnoreCase(expense.getCurrency())) {
            BigDecimal rate = tcmbCurrencyService.getExchangeRate(expense.getCurrency(), expense.getExpenseDate());
            expense.setExchangeRate(rate);
            BigDecimal totalAmountTry = expense.getOriginalAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP);
            expense.setTotalAmount(totalAmountTry);
        } else {
            expense.setExchangeRate(BigDecimal.ONE);
            expense.setTotalAmount(expense.getOriginalAmount());
        }

        // Önce harcamayı kaydet
        Expense savedExpense = expenseRepository.save(expense);
        List<InstallmentEntry> entries = generateInstallments(savedExpense);
        installmentEntryRepository.saveAll(entries);
        savedExpense.setInstallmentEntries(entries);
        auditLogService.log(AuditAction.CREATE, "Harcama", savedExpense.getId(),
                "Yeni harcama oluşturuldu: " + savedExpense.getDescription() + " - " + savedExpense.getTotalAmount() + " ₺");
        return savedExpense;
    }

    /**
     * Güncellenen harcama bilgilerine göre taksit satırlarını yeniden oluşturur.
     */
    public Expense updateExpense(Expense expense) {
        if (expense.getExpenseDate() == null) throw new IllegalArgumentException("Harcama tarihi zorunludur");
        if (expense.getOriginalAmount() != null && expense.getOriginalAmount().compareTo(BigDecimal.ZERO) <= 0)
            throw new IllegalArgumentException("Tutar 0'dan büyük olmalıdır");

        // Para birimi ve orijinal tutar doğrulamaları
        if (expense.getCurrency() == null || expense.getCurrency().trim().isEmpty()) {
            expense.setCurrency("TRY");
        }
        if (expense.getOriginalAmount() == null) {
            expense.setOriginalAmount(expense.getTotalAmount());
        }

        // Döviz çevrimi
        if (!"TRY".equalsIgnoreCase(expense.getCurrency())) {
            BigDecimal rate = tcmbCurrencyService.getExchangeRate(expense.getCurrency(), expense.getExpenseDate());
            expense.setExchangeRate(rate);
            BigDecimal totalAmountTry = expense.getOriginalAmount().multiply(rate).setScale(2, RoundingMode.HALF_UP);
            expense.setTotalAmount(totalAmountTry);
        } else {
            expense.setExchangeRate(BigDecimal.ONE);
            expense.setTotalAmount(expense.getOriginalAmount());
        }

        // Mevcut taksit satırlarını temizle/sil
        installmentEntryRepository.deleteByExpenseId(expense.getId());

        // Harcamayı kaydet
        Expense savedExpense = expenseRepository.save(expense);

        // Managed entity'yi tazele ve koleksiyonu güvenli şekilde güncelle
        Expense managedExpense = expenseRepository.findById(savedExpense.getId()).orElse(savedExpense);
        managedExpense.getInstallmentEntries().clear();

        // Yeni taksit satırlarını oluştur
        List<InstallmentEntry> entries = generateInstallments(managedExpense);
        managedExpense.getInstallmentEntries().addAll(entries);
        installmentEntryRepository.saveAll(entries);
        auditLogService.log(AuditAction.UPDATE, "Harcama", managedExpense.getId(),
                "Harcama güncellendi: " + managedExpense.getDescription() + " - " + managedExpense.getTotalAmount() + " ₺");
        return managedExpense;
    }

    /**
     * Taksit satırlarını otomatik oluşturur.
     * Toplam tutar taksit sayısına bölünür. Kalan kuruş farkı son taksite eklenir.
     */
    private List<InstallmentEntry> generateInstallments(Expense expense) {
        List<InstallmentEntry> entries = new ArrayList<>();
        int installmentCount = expense.getInstallments();
        if (installmentCount <= 0) throw new IllegalArgumentException("Taksit sayısı en az 1 olmalıdır");
        BigDecimal totalAmount = expense.getTotalAmount();

        // Her taksit tutarı (kuruş hassasiyetinde)
        BigDecimal perInstallment = totalAmount.divide(
                BigDecimal.valueOf(installmentCount), 2, RoundingMode.HALF_UP
        );

        // Kalan fark (son taksite eklenecek)
        BigDecimal remainder = totalAmount.subtract(
                perInstallment.multiply(BigDecimal.valueOf(installmentCount))
        );

        LocalDate startDate = expense.getExpenseDate();

        for (int i = 0; i < installmentCount; i++) {
            YearMonth ym = YearMonth.from(startDate).plusMonths(i + 1);
            BigDecimal amount = (i == installmentCount - 1)
                    ? perInstallment.add(remainder)
                    : perInstallment;

            InstallmentEntry entry = InstallmentEntry.builder()
                    .expense(expense)
                    .dueYear(ym.getYear())
                    .dueMonth(ym.getMonthValue())
                    .amount(amount)
                    .isPaid(false)
                    .build();

            entries.add(entry);
        }

        log.info("{} adet taksit oluşturuldu. Harcama: {}, Toplam: {}",
                installmentCount, expense.getDescription(), totalAmount);

        return entries;
    }

    public List<Expense> findAll() {
        return expenseRepository.findAllWithCard();
    }

    public org.springframework.data.domain.Page<Expense> findBySearchTerm(String term, org.springframework.data.domain.Pageable pageable) {
        return expenseRepository.findBySearchTerm(term, pageable);
    }

    public Optional<Expense> findById(Long id) {
        return expenseRepository.findById(id);
    }

    public void deleteExpense(Long id) {
        expenseRepository.findById(id).ifPresent(expense -> {
            auditLogService.log(AuditAction.DELETE, "Harcama", id, "Harcama silindi: " + expense.getDescription());
        });
        installmentEntryRepository.deleteByExpenseId(id);
        expenseRepository.deleteById(id);
    }

    public List<Expense> findByCardId(Long cardId) {
        return expenseRepository.findByCardId(cardId);
    }

    /**
     * Belirli ay/yıl için taksit detaylarını getirir
     */
    public List<InstallmentEntry> getInstallmentsForMonth(int year, int month) {
        return installmentEntryRepository.findByYearAndMonthWithDetails(year, month);
    }

    /**
     * Belirli ay/yıl ve kart için taksit detaylarını getirir
     */
    public List<InstallmentEntry> getInstallmentsForMonthAndCard(int year, int month, Long cardId) {
        return installmentEntryRepository.findByYearAndMonthAndCardId(year, month, cardId);
    }

    public org.springframework.data.domain.Page<InstallmentEntry> findBySearchTermAndYearAndMonth(String term, Integer year, Integer month, Long cardId, org.springframework.data.domain.Pageable pageable) {
        return installmentEntryRepository.findBySearchTermAndYearAndMonth(term, year, month, cardId, pageable);
    }

    public BigDecimal sumAmountBySearchTermAndYearAndMonth(String term, Integer year, Integer month, Long cardId) {
        return installmentEntryRepository.sumAmountBySearchTermAndYearAndMonth(term, year, month, cardId);
    }

    /**
     * Belirli yıl/ay aralığı için taksitleri getirir
     */
    public List<InstallmentEntry> getInstallmentsForMonthRange(int startYear, int startMonth, int endYear, int endMonth, Long cardId, String term) {
        return installmentEntryRepository.findByYearMonthRange(startYear, startMonth, endYear, endMonth, cardId, term);
    }

    /**
     * Belirli yıl/ay aralığı için toplam tutarı hesaplar
     */
    public BigDecimal sumAmountForMonthRange(int startYear, int startMonth, int endYear, int endMonth, Long cardId, String term) {
        return installmentEntryRepository.sumAmountByYearMonthRange(startYear, startMonth, endYear, endMonth, cardId, term);
    }

    /**
     * Belirli ay/yıl için toplam ödeme tutarını hesaplar
     */
    public BigDecimal getTotalForMonth(int year, int month) {
        return installmentEntryRepository.sumAmountByYearAndMonth(year, month);
    }

    /**
     * Kart bazlı aylık toplam tutarları hesaplar (Dashboard grafiği için)
     */
    public Map<String, BigDecimal> getCardTotalsForMonth(int year, int month, List<Card> cards) {
        Map<String, BigDecimal> result = new HashMap<>();
        for (Card card : cards) {
            BigDecimal total = installmentEntryRepository.sumAmountByYearAndMonthAndCardId(year, month, card.getId());
            if (total != null && total.compareTo(BigDecimal.ZERO) > 0) {
                result.put(card.getName(), total);
            }
        }
        return result;
    }

    /**
     * Önümüzdeki N ay için aylık projeksiyon hesaplar
     */
    public Map<String, BigDecimal> getMonthlyProjection(int months) {
        Map<String, BigDecimal> projection = new java.util.LinkedHashMap<>();
        YearMonth current = YearMonth.now();

        for (int i = 0; i < months; i++) {
            YearMonth ym = current.plusMonths(i);
            BigDecimal total = installmentEntryRepository.sumAmountByYearAndMonth(ym.getYear(), ym.getMonthValue());
            String label = ym.getYear() + "-" + String.format("%02d", ym.getMonthValue());
            projection.put(label, total);
        }

        return projection;
    }

    /**
     * Önümüzdeki N ay için ödenen ve ödenmeyen detaylarıyla projeksiyon döner.
     */
    public List<Map<String, Object>> getMonthlyProjectionDetailed(int months) {
        List<Map<String, Object>> list = new ArrayList<>();
        YearMonth current = YearMonth.now();

        for (int i = 0; i < months; i++) {
            YearMonth ym = current.plusMonths(i);
            BigDecimal paid = installmentEntryRepository.sumPaidAmountByYearAndMonth(ym.getYear(), ym.getMonthValue());
            BigDecimal unpaid = installmentEntryRepository.sumUnpaidAmountByYearAndMonth(ym.getYear(), ym.getMonthValue());
            
            String label = ym.getYear() + "-" + String.format("%02d", ym.getMonthValue());
            
            Map<String, Object> map = new HashMap<>();
            map.put("label", label);
            map.put("paid", paid != null ? paid : BigDecimal.ZERO);
            map.put("unpaid", unpaid != null ? unpaid : BigDecimal.ZERO);
            map.put("total", (paid != null ? paid : BigDecimal.ZERO).add(unpaid != null ? unpaid : BigDecimal.ZERO));
            list.add(map);
        }
        return list;
    }

    /**
     * Tek sorguda tüm kartların departman bazlı toplamlarını döner (Dashboard donut chart için)
     */
    public List<Object[]> getTotalsGroupedByCardAndDept(int year, int month) {
        return installmentEntryRepository.findTotalsGroupedByCardAndDept(year, month);
    }

    /**
     * Taksit ödemesini işaretle
     */
    public void markAsPaid(Long installmentId) {
        installmentEntryRepository.findById(installmentId).ifPresent(entry -> {
            entry.setIsPaid(true);
            installmentEntryRepository.save(entry);
        });
    }

    /**
     * Taksit ödemesini geri al
     */
    public void markAsUnpaid(Long installmentId) {
        installmentEntryRepository.findById(installmentId).ifPresent(entry -> {
            entry.setIsPaid(false);
            installmentEntryRepository.save(entry);
        });
    }

    /**
     * Bir kartın henüz ödenmemiş toplam taksit tutarını döner.
     */
    public BigDecimal getUnpaidBalance(Long cardId) {
        return installmentEntryRepository.sumUnpaidAmountByCardId(cardId);
    }

    /**
     * Tüm kartların ödenmemiş toplam taksit tutarlarını tek sorguda döner.
     */
    public Map<Long, BigDecimal> getUnpaidBalancesGroupedByCard() {
        List<Object[]> results = installmentEntryRepository.sumUnpaidAmountGroupedByCardId();
        Map<Long, BigDecimal> map = new HashMap<>();
        for (Object[] row : results) {
            Long cardId = (Long) row[0];
            BigDecimal total = (BigDecimal) row[1];
            map.put(cardId, total);
        }
        return map;
    }

    /**
     * Açıklama metnine göre en olası kategoriyi önerir.
     * Benzer açıklamaya sahip geçmiş harcamalardaki en yaygın kategoriyi döner.
     */
    public String suggestCategory(String description) {
        if (description == null || description.trim().isEmpty()) return null;
        String keyword = description.trim();
        List<Object[]> results = expenseRepository.findCategoryByDescriptionKeyword(keyword);
        if (results != null && !results.isEmpty()) {
            return (String) results.get(0)[0];
        }
        String[] words = keyword.split("\\s+");
        if (words.length > 0 && words[0].length() > 3) {
            results = expenseRepository.findCategoryByDescriptionKeyword(words[0]);
            if (results != null && !results.isEmpty()) {
                return (String) results.get(0)[0];
            }
        }
        return null;
    }

    public long countByCreatedBy(String createdBy) {
        return expenseRepository.countByCreatedBy(createdBy);
    }

    public BigDecimal getTotalExpenseForMonth(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.plusMonths(1);
        return expenseRepository.sumAmountByExpenseYearAndMonth(start, end);
    }

    public List<Expense> findRecentByCreatedBy(String createdBy, int limit) {
        return expenseRepository.findRecentByCreatedBy(createdBy, org.springframework.data.domain.PageRequest.of(0, limit));
    }

    public BigDecimal getDepartmentSpentForMonth(Long deptId, int year, int month) {
        return installmentEntryRepository.sumAmountByDepartmentAndYearMonth(deptId, year, month);
    }
}
