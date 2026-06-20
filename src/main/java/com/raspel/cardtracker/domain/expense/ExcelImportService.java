package com.raspel.cardtracker.domain.expense;

import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.domain.card.CardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Excel dosyasından harcama verisi import eder.
 * Beklenen Excel formatı:
 * | Kart Adı | Açıklama | Tutar | Taksit Sayısı | Tarih | Kategori |
 */
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ExcelImportService {

    private final ExpenseService expenseService;
    private final CardRepository cardRepository;

    public record ImportResult(int successCount, int errorCount, List<String> errors) {}

    public ImportResult importFromExcel(InputStream inputStream, String createdBy) {
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        int errorCount = 0;

        try (Workbook workbook = new XSSFWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            List<Card> activeCards = cardRepository.findAllByActiveTrue();

            // İlk satır başlık, 1'den başla
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;

                try {
                    String cardName = getCellStringValue(row.getCell(0));
                    String description = getCellStringValue(row.getCell(1));
                    BigDecimal amount = getCellNumericValue(row.getCell(2));
                    int installments = getCellIntValue(row.getCell(3));
                    LocalDate date = getCellDateValue(row.getCell(4));
                    String category = getCellStringValue(row.getCell(5));

                    // Kart adına göre kartı bul
                    Optional<Card> cardOpt = activeCards.stream()
                            .filter(c -> c.getName().equalsIgnoreCase(cardName))
                            .findFirst();

                    if (cardOpt.isEmpty()) {
                        errors.add("Satır " + (i + 1) + ": Kart bulunamadı: " + cardName);
                        errorCount++;
                        continue;
                    }

                    if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
                        errors.add("Satır " + (i + 1) + ": Geçersiz tutar");
                        errorCount++;
                        continue;
                    }

                    if (installments < 1) {
                        errors.add("Satır " + (i + 1) + ": Taksit sayısı 1'den küçük olamaz, 1 olarak düzeltildi");
                        installments = 1;
                    }
                    if (date == null) {
                        errors.add("Satır " + (i + 1) + ": Tarih boş, bugünün tarihi kullanıldı");
                        date = LocalDate.now();
                    }

                    Expense expense = Expense.builder()
                            .card(cardOpt.get())
                            .description(description)
                            .totalAmount(amount)
                            .installments(installments)
                            .expenseDate(date)
                            .category(category)
                            .createdBy(createdBy)
                            .build();

                    expenseService.createExpense(expense);
                    successCount++;

                } catch (Exception e) {
                    errors.add("Satır " + (i + 1) + ": " + e.getMessage());
                    errorCount++;
                }
            }
        } catch (Exception e) {
            log.error("Excel import hatası", e);
            errors.add("Dosya okuma hatası: " + e.getMessage());
        }

        log.info("Excel import tamamlandı. Başarılı: {}, Hatalı: {}", successCount, errorCount);
        return new ImportResult(successCount, errorCount, errors);
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue().trim();
            case NUMERIC -> String.valueOf((int) cell.getNumericCellValue());
            default -> "";
        };
    }

    private BigDecimal getCellNumericValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> BigDecimal.valueOf(cell.getNumericCellValue());
            case STRING -> {
                try {
                    yield new BigDecimal(cell.getStringCellValue().trim().replace(",", "."));
                } catch (NumberFormatException e) {
                    yield null;
                }
            }
            default -> null;
        };
    }

    private int getCellIntValue(Cell cell) {
        if (cell == null) return 1;
        return switch (cell.getCellType()) {
            case NUMERIC -> (int) cell.getNumericCellValue();
            case STRING -> {
                try {
                    yield Integer.parseInt(cell.getStringCellValue().trim());
                } catch (NumberFormatException e) {
                    yield 1;
                }
            }
            default -> 1;
        };
    }

    private LocalDate getCellDateValue(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toInstant()
                            .atZone(ZoneId.systemDefault()).toLocalDate();
                }
                yield null;
            }
            case STRING -> {
                try {
                    yield LocalDate.parse(cell.getStringCellValue().trim());
                } catch (Exception e) {
                    yield null;
                }
            }
            default -> null;
        };
    }
}
