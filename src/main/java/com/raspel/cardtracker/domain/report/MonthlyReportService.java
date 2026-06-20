package com.raspel.cardtracker.domain.report;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.raspel.cardtracker.domain.budget.DepartmentBudget;
import com.raspel.cardtracker.domain.budget.DepartmentBudgetService;
import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.domain.card.CardService;
import com.raspel.cardtracker.domain.cheque.Cheque;
import com.raspel.cardtracker.domain.cheque.ChequeService;
import com.raspel.cardtracker.domain.cheque.ChequeType;
import com.raspel.cardtracker.domain.expense.ExpenseService;
import com.raspel.cardtracker.domain.expense.InstallmentEntry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
@RequiredArgsConstructor
public class MonthlyReportService {

    private final ExpenseService expenseService;
    private final CardService cardService;
    private final ChequeService chequeService;
    private final DepartmentBudgetService budgetService;

    private String formatTL(BigDecimal amount) {
        if (amount == null) {
            return "0,00 TL";
        }
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.of("tr", "TR"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount) + " TL";
    }

    public ByteArrayInputStream generateMonthlyReport(int year, int month) {
        Document document = new Document(PageSize.A4, 36, 36, 36, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Font tanımlamaları (Cp1254 Türkçe karakter desteği için)
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, "Cp1254", true, 18);
            Font subtitleFont = FontFactory.getFont(FontFactory.HELVETICA, "Cp1254", true, 12);
            Font sectionFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, "Cp1254", true, 14);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, "Cp1254", true, 10);
            Font boldCellFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, "Cp1254", true, 9);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, "Cp1254", true, 9);

            // Başlık
            String monthName = java.time.Month.of(month).getDisplayName(TextStyle.FULL, Locale.of("tr", "TR"));
            Paragraph title = new Paragraph("Bozkir Agac Urunleri", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);

            Paragraph subtitle = new Paragraph(monthName + " " + year + " Finansal Özet Raporu", subtitleFont);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingAfter(20);
            document.add(subtitle);

            // Data Fetching
            List<InstallmentEntry> installments = expenseService.getInstallmentsForMonth(year, month);
            List<Card> activeCards = cardService.findAllActive();
            Map<String, BigDecimal> cardTotals = expenseService.getCardTotalsForMonth(year, month, activeCards);
            
            List<Cheque> monthlyCheques = chequeService.findAll().stream()
                    .filter(c -> c.getMaturityDate().getYear() == year && c.getMaturityDate().getMonthValue() == month)
                    .collect(Collectors.toList());

            List<DepartmentBudget> budgets = budgetService.findByYearAndMonth(year, month);

            // Hesaplamalar
            BigDecimal totalSpent = installments.stream()
                    .map(InstallmentEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal paidSpent = installments.stream()
                    .filter(InstallmentEntry::getIsPaid)
                    .map(InstallmentEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal unpaidSpent = totalSpent.subtract(paidSpent);

            BigDecimal incomingChequesTotal = monthlyCheques.stream()
                    .filter(c -> c.getType() == ChequeType.ENTERING)
                    .map(Cheque::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal outgoingChequesTotal = monthlyCheques.stream()
                    .filter(c -> c.getType() == ChequeType.EXITING)
                    .map(Cheque::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            // --- BÖLÜM 1: GENEL FİNANSAL ÖZET ---
            Paragraph sec1 = new Paragraph("1. Genel Finansal Özet", sectionFont);
            sec1.setSpacingBefore(15);
            sec1.setSpacingAfter(10);
            document.add(sec1);

            PdfPTable summaryTable = new PdfPTable(2);
            summaryTable.setWidthPercentage(100);
            summaryTable.setWidths(new float[]{3f, 2f});

            addSummaryRow(summaryTable, "Toplam Kredi Kartı Harcaması (Taksitler)", formatTL(totalSpent), boldCellFont, cellFont);
            addSummaryRow(summaryTable, " - Ödenen Taksit Tutarı", formatTL(paidSpent), cellFont, cellFont);
            addSummaryRow(summaryTable, " - Ödenmesi Bekleyen Taksit Tutarı", formatTL(unpaidSpent), cellFont, cellFont);
            addSummaryRow(summaryTable, "Toplam Alınan Çek Tutarı (Giriş)", formatTL(incomingChequesTotal), boldCellFont, cellFont);
            addSummaryRow(summaryTable, "Toplam Verilen Çek Tutarı (Çıkış)", formatTL(outgoingChequesTotal), boldCellFont, cellFont);
            
            BigDecimal netCashflow = incomingChequesTotal.subtract(outgoingChequesTotal).subtract(totalSpent);
            addSummaryRow(summaryTable, "Net Nakit Akışı Tahmini (Çekler - Giderler)", formatTL(netCashflow), boldCellFont, boldCellFont);

            document.add(summaryTable);

            // --- BÖLÜM 2: KART BAZLI DAĞILIM ---
            if (!cardTotals.isEmpty()) {
                Paragraph sec2 = new Paragraph("2. Kart Bazlı Harcama Dağılımı", sectionFont);
                sec2.setSpacingBefore(15);
                sec2.setSpacingAfter(10);
                document.add(sec2);

                PdfPTable cardTable = new PdfPTable(2);
                cardTable.setWidthPercentage(100);
                cardTable.setWidths(new float[]{3f, 2f});

                for (Map.Entry<String, BigDecimal> entry : cardTotals.entrySet()) {
                    cardTable.addCell(createCell(entry.getKey(), cellFont));
                    cardTable.addCell(createCell(formatTL(entry.getValue()), cellFont, Element.ALIGN_RIGHT));
                }
                document.add(cardTable);
            }

            // --- BÖLÜM 3: DEPARTMAN BÜTÇE DURUMU ---
            if (!budgets.isEmpty()) {
                Paragraph sec3 = new Paragraph("3. Departman Bütçe Durumları", sectionFont);
                sec3.setSpacingBefore(15);
                sec3.setSpacingAfter(10);
                document.add(sec3);

                PdfPTable budgetTable = new PdfPTable(4);
                budgetTable.setWidthPercentage(100);
                budgetTable.setWidths(new float[]{2f, 1.5f, 1.5f, 1f});

                String[] bHeaders = {"Departman", "Bütçe Limiti", "Gerçekleşen", "Kullanım %"};
                for (String bh : bHeaders) {
                    PdfPCell hCell = new PdfPCell(new Phrase(bh, headerFont));
                    hCell.setBackgroundColor(new java.awt.Color(0, 128, 128));
                    hCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    hCell.setPadding(5);
                    hCell.getPhrase().getFont().setColor(java.awt.Color.WHITE);
                    budgetTable.addCell(hCell);
                }

                for (DepartmentBudget budget : budgets) {
                    String deptName = budget.getDepartment() != null ? budget.getDepartment().getName() : "";
                    BigDecimal spent = expenseService.getDepartmentSpentForMonth(deptName, year, month);
                    BigDecimal limit = budget.getBudgetLimit();
                    double pct = limit.compareTo(BigDecimal.ZERO) > 0 
                            ? spent.divide(limit, 4, RoundingMode.HALF_UP).doubleValue() * 100 
                            : 0;

                    budgetTable.addCell(createCell(deptName, cellFont));
                    budgetTable.addCell(createCell(formatTL(limit), cellFont, Element.ALIGN_RIGHT));
                    budgetTable.addCell(createCell(formatTL(spent), cellFont, Element.ALIGN_RIGHT));
                    budgetTable.addCell(createCell(String.format("%%%.1f", pct), cellFont, Element.ALIGN_CENTER));
                }
                document.add(budgetTable);
            }

            // --- BÖLÜM 4: MEVCUT AY TAKSİT DETAYLARI ---
            if (!installments.isEmpty()) {
                Paragraph sec4 = new Paragraph("4. Kredi Kartı Taksit Detayları", sectionFont);
                sec4.setSpacingBefore(15);
                sec4.setSpacingAfter(10);
                document.add(sec4);

                PdfPTable instTable = new PdfPTable(5);
                instTable.setWidthPercentage(100);
                instTable.setWidths(new float[]{2f, 3f, 1.5f, 1.5f, 1.2f});

                String[] iHeaders = {"Kart Adı", "Acıklama", "Taksit Tutarı", "Kategori", "Durum"};
                for (String ih : iHeaders) {
                    PdfPCell hCell = new PdfPCell(new Phrase(ih, headerFont));
                    hCell.setBackgroundColor(new java.awt.Color(33, 150, 243));
                    hCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                    hCell.setPadding(5);
                    hCell.getPhrase().getFont().setColor(java.awt.Color.WHITE);
                    instTable.addCell(hCell);
                }

                for (InstallmentEntry entry : installments) {
                    instTable.addCell(createCell(entry.getExpense().getCard().getName(), cellFont));
                    instTable.addCell(createCell(entry.getExpense().getDescription(), cellFont));
                    instTable.addCell(createCell(formatTL(entry.getAmount()), cellFont, Element.ALIGN_RIGHT));
                    instTable.addCell(createCell(entry.getExpense().getCategory() != null ? entry.getExpense().getCategory() : "-", cellFont));
                    instTable.addCell(createCell(entry.getIsPaid() ? "Odedi" : "Bekliyor", cellFont, Element.ALIGN_CENTER));
                }
                document.add(instTable);
            }

            document.close();
        } catch (Exception e) {
            throw new RuntimeException("PDF oluşturulamadı: " + e.getMessage(), e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    private void addSummaryRow(PdfPTable table, String label, String value, Font labelFont, Font valFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, labelFont));
        labelCell.setPadding(6);
        table.addCell(labelCell);

        PdfPCell valCell = new PdfPCell(new Phrase(value, valFont));
        valCell.setPadding(6);
        valCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.addCell(valCell);
    }

    private PdfPCell createCell(String text, Font font) {
        return createCell(text, font, Element.ALIGN_LEFT);
    }

    private PdfPCell createCell(String text, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : "", font));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(5);
        return cell;
    }
}
