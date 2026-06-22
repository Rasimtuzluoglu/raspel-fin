package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.github.appreciated.apexcharts.ApexCharts;
import com.github.appreciated.apexcharts.ApexChartsBuilder;
import com.github.appreciated.apexcharts.config.builder.*;
import com.github.appreciated.apexcharts.config.chart.Type;
import com.github.appreciated.apexcharts.helper.Series;
import com.raspel.cardtracker.domain.card.CardService;
import com.raspel.cardtracker.domain.expense.Expense;
import com.raspel.cardtracker.domain.expense.ExpenseService;
import com.raspel.cardtracker.domain.cheque.Cheque;
import com.raspel.cardtracker.domain.cheque.ChequeService;
import com.raspel.cardtracker.domain.user.AppUser;
import com.raspel.cardtracker.domain.user.UserService;
import com.raspel.cardtracker.domain.settings.AppSettingsService;
import com.raspel.cardtracker.ui.utils.FormatUtils;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Route(value = "profile", layout = MainLayout.class)
@PageTitle("Profilim")
@PermitAll
public class ProfileView extends VerticalLayout {

    private final UserService userService;
    private final CardService cardService;
    private final ExpenseService expenseService;
    private final ChequeService chequeService;
    private final AppSettingsService appSettingsService;

    public ProfileView(UserService userService, CardService cardService,
                       ExpenseService expenseService, ChequeService chequeService,
                       AppSettingsService appSettingsService) {
        this.userService = userService;
        this.cardService = cardService;
        this.expenseService = expenseService;
        this.chequeService = chequeService;
        this.appSettingsService = appSettingsService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);
        getStyle().set("padding-top", "48px");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "";
        Optional<AppUser> userOpt = userService.findByUsername(username);

        if (userOpt.isEmpty()) {
            add(new Span("Kullanici bulunamadi."));
            return;
        }

        AppUser user = userOpt.get();

        H3 title = new H3("Profilim");
        title.getStyle().set("margin-top", "0");
        add(title);

        // Ust satir: form + istatistikler yan yana
        HorizontalLayout topRow = new HorizontalLayout();
        topRow.setWidthFull();
        topRow.setSpacing(true);
        topRow.getStyle().set("flex-wrap", "wrap");

        Div formCard = createCard("Profil Bilgileri");
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        TextField usernameField = new TextField("Kullanici Adi");
        usernameField.setValue(user.getUsername());
        usernameField.setReadOnly(true);
        usernameField.setWidthFull();

        TextField fullNameField = new TextField("Ad Soyad");
        fullNameField.setValue(user.getFullName() != null ? user.getFullName() : "");
        fullNameField.setWidthFull();

        TextField roleField = new TextField("Rol");
        roleField.setValue(user.getRole());
        roleField.setReadOnly(true);
        roleField.setWidthFull();

        form.add(usernameField, fullNameField, roleField);

