package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.IntegerField;

import com.vaadin.flow.component.textfield.TextField;
import com.raspel.cardtracker.ui.utils.FormatUtils;
import com.raspel.cardtracker.ui.utils.HolidayUtils;
import com.raspel.cardtracker.ui.utils.TurkishDatePickerI18n;
import com.raspel.cardtracker.ui.utils.CategoryConstants;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.domain.card.CardService;
import com.raspel.cardtracker.domain.expense.Expense;
import com.raspel.cardtracker.domain.expense.ExpenseService;
import com.raspel.cardtracker.domain.expense.ExcelImportService;
import com.raspel.cardtracker.domain.expense.ExcelExportService;
import com.raspel.cardtracker.domain.expense.PdfExportService;
import com.raspel.cardtracker.domain.expense.InstallmentEntry;
import com.raspel.cardtracker.domain.contact.Contact;
import com.raspel.cardtracker.domain.contact.ContactService;
import com.raspel.cardtracker.domain.department.Department;
import com.raspel.cardtracker.domain.department.DepartmentService;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Route(value = "expenses", layout = MainLayout.class)
@PageTitle("Harcamalar")
@PermitAll
public class ExpenseView extends VerticalLayout {

    private final ExpenseService expenseService;
    private final CardService cardService;
    private final ContactService contactService;
    private final ExcelImportService excelImportService;
    private final ExcelExportService excelExportService;
    private final PdfExportService pdfExportService;
    private final DepartmentService departmentService;

    private final Grid<InstallmentEntry> grid = new Grid<>(InstallmentEntry.class, false);
    private final ComboBox<Integer> yearFilter = new ComboBox<>("Yıl");
    private final ComboBox<Integer> monthFilter = new ComboBox<>("Ay");
    private final ComboBox<Card> cardFilter = new ComboBox<>("Kart");
    private final TextField searchField = new TextField();
    private final ProgressBar loadingBar = new ProgressBar();
    private final Div emptyState = new Div();

    public ExpenseView(ExpenseService expenseService, CardService cardService, ContactService contactService,
                       ExcelImportService excelImportService, ExcelExportService excelExportService,
                       PdfExportService pdfExportService, DepartmentService departmentService) {
        this.expenseService = expenseService;
        this.cardService = cardService;
        this.contactService = contactService;
        this.excelImportService = excelImportService;
        this.excelExportService = excelExportService;
        this.pdfExportService = pdfExportService;
        this.departmentService = departmentService;

        addClassName("expense-view");
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);
        loadingBar.setWidthFull();

        configureGrid();
        configureFilters();

        HorizontalLayout toolbar = createToolbar();
        toolbar.addClassName("view-toolbar");
        HorizontalLayout filters = createFilters();

        add(loadingBar, toolbar, filters, grid);

        configureEmptyState();
        add(emptyState);

