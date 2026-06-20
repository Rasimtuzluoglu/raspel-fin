package com.raspel.cardtracker.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Component
@Slf4j
public class DataArchiveTask {

    private final JdbcTemplate jdbcTemplate;

    public DataArchiveTask(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 0 3 1 * *")
    @Transactional
    public void archiveOldExpenses() {
        try {
            LocalDate cutoff = LocalDate.now().minusYears(2);

            int archived = jdbcTemplate.update(
                "INSERT INTO expense_archive (id, card_id, contact_id, description, total_amount, " +
                "installments, expense_date, category, tag, currency, original_amount, exchange_rate, " +
                "receipt_path, receipt_content_type, created_by, created_at) " +
                "SELECT id, card_id, contact_id, description, total_amount, " +
                "installments, expense_date, category, tag, currency, original_amount, exchange_rate, " +
                "receipt_path, receipt_content_type, created_by, created_at " +
                "FROM expense WHERE expense_date < ?",
                cutoff
            );

            jdbcTemplate.update(
                "INSERT INTO installment_entry_archive (id, expense_id, due_year, due_month, amount, is_paid) " +
                "SELECT ie.id, ie.expense_id, ie.due_year, ie.due_month, ie.amount, ie.is_paid " +
                "FROM installment_entry ie " +
                "INNER JOIN expense e ON e.id = ie.expense_id " +
                "WHERE e.expense_date < ?",
                cutoff
            );

            jdbcTemplate.update(
                "DELETE FROM installment_entry WHERE expense_id IN " +
                "(SELECT id FROM expense WHERE expense_date < ?)",
                cutoff
            );

            jdbcTemplate.update(
                "DELETE FROM expense WHERE expense_date < ?",
                cutoff
            );

            log.info("Arşivlendi: {} harcama ve ilişkili taksitler ({} yılından eski)", archived, cutoff.getYear());
        } catch (Exception e) {
            log.error("Veri arşivleme hatası", e);
        }
    }
}
