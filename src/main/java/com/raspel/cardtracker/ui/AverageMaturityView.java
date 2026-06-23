package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.data.renderer.ComponentRenderer;
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

    private static final String DARK = "var(--lumo-body-text-color)";
    private static final String BLUE = "#3498db";
    private static final String LIGHT_BG = "var(--lumo-contrast-5pct)";

    private final Grid<TxnRow> txnGrid = new Grid<>(TxnRow.class, false);
    private final Div resultBox = new Div();
    private final Upload upload;

    public AverageMaturityView() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle().set("overflow-y", "auto");

        Div container = new Div();
        container.getStyle()
                .set("max-width", "1200px")
                .set("margin", "0 auto")
                .set("padding", "48px 24px 24px 24px")
                .set("width", "100%");

        H3 title = new H3("Ortalama Vade Hesaplama");
        title.getStyle()
                .set("margin", "0 0 8px 0")
                .set("font-size", "24px")
                .set("font-weight", "700")
                .set("color", DARK);

        Span info = new Span("CSV veya Excel dosyası yükleyin. F sütunu = Toplam Tutar, G sütunu = Taksit Sayısı");
        info.getStyle()
                .set("color", "#7f8c8d")
                .set("font-size", "14px")
                .set("display", "block")
                .set("margin-bottom", "20px");

        MemoryBuffer buffer = new MemoryBuffer();
        upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".csv", ".xlsx", ".xls");
        upload.setMaxFiles(1);
        upload.setDropLabel(new Span("Dosyayı buraya sürükleyin veya seçin"));
        upload.getStyle()
                .set("border", "2px dashed " + BLUE)
                .set("border-radius", "12px")
                .set("padding", "24px")
                .set("background", LIGHT_BG)
                .set("width", "100%")
                .set("box-sizing", "border-box");

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

        configureGrid();

        resultBox.setVisible(false);
        txnGrid.setVisible(false);

        container.add(title, info, upload, resultBox, txnGrid);
        add(container);
    }

    private void configureGrid() {
        txnGrid.setWidthFull();
        txnGrid.getStyle().set("max-height", "280px").set("margin-top", "20px");
        txnGrid.setColumnReorderingAllowed(true);

        txnGrid.addColumn(TxnRow::getRow).setHeader("#")
                .setWidth("60px").setFlexGrow(0)
                .setClassNameGenerator(col -> "col-center");

        txnGrid.addColumn(r -> FormatUtils.formatNumber(r.getAmount()) + " ₺").setHeader("Tutar")
                .setAutoWidth(true)
                .setClassNameGenerator(col -> "col-right");

        txnGrid.addColumn(new ComponentRenderer<>(txn -> {
            int ins = txn.getInstallments();
            Span badge = new Span(ins == 0 ? "0" : String.valueOf(ins));
            badge.getStyle()
                    .set("display", "inline-block")
                    .set("padding", "2px 8px")
                    .set("border-radius", "10px")
                    .set("font-size", "12px")
                    .set("font-weight", "600");
            if (ins == 0) {
                badge.getStyle()
                        .set("background", "#fdecea")
                        .set("color", "#d32f2f");
            } else {
                badge.getStyle()
                        .set("background", "var(--lumo-contrast-10pct)")
                        .set("color", DARK);
            }
            return badge;
        })).setHeader("Taksit").setWidth("80px").setFlexGrow(0)
                .setClassNameGenerator(col -> "col-center");

        txnGrid.addColumn(TxnRow::getDesc).setHeader("Açıklama")
                .setAutoWidth(true).setFlexGrow(1);

        txnGrid.addClassName("vade-grid");

        txnGrid.getElement().executeJs(
            "var existing=document.getElementById('vade-grid-style');if(existing)existing.remove();" +
            "var style=document.createElement('style');style.id='vade-grid-style';" +
            "style.textContent='vaadin-grid.vade-grid::part(row):nth-child(even) { background: var(--lumo-contrast-5pct); }" +
            "vaadin-grid.vade-grid::part(header-cell) { background: #2c3e50; color: #fff; font-weight: 600; font-size: 13px; }" +
            ".col-right { text-align: right !important; }" +
            ".col-center { text-align: center !important; }';" +
            "document.head.appendChild(style);");
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
                if (amount <= 0) continue;

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
                double amount = getCellNumeric(row.getCell(5));
                int installments = getCellInt(row.getCell(6));

                if (amount <= 0) continue;

                String desc = row.getCell(7) != null ? row.getCell(7).toString() : "";
                transactions.add(new Transaction(BigDecimal.valueOf(amount), installments, desc));
            } catch (Exception ignored) {
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
            try { return cell.getNumericCellValue(); } catch (Exception e) {}
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

    private int getVadeDays(int taksit) {
        return Math.max(1, taksit) * 30;
    }

    private void showResult(List<Transaction> transactions) {
        BigDecimal grandTotal = BigDecimal.ZERO;
        BigDecimal tutarliVadeSum = BigDecimal.ZERO;
        long islemVadeSum = 0;
        int txnCount = transactions.size();
        List<TxnRow> rows = new ArrayList<>();

        for (int i = 0; i < transactions.size(); i++) {
            Transaction t = transactions.get(i);
            grandTotal = grandTotal.add(t.amount);
            int vadeDays = getVadeDays(t.installments);
            islemVadeSum += vadeDays;
            tutarliVadeSum = tutarliVadeSum.add(t.amount.multiply(BigDecimal.valueOf(vadeDays)));
            String desc = t.desc.length() > 40 ? t.desc.substring(0, 40) + "..." : t.desc;
            rows.add(new TxnRow(i + 1, t.amount, t.installments, desc));
        }

        BigDecimal islemGun = txnCount > 0
                ? BigDecimal.valueOf(islemVadeSum).divide(BigDecimal.valueOf(txnCount), 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal tutarGun = grandTotal.compareTo(BigDecimal.ZERO) > 0
                ? tutarliVadeSum.divide(grandTotal, 0, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        BigDecimal islemAy = islemGun.divide(BigDecimal.valueOf(30), 1, RoundingMode.HALF_UP);
        BigDecimal tutarAy = tutarGun.divide(BigDecimal.valueOf(30), 1, RoundingMode.HALF_UP);
        long maxGun = Math.max(islemGun.longValue(), tutarGun.longValue());
        if (maxGun == 0) maxGun = 1;

        upload.setVisible(false);

        resultBox.removeAll();
        resultBox.getStyle().clear();
        resultBox.setVisible(true);
        resultBox.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "16px")
                .set("padding", "24px")
                .set("box-shadow", "0 2px 12px rgba(0,0,0,0.06)")
                .set("margin-bottom", "20px");

        HorizontalLayout cardsRow = new HorizontalLayout();
        cardsRow.setWidthFull();
        cardsRow.setSpacing(true);
        cardsRow.getStyle().set("gap", "20px");

        cardsRow.add(buildCard("İşlem Bazlı Vade",
                "Her işlem eşit ağırlıkta değerlendirilir. Müşterilerin\nortalama kaç gün vadeli alışveriş yaptığını gösterir.",
                islemGun, islemAy, maxGun, BLUE));
        cardsRow.add(buildCard("Tutar Bazlı Tahsilat Vadesi",
                "Yüksek tutarlı işlemler daha fazla ağırlığa sahiptir.\nParanın ortalama kaç günde tahsil edildiğini gösterir.",
                tutarGun, tutarAy, maxGun, "#27ae60"));

        Span summary = new Span(String.format("%s ₺ toplam tutar  ·  %d işlem  ·  vade aralığı %s - %s gün",
                FormatUtils.formatNumber(grandTotal), txnCount, islemGun.toPlainString(), tutarGun.toPlainString()));
        summary.getStyle()
                .set("font-size", "13px")
                .set("color", "#7f8c8d")
                .set("display", "block")
                .set("text-align", "center")
                .set("margin-top", "16px");

        resultBox.add(cardsRow, summary);

        txnGrid.setItems(rows);
        txnGrid.setVisible(true);
    }

    private Div buildCard(String title, String desc, BigDecimal gun, BigDecimal ay, long maxGun, String color) {
        Div card = new Div();
        card.getStyle()
                .set("flex", "1")
                .set("padding", "20px")
                .set("border-radius", "12px")
                .set("background", "var(--lumo-base-color)")
                .set("box-shadow", "0 1px 6px rgba(0,0,0,0.08)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("min-width", "0");

        Span titleSpan = new Span(title);
        titleSpan.getStyle()
                .set("font-weight", "700")
                .set("font-size", "14px")
                .set("color", DARK)
                .set("display", "block");

        Span descSpan = new Span(desc);
        descSpan.getStyle()
                .set("font-size", "12px")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block")
                .set("margin-top", "4px")
                .set("white-space", "pre-line")
                .set("line-height", "1.4");

        HorizontalLayout valLine = new HorizontalLayout();
        valLine.setSpacing(false);
        valLine.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.BASELINE);
        valLine.getStyle().set("margin-top", "12px");

        Span bigNum = new Span(gun.toPlainString());
        bigNum.getStyle().set("font-size", "28px").set("font-weight", "800").set("color", color).set("line-height", "1");

        Span gunLabel = new Span(" gün");
        gunLabel.getStyle().set("font-size", "13px").set("color", "var(--lumo-secondary-text-color)").set("margin-left", "4px");

        valLine.add(bigNum, gunLabel);

        Span ayLabel = new Span(ay.stripTrailingZeros().toPlainString() + " ay");
        ayLabel.getStyle().set("font-size", "12px").set("color", "var(--lumo-tertiary-text-color)").set("display", "block").set("margin-top", "4px");

        Div bar = new Div();
        bar.getStyle()
                .set("height", "6px")
                .set("border-radius", "3px")
                .set("background", "var(--lumo-contrast-10pct)")
                .set("margin-top", "12px")
                .set("overflow", "hidden");

        int pct = (int) Math.min(100, gun.longValue() * 100 / maxGun);
        Div fill = new Div();
        fill.getStyle()
                .set("height", "100%")
                .set("width", pct + "%")
                .set("background", color)
                .set("border-radius", "3px")
                .set("transition", "width 0.5s ease");

        bar.add(fill);
        card.add(titleSpan, descSpan, valLine, ayLabel, bar);
        return card;
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
