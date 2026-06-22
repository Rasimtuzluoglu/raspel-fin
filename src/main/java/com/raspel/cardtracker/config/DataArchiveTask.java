package com.raspel.cardtracker.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

@Component
@Slf4j
public class DataArchiveTask {

    private static final int BATCH_SIZE = 1000;

    private final JdbcTemplate jdbcTemplate;

    public DataArchiveTask(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(cron = "0 0 3 1 * *")
    public void archiveOldExpenses() {
        try {
            LocalDate cutoff = LocalDate.now().minusYears(2);

            List<Long> expenseIds = jdbcTemplate.queryForList(
                "SELECT id FROM expense WHERE expense_date < ? LIMIT " + BATCH_SIZE,
                Long.class, cutoff
            );

            if (expenseIds.isEmpty()) {
                log.info("Arşivlenecek eski kayıt bulunamadı ({} yılından eski)", cutoff.getYear());
                return;
            }

            int totalArchived = 0;
            while (!expenseIds.isEmpty()) {
                int archived = jdbcTemplate.update(
                    "INSERT INTO expense_archive (id, card_id, contact_id, description, total_amount, " +
                    "installments, expense_date, category, tag, currency, original_amount, exchange_rate, " +
                    "receipt_path, receipt_content_type, created_by, created_at) " +
                    "SELECT id, card_id, contact_id, description, total_amount, " +
                    "installments, expense_date, category, tag, currency, original_amount, exchange_rate, " +
                    "receipt_path, receipt_content_type, created_by, created_at " +
                    "FROM expense WHERE id IN (" + placeholders(expenseIds.size()) + ") " +
                    "ON CONFLICT (id) DO NOTHING",
                    expenseIds.toArray()
                );

                jdbcTemplate.update(
                    "INSERT INTO installment_entry_archive (id, expense_id, due_year, due_month, amount, is_paid) " +
                    "SELECT ie.id, ie.expense_id, ie.due_year, ie.due_month, ie.amount, ie.is_paid " +
                    "FROM installment_entry ie " +
                    "WHERE ie.expense_id IN (" + placeholders(expenseIds.size()) + ") " +
                    "ON CONFLICT (id) DO NOTHING",
                    expenseIds.toArray()
                );

                jdbcTemplate.update(
                    "DELETE FROM installment_entry WHERE expense_id IN (" + placeholders(expenseIds.size()) + ")",
                    expenseIds.toArray()
                );

                jdbcTemplate.update(
                    "DELETE FROM expense WHERE id IN (" + placeholders(expenseIds.size()) + ")",
                    expenseIds.toArray()
                );

                totalArchived += archived;

                expenseIds = jdbcTemplate.queryForList(
                    "SELECT id FROM expense WHERE expense_date < ? LIMIT " + BATCH_SIZE,
                    Long.class, cutoff
                );
            }

            log.info("Arşivlendi: {} harcama ve ilişkili taksitler ({} yılından eski)", totalArchived, cutoff.getYear());
        } catch (Exception e) {
            log.error("Veri arşivleme hatası", e);
        }
    }

    private String placeholders(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        return sb.toString();
    }
}
