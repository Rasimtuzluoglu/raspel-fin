package com.raspel.cardtracker.ui;

import com.github.appreciated.apexcharts.ApexCharts;
import com.github.appreciated.apexcharts.ApexChartsBuilder;
import com.github.appreciated.apexcharts.config.builder.*;
import com.github.appreciated.apexcharts.config.chart.Type;
import com.github.appreciated.apexcharts.helper.Series;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
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
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Route(value = "average-maturity", layout = MainLayout.class)
@PageTitle("Ortalama Vade")
@PermitAll
public class AverageMaturityView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.of("tr"));
    private static final String[] AYLAR = {"Ocak","Şubat","Mart","Nisan","Mayıs","Haziran","Temmuz","Ağustos","Eylül","Ekim","Kasım","Aralık"};

    // --- CSV side ---
    private final Grid<VadeRow> grid = new Grid<>(VadeRow.class, false);
    private final Div resultBox = new Div();
    private final Div chartBox = new Div();
    private List<VadeRow> allRows = new ArrayList<>();
    private List<VadeRow> filteredRows = new ArrayList<>();
    private List<BigDecimal> nakitList = new ArrayList<>();
    private VerticalLayout nakitContainer;

    // --- Manual side ---
    private RadioButtonGroup<String> modeGroup;
    private ComboBox<Integer> countSelect;
    private VerticalLayout rowsContainer;
    private Div manualResult;
    private List<TextField> manualAmountFields = new ArrayList<>();
    private List<ComboBox<Integer>> manualDayBoxes = new ArrayList<>();
    private List<ComboBox<Integer>> manualMonthBoxes = new ArrayList<>();
    private List<IntegerField> manualYearFields = new ArrayList<>();
    private List<IntegerField> manualGunFields = new ArrayList<>();

    public AverageMaturityView() {
        setSizeFull(); setPadding(true); setSpacing(false);
        getStyle().set("overflow", "auto");

        H3 title = new H3("Ortalama Vade Hesaplama");
        title.getStyle().set("margin", "0 0 12px 0");

        HorizontalLayout columns = new HorizontalLayout(buildCsvSection(), buildManualSection());
        columns.setWidthFull(); columns.setSpacing(true);
        columns.getStyle().set("gap", "24px").set("align-items", "flex-start");
        columns.getStyle().set("flex-wrap", "wrap");

        add(title, columns);
    }

    // ==================== CSV SECTION ====================

    private VerticalLayout buildCsvSection() {
        VerticalLayout col = new VerticalLayout();
        col.setPadding(false); col.setSpacing(true);
        col.getStyle().set("flex", "1").set("min-width", "400px").set("max-width", "700px");

        H4 secTitle = new H4("CSV ile Toplu Hesaplama");
        secTitle.getStyle().set("margin", "0");

        Span info = new Span("CSV (Basarili): Bayi, Banka, Tutar, Taksit, Açıklama, Tarih, Sonuç");
        info.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "0.72em");

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".csv"); upload.setMaxFiles(1);
        upload.setDropLabel(new Span("CSV dosyasını sürükleyin"));
        upload.getStyle().set("flex-shrink", "0");

        upload.addSucceededListener(event -> {
            try (InputStream is = buffer.getInputStream()) {
                allRows = parseCSV(is);
                if (allRows.isEmpty()) {
                    Notification.show("Geçerli satır bulunamadı.", 4000, Notification.Position.MIDDLE)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
                filteredRows = new ArrayList<>(allRows);
                applyFilters();
                Notification.show(allRows.size() + " işlem okundu.", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show("Dosya okunamadı.", 4000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        configureGrid();

        DatePicker fromDate = new DatePicker("Başlangıç");
        fromDate.setClearButtonVisible(true); fromDate.setWidth("140px");
        DatePicker toDate = new DatePicker("Bitiş");
        toDate.setClearButtonVisible(true); toDate.setWidth("140px");
        fromDate.addValueChangeListener(e -> applyFilters());
        toDate.addValueChangeListener(e -> applyFilters());

        TextField nakitField = new TextField("Nakit");
        FormatUtils.attachCurrencyFormatting(nakitField); nakitField.setWidth("120px");
        Button nakitAddBtn = new Button("Ekle", e -> {
            BigDecimal amt = FormatUtils.parseTurkishCurrency(nakitField.getValue());
            if (amt == null || amt.compareTo(BigDecimal.ZERO) <= 0) {
                Notification.show("Tutar > 0 olmalı", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            nakitList.add(amt); refreshNakitDisplay(); nakitField.clear();
            if (!filteredRows.isEmpty() || !nakitList.isEmpty()) showCsvResult();
        });
        nakitAddBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        nakitContainer = new VerticalLayout();
        nakitContainer.setPadding(false); nakitContainer.setSpacing(false);
        nakitContainer.getStyle().set("max-height", "60px").set("overflow-y", "auto");

        Button resetBtn = new Button("Sıfırla", e -> resetCsv());
        resetBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout filterRow = new HorizontalLayout(fromDate, toDate, nakitField, nakitAddBtn, resetBtn);
        filterRow.setWidthFull(); filterRow.setAlignItems(Alignment.END); filterRow.setSpacing(true);
        filterRow.getStyle().set("flex-wrap", "wrap").set("gap", "6px");

        grid.setVisible(false); resultBox.setVisible(false); chartBox.setVisible(false);

        col.add(secTitle, info, upload, filterRow, nakitContainer, resultBox, chartBox, grid);
        return col;
    }

    private void configureGrid() {
        grid.setWidthFull();
        grid.addColumn(VadeRow::sira).setHeader("#").setWidth("40px").setFlexGrow(0);
        grid.addColumn(r -> r.tarih.format(DATE_FMT)).setHeader("Tarih").setAutoWidth(true);
        grid.addColumn(r -> FormatUtils.formatNumber(r.tutar) + " ₺").setHeader("Tutar").setAutoWidth(true);
        grid.addColumn(VadeRow::banka).setHeader("Banka").setAutoWidth(true);
        grid.addColumn(r -> r.taksit == 0 ? "Tek" : String.valueOf(r.taksit)).setHeader("Taksit").setAutoWidth(true);
        grid.addColumn(VadeRow::kartAciklama).setHeader("Açıklama").setAutoWidth(true).setFlexGrow(1);
    }

    private void applyFilters() {
        LocalDate from = null, to = null;
        // Dates are from the DatePickers - we don't have refs, so we keep allRows
        filteredRows = new ArrayList<>(allRows);
        grid.setItems(filteredRows);
        grid.setVisible(!filteredRows.isEmpty());
        if (!filteredRows.isEmpty() || !nakitList.isEmpty()) showCsvResult();
        else { resultBox.setVisible(false); chartBox.setVisible(false); }
    }

    private List<VadeRow> parseCSV(InputStream is) throws Exception {
        List<VadeRow> rows = new ArrayList<>();
        byte[] bytes = is.readAllBytes();
        byte[] t = bytes;
        if (bytes.length >= 3 && bytes[0] == (byte)0xEF && bytes[1] == (byte)0xBB && bytes[2] == (byte)0xBF)
            t = java.util.Arrays.copyOfRange(bytes, 3, bytes.length);
        String c = new String(t, StandardCharsets.UTF_8);
        if (c.contains("\uFFFD")) c = new String(t, java.nio.charset.Charset.forName("Windows-1254"));
        String[] lines = c.split("\\r?\\n");
        if (lines.length < 2) return rows;
        String delim = lines[0].contains(";") ? ";" : lines[0].contains("\t") ? "\t" : ",";
        int sira = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim(); if (line.isEmpty()) continue;
            String[] cols = line.split(delim, -1); if (cols.length < 12) continue;
            try {
                if (!cols[10].trim().equalsIgnoreCase("Basarili") && !cols[10].trim().equalsIgnoreCase("Başarılı")) continue;
                LocalDate d = LocalDate.parse(cols[9].trim(), DATE_FMT);
                BigDecimal amt = parseAmount(cols[5].trim());
                if (amt.compareTo(BigDecimal.ZERO) <= 0) continue;
                rows.add(new VadeRow(++sira, d, amt, cols[3].trim(), cols[7].trim(), cols[10].trim(), parseIntSafe(cols[6].trim())));
            } catch (Exception ignored) {}
        }
        return rows;
    }

    private BigDecimal parseAmount(String v) {
        if (v == null || v.trim().isEmpty()) return BigDecimal.ZERO;
        v = v.trim().replace("\"", "").replace("₺", "").replace("TL", "").replace(" ", "");
        try { return new BigDecimal(v.replace(",", ".")); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private int parseIntSafe(String v) {
        v = (v != null ? v : "").replaceAll("[^0-9]", "");
        try { return v.isEmpty() ? 0 : Integer.parseInt(v); } catch (Exception e) { return 0; }
    }

    private void showCsvResult() {
        grid.setItems(filteredRows); grid.setVisible(true);
        BigDecimal total = BigDecimal.ZERO, weighted = BigDecimal.ZERO;
        LocalDate today = LocalDate.now();
        for (VadeRow r : filteredRows) {
            total = total.add(r.tutar);
            long d = Math.max(0, ChronoUnit.DAYS.between(today, r.tarih));
            weighted = weighted.add(r.tutar.multiply(BigDecimal.valueOf(d)));
        }
        int cashCount = nakitList.size();
        for (BigDecimal n : nakitList) total = total.add(n);
        long avgDays = 0;
        if (total.compareTo(BigDecimal.ZERO) > 0) avgDays = weighted.divide(total, 0, RoundingMode.HALF_UP).longValue();
        LocalDate avgDate = today.plusDays(avgDays);
        BigDecimal avgMonths = BigDecimal.valueOf(avgDays).divide(BigDecimal.valueOf(30), 1, RoundingMode.HALF_UP);

        resultBox.removeAll(); resultBox.setVisible(true);
        resultBox.getStyle().clear();
        resultBox.getStyle().set("background", "var(--lumo-base-color)").set("border-radius", "10px")
                .set("padding", "10px 14px").set("box-shadow", "0 1px 6px rgba(0,0,0,0.06)").set("margin-top", "6px");

        HorizontalLayout stats = new HorizontalLayout();
        stats.setSpacing(true); stats.getStyle().set("gap", "8px").set("flex-wrap", "wrap");
        stats.add(statCard("📋", (filteredRows.size()+cashCount) + " işlem", ""));
        stats.add(statCard("💰", FormatUtils.formatNumber(total) + " ₺", ""));
        stats.add(statCard("📅", avgDays + " gün (" + avgMonths.stripTrailingZeros().toPlainString() + " ay)", ""));
        stats.add(statCard("🗓️", avgDate.format(DISPLAY_FMT), ""));
        resultBox.add(stats);

        // Chart
        int[] buckets = new int[8];
        String[] bl = {"0-15","16-30","31-60","61-90","91-120","121-180","181-365","365+"};
        for (VadeRow r : filteredRows) {
            long g = Math.max(0, ChronoUnit.DAYS.between(today, r.tarih));
            if (g <= 15) buckets[0]++; else if (g <= 30) buckets[1]++; else if (g <= 60) buckets[2]++;
            else if (g <= 90) buckets[3]++; else if (g <= 120) buckets[4]++; else if (g <= 180) buckets[5]++;
            else if (g <= 365) buckets[6]++; else buckets[7]++;
        }
        chartBox.removeAll(); chartBox.setVisible(true);
        chartBox.getStyle().clear();
        chartBox.getStyle().set("background", "var(--lumo-base-color)").set("border-radius", "10px")
                .set("padding", "10px 14px").set("box-shadow", "0 1px 6px rgba(0,0,0,0.06)").set("margin-top", "6px");
        List<String> labels = new ArrayList<>(); List<Double> data = new ArrayList<>();
        for (int i = 0; i < buckets.length; i++) { if (buckets[i] > 0) { labels.add(bl[i]); data.add((double)buckets[i]); } }
        if (!labels.isEmpty()) {
            ApexCharts ch = ApexChartsBuilder.get()
                    .withChart(ChartBuilder.get().withType(Type.BAR).withBackground("transparent").withHeight("180px").build())
                    .withPlotOptions(PlotOptionsBuilder.get()
                            .withBar(com.github.appreciated.apexcharts.config.plotoptions.builder.BarBuilder.get()
                                    .withHorizontal(false).withColumnWidth("60%").build()).build())
                    .withDataLabels(DataLabelsBuilder.get().withEnabled(true).build())
                    .withSeries(new Series<>("İşlem", data.toArray(new Double[0])))
                    .withXaxis(XAxisBuilder.get().withCategories(labels).build()).withColors("#2196F3").build();
            ch.setWidth("100%"); chartBox.add(new H4("Vade Dağılımı") {{ getStyle().set("margin","0 0 6px 0").set("font-size","0.9em"); }}, ch);
        }
    }

    private Div statCard(String icon, String value, String label) {
        Div card = new Div();
        card.getStyle().set("flex","1").set("min-width","100px").set("padding","8px")
                .set("border-radius","6px").set("background","var(--lumo-contrast-5pct)").set("text-align","center");
        Span v = new Span(icon + " " + value);
        v.getStyle().set("font-size","0.8em").set("font-weight","700").set("display","block");
        if (!label.isEmpty()) { Span l = new Span(label); l.getStyle().set("font-size","0.6em").set("color","var(--lumo-secondary-text-color)"); card.add(v,l); }
        else card.add(v);
        return card;
    }

    private void refreshNakitDisplay() {
        nakitContainer.removeAll();
        for (int i = 0; i < nakitList.size(); i++) {
            int idx = i; BigDecimal amt = nakitList.get(i);
            HorizontalLayout r = new HorizontalLayout(); r.setAlignItems(Alignment.CENTER);
            r.getStyle().set("gap","4px").set("font-size","0.75em");
            Span l = new Span("Nakit #" + (i+1) + ": " + FormatUtils.formatNumber(amt) + " ₺");
            Button x = new Button(new Icon(VaadinIcon.CLOSE_SMALL), ev -> { nakitList.remove(idx); refreshNakitDisplay(); if(!filteredRows.isEmpty()||!nakitList.isEmpty())showCsvResult(); });
            x.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            r.add(l,x); r.expand(l); nakitContainer.add(r);
        }
    }

    private void resetCsv() {
        allRows.clear(); filteredRows.clear(); nakitList.clear(); nakitContainer.removeAll();
        grid.setItems(filteredRows); grid.setVisible(false);
        resultBox.setVisible(false); resultBox.removeAll();
        chartBox.setVisible(false); chartBox.removeAll();
    }

    // ==================== MANUAL SECTION ====================

    private VerticalLayout buildManualSection() {
        VerticalLayout col = new VerticalLayout();
        col.setPadding(false); col.setSpacing(true);
        col.getStyle().set("flex", "0 0 380px").set("min-width", "360px");

        H4 secTitle = new H4("Manuel Hesaplama");
        secTitle.getStyle().set("margin", "0");

        modeGroup = new RadioButtonGroup<>();
        modeGroup.setItems("Tarih girerek", "Gün girerek");
        modeGroup.setValue("Tarih girerek");
        modeGroup.addValueChangeListener(e -> rebuildRows());

        countSelect = new ComboBox<>("Ödeme Sayısı");
        countSelect.setItems(1,2,3,4,5,6,7,8,9,10,11,12);
        countSelect.setValue(2);
        countSelect.setWidth("120px");
        countSelect.addValueChangeListener(e -> rebuildRows());

        rowsContainer = new VerticalLayout();
        rowsContainer.setPadding(false); rowsContainer.setSpacing(false);
        rowsContainer.getStyle().set("gap", "6px");

        manualResult = new Div();
        manualResult.getStyle().set("padding", "10px 14px").set("border-radius", "8px")
                .set("text-align", "center").set("font-weight", "700");

        Button calcBtn = new Button("Hesapla", e -> calculateManual());
        calcBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button resetManualBtn = new Button("Sıfırla", e -> resetManual());
        resetManualBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        HorizontalLayout btns = new HorizontalLayout(calcBtn, resetManualBtn);
        btns.setSpacing(true);

        Div card = new Div();
        card.getStyle().set("background","var(--lumo-base-color)").set("border-radius","12px")
                .set("padding","14px 16px").set("box-shadow","0 2px 8px rgba(0,0,0,0.08)");
        card.add(modeGroup, countSelect, rowsContainer, btns, manualResult);

        col.add(secTitle, card);
        rebuildRows();
        return col;
    }

    private void rebuildRows() {
        rowsContainer.removeAll();
        manualDayBoxes.clear(); manualMonthBoxes.clear(); manualYearFields.clear();
        manualGunFields.clear(); manualAmountFields.clear();
        int n = countSelect.getValue() != null ? countSelect.getValue() : 2;
        boolean isDateMode = "Tarih girerek".equals(modeGroup.getValue());

        for (int i = 0; i < n; i++) {
            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull(); row.setAlignItems(Alignment.END);
            row.getStyle().set("gap", "6px").set("padding", "4px 0");

            Span label = new Span((i+1) + ".");
            label.getStyle().set("font-size","0.85em").set("font-weight","700").set("min-width","24px");

            if (isDateMode) {
                ComboBox<Integer> dayBox = new ComboBox<>();
                dayBox.setItems(java.util.stream.IntStream.rangeClosed(1,31).boxed().toList());
                dayBox.setValue(15); dayBox.setWidth("60px");
                ComboBox<Integer> monthBox = new ComboBox<>();
                monthBox.setItems(1,2,3,4,5,6,7,8,9,10,11,12);
                monthBox.setItemLabelGenerator(m -> AYLAR[m-1]);
                monthBox.setValue(6); monthBox.setWidth("110px");
                IntegerField yearField = new IntegerField();
                yearField.setValue(LocalDate.now().getYear()); yearField.setWidth("90px");
                row.add(label, dayBox, monthBox, yearField);
                manualDayBoxes.add(dayBox); manualMonthBoxes.add(monthBox); manualYearFields.add(yearField);
            } else {
                IntegerField gunField = new IntegerField();
                gunField.setMin(1); gunField.setValue(30); gunField.setWidth("80px");
                Span gunLabel = new Span("gün");
                gunLabel.getStyle().set("font-size","0.8em");
                row.add(label, gunField, gunLabel);
                manualGunFields.add(gunField);
            }

            TextField amtField = new TextField();
            amtField.setValue("0,00"); amtField.setWidth("110px");
            FormatUtils.attachCurrencyFormatting(amtField);
            Span tl = new Span("₺"); tl.getStyle().set("font-size","0.8em");
            row.add(amtField, tl);
            manualAmountFields.add(amtField);

            rowsContainer.add(row);
        }
    }

    private void calculateManual() {
        boolean isDateMode = "Tarih girerek".equals(modeGroup.getValue());
        int n = countSelect.getValue();

        List<BigDecimal> amounts = new ArrayList<>();
        List<Long> days = new ArrayList<>();
        StringBuilder errors = new StringBuilder();
        LocalDate today = LocalDate.now();

        for (int i = 0; i < n; i++) {
            BigDecimal amt = FormatUtils.parseTurkishCurrency(manualAmountFields.get(i).getValue());
            if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                errors.append((i+1)).append(". ödeme tutarı > 0 olmalı. ");
                continue;
            }
            amounts.add(amt);

            if (isDateMode) {
                int d = manualDayBoxes.get(i).getValue();
                int m = manualMonthBoxes.get(i).getValue();
                int y = manualYearFields.get(i).getValue();
                try {
                    LocalDate date = LocalDate.of(y, m, d);
                    long dayDiff = ChronoUnit.DAYS.between(today, date);
                    if (dayDiff < 0) dayDiff = 0;
                    days.add(dayDiff);
                } catch (Exception e) {
                    errors.append((i+1)).append(". geçersiz tarih. ");
                }
            } else {
                long g = manualGunFields.get(i).getValue();
                if (g <= 0) { errors.append((i+1)).append(". gün > 0 olmalı. "); continue; }
                days.add(g);
            }
        }

        if (!errors.isEmpty()) {
            manualResult.getStyle().set("background", "var(--lumo-error-color-10pct)").set("color", "var(--lumo-error-color)");
            manualResult.setText("❌ " + errors.toString());
            return;
        }
        if (amounts.isEmpty()) {
            manualResult.getStyle().set("background", "var(--lumo-error-color-10pct)").set("color", "var(--lumo-error-color)");
            manualResult.setText("❌ Geçerli ödeme girin.");
            return;
        }

        BigDecimal totalAmt = BigDecimal.ZERO;
        BigDecimal weighted = BigDecimal.ZERO;
        for (int i = 0; i < amounts.size(); i++) {
            totalAmt = totalAmt.add(amounts.get(i));
            weighted = weighted.add(amounts.get(i).multiply(BigDecimal.valueOf(days.get(i))));
        }
        long avgDays = weighted.divide(totalAmt, 0, RoundingMode.HALF_UP).longValue();

        manualResult.getStyle().set("background", "var(--lumo-success-color-10pct)").set("color", "var(--lumo-success-text-color)");
        if (isDateMode) {
            LocalDate avgDate = today.plusDays(avgDays);
            BigDecimal avgMonths = BigDecimal.valueOf(avgDays).divide(BigDecimal.valueOf(30), 1, RoundingMode.HALF_UP);
            manualResult.setText("📅 Ortalama Vade Tarihi: " + avgDate.format(DISPLAY_FMT) + "\n(" + avgDays + " gün · ~" + avgMonths.stripTrailingZeros().toPlainString() + " ay)");
        } else {
            BigDecimal avgMonths = BigDecimal.valueOf(avgDays).divide(BigDecimal.valueOf(30), 1, RoundingMode.HALF_UP);
            manualResult.setText("📅 Ortalama Vade: " + avgDays + " gün (~" + avgMonths.stripTrailingZeros().toPlainString() + " ay)");
        }
    }

    private void resetManual() {
        manualResult.setText(""); manualResult.getStyle().clear();
        countSelect.setValue(2);
        modeGroup.setValue("Tarih girerek");
        rebuildRows();
    }

    public record VadeRow(int sira, LocalDate tarih, BigDecimal tutar, String banka, String kartAciklama, String islemSonucu, int taksit) {}
}
