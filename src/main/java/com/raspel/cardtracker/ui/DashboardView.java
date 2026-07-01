package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.raspel.cardtracker.domain.expense.InstallmentEntry;
import com.raspel.cardtracker.domain.reminder.PaymentReminderService;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.domain.card.CardService;
import com.raspel.cardtracker.domain.expense.ExpenseService;
import com.raspel.cardtracker.domain.expense.TcmbCurrencyService;
import com.raspel.cardtracker.domain.cheque.Cheque;
import com.raspel.cardtracker.domain.cheque.ChequeService;
import com.raspel.cardtracker.domain.cheque.ChequeType;
import com.github.appreciated.apexcharts.ApexCharts;
import com.github.appreciated.apexcharts.ApexChartsBuilder;
import com.github.appreciated.apexcharts.config.builder.*;
import com.github.appreciated.apexcharts.config.tooltip.builder.YBuilder;
import com.github.appreciated.apexcharts.config.chart.Type;
import com.github.appreciated.apexcharts.config.plotoptions.builder.BarBuilder;
import com.github.appreciated.apexcharts.config.stroke.Curve;
import com.github.appreciated.apexcharts.config.builder.StrokeBuilder;
import com.github.appreciated.apexcharts.helper.Series;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import com.raspel.cardtracker.domain.employee.EmployeeService;
import com.raspel.cardtracker.domain.employee.EmployeeTask;
import com.raspel.cardtracker.domain.employee.TaskStatus;
import com.raspel.cardtracker.ui.utils.HolidayUtils;
import com.raspel.cardtracker.domain.user.UserService;
import com.raspel.cardtracker.domain.user.DashboardConfig;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;

@Route(value = "", layout = MainLayout.class)
@RouteAlias(value = "dashboard", layout = MainLayout.class)
@PageTitle("Ana Sayfa")
@PermitAll
public class DashboardView extends VerticalLayout implements BeforeEnterObserver {

    private final ExpenseService expenseService;
    private final CardService cardService;
    private final TcmbCurrencyService currencyService;
    private final ChequeService chequeService;
    private final UserService userService;
    private final PaymentReminderService reminderService;
    private final EmployeeService employeeService;
    private final NumberFormat currencyFormat;
    
    private DashboardConfig currentConfig;
    private VerticalLayout dashboardContent;
    private List<Card> cachedCards;
    private Map<Long, BigDecimal> cachedUnpaidMap;
    private List<InstallmentEntry> cachedMonthInstallments;
    private YearMonth cachedYearMonth;

    public DashboardView(ExpenseService expenseService, CardService cardService,
                         TcmbCurrencyService currencyService,
                         ChequeService chequeService, UserService userService, PaymentReminderService reminderService,
                         EmployeeService employeeService) {
        this.expenseService = expenseService;
        this.cardService = cardService;
        this.currencyService = currencyService;
        this.chequeService = chequeService;
        this.userService = userService;
        this.reminderService = reminderService;
        this.employeeService = employeeService;
        this.currencyFormat = NumberFormat.getCurrencyInstance(Locale.of("tr", "TR"));

        addClassName("dashboard-view");
        setSpacing(true);
        setPadding(true);

        Div loadingIndicator = new Div();
        loadingIndicator.setText("Yükleniyor...");
        loadingIndicator.getStyle().set("text-align", "center").set("padding", "40px");
        loadingIndicator.setId("dashboard-loading");
        add(loadingIndicator);

        dashboardContent = new VerticalLayout();
        dashboardContent.setPadding(false);
        dashboardContent.setSpacing(true);
        add(dashboardContent);

        loadConfigAndRender();
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        loadConfigAndRender();
    }
    
    private void loadConfigAndRender() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "system";
        currentConfig = userService.getDashboardConfig(username);

        cachedYearMonth = YearMonth.now();
        cachedCards = cardService.findAllActive();
        cachedUnpaidMap = cachedCards.isEmpty() ? java.util.Collections.emptyMap() : expenseService.getUnpaidBalancesGroupedByCard();
        cachedMonthInstallments = expenseService.getInstallmentsForMonth(cachedYearMonth.getYear(), cachedYearMonth.getMonthValue());
        
        getUI().ifPresent(ui -> ui.getPage().executeJs(
            "window._dashScrollY = window.scrollY || window.pageYOffset; return window._dashScrollY;"
        ));
        
        dashboardContent.removeAll();

        dashboardContent.add(createWelcomeSection(username));

        for (DashboardConfig.WidgetConfig wc : currentConfig.getVisibleWidgets()) {
            switch (wc.getId()) {
                case "limit_warnings" -> dashboardContent.add(createLimitWarningBanners());
                case "currency_rates" -> dashboardContent.add(createCurrencyRatesPanel());
                case "payment_reminders" -> dashboardContent.add(createRemindersPanel());
                case "summary_cards" -> dashboardContent.add(createSummaryCards());
                case "charts_row" -> dashboardContent.add(createChartsRow());
                case "second_row" -> dashboardContent.add(createSecondRow());
                case "third_row" -> dashboardContent.add(createThirdRow());
                case "fourth_row" -> dashboardContent.add(createFourthRow());
            }
        }

