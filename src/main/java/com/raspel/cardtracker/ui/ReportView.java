package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.raspel.cardtracker.domain.cheque.Cheque;
import com.raspel.cardtracker.domain.cheque.ChequeService;
import com.raspel.cardtracker.domain.cheque.ChequeType;
import com.raspel.cardtracker.domain.expense.ExpenseService;
import com.raspel.cardtracker.domain.expense.InstallmentEntry;
import com.raspel.cardtracker.domain.report.MonthlyReportService;
import jakarta.annotation.security.PermitAll;

import com.github.appreciated.apexcharts.ApexCharts;
import com.github.appreciated.apexcharts.ApexChartsBuilder;
import com.github.appreciated.apexcharts.config.builder.*;
import com.github.appreciated.apexcharts.config.chart.Type;
import com.github.appreciated.apexcharts.config.plotoptions.builder.BarBuilder;
import com.github.appreciated.apexcharts.helper.Series;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import com.raspel.cardtracker.domain.settings.AppSettingsService;

@Route(value = "reports", layout = MainLayout.class)
@PageTitle("Finansal Raporlar")
@PermitAll
public class ReportView extends VerticalLayout {

    private final MonthlyReportService reportService;
    private final ExpenseService expenseService;
    private final ChequeService chequeService;
    private final AppSettingsService appSettingsService;

