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
import com.raspel.cardtracker.domain.backup.BackupRestoreService;
import com.raspel.cardtracker.ui.utils.FormatUtils;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
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
    private final BackupRestoreService backupService;

    public ProfileView(UserService userService, CardService cardService,
                       ExpenseService expenseService, ChequeService chequeService,
                       AppSettingsService appSettingsService,
                       BackupRestoreService backupService) {
        this.userService = userService;
        this.cardService = cardService;
        this.expenseService = expenseService;
        this.chequeService = chequeService;
        this.appSettingsService = appSettingsService;
        this.backupService = backupService;

        setSizeFull();
        setPadding(true);
        setSpacing(false);
        getStyle().set("padding-top", "48px").set("gap", "20px");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "";
        Optional<AppUser> userOpt = userService.findByUsername(username);

        if (userOpt.isEmpty()) {
            add(new Span("Kullanici bulunamadi."));
            return;
        }

        AppUser user = userOpt.get();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.of("tr"));
        long activeCards = cardService.findAllActive().size();
        long totalExpenses = expenseService.countByCreatedBy(username);
        String createdDate = user.getCreatedAt() != null ? user.getCreatedAt().format(dtf) : "-";
        String lastLogin = user.getLastLoginAt() != null ? user.getLastLoginAt().format(dtf) : "İlk giriş";

        Div grid = new Div();
        grid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr")
                .set("gap", "16px")
                .set("width", "100%");

        // 1. SOL ÜST: Profil Bilgileri
        Div profileCard = buildCard("Profil Bilgileri");
        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        form.addClassName("profile-form");

        TextField usernameField = new TextField("Kullanıcı Adı");
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

        Button saveProfileBtn = new Button("Kaydet", new Icon(VaadinIcon.CHECK), e -> {
            user.setFullName(fullNameField.getValue().trim());
            userService.save(user);
            Notification.show("Profil güncellendi.", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
        });
        saveProfileBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveProfileBtn.getStyle().set("align-self", "flex-end");

        form.add(usernameField, fullNameField, roleField, saveProfileBtn);
        profileCard.add(form);
        grid.add(profileCard);

        // 2. SAĞ ÜST: Hesap Özeti
        Div statsCard = buildCard("Hesap Özeti");
        Div statsGrid = new Div();
        statsGrid.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr 1fr")
                .set("gap", "8px");
        statsGrid.add(
            buildStatItem("Aktif Kart", String.valueOf(activeCards), "#2196F3"),
            buildStatItem("Toplam Harcama", totalExpenses + " işlem", "#4CAF50"),
            buildStatItem("Üyelik", createdDate, "#FF9800"),
            buildStatItem("Son Giriş", lastLogin, "#9C27B0")
        );
        statsCard.add(statsGrid);
        grid.add(statsCard);

        // 3. ORTA ÜST: Son İşlemler
        Div recentCard = buildCard("Son İşlemler");
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
            recentList.add(createRecentItem("Harcama", date, desc, FormatUtils.formatNumber(e.getTotalAmount()) + " ₺", "#4CAF50"));
        }
        for (Cheque c : recentCheques) {
            String date = c.getMaturityDate() != null ? c.getMaturityDate().format(DateTimeFormatter.ofPattern("dd.MM")) : "-";
            String desc = c.getChequeNumber() + " - " + (c.getBank() != null ? c.getBank() : "");
            recentList.add(createRecentItem("Çek", date, desc, FormatUtils.formatNumber(c.getAmount()) + " ₺", "#2196F3"));
        }
        if (recentExpenses.isEmpty() && recentCheques.isEmpty()) {
            recentList.add(new Span("Henüz işlem bulunmuyor."));
        }
        recentCard.add(recentList);
        grid.add(recentCard);

        // SOL ALT: Firma Adı (admin)
        if (isAdmin) {
            Div companyCard = buildCard("Firma Adı Değiştir");
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

            HorizontalLayout companyRow = new HorizontalLayout(companyField, saveCompanyBtn);
            companyRow.setWidthFull();
            companyRow.setAlignItems(FlexComponent.Alignment.END);
            companyRow.expand(companyField);
            companyCard.add(companyRow);
            grid.add(companyCard);

            // Yedekleme kartı (admin only)
            Div backupCard = buildCard("Veritabanı Yedekleme");

            HorizontalLayout backupMain = new HorizontalLayout();
            backupMain.setWidthFull();
            backupMain.setSpacing(true);
            backupMain.getStyle().set("gap", "20px");

            // SOL: Yedek Al
            Div backupLeft = new Div();
            backupLeft.getStyle()
                    .set("flex", "1")
                    .set("padding", "16px")
                    .set("border-radius", "10px")
                    .set("background", "var(--lumo-contrast-5pct)")
                    .set("text-align", "center");

            Span backupIcon = new Span("\uD83D\uDCBE");
            backupIcon.getStyle().set("font-size", "24px").set("display", "block").set("margin-bottom", "8px");

            Span backupLabel = new Span("Yedek Al");
            backupLabel.getStyle().set("font-weight", "600").set("font-size", "0.85em").set("display", "block");

            Span backupDesc = new Span("Tüm veritabanını .sql dosyası olarak bilgisayara indirir.");
            backupDesc.getStyle().set("font-size", "0.7em").set("color", "var(--lumo-secondary-text-color)").set("display", "block").set("margin-top", "4px");

            Button backupBtn = new Button("Yedek Al", new Icon(VaadinIcon.DOWNLOAD), ev -> {
                try {
                    java.io.File f = backupService.createBackup();
                    com.vaadin.flow.server.StreamResource res = new com.vaadin.flow.server.StreamResource(
                        f.getName(), () -> {
                            try { return new java.io.FileInputStream(f); } catch (Exception ex) { return null; }
                        });
                    res.setContentType("application/sql");
                    com.vaadin.flow.component.html.Anchor anchor = new com.vaadin.flow.component.html.Anchor(res, "");
                    anchor.getElement().setAttribute("download", f.getName());
                    anchor.getElement().getStyle().set("display", "none");
                    add(anchor);
                    anchor.getElement().executeJs("this.click()");
                    getUI().ifPresent(ui -> ui.getPage().executeJs("setTimeout(function(){arguments[0].remove()},1000)", anchor.getElement()));
                } catch (Exception ex) {
                    Notification.show("Yedek alınamadı: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            });
            backupBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            backupBtn.getStyle().set("margin-top", "12px");

            backupLeft.add(backupIcon, backupLabel, backupDesc, backupBtn);

            // SAĞ: Geri Yükle
            Div backupRight = new Div();
            backupRight.getStyle()
                    .set("flex", "1")
                    .set("padding", "16px")
                    .set("border-radius", "10px")
                    .set("background", "var(--lumo-contrast-5pct)")
                    .set("text-align", "center");

            Span restoreIcon = new Span("\uD83D\uDCC2");
            restoreIcon.getStyle().set("font-size", "24px").set("display", "block").set("margin-bottom", "8px");

            Span restoreLabel = new Span("Geri Yükle");
            restoreLabel.getStyle().set("font-weight", "600").set("font-size", "0.85em").set("display", "block");

            Span restoreDesc = new Span("Daha önce aldığınız .sql yedek dosyasını seçerek veritabanını eski haline getirir.");
            restoreDesc.getStyle().set("font-size", "0.7em").set("color", "var(--lumo-secondary-text-color)").set("display", "block").set("margin-top", "4px");

            com.vaadin.flow.component.upload.Upload restoreUpload = new com.vaadin.flow.component.upload.Upload(
                new com.vaadin.flow.component.upload.receivers.MemoryBuffer());
            restoreUpload.setAcceptedFileTypes(".sql");
            restoreUpload.setMaxFiles(1);
            restoreUpload.setDropLabel(new Span("SQL dosyası seç"));
            restoreUpload.getStyle().set("margin-top", "12px");

            restoreUpload.addSucceededListener(ev -> {
                com.vaadin.flow.component.dialog.Dialog confirm = new com.vaadin.flow.component.dialog.Dialog();
                confirm.setHeaderTitle("\u26A0\uFE0F Geri Yükleme Onayı");
                Span warn = new Span("Veritabanı geri yüklenecek. Mevcut tüm veriler silinip yedekteki veriler gelecek. Bu işlem geri alınamaz!");
                warn.getStyle().set("color", "var(--lumo-error-color)").set("font-weight", "600");
                confirm.add(warn);
                Button yesBtn = new Button("Evet, Geri Yükle", e -> {
                    try {
                        backupService.restoreFromFile(((com.vaadin.flow.component.upload.receivers.MemoryBuffer)restoreUpload.getReceiver()).getInputStream());
                        Notification.show("Veritabanı başarıyla geri yüklendi.", 4000, Notification.Position.MIDDLE)
                                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    } catch (Exception ex) {
                        Notification.show("Geri yükleme başarısız: " + ex.getMessage(), 6000, Notification.Position.MIDDLE)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    }
                    confirm.close();
                });
                yesBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
                Button noBtn = new Button("Vazgeç", ev2 -> confirm.close());
                confirm.getFooter().add(noBtn, yesBtn);
                confirm.open();
            });

            backupRight.add(restoreIcon, restoreLabel, restoreDesc, restoreUpload);

            backupMain.add(backupLeft, backupRight);
            backupCard.add(backupMain);
            grid.add(backupCard);
        }

        // SAĞ ALT: Aylık Harcama (grafik)
        Div chartCard = buildCard("Aylık Harcama Projeksiyonu");
        Map<String, BigDecimal> projection = expenseService.getMonthlyProjection(6);

        YearMonth current = YearMonth.now().minusMonths(1);
        String[] monthNames = new String[6];
        for (int i = 0; i < 6; i++) {
            monthNames[i] = current.plusMonths(i).getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.of("tr"));
        }

        ApexCharts miniChart = ApexChartsBuilder.get()
                .withChart(ChartBuilder.get().withType(Type.BAR).withHeight("200px").build())
                .withXaxis(XAxisBuilder.get().withCategories(java.util.Arrays.asList(monthNames)).build())
                .withSeries(new Series<>("TL", projection.values().stream().map(BigDecimal::doubleValue).toArray(Double[]::new)))
                .withColors("#2196F3")
                .build();
        miniChart.setWidth("100%");
        chartCard.add(miniChart);
        grid.add(chartCard);

        add(grid);
    }

    private Div buildCard(String title) {
        Div card = new Div();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "12px")
                .set("padding", "20px")
                .set("box-shadow", "0 1px 6px rgba(0,0,0,0.08)")
                .set("border", "1px solid var(--lumo-contrast-10pct)");
        H4 cardTitle = new H4(title);
        cardTitle.getStyle()
                .set("margin", "0 0 16px 0")
                .set("font-size", "1em")
                .set("font-weight", "700")
                .set("color", "#6b7280");
        card.add(cardTitle);
        return card;
    }

    private Div buildStatItem(String label, String value, String color) {
        Div item = new Div();
        item.getStyle()
                .set("padding", "12px")
                .set("border-radius", "8px")
                .set("background", "var(--lumo-contrast-5pct)")
                .set("border-left", "3px solid " + color);
        Span val = new Span(value);
        val.getStyle()
                .set("font-size", "0.9em").set("font-weight", "700").set("color", "var(--lumo-body-text-color)")
                .set("display", "block");
        Span lbl = new Span(label);
        lbl.getStyle()
                .set("font-size", "0.7em").set("color", "#6b7280")
                .set("display", "block").set("margin-top", "2px");
        item.add(val, lbl);
        return item;
    }

    private Div createMiniStat(String label, String value, String color) {
        return buildStatItem(label, value, color);
    }

    private Div createCard(String title) {
        return buildCard(title);
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