        getElement().executeJs(
            "var el=document.getElementById('dashboard-loading');if(el)el.style.display='none';" +
            "if(window._dashScrollY !== undefined){" +
            "  window.scrollTo(0, window._dashScrollY);" +
            "  delete window._dashScrollY;" +
            "}" +
            "if(!window.__tooltipPatched){" +
            "window.__tooltipPatched=true;" +
            "var fmt=new Intl.NumberFormat('tr-TR',{minimumFractionDigits:0,maximumFractionDigits:1});" +
            "function patchCurrency(){document.querySelectorAll('.apexcharts-tooltip-text-y-value:not([data-fmt]), .apexcharts-text.apexcharts-yaxis-label:not([data-fmt])').forEach(function(v){" +
            "  var num=parseFloat(v.textContent.replace(/[^0-9.-]/g,''));" +
            "  if(!isNaN(num)){v.textContent=fmt.format(num)+'₺';v.setAttribute('data-fmt','1');}" +
            "});}" +
            "patchCurrency();" +
            "setInterval(patchCurrency,500);" +
            "}"
        );
    }

    private Div createWelcomeSection(String username) {
        String displayName = username;
        try {
            var user = userService.findByUsername(username);
            if (user.isPresent() && user.get().getFullName() != null && !user.get().getFullName().trim().isEmpty()) {
                displayName = user.get().getFullName();
            }
        } catch (Exception ignored) {
            // Expected: user data unavailable during initial load
        }

        Div welcomeBox = new Div();
        welcomeBox.getStyle()
                .set("background", "linear-gradient(135deg, var(--lumo-primary-color-10pct), var(--lumo-base-color))")
                .set("border-radius", "16px")
                .set("padding", "2em 2.5em")
                .set("margin-bottom", "1em")
                .set("box-shadow", "0 2px 12px rgba(0,0,0,0.1)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("width", "100%")
                .set("box-sizing", "border-box");

        Span greeting = new Span("Hoş geldin, " + displayName);
        greeting.getStyle()
                .set("font-size", "2em")
                .set("font-weight", "700")
                .set("color", "var(--lumo-header-text-color)")
                .set("display", "block")
                .set("margin-bottom", "0.3em");

        LocalDate today = LocalDate.now();
        DateTimeFormatter trDateFormatter = DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.of("tr"));
        Span dateSpan = new Span(today.format(trDateFormatter));
        dateSpan.getStyle()
                .set("font-size", "0.95em")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block")
                .set("margin-bottom", "0.5em");

        long unpaidPayments = cachedMonthInstallments.stream().filter(e -> !e.getIsPaid()).count();

        long pendingTasks = employeeService.findAllTasks().stream()
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED)
                .count();

        long limitWarnings = cachedCards.stream()
                .filter(c -> c.getCardLimit() != null && c.getCardLimit().compareTo(BigDecimal.ZERO) > 0)
                .filter(c -> {
                    BigDecimal unpaid = cachedUnpaidMap.getOrDefault(c.getId(), BigDecimal.ZERO);
                    int threshold = c.getLimitWarningThreshold() != null ? c.getLimitWarningThreshold() : 80;
                    return unpaid.divide(c.getCardLimit(), 4, RoundingMode.HALF_UP).doubleValue() * 100 >= threshold;
                })
                .count();

        long totalWarnings = unpaidPayments + pendingTasks + limitWarnings;
        String warningText = "Bugün sistemde " + totalWarnings + " adet kritik uyarı bulunuyor.";
        if (limitWarnings > 0) {
            warningText += " (" + limitWarnings + " limit uyarısı)";
        }

        Span summary = new Span(warningText);
        summary.getStyle()
                .set("font-size", "0.9em")
                .set("color", "var(--lumo-body-text-color)")
                .set("display", "block");

        VerticalLayout textPart = new VerticalLayout(greeting, dateSpan, summary);
        textPart.setPadding(false);
        textPart.setSpacing(false);

        VerticalLayout actionsPart = new VerticalLayout();
        actionsPart.setPadding(false);
        actionsPart.setSpacing(false);
        actionsPart.getStyle().set("gap", "8px");
        actionsPart.setAlignItems(FlexComponent.Alignment.END);

        Button newExpenseBtn = new Button("Harcama Ekle", new Icon(VaadinIcon.PLUS), e ->
                getUI().ifPresent(ui -> ui.navigate("expenses")));
        newExpenseBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        newExpenseBtn.getStyle().set("border-radius", "8px");

        Button newChequeBtn = new Button("Çek Ekle", new Icon(VaadinIcon.PLUS), e ->
                getUI().ifPresent(ui -> ui.navigate("cheques")));
        newChequeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        newChequeBtn.getStyle().set("border-radius", "8px");

        Button newCardBtn = new Button("Kart Ekle", new Icon(VaadinIcon.PLUS), e ->
                getUI().ifPresent(ui -> ui.navigate("cards")));
        newCardBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        newCardBtn.getStyle().set("border-radius", "8px");

        actionsPart.add(newExpenseBtn, newChequeBtn, newCardBtn);

        HorizontalLayout cardContent = new HorizontalLayout(textPart, actionsPart);
        cardContent.setWidthFull();
        cardContent.expand(textPart);
        cardContent.setAlignItems(FlexComponent.Alignment.CENTER);

        welcomeBox.add(cardContent);
        return welcomeBox;
    }

    private VerticalLayout createLimitWarningBanners() {
        VerticalLayout warningsLayout = new VerticalLayout();
        warningsLayout.setPadding(false);
        warningsLayout.setSpacing(true);

        for (Card card : cachedCards) {
            BigDecimal unpaid = cachedUnpaidMap.getOrDefault(card.getId(), BigDecimal.ZERO);
            BigDecimal limit = card.getCardLimit();
            
            if (limit != null && limit.compareTo(BigDecimal.ZERO) > 0) {
                double pct = unpaid.divide(limit, 4, RoundingMode.HALF_UP).doubleValue() * 100;
                int threshold = card.getLimitWarningThreshold() != null ? card.getLimitWarningThreshold() : 80;

                // % limit doluluk uyarisi
                if (pct >= threshold) {
                    Div banner = new Div();
                    banner.getStyle()
                            .set("background-color", "#FFEBEE")
                            .set("color", "#C62828")
                            .set("border-left", "5px solid #D32F2F")
                            .set("border-radius", "8px")
                            .set("padding", "1em 1.5em")
                            .set("box-shadow", "0 2px 4px rgba(0,0,0,0.05)")
                            .set("width", "100%")
                            .set("box-sizing", "border-box")
                            .set("display", "flex")
                            .set("align-items", "center")
                            .set("gap", "1em");

                    Icon warningIcon = new Icon(VaadinIcon.WARNING);
                    warningIcon.getStyle().set("color", "#D32F2F").set("font-size", "1.5em");

                    VerticalLayout textLayout = new VerticalLayout();
                    textLayout.setPadding(false);
                    textLayout.setSpacing(false);

                    Span title = new Span("KRİTİK LİMİT UYARISI: " + card.getName() + " (" + card.getBank() + ")");
                    title.getStyle().set("font-weight", "bold");

                    Span details = new Span(String.format(
                            "Kart limit kullanımı %%%,.1f seviyesine ulaştı! Borç: %,.2f ₺ / Limit: %,.2f ₺. Sorumlu: %s (%s)",
                            pct, unpaid, limit, 
                            card.getHolderName() != null ? card.getHolderName() : "-",
                            card.getDepartment() != null ? card.getDepartment() : "-"
                    ));
                    details.getStyle().set("font-size", "0.9em");

                    textLayout.add(title, details);
                    banner.add(warningIcon, textLayout);
                    warningsLayout.add(banner);
                }
            }
        }
        return warningsLayout;
    }

    private HorizontalLayout createCurrencyRatesPanel() {
        HorizontalLayout layout = new HorizontalLayout();
        layout.setWidthFull();
        layout.setSpacing(true);
        layout.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("padding", "0.8em 1.5em")
                .set("border-radius", "12px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.08)")
                .set("align-items", "center")
                .set("justify-content", "space-around")
                .set("flex-wrap", "wrap");

        try {
            BigDecimal usd = currencyService.getExchangeRate("USD", LocalDate.now());
            BigDecimal eur = currencyService.getExchangeRate("EUR", LocalDate.now());
            BigDecimal gold = currencyService.getGoldPrice();
            BigDecimal sar = currencyService.getExchangeRate("SAR", LocalDate.now());

            Span usdSpan = new Span(String.format("💵 USD %,.4f ₺", usd));
            usdSpan.getStyle().set("font-weight", "600").set("color", "#1E3C72");

            Span eurSpan = new Span(String.format("💶 EUR %,.4f ₺", eur));
            eurSpan.getStyle().set("font-weight", "600").set("color", "#2A5298");

            Span sarSpan = new Span(String.format("﷼ SAR %,.4f ₺", sar));
            sarSpan.getStyle().set("font-weight", "600").set("color", "#1B5E20");

            Span goldSpan = new Span(String.format("🪙 Altın %,.2f ₺", gold));
            goldSpan.getStyle().set("font-weight", "600").set("color", "#C5A059");

            layout.add(usdSpan, eurSpan, sarSpan, goldSpan);
        } catch (Exception e) {
            Span error = new Span("Canlı finans kurları yüklenemedi.");
            error.getStyle().set("color", "var(--lumo-error-color)");
            layout.add(error);
        }

        return layout;
    }

    private HorizontalLayout createSummaryCards() {
        YearMonth now = YearMonth.now();
        int year = now.getYear();
        int month = now.getMonthValue();

        BigDecimal totalThisMonth = expenseService.getTotalForMonth(year, month);
        long activeCards = cachedCards.size();
        int installmentCount = cachedMonthInstallments.size();

        Map<String, BigDecimal> projection = expenseService.getMonthlyProjection(6);
        BigDecimal totalDebt = projection.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        // Geçen ay karşılaştırması
        YearMonth lastMonth = now.minusMonths(1);
        BigDecimal totalLastMonth = expenseService.getTotalForMonth(lastMonth.getYear(), lastMonth.getMonthValue());
        String comparisonStr = currencyFormat.format(totalThisMonth);
        if (totalLastMonth.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal diff = totalThisMonth.subtract(totalLastMonth);
            double pctChange = diff.divide(totalLastMonth, 4, RoundingMode.HALF_UP).doubleValue() * 100;
            String arrow = pctChange >= 0 ? "↑" : "↓";
            String pctColor = pctChange >= 0 ? "#F44336" : "#4CAF50";
            comparisonStr += " " + arrow + " %" + Math.abs(Math.round(pctChange));
        }

        Div totalCard = createStatCard("Bu Ay Toplam Harcama", comparisonStr, "💰", "#4CAF50");
        totalCard.getStyle().set("cursor", "pointer");
        totalCard.addClickListener(e -> totalCard.getUI().ifPresent(ui -> ui.navigate(ExpenseView.class)));
        
        // Geçen ay toplamını da göster
        Span lastMonthSpan = new Span("Geçen Ay: " + currencyFormat.format(totalLastMonth));
        lastMonthSpan.getStyle()
                .set("font-size", "0.7em")
                .set("color", "var(--lumo-tertiary-text-color)")
                .set("display", "block")
                .set("margin-top", "0.2em");
        totalCard.add(lastMonthSpan);

        Div activeCardsCard = createStatCard("Aktif Kartlar", String.valueOf(activeCards), "💳", "#2196F3");
        activeCardsCard.getStyle().set("cursor", "pointer");
        activeCardsCard.addClickListener(e -> activeCardsCard.getUI().ifPresent(ui -> ui.navigate(CardListView.class)));

        Div installmentCard = createStatCard("Bu Ay Taksit Sayısı", String.valueOf(installmentCount), "📋", "#FF9800");
        installmentCard.getStyle().set("cursor", "pointer");
        installmentCard.addClickListener(e -> installmentCard.getUI().ifPresent(ui -> ui.navigate(ExpenseView.class)));

        Div debtCard = createStatCard("6 Ay Toplam Borç", currencyFormat.format(totalDebt), "📊", "#F44336");

        long pendingTasks = employeeService.findAllTasks().stream().filter(t -> t.getStatus() != TaskStatus.COMPLETED).count();
        Div taskCard = createStatCard("Bekleyen Görevler", String.valueOf(pendingTasks), "✅", "#9C27B0");
        taskCard.getStyle().set("cursor", "pointer");
        taskCard.addClickListener(e -> taskCard.getUI().ifPresent(ui -> ui.navigate(EmployeeTaskView.class)));

        HorizontalLayout cards = new HorizontalLayout(totalCard, activeCardsCard, installmentCard, debtCard, taskCard);
        cards.addClassName("summary-cards");
        cards.setWidthFull();
        cards.setSpacing(true);

        return cards;
    }

    private Div createStatCard(String title, String value, String icon, String color) {
        Div card = new Div();
        card.addClassName("stat-card");
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "12px")
                .set("padding", "1.5em")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.1)")
                .set("flex", "1")
                .set("min-width", "200px")
                .set("border-left", "4px solid " + color);

        Span iconSpan = new Span(icon);
        iconSpan.getStyle().set("font-size", "2em");

        H4 titleEl = new H4(title);
        titleEl.getStyle()
                .set("margin", "0.5em 0 0.2em 0")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "0.9em")
                .set("font-weight", "400");

        Span valueSpan = new Span(value);
        valueSpan.getStyle()
                .set("font-size", "1.6em")
                .set("font-weight", "700")
                .set("color", color);

        card.add(iconSpan, titleEl, valueSpan);
        return card;
    }

    private HorizontalLayout createChartsRow() {
        HorizontalLayout row = new HorizontalLayout();
        row.addClassName("charts-row");
        row.setWidthFull();
        row.setSpacing(true);

        Div barChartContainer = createChartContainer("Kart Bazlı Ödeme Dağılımı", createBarChart());
        Div lineChartContainer = createChartContainer("6 Aylık Projeksiyon", createLineChart());

        barChartContainer.getStyle().set("flex", "1").set("min-width", "350px");
        lineChartContainer.getStyle().set("flex", "1").set("min-width", "350px");

        row.add(barChartContainer, lineChartContainer);
        return row;
    }

    private HorizontalLayout createSecondRow() {
        HorizontalLayout row = new HorizontalLayout();
        row.addClassName("second-row");
        row.setWidthFull();
        row.setSpacing(true);

        Div deptChartContainer = createChartContainer("Departman Harcama Dağılımı", createDeptChart());
        Div categoryPieContainer = createChartContainer("Kategori Dağılımı (Bu Ay)", createCategoryPieChart());

        for (Div c : new Div[]{deptChartContainer, categoryPieContainer}) {
            Span emptyHint = new Span("Bu ay için henüz harcama kaydı bulunmuyor.");
            emptyHint.getStyle()
                    .set("display", "none")
                    .set("text-align", "center")
                    .set("padding", "2em")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("font-size", "0.85em")
                    .set("border", "1px dashed var(--lumo-contrast-20pct)")
                    .set("border-radius", "8px")
                    .set("margin-top", "1em");
            emptyHint.setId("empty-chart-hint");
            c.add(emptyHint);
        }
        Div scheduleContainer = createPaymentScheduleContainer();

        deptChartContainer.getStyle().set("flex", "1").set("min-width", "350px");
        categoryPieContainer.getStyle().set("flex", "1").set("min-width", "350px");
        scheduleContainer.getStyle().set("flex", "1").set("min-width", "350px");

        row.add(deptChartContainer, categoryPieContainer, scheduleContainer);
        return row;
    }

    private HorizontalLayout createThirdRow() {
        HorizontalLayout row = new HorizontalLayout();
        row.addClassName("third-row");
        row.setWidthFull();
        row.setSpacing(true);

        Div yearlyComparisonContainer = createYearlyComparisonPanel();
        Div cashFlowContainer = createChartContainer("6 Aylık Nakit Akış Analizi (Çek ve Harcamalar)", createCashFlowChart());

        yearlyComparisonContainer.getStyle().set("flex", "1").set("min-width", "350px");
        cashFlowContainer.getStyle().set("flex", "1").set("min-width", "350px");

        row.add(yearlyComparisonContainer, cashFlowContainer);
        return row;
    }

    private HorizontalLayout createFourthRow() {
        HorizontalLayout row = new HorizontalLayout();
        row.addClassName("fourth-row");
        row.setWidthFull();
        row.setSpacing(true);

        Div trendChartContainer = createChartContainer("12 Aylık Harcama Trendi", createTrendChart());

        trendChartContainer.getStyle().set("flex", "1").set("min-width", "350px");

        row.add(trendChartContainer);
        return row;
    }

    private Div createYearlyComparisonPanel() {
        Div container = new Div();
        container.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "12px")
                .set("padding", "1.5em")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.1)")
                .set("height", "420px")
                .set("overflow-y", "auto");

        H3 titleEl = new H3("Yıllık Karşılaştırma (Bu Yıl vs Geçen Yıl)");
        titleEl.getStyle().set("margin", "0 0 1em 0").set("font-size", "1.1em");
        container.add(titleEl);

        int thisYear = YearMonth.now().getYear();
        int lastYear = thisYear - 1;
        int currentMonth = YearMonth.now().getMonthValue();

        // Yıllık toplamlar
        BigDecimal thisYearTotal = BigDecimal.ZERO;
        BigDecimal lastYearTotal = BigDecimal.ZERO;
        for (int m = 1; m <= currentMonth; m++) {
            thisYearTotal = thisYearTotal.add(expenseService.getTotalForMonth(thisYear, m));
            lastYearTotal = lastYearTotal.add(expenseService.getTotalForMonth(lastYear, m));
        }

        HorizontalLayout totalsRow = new HorizontalLayout();
        totalsRow.setWidthFull();
        totalsRow.getStyle().set("margin-bottom", "1em").set("gap", "1em");

        Div thisYearDiv = new Div();
        thisYearDiv.getStyle()
                .set("flex", "1")
                .set("padding", "0.8em")
                .set("border-radius", "8px")
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("text-align", "center");
        Span thisYearLabel = new Span(String.valueOf(thisYear));
        thisYearLabel.getStyle().set("font-size", "0.8em").set("color", "var(--lumo-secondary-text-color)").set("display", "block");
        Span thisYearVal = new Span(currencyFormat.format(thisYearTotal));
        thisYearVal.getStyle().set("font-weight", "bold").set("font-size", "1.2em").set("color", "var(--lumo-primary-text-color)");
        thisYearDiv.add(thisYearLabel, thisYearVal);

        Div lastYearDiv = new Div();
        lastYearDiv.getStyle()
                .set("flex", "1")
                .set("padding", "0.8em")
                .set("border-radius", "8px")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("text-align", "center");
        Span lastYearLabel = new Span(String.valueOf(lastYear));
        lastYearLabel.getStyle().set("font-size", "0.8em").set("color", "var(--lumo-secondary-text-color)").set("display", "block");
        Span lastYearVal = new Span(currencyFormat.format(lastYearTotal));
        lastYearVal.getStyle().set("font-weight", "bold").set("font-size", "1.2em").set("color", "var(--lumo-secondary-text-color)");
        lastYearDiv.add(lastYearLabel, lastYearVal);

        totalsRow.add(thisYearDiv, lastYearDiv);

        // Yüzde fark
        Div diffDiv = new Div();
        if (lastYearTotal.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal yDiff = thisYearTotal.subtract(lastYearTotal);
            double yPct = yDiff.divide(lastYearTotal, 4, RoundingMode.HALF_UP).doubleValue() * 100;
            String arrow = yPct >= 0 ? "↑" : "↓";
            String color = yPct >= 0 ? "var(--lumo-error-color)" : "var(--lumo-success-color)";
            Span diffSpan = new Span(arrow + " %" + Math.abs(Math.round(yPct)) + " fark");
            diffSpan.getStyle().set("font-weight", "bold").set("font-size", "1em").set("color", color);
            diffDiv.add(diffSpan);
            diffDiv.getStyle().set("text-align", "center").set("margin-bottom", "1em");
        }

        container.add(totalsRow, diffDiv);
        return container;
    }

    private Div createChartContainer(String title, ApexCharts chart) {
        Div container = new Div();
        container.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "12px")
                .set("padding", "1.5em")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.1)")
                .set("min-width", "350px")
                .set("max-width", "100%")
                .set("overflow", "hidden");

        H3 titleEl = new H3(title);
        titleEl.getStyle()
                .set("margin", "0 0 1em 0")
                .set("font-size", "1.1em");

        chart.setWidth("100%");
        chart.setHeight("350px");

        container.add(titleEl, chart);
        return container;
    }

    private Div createPaymentScheduleContainer() {
        Div container = new Div();
        container.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "12px")
                .set("padding", "1.5em")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.1)")
                .set("height", "420px")
                .set("overflow-y", "auto");

        H3 titleEl = new H3("Yaklaşan Hesap Kesim ve Ödeme Takvimi");
        titleEl.getStyle().set("margin", "0 0 1.5em 0").set("font-size", "1.1em");
        container.add(titleEl);

        VerticalLayout scheduleList = new VerticalLayout();
        scheduleList.setPadding(false);
        scheduleList.setSpacing(true);

        List<Card> activeCards = cachedCards;
        
        List<CardSchedule> cardSchedules = new ArrayList<>();
        LocalDate today = LocalDate.now();

        for (Card card : activeCards) {
            int closingDay = card.getClosingDay();
            LocalDate statementDate = LocalDate.of(today.getYear(), today.getMonthValue(), Math.min(closingDay, today.lengthOfMonth()));
            if (today.isAfter(statementDate)) {
                statementDate = statementDate.plusMonths(1);
            }
            LocalDate originalDueDate = statementDate.plusDays(card.getDueDay());
            LocalDate actualDueDate = HolidayUtils.getNextBusinessDay(originalDueDate);
            long daysRemaining = ChronoUnit.DAYS.between(today, actualDueDate);

            cardSchedules.add(new CardSchedule(card, statementDate, originalDueDate, actualDueDate, daysRemaining));
        }

        cardSchedules.sort((a, b) -> Long.compare(a.daysRemaining, b.daysRemaining));
        cardSchedules.removeIf(s -> s.daysRemaining > 7);

        if (cardSchedules.isEmpty()) {
            Span noReminders = new Span("Yakin zamanda hesap kesimi / ödeme yok.");
            noReminders.getStyle().set("color", "var(--lumo-success-text-color)").set("font-weight", "600");
            scheduleList.add(noReminders);
        } else {
            for (CardSchedule schedule : cardSchedules) {
                Card card = schedule.card;

                HorizontalLayout item = new HorizontalLayout();
                item.setWidthFull();
                item.setAlignItems(Alignment.CENTER);
                item.getStyle()
                        .set("border-bottom", "1px solid var(--lumo-contrast-10pct)")
                        .set("padding-bottom", "0.8em")
                        .set("margin-bottom", "0.5em");

                Div colorIcon = new Div();
                colorIcon.getStyle()
                        .set("width", "12px")
                        .set("height", "12px")
                        .set("border-radius", "50%")
                        .set("background-color", card.getColor() != null ? card.getColor() : "#1976D2")
                        .set("flex-shrink", "0");

                VerticalLayout cardDetails = new VerticalLayout();
                cardDetails.setPadding(false);
                cardDetails.setSpacing(false);
                Span cardName = new Span(card.getName() + " (" + card.getBank() + ")");
                cardName.getStyle().set("font-weight", "bold").set("font-size", "0.95em");
                Span ownerInfo = new Span((card.getHolderName() != null ? card.getHolderName() : "-") +
                                         " | " + (card.getDepartment() != null ? card.getDepartment() : "-"));
                ownerInfo.getStyle().set("font-size", "0.8em").set("color", "var(--lumo-secondary-text-color)");
                cardDetails.add(cardName, ownerInfo);

                VerticalLayout datesDetails = new VerticalLayout();
                datesDetails.setPadding(false);
                datesDetails.setSpacing(false);
                datesDetails.setAlignItems(Alignment.END);
                Span closeDate = new Span("Hesap Kesim: " + schedule.statementDate.format(DateTimeFormatter.ofPattern("dd MMMM", Locale.of("tr"))));
                closeDate.getStyle().set("font-size", "0.8em").set("color", "var(--lumo-secondary-text-color)");
                Span dueDate = new Span("Son Ödeme: " + schedule.actualDueDate.format(DateTimeFormatter.ofPattern("dd MMMM EEEE", Locale.of("tr"))));
                dueDate.getStyle().set("font-weight", "500").set("font-size", "0.85em");
                datesDetails.add(closeDate, dueDate);

                if (!schedule.actualDueDate.equals(schedule.originalDueDate)) {
                    Span holidayWarning = new Span("Tatil/Haftasonu nedeniyle ertelenmiştir (" + schedule.originalDueDate.format(DateTimeFormatter.ofPattern("dd.MM")) + ")");
                    holidayWarning.getStyle().set("font-size", "0.7em").set("color", "var(--lumo-error-color)");
                    datesDetails.add(holidayWarning);
                }

                Span daysBadge = new Span();
                if (schedule.daysRemaining < 0) {
                    daysBadge.setText("Gecikti");
                    daysBadge.getElement().getThemeList().add("badge error");
                } else if (schedule.daysRemaining <= 3) {
                    daysBadge.setText(schedule.daysRemaining + " Gün Kaldı");
                    daysBadge.getElement().getThemeList().add("badge error");
                } else if (schedule.daysRemaining <= 7) {
                    daysBadge.setText(schedule.daysRemaining + " Gün Kaldı");
                    daysBadge.getElement().getThemeList().add("badge contrast");
                } else {
                    daysBadge.setText(schedule.daysRemaining + " Gün");
                    daysBadge.getElement().getThemeList().add("badge success");
                }
                daysBadge.getStyle().set("min-width", "80px").set("text-align", "center");

                item.add(colorIcon, cardDetails, datesDetails, daysBadge);
                scheduleList.add(item);
            }
        } // close the else block

        if (activeCards.isEmpty()) {
            Span noData = new Span("Ödeme takvimi için kayıtlı aktif kredi kartı bulunamadı.");
            noData.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "0.9em");
            scheduleList.add(noData);
        }

        container.add(scheduleList);
        return container;
    }

    private ApexCharts createBarChart() {
        Map<String, BigDecimal> cardTotals = expenseService.getCardTotalsForMonth(
                cachedYearMonth.getYear(), cachedYearMonth.getMonthValue(), cachedCards);

        List<String> categories = new ArrayList<>();
        List<Double> data = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : cardTotals.entrySet()) {
            categories.add(entry.getKey());
            data.add(entry.getValue().doubleValue());
        }

        if (cardTotals.isEmpty()) {
            categories.add("Veri yok");
            data.add(0.0);
        }

        ApexCharts chart = ApexChartsBuilder.get()
                .withChart(ChartBuilder.get()
                        .withType(Type.BAR)
                        .withBackground("transparent")
                        .build())
                .withPlotOptions(PlotOptionsBuilder.get()
                        .withBar(BarBuilder.get()
                                .withHorizontal(false)
                                .withColumnWidth("55%")
                                .build())
                        .build())
                .withSeries(new Series<>("Ödeme Tutarı", data.toArray(new Double[0])))
                .withXaxis(XAxisBuilder.get()
                        .withCategories(categories)
                        .build())
                .withColors("#2196F3")
                .withTooltip(TooltipBuilder.get()
                        .withY(YBuilder.get()
                                .withFormatter("function(value) { return new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value); }")
                                .build())
                        .build())
                .build();
        return chart;
    }

    private ApexCharts createLineChart() {
        List<Map<String, Object>> projection = expenseService.getMonthlyProjectionDetailed(6);

        List<String> categories = new ArrayList<>();
        List<Double> paidData = new ArrayList<>();
        List<Double> unpaidData = new ArrayList<>();

        for (Map<String, Object> map : projection) {
            String[] parts = ((String) map.get("label")).split("-");
            YearMonth ym = YearMonth.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            String label = ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.of("tr")) + " " + ym.getYear();
            categories.add(label);
            paidData.add(((BigDecimal) map.get("paid")).doubleValue());
            unpaidData.add(((BigDecimal) map.get("unpaid")).doubleValue());
        }

        ApexCharts chart = ApexChartsBuilder.get()
                .withChart(ChartBuilder.get()
                        .withType(Type.AREA)
                        .withBackground("transparent")
                        .build())
                .withDataLabels(DataLabelsBuilder.get()
                        .withEnabled(false)
                        .build())
                .withStroke(StrokeBuilder.get()
                        .withCurve(Curve.SMOOTH)
                        .build())
                .withSeries(
                        new Series<>("Ödenen", paidData.toArray(new Double[0])),
                        new Series<>("Ödenecek", unpaidData.toArray(new Double[0]))
                )
                .withXaxis(XAxisBuilder.get()
                        .withCategories(categories)
                        .build())
                .withColors("#4CAF50", "#F44336")
                .withTooltip(TooltipBuilder.get()
                        .withY(YBuilder.get()
                                .withFormatter("function(value) { return new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value); }")
                                .build())
                        .build())
                .build();
        return chart;
    }

    private ApexCharts createTrendChart() {
        List<Map<String, Object>> projection = expenseService.getMonthlyProjectionDetailed(12);

        List<String> categories = new ArrayList<>();
        List<Double> paidData = new ArrayList<>();
        List<Double> unpaidData = new ArrayList<>();

        for (Map<String, Object> map : projection) {
            String[] parts = ((String) map.get("label")).split("-");
            YearMonth ym = YearMonth.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
            String label = ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.of("tr")) + " " + ym.getYear();
            categories.add(label);
            paidData.add(((BigDecimal) map.get("paid")).doubleValue());
            unpaidData.add(((BigDecimal) map.get("unpaid")).doubleValue());
        }

        ApexCharts chart = ApexChartsBuilder.get()
                .withChart(ChartBuilder.get()
                        .withType(Type.LINE)
                        .withBackground("transparent")
                        .build())
                .withDataLabels(DataLabelsBuilder.get()
                        .withEnabled(false)
                        .build())
                .withStroke(StrokeBuilder.get()
                        .withCurve(Curve.SMOOTH)
                        .withWidth(3.0)
                        .build())
                .withSeries(
                        new Series<>("Ödenen", paidData.toArray(new Double[0])),
                        new Series<>("Ödenecek", unpaidData.toArray(new Double[0]))
                )
                .withXaxis(XAxisBuilder.get()
                        .withCategories(categories)
                        .build())
                .withColors("#4CAF50", "#F44336")
                .withTooltip(TooltipBuilder.get()
                        .withY(YBuilder.get()
                                .withFormatter("function(value) { return new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value); }")
                                .build())
                        .build())
                .build();
        return chart;
    }

    private ApexCharts createDeptChart() {
        YearMonth now = YearMonth.now();

        List<Object[]> results = expenseService.getTotalsGroupedByCardAndDept(
                now.getYear(), now.getMonthValue());

        Map<String, BigDecimal> deptTotals = new java.util.HashMap<>();
        for (Object[] row : results) {
            String deptFromQuery = row[1] != null ? (String) row[1] : "Diger";
            BigDecimal total = (BigDecimal) row[2];
            if (total.compareTo(BigDecimal.ZERO) > 0) {
                String dept = deptFromQuery.trim().isEmpty() ? "Diger" : deptFromQuery.trim();
                deptTotals.put(dept, deptTotals.getOrDefault(dept, BigDecimal.ZERO).add(total));
            }
        }

        List<String> labels = new ArrayList<>();
        List<Double> series = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : deptTotals.entrySet()) {
            labels.add(entry.getKey());
            series.add(entry.getValue().doubleValue());
        }

        if (deptTotals.isEmpty()) {
            labels.add("Veri Yok");
            series.add(100.0);
        }

        ApexCharts chart = ApexChartsBuilder.get()
                .withChart(ChartBuilder.get()
                        .withType(Type.DONUT)
                        .withBackground("transparent")
                        .build())
                .withSeries(series.toArray(new Double[0]))
                .withLabels(labels.toArray(new String[0]))
                .withTooltip(TooltipBuilder.get()
                        .withY(YBuilder.get()
                                .withFormatter("function(value) { return new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value); }")
                                .build())
                        .build())
                .build();
        return chart;
    }

    private ApexCharts createCategoryPieChart() {
        YearMonth now = YearMonth.now();
        List<InstallmentEntry> monthInstallments = cachedMonthInstallments;

        Map<String, BigDecimal> categoryTotals = new java.util.HashMap<>();
        for (InstallmentEntry entry : monthInstallments) {
            String category = entry.getExpense().getCategory();
            if (category == null || category.trim().isEmpty()) {
                category = "Diger";
            }
            category = category.trim();
            categoryTotals.put(category, categoryTotals.getOrDefault(category, BigDecimal.ZERO).add(entry.getAmount()));
        }

        List<String> labels = new ArrayList<>();
        List<Double> series = new ArrayList<>();

        for (Map.Entry<String, BigDecimal> entry : categoryTotals.entrySet()) {
            labels.add(entry.getKey());
            series.add(entry.getValue().doubleValue());
        }

        if (categoryTotals.isEmpty()) {
            labels.add("Veri Yok");
            series.add(100.0);
        }

        ApexCharts chart = ApexChartsBuilder.get()
                .withChart(ChartBuilder.get()
                        .withType(Type.DONUT)
                        .withBackground("transparent")
                        .build())
                .withSeries(series.toArray(new Double[0]))
                .withLabels(labels.toArray(new String[0]))
                .withTooltip(TooltipBuilder.get()
                        .withY(YBuilder.get()
                                .withFormatter("function(value) { return new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value); }")
                                .build())
                        .build())
                .build();
        return chart;
    }

    private ApexCharts createCashFlowChart() {
        List<String> categories = new ArrayList<>();
        List<Double> inflowData = new ArrayList<>();
        List<Double> outflowData = new ArrayList<>();

        YearMonth current = YearMonth.now();
        List<Cheque> cheques = chequeService.findAll();

        for (int i = 0; i < 6; i++) {
            YearMonth ym = current.plusMonths(i);
            String label = ym.getMonth().getDisplayName(TextStyle.SHORT, Locale.of("tr")) + " " + ym.getYear();
            categories.add(label);

            // Giriş Çekleri: Maturing in ym
            BigDecimal monthlyInflow = cheques.stream()
                    .filter(c -> c.getType() == ChequeType.ENTERING)
                    .filter(c -> {
                        LocalDate d = c.getMaturityDate();
                        return d.getYear() == ym.getYear() && d.getMonthValue() == ym.getMonthValue();
                    })
                    .map(Cheque::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            inflowData.add(monthlyInflow.doubleValue());

            // Çıkış Çekleri + Kart Taksitleri: Maturing/Due in ym
            BigDecimal monthlyOutgoingCheques = cheques.stream()
                    .filter(c -> c.getType() == ChequeType.EXITING)
                    .filter(c -> {
                        LocalDate d = c.getMaturityDate();
                        return d.getYear() == ym.getYear() && d.getMonthValue() == ym.getMonthValue();
                    })
                    .map(Cheque::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal monthlyInstallments = expenseService.getTotalForMonth(ym.getYear(), ym.getMonthValue());
            
            BigDecimal monthlyOutflow = monthlyOutgoingCheques.add(monthlyInstallments);
            outflowData.add(monthlyOutflow.doubleValue());
        }

        ApexCharts chart = ApexChartsBuilder.get()
                .withChart(ChartBuilder.get()
                        .withType(Type.BAR)
                        .withBackground("transparent")
                        .build())
                .withDataLabels(DataLabelsBuilder.get()
                        .withEnabled(false)
                        .build())
                .withPlotOptions(PlotOptionsBuilder.get()
                        .withBar(BarBuilder.get()
                                .withHorizontal(false)
                                .withColumnWidth("55%")
                                .build())
                        .build())
                .withSeries(
                        new Series<>("Nakit Girişi (Çekler)", inflowData.toArray(new Double[0])),
                        new Series<>("Nakit Çıkışı (Kartlar & Çekler)", outflowData.toArray(new Double[0]))
                )
                .withXaxis(XAxisBuilder.get()
                        .withCategories(categories)
                        .build())
                .withColors("#4CAF50", "#F44336") // Green & Red
                .withTooltip(TooltipBuilder.get()
                        .withY(YBuilder.get()
                                .withFormatter("function(value) { return new Intl.NumberFormat('tr-TR', { style: 'currency', currency: 'TRY', minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value); }")
                                .build())
                        .build())
                .build();
        return chart;
    }

    private static class CardSchedule {
        private final Card card;
        private final LocalDate statementDate;
        private final LocalDate originalDueDate;
        private final LocalDate actualDueDate;
        private final long daysRemaining;

        public CardSchedule(Card card, LocalDate statementDate, LocalDate originalDueDate, LocalDate actualDueDate, long daysRemaining) {
            this.card = card;
            this.statementDate = statementDate;
            this.originalDueDate = originalDueDate;
            this.actualDueDate = actualDueDate;
            this.daysRemaining = daysRemaining;
        }
    }

    private Div createRemindersPanel() {
        Div container = new Div();
        container.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "12px")
                .set("padding", "1.5em")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.1)")
                .set("width", "100%")
                .set("box-sizing", "border-box");

        H3 titleEl = new H3("Geciken Ödemeler");
        titleEl.getStyle()
                .set("margin", "0")
                .set("font-size", "1.2em")
                .set("font-weight", "700")
                .set("color", "var(--lumo-error-color)")
                .set("padding", "0.4em 0")
                .set("border-bottom", "3px solid var(--lumo-error-color)")
                .set("display", "inline-block")
                .set("margin-bottom", "1em");

        Span countBadge = new Span("0");
        countBadge.getStyle()
                .set("background", "var(--lumo-error-color)")
                .set("color", "white")
                .set("padding", "2px 10px")
                .set("border-radius", "12px")
                .set("font-size", "0.85em")
                .set("font-weight", "700")
                .set("margin-left", "0.5em");
        countBadge.setVisible(false);

        VerticalLayout list = new VerticalLayout();
        list.setPadding(false);
        list.setSpacing(true);

        Scroller scroller = new Scroller(list);
        scroller.setScrollDirection(Scroller.ScrollDirection.VERTICAL);
        scroller.getStyle()
                .set("max-height", "250px")
                .set("padding-right", "8px");

        List<InstallmentEntry> overdue = reminderService.getOverdueInstallments();
        List<InstallmentEntry> upcoming = reminderService.getUpcomingInstallments(7);
        List<Cheque> overdueCheques = reminderService.getOverdueCheques();
        List<Cheque> upcomingCheques = reminderService.getUpcomingCheques(7);

        int count = 0;
        int isTodayCount = 0;
        
        // Geciken taksitler
        for (InstallmentEntry entry : overdue) {
            list.add(createReminderItemRow(entry.getExpense().getCard().getName() + " - " + entry.getExpense().getDescription(), 
                    entry.getAmount(), reminderService.calculateDueDate(entry), true, "Taksit"));
            count++;
        }
        
        // Geciken çekler
        for (Cheque c : overdueCheques) {
            list.add(createReminderItemRow(c.getBank() + " (Çek No: " + c.getChequeNumber() + ")", 
                    c.getAmount(), c.getMaturityDate(), true, "Çek"));
            count++;
        }
        
        // Yaklaşan taksitler
        for (InstallmentEntry entry : upcoming) {
            LocalDate due = reminderService.calculateDueDate(entry);
            if (due.isEqual(LocalDate.now())) isTodayCount++;
            list.add(createReminderItemRow(entry.getExpense().getCard().getName() + " - " + entry.getExpense().getDescription(), 
                    entry.getAmount(), due, false, "Taksit"));
            count++;
        }

        // Yaklaşan çekler
        for (Cheque c : upcomingCheques) {
            if (c.getMaturityDate().isEqual(LocalDate.now())) isTodayCount++;
            list.add(createReminderItemRow(c.getBank() + " (Çek No: " + c.getChequeNumber() + ")", 
                    c.getAmount(), c.getMaturityDate(), false, "Çek"));
            count++;
        }

        if (count == 0) {
            Span noReminders = new Span("Herhangi bir geciken ödemeniz bulunmuyor.");
            noReminders.getStyle().set("color", "var(--lumo-success-text-color)").set("font-weight", "600");
            list.add(noReminders);
        }

        Button viewAllBtn = new Button("Tümünü Gör", new Icon(VaadinIcon.ARROW_RIGHT), 
                e -> getUI().ifPresent(ui -> ui.navigate(ReminderView.class)));
        viewAllBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        viewAllBtn.getStyle().set("margin-top", "1em").set("transition", "none");

        if (count > 0) {
            countBadge.setText(String.valueOf(count));
            countBadge.setVisible(true);
            if (overdue.size() + overdueCheques.size() > 0) {
                countBadge.getStyle().set("background", "var(--lumo-error-color)");
            } else if (isTodayCount > 0) {
                countBadge.getStyle().set("background", "#F57C00");
            }
        }

        container.add(titleEl, countBadge, scroller, viewAllBtn);
        return container;
    }

    private HorizontalLayout createReminderItemRow(String title, BigDecimal amount, LocalDate dueDate, boolean isOverdue, String type) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.getStyle()
                .set("padding", "0.6em 0.8em")
                .set("margin-bottom", "0.3em")
                .set("border-radius", "8px");

        LocalDate today = LocalDate.now();
        long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, dueDate);
        boolean isToday = dueDate.isEqual(today);
        boolean isUrgent = isOverdue || isToday || (daysRemaining > 0 && daysRemaining <= 3);

        String icon;
        if (isOverdue) {
            icon = "\uD83D\uDD34";
            row.getStyle()
                    .set("background-color", "#FFEBEE")
                    .set("border", "2px solid #EF9A9A")
                    .set("font-weight", "700")
                    .set("animation", "pulse 1.5s infinite");
        } else if (isToday) {
            icon = "\uD83D\uDFE1";
            row.getStyle()
                    .set("background-color", "#FFF8E1")
                    .set("border", "2px solid #FFE082")
                    .set("font-weight", "700");
        } else if (daysRemaining <= 3) {
            icon = "\uD83D\uDFE0";
            row.getStyle()
                    .set("background-color", "#FFF3E0")
                    .set("border", "2px solid #FFCC80")
                    .set("font-weight", "600");
        } else {
            icon = "\uD83D\uDD35";
            row.getStyle()
                    .set("background-color", "var(--lumo-contrast-5pct)")
                    .set("border-bottom", "1px solid var(--lumo-contrast-10pct)");
        }

        Span iconSpan = new Span(icon);
        iconSpan.getStyle().set("font-size", "1.3em").set("flex-shrink", "0").set("margin-right", "0.4em");

        Span typeBadge = new Span(type);
        typeBadge.getStyle()
                .set("font-size", "0.7em")
                .set("padding", "0.2em 0.5em")
                .set("border-radius", "4px")
                .set("flex-shrink", "0")
                .set("margin-right", "0.6em")
                .set("font-weight", "600");
        if ("Çek".equals(type)) {
            typeBadge.getStyle().set("background-color", "#C8E6C9").set("color", "#2E7D32");
        } else {
            typeBadge.getStyle().set("background-color", "#BBDEFB").set("color", "#1565C0");
        }

        Span titleSpan = new Span(title);
        titleSpan.getStyle()
                .set("font-weight", isUrgent ? "700" : "500")
                .set("font-size", isUrgent ? "1em" : "0.9em")
                .set("text-overflow", "ellipsis")
                .set("overflow", "hidden")
                .set("white-space", "nowrap")
                .set("min-width", "350px");

        Span amountSpan = new Span(currencyFormat.format(amount));
        amountSpan.getStyle()
                .set("font-weight", "700")
                .set("flex-shrink", "0")
                .set("margin-left", "0.5em")
                .set("font-size", isUrgent ? "1.05em" : "0.95em")
                .set("color", isOverdue ? "var(--lumo-error-color)" : "var(--lumo-body-text-color)");

        Span dateSpan = new Span(dueDate.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));
        dateSpan.getStyle()
                .set("font-size", "0.8em")
                .set("color", isOverdue ? "var(--lumo-error-color)" : "var(--lumo-secondary-text-color)")
                .set("flex-shrink", "0")
                .set("margin-left", "0.5em")
                .set("font-weight", isOverdue ? "700" : "400");

        String badgeText = isOverdue ? "GECİKTİ" : (isToday ? "BUGÜN!" : (daysRemaining <= 3 ? daysRemaining + " gün" : "Yakında"));
        String badgeTheme = isOverdue ? "error" : (isToday ? "warning" : "contrast");
        
        Span statusBadge = new Span(badgeText);
        statusBadge.getElement().getThemeList().add("badge " + badgeTheme);
        statusBadge.getStyle()
                .set("flex-shrink", "0")
                .set("margin-left", "0.5em")
                .set("font-size", isUrgent ? "0.85em" : "0.75em")
                .set("font-weight", "700");

        row.add(iconSpan, typeBadge, titleSpan, amountSpan, dateSpan, statusBadge);
        row.expand(titleSpan);
        return row;
    }
}
