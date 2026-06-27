package com.raspel.cardtracker.domain.expense;

import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.domain.card.CardRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ExcelImportServiceTest {

    @Mock private ExpenseService expenseService;
    @Mock private CardRepository cardRepository;

    @InjectMocks
    private ExcelImportService excelImportService;

    @Test
    void importFromExcel_shouldHandleEmptyWorkbook() {
        when(cardRepository.findAllByActiveTrue()).thenReturn(List.of());

        byte[] emptyXlsx = createEmptyXlsx();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(emptyXlsx);
        ExcelImportService.ImportResult result = excelImportService.importFromExcel(inputStream, "testUser");

        assertThat(result).isNotNull();
        assertThat(result.successCount()).isEqualTo(0);
    }

    @Test
    void importFromExcel_shouldHandleInvalidCard() throws Exception {
        when(cardRepository.findAllByActiveTrue()).thenReturn(List.of());

        byte[] xlsx = createMinimalXlsx();
        ByteArrayInputStream inputStream = new ByteArrayInputStream(xlsx);
        ExcelImportService.ImportResult result = excelImportService.importFromExcel(inputStream, "testUser");

        assertThat(result).isNotNull();
    }

    private byte[] createEmptyXlsx() {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            workbook.createSheet("Sheet1");
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] createMinimalXlsx() {
        try (org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
            org.apache.poi.ss.usermodel.Sheet sheet = workbook.createSheet("Sheet1");
            org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(0);
            String[] headers = {"Kart Adı", "Açıklama", "Tutar", "Taksit Sayısı", "Tarih", "Kategori"};
            for (int i = 0; i < headers.length; i++) {
                headerRow.createCell(i).setCellValue(headers[i]);
            }
            org.apache.poi.ss.usermodel.Row dataRow = sheet.createRow(1);
            dataRow.createCell(0).setCellValue("TestKart");
            dataRow.createCell(1).setCellValue("Test Harcama");
            dataRow.createCell(2).setCellValue(100);
            dataRow.createCell(3).setCellValue(1);
            dataRow.createCell(4).setCellValue("2026-01-01");
            dataRow.createCell(5).setCellValue("Genel");
            workbook.write(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
