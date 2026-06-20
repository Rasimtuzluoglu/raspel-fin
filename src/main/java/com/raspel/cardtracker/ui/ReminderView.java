package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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

    public ReminderView(PaymentReminderService reminderService, ExpenseService expenseService, ChequeService chequeService, NoteService noteService) {
        this.reminderService = reminderService;
        this.expenseService = expenseService;
        this.chequeService = chequeService;
        this.noteService = noteService;

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);
        loadingBar.setWidthFull();
        add(loadingBar);

        createHeader();
        createNoteSection();
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

        summaryLayout = new HorizontalLayout(overdueCard, upcomingCard, overdueChequeCard, upcomingChequeCard);
        summaryLayout.addClassName("summary-cards");
        summaryLayout.setWidthFull();
        summaryLayout.setSpacing(true);
        summaryLayout.getStyle().set("flex-wrap", "wrap");
        add(summaryLayout);
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

        installmentGrid.addColumn(entry -> entry.getExpense().getCard().getName())
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

        layout.add(sectionTitle, installmentGrid);
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

        layout.add(sectionTitle, chequeGrid);
        contentDiv.add(layout);
    }

    private void refreshData() {
        loadingBar.setVisible(true);
        if (summaryLayout != null) {
            remove(summaryLayout);
        }
        createSummaryCards();
        if (tabs.getSelectedTab() == null || tabs.getSelectedTab().equals(installmentTab)) {
            showInstallmentsContent();
        } else {
            showChequesContent();
        }
        loadingBar.setVisible(false);
    }
}
