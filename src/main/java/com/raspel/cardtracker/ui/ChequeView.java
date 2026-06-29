package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
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
import com.vaadin.flow.data.provider.CallbackDataProvider;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import com.raspel.cardtracker.domain.cheque.Cheque;
import com.raspel.cardtracker.domain.cheque.ChequeService;
import com.raspel.cardtracker.domain.cheque.ChequeStatus;
import com.raspel.cardtracker.domain.cheque.ChequeType;
import com.raspel.cardtracker.domain.contact.Contact;
import com.raspel.cardtracker.domain.contact.ContactService;
import com.raspel.cardtracker.domain.contact.ContactType;
import com.raspel.cardtracker.domain.expense.ExcelExportService;
import com.raspel.cardtracker.domain.expense.PdfExportService;
import com.raspel.cardtracker.domain.audit.AuditLog;
import com.raspel.cardtracker.domain.audit.AuditLogService;
import com.raspel.cardtracker.ui.utils.FormatUtils;
import com.raspel.cardtracker.ui.utils.HolidayUtils;
import com.raspel.cardtracker.ui.utils.TurkishDatePickerI18n;
import jakarta.annotation.security.PermitAll;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Route(value = "cheques", layout = MainLayout.class)
@PageTitle("Çek Takibi")
@PermitAll
public class ChequeView extends VerticalLayout {

    private final ChequeService chequeService;
    private final ContactService contactService;
    private final AuditLogService auditLogService;
    private final ExcelExportService excelExportService;
    private final PdfExportService pdfExportService;

    private final Grid<Cheque> grid = new Grid<>(Cheque.class, false);
    private final TextField searchFilter = new TextField("Ara (Çek No, Banka, Karşı Taraf)");
    private final ComboBox<String> typeFilter = new ComboBox<>("Çek Tipi");
    private final ComboBox<ChequeStatus> statusFilter = new ComboBox<>("Durum");
    private final DatePicker startDateFilter = new DatePicker("Başlangıç Tarihi");
    private final DatePicker endDateFilter = new DatePicker("Bitiş Tarihi");

    private final Span totalIncomingSpan = new Span("0,00 ₺");
    private final Span totalOutgoingSpan = new Span("0,00 ₺");
    private final Div emptyState = new Div();

    public ChequeView(ChequeService chequeService, ContactService contactService,
                      AuditLogService auditLogService,
                      ExcelExportService excelExportService, PdfExportService pdfExportService) {
        this.chequeService = chequeService;
        this.contactService = contactService;
        this.auditLogService = auditLogService;
        this.excelExportService = excelExportService;
        this.pdfExportService = pdfExportService;

        addClassName("cheque-view");
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        add(createSummaryPanel());

        HorizontalLayout toolbar = createToolbar();
        toolbar.addClassName("view-toolbar");
        HorizontalLayout filters = createFiltersLayout();
        filters.addClassName("filters-layout");

        configureGrid();
        configureEmptyState();

        Div gridWrapper = new Div(grid, emptyState);
        gridWrapper.setSizeFull();
        gridWrapper.getStyle()
                .set("position", "relative")
                .set("overflow", "hidden");
        add(toolbar, filters, gridWrapper);

        loadData();
    }

