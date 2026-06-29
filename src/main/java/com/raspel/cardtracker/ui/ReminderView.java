package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.raspel.cardtracker.domain.cheque.Cheque;
import com.raspel.cardtracker.domain.cheque.ChequeService;
import com.raspel.cardtracker.domain.cheque.ChequeStatus;
import com.raspel.cardtracker.domain.expense.ExpenseService;
import com.raspel.cardtracker.domain.expense.InstallmentEntry;
import com.raspel.cardtracker.domain.note.Note;
import com.raspel.cardtracker.domain.note.NoteService;
import com.raspel.cardtracker.domain.reminder.PaymentReminderService;
import com.raspel.cardtracker.domain.settings.AppSettingsService;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Route(value = "reminders", layout = MainLayout.class)
@PageTitle("Ödeme Hatırlatıcıları")
@PermitAll
public class ReminderView extends VerticalLayout {

    private final PaymentReminderService reminderService;
    private final ExpenseService expenseService;
    private final ChequeService chequeService;
    private final NoteService noteService;
    private final AppSettingsService appSettingsService;

    private final Grid<InstallmentEntry> installmentGrid = new Grid<>(InstallmentEntry.class, false);
    private final Grid<Cheque> chequeGrid = new Grid<>(Cheque.class, false);
    private final ProgressBar loadingBar = new ProgressBar();

    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(Locale.of("tr", "TR"));
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final Div contentDiv = new Div();
    private final Tab installmentTab = new Tab("Kredi Kartı Taksitleri");
    private final Tab chequeTab = new Tab("Çek Ödemeleri/Vadeleri");
    private final Tabs tabs = new Tabs(installmentTab, chequeTab);
    private HorizontalLayout summaryLayout;

    public ReminderView(PaymentReminderService reminderService, ExpenseService expenseService, ChequeService chequeService, NoteService noteService, AppSettingsService appSettingsService) {
        this.reminderService = reminderService;
        this.expenseService = expenseService;
        this.chequeService = chequeService;
        this.noteService = noteService;
        this.appSettingsService = appSettingsService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);
        loadingBar.setWidthFull();
        add(loadingBar);

        createHeader();
        createNoteSection();
        createUpdateBanner();
        createSummaryCards();
        createTabs();
        createGrids();

        add(tabs, contentDiv);
        
