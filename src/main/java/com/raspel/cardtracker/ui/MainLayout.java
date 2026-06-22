package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Footer;
import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Header;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.theme.lumo.LumoUtility;
import com.raspel.cardtracker.domain.budget.DepartmentBudget;
import com.raspel.cardtracker.domain.budget.DepartmentBudgetService;
import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.domain.card.CardService;
import com.raspel.cardtracker.domain.expense.ExpenseService;
import com.raspel.cardtracker.domain.reminder.PaymentReminderService;
import com.raspel.cardtracker.domain.user.DashboardConfig;
import com.raspel.cardtracker.domain.user.UserService;
import com.raspel.cardtracker.domain.note.NoteService;
import com.raspel.cardtracker.domain.note.Note;
import com.raspel.cardtracker.domain.settings.AppSettingsService;
import java.time.LocalDate;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@PermitAll
public class MainLayout extends AppLayout {

    private final CardService cardService;
    private final ExpenseService expenseService;
    private final PaymentReminderService reminderService;
    private final UserService userService;
    private final NoteService noteService;
    private final DepartmentBudgetService departmentBudgetService;
    private final AppSettingsService appSettingsService;
    private H2 viewTitle;
    private VerticalLayout cardDebtList;

    public MainLayout(CardService cardService, ExpenseService expenseService, PaymentReminderService reminderService, UserService userService, NoteService noteService, DepartmentBudgetService departmentBudgetService, AppSettingsService appSettingsService) {
        this.cardService = cardService;
        this.expenseService = expenseService;
        this.reminderService = reminderService;
        this.userService = userService;
        this.noteService = noteService;
        this.departmentBudgetService = departmentBudgetService;
        this.appSettingsService = appSettingsService;
        
        setPrimarySection(Section.DRAWER);
        addDrawerContent();
        addHeaderContent();
    }

    private void injectKeyboardShortcuts() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        String adminJs = isAdmin
            ? "if(k==='1'||k==='2'){var now=Date.now();if(last[k]&&(now-last[k])<500){window.location.href=(k==='1'?'users':'audit-log');last[k]=0;}else{last[k]=now;}return;}"
            : "";
        String quickJs = 
            "if(k==='9'||k==='0'){" +
            "var now=Date.now();" +
            "if(last[k]&&(now-last[k])<500){" +
            "var dest=(k==='9'?'cheques':'expenses');" +
            "sessionStorage.setItem('fabOpenDialog',k==='9'?'cheque':'expense');" +
            "window.location.href=dest;last[k]=0;}" +
            "else{last[k]=now;}return;}" ;