        refreshGrid();
    }

    private void configureEmptyState() {
        emptyState.getStyle()
                .set("display", "none")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("text-align", "center")
                .set("width", "100%")
                .set("padding-top", "15vh");
        Span emptyIcon = new Span("💰");
        emptyIcon.getStyle().set("font-size", "3em").set("display", "block");
        Span emptyText = new Span("Bu aya ait harcama bulunamadı.");
        emptyText.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "1.1em")
                .set("margin-top", "1em")
                .set("width", "100%")
                .set("text-align", "center");
        emptyState.add(emptyIcon, emptyText);
    }

    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setWidthFull();
        grid.setPageSize(30);

        grid.addColumn(entry -> entry.getExpense().getCard() != null ? entry.getExpense().getCard().getName() : "-")
                .setHeader("Kart").setSortable(true).setAutoWidth(true);

        grid.addColumn(entry -> {
            String desc = entry.getExpense().getDescription();
            if (entry.getExpense().getContact() != null) {
                desc += " (" + entry.getExpense().getContact().getName() + ")";
            }
            return desc.length() > 40 ? desc.substring(0, 40) + "..." : desc;
        }).setHeader("Açıklama").setSortable(true).setAutoWidth(true).setFlexGrow(1);

        grid.addColumn(entry -> FormatUtils.formatNumber(entry.getAmount()) + " ₺")
                .setHeader("Tutar").setSortable(true).setAutoWidth(true);

        grid.addColumn(entry -> {
            int total = entry.getExpense().getInstallments() != null ? entry.getExpense().getInstallments() : 1;
            int expYm = entry.getExpense().getExpenseDate().getYear() * 12 + entry.getExpense().getExpenseDate().getMonthValue();
            int dueYm = entry.getDueYear() * 12 + entry.getDueMonth();
            int idx = dueYm - expYm;
            return idx + "/" + total;
        }).setHeader("Taksit").setSortable(true).setAutoWidth(true);

        grid.addColumn(entry -> {
            if (entry.getExpense().getCard() == null) return "-";
            YearMonth ym = YearMonth.of(entry.getDueYear(), entry.getDueMonth());
            int cd = Math.min(entry.getExpense().getCard().getClosingDay(), ym.lengthOfMonth());
            return HolidayUtils.getNextBusinessDay(
                LocalDate.of(entry.getDueYear(), entry.getDueMonth(), cd)
                    .plusDays(entry.getExpense().getCard().getDueDay()))
                .format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        }).setHeader("Ödeme").setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(entry -> {
            Span badge = new Span(entry.getIsPaid() ? "Ödendi" : "Bekliyor");
            badge.getElement().getThemeList().add("badge " + (entry.getIsPaid() ? "success" : "error"));
            return badge;
        }).setHeader("Durum").setAutoWidth(true);

        grid.addComponentColumn(entry -> {
            String receiptPath = entry.getExpense().getReceiptPath();
            if (receiptPath != null && !receiptPath.trim().isEmpty()) {
                String validatedPath = validateReceiptPath(receiptPath);
                if (validatedPath == null) {
                    return new Span("-");
                }
                Anchor downloadAnchor = new Anchor();
                downloadAnchor.getElement().setAttribute("download", true);
                Button downloadBtn = new Button(new Icon(VaadinIcon.DOWNLOAD_ALT));
                downloadBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                downloadBtn.getElement().setAttribute("title", "İndir");
                downloadAnchor.add(downloadBtn);
                downloadAnchor.setHref(new StreamResource(
                    new java.io.File(validatedPath).getName(),
                    () -> { try { return java.nio.file.Files.newInputStream(java.nio.file.Paths.get(validatedPath)); }
                            catch (Exception ex) { return null; } }));

                Button previewBtn = new Button(new Icon(VaadinIcon.EYE));
                previewBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
                previewBtn.getElement().setAttribute("title", "Önizleme");
                previewBtn.addClickListener(e -> openReceiptPreview(validatedPath));

                HorizontalLayout actions = new HorizontalLayout(downloadAnchor, previewBtn);
                actions.setSpacing(false);
                return actions;
            }
            return new Span("-");
        }).setHeader("Fatura").setAutoWidth(true);

        // Ödendi/İptal butonu
        grid.addComponentColumn(entry -> {
            if (entry.getIsPaid()) {
                Button btn = new Button(new Icon(VaadinIcon.CLOSE_SMALL), e -> {
                    expenseService.markAsUnpaid(entry.getId()); refreshGrid(); });
                btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
                btn.getElement().setAttribute("title", "Ödemeyi Geri Al");
                return btn;
            }
            Button btn = new Button(new Icon(VaadinIcon.CHECK), e -> {
                expenseService.markAsPaid(entry.getId()); refreshGrid(); });
            btn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SUCCESS);
            btn.getElement().setAttribute("title", "Ödendi İşaretle");
            return btn;
        }).setHeader("").setWidth("50px").setFlexGrow(0);

        grid.addComponentColumn(entry -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT), e -> openExpenseDialog(entry.getExpense()));
            editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH), e -> deleteExpense(entry.getExpense()));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            HorizontalLayout layout = new HorizontalLayout(editBtn, deleteBtn);
            layout.setSpacing(true);
            return layout;
        }).setHeader("İşlemler").setAutoWidth(true);
    }

    private void configureFilters() {
        int currentYear = LocalDate.now().getYear();
        yearFilter.setItems(java.util.stream.IntStream.rangeClosed(currentYear - 1, currentYear + 10).boxed().toList());
        yearFilter.setValue(currentYear);
        yearFilter.setWidth("120px");
        yearFilter.addValueChangeListener(e -> refreshGrid());

        monthFilter.setItems(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12));
        monthFilter.setItemLabelGenerator(m -> {
            String[] months = {"", "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
                    "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};
            return months[m];
        });
        monthFilter.setValue(LocalDate.now().getMonthValue());
        monthFilter.setWidth("140px");
        monthFilter.addValueChangeListener(e -> refreshGrid());

        List<Card> cards = cardService.findAllActive();
        cardFilter.setItems(cards);
        cardFilter.setItemLabelGenerator(Card::getName);
        cardFilter.setPlaceholder("Tüm Kartlar");
        cardFilter.setClearButtonVisible(true);
        cardFilter.setWidth("200px");
        cardFilter.addValueChangeListener(e -> refreshGrid());
    }

    private HorizontalLayout createToolbar() {
        H3 title = new H3("Harcama Yönetimi");
        title.getStyle().set("margin", "0");

        Button addBtn = new Button("Yeni Harcama", new Icon(VaadinIcon.PLUS), e -> openExpenseDialog(null));
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button importBtn = new Button("Excel Import", new Icon(VaadinIcon.UPLOAD), e -> openImportDialog());
        importBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        // Şablon İndir Anchor ve Butonu
        Anchor templateAnchor = new Anchor();
        templateAnchor.getElement().setAttribute("download", true);
        Button templateBtn = new Button("Şablon İndir", new Icon(VaadinIcon.DOWNLOAD));
        templateBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        templateAnchor.add(templateBtn);

        StreamResource templateResource = new StreamResource(
                "harcama_sablonu.xlsx",
                () -> excelExportService.createSampleTemplate()
        );
        templateAnchor.setHref(templateResource);

        // Excel Export Anchor ve Butonu
        Anchor exportAnchor = new Anchor();
        exportAnchor.getElement().setAttribute("download", true);
        Button exportBtn = new Button("Excel Export", new Icon(VaadinIcon.DOWNLOAD));
        exportBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS);
        exportAnchor.add(exportBtn);

        StreamResource exportResource = new StreamResource(
                "harcamalar_raporu.xlsx",
                () -> {
                    Integer startYear = yearFilter.getValue();
                    Integer startMonth = monthFilter.getValue();
                    Card selectedCard = cardFilter.getValue();
                    Long cardId = selectedCard != null ? selectedCard.getId() : null;
                    List<InstallmentEntry> entries;
                    if (selectedCard != null) {
                        entries = expenseService.getInstallmentsForMonthAndCard(startYear, startMonth, selectedCard.getId());
                    } else {
                        entries = expenseService.getInstallmentsForMonth(startYear, startMonth);
                    }
                    return excelExportService.exportInstallments(entries);
                }
        );
        exportAnchor.setHref(exportResource);

        // PDF Export Anchor ve Butonu
        Anchor pdfExportAnchor = new Anchor();
        pdfExportAnchor.getElement().setAttribute("download", true);
        Button pdfExportBtn = new Button("PDF Raporu", new Icon(VaadinIcon.FILE_TEXT_O));
        pdfExportBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        pdfExportBtn.getStyle()
                .set("background-color", "#d32f2f")
                .set("color", "#ffffff"); 
        pdfExportAnchor.add(pdfExportBtn);

        StreamResource pdfExportResource = new StreamResource(
                "harcamalar_raporu.pdf",
                () -> {
                    Integer startYear = yearFilter.getValue();
                    Integer startMonth = monthFilter.getValue();
                    Card selectedCard = cardFilter.getValue();
                    Long cardId = selectedCard != null ? selectedCard.getId() : null;
                    List<InstallmentEntry> entries;
                    if (selectedCard != null) {
                        entries = expenseService.getInstallmentsForMonthAndCard(startYear, startMonth, selectedCard.getId());
                    } else {
                        entries = expenseService.getInstallmentsForMonth(startYear, startMonth);
                    }
                    return pdfExportService.exportInstallments(entries);
                }
        );
        pdfExportAnchor.setHref(pdfExportResource);

        HorizontalLayout toolbar = new HorizontalLayout(title, addBtn, importBtn, templateAnchor, exportAnchor, pdfExportAnchor);
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setWidthFull();
        toolbar.expand(title);

        return toolbar;
    }

    private HorizontalLayout createFilters() {
        searchField.setPlaceholder("Açıklama, kart adı veya kategori ara...");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setClearButtonVisible(true);
        searchField.setWidth("300px");
        searchField.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);
        searchField.setValueChangeTimeout(400);
        searchField.addValueChangeListener(e -> refreshGrid());

        HorizontalLayout filters = new HorizontalLayout(yearFilter, monthFilter, cardFilter, searchField);
        filters.setAlignItems(Alignment.END);

        Button resetBtn = new Button(new Icon(VaadinIcon.ERASER));
        resetBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        resetBtn.setAriaLabel("Filtreleri Sıfırla");
        resetBtn.addClickListener(e -> {
            int currentYear = LocalDate.now().getYear();
            yearFilter.setValue(currentYear);
            monthFilter.setValue(LocalDate.now().getMonthValue());
            cardFilter.clear();
            searchField.clear();
        });
        filters.add(resetBtn);

        // Toplam tutar gösterimi
        Div totalDiv = new Div();
        totalDiv.setId("total-display");
        totalDiv.getStyle()
                .set("padding", "0.5em 1em")
                .set("background", "var(--lumo-primary-color-10pct)")
                .set("border-radius", "8px")
                .set("font-weight", "bold")
                .set("display", "flex")
                .set("align-items", "center");

        filters.add(totalDiv);
        return filters;
    }

    void openExpenseDialog(Expense expenseToEdit) {
        Dialog dialog = new Dialog();
        boolean isEdit = expenseToEdit != null && expenseToEdit.getId() != null;
        dialog.setHeaderTitle(isEdit ? "Harcama Düzenle" : (expenseToEdit != null ? "Harcama Kopyala" : "Yeni Harcama"));
        dialog.setWidth("550px");

        FormLayout form = new FormLayout();

        ComboBox<Card> cardField = new ComboBox<>("Kart");
        cardField.setItems(cardService.findAllActive());
        cardField.setItemLabelGenerator(c -> c.getName() + " (" + c.getBank() + ")");
        cardField.setRequired(true);
        cardField.setWidthFull();

        ComboBox<Contact> contactField = new ComboBox<>("Cari (Opsiyonel)");
        contactField.setItems(contactService.findAll());
        contactField.setItemLabelGenerator(Contact::getName);
        contactField.setClearButtonVisible(true);
        contactField.setWidthFull();

        ComboBox<String> categoryField = new ComboBox<>("Kategori");
        categoryField.setItems(CategoryConstants.EXPENSE_CATEGORIES);
        categoryField.setAllowCustomValue(true);
        categoryField.addCustomValueSetListener(e -> categoryField.setValue(e.getDetail()));

        TextField descField = new TextField("Açıklama");
        descField.setRequired(true);
        descField.addBlurListener(e -> {
            if (categoryField.isEmpty()) {
                String suggested = expenseService.suggestCategory(descField.getValue());
                if (suggested != null && !suggested.isEmpty()) {
                    categoryField.setValue(suggested);
                }
            }
        });

        TextField amountField = new TextField("Tutar");
        amountField.setValue("0,00");
        amountField.setRequired(true);
        FormatUtils.attachCurrencyFormatting(amountField);

        ComboBox<String> currencyField = new ComboBox<>("Para Birimi");
        currencyField.setItems("TRY", "USD", "EUR");
        currencyField.setValue("TRY");
        currencyField.setRequired(true);

        IntegerField installmentField = new IntegerField("Taksit Sayısı");
        installmentField.setMin(1);
        installmentField.setMax(48);
        installmentField.setValue(1);
        installmentField.setStepButtonsVisible(true);

        DatePicker dateField = new DatePicker("Harcama Tarihi");
        dateField.setValue(LocalDate.now());
        dateField.setRequired(true);
        DatePicker.DatePickerI18n turkishI18n = TurkishDatePickerI18n.get();
        dateField.setI18n(turkishI18n);

        ComboBox<String> tagField = new ComboBox<>("Etiket");
        tagField.setItems(CategoryConstants.EXPENSE_TAGS);
        tagField.setClearButtonVisible(true);
        tagField.setWidthFull();

        // Fiş/Fatura Yükleme (Upload) Bölümü
        MemoryBuffer buffer = new MemoryBuffer();
        Upload uploadField = new Upload(buffer);
        uploadField.setAcceptedFileTypes("image/*", "application/pdf");
        uploadField.setMaxFiles(1);
        uploadField.setMaxFileSize(10 * 1024 * 1024); // 10MB
        uploadField.setDropLabel(new Span("Belgeyi buraya sürükleyin (PDF, PNG, JPG)"));

        final String[] uploadedPath = {null};
        final String[] uploadedContentType = {null};

        uploadField.addSucceededListener(event -> {
            try {
                String originalFileName = event.getFileName();
                String fileExtension = "";
                if (originalFileName.contains(".")) {
                    fileExtension = originalFileName.substring(originalFileName.lastIndexOf("."));
                }
                
                // uploads dizinini oluştur
                java.nio.file.Path uploadDir = java.nio.file.Paths.get("uploads");
                if (!java.nio.file.Files.exists(uploadDir)) {
                    java.nio.file.Files.createDirectories(uploadDir);
                }
                
                // Benzersiz bir dosya adı oluştur ve kaydet
                java.nio.file.Path targetPath = java.nio.file.Files.createTempFile(uploadDir, "receipt_", fileExtension);
                String mimeType = event.getMIMEType();
                if (mimeType != null && mimeType.startsWith("image/")) {
                    net.coobird.thumbnailator.Thumbnails.of(buffer.getInputStream())
                            .size(1024, 1024)
                            .outputQuality(0.7)
                            .toFile(targetPath.toFile());
                } else {
                    try (java.io.InputStream is = buffer.getInputStream();
                         java.io.OutputStream os = java.nio.file.Files.newOutputStream(targetPath)) {
                        is.transferTo(os);
                    }
                }
                
                uploadedPath[0] = targetPath.toAbsolutePath().toString();
                uploadedContentType[0] = event.getMIMEType();

            } catch (Exception ex) {
                Notification.show("Bir hata oluştu, lütfen tekrar deneyin.", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });

        // Form alanlarını doldur
        if (expenseToEdit != null) {
            cardField.setValue(expenseToEdit.getCard());
            contactField.setValue(expenseToEdit.getContact());
            descField.setValue(expenseToEdit.getDescription() != null ? expenseToEdit.getDescription() : "");
            amountField.setValue(FormatUtils.formatTurkishCurrency(expenseToEdit.getOriginalAmount()));
            currencyField.setValue(expenseToEdit.getCurrency() != null ? expenseToEdit.getCurrency() : "TRY");
            installmentField.setValue(expenseToEdit.getInstallments() != null ? expenseToEdit.getInstallments() : 1);
            dateField.setValue(expenseToEdit.getExpenseDate() != null ? expenseToEdit.getExpenseDate() : LocalDate.now());
            categoryField.setValue(expenseToEdit.getCategory());
            tagField.setValue(expenseToEdit.getTag());
            uploadedPath[0] = expenseToEdit.getReceiptPath();
            uploadedContentType[0] = expenseToEdit.getReceiptContentType();
        }

        // Form düzeni
        form.add(cardField, contactField, descField, amountField, currencyField, installmentField, dateField, categoryField, tagField);
        
        VerticalLayout formContainer = new VerticalLayout(form, new Span("Belge Eki (Opsiyonel / Akıllı Tarama)"), uploadField);
        formContainer.setPadding(false);

        Button saveBtn = new Button("Kaydet", e -> {
            if (cardField.getValue() == null || descField.getValue().isEmpty() || amountField.getValue().trim().isEmpty() || FormatUtils.parseTurkishCurrency(amountField.getValue()).compareTo(BigDecimal.ZERO) <= 0) {
                Notification.show("Lütfen geçerli bir tutar girin", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "unknown";

            Expense targetExpense;
            if (isEdit) {
                targetExpense = expenseToEdit;
            } else if (expenseToEdit != null) {
                targetExpense = expenseToEdit;
                targetExpense.setCreatedBy(username);
            } else {
                targetExpense = new Expense();
                targetExpense.setCreatedBy(username);
            }

            targetExpense.setCard(cardField.getValue());
            targetExpense.setContact(contactField.getValue());
            targetExpense.setDescription(descField.getValue());
            targetExpense.setOriginalAmount(FormatUtils.parseTurkishCurrency(amountField.getValue()));
            targetExpense.setCurrency(currencyField.getValue());
            targetExpense.setReceiptPath(uploadedPath[0]);
            targetExpense.setReceiptContentType(uploadedContentType[0]);
            targetExpense.setInstallments(installmentField.getValue() != null ? installmentField.getValue() : 1);
            targetExpense.setExpenseDate(dateField.getValue());
            targetExpense.setCategory(categoryField.getValue());
            targetExpense.setTag(tagField.getValue());

            if (isEdit) {
                try {
                    expenseService.updateExpense(targetExpense);
                } catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
                    com.vaadin.flow.component.UI.getCurrent().access(() -> {
                        com.vaadin.flow.component.notification.Notification.show("Veri başka bir kullanıcı tarafından değiştirilmiş. Lütfen sayfayı yenileyin.", 5000, com.vaadin.flow.component.notification.Notification.Position.MIDDLE).addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
                    });
                    return;
                }
                Notification.show(
                        "Harcama güncellendi. " + targetExpense.getInstallments() + " taksit yeniden oluşturuldu.",
                        4000, Notification.Position.BOTTOM_CENTER
                    ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } else {
                expenseService.createExpense(targetExpense);
                Notification.show(
                        "Harcama kaydedildi. " + targetExpense.getInstallments() + " taksit oluşturuldu.",
                        4000, Notification.Position.BOTTOM_CENTER
                    ).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            }

            Card savedCard = cardField.getValue();
            if (savedCard != null && savedCard.getCardLimit() != null && savedCard.getCardLimit().compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal unpaid = expenseService.getUnpaidBalance(savedCard.getId());
                if (unpaid.compareTo(savedCard.getCardLimit()) > 0) {
                    Notification.show("Uyarı: Kart limiti aşıldı! Mevcut borç: " + FormatUtils.formatNumber(unpaid) + " TL / Limit: " + FormatUtils.formatNumber(savedCard.getCardLimit()) + " TL",
                            5000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                }
            }

            dialog.close();
            refreshGrid();
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        Button cancelBtn = new Button("İptal", e -> dialog.close());
        cancelBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);

        dialog.add(formContainer);
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
        dialog.getElement().getStyle().set("overflow", "hidden");
    }


    private void openReceiptPreview(String receiptPath) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Fatura / Fiş Önizleme");
        dialog.setWidth("90vw");
        dialog.setHeight("85vh");

        String fileName = new java.io.File(receiptPath).getName().toLowerCase();
        boolean isImage = fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".jpeg");

        StreamResource resource = new StreamResource(new java.io.File(receiptPath).getName(),
                () -> { try { return java.nio.file.Files.newInputStream(java.nio.file.Paths.get(receiptPath)); }
                        catch (Exception ex) { return null; } });

        if (isImage) {
            com.vaadin.flow.component.html.Image img = new com.vaadin.flow.component.html.Image(resource, "Fatura");
            img.setWidth("100%");
            img.getStyle().set("object-fit", "contain").set("max-height", "75vh");
            dialog.add(img);
        } else {
            Anchor anchor = new Anchor(resource, "PDF'yi görüntülemek için tıklayın");
            anchor.setTarget("_blank");
            Span info = new Span("PDF dosyası yeni sekmede açılacaktır.");
            info.getStyle().set("color", "var(--lumo-secondary-text-color)");
            VerticalLayout v = new VerticalLayout(info, anchor);
            v.setAlignItems(Alignment.CENTER);
            v.setJustifyContentMode(JustifyContentMode.CENTER);
            v.setSizeFull();
            dialog.add(v);
        }

        Button closeBtn = new Button("Kapat", e -> dialog.close());
        closeBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);
        dialog.getFooter().add(closeBtn);
        dialog.open();
        dialog.getElement().getStyle().set("overflow", "hidden");
    }

    private void openImportDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Excel Import");
        dialog.setWidth("500px");

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false);

        Paragraph info = new Paragraph(
                "Excel dosyasının formatı: Kart Adı | Açıklama | Tutar | Taksit Sayısı | Tarih | Kategori");
        info.getStyle().set("color", "var(--lumo-secondary-text-color)");

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes(".xlsx", ".xls");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(10 * 1024 * 1024); // 10MB

        Div resultDiv = new Div();

        upload.addSucceededListener(event -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String username = auth != null ? auth.getName() : "unknown";
            ExcelImportService.ImportResult result = excelImportService.importFromExcel(
                    buffer.getInputStream(), username);

            StringBuilder sb = new StringBuilder();
            sb.append("✔ Başarılı: ").append(result.successCount()).append(" kayıt\n");
            sb.append("❌ Hatalı: ").append(result.errorCount()).append(" kayıt\n");

            if (!result.errors().isEmpty()) {
                sb.append("\nHatalar:\n");
                result.errors().forEach(error -> sb.append("• ").append(error).append("\n"));
            }

            resultDiv.setText(sb.toString());
            resultDiv.getStyle().set("white-space", "pre-wrap");

            Notification.show("Import tamamlandı: " + result.successCount() + " başarılı",
                            4000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            refreshGrid();
        });

        content.add(info, upload, resultDiv);
        dialog.add(content);

        Button closeBtn = new Button("Kapat", e -> dialog.close());
        closeBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);
        dialog.getFooter().add(closeBtn);
        dialog.open();
        dialog.getElement().getStyle().set("overflow", "hidden");
    }

    private void refreshGrid() {
        loadingBar.setVisible(true);
        Integer startYear = yearFilter.getValue();
        Integer startMonth = monthFilter.getValue();

        if (startYear == null || startMonth == null) {
            grid.setItems(java.util.Collections.emptyList());
            loadingBar.setVisible(false);
            showEmptyState();
            return;
        }

        Card selectedCard = cardFilter.getValue();
        String term = searchField.getValue() != null ? searchField.getValue().trim() : null;
        Long cardId = selectedCard != null ? selectedCard.getId() : null;

        List<InstallmentEntry> entries;
        if (cardId != null) {
            entries = expenseService.getInstallmentsForMonthAndCard(startYear, startMonth, cardId);
        } else {
            entries = expenseService.getInstallmentsForMonth(startYear, startMonth);
        }
        if (term != null && !term.isEmpty()) {
            String lowerTerm = term.toLowerCase();
            entries = entries.stream().filter(e ->
                (e.getExpense().getDescription() != null && e.getExpense().getDescription().toLowerCase().contains(lowerTerm)) ||
                (e.getExpense().getCategory() != null && e.getExpense().getCategory().toLowerCase().contains(lowerTerm)) ||
                (e.getExpense().getCard().getName() != null && e.getExpense().getCard().getName().toLowerCase().contains(lowerTerm)) ||
                (e.getExpense().getCard().getDepartment() != null && e.getExpense().getCard().getDepartment().getName().toLowerCase().contains(lowerTerm))
            ).collect(java.util.stream.Collectors.toList());
        }

        grid.setItems(entries);

        if (entries.isEmpty()) {
            showEmptyState();
        } else {
            showGrid();
        }

        BigDecimal total = entries.stream().map(InstallmentEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        getElement().executeJs(
                "var el = document.getElementById('total-display'); if(el) el.textContent = 'Toplam: ' + $0 + ' ₺'",
                FormatUtils.formatNumber(total)
        );
        loadingBar.setVisible(false);
    }

    private void showEmptyState() {
        grid.setVisible(false);
        emptyState.getStyle().set("display", "flex");
    }

    private void showGrid() {
        grid.setVisible(true);
        emptyState.getStyle().set("display", "none");
    }

    private void deleteExpense(Expense expense) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle("Harcama Silme Onayı");
        confirmDialog.add(new Span("\"" + expense.getDescription() + "\" harcamasını ve tüm taksitlerini silmek istediğinize emin misiniz?"));

        Button confirmBtn = new Button("Sil", e -> {
            expenseService.deleteExpense(expense.getId());
            Notification.show("Harcama silindi", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            confirmDialog.close();
            refreshGrid();
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        confirmBtn.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        Button cancelBtn = new Button("İptal", e -> confirmDialog.close());
        cancelBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);

        confirmDialog.getFooter().add(cancelBtn, confirmBtn);
        confirmDialog.open();
    }

    private String validateReceiptPath(String receiptPath) {
        if (receiptPath == null || receiptPath.trim().isEmpty()) return null;
        try {
            java.nio.file.Path path = java.nio.file.Paths.get(receiptPath).toRealPath();
            java.nio.file.Path uploadsDir = java.nio.file.Paths.get("uploads").toRealPath();
            if (!path.startsWith(uploadsDir)) {
                return null;
            }
            return path.toString();
        } catch (Exception ex) {
            return null;
        }
    }
}
