package com.raspel.cardtracker.ui;

import com.github.appreciated.apexcharts.ApexCharts;
import com.github.appreciated.apexcharts.ApexChartsBuilder;
import com.github.appreciated.apexcharts.config.builder.*;
import com.github.appreciated.apexcharts.config.chart.Type;
import com.github.appreciated.apexcharts.helper.Series;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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

    private final Grid<VadeRow> grid = new Grid<>(VadeRow.class, false);
    private final Div resultBox = new Div();
    private final Div chartBox = new Div();
    private final Upload upload;
    private List<VadeRow> allRows = new ArrayList<>();
    private List<VadeRow> filteredRows = new ArrayList<>();
    private List<BigDecimal> nakitList = new ArrayList<>();
    private VerticalLayout nakitContainer;
    private DatePicker fromDate;
    private DatePicker toDate;

    public AverageMaturityView() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle().set("overflow", "hidden");

        Div container = new Div();
        container.getStyle()
                .set("max-width", "1200px")
                .set("margin", "0 auto")
                .set("padding", "16px 24px 0 24px")
                .set("width", "100%")
                .set("height", "100%")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("overflow", "auto");

        H3 title = new H3("Ortalama Vade Hesaplama");
        title.getStyle().set("margin", "0").set("font-size", "1.2em");

        Span info = new Span("CSV (Basarili işlemler): Bayi, Banka, Tutar, Taksit, Kart Açıklaması, Tarih, İşlem Sonucu");
        info.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "0.75em");

        MemoryBuffer buffer = new MemoryBuffer();
        upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".csv");
        upload.setMaxFiles(1);
        upload.setDropLabel(new Span("CSV dosyasını sürükleyin"));

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

        // Tarih filtresi
        fromDate = new DatePicker("Başlangıç");
        fromDate.setClearButtonVisible(true);
        fromDate.setWidth("150px");
        toDate = new DatePicker("Bitiş");
        toDate.setClearButtonVisible(true);
        toDate.setWidth("150px");

        fromDate.addValueChangeListener(e -> { applyFilters(); });
        toDate.addValueChangeListener(e -> { applyFilters(); });

        // Nakit ekleme
        TextField nakitField = new TextField("Nakit Tutar");
        FormatUtils.attachCurrencyFormatting(nakitField);
        nakitField.setWidth("150px");

        Button nakitAddBtn = new Button("Nakit Ekle", new Icon(VaadinIcon.PLUS), e -> {
            BigDecimal amt = FormatUtils.parseTurkishCurrency(nakitField.getValue());
            if (amt == null || amt.compareTo(BigDecimal.ZERO) <= 0) {
                Notification.show("Geçerli bir tutar girin", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            nakitList.add(amt);
            refreshNakitDisplay();
            nakitField.clear();
            if (!filteredRows.isEmpty() || !nakitList.isEmpty()) showResult();
        });
        nakitAddBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        nakitContainer = new VerticalLayout();
        nakitContainer.setPadding(false); nakitContainer.setSpacing(false);
        nakitContainer.getStyle().set("max-height", "80px").set("overflow-y", "auto");

        Button resetBtn = new Button("Sıfırla", e -> resetAll());
        resetBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        HorizontalLayout filterRow = new HorizontalLayout(fromDate, toDate, nakitField, nakitAddBtn, resetBtn);
        filterRow.setWidthFull(); filterRow.setAlignItems(Alignment.END); filterRow.setSpacing(true);
        filterRow.getStyle().set("flex-wrap", "wrap").set("gap", "8px");

        grid.setVisible(false);
        resultBox.setVisible(false);
        chartBox.setVisible(false);

        container.add(title, info, upload, filterRow, nakitContainer, resultBox, chartBox, grid);
        add(container);
    }

    private void configureGrid() {
        grid.setWidthFull();
        grid.addColumn(VadeRow::sira).setHeader("#").setWidth("50px").setFlexGrow(0);
        grid.addColumn(r -> r.tarih.format(DATE_FMT)).setHeader("Tarih").setAutoWidth(true);
        grid.addColumn(r -> FormatUtils.formatNumber(r.tutar) + " ₺").setHeader("Tutar").setAutoWidth(true);
        grid.addColumn(VadeRow::banka).setHeader("Banka").setAutoWidth(true);
        grid.addColumn(r -> r.taksit == 0 ? "Tek Çekim" : String.valueOf(r.taksit)).setHeader("Taksit").setAutoWidth(true);
        grid.addColumn(VadeRow::kartAciklama).setHeader("Açıklama").setAutoWidth(true).setFlexGrow(1);
    }

    private void applyFilters() {
        LocalDate from = fromDate.getValue();
        LocalDate to = toDate.getValue();

        filteredRows = allRows.stream()
                .filter(r -> from == null || !r.tarih.isBefore(from))
                .filter(r -> to == null || !r.tarih.isAfter(to))
                .collect(Collectors.toList());

        grid.setItems(filteredRows);
        grid.setVisible(!filteredRows.isEmpty());

        if (!filteredRows.isEmpty() || !nakitList.isEmpty()) {
            showResult();
        } else {
            resultBox.setVisible(false);
            chartBox.setVisible(false);
        }
    }

    private List<VadeRow> parseCSV(InputStream is) throws Exception {
        List<VadeRow> rows = new ArrayList<>();
        byte[] bytes = is.readAllBytes();
        byte[] trimmed = bytes;
        if (bytes.length >= 3 && bytes[0] == (byte)0xEF && bytes[1] == (byte)0xBB && bytes[2] == (byte)0xBF) {
            trimmed = java.util.Arrays.copyOfRange(bytes, 3, bytes.length);
        }
        String content = new String(trimmed, StandardCharsets.UTF_8);
        if (content.contains("\uFFFD")) {
            content = new String(trimmed, java.nio.charset.Charset.forName("Windows-1254"));
        }
        String[] lines = content.split("\\r?\\n");
        if (lines.length < 2) return rows;
        String delim = lines[0].contains(";") ? ";" : lines[0].contains("\t") ? "\t" : ",";
        int sira = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;
            String[] cols = line.split(delim, -1);
            if (cols.length < 12) continue;
            try {
                String islemSonucu = cols[10].trim();
                if (!islemSonucu.equalsIgnoreCase("Basarili") && !islemSonucu.equalsIgnoreCase("Başarılı")) continue;
                LocalDate tarih = LocalDate.parse(cols[9].trim(), DATE_FMT);
                BigDecimal tutar = parseAmount(cols[5].trim());
                if (tutar.compareTo(BigDecimal.ZERO) <= 0) continue;
                int taksit = parseIntSafe(cols[6].trim());
                sira++;
                rows.add(new VadeRow(sira, tarih, tutar, cols[3].trim(), cols[7].trim(), cols[10].trim(), taksit));
            } catch (Exception ignored) {}
        }
        return rows;
    }

    private BigDecimal parseAmount(String val) {
        if (val == null || val.trim().isEmpty()) return BigDecimal.ZERO;
        val = val.trim().replace("\"", "").replace("₺", "").replace("TL", "").replace(" ", "");
        try { return new BigDecimal(val.replace(",", ".")); } catch (Exception e) { return BigDecimal.ZERO; }
    }

    private int parseIntSafe(String val) {
        val = (val != null ? val : "").replaceAll("[^0-9]", "");
        try { return val.isEmpty() ? 0 : Integer.parseInt(val); } catch (Exception e) { return 0; }
    }

    private void showResult() {
        grid.setItems(filteredRows);
        grid.setVisible(true);

        BigDecimal toplamTutar = BigDecimal.ZERO;
        BigDecimal agirlikliToplam = BigDecimal.ZERO;
        long taksitVadeToplam = 0;
        int basariliSayi = filteredRows.size();
        LocalDate bugun = LocalDate.now();

        // Vade dağılımı için gruplama
        int[] buckets = new int[8];
        String[] bucketLabels = {"0-15", "16-30", "31-60", "61-90", "91-120", "121-180", "181-365", "365+"};

        for (VadeRow row : filteredRows) {
            toplamTutar = toplamTutar.add(row.tutar);
            long gun = ChronoUnit.DAYS.between(bugun, row.tarih);
            if (gun < 0) gun = 0;
            agirlikliToplam = agirlikliToplam.add(row.tutar.multiply(BigDecimal.valueOf(gun)));
            taksitVadeToplam += Math.max(1, row.taksit) * 30;

            if (gun <= 15) buckets[0]++;
            else if (gun <= 30) buckets[1]++;
            else if (gun <= 60) buckets[2]++;
            else if (gun <= 90) buckets[3]++;
            else if (gun <= 120) buckets[4]++;
            else if (gun <= 180) buckets[5]++;
            else if (gun <= 365) buckets[6]++;
            else buckets[7]++;
        }

        BigDecimal nakitToplam = BigDecimal.ZERO;
        for (BigDecimal n : nakitList) { toplamTutar = toplamTutar.add(n); nakitToplam = nakitToplam.add(n); }
        int nakitSayi = nakitList.size();
        basariliSayi += nakitSayi;

        long ortalamaVadeGun = 0;
        if (toplamTutar.compareTo(BigDecimal.ZERO) > 0) {
            ortalamaVadeGun = agirlikliToplam.divide(toplamTutar, 0, RoundingMode.HALF_UP).longValue();
        }
        long taksitVadeGun = basariliSayi > 0 ? taksitVadeToplam / basariliSayi : 0;
        LocalDate ortalamaVadeTarihi = bugun.plusDays(ortalamaVadeGun);
        BigDecimal ortalamaAy = BigDecimal.valueOf(ortalamaVadeGun).divide(BigDecimal.valueOf(30), 1, RoundingMode.HALF_UP);
        BigDecimal taksitAy = BigDecimal.valueOf(taksitVadeGun).divide(BigDecimal.valueOf(30), 1, RoundingMode.HALF_UP);

        // Sonuç kartları
        resultBox.removeAll();
        resultBox.setVisible(true);
        resultBox.getStyle().clear();
        resultBox.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "12px").set("padding", "14px 20px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.06)").set("margin-top", "8px");

        HorizontalLayout stats = new HorizontalLayout();
        stats.setWidthFull(); stats.setSpacing(true);
        stats.getStyle().set("gap", "10px").set("flex-wrap", "wrap");
        stats.add(statCard("📋", "Toplam", basariliSayi + " işlem"));
        stats.add(statCard("💰", "Tutar", FormatUtils.formatNumber(toplamTutar) + " ₺"));
        stats.add(statCard("📅", "Ort. Vade", ortalamaVadeGun + " gün (" + ortalamaAy.stripTrailingZeros().toPlainString() + " ay)"));
        stats.add(statCard("🗓️", "Vade Tarihi", ortalamaVadeTarihi.format(DISPLAY_FMT)));
        resultBox.add(stats);

        // Grafik
        chartBox.removeAll();
        chartBox.setVisible(true);
        chartBox.getStyle().clear();
        chartBox.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "12px").set("padding", "14px 20px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.06)").set("margin-top", "8px");

        H4 chartTitle = new H4("Vade Dağılımı");
        chartTitle.getStyle().set("margin", "0 0 8px 0").set("font-size", "0.95em");
        chartBox.add(chartTitle);

        List<String> labels = new ArrayList<>();
        List<Double> data = new ArrayList<>();
        for (int i = 0; i < buckets.length; i++) {
            if (buckets[i] > 0) {
                labels.add(bucketLabels[i]);
                data.add((double) buckets[i]);
            }
        }
        if (!labels.isEmpty()) {
            ApexCharts chart = ApexChartsBuilder.get()
                    .withChart(ChartBuilder.get().withType(Type.BAR).withBackground("transparent").withHeight("220px").build())
                    .withPlotOptions(PlotOptionsBuilder.get()
                            .withBar(com.github.appreciated.apexcharts.config.plotoptions.builder.BarBuilder.get()
                                    .withHorizontal(false).withColumnWidth("60%").build()).build())
                    .withDataLabels(DataLabelsBuilder.get().withEnabled(true).build())
                    .withSeries(new Series<>("İşlem Sayısı", data.toArray(new Double[0])))
                    .withXaxis(XAxisBuilder.get().withCategories(labels).build())
                    .withColors("#2196F3").build();
            chart.setWidth("100%");
            chartBox.add(chart);
        }
    }

    private Div statCard(String icon, String label, String value) {
        Div card = new Div();
        card.getStyle().set("flex", "1").set("min-width", "120px").set("padding", "10px")
                .set("border-radius", "8px").set("background", "var(--lumo-contrast-5pct)").set("text-align", "center");
        Span v = new Span(icon + " " + value);
        v.getStyle().set("font-size", "0.85em").set("font-weight", "700").set("display", "block");
        Span l = new Span(label);
        l.getStyle().set("font-size", "0.65em").set("color", "var(--lumo-secondary-text-color)");
        card.add(v, l);
        return card;
    }

    private void refreshNakitDisplay() {
        nakitContainer.removeAll();
        for (int i = 0; i < nakitList.size(); i++) {
            int idx = i;
            BigDecimal amt = nakitList.get(i);
            HorizontalLayout row = new HorizontalLayout();
            row.setAlignItems(Alignment.CENTER); row.setWidthFull();
            row.getStyle().set("gap", "6px").set("font-size", "0.8em");
            Span label = new Span("Nakit #" + (i + 1) + ": " + FormatUtils.formatNumber(amt) + " ₺");
            Button removeBtn = new Button(new Icon(VaadinIcon.CLOSE_SMALL), ev -> {
                nakitList.remove(idx);
                refreshNakitDisplay();
                if (!filteredRows.isEmpty() || !nakitList.isEmpty()) showResult();
            });
            removeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            row.add(label, removeBtn); row.expand(label);
            nakitContainer.add(row);
        }
    }

    private void resetAll() {
        allRows.clear(); filteredRows.clear(); nakitList.clear();
        nakitContainer.removeAll();
        grid.setItems(filteredRows); grid.setVisible(false);
        resultBox.setVisible(false); resultBox.removeAll();
        chartBox.setVisible(false); chartBox.removeAll();
    }

    public record VadeRow(int sira, LocalDate tarih, BigDecimal tutar, String banka, String kartAciklama, String islemSonucu, int taksit) {}
}
