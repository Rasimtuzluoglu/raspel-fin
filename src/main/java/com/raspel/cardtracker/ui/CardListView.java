package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.raspel.cardtracker.ui.utils.FormatUtils;
import com.raspel.cardtracker.ui.utils.CategoryConstants;
import com.raspel.cardtracker.ui.utils.HolidayUtils;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.domain.card.CardType;
import com.raspel.cardtracker.domain.card.CardService;
import com.raspel.cardtracker.domain.expense.Expense;
import com.raspel.cardtracker.domain.department.Department;
import com.raspel.cardtracker.domain.department.DepartmentService;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import com.vaadin.flow.data.renderer.ComponentRenderer;

@Route(value = "cards", layout = MainLayout.class)
@PageTitle("Kartlar")
@PermitAll
public class CardListView extends VerticalLayout {

    private final CardService cardService;
    private final DepartmentService departmentService;
    private final com.raspel.cardtracker.domain.expense.ExpenseService expenseService;
    private final Grid<Card> grid = new Grid<>(Card.class, false);
    private final TextField searchField = new TextField();
    private final Checkbox showInactive = new Checkbox("Pasif Kartları Göster");
    private final Div emptyState = new Div();
    private final ProgressBar loadingBar = new ProgressBar();
    private boolean isAdmin;
    private java.util.Map<Long, BigDecimal> unpaidBalanceCache = new java.util.HashMap<>();

    public CardListView(CardService cardService, com.raspel.cardtracker.domain.expense.ExpenseService expenseService, DepartmentService departmentService) {
        this.cardService = cardService;
        this.departmentService = departmentService;
        this.expenseService = expenseService;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        this.isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        addClassName("card-list-view");
        setSizeFull();
        setPadding(true);
        setSpacing(false);
        getStyle().set("overflow", "hidden");

        configureGrid();

        Div topSpacer = new Div();
        topSpacer.setHeight("48px");
        topSpacer.setWidthFull();

        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);
        loadingBar.setWidthFull();
        loadingBar.setHeight("3px");
        loadingBar.getStyle().set("margin", "0");
        add(loadingBar);

        HorizontalLayout toolbar = createToolbar();
        toolbar.addClassName("view-toolbar");
        toolbar.getStyle()
            .set("margin-bottom", "32px")
            .set("margin-top", "0");

