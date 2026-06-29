package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
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
import com.vaadin.flow.component.progressbar.ProgressBar;
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
import com.vaadin.flow.component.ClientCallable;
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
            add(new Span("Kullanıcı bulunamadı."));
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
        roleField.setValue(user.getRole() != null ? user.getRole().name() : "");
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

        // TELEGRAM BAĞLANTISI
        Div telegramCard = buildCard("Telegram Bağlantısı");
        VerticalLayout telegramContent = new VerticalLayout();
        telegramContent.setPadding(false);
        telegramContent.setSpacing(true);

        if (user.getTelegramChatId() != null) {
            Span connectedIcon = new Span("\u2705");
            connectedIcon.getStyle().set("font-size", "24px").set("display", "block");

            Span connectedText = new Span("Telegram hesabınız bağlı!");
            connectedText.getStyle()
                    .set("font-weight", "600")
                    .set("color", "var(--lumo-success-text-color)")
                    .set("font-size", "0.95em");

            Span chatIdInfo = new Span("Chat ID: " + user.getTelegramChatId());
            chatIdInfo.getStyle()
                    .set("font-size", "0.8em")
                    .set("color", "var(--lumo-secondary-text-color)");

            Button disconnectBtn = new Button("Telegram Bağlantısını Kes", new Icon(VaadinIcon.UNLINK), e -> {
                userService.disconnectTelegram(username);
                Notification.show("Telegram bağlantısı kesildi.", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                getUI().ifPresent(ui -> ui.getPage().reload());
            });
            disconnectBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

            telegramContent.add(connectedIcon, connectedText, chatIdInfo, disconnectBtn);
        } else {
            Span notConnected = new Span("Telegram hesabınız henüz bağlı değil.");
            notConnected.getStyle()
                    .set("font-size", "0.9em")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("display", "block");

            Span instructions = new Span("Bağlamak için:\n" +
                    "1. Aşağıdaki butona tıklayarak doğrulama kodu alın\n" +
                    "2. Telegram'da @raspel_fin_bot bot'unu başlatın\n" +
                    "3. /start yazın ve size verilen kodu gönderin");
            instructions.getStyle()
                    .set("font-size", "0.8em")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("display", "block")
                    .set("white-space", "pre-wrap")
                    .set("margin-top", "4px");

            Button connectBtn = new Button("Telegram'a Bağlan", new Icon(VaadinIcon.PAPERPLANE), e -> {
                try {
                    String code = userService.generateTelegramVerificationCode(username);
                    Dialog codeDialog = new Dialog();
                    codeDialog.setHeaderTitle("Telegram Doğrulama Kodu");
                    codeDialog.setWidth("400px");

                    Div codeDisplay = new Div();
                    codeDisplay.getStyle()
                            .set("background", "var(--lumo-contrast-5pct)")
                            .set("border-radius", "12px")
                            .set("padding", "24px")
                            .set("text-align", "center");

                    Span codeLabel = new Span("Doğrulama Kodunuz");
                    codeLabel.getStyle()
                            .set("font-size", "0.8em")
                            .set("color", "var(--lumo-secondary-text-color)")
                            .set("display", "block")
                            .set("margin-bottom", "8px");

                    Span codeValue = new Span(code);
                    codeValue.getStyle()
                            .set("font-size", "2em")
                            .set("font-weight", "bold")
                            .set("letter-spacing", "0.1em")
                            .set("color", "var(--lumo-primary-text-color)")
                            .set("display", "block")
                            .set("font-family", "monospace");

                    Span botLink = new Span("Bu kodu @raspel_fin_bot bot'una gönderin");
                    botLink.getStyle()
                            .set("font-size", "0.8em")
                            .set("color", "var(--lumo-secondary-text-color)")
                            .set("display", "block")
                            .set("margin-top", "12px");

                    codeDisplay.add(codeLabel, codeValue, botLink);

                    Button openBotBtn = new Button("Bot'u Aç", new Icon(VaadinIcon.EXTERNAL_LINK), ev -> {
                        getUI().ifPresent(ui -> ui.getPage().open("https://t.me/raspel_fin_bot"));
                    });
                    openBotBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                    openBotBtn.getStyle().set("margin-top", "12px");

                    codeDisplay.add(openBotBtn);

                    Button closeBtn = new Button("Tamam, Kodladım", ev2 -> {
                        codeDialog.close();
                        getUI().ifPresent(ui -> ui.getPage().reload());
                    });
                    closeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

                    codeDialog.add(codeDisplay);
                    codeDialog.getFooter().add(closeBtn);
                    codeDialog.open();
                    codeDialog.getElement().getStyle().set("overflow", "hidden");
                } catch (Exception ex) {
                    Notification.show("Kod oluşturulurken hata oluştu.", 3000, Notification.Position.BOTTOM_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            });
            connectBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

            telegramContent.add(notConnected, instructions, connectBtn);
        }
        telegramCard.add(telegramContent);
        grid.add(telegramCard);

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
                .limit(3).collect(Collectors.toList());

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
        } else {
            Span info = new Span("Son harcamalar ve sistemdeki son çekler");
            info.getStyle().set("font-size", "0.7em").set("color", "var(--lumo-tertiary-text-color)").set("display", "block").set("margin-top", "4px");
            recentList.add(info);
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
                            try { return new java.io.FileInputStream(f); } catch (Exception ex) { throw new RuntimeException("Dosya okunamadı", ex); }
                        });
                    res.setContentType("application/sql");
                    com.vaadin.flow.component.html.Anchor anchor = new com.vaadin.flow.component.html.Anchor(res, "");
                    anchor.getElement().setAttribute("download", f.getName());
                    anchor.getElement().getStyle().set("display", "none");
                    add(anchor);
                    anchor.getElement().executeJs("this.click()");
                    getUI().ifPresent(ui -> ui.getPage().executeJs("setTimeout(function(){arguments[0].remove()},1000)", anchor.getElement()));
                } catch (Exception ex) {
                    Notification.show("Yedek alınamadı, lütfen daha sonra tekrar deneyin.", 5000, Notification.Position.MIDDLE)
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
                        Notification.show("Geri yükleme başarısız, lütfen daha sonra tekrar deneyin.", 6000, Notification.Position.MIDDLE)
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

        // Guncelleme Al - herkes gorur, sadece admin calistirabilir
        Div updateCard = buildCard("Yaz\u0131l\u0131m G\u00FCncelleme");
        VerticalLayout updateContent = new VerticalLayout();
        updateContent.setPadding(false);
        updateContent.setSpacing(true);

        Span updateIcon = new Span("\uD83D\uDE80");
        updateIcon.getStyle().set("font-size", "28px").set("display", "block");

        Span updateLabel = new Span("Yeni S\u00FCr\u00FCm Kontrol\u00FC");
        updateLabel.getStyle().set("font-weight", "600").set("font-size", "0.95em").set("display", "block");

        Span updateDesc = new Span("En son yaz\u0131l\u0131m s\u00FCr\u00FCm\u00FCn\u00FC kontrol edin ve g\u00FCncelleme yap\u0131n.");
        updateDesc.getStyle().set("font-size", "0.8em").set("color", "var(--lumo-secondary-text-color)")
                .set("display", "block").set("margin-top", "2px");

        Span updateInfo = new Span("Yaz\u0131l\u0131m\u0131m\u0131z s\u00FCrekli geli\u015Fmekte, yeni \u00F6zellikler eklenmektedir.");
        updateInfo.getStyle().set("font-size", "0.72em").set("color", "var(--lumo-tertiary-text-color)")
                .set("display", "block").set("font-style", "italic");

        Button updateBtn = new Button("G\u00FCncelleme Al", new Icon(VaadinIcon.REFRESH), e -> {
            if (!isAdmin) {
                Notification.show(
                        "Bu \u00F6zellik sadece y\u00F6netici taraf\u0131ndan kullan\u0131labilir. Yaz\u0131l\u0131m\u0131m\u0131z s\u00FCrekli geli\u015Fmekte, yeni \u00F6zellikler eklenmektedir.",
                        5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_CONTRAST);
                return;
            }

            // Kontrol dialog'u ac
            Dialog checkDialog = new Dialog();
            checkDialog.setHeaderTitle("G\u00FCncelleme Kontrol\u00FC");
            checkDialog.setWidth("450px");
            checkDialog.setCloseOnOutsideClick(false);

            VerticalLayout checkContent = new VerticalLayout();
            checkContent.setPadding(true);
            checkContent.setSpacing(true);
            checkContent.setAlignItems(FlexComponent.Alignment.CENTER);

            ProgressBar loadingBar = new ProgressBar();
            loadingBar.setIndeterminate(true);
            loadingBar.setWidth("60px");

            Span statusText = new Span("G\u00FCncelleme kontrol ediliyor, l\u00FCtfen bekleyin...");
            statusText.getStyle().set("font-size", "0.9em").set("color", "var(--lumo-secondary-text-color)");

            checkContent.add(loadingBar, statusText);
            checkDialog.add(checkContent);

            Button cancelBtn = new Button("Kapat", ev -> checkDialog.close());
            cancelBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);
            checkDialog.getFooter().add(cancelBtn);
            checkDialog.open();

            getElement().executeJs(
                "var d=arguments[0]; var l=arguments[1]; var s=arguments[2]; var view=arguments[3];" +
                "var controller=new AbortController(); var timeout=setTimeout(function(){controller.abort()},30000);" +
                "fetch('/api/system/check-update', {method:'POST',headers:{'Content-Type':'application/json'},credentials:'same-origin',signal:controller.signal})" +
                ".then(r=>r.json())" +
                ".then(data=>{" +
                "  clearTimeout(timeout);" +
                "  l.style.display='none';" +
                "  if(data.updateAvailable){" +
                "    s.textContent='Yeni g\u00FCncelleme mevcut!';" +
                "    s.style.color='var(--lumo-success-color)'; s.style.fontWeight='700';" +
                "    d.close();" +
                "    view.$server.openUpdateConfirm();" +
                "  }else if(data.upToDate){" +
                "    var icon=document.createElement('span'); icon.textContent='\u2705'; icon.style.fontSize='36px';" +
                "    s.parentElement.insertBefore(icon,s);" +
                "    s.textContent='Yaz\u0131l\u0131m\u0131n\u0131z g\u00FCncel.';" +
                "    s.style.color='var(--lumo-success-color)'; s.style.fontWeight='600';" +
                "    var sub=document.createElement('span'); sub.textContent='Yeni bir s\u00FCr\u00FCm bulunamad\u0131.';" +
                "    sub.style.cssText='font-size:0.78em;color:var(--lumo-tertiary-text-color);display:block;margin-top:4px';" +
                "    s.parentElement.appendChild(sub);" +
                "  }else{" +
                "    var icon=document.createElement('span'); icon.textContent='\u26A0\uFE0F'; icon.style.fontSize='36px';" +
                "    s.parentElement.insertBefore(icon,s);" +
                "    s.textContent='G\u00FCncelleme kontrol edilemedi.';" +
                "    s.style.color='var(--lumo-error-color)'; s.style.fontWeight='600';" +
                "    var sub=document.createElement('span'); sub.textContent=data.message||'Docker eri\u015Filebilir olmayabilir.';" +
                "    sub.style.cssText='font-size:0.78em;color:var(--lumo-tertiary-text-color);display:block;margin-top:4px';" +
                "    s.parentElement.appendChild(sub);" +
                "  }" +
                "}).catch(function(err){" +
                "  clearTimeout(timeout);" +
                "  l.style.display='none';" +
                "  var icon=document.createElement('span'); icon.textContent='\u26A0\uFE0F'; icon.style.fontSize='36px';" +
                "  s.parentElement.insertBefore(icon,s);" +
                "  s.textContent='G\u00FCncelleme kontrol edilemedi.';" +
                "  s.style.color='var(--lumo-error-color)'; s.style.fontWeight='600';" +
                "  var sub=document.createElement('span');" +
                "  sub.textContent=err.name==='AbortError'?'\u0130\u015Flem zaman a\u015F\u0131m\u0131na u\u011Frad\u0131 (30sn). Docker eri\u015Filebilir olmayabilir.':'Docker eri\u015Filebilir olmayabilir.';" +
                "  sub.style.cssText='font-size:0.78em;color:var(--lumo-tertiary-text-color);display:block;margin-top:4px';" +
                "  s.parentElement.appendChild(sub);" +
                "})",
                checkDialog.getElement(), loadingBar.getElement(), statusText.getElement(), getElement()
            );
        });
        updateBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (!isAdmin) {
            updateBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            updateBtn.getElement().setAttribute("title", "Y\u00F6netici yetkisi gerektirir");
        }

        updateContent.add(updateIcon, updateLabel, updateDesc, updateInfo, updateBtn);
        updateContent.setAlignItems(FlexComponent.Alignment.CENTER);
        updateContent.getStyle().set("text-align", "center").set("padding", "8px 0");
        updateCard.add(updateContent);
        grid.add(updateCard);

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
                .withSeries(new Series<>("TL", projection.values().stream()
                        .map(v -> v != null ? v.doubleValue() : 0.0).toArray(Double[]::new)))
                .withColors("#2196F3")
                .build();
        miniChart.setWidth("100%");
        chartCard.add(miniChart);
        grid.add(chartCard);

        add(grid);
    }

    @ClientCallable
    private void openUpdateConfirm() {
        getUI().ifPresent(ui -> ui.access(() -> {
            Dialog confirmDialog = new Dialog();
            confirmDialog.setHeaderTitle("G\u00FCncelleme Onay\u0131 - Dikkat!");
            confirmDialog.setWidth("480px");

            VerticalLayout confirmContent = new VerticalLayout();
            confirmContent.setPadding(false);
            confirmContent.setSpacing(true);

            Span warnTitle = new Span("Sistem g\u00FCncellemesi ba\u015Flat\u0131lacak!");
            warnTitle.getStyle().set("font-weight", "700").set("font-size", "1.05em")
                    .set("color", "var(--lumo-error-color)").set("display", "block");

            Span warnMsg = new Span(
                    "G\u00FCncelleme s\u0131ras\u0131nda uygulama k\u0131sa s\u00FCreli\u011Fine eri\u015Filemez olacakt\u0131r. " +
                    "T\u00FCm kullan\u0131c\u0131lar\u0131n i\u015Flemlerini kaydetmi\u015F oldu\u011Fundan emin olun. " +
                    "Bu i\u015Flemi mesai saati d\u0131\u015F\u0131nda yapman\u0131z \u00F6nerilir.");
            warnMsg.getStyle().set("font-size", "0.88em").set("color", "var(--lumo-body-text-color)")
                    .set("display", "block").set("line-height", "1.5");

            Span activeWarning = new Span("Aktif kullan\u0131c\u0131 say\u0131s\u0131 sorgulan\u0131yor...");
            activeWarning.getStyle().set("display", "block").set("font-size", "0.85em")
                    .set("color", "var(--lumo-warning-color)").set("font-weight", "600");

            confirmContent.add(warnTitle, warnMsg, activeWarning);
            confirmDialog.add(confirmContent);

            Button proceedBtn = new Button("G\u00FCncellemeyi Ba\u015Flat", ev -> {
                confirmDialog.close();
                executeUpdate();
            });
            proceedBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
            proceedBtn.setEnabled(false);

            Button cancelBtn = new Button("\u0130ptal", ev -> confirmDialog.close());
            cancelBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);
            confirmDialog.getFooter().add(cancelBtn, proceedBtn);
            confirmDialog.open();

            getElement().executeJs(
                "fetch('/api/system/active-users', {method:'POST',headers:{'Content-Type':'application/json'},credentials:'same-origin'})" +
                ".then(r=>r.json())" +
                ".then(data=>{" +
                "  var el=arguments[0];" +
                "  if(data.count>0){" +
                "    el.textContent='Uyar\u0131: Sistemde ' + data.count + ' aktif kullan\u0131c\u0131 bulunuyor! L\u00FCtfen g\u00FCncellemeyi mesai saati d\u0131\u015F\u0131nda yap\u0131n.';" +
                "    el.style.color='var(--lumo-error-color)';" +
                "  }else{" +
                "    el.textContent='Aktif kullan\u0131c\u0131 bulunmuyor. G\u00FCncelleme i\u00E7in uygun zaman.';" +
                "    el.style.color='var(--lumo-success-color)';" +
                "    arguments[1].disabled=false;" +
                "  }" +
                "}).catch(()=>{" +
                "  arguments[1].disabled=false;" +
                "})",
                activeWarning.getElement(), proceedBtn.getElement()
            );
        }));
    }

    private void executeUpdate() {
        Notification.show("G\u00FCncelleme ba\u015Flat\u0131l\u0131yor, l\u00FCtfen bekleyin...", 3000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_PRIMARY);

        getElement().executeJs(
            "fetch('/api/system/update', {method:'POST',headers:{'Content-Type':'application/json'},credentials:'same-origin'})" +
            ".then(r=>r.json())" +
            ".then(data=>{" +
            "  if(data.status==='ok'){" +
            "    document.body.innerHTML='<div style=\"display:flex;align-items:center;justify-content:center;height:100vh;flex-direction:column;font-family:sans-serif;\">" +
            "      <div style=\"font-size:48px;margin-bottom:16px\">&#128640;</div>" +
            "      <h2 style=\"color:#4CAF50\">G\u00FCncelleme Ba\u015Far\u0131l\u0131!</h2>" +
            "      <p style=\"color:#666;margin-bottom:24px\">Uygulama yeniden ba\u015Flat\u0131l\u0131yor, l\u00FCtfen bekleyin...</p>" +
            "      <div class=\\\"loading-spinner\\\" style=\\\"width:40px;height:40px;border:4px solid #e0e0e0;border-top:4px solid #2196F3;border-radius:50%;animation:spin 1s linear infinite;\\\"></div>" +
            "      <style>@keyframes spin{0%{transform:rotate(0deg)}100%{transform:rotate(360deg)}}</style>" +
            "    </div>';" +
            "    setTimeout(function(){location.reload()},8000);" +
            "  }else{" +
            "    alert('G\u00FCncelleme ba\u015Far\u0131s\u0131z: ' + data.message);" +
            "  }" +
            "}).catch(err=>{alert('G\u00FCncelleme yap\u0131lamad\u0131. Docker kurulu olmayabilir.');});"
        );
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
