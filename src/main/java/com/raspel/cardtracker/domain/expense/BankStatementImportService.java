package com.raspel.cardtracker.domain.expense;

import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.domain.card.CardRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

@Service
@Transactional
@Slf4j
public class BankStatementImportService {

    private final ExpenseService expenseService;
    private final CardRepository cardRepository;

    public record ImportRow(String date, String description, BigDecimal amount, boolean isExpense) {}
    public record ImportResult(int successCount, int errorCount, List<String> errors) {}

    private static final DateTimeFormatter[] DATE_FORMATS = {
        DateTimeFormatter.ofPattern("dd.MM.yyyy"),
        DateTimeFormatter.ofPattern("dd/MM/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("dd-MM-yyyy"),
        DateTimeFormatter.ofPattern("MM/dd/yyyy")
    };

    public BankStatementImportService(ExpenseService expenseService, CardRepository cardRepository) {
        this.expenseService = expenseService;
        this.cardRepository = cardRepository;
    }

    public List<ImportRow> parseCsv(InputStream inputStream) {
        List<ImportRow> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, detectCharset(inputStream)))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return rows;

            String delimiter = detectDelimiter(headerLine);
            int[] colMap = detectColumns(headerLine, delimiter);

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split(Pattern.quote(delimiter), -1);
                try {
                    String dateStr = colMap[0] >= 0 && colMap[0] < parts.length ? parts[colMap[0]].replace("\"", "").trim() : "";
                    String desc = colMap[1] >= 0 && colMap[1] < parts.length ? parts[colMap[1]].replace("\"", "").trim() : "";
                    String amountStr = colMap[2] >= 0 && colMap[2] < parts.length ? parts[colMap[2]].replace("\"", "").trim() : "";

                    if (dateStr.isEmpty()) continue;

                    boolean isExpense = amountStr.startsWith("-");
                    String cleanAmount = amountStr.replace("-", "").replace("+", "").replace("TL", "").replace("₺", "").trim();
                    cleanAmount = normalizeAmount(cleanAmount);
                    cleanAmount = cleanAmount.replaceAll("[^0-9.]", "");

                    if (cleanAmount.isEmpty() || cleanAmount.equals(".")) continue;
                    BigDecimal amount = new BigDecimal(cleanAmount);
                    if (amount.compareTo(BigDecimal.ZERO) == 0) continue;

                    rows.add(new ImportRow(dateStr, desc, amount, isExpense));
                } catch (Exception e) {
                    log.debug("Satır atlandı: {}", line);
                }
            }
        } catch (Exception e) {
            log.error("CSV parse hatası", e);
        }
        return rows;
    }

    public ImportResult importRows(List<ImportRow> rows, Long cardId, String createdBy) {
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        int errorCount = 0;

        Card card = cardRepository.findById(cardId).orElse(null);
        if (card == null) {
            errors.add("Kart bulunamadı");
            return new ImportResult(0, rows.size(), errors);
        }

        for (ImportRow row : rows) {
            try {
                LocalDate date = parseDate(row.date());
                if (date == null) {
                    errors.add("Tarih ayrıştırılamadı: " + row.date());
                    errorCount++;
                    continue;
                }
                if (!row.isExpense()) {
                    continue; // skip income transactions
                }

                Expense expense = Expense.builder()
                        .card(card)
                        .description(row.description())
                        .totalAmount(row.amount())
                        .originalAmount(row.amount())
                        .currency("TRY")
                        .installments(1)
                        .expenseDate(date)
                        .createdBy(createdBy)
                        .build();

                expenseService.createExpense(expense);
                successCount++;
            } catch (Exception e) {
                log.warn("İçe aktarma hatası: {}", row.description(), e);
                errors.add("Aktarılamadı: " + row.description());
                errorCount++;
            }
        }

        log.info("Banka ekstresi import tamamlandı. Başarılı: {}, Hatalı: {}", successCount, errorCount);
        return new ImportResult(successCount, errorCount, errors);
    }

    private String detectCharset(InputStream is) {
        try {
            is.mark(4096);
            byte[] bytes = new byte[4096];
            int read = is.read(bytes);
            is.reset();
            String sample = new String(bytes, 0, read, StandardCharsets.UTF_8);
            if (sample.contains("�")) return "Windows-1254";
            return "UTF-8";
        } catch (Exception e) {
            return "UTF-8";
        }
    }

    private String detectDelimiter(String headerLine) {
        long semicolons = headerLine.chars().filter(c -> c == ';').count();
        long commas = headerLine.chars().filter(c -> c == ',').count();
        long tabs = headerLine.chars().filter(c -> c == '\t').count();
        if (semicolons >= commas && semicolons >= tabs && semicolons > 0) return ";";
        if (tabs >= commas && tabs >= semicolons && tabs > 0) return "\t";
        return ",";
    }

    private int[] detectColumns(String headerLine, String delimiter) {
        String lower = headerLine.toLowerCase(new java.util.Locale("tr"));
        String[] parts = headerLine.split(Pattern.quote(delimiter), -1);
        String[] lowerParts = lower.split(Pattern.quote(delimiter), -1);

        int dateCol = -1, descCol = -1, amountCol = -1;
        for (int i = 0; i < lowerParts.length; i++) {
            String col = lowerParts[i].replace("\"", "").trim();
            if (dateCol == -1 && (col.contains("tarih") || col.contains("date") || (col.contains("işlem") && col.contains("tarih")))) {
                dateCol = i;
            } else if (descCol == -1 && (col.contains("açıklama") || col.contains("aciklama") || col.contains("description"))) {
                descCol = i;
            } else if (amountCol == -1 && (col.contains("tutar") || col.contains("amount") || col.contains("işlem"))) {
                amountCol = i;
            }
        }
        if (dateCol == -1) dateCol = 0;
        if (descCol == -1) descCol = 1;
        if (amountCol == -1) amountCol = parts.length >= 3 ? 2 : parts.length - 1;
        return new int[]{dateCol, descCol, amountCol};
    }

    private LocalDate parseDate(String dateStr) {
        for (DateTimeFormatter fmt : DATE_FORMATS) {
            try {
                return LocalDate.parse(dateStr, fmt);
            } catch (DateTimeParseException ignored) {}
        }
        return null;
    }

    private String normalizeAmount(String amount) {
        if (amount == null || amount.isEmpty()) return amount;
        int lastComma = amount.lastIndexOf(',');
        int lastDot = amount.lastIndexOf('.');
        if (lastComma > lastDot) {
            return amount.replace(".", "").replace(",", ".");
        }
        return amount.replace(",", "");
    }
}