        add(topSpacer, toolbar, grid);

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
                .set("flex", "1")
                .set("margin", "auto")
                .set("width", "100%");
        Span emptyIcon = new Span("💳");
        emptyIcon.getStyle().set("font-size", "3em").set("display", "block");
        Span emptyText = new Span("Henüz kart eklenmemiş. Yeni Kart butonuyla başlayın!");
        emptyText.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "1.1em")
                .set("margin-top", "1em");
        emptyState.add(emptyIcon, emptyText);
    }

    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setWidthFull();
        grid.setPageSize(25);

        grid.addItemDoubleClickListener(e -> openExpenseDetailsDialog(e.getItem()));

        grid.addComponentColumn(card -> {
            Div colorDot = new Div();
            colorDot.getStyle()
                    .set("width", "12px")
                    .set("height", "12px")
                    .set("border-radius", "50%")
                    .set("background-color", card.getColor() != null ? card.getColor() : "#1976D2");
            return colorDot;
        }).setHeader("").setWidth("30px").setFlexGrow(0);

        grid.addColumn(Card::getName).setHeader("Kart Adı").setSortable(true).setAutoWidth(true).setFlexGrow(1);
        grid.addColumn(Card::getBank).setHeader("Banka").setSortable(true).setAutoWidth(true);
        grid.addColumn(card -> card.getHolderName() != null ? card.getHolderName() : "-").setHeader("Kart Sahibi").setSortable(true).setAutoWidth(true);
        grid.addColumn(card -> FormatUtils.formatNumber(card.getCardLimit()) + " TL").setHeader("Limit").setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(card -> {
            BigDecimal unpaid = unpaidBalanceCache.getOrDefault(card.getId(), BigDecimal.ZERO);
            BigDecimal limit = card.getCardLimit();
            double pct = 0;
            if (limit != null && limit.compareTo(BigDecimal.ZERO) > 0) {
                pct = unpaid.divide(limit, 4, java.math.RoundingMode.HALF_UP).doubleValue() * 100;
            }
            if (pct > 100) pct = 100;
            String barColor = pct >= 80.0 ? "var(--lumo-error-color)" : "var(--lumo-success-color)";
            Span pctSpan = new Span(String.format("%.0f%%", pct));
            pctSpan.getStyle().set("font-weight", "600").set("font-size", "0.85em").set("color", barColor);
            return pctSpan;
        }).setHeader("Kullanım %").setAutoWidth(true);

        grid.addColumn(card -> String.format("Ayın %02d. günü +%d", card.getClosingDay(), card.getDueDay()))
                .setHeader("Kesim / Vade").setSortable(true).setAutoWidth(true);
        grid.addColumn(card -> {
            int closingDay = card.getClosingDay() != null ? card.getClosingDay() : 1;
            int dueDay = card.getDueDay() != null ? card.getDueDay() : 10;
            java.time.LocalDate today = java.time.LocalDate.now();
            java.time.YearMonth ym = java.time.YearMonth.from(today);
            int daysInMonth = ym.lengthOfMonth();
            int rawClosing = Math.min(closingDay, daysInMonth);
            java.time.LocalDate statementDate = java.time.LocalDate.of(ym.getYear(), ym.getMonthValue(), rawClosing);
            if (!today.isBefore(statementDate)) {
                statementDate = statementDate.plusMonths(1);
            }
            java.time.LocalDate originalDueDate = statementDate.plusDays(dueDay);
            java.time.LocalDate actualDueDate = HolidayUtils.getNextBusinessDay(originalDueDate);
            return actualDueDate.format(java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy"));
        }).setHeader("Son Ödeme Tarihi").setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(card -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT), e -> openEditDialog(card));
            editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            editBtn.getElement().setAttribute("title", "Duzenle");

            boolean isPassive = card.getActive() != null && !card.getActive();

            if (isPassive) {
                Button activateBtn = new Button(new Icon(VaadinIcon.REFRESH), e -> {
                    cardService.activate(card.getId());
                    Notification.show("Kart aktifleştirildi", 3000, Notification.Position.BOTTOM_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    refreshGrid();
                });
                activateBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SUCCESS);
                activateBtn.getElement().setAttribute("title", "Aktifleştir");

                Button hardDeleteBtn = new Button(new Icon(VaadinIcon.CLOSE_CIRCLE), e -> hardDeleteCard(card));
                hardDeleteBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
                hardDeleteBtn.getElement().setAttribute("title", "Tamamen Sil");

                return new HorizontalLayout(editBtn, activateBtn, hardDeleteBtn);
            }

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH), e -> deleteCard(card));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            deleteBtn.getElement().setAttribute("title", "Sil");

            return new HorizontalLayout(editBtn, deleteBtn);
        }).setHeader("İşlemler").setAutoWidth(true);
    }

    private void openExpenseDetailsDialog(Card card) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(card.getName() + " - Harcamalar");
        dialog.setWidth("800px");

        Grid<Expense> expenseGrid = new Grid<>(Expense.class, false);
        expenseGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        expenseGrid.setSizeFull();
        expenseGrid.setHeight("400px");

        expenseGrid.addColumn(Expense::getExpenseDate).setHeader("Tarih").setSortable(true).setAutoWidth(true);
        expenseGrid.addColumn(e -> FormatUtils.formatNumber(e.getTotalAmount()) + " " + e.getCurrency()).setHeader("Tutar").setSortable(true).setAutoWidth(true);
        expenseGrid.addColumn(Expense::getDescription).setHeader("Açıklama").setSortable(true).setAutoWidth(true);
        expenseGrid.addColumn(Expense::getCategory).setHeader("Kategori").setSortable(true).setAutoWidth(true);
        expenseGrid.addColumn(e -> e.getCard().getDepartment() != null ? e.getCard().getDepartment().getName() : "-").setHeader("Departman").setSortable(true).setAutoWidth(true);

        List<Expense> expenses = expenseService.findByCardId(card.getId());
        expenseGrid.setItems(expenses);

        Button closeBtn = new Button("Kapat", e -> dialog.close());
        closeBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);
        dialog.getFooter().add(closeBtn);
        dialog.add(expenseGrid);
        dialog.open();
        dialog.getElement().getStyle().set("overflow", "hidden");
    }

    private HorizontalLayout createToolbar() {
        H3 title = new H3("Kartlar");
        title.getStyle().set("margin", "0");

        searchField.setPlaceholder("Kart adı, banka, departman veya sahip ara...");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setClearButtonVisible(true);
        searchField.setWidth("350px");
        searchField.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);
        searchField.setValueChangeTimeout(400);
        searchField.addValueChangeListener(e -> refreshGrid());

        showInactive.addValueChangeListener(e -> refreshGrid());

        Button addBtn = new Button("Yeni Kart", new Icon(VaadinIcon.PLUS), e -> openEditDialog(null));
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(title, searchField, showInactive, addBtn);
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setWidthFull();
        toolbar.setSpacing(true);
        toolbar.expand(title);
        searchField.getStyle().set("margin-right", "8px");

        return toolbar;
    }

    void openEditDialog(Card card) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(card == null ? "Yeni Kart Ekle" : "Kart Düzenle");
        dialog.setWidth("500px");

        Card editCard = card != null ? card : Card.builder().build();

        FormLayout form = new FormLayout();

        TextField nameField = new TextField("Kart Adı");
        nameField.setRequired(true);
        TextField bankField = new TextField("Banka");
        bankField.setRequired(true);
        
        ComboBox<CardType> cardTypeField = new ComboBox<>("Kart Tipi");
        cardTypeField.setItems(CardType.values());
        cardTypeField.setItemLabelGenerator(CardType::getDisplayName);
        cardTypeField.setRequired(true);

        TextField holderNameField = new TextField("Kart Sahibi");

        ComboBox<Department> deptField = new ComboBox<>("Departman");
        deptField.setItems(departmentService.findAllActive());
        deptField.setItemLabelGenerator(Department::getName);
        deptField.setClearButtonVisible(true);

        TextField limitField = new TextField("Limit (TL)");
        limitField.setRequired(true);
        limitField.setValue("0,00");
        FormatUtils.attachCurrencyFormatting(limitField);

        TextField monthlyAssignmentField = new TextField("Aylık Atama Tutarı");
        monthlyAssignmentField.setPlaceholder("Örn: 5000 (Banka/Ön Ödemeli içindir)");
        FormatUtils.attachCurrencyFormatting(monthlyAssignmentField);

        ComboBox<String> categoryField = new ComboBox<>("Kategori");
        categoryField.setItems(CategoryConstants.CARD_CATEGORIES);
        categoryField.setAllowCustomValue(true);
        categoryField.addCustomValueSetListener(e -> categoryField.setValue(e.getDetail()));

        ComboBox<ColorOption> colorField = new ComboBox<>("Renk");
        List<ColorOption> colorOptions = Arrays.asList(
            new ColorOption("Mavi", "#1976D2"),
            new ColorOption("Yeşil", "#2E7D32"),
            new ColorOption("Kırmızı", "#D32F2F"),
            new ColorOption("Turuncu", "#E65100"),
            new ColorOption("Gri", "#607D8B"),
            new ColorOption("Mor", "#7B1FA2"),
            new ColorOption("Sarı", "#FBC02D"),
            new ColorOption("Turkuaz", "#00796B")
        );
        colorField.setItems(colorOptions);
        colorField.setItemLabelGenerator(ColorOption::getName);
        colorField.setRenderer(new ComponentRenderer<>(colorOption -> {
            HorizontalLayout itemLayout = new HorizontalLayout();
            itemLayout.setAlignItems(Alignment.CENTER);
            itemLayout.setSpacing(true);

            Div colorDot = new Div();
            colorDot.getStyle()
                    .set("width", "12px")
                    .set("height", "12px")
                    .set("border-radius", "50%")
                    .set("background-color", colorOption.getHex())
                    .set("flex-shrink", "0");

            Span nameSpan = new Span(colorOption.getName());

            itemLayout.add(colorDot, nameSpan);
            return itemLayout;
        }));

        IntegerField closingDayField = new IntegerField("Hesap Kesim Günü");
        closingDayField.setMin(1);
        closingDayField.setMax(31);
        closingDayField.setStepButtonsVisible(true);

        IntegerField dueDayField = new IntegerField("Ödeme Vadesi (Kesimden N gün sonra)");
        dueDayField.setMin(1);
        dueDayField.setMax(30);
        dueDayField.setValue(10);
        dueDayField.setStepButtonsVisible(true);

        IntegerField warningThresholdField = new IntegerField("Limit Uyarı Eşiği (%)");
        warningThresholdField.setMin(10);
        warningThresholdField.setMax(100);
        warningThresholdField.setValue(80);
        warningThresholdField.setStepButtonsVisible(true);
        warningThresholdField.setHelperText("Kart limitinin yüzde kaçı dolunca uyarı gelsin?");

        form.add(nameField, bankField, cardTypeField, holderNameField, deptField, limitField, monthlyAssignmentField, categoryField, colorField, closingDayField, dueDayField, warningThresholdField);

        if (card != null) {
            nameField.setValue(card.getName() != null ? card.getName() : "");
            bankField.setValue(card.getBank() != null ? card.getBank() : "");
            cardTypeField.setValue(card.getCardType() != null ? card.getCardType() : CardType.CREDIT_CARD);
            holderNameField.setValue(card.getHolderName() != null ? card.getHolderName() : "");
            limitField.setValue(FormatUtils.formatTurkishCurrency(card.getCardLimit()));
            monthlyAssignmentField.setValue(card.getMonthlyAssignment() != null ? FormatUtils.formatTurkishCurrency(card.getMonthlyAssignment()) : "");
            categoryField.setValue(card.getCategory());
            
            String currentHex = card.getColor() != null ? card.getColor() : "#1976D2";
            ColorOption selectedOption = colorOptions.stream()
                .filter(opt -> opt.getHex().equalsIgnoreCase(currentHex))
                .findFirst()
                .orElseGet(() -> new ColorOption("Ozel (" + currentHex + ")", currentHex));
            colorField.setValue(selectedOption);
            
            closingDayField.setValue(card.getClosingDay() != null ? card.getClosingDay() : 1);
            dueDayField.setValue(card.getDueDay() != null ? card.getDueDay() : 10);
            warningThresholdField.setValue(card.getLimitWarningThreshold() != null ? card.getLimitWarningThreshold() : 80);
            deptField.setValue(card.getDepartment());
        } else {
            colorField.setValue(colorOptions.get(0)); // Default to first
            cardTypeField.setValue(CardType.CREDIT_CARD);
        }

        Button saveBtn = new Button("Kaydet", e -> {
            if (nameField.getValue() == null || nameField.getValue().trim().isEmpty()) {
                Notification.show("Kart adı zorunludur", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (bankField.getValue() == null || bankField.getValue().trim().isEmpty()) {
                Notification.show("Banka adı zorunludur", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (limitField.getValue() == null || limitField.getValue().trim().isEmpty() || FormatUtils.parseTurkishCurrency(limitField.getValue()).compareTo(BigDecimal.ZERO) <= 0) {
                Notification.show("Geçerli bir limit girin", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            editCard.setName(nameField.getValue());
            editCard.setBank(bankField.getValue());
            editCard.setCardType(cardTypeField.getValue());
            editCard.setHolderName(holderNameField.getValue());
            editCard.setDepartment(deptField.getValue());
            editCard.setCardLimit(FormatUtils.parseTurkishCurrency(limitField.getValue()));
            
            String assignmentVal = monthlyAssignmentField.getValue();
            if (assignmentVal != null && !assignmentVal.trim().isEmpty()) {
                editCard.setMonthlyAssignment(FormatUtils.parseTurkishCurrency(assignmentVal));
            } else {
                editCard.setMonthlyAssignment(null);
            }
            
            editCard.setCategory(categoryField.getValue());
            ColorOption selectedColor = colorField.getValue();
            editCard.setColor(selectedColor != null ? selectedColor.getHex() : "#1976D2");
            editCard.setClosingDay(closingDayField.getValue() != null ? closingDayField.getValue() : 1);
            editCard.setDueDay(dueDayField.getValue() != null ? dueDayField.getValue() : 10);
            editCard.setLimitWarningThreshold(warningThresholdField.getValue() != null ? warningThresholdField.getValue() : 80);

            String newName = nameField.getValue().trim();
            boolean duplicate = cardService.existsByNameIgnoreCaseAndIdNot(newName, editCard.getId());
            if (duplicate) {
                Notification.show("Bu isimde bir kart zaten mevcut", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                cardService.save(editCard);
            } catch (org.springframework.orm.ObjectOptimisticLockingFailureException ex) {
                com.vaadin.flow.component.UI.getCurrent().access(() -> {
                    com.vaadin.flow.component.notification.Notification.show("Veri başka bir kullanıcı tarafından değiştirilmiş. Lütfen sayfayı yenileyin.", 5000, com.vaadin.flow.component.notification.Notification.Position.MIDDLE).addThemeVariants(com.vaadin.flow.component.notification.NotificationVariant.LUMO_ERROR);
                });
                return;
            }
            Notification.show("Kart kaydedildi", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            dialog.close();
            refreshGrid();
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

    private void deleteCard(Card card) {
        long expenseCount = expenseService.countByCardId(card.getId());

        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle("Kart Silme Onayı");

        if (expenseCount > 0) {
            confirmDialog.add(new Span("\"" + card.getName() + "\" kartına ait " + expenseCount +
                    " harcama bulunuyor. Kartı silerseniz harcamalar sahipsiz kalacaktır."));
            confirmDialog.add(new Span("Tüm harcamalarıyla birlikte silmek için pasif duruma geldiğinde \"Tamamen Sil\" seçeneğini kullanabilirsiniz."));
        } else {
            confirmDialog.add(new Span("\"" + card.getName() + "\" kartını silmek istediğinize emin misiniz?"));
        }

        Button confirmBtn = new Button("Sil", e -> {
            cardService.delete(card.getId());
            Notification.show("Kart silindi", 3000, Notification.Position.BOTTOM_CENTER)
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

    private void hardDeleteCard(Card card) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle("Kartı Tamamen Sil");
        confirmDialog.add(new Span("\"" + card.getName() + "\" kartını ve tüm harcamalarını kalıcı olarak silmek istediğinize emin misiniz? Bu işlem geri alınamaz!"));

        Button confirmBtn = new Button("Kalıcı Olarak Sil", e -> {
            try {
                cardService.hardDelete(card.getId());
                Notification.show("Kart tamamen silindi", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            } catch (Exception ex) {
                Notification.show("Bir hata oluştu, lütfen tekrar deneyin.", 4000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
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

    private void refreshGrid() {
        loadingBar.setVisible(true);
        unpaidBalanceCache = expenseService.getUnpaidBalancesGroupedByCard();
        List<Card> allCards = showInactive.getValue() ? cardService.findAll() : cardService.findAllActive();
        String term = searchField.getValue() != null ? searchField.getValue().trim().toLowerCase(java.util.Locale.forLanguageTag("tr-TR")) : "";

        if (!term.isEmpty()) {
            allCards = allCards.stream().filter(card -> {
                boolean matchName = card.getName() != null && card.getName().toLowerCase(java.util.Locale.forLanguageTag("tr-TR")).contains(term);
                boolean matchBank = card.getBank() != null && card.getBank().toLowerCase(java.util.Locale.forLanguageTag("tr-TR")).contains(term);
                boolean matchDept = card.getDepartment() != null && card.getDepartment().getName().toLowerCase(java.util.Locale.forLanguageTag("tr-TR")).contains(term);
                boolean matchHolder = card.getHolderName() != null && card.getHolderName().toLowerCase(java.util.Locale.forLanguageTag("tr-TR")).contains(term);
                return matchName || matchBank || matchDept || matchHolder;
            }).collect(java.util.stream.Collectors.toList());
        }

        // Akıllı sıralama: Önce banka adı, sonra kart adı
        allCards.sort((c1, c2) -> {
            String b1 = c1.getBank() != null ? c1.getBank() : "";
            String b2 = c2.getBank() != null ? c2.getBank() : "";
            int bankCmp = b1.compareToIgnoreCase(b2);
            if (bankCmp != 0) return bankCmp;
            
            String n1 = c1.getName() != null ? c1.getName() : "";
            String n2 = c2.getName() != null ? c2.getName() : "";
            return n1.compareToIgnoreCase(n2);
        });

        if (allCards.isEmpty()) {
            grid.setVisible(false);
            emptyState.getStyle().set("display", "flex");
        } else {
            grid.setVisible(true);
            emptyState.getStyle().set("display", "none");
        }
        grid.setItems(allCards);
        loadingBar.setVisible(false);
    }

    public static class ColorOption {
        private final String name;
        private final String hex;

        public ColorOption(String name, String hex) {
            this.name = name;
            this.hex = hex;
        }

        public String getName() {
            return name;
        }

        public String getHex() {
            return hex;
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