        // Varsayılan olarak taksit tabını göster
        showInstallmentsContent();
    }

    private void createHeader() {
        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);

        H3 title = new H3("Ödeme Hatırlatıcıları & Vade Takip");
        title.getStyle().set("margin", "0");

        Button refreshBtn = new Button("Yenile", new Icon(VaadinIcon.REFRESH), e -> refreshData());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        header.add(title, refreshBtn);
        header.expand(title);
        add(header);
    }

    private void createUpdateBanner() {
        String updateFlag = appSettingsService.getSetting("updateAvailable");
        if (!"true".equals(updateFlag)) return;

        Div banner = new Div();
        banner.getStyle()
                .set("background", "linear-gradient(135deg, var(--lumo-primary-color-10pct), var(--lumo-base-color))")
                .set("border", "1px solid var(--lumo-primary-color-30pct)")
                .set("border-radius", "12px")
                .set("padding", "1em 1.5em")
                .set("margin-bottom", "1em")
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "1em");

        Span icon = new Span("\uD83C\uDF1F");
        icon.getStyle().set("font-size", "1.8em");

        VerticalLayout text = new VerticalLayout();
        text.setPadding(false);
        text.setSpacing(false);

        Span title = new Span("Yeni G\u00FCncelleme Mevcut!");
        title.getStyle().set("font-weight", "700").set("font-size", "1em").set("color", "var(--lumo-primary-text-color)");

        Span sub = new Span("Yaz\u0131l\u0131m\u0131n yeni bir s\u00FCr\u00FCm\u00FC bulundu. Profil sayfas\u0131ndan g\u00FCncelleme yapabilirsiniz.");
        sub.getStyle().set("font-size", "0.8em").set("color", "var(--lumo-secondary-text-color)");

        text.add(title, sub);

        Button goBtn = new Button("G\u00FCncelle", new Icon(VaadinIcon.ARROW_RIGHT), e -> {
            appSettingsService.setSetting("updateAvailable", "false");
            getUI().ifPresent(ui -> ui.navigate("profile"));
        });
        goBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        goBtn.getStyle().set("flex-shrink", "0");

        HorizontalLayout row = new HorizontalLayout(icon, text, goBtn);
        row.setWidthFull();
        row.setAlignItems(Alignment.CENTER);
        row.expand(text);

        banner.add(row);
        add(banner);
    }

    private void createNoteSection() {
        HorizontalLayout noteForm = new HorizontalLayout();
        noteForm.setWidthFull();
        noteForm.setAlignItems(Alignment.CENTER);
        noteForm.getStyle().set("flex-wrap", "wrap");

        com.vaadin.flow.component.textfield.TextField noteField = new com.vaadin.flow.component.textfield.TextField();
        noteField.setPlaceholder("Hızlı not ekle...");
        noteField.setClearButtonVisible(true);

        Button addNoteBtn = new Button("Not Ekle", new Icon(VaadinIcon.PLUS), e -> {
            String content = noteField.getValue() != null ? noteField.getValue().trim() : "";
            if (content.isEmpty()) {
                Notification.show("Not içeriği boş olamaz.", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "system";
            Note note = Note.builder()
                    .title(content.length() > 50 ? content.substring(0, 50) : content)
                    .content(content)
                    .category("Hatırlatma")
                    .createdBy(username)
                    .build();
            noteService.save(note);
            Notification.show("Not eklendi.", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            noteField.clear();
            showInstallmentsContent();
        });
        addNoteBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        noteForm.add(noteField, addNoteBtn);
        noteForm.expand(noteField);
        add(noteForm);
    }

    private void createSummaryCards() {
        PaymentReminderService.ReminderSummary summary = reminderService.getReminderSummary();

        Div overdueCard = createStatCard("Geciken Taksitler", 
                summary.getOverdueInstallmentCount() + " Adet (" + currencyFormat.format(summary.getOverdueInstallmentTotal()) + ")", 
                "⚠️", "#D32F2F");

        Div upcomingCard = createStatCard("Yaklaşan Taksitler (7 Gün)", 
                summary.getUpcomingInstallmentCount() + " Adet (" + currencyFormat.format(summary.getUpcomingInstallmentTotal()) + ")", 
                "⏳", "#FF9800");

        Div overdueChequeCard = createStatCard("Vadesi Geçen Çekler", 
                summary.getOverdueChequeCount() + " Adet (" + currencyFormat.format(summary.getOverdueChequeTotal()) + ")", 
                "💸", "#C62828");

        Div upcomingChequeCard = createStatCard("Yaklaşan Çekler (7 Gün)", 
                summary.getUpcomingChequeCount() + " Adet (" + currencyFormat.format(summary.getUpcomingChequeTotal()) + ")", 
                "📅", "#2196F3");

        if (summaryLayout == null) {
            summaryLayout = new HorizontalLayout(overdueCard, upcomingCard, overdueChequeCard, upcomingChequeCard);
            summaryLayout.addClassName("summary-cards");
            summaryLayout.setWidthFull();
            summaryLayout.setSpacing(true);
            summaryLayout.getStyle().set("flex-wrap", "wrap");
            add(summaryLayout);
        } else {
            summaryLayout.removeAll();
            summaryLayout.add(overdueCard, upcomingCard, overdueChequeCard, upcomingChequeCard);
        }
    }

    private Div createStatCard(String title, String value, String icon, String color) {
        Div card = new Div();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "8px")
                .set("padding", "1em")
                .set("box-shadow", "0 2px 5px rgba(0,0,0,0.05)")
                .set("flex", "1")
                .set("border-left", "4px solid " + color);

        HorizontalLayout layout = new HorizontalLayout();
        layout.setAlignItems(Alignment.CENTER);

        Span iconSpan = new Span(icon);
        iconSpan.getStyle().set("font-size", "1.5em").set("color", color);

        VerticalLayout textLayout = new VerticalLayout();
        textLayout.setPadding(false);
        textLayout.setSpacing(false);

        Span titleEl = new Span(title);
        titleEl.getStyle().set("font-size", "0.85em").set("color", "var(--lumo-secondary-text-color)");

        Span valueSpan = new Span(value);
        valueSpan.getStyle().set("font-size", "1em").set("font-weight", "bold").set("color", color);

        textLayout.add(titleEl, valueSpan);
        layout.add(iconSpan, textLayout);
        card.add(layout);
        return card;
    }

    private void createTabs() {
        tabs.setWidthFull();
        tabs.addSelectedChangeListener(event -> {
            if (tabs.getSelectedTab().equals(installmentTab)) {
                showInstallmentsContent();
            } else {
                showChequesContent();
            }
        });
    }

    private void createGrids() {
        installmentGrid.removeAllColumns();
        chequeGrid.removeAllColumns();

        // Taksit Tablosu
        installmentGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        installmentGrid.setPageSize(20);
        installmentGrid.setSizeFull();

        installmentGrid.addColumn(entry -> entry.getExpense().getCard() != null ? entry.getExpense().getCard().getName() : "-")
                .setHeader("Kart Adı").setSortable(true).setAutoWidth(true);
        installmentGrid.addColumn(entry -> entry.getExpense().getDescription())
                .setHeader("Harcama").setSortable(true).setAutoWidth(true);
        installmentGrid.addColumn(entry -> currencyFormat.format(entry.getAmount()))
                .setHeader("Taksit Tutarı").setSortable(true).setAutoWidth(true);
        
        installmentGrid.addColumn(entry -> {
            return reminderService.calculateDueDate(entry).format(dateFormatter);
        }).setHeader("Son Ödeme Tarihi").setSortable(true).setAutoWidth(true);

        installmentGrid.addComponentColumn(entry -> {
            LocalDate dueDate = reminderService.calculateDueDate(entry);
            Span badge = new Span();
            java.time.LocalDate today = java.time.LocalDate.now();
            if (dueDate.isBefore(today)) {
                badge.setText("Gecikti");
                badge.getElement().getThemeList().add("badge error");
            } else if (dueDate.isEqual(today)) {
                badge.setText("Bugün");
                badge.getElement().getThemeList().add("badge warning");
            } else {
                badge.setText("Yaklaşıyor");
                badge.getElement().getThemeList().add("badge contrast");
            }
            return badge;
        }).setHeader("Durum").setAutoWidth(true);

        installmentGrid.addComponentColumn(entry -> {
            Button payBtn = new Button("Ödendi Yap", new Icon(VaadinIcon.CHECK), e -> {
                expenseService.markAsPaid(entry.getId());
                Notification.show("Taksit ödendi olarak işaretlendi.", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                refreshData();
            });
            payBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
            return payBtn;
        }).setHeader("İşlem").setAutoWidth(true);

        // Çek Tablosu
        chequeGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        chequeGrid.setPageSize(20);
        chequeGrid.setSizeFull();

        chequeGrid.addColumn(Cheque::getChequeNumber).setHeader("Çek No").setSortable(true).setAutoWidth(true);
        chequeGrid.addColumn(Cheque::getBank).setHeader("Banka").setSortable(true).setAutoWidth(true);
        chequeGrid.addColumn(c -> currencyFormat.format(c.getAmount())).setHeader("Tutar").setSortable(true).setAutoWidth(true);
        chequeGrid.addColumn(c -> c.getMaturityDate().format(dateFormatter)).setHeader("Vade Tarihi").setSortable(true).setAutoWidth(true);
        chequeGrid.addColumn(c -> c.getParty()).setHeader("Cari / Karşı Taraf").setSortable(true).setAutoWidth(true);

        chequeGrid.addComponentColumn(c -> {
            Span badge = new Span();
            java.time.LocalDate today = java.time.LocalDate.now();
            if (c.getMaturityDate().isBefore(today)) {
                badge.setText("Vadesi Geçti");
                badge.getElement().getThemeList().add("badge error");
            } else if (c.getMaturityDate().isEqual(today)) {
                badge.setText("Bugün");
                badge.getElement().getThemeList().add("badge warning");
            } else {
                badge.setText("Yaklaşıyor");
                badge.getElement().getThemeList().add("badge contrast");
            }
            return badge;
        }).setHeader("Durum").setAutoWidth(true);

        chequeGrid.addComponentColumn(c -> {
            Button clearBtn = new Button("Tahsil Et", new Icon(VaadinIcon.CHECK), e -> {
                c.setStatus(ChequeStatus.CLEARED);
                chequeService.save(c);
                Notification.show("Çek tahsil edildi olarak güncellendi.", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                refreshData();
            });
            clearBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            return clearBtn;
        }).setHeader("İşlem").setAutoWidth(true);

        chequeGrid.addItemClickListener(event -> showChequeDetailDialog(event.getItem()));
    }

    private void showChequeDetailDialog(Cheque cheque) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Çek Detayı: " + cheque.getChequeNumber());
        dialog.setWidth("450px");

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        TextField chequeNoField = new TextField("Çek No");
        chequeNoField.setValue(cheque.getChequeNumber() != null ? cheque.getChequeNumber() : "-");
        chequeNoField.setReadOnly(true);

        TextField bankField = new TextField("Banka");
        bankField.setValue(cheque.getBank() != null ? cheque.getBank() : "-");
        bankField.setReadOnly(true);

        TextField maturityField = new TextField("Vade Tarihi");
        maturityField.setValue(cheque.getMaturityDate() != null ? cheque.getMaturityDate().format(dateFormatter) : "-");
        maturityField.setReadOnly(true);

        TextField amountField = new TextField("Tutar");
        amountField.setValue(currencyFormat.format(cheque.getAmount()));
        amountField.setReadOnly(true);

        TextField partyField = new TextField("Cari / Karşı Taraf");
        partyField.setValue(cheque.getParty() != null ? cheque.getParty() : "-");
        partyField.setReadOnly(true);

        TextField descField = new TextField("Açıklama");
        descField.setValue(cheque.getDescription() != null ? cheque.getDescription() : "-");
        descField.setReadOnly(true);

        TextField typeField = new TextField("Tip");
        typeField.setValue(cheque.getType() != null ? cheque.getType().getLabel() : "-");
        typeField.setReadOnly(true);

        TextField statusField = new TextField("Durum");
        statusField.setValue(cheque.getStatus() != null ? cheque.getStatus().getLabel() : "-");
        statusField.setReadOnly(true);

        form.add(chequeNoField, bankField, typeField, maturityField, amountField, partyField, statusField, descField);

        Button closeBtn = new Button("Kapat", e -> dialog.close());
        closeBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);

        dialog.add(form);
        dialog.getFooter().add(closeBtn);
        dialog.open();
        dialog.getElement().getStyle().set("overflow", "hidden");
    }

    private void showInstallmentsContent() {
        contentDiv.removeAll();
        contentDiv.setSizeFull();

        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);

        H3 sectionTitle = new H3("Vadesi Geçen ve Yaklaşan Taksitler");
        sectionTitle.getStyle().set("margin-top", "10px");

        // Geciken ve yaklaşan taksitleri birleştirip grid'e yükleyelim
        java.util.List<InstallmentEntry> overdue = reminderService.getOverdueInstallments();
        java.util.List<InstallmentEntry> upcoming = reminderService.getUpcomingInstallments(7);
        java.util.List<InstallmentEntry> combined = new java.util.ArrayList<>();
        combined.addAll(overdue);
        combined.addAll(upcoming);

        installmentGrid.setItems(combined);

        Span emptyState = new Span("Yaklaşan veya geciken taksit bulunmuyor. Harika!");
        emptyState.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "1.1em")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("width", "100%")
                .set("padding", "3em 0")
                .set("margin", "auto");
        emptyState.setVisible(combined.isEmpty());
        installmentGrid.setVisible(!combined.isEmpty());

        layout.add(sectionTitle, installmentGrid, emptyState);
        contentDiv.add(layout);
    }

    private void showChequesContent() {
        contentDiv.removeAll();
        contentDiv.setSizeFull();

        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);

        H3 sectionTitle = new H3("Vadesi Geçen ve Yaklaşan Çekler");
        sectionTitle.getStyle().set("margin-top", "10px");

        java.util.List<Cheque> overdue = reminderService.getOverdueCheques();
        java.util.List<Cheque> upcoming = reminderService.getUpcomingCheques(7);
        java.util.List<Cheque> combined = new java.util.ArrayList<>();
        combined.addAll(overdue);
        combined.addAll(upcoming);

        chequeGrid.setItems(combined);

        Span emptyState = new Span("Yaklaşan veya geciken çek bulunmuyor. Harika!");
        emptyState.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "1.1em")
                .set("display", "flex")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("width", "100%")
                .set("padding", "3em 0")
                .set("margin", "auto");
        emptyState.setVisible(combined.isEmpty());
        chequeGrid.setVisible(!combined.isEmpty());

        layout.add(sectionTitle, chequeGrid, emptyState);
        contentDiv.add(layout);
    }

    private void refreshData() {
        loadingBar.setVisible(true);
        createSummaryCards();
        if (tabs.getSelectedTab() == null || tabs.getSelectedTab().equals(installmentTab)) {
            showInstallmentsContent();
        } else {
            showChequesContent();
        }
        loadingBar.setVisible(false);
    }
}