    private void configureEmptyState() {
        emptyState.getStyle()
                .set("display", "none")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("position", "absolute")
                .set("top", "0")
                .set("left", "0")
                .set("right", "0")
                .set("bottom", "0")
                .set("z-index", "1");
        Span emptyIcon = new Span("\uD83D\uDCC4");
        emptyIcon.getStyle().set("font-size", "3em").set("display", "block");
        Span emptyText = new Span("Henüz çek kaydı bulunmuyor.");
        emptyText.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "1.1em")
                .set("margin-top", "1em");
        emptyState.add(emptyIcon, emptyText);
    }

    private HorizontalLayout createSummaryPanel() {
        HorizontalLayout panel = new HorizontalLayout();
        panel.setWidthFull();
        panel.setSpacing(true);

        Div incomingCard = createSummaryCard("Portföydeki Giriş Çekleri", totalIncomingSpan, "\uD83D\uDCE5", "#4CAF50");
        Div outgoingCard = createSummaryCard("Ödenecek Çıkış Çekleri", totalOutgoingSpan, "\uD83D\uDCE4", "#F44336");

        panel.add(incomingCard, outgoingCard);
        return panel;
    }

    private Div createSummaryCard(String title, Span valueSpan, String icon, String color) {
        Div card = new Div();
        card.getStyle()
                .set("background", "var(--lumo-base-color)")
                .set("border-radius", "12px")
                .set("padding", "1.2em 1.5em")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.08)")
                .set("flex", "1")
                .set("border-left", "4px solid " + color)
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "0.2em");

        HorizontalLayout header = new HorizontalLayout();
        header.setWidthFull();
        header.setAlignItems(Alignment.CENTER);

        Span iconSpan = new Span(icon);
        iconSpan.getStyle().set("font-size", "1.5em");

        H4 titleEl = new H4(title);
        titleEl.getStyle()
                .set("margin", "0")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "0.9em")
                .set("font-weight", "500");

        header.add(iconSpan, titleEl);
        header.expand(titleEl);

        valueSpan.getStyle()
                .set("font-size", "1.6em")
                .set("font-weight", "700")
                .set("color", color)
                .set("margin-top", "0.2em");

        card.add(header, valueSpan);
        return card;
    }

    private HorizontalLayout createToolbar() {
        H3 title = new H3("Çek Giriş ve Çıkış Takibi");
        title.getStyle().set("margin", "0");

        Button addIncomingBtn = new Button("Yeni Çek Girişi", new Icon(VaadinIcon.PLUS), e -> openEditDialog(null, ChequeType.ENTERING));
        addIncomingBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addIncomingBtn.getStyle().set("background-color", "#2E7D32");

        Button addOutgoingBtn = new Button("Yeni Çek Çıkışı", new Icon(VaadinIcon.PLUS), e -> openEditDialog(null, ChequeType.EXITING));
        addOutgoingBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        addOutgoingBtn.getStyle().set("background-color", "#C62828");

        Button excelBtn = new Button("Excel Export", new Icon(VaadinIcon.DOWNLOAD));
        excelBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        excelBtn.addClickListener(e -> exportExcel());

        Button pdfBtn = new Button("PDF Raporu", new Icon(VaadinIcon.FILE_TEXT_O));
        pdfBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        pdfBtn.getStyle()
                .set("background-color", "#d32f2f")
                .set("color", "#ffffff");
        pdfBtn.addClickListener(e -> exportPdf());

        HorizontalLayout toolbar = new HorizontalLayout(title, addIncomingBtn, addOutgoingBtn, excelBtn, pdfBtn);
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setWidthFull();
        toolbar.expand(title);

        return toolbar;
    }

    private void exportExcel() {
        List<Cheque> all = chequeService.findAll();
        StreamResource resource = new StreamResource(
                "cekler_raporu.xlsx",
                () -> excelExportService.exportCheques(all)
        );
        triggerDownload(resource);
    }

    private void exportPdf() {
        List<Cheque> all = chequeService.findAll();
        StreamResource resource = new StreamResource(
                "cekler_raporu.pdf",
                () -> pdfExportService.exportCheques(all)
        );
        triggerDownload(resource);
    }

    private void triggerDownload(StreamResource resource) {
        Anchor anchor = new Anchor(resource, "");
        anchor.getElement().setAttribute("download", true);
        anchor.getStyle().set("display", "none");
        add(anchor);
        anchor.getElement().executeJs("this.click(); setTimeout(() => this.remove(), 100)");
    }

    private HorizontalLayout createFiltersLayout() {
        searchFilter.setPlaceholder("Arama terimi girin...");
        searchFilter.setClearButtonVisible(true);
        searchFilter.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);
        searchFilter.setValueChangeTimeout(400);
        searchFilter.addValueChangeListener(e -> applyFilters());
        searchFilter.getStyle().set("flex-grow", "2");

        typeFilter.setItems("Hepsi", "Giriş Çekleri", "Çıkış Çekleri");
        typeFilter.setValue("Hepsi");
        typeFilter.addValueChangeListener(e -> applyFilters());

        statusFilter.setItems(ChequeStatus.values());
        statusFilter.setItemLabelGenerator(ChequeStatus::getLabel);
        statusFilter.setPlaceholder("Tüm Durumlar");
        statusFilter.setClearButtonVisible(true);
        statusFilter.addValueChangeListener(e -> applyFilters());

        DatePicker.DatePickerI18n turkishI18n = TurkishDatePickerI18n.get();
        startDateFilter.setI18n(turkishI18n);
        startDateFilter.setClearButtonVisible(true);
        startDateFilter.addValueChangeListener(e -> applyFilters());
        endDateFilter.setI18n(turkishI18n);
        endDateFilter.setClearButtonVisible(true);
        endDateFilter.addValueChangeListener(e -> applyFilters());

        HorizontalLayout filters = new HorizontalLayout(searchFilter, typeFilter, statusFilter, startDateFilter, endDateFilter);
        filters.setWidthFull();
        filters.setAlignItems(Alignment.END);

        Button resetBtn = new Button(new Icon(VaadinIcon.ERASER));
        resetBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        resetBtn.setAriaLabel("Filtreleri Sıfırla");
        resetBtn.addClickListener(e -> {
            searchFilter.clear();
            typeFilter.setValue("Hepsi");
            statusFilter.clear();
            startDateFilter.clear();
            endDateFilter.clear();
        });
        filters.add(resetBtn);

        return filters;
    }

    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setSizeFull();

        grid.addComponentColumn(cheque -> {
            Span badge = new Span(cheque.getType().getLabel());
            badge.getElement().getThemeList().add("badge " + (cheque.getType() == ChequeType.ENTERING ? "success" : "error"));
            return badge;
        }).setHeader("Tip").setAutoWidth(true);

        grid.addColumn(Cheque::getChequeNumber).setHeader("Çek No").setSortable(true).setAutoWidth(true);
        grid.addColumn(Cheque::getBank).setHeader("Banka").setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(cheque -> {
            LocalDate orig = cheque.getMaturityDate();
            LocalDate actual = HolidayUtils.getNextBusinessDay(orig);

            Span dateSpan = new Span(orig.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));

            if (!actual.equals(orig)) {
                dateSpan.getStyle().set("color", "var(--lumo-error-color)").set("font-weight", "500");

                Icon warning = new Icon(VaadinIcon.INFO_CIRCLE_O);
                warning.getStyle().set("font-size", "0.8em").set("color", "var(--lumo-error-color)").set("margin-left", "4px").set("vertical-align", "middle");
                warning.setSize("12px");
                warning.getElement().setAttribute("title", "Resmi Tatil / Hafta Sonu. Ödeme Günü: " + actual.format(DateTimeFormatter.ofPattern("dd.MM.yyyy")));

                HorizontalLayout item = new HorizontalLayout(dateSpan, warning);
                item.setSpacing(false);
                item.setAlignItems(Alignment.CENTER);
                return item;
            }
            return dateSpan;
        }).setHeader("Vade Tarihi").setAutoWidth(true);

        grid.addColumn(cheque -> FormatUtils.formatNumber(cheque.getAmount()) + " ₺")
                .setHeader("Tutar").setSortable(true).setAutoWidth(true);

        grid.addColumn(cheque -> cheque.getContact() != null ? cheque.getContact().getName() : cheque.getParty())
                .setHeader("Karşı Firma / Şahıs").setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(cheque -> {
            Span badge = new Span(cheque.getStatus().getLabel());
            String theme = "badge contrast";
            switch (cheque.getStatus()) {
                case PORTFOLIO -> theme = "badge warning";
                case CLEARED, PAID -> theme = "badge success";
                case CANCELLED -> theme = "badge error";
            }
            badge.getElement().getThemeList().add(theme);
            return badge;
        }).setHeader("Durum").setAutoWidth(true);

        grid.addColumn(Cheque::getDescription).setHeader("Açıklama").setAutoWidth(false).setWidth("350px");

        grid.addComponentColumn(cheque -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT), e -> openEditDialog(cheque, cheque.getType()));
            editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            Button historyBtn = new Button(new Icon(VaadinIcon.CLOCK), e -> openHistoryDialog(cheque));
            historyBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            historyBtn.getElement().setAttribute("title", "Geçmiş");

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH), e -> deleteCheque(cheque));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);

            return new HorizontalLayout(editBtn, historyBtn, deleteBtn);
        }).setHeader("İşlemler").setAutoWidth(true);
    }

    void openEditDialog(Cheque cheque, ChequeType defaultType) {
        Dialog dialog = new Dialog();
        boolean isNew = (cheque == null);
        dialog.setHeaderTitle(isNew ? (defaultType == ChequeType.ENTERING ? "Yeni Çek Girişi Ekle" : "Yeni Çek Çıkışı Ekle") : "Çek Düzenle");
        dialog.setMinWidth("350px");
        dialog.setMaxWidth("550px");
        dialog.setWidth("95vw");

        Cheque targetCheque = isNew ? Cheque.builder().type(defaultType).status(ChequeStatus.PORTFOLIO).build() : cheque;

        FormLayout form = new FormLayout();

        TextField chequeNoField = new TextField("Çek Numarası");
        chequeNoField.setRequired(true);
        chequeNoField.setMaxLength(50);

        TextField bankField = new TextField("Banka");
        bankField.setRequired(true);
        bankField.setMaxLength(100);

        DatePicker maturityField = new DatePicker("Vade Tarihi");
        maturityField.setRequired(true);
        DatePicker.DatePickerI18n turkishI18n = TurkishDatePickerI18n.get();
        maturityField.setI18n(turkishI18n);

        TextField amountField = new TextField("Tutar");
        amountField.setRequired(true);
        FormatUtils.attachCurrencyFormatting(amountField);

        ComboBox<Contact> contactField = new ComboBox<>("Karşı Taraf (Cari)");
        contactField.setItems(contactService.findAll());
        contactField.setItemLabelGenerator(Contact::getName);
        contactField.setRequired(true);

        Button quickAddContactBtn = new Button(new Icon(VaadinIcon.PLUS), e -> openQuickContactDialog(contactField));
        quickAddContactBtn.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
        quickAddContactBtn.getElement().setAttribute("title", "Hızlı Yeni Cari Ekle");

        HorizontalLayout contactRow = new HorizontalLayout(contactField, quickAddContactBtn);
        contactRow.setAlignItems(Alignment.END);
        contactRow.setSpacing(true);
        contactRow.setWidthFull();
        contactField.getStyle().set("flex-grow", "1");

        ComboBox<ChequeStatus> statusField = new ComboBox<>("Durum");
        if (defaultType == ChequeType.ENTERING) {
            statusField.setItems(ChequeStatus.PORTFOLIO, ChequeStatus.CLEARED, ChequeStatus.ENDORSED, ChequeStatus.CANCELLED);
        } else {
            statusField.setItems(ChequeStatus.PORTFOLIO, ChequeStatus.PAID, ChequeStatus.CANCELLED);
        }
        statusField.setItemLabelGenerator(ChequeStatus::getLabel);
        statusField.setRequired(true);

        TextField descField = new TextField("Açıklama");

        form.add(chequeNoField, bankField, maturityField, amountField, contactRow, statusField, descField);

        if (!isNew) {
            chequeNoField.setValue(cheque.getChequeNumber());
            bankField.setValue(cheque.getBank());
            maturityField.setValue(cheque.getMaturityDate());
            amountField.setValue(FormatUtils.formatTurkishCurrency(cheque.getAmount()));
            contactField.setValue(cheque.getContact());
            statusField.setValue(cheque.getStatus());
            descField.setValue(cheque.getDescription() != null ? cheque.getDescription() : "");
        } else {
            maturityField.setValue(LocalDate.now());
            amountField.setValue("0,00");
            statusField.setValue(ChequeStatus.PORTFOLIO);
        }

        Button saveBtn = new Button("Kaydet", e -> {
            if (chequeNoField.getValue().trim().isEmpty() || bankField.getValue().trim().isEmpty() ||
                    maturityField.getValue() == null || amountField.getValue().trim().isEmpty() ||
                    contactField.isEmpty() || statusField.getValue() == null) {
                Notification.show("Lütfen zorunlu alanları doldurun", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            String chequeNo = chequeNoField.getValue().trim();
            if (!chequeNo.matches("\\d{11}")) {
                Notification.show("Çek numarası tam olarak 11 rakam olmalıdır", 4000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            BigDecimal amountVal = FormatUtils.parseTurkishCurrency(amountField.getValue());
            if (amountVal.compareTo(BigDecimal.ZERO) <= 0) {
                Notification.show("Lütfen geçerli bir tutar girin", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            if (maturityField.getValue().isBefore(LocalDate.now())) {
                Notification.show("Vade tarihi geçmiş bir tarih olamaz", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            targetCheque.setChequeNumber(chequeNo);
            targetCheque.setBank(bankField.getValue().trim());
            targetCheque.setMaturityDate(maturityField.getValue());
            targetCheque.setAmount(amountVal);
            targetCheque.setContact(contactField.getValue());
            targetCheque.setParty(contactField.getValue().getName());
            targetCheque.setStatus(statusField.getValue());
            targetCheque.setDescription(descField.getValue().trim());

            if (isNew || !chequeNo.equals(cheque.getChequeNumber())) {
                Optional<Cheque> existing = chequeService.findByChequeNumber(chequeNo);
                if (existing.isPresent() && (isNew || !existing.get().getId().equals(cheque.getId()))) {
                    Notification.show("Bu çek numarası zaten kayıtlı", 3000, Notification.Position.BOTTOM_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
            }

            chequeService.save(targetCheque);
            Notification.show("Çek kaydedildi", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            dialog.close();
            loadData();
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        Button cancelBtn = new Button("İptal", e -> dialog.close());
        cancelBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);

        dialog.add(form);
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
        dialog.getElement().getStyle().set("overflow", "hidden");
    }

    private void openQuickContactDialog(ComboBox<Contact> parentField) {
        Dialog qDialog = new Dialog();
        qDialog.setHeaderTitle("Hızlı Cari Ekle");
        qDialog.setWidth("350px");

        FormLayout qForm = new FormLayout();
        TextField qName = new TextField("Cari Adı");
        qName.setRequired(true);

        ComboBox<ContactType> qType = new ComboBox<>("Cari Tipi");
        qType.setItems(ContactType.values());
        qType.setItemLabelGenerator(ContactType::getLabel);
        qType.setValue(ContactType.BOTH);
        qType.setRequired(true);

        qForm.add(qName, qType);

        Button qSave = new Button("Kaydet ve Seç", e -> {
            if (qName.isEmpty() || qType.isEmpty()) {
                Notification.show("Lütfen adı doldurun", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            Contact c = new Contact();
            c.setName(qName.getValue().trim());
            c.setType(qType.getValue());
            try {
                Contact saved = contactService.save(c);
                Notification.show("Cari başarıyla eklendi", 2000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                parentField.setItems(contactService.findAll());
                parentField.setValue(saved);
                qDialog.close();
            } catch (Exception ex) {
                Notification.show("Bir hata oluştu, lütfen tekrar deneyin.", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        qSave.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        qSave.addClickShortcut(com.vaadin.flow.component.Key.ENTER);
        Button qCancel = new Button("Vazgeç", e -> qDialog.close());
        qCancel.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);

        qDialog.add(qForm);
        qDialog.getFooter().add(qCancel, qSave);
        qDialog.open();
    }

    private void openHistoryDialog(Cheque cheque) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Çek Geçmişi - " + cheque.getChequeNumber());
        dialog.setMinWidth("350px");
        dialog.setMaxWidth("550px");
        dialog.setWidth("95vw");

        VerticalLayout timeline = new VerticalLayout();
        timeline.setPadding(true);
        timeline.setSpacing(false);
        timeline.getStyle().set("max-height", "400px").set("overflow-y", "auto");

        List<AuditLog> logs = auditLogService.findByEntityTypeAndEntityId("Çek", cheque.getId());

        if (logs.isEmpty()) {
            timeline.add(new Span("Bu çek için geçmiş kaydı bulunamadı."));
        } else {
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
            for (AuditLog log : logs) {
                Span entry = new Span(log.getCreatedAt().format(dtf) + " - " + log.getAction().getLabel().toUpperCase(java.util.Locale.forLanguageTag("tr-TR")));
                entry.getStyle()
                        .set("font-weight", "600")
                        .set("font-size", "0.9em")
                        .set("color", "var(--lumo-primary-color)")
                        .set("display", "block")
                        .set("margin-top", "0.6em");

                Span desc = new Span(log.getDescription());
                desc.getStyle()
                        .set("font-size", "0.85em")
                        .set("color", "var(--lumo-secondary-text-color)")
                        .set("display", "block")
                        .set("padding-left", "0.8em")
                        .set("border-left", "2px solid var(--lumo-contrast-20pct)")
                        .set("margin-left", "0.2em");

                timeline.add(entry, desc);
            }
        }

        Button closeBtn = new Button("Kapat", e -> dialog.close());
        closeBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        closeBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);

        dialog.add(timeline);
        dialog.getFooter().add(closeBtn);
        dialog.open();
        dialog.getElement().getStyle().set("overflow", "hidden");
    }

    private void deleteCheque(Cheque cheque) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle("Çek Silme Onayı");
        confirmDialog.add(new Span("\"" + cheque.getChequeNumber() + "\" numaralı çeki silmek istediğinize emin misiniz?"));

        Button confirmBtn = new Button("Sil", e -> {
            chequeService.delete(cheque.getId());
            Notification.show("Çek silindi", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            confirmDialog.close();
            loadData();
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        confirmBtn.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        Button cancelBtn = new Button("İptal", e -> confirmDialog.close());
        cancelBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);

        confirmDialog.getFooter().add(cancelBtn, confirmBtn);
        confirmDialog.open();
    }

    private void loadData() {
        final String term = searchFilter.getValue().trim();
        final String typeVal = typeFilter.getValue();
        final ChequeStatus statusVal = statusFilter.getValue();

        ChequeType type = null;
        if ("Giriş Çekleri".equals(typeVal)) type = ChequeType.ENTERING;
        else if ("Çıkış Çekleri".equals(typeVal)) type = ChequeType.EXITING;
        final ChequeType finalType = type;

        final LocalDate startDate = startDateFilter.getValue();
        final LocalDate endDate = endDateFilter.getValue();

        // Update portfolio sums from DB
        BigDecimal incomingPortfolioSum = chequeService.sumAmountByTypeAndStatus(ChequeType.ENTERING, ChequeStatus.PORTFOLIO);
        BigDecimal outgoingPortfolioSum = chequeService.sumAmountByTypeAndStatus(ChequeType.EXITING, ChequeStatus.PORTFOLIO);
        totalIncomingSpan.setText(FormatUtils.formatNumber(incomingPortfolioSum) + " ₺");
        totalOutgoingSpan.setText(FormatUtils.formatNumber(outgoingPortfolioSum) + " ₺");

        grid.setItems(new CallbackDataProvider<>(
                query -> {
                    var page = chequeService.findFilteredPaged(term, finalType, statusVal, startDate, endDate,
                            PageRequest.of(query.getPage(), query.getPageSize()));
                    return page.getContent().stream();
                },
                query -> {
                    var page = chequeService.findFilteredPaged(term, finalType, statusVal, startDate, endDate,
                            PageRequest.of(query.getPage(), query.getPageSize()));
                    return (int) page.getTotalElements();
                }
        ));
    }

    private void applyFilters() {
        loadData();
    }
}
