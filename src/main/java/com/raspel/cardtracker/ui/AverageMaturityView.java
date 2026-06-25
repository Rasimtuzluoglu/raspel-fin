package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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

@Route(value = "average-maturity", layout = MainLayout.class)
@PageTitle("Ortalama Vade Hesaplama")
@PermitAll
public class AverageMaturityView extends VerticalLayout {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.of("tr"));

    private final Grid<VadeRow> grid = new Grid<>(VadeRow.class, false);
    private final Div resultBox = new Div();
    private final Upload upload;
    private List<VadeRow> currentRows = new ArrayList<>();
    private List<BigDecimal> nakitList = new ArrayList<>();
    private VerticalLayout nakitContainer;

    public AverageMaturityView() {
        setSizeFull();
        setPadding(false);
        setSpacing(false);
        getStyle().set("overflow", "hidden");

        Div container = new Div();
        container.getStyle()
                .set("max-width", "1200px")
                .set("margin", "0 auto")
                .set("padding", "24px 24px 0 24px")
                .set("width", "100%")
                .set("height", "100%")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("overflow", "hidden");

        H3 title = new H3("Ortalama Vade Hesaplama");
        title.getStyle().set("margin", "0 0 2px 0").set("font-size", "20px").set("font-weight", "700").set("flex-shrink", "0");

        Span info = new Span("CSV yükleyin. Bayi, Banka, Tutar, Taksit, Kart Açıklaması, Tarih, İşlem Sonucu");
        info.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "12px").set("display", "block").set("margin-bottom", "8px").set("flex-shrink", "0");

        MemoryBuffer buffer = new MemoryBuffer();
        upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".csv");
        upload.setMaxFiles(1);
        upload.setDropLabel(new Span("CSV dosyasını sürükleyin veya seçin"));
        upload.getStyle().set("flex-shrink", "0");

        upload.addSucceededListener(event -> {
            try (InputStream is = buffer.getInputStream()) {
                currentRows = parseCSV(is);
                if (currentRows.isEmpty()) {
                    Notification.show("Geçerli satır bulunamadı. İşlem Sonucu 'Basarili' olan satırlar işlenir.", 5000, Notification.Position.MIDDLE)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
                showResult();
                Notification.show(currentRows.size() + " işlem okundu.", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show("Dosya okunamadı, lütfen formatı kontrol edin.", 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        configureGrid();

        // Nakit ödeme ekleme alanı
        Div nakitSection = new Div();
        nakitSection.getStyle()
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-radius", "10px")
                .set("padding", "12px 16px")
                .set("margin-top", "12px");

        HorizontalLayout nakitRow = new HorizontalLayout();
        nakitRow.setWidthFull();
        nakitRow.setAlignItems(Alignment.END);
        nakitRow.setSpacing(true);

        TextField nakitField = new TextField("Nakit Tutar (₺)");
        FormatUtils.attachCurrencyFormatting(nakitField);
        nakitField.setWidth("180px");

        Button nakitAddBtn = new Button("Nakit Ekle", new Icon(VaadinIcon.PLUS), e -> {
            BigDecimal amt = FormatUtils.parseTurkishCurrency(nakitField.getValue());
            if (amt == null || amt.compareTo(BigDecimal.ZERO) <= 0) {
                Notification.show("Geçerli bir tutar girin", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            nakitList.add(amt);
            refreshNakitDisplay();
            nakitField.clear();
            if (!currentRows.isEmpty()) showResult();
        });
        nakitAddBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        nakitRow.add(nakitField, nakitAddBtn);
        nakitRow.expand(nakitField);

        nakitContainer = new VerticalLayout();
        nakitContainer.setPadding(false);
        nakitContainer.setSpacing(false);
        nakitContainer.getStyle().set("max-height", "120px").set("overflow-y", "auto");

        nakitSection.add(nakitRow, nakitContainer);
        nakitSection.getStyle().set("flex-shrink", "0").set("margin-bottom", "4px");

        Button resetBtn = new Button("Sıfırla", new Icon(VaadinIcon.REFRESH), e -> resetAll());
        resetBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        resetBtn.getStyle().set("margin-left", "auto");

        HorizontalLayout toolbar = new HorizontalLayout(title, resetBtn);
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.END);
        toolbar.expand(title);

        resultBox.setVisible(false);
        resultBox.getStyle().set("flex-shrink", "0").set("margin-top", "8px");
        grid.setVisible(false);
        grid.getStyle().set("flex-shrink", "1").set("overflow-y", "auto").set("margin-top", "8px");

        container.add(toolbar, info, upload, nakitSection, resultBox, grid);
        add(container);
    }

    private void configureGrid() {
        grid.setWidthFull();
        grid.getStyle().set("max-height", "220px").set("margin-top", "8px");

        grid.addColumn(VadeRow::sira).setHeader("#").setWidth("60px").setFlexGrow(0);
        grid.addColumn(r -> r.tarih.format(DATE_FMT)).setHeader("Tarih").setAutoWidth(true);
        grid.addColumn(r -> FormatUtils.formatNumber(r.tutar) + " ₺").setHeader("Tutar").setAutoWidth(true)
                .setClassNameGenerator(c -> "col-right");
        grid.addColumn(VadeRow::banka).setHeader("Banka").setAutoWidth(true);
        grid.addColumn(r -> r.taksit == 0 ? "Tek Çekim" : String.valueOf(r.taksit)).setHeader("Taksit").setAutoWidth(true)
                .setClassNameGenerator(c -> "col-center");
        grid.addColumn(VadeRow::kartAciklama).setHeader("Kart Açıklaması").setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(VadeRow::islemSonucu).setHeader("İşlem Sonucu").setAutoWidth(true);
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

        String delim = detectDelimiter(lines[0]);

        int sira = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) continue;

            String[] cols = line.split(delim, -1);
            if (cols.length < 12) continue;

            try {
                String islemSonucu = cols[10].trim();
                if (!islemSonucu.equalsIgnoreCase("Basarili") && !islemSonucu.equalsIgnoreCase("Başarılı")) continue;

                String banka = cols[3].trim();
                String kartAciklama = cols[7].trim();
                String tarihStr = cols[9].trim();
                String tutarStr = cols[5].trim();
                int taksit = parseIntSafe(cols[6].trim());

                LocalDate tarih;
                try {
                    tarih = LocalDate.parse(tarihStr, DATE_FMT);
                } catch (Exception e) {
                    continue;
                }

                BigDecimal tutar = parseAmount(tutarStr);
                if (tutar.compareTo(BigDecimal.ZERO) <= 0) continue;

                sira++;
                rows.add(new VadeRow(sira, tarih, tutar, banka, kartAciklama, islemSonucu, taksit));
            } catch (Exception ignored) {
            }
        }
        return rows;
    }

    private String detectDelimiter(String headerLine) {
        if (headerLine.contains("\t")) return "\t";
        if (headerLine.contains(";")) return ";";
        return ",";
    }

    private int parseIntSafe(String val) {
        if (val == null || val.isEmpty()) return 0;
        val = val.replaceAll("[^0-9]", "");
        try { return val.isEmpty() ? 0 : Integer.parseInt(val); } catch (Exception e) { return 0; }
    }

    private BigDecimal parseAmount(String val) {
        if (val == null || val.trim().isEmpty()) return BigDecimal.ZERO;
        val = val.trim().replace("\"", "").replace("₺", "").replace("TL", "").replace(" ", "");
        try {
            val = val.replace(",", ".");
            return new BigDecimal(val);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private void showResult() {
        grid.setItems(currentRows);
        grid.setVisible(true);

        BigDecimal toplamTutar = BigDecimal.ZERO;
        BigDecimal agirlikliToplam = BigDecimal.ZERO;
        long taksitVadeToplam = 0;
        int basariliSayi = currentRows.size();
        LocalDate bugun = LocalDate.now();

        for (VadeRow row : currentRows) {
            toplamTutar = toplamTutar.add(row.tutar);
            long gunFarki = ChronoUnit.DAYS.between(bugun, row.tarih);
            agirlikliToplam = agirlikliToplam.add(row.tutar.multiply(BigDecimal.valueOf(gunFarki)));
            taksitVadeToplam += Math.max(1, row.taksit) * 30;
        }

        // Nakit ödemeleri ekle (vade = 0 gün)
        BigDecimal nakitToplam = BigDecimal.ZERO;
        for (BigDecimal n : nakitList) {
            toplamTutar = toplamTutar.add(n);
            nakitToplam = nakitToplam.add(n);
        }
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

        resultBox.removeAll();
        resultBox.setVisible(true);
        resultBox.getStyle().clear();
        resultBox.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "16px")
                .set("padding", "20px 24px")
                .set("box-shadow", "0 2px 12px rgba(0,0,0,0.06)");

        H4 resultTitle = new H4("Vade Sonucu");
        resultTitle.getStyle().set("margin", "0 0 12px 0").set("font-size", "1em");

        HorizontalLayout stats = new HorizontalLayout();
        stats.setWidthFull();
        stats.setSpacing(true);
        stats.getStyle().set("gap", "16px").set("flex-wrap", "wrap");

        stats.add(buildStatCard("📋", "Toplam İşlem", basariliSayi + " adet" + (nakitSayi > 0 ? " · " + nakitSayi + " nakit" : "")));
        stats.add(buildStatCard("💰", "Toplam Tutar", FormatUtils.formatNumber(toplamTutar) + " ₺"));
        stats.add(buildStatCard("📅", "Tarihe Göre Vade", ortalamaVadeGun + " gün (" + ortalamaAy.stripTrailingZeros().toPlainString() + " ay)"));
        stats.add(buildStatCard("🗓️", "Ort. Vade Tarihi", ortalamaVadeTarihi.format(DISPLAY_FMT)));
        stats.add(buildStatCard("🔢", "Taksite Göre Vade", taksitVadeGun + " gün (" + taksitAy.stripTrailingZeros().toPlainString() + " ay)"));

        resultBox.add(resultTitle, stats);
    }

    private Div buildStatCard(String icon, String label, String value) {
        Div card = new Div();
        card.getStyle()
                .set("flex", "1")
                .set("min-width", "140px")
                .set("padding", "12px")
                .set("border-radius", "10px")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("text-align", "center");

        Span iconSpan = new Span(icon);
        iconSpan.getStyle().set("font-size", "18px").set("display", "block");

        Span valSpan = new Span(value);
        valSpan.getStyle().set("font-size", "14px").set("font-weight", "700").set("color", "var(--lumo-body-text-color)").set("display", "block").set("margin-top", "4px");

        Span lblSpan = new Span(label);
        lblSpan.getStyle().set("font-size", "10px").set("color", "var(--lumo-secondary-text-color)").set("display", "block");

        card.add(iconSpan, valSpan, lblSpan);
        return card;
    }

    private void refreshNakitDisplay() {
        nakitContainer.removeAll();
        for (int i = 0; i < nakitList.size(); i++) {
            int idx = i;
            BigDecimal amt = nakitList.get(i);
            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull();
            row.setAlignItems(Alignment.CENTER);
            row.getStyle().set("padding", "2px 0").set("gap", "8px");

            Span label = new Span("Nakit #" + (i + 1) + ": " + FormatUtils.formatNumber(amt) + " ₺");
            label.getStyle().set("font-size", "0.8em").set("color", "var(--lumo-body-text-color)");

            Button removeBtn = new Button(new Icon(VaadinIcon.CLOSE_SMALL), ev -> {
                nakitList.remove(idx);
                refreshNakitDisplay();
                if (!currentRows.isEmpty()) showResult();
            });
            removeBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);

            row.add(label, removeBtn);
            row.expand(label);
            nakitContainer.add(row);
        }
    }

    private void resetAll() {
        currentRows.clear();
        nakitList.clear();
        nakitContainer.removeAll();
        grid.setItems(currentRows);
        grid.setVisible(false);
        resultBox.setVisible(false);
        resultBox.removeAll();
    }

    public record VadeRow(int sira, LocalDate tarih, BigDecimal tutar, String banka, String kartAciklama, String islemSonucu, int taksit) {}
}