    private final ComboBox<Integer> yearSelect = new ComboBox<>("Yıl");
    private final ComboBox<Month> monthSelect = new ComboBox<>("Ay");
    private final Div previewContainer = new Div();
    private final HorizontalLayout downloadLayout = new HorizontalLayout();
    private final ProgressBar loadingBar = new ProgressBar();

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.of("tr", "TR"));

    public ReportView(MonthlyReportService reportService, ExpenseService expenseService, ChequeService chequeService, AppSettingsService appSettingsService) {
        this.reportService = reportService;
        this.expenseService = expenseService;
        this.chequeService = chequeService;
        this.appSettingsService = appSettingsService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);
        loadingBar.setWidthFull();
        add(loadingBar);

        H3 title = new H3("Aylık Finansal PDF Rapor Oluşturma");
        title.getStyle().set("margin", "0");
        
        HorizontalLayout header = new HorizontalLayout(title, downloadLayout);
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);
        header.setJustifyContentMode(JustifyContentMode.BETWEEN);

        add(header);

        setupFilters();
        add(previewContainer);

        // Varsayılan olarak geçerli ayı yükle
        LocalDate today = LocalDate.now();
        yearSelect.setValue(today.getYear());
        monthSelect.setValue(today.getMonth());
        
        updatePreviewAndDownload();
    }

    private void setupFilters() {
        HorizontalLayout filterLayout = new HorizontalLayout();
        filterLayout.setAlignItems(Alignment.END);
        filterLayout.setSpacing(true);

        List<Integer> years = new java.util.ArrayList<>();
        for (int y = LocalDate.now().getYear() - 1; y <= LocalDate.now().getYear() + 10; y++) years.add(y);
        yearSelect.setItems(years);
        yearSelect.setValue(LocalDate.now().getYear());
        yearSelect.addValueChangeListener(e -> updatePreviewAndDownload());

        monthSelect.setItems(Arrays.asList(Month.values()));
        monthSelect.setItemLabelGenerator(m -> m.getDisplayName(TextStyle.FULL, Locale.of("tr", "TR")));
        monthSelect.setValue(LocalDate.now().getMonth());
        monthSelect.addValueChangeListener(e -> updatePreviewAndDownload());

        filterLayout.add(yearSelect, monthSelect);
        add(filterLayout);
    }

    private void updatePreviewAndDownload() {
        loadingBar.setVisible(true);
        Integer year = yearSelect.getValue();
        Month month = monthSelect.getValue();

        if (year == null || month == null) {
            previewContainer.setText("Lütfen yıl ve ay seçimi yapın.");
            downloadLayout.removeAll();
            loadingBar.setVisible(false);
            return;
        }

        int monthVal = month.getValue();

        // 1) Önizleme Verilerini Al
        List<InstallmentEntry> installments = expenseService.getInstallmentsForMonth(year, monthVal);
        List<Cheque> monthlyCheques = chequeService.findAll().stream()
                .filter(c -> c.getMaturityDate().getYear() == year && c.getMaturityDate().getMonthValue() == monthVal)
                .collect(Collectors.toList());

        BigDecimal cardSpent = installments.stream()
                .map(InstallmentEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal incomingCheques = monthlyCheques.stream()
                .filter(c -> c.getType() == ChequeType.ENTERING)
                .map(Cheque::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal outgoingCheques = monthlyCheques.stream()
                .filter(c -> c.getType() == ChequeType.EXITING)
                .map(Cheque::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal netCashflow = incomingCheques.subtract(outgoingCheques).subtract(cardSpent);

        // 2) Önizleme Kartlarını Oluştur
        previewContainer.removeAll();
        previewContainer.setWidthFull();

        Div title = new Div();
        title.setText(month.getDisplayName(TextStyle.FULL, Locale.of("tr", "TR")) + " " + year + " Önizleme Verileri");
        title.getStyle().set("font-weight", "bold").set("margin-bottom", "1em").set("font-size", "1.1em");
        previewContainer.add(title);

        Div cardSpentCard = createStatCard("Kart Taksit Harcaması", currencyFormat.format(cardSpent), "💸", "#F44336");
        Div incomingChequeCard = createStatCard("Alınan Çek Portföyü", currencyFormat.format(incomingCheques), "📥", "#4CAF50");
        Div outgoingChequeCard = createStatCard("Verilen Çek Portföyü", currencyFormat.format(outgoingCheques), "📤", "#FF9800");
        Div netCashflowCard = createStatCard("Tahmini Net Akış", currencyFormat.format(netCashflow), "📊", "#2196F3");

        HorizontalLayout cards = new HorizontalLayout(cardSpentCard, incomingChequeCard, outgoingChequeCard, netCashflowCard);
        cards.setWidthFull();
        cards.setSpacing(true);
        previewContainer.add(cards);

        // Grafik Container
        HorizontalLayout chartsLayout = new HorizontalLayout();
        chartsLayout.setWidthFull();
        chartsLayout.setSpacing(true);
        chartsLayout.getStyle().set("margin-top", "2em");

        // Pasta Grafiği (Gelir / Gider Dağılımı)
        BigDecimal totalExpenses = cardSpent.add(outgoingCheques);
        ApexCharts donutChart = ApexChartsBuilder.get()
                .withChart(ChartBuilder.get().withType(Type.DONUT).build())
                .withLabels("Gelen Çekler", "Giderler (Çıkan Çek + Taksit)")
                .withSeries(incomingCheques.doubleValue(), totalExpenses.doubleValue())
                .withColors("#4CAF50", "#F44336")
                .build();
        donutChart.setWidth("100%");

        Div donutContainer = new Div(new H3("Gelir / Gider (Nakit Akışı)"), donutChart);
        donutContainer.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("padding", "1.5em")
                .set("border-radius", "8px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.06)")
                .set("flex", "1");

        // Bar Grafiği (Kategorik Harcama Dağılımı)
        Map<String, Double> categoryTotals = installments.stream()
                .collect(Collectors.groupingBy(
                        e -> e.getExpense().getCategory() != null ? e.getExpense().getCategory() : "Diğer",
                        Collectors.summingDouble(e -> e.getAmount().doubleValue())
                ));

        List<String> categories = new java.util.ArrayList<>(categoryTotals.keySet());
        List<Double> amounts = categories.stream().map(categoryTotals::get).collect(Collectors.toList());

        ApexCharts barChart = ApexChartsBuilder.get()
                .withChart(ChartBuilder.get().withType(Type.BAR).build())
                .withPlotOptions(PlotOptionsBuilder.get()
                        .withBar(BarBuilder.get()
                                .withHorizontal(false)
                                .withColumnWidth("55%")
                                .build())
                        .build())
                .withXaxis(XAxisBuilder.get()
                        .withCategories(categories.toArray(new String[0]))
                        .build())
                .withSeries(new Series<>("Harcama Tutarı (₺)", amounts.toArray(new Double[0])))
                .withColors("#2196F3")
                .build();
        barChart.setWidth("100%");

        Div barContainer = new Div(new H3("Kategorik Harcama Dağılımı (Kredi Kartları)"), barChart);
        barContainer.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("padding", "1.5em")
                .set("border-radius", "8px")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.06)")
                .set("flex", "2");

        if (incomingCheques.compareTo(BigDecimal.ZERO) == 0 && totalExpenses.compareTo(BigDecimal.ZERO) == 0) {
            donutContainer.add(new Span("Veri bulunamadı."));
        }
        if (amounts.isEmpty()) {
            barContainer.add(new Span("Harcama verisi bulunamadı."));
        }

        chartsLayout.add(donutContainer, barContainer);
        previewContainer.add(chartsLayout);

        // 3) PDF İndirme Butonu Anchor Olarak Oluştur
        downloadLayout.removeAll();
        
        String companySlug = appSettingsService.getCompanyName().replaceAll("[^a-zA-Z0-9ğüşıöçĞÜŞİÖÇ]", "_");
        String fileName = String.format("%s_Rapor_%d_%d.pdf", companySlug, year, monthVal);
        StreamResource resource = new StreamResource(fileName, () -> reportService.generateMonthlyReport(year, monthVal));
        
        Anchor downloadAnchor = new Anchor(resource, "");
        downloadAnchor.getElement().setAttribute("download", true);
        
        Button downloadBtn = new Button("PDF Raporu İndir", new Icon(VaadinIcon.DOWNLOAD));
        downloadBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        downloadBtn.getStyle().set("margin-top", "0");
        
        downloadAnchor.add(downloadBtn);
        downloadLayout.add(downloadAnchor);
        loadingBar.setVisible(false);
    }

    private Div createStatCard(String title, String value, String icon, String color) {
        Div card = new Div();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "8px")
                .set("padding", "1.2em")
                .set("box-shadow", "0 2px 6px rgba(0,0,0,0.06)")
                .set("flex", "1")
                .set("border-left", "4px solid " + color);

        HorizontalLayout layout = new HorizontalLayout();
        layout.setAlignItems(Alignment.CENTER);

        Span iconSpan = new Span(icon);
        iconSpan.getStyle().set("font-size", "1.6em").set("color", color);

        VerticalLayout textLayout = new VerticalLayout();
        textLayout.setPadding(false);
        textLayout.setSpacing(false);

        Span titleEl = new Span(title);
        titleEl.getStyle().set("font-size", "0.85em").set("color", "var(--lumo-secondary-text-color)");

        Span valueSpan = new Span(value);
        valueSpan.getStyle().set("font-size", "1.1em").set("font-weight", "bold").set("color", color);

        textLayout.add(titleEl, valueSpan);
        layout.add(iconSpan, textLayout);
        card.add(layout);
        return card;
    }
}
