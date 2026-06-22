package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.raspel.cardtracker.ui.utils.FormatUtils;
import jakarta.annotation.security.PermitAll;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Route(value = "average-maturity", layout = MainLayout.class)
@PageTitle("Ortalama Vade Hesaplama")
@PermitAll
public class AverageMaturityView extends VerticalLayout {

    private final Grid<TxnRow> txnGrid = new Grid<>(TxnRow.class, false);
    private final Div resultBox = new Div();
    private final VerticalLayout txnList = new VerticalLayout();

    public AverageMaturityView() {
        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("padding-top", "48px");

        H3 title = new H3("Ortalama Vade Hesaplama");
        title.getStyle().set("margin-top", "0");

        Span info = new Span("CSV veya Excel dosyası yükleyin. F sütunu = Toplam Tutar, G sütunu = Taksit Sayısı");
        info.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "0.9em");

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".csv", ".xlsx", ".xls");
        upload.setMaxFiles(1);
        upload.setDropLabel(new Span("Dosyayı buraya sürükleyin (CSV veya Excel)"));

        upload.addSucceededListener(event -> {
            try (InputStream is = buffer.getInputStream()) {
                String fileName = event.getFileName().toLowerCase();
                List<Transaction> transactions;
                if (fileName.endsWith(".csv")) {
                    transactions = parseCSV(is);
                } else {
                    transactions = parseExcel(is);
                }

                if (transactions.isEmpty()) {
                    Notification.show("Veri okunamadı. F=Tutar ve G=Taksit sütunlarını kontrol edin.", 5000, Notification.Position.MIDDLE)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }

                showResult(transactions);
                Notification.show(transactions.size() + " işlem okundu.", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show("Bir hata oluştu, lütfen tekrar deneyin.", 8000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        resultBox.getStyle()
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("padding", "1.5em 2em")
                .set("border-radius", "12px")
                .set("margin-top", "1em");
        resultBox.setVisible(false);

        txnList.setPadding(false);
        txnList.setSpacing(false);
        txnList.setVisible(false);

        txnGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        txnGrid.setWidthFull();
        txnGrid.setPageSize(100);
        txnGrid.addColumn(TxnRow::getRow).setHeader("#").setWidth("50px").setFlexGrow(0);
        txnGrid.addColumn(r -> FormatUtils.formatNumber(r.getAmount()) + " ₺").setHeader("Tutar").setAutoWidth(true);
        txnGrid.addColumn(TxnRow::getInstallments).setHeader("Taksit").setWidth("70px").setFlexGrow(0);
        txnGrid.addColumn(TxnRow::getDesc).setHeader("Açıklama").setAutoWidth(true).setFlexGrow(1);
        txnGrid.setVisible(false);

        add(title, info, upload, resultBox, txnGrid);
    }

    private List<Transaction> parseCSV(InputStream is) throws Exception {
        List<Transaction> transactions = new ArrayList<>();
        byte[] bytes = is.readAllBytes();

        byte[] trimmedBytes = bytes;
        if (bytes.length >= 3 && bytes[0] == (byte)0xEF && bytes[1] == (byte)0xBB && bytes[2] == (byte)0xBF) {
            trimmedBytes = java.util.Arrays.copyOfRange(bytes, 3, bytes.length);
        }

        String content = new String(trimmedBytes, StandardCharsets.UTF_8);

        if (content.contains("\uFFFD")) {
            content = new String(trimmedBytes, java.nio.charset.Charset.forName("Windows-1254"));
        }

        String[] lines = content.split("\\r?\\n");
        if (lines.length == 0) return transactions;

        String headerLine = lines[0].trim();
        String delim = headerLine.contains(";") ? ";" : ",";
        String[] headers = headerLine.split(delim);
        int amountCol = -1, installmentCol = -1;

        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().toLowerCase(Locale.of("tr")).replace("\"", "");
            if (h.contains("tutar") || h.contains("amount")) amountCol = i;
            if (h.contains("taksit") || h.contains("installment")) installmentCol = i;
        }

        if (amountCol < 0 || installmentCol < 0) {
            amountCol = 5;
            installmentCol = 6;
        }

        int totalRows = 0;
        for (int li = 1; li < lines.length; li++) {
            String line = lines[li].trim();
            if (line.isEmpty()) continue;
            totalRows++;
            String[] cols = line.split(delim);
            if (cols.length <= Math.max(amountCol, installmentCol)) continue;

            try {
                double amount = parseNumber(cols[amountCol]);
                int installments = parseInt(cols[installmentCol]);
                if (amount <= 0 || installments <= 0) continue;

                String desc = cols.length > 7 ? cols[7] : "";
                transactions.add(new Transaction(BigDecimal.valueOf(amount), installments, desc));
            } catch (Exception ignored) {
            }
        }

        if (transactions.isEmpty() && totalRows > 0) {
            throw new RuntimeException(totalRows + " satır tarandı ancak F(Tutar) ve G(Taksit) okunamadı. Sütun: " + amountCol + "/" + installmentCol);
        }
        return transactions;
    }

    private List<Transaction> parseExcel(InputStream is) throws Exception {
        List<Transaction> transactions = new ArrayList<>();
        org.apache.poi.ss.usermodel.Workbook workbook =
                org.apache.poi.ss.usermodel.WorkbookFactory.create(is);
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);

        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            org.apache.poi.ss.usermodel.Row row = sheet.getRow(r);
            if (row == null) continue;

            try {
                double amount = getCellNumeric(row.getCell(5));  // F
                int installments = getCellInt(row.getCell(6));    // G

                if (amount <= 0 || installments <= 0) continue;

                String desc = row.getCell(7) != null ? row.getCell(7).toString() : "";
                transactions.add(new Transaction(BigDecimal.valueOf(amount), installments, desc));
            } catch (Exception ignored) {
                // Expected: unparseable row, skip silently
            }
        }
        workbook.close();
        return transactions;
    }

    private double getCellNumeric(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return 0;
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
            return cell.getNumericCellValue();
        }
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.FORMULA) {
            try { return cell.getNumericCellValue(); } catch (Exception e) {
                // Expected: FORMULA cell may not be numeric
            }
        }
        return parseNumber(cell.toString().trim());
    }

    private int getCellInt(org.apache.poi.ss.usermodel.Cell cell) {
        if (cell == null) return 0;
        if (cell.getCellType() == org.apache.poi.ss.usermodel.CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        }
        return parseInt(cell.toString().trim());
    }

    private double parseNumber(String val) {
        if (val == null || val.isEmpty()) return 0;
        val = val.replace("\"", "").trim().replace(" TL", "").replace(" ₺", "");
        try {
            int dots = val.length() - val.replace(".", "").length();
            int commas = val.length() - val.replace(",", "").length();

            if (dots > 0 && commas > 0) {
                int lastDot = val.lastIndexOf(".");
                int lastComma = val.lastIndexOf(",");
                val = (lastDot > lastComma) ? val.replace(",", "")
                        : val.replace(".", "").replace(",", ".");
            } else if (commas > 0 && dots == 0) {
                val = val.replace(",", ".");
            } else if (dots > 0 && commas == 0) {
                int afterDot = val.length() - val.lastIndexOf(".") - 1;
                if (afterDot > 2) val = val.replace(".", "");
            }

            val = val.replaceAll("[^0-9.]", "");
            return val.isEmpty() ? 0 : Double.parseDouble(val);
        } catch (Exception e) {
            return 0;
        }
    }

    private int parseInt(String val) {
        if (val == null || val.isEmpty()) return 0;
        val = val.replaceAll("[^0-9]", "");
        try { return val.isEmpty() ? 0 : Integer.parseInt(val); } catch (Exception e) { return 0; }
    }

    private void showResult(List<Transaction> transactions) {
        BigDecimal grandTotal = BigDecimal.ZERO;
        BigDecimal weightedDaySum = BigDecimal.ZERO;
        int totalInstallmentCount = 0;
        LocalDate today = LocalDate.now();
        List<TxnRow> rows = new ArrayList<>();

        for (int i = 0; i < transactions.size(); i++) {
            Transaction t = transactions.get(i);
            grandTotal = grandTotal.add(t.amount);
            totalInstallmentCount += t.installments;

            BigDecimal perIns = t.amount.divide(BigDecimal.valueOf(t.installments), 2, RoundingMode.HALF_UP);
            LocalDate firstDue = today.plusMonths(1).withDayOfMonth(1);

            for (int j = 0; j < t.installments; j++) {
                BigDecimal insAmt = (j == t.installments - 1)
                        ? t.amount.subtract(perIns.multiply(BigDecimal.valueOf(t.installments - 1)))
                        : perIns;
                LocalDate due = firstDue.plusMonths(j);
                long days = Math.max(1, ChronoUnit.DAYS.between(today, due));
                weightedDaySum = weightedDaySum.add(insAmt.multiply(BigDecimal.valueOf(days)));
            }

            String desc = t.desc.length() > 40 ? t.desc.substring(0, 40) + "..." : t.desc;
            rows.add(new TxnRow(i + 1, t.amount, t.installments, desc));
        }

        txnGrid.setItems(rows);
        txnGrid.setVisible(true);

        BigDecimal avgDays = weightedDaySum.divide(grandTotal, 4, RoundingMode.HALF_UP);
        BigDecimal avgMonths = avgDays.divide(BigDecimal.valueOf(30.4375), 2, RoundingMode.HALF_UP);
        LocalDate avgDate = today.plusDays(avgDays.longValue());

        resultBox.removeAll();
        resultBox.setVisible(true);

        Span label = new Span("Ortalama Vade Sonucu");
        label.getStyle().set("font-size", "0.85em").set("color", "var(--lumo-secondary-text-color)");

        Span value = new Span(avgMonths + " ay (" + avgDays.longValue() + " gün)");
        value.getStyle().set("font-size", "1.5em").set("font-weight", "700").set("display", "block").set("margin-top", "0.3em");

        Span dateSpan = new Span("Tahmini ortalama vade tarihi: " +
                avgDate.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.of("tr"))));
        dateSpan.getStyle().set("font-size", "1em").set("color", "var(--lumo-primary-text-color)")
                .set("display", "block").set("margin-top", "0.3em");

        Span stats = new Span(String.format("Toplam Tutar: %s ₺  |  İşlem Sayısı: %d  |  Toplam Taksit: %d",
                FormatUtils.formatNumber(grandTotal), transactions.size(), totalInstallmentCount));
        stats.getStyle().set("font-size", "0.9em").set("color", "var(--lumo-body-text-color)")
                .set("display", "block").set("margin-top", "0.8em");

        resultBox.add(label, value, dateSpan, stats);
    }

    public static class TxnRow {
        private final int row;
        private final BigDecimal amount;
        private final int installments;
        private final String desc;
        public TxnRow(int row, BigDecimal amount, int installments, String desc) {
            this.row = row; this.amount = amount; this.installments = installments; this.desc = desc;
        }
        public int getRow() { return row; }
        public BigDecimal getAmount() { return amount; }
        public int getInstallments() { return installments; }
        public String getDesc() { return desc; }
    }

    private static class Transaction {
        final BigDecimal amount;
        final int installments;
        final String desc;

        Transaction(BigDecimal amount, int installments, String desc) {
            this.amount = amount;
            this.installments = installments;
            this.desc = desc;
        }
    }
}