        Button saveBtn = new Button("Kaydet", new Icon(VaadinIcon.CHECK), e -> {
            user.setFullName(fullNameField.getValue().trim());
            userService.save(user);
            Notification.show("Profil guncellendi.", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        HorizontalLayout saveRow = new HorizontalLayout();
        saveRow.setWidthFull();
        saveRow.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        saveRow.add(saveBtn);

        formCard.add(form, saveRow);

        // Istatistikler karti
        Div statsCard = createCard("Hesap Ozeti");
        long activeCards = cardService.findAllActive().size();
        long totalExpenses = expenseService.countByCreatedBy(username);
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.of("tr"));
        String createdDate = user.getCreatedAt() != null ? user.getCreatedAt().format(dtf) : "-";
        String lastLogin = user.getLastLoginAt() != null ? user.getLastLoginAt().format(dtf) : "Ilk giris";

        HorizontalLayout statsRow1 = new HorizontalLayout();
        statsRow1.setWidthFull();
        statsRow1.setSpacing(true);
        statsRow1.add(createMiniStat("Aktif Kartlar", String.valueOf(activeCards), "#2196F3"),
                       createMiniStat("Toplam Harcama", String.valueOf(totalExpenses), "#4CAF50"));

        HorizontalLayout statsRow2 = new HorizontalLayout();
        statsRow2.setWidthFull();
        statsRow2.setSpacing(true);
        statsRow2.getStyle().set("margin-top", "0.8em");
        statsRow2.add(createMiniStat("Uyelik", createdDate, "#FF9800"),
                       createMiniStat("Son Giris", lastLogin, "#9C27B0"));

        statsCard.add(statsRow1, statsRow2);

        formCard.getStyle().set("flex", "1").set("min-width", "320px");
        statsCard.getStyle().set("flex", "1").set("min-width", "320px");
        topRow.add(formCard, statsCard);
        add(topRow);

        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        if (isAdmin) {
            Div companyCard = createCard("Firma Adı Değiştir");
            HorizontalLayout companyRow = new HorizontalLayout();
            companyRow.setWidthFull();
            companyRow.setAlignItems(FlexComponent.Alignment.END);
            companyRow.setSpacing(true);

            TextField companyField = new TextField("Firma Adı");
            companyField.setValue(appSettingsService.getCompanyName());
            companyField.setWidthFull();

            Button saveCompanyBtn = new Button("Kaydet", new Icon(VaadinIcon.CHECK), e -> {
                String name = companyField.getValue().trim();
                if (name.isEmpty()) {
                    Notification.show("Firma adı boş olamaz", 3000, Notification.Position.BOTTOM_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
                appSettingsService.setCompanyName(name);
                Notification.show("Firma adı güncellendi", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            });
            saveCompanyBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            saveCompanyBtn.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

            companyRow.add(companyField, saveCompanyBtn);
            companyRow.expand(companyField);
            companyCard.add(companyRow);
            companyCard.getStyle().set("margin-bottom", "1em");
            add(companyCard);
        }

        // Alt satir: son islemler + grafik yan yana
        HorizontalLayout bottomRow = new HorizontalLayout();
        bottomRow.setWidthFull();
        bottomRow.setSpacing(true);
        bottomRow.getStyle().set("flex-wrap", "wrap");

        Div recentCard = createCard("Son Islemler");
        VerticalLayout recentList = new VerticalLayout();
        recentList.setPadding(false);
        recentList.setSpacing(false);

        List<Expense> recentExpenses = expenseService.findRecentByCreatedBy(username, 3);
        List<Cheque> recentCheques = chequeService.findAll().stream()
                .sorted(Comparator.comparing(c -> c.getMaturityDate(), Comparator.reverseOrder()))
                .limit(2).collect(Collectors.toList());

        for (Expense e : recentExpenses) {
            String date = e.getExpenseDate() != null ? e.getExpenseDate().format(DateTimeFormatter.ofPattern("dd.MM")) : "-";
            String desc = e.getDescription() != null ? e.getDescription() : "";
            if (desc.length() > 30) desc = desc.substring(0, 30) + "...";
            recentList.add(createRecentItem("Harcama", date, desc, FormatUtils.formatNumber(e.getTotalAmount()) + " TL", "#4CAF50"));
        }
        for (Cheque c : recentCheques) {
            String date = c.getMaturityDate() != null ? c.getMaturityDate().format(DateTimeFormatter.ofPattern("dd.MM")) : "-";
            String desc = c.getChequeNumber() + " - " + (c.getBank() != null ? c.getBank() : "");
            recentList.add(createRecentItem("Cek", date, desc, FormatUtils.formatNumber(c.getAmount()) + " TL", "#2196F3"));
        }
        if (recentExpenses.isEmpty() && recentCheques.isEmpty()) {
            recentList.add(new Span("Henuz islem bulunmuyor."));
        }
        recentCard.add(recentList);

        Div chartCard = createCard("Aylik Harcama");
        Map<String, BigDecimal> projection = expenseService.getMonthlyProjection(4);
        java.util.List<String> categories = new ArrayList<>();
        java.util.List<Double> data = new ArrayList<>();
        YearMonth current = YearMonth.now().minusMonths(1);
        for (int i = 0; i < 4; i++) {
            YearMonth ym = current.plusMonths(i);
            categories.add(ym.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.of("tr")));
            String key = ym.getYear() + "-" + String.format("%02d", ym.getMonthValue());
            BigDecimal val = projection.getOrDefault(key, BigDecimal.ZERO);
            data.add(val.doubleValue());
        }
        ApexCharts miniChart = ApexChartsBuilder.get()
                .withChart(ChartBuilder.get().withType(Type.BAR).withHeight("180px").build())
                .withXaxis(XAxisBuilder.get().withCategories(categories).build())
                .withSeries(new Series<>("Harcama", data.toArray(new Double[0])))
                .withColors("#2196F3")
                .build();
        miniChart.setWidth("100%");
        chartCard.add(miniChart);

        recentCard.getStyle().set("flex", "1").set("min-width", "320px");
        chartCard.getStyle().set("flex", "1").set("min-width", "320px");
        bottomRow.add(recentCard, chartCard);
        add(bottomRow);
    }

    private Div createCard(String title) {
        Div card = new Div();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "16px")
                .set("padding", "1.5em")
                .set("box-shadow", "0 2px 12px rgba(0,0,0,0.08)")
                .set("margin-bottom", "1em");

        H4 cardTitle = new H4(title);
        cardTitle.getStyle().set("margin-top", "0").set("margin-bottom", "1em");
        card.add(cardTitle);
        return card;
    }

    private Div createMiniStat(String label, String value, String color) {
        Div mini = new Div();
        mini.getStyle()
                .set("flex", "1")
                .set("text-align", "center")
                .set("padding", "0.8em")
                .set("border-radius", "8px")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-left", "3px solid " + color);

        Span val = new Span(value);
        val.getStyle()
                .set("font-size", "1em").set("font-weight", "700").set("color", color)
                .set("display", "block").set("overflow", "hidden").set("text-overflow", "ellipsis");

        Span lbl = new Span(label);
        lbl.getStyle()
                .set("font-size", "0.7em").set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block").set("margin-top", "0.2em");

        mini.add(val, lbl);
        return mini;
    }

    private HorizontalLayout createRecentItem(String type, String date, String desc, String amount, String color) {
        HorizontalLayout row = new HorizontalLayout();
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.getStyle().set("padding", "0.4em 0").set("border-bottom", "1px solid var(--lumo-contrast-10pct)");

        Span typeBadge = new Span(type);
        typeBadge.getStyle().set("background", color).set("color", "#fff")
                .set("padding", "2px 6px").set("border-radius", "4px")
                .set("font-size", "0.65em").set("font-weight", "600").set("flex-shrink", "0");

        Span dateSpan = new Span(date);
        dateSpan.getStyle().set("font-size", "0.75em").set("color", "var(--lumo-secondary-text-color)")
                .set("margin-left", "0.5em").set("flex-shrink", "0");

        Span descSpan = new Span(desc);
        descSpan.getStyle().set("font-size", "0.8em").set("min-width", "0").set("margin-left", "0.5em");

        Span amountSpan = new Span(amount);
        amountSpan.getStyle().set("font-weight", "600").set("font-size", "0.8em")
                .set("margin-left", "auto").set("flex-shrink", "0");

        row.add(typeBadge, dateSpan, descSpan, amountSpan);
        row.expand(descSpan);
        return row;
    }
}
