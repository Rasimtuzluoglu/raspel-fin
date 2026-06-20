package com.raspel.cardtracker.domain.reminder;

import com.raspel.cardtracker.domain.cheque.Cheque;
import com.raspel.cardtracker.domain.cheque.ChequeRepository;
import com.raspel.cardtracker.domain.cheque.ChequeStatus;
import com.raspel.cardtracker.domain.expense.InstallmentEntry;
import com.raspel.cardtracker.domain.expense.InstallmentEntryRepository;
import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.ui.utils.HolidayUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.stream.Collectors;

@Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
@RequiredArgsConstructor
public class PaymentReminderService {

    private final InstallmentEntryRepository installmentEntryRepository;
    private final ChequeRepository chequeRepository;

    /**
     * Taksit için son ödeme tarihini hesaplar.
     */
    public LocalDate calculateDueDate(InstallmentEntry entry) {
        if (entry == null || entry.getExpense() == null || entry.getExpense().getCard() == null) return LocalDate.now().plusMonths(1);
        Card card = entry.getExpense().getCard();
        int dueYear = entry.getDueYear();
        int dueMonth = entry.getDueMonth();
        
        YearMonth ym = YearMonth.of(dueYear, dueMonth);
        int closingDay = Math.min(card.getClosingDay(), ym.lengthOfMonth());
        
        LocalDate statementDate = LocalDate.of(dueYear, dueMonth, closingDay);
        LocalDate originalDueDate = statementDate.plusDays(card.getDueDay());
        return HolidayUtils.getNextBusinessDay(originalDueDate);
    }

    /**
     * Vadesi geçmiş ama ödenmemiş taksitleri getirir.
     */
    public List<InstallmentEntry> getOverdueInstallments() {
        LocalDate today = LocalDate.now();
        List<InstallmentEntry> allEntries = installmentEntryRepository.findAllUnpaidWithDetails();
        
        return allEntries.stream()
                .filter(entry -> !entry.getIsPaid())
                .filter(entry -> calculateDueDate(entry).isBefore(today))
                .sorted((a, b) -> calculateDueDate(a).compareTo(calculateDueDate(b)))
                .collect(Collectors.toList());
    }

    /**
     * Yaklaşan ödenmemiş taksitleri getirir (days gün içinde).
     */
    public List<InstallmentEntry> getUpcomingInstallments(int days) {
        LocalDate today = LocalDate.now();
        LocalDate limitDate = today.plusDays(days);
        List<InstallmentEntry> allEntries = installmentEntryRepository.findAllUnpaidWithDetails();
        
        return allEntries.stream()
                .filter(entry -> !entry.getIsPaid())
                .filter(entry -> {
                    LocalDate dueDate = calculateDueDate(entry);
                    return (dueDate.isEqual(today) || dueDate.isAfter(today)) && dueDate.isBefore(limitDate);
                })
                .sorted((a, b) -> calculateDueDate(a).compareTo(calculateDueDate(b)))
                .collect(Collectors.toList());
    }

    /**
     * Vadesi gelen/geçen veya yaklaşan çekleri getirir.
     */
    public List<Cheque> getActiveCheques() {
        return chequeRepository.findAllByOrderByMaturityDateAsc().stream()
                .filter(c -> c.getStatus() == ChequeStatus.PORTFOLIO)
                .collect(Collectors.toList());
    }

    /**
     * Yaklaşan çekleri getirir (days gün içinde vadesi gelen).
     */
    public List<Cheque> getUpcomingCheques(int days) {
        LocalDate today = LocalDate.now();
        LocalDate limitDate = today.plusDays(days);
        
        return getActiveCheques().stream()
                .filter(c -> {
                    LocalDate maturity = c.getMaturityDate();
                    return (maturity.isEqual(today) || maturity.isAfter(today)) && maturity.isBefore(limitDate);
                })
                .collect(Collectors.toList());
    }

    /**
     * Vadesi geçmiş bekleyen çekleri getirir.
     */
    public List<Cheque> getOverdueCheques() {
        LocalDate today = LocalDate.now();
        
        return getActiveCheques().stream()
                .filter(c -> c.getMaturityDate().isBefore(today))
                .collect(Collectors.toList());
    }

    /**
     * Hatırlatıcı özeti döner (Badge sayıları için).
     */
    public ReminderSummary getReminderSummary() {
        List<InstallmentEntry> overdueInstallments = getOverdueInstallments();
        List<InstallmentEntry> upcomingInstallments = getUpcomingInstallments(7); // 7 günlük yaklaşanlar
        List<Cheque> overdueCheques = getOverdueCheques();
        List<Cheque> upcomingCheques = getUpcomingCheques(7);

        BigDecimal overdueInstallmentTotal = overdueInstallments.stream()
                .map(InstallmentEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal upcomingInstallmentTotal = upcomingInstallments.stream()
                .map(InstallmentEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal overdueChequeTotal = overdueCheques.stream()
                .map(Cheque::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal upcomingChequeTotal = upcomingCheques.stream()
                .map(Cheque::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ReminderSummary(
                overdueInstallments.size(),
                overdueInstallmentTotal,
                upcomingInstallments.size(),
                upcomingInstallmentTotal,
                overdueCheques.size(),
                overdueChequeTotal,
                upcomingCheques.size(),
                upcomingChequeTotal
        );
    }

    @lombok.Value
    public static class ReminderSummary {
        int overdueInstallmentCount;
        BigDecimal overdueInstallmentTotal;
        int upcomingInstallmentCount;
        BigDecimal upcomingInstallmentTotal;
        int overdueChequeCount;
        BigDecimal overdueChequeTotal;
        int upcomingChequeCount;
        BigDecimal upcomingChequeTotal;

        public int getTotalCount() {
            return overdueInstallmentCount + upcomingInstallmentCount + overdueChequeCount + upcomingChequeCount;
        }

        public int getCriticalCount() {
            return overdueInstallmentCount + overdueChequeCount;
        }
    }
}