        getElement().executeJs(
            "if(window._shortcutInstalled) return; window._shortcutInstalled=true;" +
            "var routes={a:'',k:'cards',h:'expenses',f:'expenses','\\u00E7':'cheques',c:'contacts'," +
            "b:'budgets',n:'notes',t:'reminders',r:'reports',e:'employee-tasks'};" +
            "var last={};" +
            "document.addEventListener('keydown',function(e){" +
            "if(e.target.tagName==='INPUT'||e.target.tagName==='TEXTAREA'||e.ctrlKey||e.altKey||e.metaKey)return;" +
            "var k=e.key.toLowerCase();" +
            adminJs +
            quickJs +
            "if(k==='?'){e.preventDefault();document.getElementById('shortcuts-help-trigger').click();return;}" +
            "if(routes.hasOwnProperty(k)){" +
            "window.location.href=(routes[k]?routes[k]:'');}" +
            "});"
        );
    }

    private void addHeaderContent() {
        DrawerToggle toggle = new DrawerToggle();
        toggle.setAriaLabel("Menü");

        viewTitle = new H2();
        viewTitle.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);

        // Kullanıcı bilgisi ve çıkış butonu
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "";

        Span userInfo = new Span("👤 " + username);
        userInfo.addClassName("header-user-info");
        userInfo.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "0.9em");

        Button logoutBtn = new Button("Çıkış", new Icon(VaadinIcon.SIGN_OUT), e -> {
            getUI().ifPresent(ui -> {
                SecurityContextHolder.clearContext();
                ui.getSession().close();
                ui.getPage().setLocation("/login");
            });
        });
        logoutBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        // Limit aşım uyarısı olan kartları kontrol et
        int warningCount = 0;
        try {
            List<Card> activeCards = cardService.findAllActive();
            for (Card card : activeCards) {
                BigDecimal unpaid = expenseService.getUnpaidBalance(card.getId());
                BigDecimal limit = card.getCardLimit();
                if (limit != null && limit.compareTo(BigDecimal.ZERO) > 0) {
                    double pct = unpaid.divide(limit, 4, RoundingMode.HALF_UP).doubleValue() * 100;
                    if (pct >= 80.0) {
                        warningCount++;
                    }
                }
            }
        } catch (Exception ex) {
            // Expected: data may not be available during initial navigation
        }

        // Yaklaşan/Geciken ödemeleri kontrol et
        int reminderCount = 0;
        try {
            PaymentReminderService.ReminderSummary summary = reminderService.getReminderSummary();
            reminderCount = summary.getTotalCount();
        } catch (Exception ex) {
            // Expected: initial load without data
        }

        final int finalWarningCount = warningCount;
        final int finalReminderCount = reminderCount;
        final int totalNotifications = warningCount + reminderCount;

        // session attribute kontrolü
        Integer readCount = (Integer) com.vaadin.flow.server.VaadinSession.getCurrent().getAttribute("notificationsReadCount");
        if (readCount == null) readCount = 0;
        
        int unreadCount = Math.max(0, totalNotifications - readCount);

        HorizontalLayout rightSection = new HorizontalLayout();
        rightSection.addClassName("header-right-section");
        rightSection.setAlignItems(FlexComponent.Alignment.CENTER);
        rightSection.setSpacing(true);

        // Zil bildirimi - her zaman header'da kalır, sayıya göre görünür/gizlenir
        Icon bellIcon = new Icon(VaadinIcon.BELL);
        bellIcon.getStyle().set("color", "var(--lumo-tertiary-text-color)");
        
        Span alertBadge = new Span("0");
        alertBadge.getElement().getThemeList().add("badge error pill");
        alertBadge.getStyle().set("font-size", "0.75em").set("padding", "0.2em 0.5em");
        alertBadge.setVisible(false);

        HorizontalLayout alertTray = new HorizontalLayout(bellIcon, alertBadge);
        alertTray.addClassName("header-alert-tray");
        alertTray.setSpacing(false);
        alertTray.setAlignItems(FlexComponent.Alignment.CENTER);
        alertTray.getStyle().set("cursor", "pointer");
        alertTray.getElement().setAttribute("title", "Hatırlatıcılar");

        alertTray.addClickListener(e -> {
            com.vaadin.flow.server.VaadinSession.getCurrent().setAttribute("notificationsReadCount", finalWarningCount + finalReminderCount);
            e.getSource().getUI().ifPresent(ui -> {
                ui.access(() -> {
                    alertBadge.setText("0");
                    alertBadge.setVisible(false);
                });
                ui.navigate(ReminderView.class);
            });
        });

        if (totalNotifications > 0) {
            bellIcon.getStyle().set("color", reminderCount > 0 ? "var(--lumo-warning-color)" : "var(--lumo-error-color)");
            alertBadge.setText(String.valueOf(unreadCount));
            alertBadge.setVisible(true);
            String tooltip = String.format("%d limit uyarısı, %d bekleyen ödeme var!", warningCount, reminderCount);
            alertTray.getElement().setAttribute("title", tooltip);
        }

        rightSection.add(alertTray);

        // Karanlık Mod Butonu
        Button darkModeBtn = new Button(new Icon(VaadinIcon.MOON_O));
        darkModeBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        darkModeBtn.getElement().setAttribute("title", "Karanlık Mod");
        darkModeBtn.getElement().setAttribute("aria-label", "Karanlık modu aç/kapat");
        darkModeBtn.addClickListener(e -> {
            getElement().executeJs("document.documentElement.classList.toggle('dark')");
            Authentication currentAuth = SecurityContextHolder.getContext().getAuthentication();
            if (currentAuth != null) {
                String uname = currentAuth.getName();
                userService.findByUsername(uname).ifPresent(user -> {
                    boolean newDark = user.getDarkMode() == null || !user.getDarkMode();
                    user.setDarkMode(newDark);
                    userService.save(user);
                    darkModeBtn.setIcon(new Icon(newDark ? VaadinIcon.SUN_O : VaadinIcon.MOON_O));
                });
            }
        });
        Boolean darkMode = false;
        Authentication darkModeAuth = SecurityContextHolder.getContext().getAuthentication();
        if (darkModeAuth != null) {
            darkMode = userService.findByUsername(darkModeAuth.getName())
                    .map(u -> u.getDarkMode() != null && u.getDarkMode())
                    .orElse(false);
        }
        if (darkMode) {
            darkModeBtn.setIcon(new Icon(VaadinIcon.SUN_O));
            getElement().executeJs("document.documentElement.classList.add('dark')");
        }

        // Görünüm Ayarları Butonu
        Button settingsBtn = new Button(new Icon(VaadinIcon.COG), e -> openSettingsDialog());
        settingsBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        settingsBtn.getElement().setAttribute("title", "Dashboard Görünüm Ayarları");
        settingsBtn.getElement().setAttribute("aria-label", "Dashboard görünüm ayarları");

        // Şifre Değiştirme Butonu
        Button changePwdBtn = new Button(new Icon(VaadinIcon.LOCK), e -> openChangePasswordDialog());
        changePwdBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        changePwdBtn.getElement().setAttribute("title", "Şifre Değiştir");
        changePwdBtn.getElement().setAttribute("aria-label", "Şifre değiştir");

        rightSection.add(darkModeBtn, settingsBtn, changePwdBtn, userInfo, logoutBtn);

        Button shortcutsHelpBtn = new Button(new Icon(VaadinIcon.QUESTION_CIRCLE));
        shortcutsHelpBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        shortcutsHelpBtn.getElement().setAttribute("title", "Kısayollar (?)");
        shortcutsHelpBtn.getElement().setAttribute("aria-label", "Klavye kısayolları");
        shortcutsHelpBtn.setId("shortcuts-help-trigger");
        shortcutsHelpBtn.addClickListener(e -> openShortcutsDialog());
        rightSection.add(shortcutsHelpBtn);

        HorizontalLayout header = new HorizontalLayout(toggle, viewTitle, rightSection);
        header.addClassName("main-header-layout");
        header.setDefaultVerticalComponentAlignment(FlexComponent.Alignment.CENTER);
        header.expand(viewTitle);
        header.setWidthFull();
        header.addClassNames(
                LumoUtility.Padding.Vertical.NONE,
                LumoUtility.Padding.Horizontal.MEDIUM
        );

        addToNavbar(true, header);
    }

    private void openSettingsDialog() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "system";
        DashboardConfig config = userService.getDashboardConfig(username);

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Dashboard Görünüm Ayarları");
        
        VerticalLayout list = new VerticalLayout();
        list.setPadding(false);
        
        List<DashboardConfig.WidgetConfig> widgets = config.getOrderedWidgets();
        
        for (int i = 0; i < widgets.size(); i++) {
            DashboardConfig.WidgetConfig wc = widgets.get(i);
            HorizontalLayout item = new HorizontalLayout();
            item.setAlignItems(FlexComponent.Alignment.CENTER);
            item.setWidthFull();
            
            Checkbox visibilityCheckbox = new Checkbox(wc.getLabel(), wc.isVisible());
            visibilityCheckbox.addValueChangeListener(e -> wc.setVisible(e.getValue()));
            
            item.add(visibilityCheckbox);
            item.expand(visibilityCheckbox);
            list.add(item);
        }
        
        Button saveBtn = new Button("Kaydet", e -> {
            userService.saveDashboardConfig(username, config);
            Notification.show("Görünüm ayarları kaydedildi.", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            dialog.close();
            getUI().ifPresent(ui -> ui.getPage().reload());
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickShortcut(com.vaadin.flow.component.Key.ENTER);
        
        Button cancelBtn = new Button("İptal", e -> dialog.close());
        cancelBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);
        
        dialog.add(list);
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private void openChangePasswordDialog() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "";

        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Şifre Değiştir");
        dialog.setWidth("400px");

        PasswordField currentPwd = new PasswordField("Mevcut Şifre");
        currentPwd.setRequired(true);
        currentPwd.setWidthFull();

        PasswordField newPwd = new PasswordField("Yeni Şifre");
        newPwd.setRequired(true);
        newPwd.setWidthFull();
        newPwd.setMinLength(4);

        PasswordField confirmPwd = new PasswordField("Yeni Şifre Tekrar");
        confirmPwd.setRequired(true);
        confirmPwd.setWidthFull();

        VerticalLayout form = new VerticalLayout(currentPwd, newPwd, confirmPwd);
        form.setPadding(false);
        form.setSpacing(true);

        Button saveBtn = new Button("Kaydet", e -> {
            if (currentPwd.isEmpty() || newPwd.isEmpty() || confirmPwd.isEmpty()) {
                Notification.show("Lütfen tüm alanları doldurun", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (!userService.validatePassword(username, currentPwd.getValue())) {
                Notification.show("Mevcut şifre hatalı", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (!newPwd.getValue().equals(confirmPwd.getValue())) {
                Notification.show("Yeni şifreler eşleşmiyor", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            userService.findByUsername(username).ifPresent(user -> {
                userService.updatePassword(user.getId(), newPwd.getValue());
                Notification.show("Şifre başarıyla değiştirildi", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            });
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        Button cancelBtn = new Button("İptal", e -> dialog.close());
        cancelBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);

        dialog.add(form);
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private void openShortcutsDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Klavye Kısayolları");

        VerticalLayout layout = new VerticalLayout();
        layout.setPadding(true);
        layout.setSpacing(false);

        String[][] shortcuts = {
            {"A", "Ana Sayfa"},
            {"K", "Kartlar"},
            {"H", "Harcamalar"},
            {"F", "Harcamalar (Hızlı)"},
            {"Ç", "Çek"},
            {"C", "Cari"},
            {"B", "Bütçe"},
            {"N", "Notlar"},
            {"T", "Hatırlatıcılar"},
            {"R", "Raporlar"},
            {"E", "Görev"},
            {"11", "Kullanıcı"},
            {"22", "İşlem Geçmişi"},
            {"?", "Kısayol Yardım"}
        };

        for (String[] shortcut : shortcuts) {
            Span item = new Span(shortcut[0] + "  →  " + shortcut[1]);
            item.getStyle().set("font-size", "1em").set("padding", "0.3em 0");
            layout.add(item);
        }

        Button closeBtn = new Button("Kapat", e -> dialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        closeBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);

        dialog.add(layout);
        dialog.getFooter().add(closeBtn);
        dialog.open();
    }

    private void addDrawerContent() {
        H1 appName = new H1(appSettingsService.getCompanyName());
        appName.addClassNames(LumoUtility.FontSize.LARGE, LumoUtility.Margin.NONE);
        appName.getStyle().set("padding", "var(--lumo-space-m)");

        Header header = new Header(appName);

        Scroller scroller = new Scroller(createNavigation());

        addToDrawer(header, scroller, createCardDebtSection(), createFooter());
    }

    private SideNav createNavigation() {
        SideNav nav = new SideNav();

        nav.addItem(new SideNavItem("Ana Sayfa", DashboardView.class, VaadinIcon.HOME.create()));
        nav.addItem(new SideNavItem("Kartlar", CardListView.class, VaadinIcon.CREDIT_CARD.create()));
        nav.addItem(new SideNavItem("Harcamalar", ExpenseView.class, VaadinIcon.MONEY.create()));
        nav.addItem(new SideNavItem("Çek Takibi", ChequeView.class, VaadinIcon.FILE_TEXT.create()));
        nav.addItem(new SideNavItem("Cari Hesaplar", ContactView.class, VaadinIcon.USER_CARD.create()));
        nav.addItem(new SideNavItem("Bütçe Yönetimi", BudgetView.class, VaadinIcon.TRENDING_UP.create()));
        nav.addItem(new SideNavItem("Notlarım", NoteView.class, VaadinIcon.NOTEBOOK.create()));
        nav.addItem(new SideNavItem("Hatırlatıcılar", ReminderView.class, VaadinIcon.BELL.create()));
        nav.addItem(new SideNavItem("Raporlar", ReportView.class, VaadinIcon.BAR_CHART.create()));
        nav.addItem(new SideNavItem("Ortalama Vade", AverageMaturityView.class, VaadinIcon.CALENDAR_CLOCK.create()));
        nav.addItem(new SideNavItem("Görev & Eleman", EmployeeTaskView.class, VaadinIcon.TASKS.create()));
        nav.addItem(new SideNavItem("Profilim", ProfileView.class, VaadinIcon.USER.create()));

        // Admin menü öğeleri
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"))) {
            nav.addItem(new SideNavItem("Kullanıcılar", UserManagementView.class, VaadinIcon.USERS.create()));
            nav.addItem(new SideNavItem("İşlem Geçmişi", AuditLogView.class, VaadinIcon.CLIPBOARD_TEXT.create()));
        }

        return nav;
    }

    private Div createCardDebtSection() {
        Div container = new Div();
        container.getStyle()
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("border-top", "1px solid var(--lumo-contrast-10pct)")
                .set("margin-top", "var(--lumo-space-s)")
                .set("max-height", "180px")
                .set("overflow-y", "auto");

        Span title = new Span("Kart Borç Durumu");
        title.getStyle()
                .set("font-size", "0.75em")
                .set("font-weight", "700")
                .set("color", "var(--lumo-tertiary-text-color)")
                .set("text-transform", "uppercase")
                .set("letter-spacing", "0.05em")
                .set("display", "block")
                .set("margin-bottom", "0.5em");

        VerticalLayout list = new VerticalLayout();
        list.setPadding(false);
        list.setSpacing(false);
        cardDebtList = list;
        populateCardDebtList(list);

        container.add(title, list);
        return container;
    }

    private void populateCardDebtList(VerticalLayout list) {
        list.removeAll();
        NumberFormat trCurrency = NumberFormat.getCurrencyInstance(Locale.of("tr", "TR"));
        List<Card> activeCards = cardService.findAllActive();

        Map<Long, BigDecimal> unpaidBalances = expenseService.getUnpaidBalancesGroupedByCard();

        for (Card card : activeCards) {
            BigDecimal unpaid = unpaidBalances.getOrDefault(card.getId(), BigDecimal.ZERO);
            BigDecimal limit = card.getCardLimit();

            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull();
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            row.setSpacing(false);
            row.getStyle()
                    .set("padding", "0.3em 0")
                    .set("font-size", "0.8em");

            Div dot = new Div();
            dot.getStyle()
                    .set("width", "8px")
                    .set("height", "8px")
                    .set("border-radius", "50%")
                    .set("background-color", card.getColor() != null ? card.getColor() : "#1976D2")
                    .set("flex-shrink", "0")
                    .set("margin-right", "0.5em");

            Span nameSpan = new Span(card.getName());
            nameSpan.getStyle()
                    .set("font-size", "0.8em")
                    .set("min-width", "0")
                    .set("overflow", "hidden")
                    .set("text-overflow", "ellipsis")
                    .set("white-space", "nowrap");

            Span amountSpan;
            if (unpaid.compareTo(BigDecimal.ZERO) == 0) {
                amountSpan = new Span("₺0");
                amountSpan.getStyle().set("color", "var(--lumo-success-text-color)").set("font-weight", "600");
            } else {
                amountSpan = new Span(trCurrency.format(unpaid));
                amountSpan.getStyle().set("color", "var(--lumo-error-text-color)").set("font-weight", "600");
            }
            amountSpan.getStyle()
                    .set("font-size", "0.8em")
                    .set("flex-shrink", "0")
                    .set("margin-left", "0.5em");

            row.add(dot, nameSpan, amountSpan);
            row.expand(nameSpan);

            VerticalLayout cardItem = new VerticalLayout();
            cardItem.setPadding(false);
            cardItem.setSpacing(false);
            cardItem.add(row);

            if (limit != null && limit.compareTo(BigDecimal.ZERO) > 0) {
                double ratio = unpaid.divide(limit, 4, RoundingMode.HALF_UP).doubleValue();
                ProgressBar pb = new ProgressBar();
                pb.setValue(Math.min(ratio, 1.0));
                pb.setWidth("100%");
                pb.getStyle().set("height", "3px").set("margin-top", "0.2em");

                if (ratio >= 1.0) {
                    pb.getElement().setAttribute("theme", "error");
                } else if (ratio >= 0.8) {
                    pb.getElement().setAttribute("theme", "contrast");
                } else {
                    pb.getElement().setAttribute("theme", "success");
                }

                cardItem.add(pb);
            }

            list.add(cardItem);
        }

        if (activeCards.isEmpty()) {
            Span noCards = new Span("Aktif kart bulunamadı");
            noCards.getStyle()
                    .set("font-size", "0.75em")
                    .set("color", "var(--lumo-tertiary-text-color)");
            list.add(noCards);
        }
    }

    private void updateCardDebtSection() {
        if (cardDebtList != null) {
            populateCardDebtList(cardDebtList);
        }
    }

    private Footer createFooter() {
        Footer footer = new Footer();
        footer.getStyle().set("display", "flex")
                .set("flex-direction", "column")
                .set("padding", "var(--lumo-space-m)")
                .set("border-top", "1px solid var(--lumo-contrast-10pct)");

        Span copyright = new Span("© 2026 RasPel");
        copyright.getStyle()
                .set("font-size", "0.8em")
                .set("font-weight", "600")
                .set("color", "var(--lumo-secondary-text-color)");

        Span author = new Span("Yazılım: Rasim Tuzluoğlu");
        author.getStyle()
                .set("font-size", "0.75em")
                .set("color", "var(--lumo-tertiary-text-color)")
                .set("margin-top", "0.2em");

        footer.add(copyright, author);
        return footer;
    }

    @Override
    protected void afterNavigation() {
        super.afterNavigation();

        viewTitle.setText(getCurrentPageTitle());
        injectKeyboardShortcuts();
        injectFabButton();
        checkNoteReminders();
        checkBudgetWarnings();
        updateCardDebtSection();

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            userService.updateLastLogin(auth.getName());
            userService.recordSuccessfulLogin(auth.getName());
        }

        Boolean sessionDarkInitialized = (Boolean) com.vaadin.flow.server.VaadinSession.getCurrent().getAttribute("darkModeInitialized");
        if (sessionDarkInitialized == null || !sessionDarkInitialized) {
            com.vaadin.flow.server.VaadinSession.getCurrent().setAttribute("darkModeInitialized", true);
            if (auth != null) {
                boolean userDark = userService.findByUsername(auth.getName())
                        .map(u -> u.getDarkMode() != null && u.getDarkMode())
                        .orElse(false);
                if (userDark) {
                    getElement().executeJs("document.documentElement.classList.add('dark')");
                }
            }
        }

        Object openDialog = com.vaadin.flow.server.VaadinSession.getCurrent().getAttribute("fabOpenDialog");
        if (openDialog instanceof String dialogType) {
            com.vaadin.flow.server.VaadinSession.getCurrent().setAttribute("fabOpenDialog", null);
            com.vaadin.flow.component.Component content = getContent();
            if ("card".equals(dialogType) && content instanceof CardListView cv) {
                cv.openEditDialog(null);
            } else if ("expense".equals(dialogType) && content instanceof ExpenseView ev) {
                ev.openExpenseDialog(null);
            } else if ("cheque".equals(dialogType) && content instanceof ChequeView cv) {
                cv.openEditDialog(null, com.raspel.cardtracker.domain.cheque.ChequeType.ENTERING);
            }
        }
    }

    private void injectFabButton() {
        getElement().executeJs(
            "setTimeout(function(){" +
            "if(document.getElementById('global-fab')) return;" +
            "var fab=document.createElement('button');" +
            "fab.id='global-fab';" +
            "fab.innerHTML='+';" +
            "fab.setAttribute('role','button');" +
            "fab.setAttribute('aria-label','Hızlı ekleme menüsü');" +
            "fab.setAttribute('tabindex','0');" +
            "fab.style.cssText='position:fixed;bottom:24px;right:24px;z-index:1000;" +
            "width:56px;height:56px;border-radius:50%;border:none;" +
            "background:var(--lumo-primary-color);color:#fff;font-size:28px;" +
            "cursor:pointer;box-shadow:0 4px 12px rgba(0,0,0,0.3);" +
            "display:flex;align-items:center;justify-content:center;';" +
            "fab.onkeydown=function(e){" +
            "  if(e.key==='Enter'||e.key===' '){" +
            "    e.preventDefault();fab.click();" +
            "  }" +
            "};" +
            "fab.onclick=function(e){" +
            "e.stopPropagation();" +
            "var existing=document.getElementById('fab-menu');if(existing){existing.remove();return;}" +
            "var d=document.createElement('div');" +
            "d.style.cssText='position:fixed;bottom:90px;right:24px;z-index:1001;" +
            "background:var(--lumo-base-color);border-radius:12px;padding:8px;" +
            "box-shadow:0 4px 16px rgba(0,0,0,0.2);display:flex;flex-direction:column;gap:4px;';" +
            "d.id='fab-menu';" +
            "['Kart','Harcama','Çek'].forEach(function(label){" +
            "var b=document.createElement('button');" +
            "b.textContent='Yeni '+label;" +
            "b.style.cssText='padding:10px 16px;border:none;border-radius:8px;" +
            "background:transparent;color:var(--lumo-body-text-color);" +
            "cursor:pointer;font-size:14px;text-align:left;width:100%;';" +
            "b.onclick=function(ev){ev.stopPropagation();" +
            "sessionStorage.setItem('fabOpenDialog',label==='Kart'?'card':label==='Harcama'?'expense':'cheque');" +
            "window.location.href=label==='Kart'?'cards':label==='Harcama'?'expenses':'cheques';" +
            "};" +
            "d.appendChild(b);});" +
            "document.body.appendChild(d);" +
            "setTimeout(function(){" +
            "document.addEventListener('click',function closeFab(){" +
            "var m=document.getElementById('fab-menu');if(m){m.remove();}" +
            "document.removeEventListener('click',closeFab);" +
            "});},10);" +
            "};" +
            "document.body.appendChild(fab);" +
            "},200);"
        );
    }

    private void checkNoteReminders() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null) return;
            String username = auth.getName();
            List<Note> dueReminders = noteService.getDueReminders(username);
            if (!dueReminders.isEmpty()) {
                for (Note note : dueReminders) {
                    Notification noteNotif = Notification.show(
                        "Hatırlatma: " + note.getTitle(),
                        4000, Notification.Position.MIDDLE);
                    noteNotif.addThemeVariants(NotificationVariant.LUMO_PRIMARY);
                    noteNotif.getElement().getStyle().set("animation", "none").set("transition", "none");
                    noteService.markReminded(note.getId());
                }
            }
        } catch (Exception ignored) {
            // Expected: no reminders or data unavailable at this time
        }
    }

    private void checkBudgetWarnings() {
        try {
            LocalDate now = LocalDate.now();
            int year = now.getYear();
            int month = now.getMonthValue();

            List<Card> activeCards = cardService.findAllActive();
            for (Card card : activeCards) {
                BigDecimal unpaid = expenseService.getUnpaidBalance(card.getId());
                BigDecimal limit = card.getCardLimit();
                if (limit != null && limit.compareTo(BigDecimal.ZERO) > 0) {
                    double pct = unpaid.divide(limit, 4, RoundingMode.HALF_UP).doubleValue() * 100;
                    if (pct >= 90.0) {
                        Notification.show(
                            card.getName() + " kart limiti %" + (int) pct + " dolu!",
                            5000, Notification.Position.MIDDLE)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    }
                }
            }

            List<DepartmentBudget> budgets = departmentBudgetService.findByYearAndMonth(year, month);
            for (DepartmentBudget budget : budgets) {
                String deptName = budget.getDepartment() != null ? budget.getDepartment().getName() : "";
                BigDecimal spent = expenseService.getDepartmentSpentForMonth(deptName, year, month);
                BigDecimal budgetLimit = budget.getBudgetLimit();
                if (budgetLimit != null && budgetLimit.compareTo(BigDecimal.ZERO) > 0) {
                    double pct = spent.divide(budgetLimit, 4, RoundingMode.HALF_UP).doubleValue() * 100;
                    if (pct >= 80.0) {
                        Notification.show(
                            deptName + " bütçesi %" + (int) pct + " kullanıldı!",
                            5000, Notification.Position.MIDDLE)
                            .addThemeVariants(NotificationVariant.LUMO_WARNING);
                    }
                }
            }
        } catch (Exception ignored) {
            // Expected: data may not be available during initial navigation
        }
    }

    private String getCurrentPageTitle() {
        PageTitle title = getContent().getClass().getAnnotation(PageTitle.class);
        String pageTitle = title == null ? "" : title.value();
        String company = appSettingsService.getCompanyName();
        if (!pageTitle.contains(company)) {
            return pageTitle + " | " + company;
        }
        return pageTitle;
    }
}
