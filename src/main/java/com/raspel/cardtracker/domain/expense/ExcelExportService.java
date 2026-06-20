package com.raspel.cardtracker.domain.expense;

import com.raspel.cardtracker.domain.cheque.Cheque;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ExcelExportService {

    public ByteArrayInputStream exportInstallments(List<InstallmentEntry> entries) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Taksit Detayları");

            // Başlık fontu ve stili
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 11);

            CellStyle headerCellStyle = workbook.createCellStyle();
            headerCellStyle.setFont(headerFont);
            headerCellStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerCellStyle.setAlignment(HorizontalAlignment.CENTER);
            headerCellStyle.setBorderBottom(BorderStyle.THIN);
            headerCellStyle.setBorderTop(BorderStyle.THIN);
            headerCellStyle.setBorderLeft(BorderStyle.THIN);
            headerCellStyle.setBorderRight(BorderStyle.THIN);

            // Para birimi (currency) hücresi stili
            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00\" ₺\""));
            currencyStyle.setBorderBottom(BorderStyle.THIN);
            currencyStyle.setBorderTop(BorderStyle.THIN);
            currencyStyle.setBorderLeft(BorderStyle.THIN);
            currencyStyle.setBorderRight(BorderStyle.THIN);

            // Normal veri hücresi stili
            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            // Başlıklar
            String[] columns = {"Kart Adı", "Departman", "Kart Sahibi", "Açıklama", "Taksit Tutarı", "Toplam Harcama", "Döviz Tutar", "Taksit No", "Vade (Ay/Yıl)", "Kategori", "Durum"};
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < columns.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(columns[col]);
                cell.setCellStyle(headerCellStyle);
            }

            // Verileri doldur
            int rowIdx = 1;
            for (InstallmentEntry entry : entries) {
                Row row = sheet.createRow(rowIdx++);
                Expense expense = entry.getExpense();
                
                // Kart adı
                Cell cell0 = row.createCell(0);
                cell0.setCellValue(expense.getCard().getName());
                cell0.setCellStyle(dataStyle);

                // Departman
                Cell cell1 = row.createCell(1);
                cell1.setCellValue(expense.getCard().getDepartment() != null ? expense.getCard().getDepartment().getName() : "-");
                cell1.setCellStyle(dataStyle);

                // Kart Sahibi
                Cell cell2 = row.createCell(2);
                cell2.setCellValue(expense.getCard().getHolderName() != null ? expense.getCard().getHolderName() : "-");
                cell2.setCellStyle(dataStyle);

                // Açıklama
                Cell cell3 = row.createCell(3);
                cell3.setCellValue(expense.getDescription());
                cell3.setCellStyle(dataStyle);

                // Taksit Tutarı (TL)
                Cell cell4 = row.createCell(4);
                cell4.setCellValue(entry.getAmount().doubleValue());
                cell4.setCellStyle(currencyStyle);

                // Toplam Tutar (TL)
                Cell cell5 = row.createCell(5);
                cell5.setCellValue(expense.getTotalAmount().doubleValue());
                cell5.setCellStyle(currencyStyle);

                // Orijinal Döviz Tutarı ve Cinsi
                Cell cell6 = row.createCell(6);
                if (!"TRY".equalsIgnoreCase(expense.getCurrency())) {
                    cell6.setCellValue(expense.getOriginalAmount().doubleValue() + " " + expense.getCurrency());
                } else {
                    cell6.setCellValue("-");
                }
                cell6.setCellStyle(dataStyle);

                // Taksit Detay (Harcama oluşturulurken taksit sayısı)
                Cell cell7 = row.createCell(7);
                cell7.setCellValue(expense.getInstallments() + " Taksit");
                cell7.setCellStyle(dataStyle);

                // Vade
                Cell cell8 = row.createCell(8);
                cell8.setCellValue(entry.getDueMonth() + "/" + entry.getDueYear());
                cell8.setCellStyle(dataStyle);

                // Kategori
                Cell cell9 = row.createCell(9);
                cell9.setCellValue(expense.getCategory() != null ? expense.getCategory() : "-");
                cell9.setCellStyle(dataStyle);

                // Durum
                Cell cell10 = row.createCell(10);
                cell10.setCellValue(entry.getIsPaid() ? "Ödendi" : "Bekliyor");
                cell10.setCellStyle(dataStyle);
            }

            // Sütun genişliklerini otomatik ayarla
            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Excel dışa aktarımında hata oluştu: " + e.getMessage(), e);
        }
    }

    public ByteArrayInputStream createSampleTemplate() {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Şablon");

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 11);

            CellStyle headerCellStyle = workbook.createCellStyle();
            headerCellStyle.setFont(headerFont);
            headerCellStyle.setFillForegroundColor(IndexedColors.TEAL.getIndex());
            headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerCellStyle.setAlignment(HorizontalAlignment.CENTER);
            headerCellStyle.setBorderBottom(BorderStyle.THIN);
            headerCellStyle.setBorderTop(BorderStyle.THIN);
            headerCellStyle.setBorderLeft(BorderStyle.THIN);
            headerCellStyle.setBorderRight(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            String[] columns = {"Kart Adı", "Açıklama", "Tutar", "Taksit Sayısı", "Tarih", "Kategori"};
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < columns.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(columns[col]);
                cell.setCellStyle(headerCellStyle);
            }

            Row sampleRow = sheet.createRow(1);
            String[] sampleData = {"Kredi Kartım", "Örnek Harcama", "100,00", "3", "2025-01-15", "Genel"};
            for (int col = 0; col < sampleData.length; col++) {
                Cell cell = sampleRow.createCell(col);
                cell.setCellValue(sampleData[col]);
                cell.setCellStyle(dataStyle);
            }

            sheet.createRow(2);

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Excel şablon oluşturmada hata: " + e.getMessage(), e);
        }
    }

    public ByteArrayInputStream exportCheques(List<Cheque> cheques) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Çek Portföyü");

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerFont.setFontHeightInPoints((short) 11);

            CellStyle headerCellStyle = workbook.createCellStyle();
            headerCellStyle.setFont(headerFont);
            headerCellStyle.setFillForegroundColor(IndexedColors.GREEN.getIndex());
            headerCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerCellStyle.setAlignment(HorizontalAlignment.CENTER);
            headerCellStyle.setBorderBottom(BorderStyle.THIN);
            headerCellStyle.setBorderTop(BorderStyle.THIN);
            headerCellStyle.setBorderLeft(BorderStyle.THIN);
            headerCellStyle.setBorderRight(BorderStyle.THIN);

            CellStyle currencyStyle = workbook.createCellStyle();
            currencyStyle.setDataFormat(workbook.createDataFormat().getFormat("#,##0.00\" ₺\""));
            currencyStyle.setBorderBottom(BorderStyle.THIN);
            currencyStyle.setBorderTop(BorderStyle.THIN);
            currencyStyle.setBorderLeft(BorderStyle.THIN);
            currencyStyle.setBorderRight(BorderStyle.THIN);

            CellStyle dataStyle = workbook.createCellStyle();
            dataStyle.setBorderBottom(BorderStyle.THIN);
            dataStyle.setBorderTop(BorderStyle.THIN);
            dataStyle.setBorderLeft(BorderStyle.THIN);
            dataStyle.setBorderRight(BorderStyle.THIN);

            String[] columns = {"Çek Tipi", "Çek No", "Banka", "Vade Tarihi", "Tutar", "Karşı Taraf (Cari)", "Durum", "Açıklama"};
            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < columns.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(columns[col]);
                cell.setCellStyle(headerCellStyle);
            }

            int rowIdx = 1;
            for (Cheque cheque : cheques) {
                Row row = sheet.createRow(rowIdx++);

                Cell cell0 = row.createCell(0);
                cell0.setCellValue(cheque.getType().getLabel());
                cell0.setCellStyle(dataStyle);

                Cell cell1 = row.createCell(1);
                cell1.setCellValue(cheque.getChequeNumber());
                cell1.setCellStyle(dataStyle);

                Cell cell2 = row.createCell(2);
                cell2.setCellValue(cheque.getBank());
                cell2.setCellStyle(dataStyle);

                Cell cell3 = row.createCell(3);
                cell3.setCellValue(cheque.getMaturityDate().toString());
                cell3.setCellStyle(dataStyle);

                Cell cell4 = row.createCell(4);
                cell4.setCellValue(cheque.getAmount().doubleValue());
                cell4.setCellStyle(currencyStyle);

                Cell cell5 = row.createCell(5);
                String partyName = cheque.getContact() != null ? cheque.getContact().getName() : cheque.getParty();
                cell5.setCellValue(partyName != null ? partyName : "-");
                cell5.setCellStyle(dataStyle);

                Cell cell6 = row.createCell(6);
                cell6.setCellValue(cheque.getStatus().getLabel());
                cell6.setCellStyle(dataStyle);

                Cell cell7 = row.createCell(7);
                cell7.setCellValue(cheque.getDescription() != null ? cheque.getDescription() : "-");
                cell7.setCellStyle(dataStyle);
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Çek Excel dışa aktarımında hata oluştu: " + e.getMessage(), e);
        }
    }
}
