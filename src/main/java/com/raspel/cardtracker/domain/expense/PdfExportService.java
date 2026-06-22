package com.raspel.cardtracker.domain.expense;

import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import com.raspel.cardtracker.domain.cheque.Cheque;
import com.raspel.cardtracker.domain.cheque.ChequeType;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Service
public class PdfExportService {

    private String formatTL(BigDecimal amount) {
        if (amount == null) {
            return "0,00 TL";
        }
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.of("tr", "TR"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return nf.format(amount) + " TL";
    }
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public ByteArrayInputStream exportInstallments(List<InstallmentEntry> entries) {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // Fontlar (Türkçe Karakter Desteği için Cp1254)
            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, "Cp1254", true, 16);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, "Cp1254", true, 10);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, "Cp1254", true, 9);

            // Başlık
            Paragraph title = new Paragraph("Kredi Kartı Taksit Ödemeleri Raporu", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            // Tablo Oluşturma
            PdfPTable table = new PdfPTable(9); // 9 Kolon
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2f, 2.5f, 1.5f, 1.2f, 1.2f, 1.2f, 1f, 1.2f, 1f});

            // Başlık satırı
            String[] headers = {"Kart Adı", "Açıklama", "Taksit Tutarı", "Toplam", "Döviz", "Vade", "Taksit", "Kategori", "Durum"};
            for (String headerText : headers) {
                PdfPCell headerCell = new PdfPCell(new Phrase(headerText, headerFont));
                headerCell.setBackgroundColor(new java.awt.Color(0, 128, 128)); // Teal
                headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                headerCell.setPadding(6);
                headerCell.getPhrase().getFont().setColor(java.awt.Color.WHITE);
                table.addCell(headerCell);
            }

            BigDecimal totalAmountSum = BigDecimal.ZERO;

            for (InstallmentEntry entry : entries) {
                Expense exp = entry.getExpense();

                table.addCell(createCell(exp.getCard() != null ? exp.getCard().getName() : "-", cellFont));
                table.addCell(createCell(exp.getDescription(), cellFont));

                // Taksit Tutarı
                table.addCell(createCell(formatTL(entry.getAmount()), cellFont, Element.ALIGN_RIGHT));
                totalAmountSum = totalAmountSum.add(entry.getAmount());

                // Toplam Tutar
                table.addCell(createCell(formatTL(exp.getTotalAmount()), cellFont, Element.ALIGN_RIGHT));

                // Döviz
                String currencyStr = "TRY".equalsIgnoreCase(exp.getCurrency()) ? "-" 
                        : String.format("%.2f %s", exp.getOriginalAmount(), exp.getCurrency());
                table.addCell(createCell(currencyStr, cellFont, Element.ALIGN_CENTER));

                // Vade
                table.addCell(createCell(entry.getDueMonth() + "/" + entry.getDueYear(), cellFont, Element.ALIGN_CENTER));

                // Taksit No
                table.addCell(createCell(exp.getInstallments() + " Taksit", cellFont, Element.ALIGN_CENTER));

                // Kategori
                table.addCell(createCell(exp.getCategory() != null ? exp.getCategory() : "-", cellFont));

                // Durum
                String statusStr = entry.getIsPaid() ? "Ödendi" : "Bekliyor";
                table.addCell(createCell(statusStr, cellFont, Element.ALIGN_CENTER));
            }

            document.add(table);

            // Alt Toplam Bilgisi
            Paragraph totalParagraph = new Paragraph("\nToplam Taksit Tutarı: " + formatTL(totalAmountSum), titleFont);
            totalParagraph.setAlignment(Element.ALIGN_RIGHT);
            document.add(totalParagraph);

            document.close();
        } catch (Exception e) {
            throw new RuntimeException("PDF oluşturulamadı, lütfen daha sonra tekrar deneyin.", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
    }

    public ByteArrayInputStream exportCheques(List<Cheque> cheques) {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 36, 36);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        try {
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, "Cp1254", true, 16);
            Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, "Cp1254", true, 10);
            Font cellFont = FontFactory.getFont(FontFactory.HELVETICA, "Cp1254", true, 9);

            Paragraph title = new Paragraph("Çek Portföyü / Vade Takip Raporu", titleFont);
            title.setAlignment(Element.ALIGN_CENTER);
            title.setSpacingAfter(20);
            document.add(title);

            PdfPTable table = new PdfPTable(8);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{1f, 1.5f, 2f, 1.2f, 1.5f, 2.5f, 1.2f, 1.2f});

            String[] headers = {"Tip", "Çek No", "Banka", "Vade Tarihi", "Tutar", "Karşı Taraf (Cari)", "Durum", "Açıklama"};
            for (String headerText : headers) {
                PdfPCell headerCell = new PdfPCell(new Phrase(headerText, headerFont));
                headerCell.setBackgroundColor(new java.awt.Color(46, 125, 50)); // Koyu Yeşil
                headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
                headerCell.setPadding(6);
                headerCell.getPhrase().getFont().setColor(java.awt.Color.WHITE);
                table.addCell(headerCell);
            }

            BigDecimal totalIn = BigDecimal.ZERO;
            BigDecimal totalOut = BigDecimal.ZERO;

            for (Cheque cheque : cheques) {
                table.addCell(createCell(cheque.getType().getLabel(), cellFont, Element.ALIGN_CENTER));
                table.addCell(createCell(cheque.getChequeNumber(), cellFont));
                table.addCell(createCell(cheque.getBank(), cellFont));
                table.addCell(createCell(cheque.getMaturityDate().format(dateFormatter), cellFont, Element.ALIGN_CENTER));
                
                table.addCell(createCell(formatTL(cheque.getAmount()), cellFont, Element.ALIGN_RIGHT));
                if (cheque.getType() == ChequeType.ENTERING) {
                    totalIn = totalIn.add(cheque.getAmount());
                } else {
                    totalOut = totalOut.add(cheque.getAmount());
                }

                String partyName = cheque.getContact() != null ? cheque.getContact().getName() : cheque.getParty();
                table.addCell(createCell(partyName != null ? partyName : "-", cellFont));
                table.addCell(createCell(cheque.getStatus().getLabel(), cellFont, Element.ALIGN_CENTER));
                table.addCell(createCell(cheque.getDescription() != null ? cheque.getDescription() : "-", cellFont));
            }

            document.add(table);

            Paragraph summary = new Paragraph("\nToplam Alınan (Giriş) Çekler: " + formatTL(totalIn) +
                    "  |  Toplam Verilen (Çıkış) Çekler: " + formatTL(totalOut), headerFont);
            summary.setAlignment(Element.ALIGN_RIGHT);
            document.add(summary);

            document.close();
        } catch (Exception e) {
            throw new RuntimeException("PDF oluşturulamadı, lütfen daha sonra tekrar deneyin.", e);
        }

        return new ByteArrayInputStream(out.toByteArray());
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
