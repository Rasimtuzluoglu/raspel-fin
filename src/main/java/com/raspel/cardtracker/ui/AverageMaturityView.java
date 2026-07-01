package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.github.appreciated.apexcharts.ApexCharts;
import com.github.appreciated.apexcharts.ApexChartsBuilder;
import com.github.appreciated.apexcharts.config.builder.*;
import com.github.appreciated.apexcharts.config.tooltip.builder.YBuilder;
import com.github.appreciated.apexcharts.config.chart.Type;
import com.github.appreciated.apexcharts.helper.Series;
import com.raspel.cardtracker.ui.utils.FormatUtils;
import com.raspel.cardtracker.ui.utils.TurkishDatePickerI18n;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Route(value = "average-maturity", layout = MainLayout.class)
@PageTitle("Ortalama Vade")
@PermitAll
public class AverageMaturityView extends VerticalLayout {

    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.of("tr"));

    private RadioButtonGroup<String> modeGroup;
    private ComboBox<Integer> countSelect;
    private VerticalLayout dynamicRowsLayout;
    private Span manualTotalSpan;
    private Div manualResultBox;
    private Div chartsContainer;
    private final List<DatePicker> rowDates = new ArrayList<>();
    private final List<IntegerField> rowDays = new ArrayList<>();
    private final List<TextField> rowAmounts = new ArrayList<>();

    public AverageMaturityView() {
        setSizeFull();
        setPadding(true);
        setSpacing(false);
        getStyle().set("overflow", "auto");

        add(buildHeader());
        add(buildContent());
    }

    private HorizontalLayout buildHeader() {
        Div icon = new Div();
        icon.addClassName("header-icon");
        icon.add(VaadinIcon.CALC.create());

        H2 title = new H2("Ortalama Vade Hesaplayıcı");
        title.getStyle().set("margin", "0").set("font-size", "1.3em");

        Paragraph sub = new Paragraph("Manuel giriş ile ortalama vade hesaplama");
        sub.getStyle().set("font-size", "0.8em").set("color", "var(--lumo-tertiary-text-color)").set("margin", "0");

        VerticalLayout texts = new VerticalLayout(title, sub);
        texts.setPadding(false);
        texts.setSpacing(false);

        HorizontalLayout h = new HorizontalLayout(icon, texts);
        h.setAlignItems(Alignment.CENTER);
        h.setSpacing(true);
        h.getStyle().set("margin-bottom", "12px");
        return h;
    }

    private VerticalLayout buildContent() {
        VerticalLayout p = new VerticalLayout();
        p.addClassName("maturity-card");
        p.setPadding(true);
        p.setSpacing(false);
        p.setWidthFull();
        p.getStyle().set("min-width", "0").set("overflow", "auto");

        Span warning = new Span("* Doldurulması zorunlu alanlar.");
        warning.getStyle()
                .set("color", "var(--lumo-error-color)")
                .set("font-size", "0.72em")
                .set("font-weight", "500")
                .set("display", "block")
                .set("margin", "12px 0 4px 0");
        p.add(warning);

        modeGroup = new RadioButtonGroup<>();
        modeGroup.setLabel("Vade Şekli");
        modeGroup.setItems("Vade sonu tarihlerini girerek hesapla", "Gün cinsinden vade girerek hesapla");
        modeGroup.setValue("Vade sonu tarihlerini girerek hesapla");
        modeGroup.setWidthFull();
        modeGroup.addValueChangeListener(e -> rebuildRows());
        modeGroup.getStyle().set("margin-top", "8px");
        p.add(modeGroup);

        countSelect = new ComboBox<>("Ödeme Sayısı");
        countSelect.setItems(java.util.stream.IntStream.rangeClosed(1, 20).boxed().toList());
        countSelect.setValue(2);
        countSelect.setWidthFull();
        countSelect.addValueChangeListener(e -> rebuildRows());
        countSelect.getStyle().set("margin-top", "8px");
        p.add(countSelect);

        dynamicRowsLayout = new VerticalLayout();
        dynamicRowsLayout.setPadding(false);
        dynamicRowsLayout.setSpacing(false);
        dynamicRowsLayout.getStyle().set("gap", "6px").set("margin-top", "8px");
        p.add(dynamicRowsLayout);

        HorizontalLayout totalRow = new HorizontalLayout();
        totalRow.setWidthFull();
        totalRow.setAlignItems(Alignment.BASELINE);
        totalRow.getStyle().set("margin-top", "8px").set("padding", "8px 12px")
                .set("background", "var(--lumo-contrast-5pct)").set("border-radius", "8px");

        Span totalLabel = new Span("Toplam:");
        totalLabel.getStyle().set("font-weight", "600").set("font-size", "0.85em");
        manualTotalSpan = new Span("0,00 TL");
        manualTotalSpan.getStyle().set("font-weight", "700").set("font-size", "0.95em")
                .set("color", "var(--lumo-primary-color)").set("margin-left", "8px");

        totalRow.add(totalLabel, manualTotalSpan);
        p.add(totalRow);

        HorizontalLayout btnRow = new HorizontalLayout();
        btnRow.getStyle().set("gap", "10px").set("margin-top", "12px");
        btnRow.setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        Button resetBtn = new Button("Sıfırla", VaadinIcon.TRASH.create(), e -> resetManual());
        resetBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

        Button calcBtn = new Button("Hesapla", VaadinIcon.CALC.create(), e -> calcManual());
        calcBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        btnRow.add(resetBtn, calcBtn);
        p.add(btnRow);

        manualResultBox = new Div();
        manualResultBox.addClassName("manual-result-card");
        manualResultBox.setVisible(false);
        p.add(manualResultBox);

        chartsContainer = new Div();
        chartsContainer.setVisible(false);
        chartsContainer.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr")
                .set("gap", "16px")
                .set("margin-top", "16px")
                .set("width", "100%");
        p.add(chartsContainer);

        rebuildRows();
        return p;
    }

    private void rebuildRows() {
        dynamicRowsLayout.removeAll();
        rowDates.clear();
        rowDays.clear();
        rowAmounts.clear();

        int count = countSelect.getValue() != null ? countSelect.getValue() : 2;
        boolean dateMode = isDateMode();

        for (int i = 0; i < count; i++) {
            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull();
            row.setAlignItems(Alignment.BASELINE);
            row.addClassName("taksit-data-row");
            row.getStyle().set("gap", "8px");

            Span num = new Span(String.valueOf(i + 1) + ".");
            num.getStyle()
                    .set("font-size", "0.78em")
                    .set("color", "var(--lumo-tertiary-text-color)")
                    .set("font-weight", "600")
                    .set("min-width", "22px")
                    .set("text-align", "center")
                    .set("flex-shrink", "0")
                    .set("line-height", "32px");

            row.add(num);

            if (dateMode) {
                DatePicker dp = new DatePicker();
                TurkishDatePickerI18n.applyTo(dp);
                dp.setWidthFull();
                dp.getStyle().set("min-width", "0");
                dp.setPlaceholder("Vade tarihi");

                if (!rowDates.isEmpty()) {
                    DatePicker prev = rowDates.get(rowDates.size() - 1);
                    if (prev.getValue() != null) {
                        dp.setValue(prev.getValue().plusMonths(1));
                    } else {
                        dp.setValue(LocalDate.now().plusMonths(i));
                    }
                } else {
                    dp.setValue(LocalDate.now().plusMonths(i));
                }
                dp.addValueChangeListener(e -> updateTotalDisplay());

                rowDates.add(dp);
                row.add(dp);
            } else {
                IntegerField daysField = new IntegerField();
                daysField.setMin(1);
                daysField.setValue(30);
                daysField.setWidthFull();
                daysField.getStyle().set("min-width", "0");
                daysField.setPlaceholder("Gün");

                Span daySuffix = new Span("gün");
                daySuffix.getStyle()
                        .set("font-size", "0.75em")
                        .set("color", "var(--lumo-tertiary-text-color)")
                        .set("flex-shrink", "0")
                        .set("line-height", "32px");

                daysField.addValueChangeListener(e -> updateTotalDisplay());
                rowDays.add(daysField);
                row.add(daysField, daySuffix);
            }

            TextField amtField = new TextField();
            amtField.setValue("0,00");
            amtField.setWidthFull();
            amtField.getStyle().set("min-width", "0");
            amtField.setPlaceholder("Örn: 20650,75");
            FormatUtils.attachCurrencyFormatting(amtField);
            amtField.setSuffixComponent(new Span("TL"));
            amtField.addValueChangeListener(e -> updateTotalDisplay());

            rowAmounts.add(amtField);
            row.add(amtField);

            dynamicRowsLayout.add(row);
        }
        updateTotalDisplay();
    }

    private boolean isDateMode() {
        return modeGroup.getValue() == null || modeGroup.getValue().contains("tarihlerini");
    }

    private void updateTotalDisplay() {
        BigDecimal total = BigDecimal.ZERO;
        for (TextField amt : rowAmounts) {
            total = total.add(FormatUtils.parseTurkishCurrency(amt.getValue()));
        }
        manualTotalSpan.setText(FormatUtils.formatTurkishCurrency(total) + " TL");
    }

    private void calcManual() {
        boolean dateMode = isDateMode();
        int count = rowAmounts.size();
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal weighted = BigDecimal.ZERO;
        LocalDate ref = LocalDate.now();
        int validCount = 0;
        StringBuilder err = new StringBuilder();

        for (int i = 0; i < count; i++) {
            BigDecimal amt = FormatUtils.parseTurkishCurrency(rowAmounts.get(i).getValue());
            if (amt.compareTo(BigDecimal.ZERO) <= 0) {
                err.append(i + 1).append(". tutar > 0 olmalı. ");
                continue;
            }
            total = total.add(amt);

            long days;
            if (dateMode) {
                DatePicker dp = rowDates.get(i);
                if (dp.getValue() == null) {
                    err.append(i + 1).append(". tarih seçilmeli. ");
                    continue;
                }
                days = Math.max(1, ChronoUnit.DAYS.between(ref, dp.getValue()));
            } else {
                IntegerField df = rowDays.get(i);
                if (df.getValue() == null || df.getValue() <= 0) {
                    err.append(i + 1).append(". gün > 0 olmalı. ");
                    continue;
                }
                days = df.getValue();
            }
            weighted = weighted.add(amt.multiply(BigDecimal.valueOf(days)));
            validCount++;
        }

        manualResultBox.setVisible(true);
        manualResultBox.removeAll();

        if (err.length() > 0) {
            manualResultBox.addClassName("result-error");
            manualResultBox.removeClassName("result-success");
            Span errSpan = new Span(err.toString().trim());
            errSpan.getStyle().set("color", "var(--lumo-error-color)").set("font-size", "0.82em");
            manualResultBox.add(errSpan);
            return;
        }

        manualResultBox.addClassName("result-success");
        manualResultBox.removeClassName("result-error");

        long avgD = weighted.divide(total, 0, RoundingMode.HALF_UP).longValue();
        BigDecimal avgM = BigDecimal.valueOf(avgD).divide(BigDecimal.valueOf(30), 1, RoundingMode.HALF_UP);
        LocalDate avgDate = ref.plusDays(avgD);

        VerticalLayout rc = new VerticalLayout();
        rc.setPadding(false);
        rc.setSpacing(false);
        rc.getStyle().set("gap", "4px");

        Span rl = new Span("Hesaplama Sonucu");
        rl.addClassName("result-label");

        Span rm = new Span(avgD + " Gün");
        rm.addClassName("result-main-value");

        Span rs = new Span("(~" + avgM.stripTrailingZeros().toPlainString() + " Ay)  ·  " + avgDate.format(DISPLAY_FMT));
        rs.addClassName("result-sub-value");

        HorizontalLayout rd = new HorizontalLayout();
        rd.setWidthFull();
        rd.getStyle().set("gap", "12px").set("margin-top", "10px").set("justify-content", "center").set("flex-wrap", "wrap");
        rd.add(buildDetail("Ödeme Sayısı", String.valueOf(validCount)));
        rd.add(buildDetail("Toplam Tutar", FormatUtils.formatTurkishCurrency(total) + " TL"));
        rd.add(buildDetail("Ağırlıklı Ortalama", avgD + " gün"));

        rc.add(rl, rm, rs, rd);
        manualResultBox.add(rc);

        // Build charts
        buildCharts(validCount, rowAmounts, rowDates, rowDays, dateMode, ref);
    }

    private Div buildDetail(String label, String value) {
        Div d = new Div();
        d.getStyle().set("text-align", "center").set("padding", "4px 8px");
        Span lbl = new Span(label);
        lbl.getStyle().set("display", "block").set("font-size", "0.6em")
                .set("text-transform", "uppercase").set("letter-spacing", "0.04em")
                .set("color", "var(--lumo-secondary-text-color)");
        Span val = new Span(value);
        val.getStyle().set("display", "block").set("font-size", "0.85em")
                .set("font-weight", "600").set("margin-top", "2px");
        d.add(lbl, val);
        return d;
    }

    private void buildCharts(int count, List<TextField> amounts, List<DatePicker> dates,
                             List<IntegerField> days, boolean dateMode, LocalDate ref) {
        chartsContainer.removeAll();
        chartsContainer.setVisible(true);

        List<String> catLabels = new ArrayList<>();
        List<Double> lineData = new ArrayList<>();
        List<Double> pieData = new ArrayList<>();
        List<String> pieLabels = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            BigDecimal amt = FormatUtils.parseTurkishCurrency(amounts.get(i).getValue());
            if (amt.compareTo(BigDecimal.ZERO) <= 0) continue;

            String label = (i + 1) + ". Ödeme";
            catLabels.add(label);
            pieLabels.add(label);
            lineData.add(amt.doubleValue());
            pieData.add(amt.doubleValue());
        }

        if (lineData.isEmpty()) {
            lineData.add(1.0);
            catLabels.add("Veri yok");
            pieData.add(1.0);
            pieLabels.add("Veri yok");
        }

        // Line chart - Payment timeline
        ApexCharts lineChart = ApexChartsBuilder.get()
                .withChart(ChartBuilder.get().withType(Type.LINE).withHeight("260px").withBackground("transparent").build())
                .withXaxis(XAxisBuilder.get().withCategories(catLabels).build())
                .withSeries(new Series<>("Tutar", lineData.toArray(new Double[0])))
                .withColors("#2196F3")
                .withTooltip(TooltipBuilder.get()
                        .withY(YBuilder.get()
                                .withFormatter("function(val, opts) { return new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(val)); }")
                                .build())
                        .build())
                .build();
        lineChart.setWidth("100%");

        // Pie chart - Payment distribution
        ApexCharts pieChart = ApexChartsBuilder.get()
                .withChart(ChartBuilder.get().withType(Type.DONUT).withHeight("260px").withBackground("transparent").build())
                .withLabels(pieLabels.toArray(new String[0]))
                .withSeries(pieData.toArray(new Double[0]))
                .withTooltip(TooltipBuilder.get()
                        .withY(YBuilder.get()
                                .withFormatter("function(val, opts) { return new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Number(val)); }")
                                .build())
                        .build())
                .build();
        pieChart.setWidth("100%");

        Div lineWrapper = buildChartCard("Ödeme Akışı (Çizgi Grafik)", lineChart);
        Div pieWrapper = buildChartCard("Ödeme Dağılımı (Pasta Grafik)", pieChart);
        chartsContainer.add(lineWrapper, pieWrapper);
    }

    private Div buildChartCard(String title, ApexCharts chart) {
        Div card = new Div();
        card.getStyle()
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "8px")
                .set("padding", "12px")
                .set("background", "var(--lumo-base-color)");
        Span titleSpan = new Span(title);
        titleSpan.getStyle()
                .set("font-size", "0.8em")
                .set("font-weight", "600")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block")
                .set("margin-bottom", "8px");
        card.add(titleSpan, chart);
        return card;
    }

    private void resetManual() {
        manualResultBox.setVisible(false);
        manualResultBox.removeAll();
        chartsContainer.setVisible(false);
        chartsContainer.removeAll();
        modeGroup.setValue("Vade sonu tarihlerini girerek hesapla");
        countSelect.setValue(2);
        manualTotalSpan.setText("0,00 TL");
        rebuildRows();
    }
}
