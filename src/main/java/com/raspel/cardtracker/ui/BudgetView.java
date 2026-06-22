package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.raspel.cardtracker.domain.budget.DepartmentBudget;
import com.raspel.cardtracker.domain.budget.DepartmentBudgetService;
import com.raspel.cardtracker.domain.department.Department;
import com.raspel.cardtracker.domain.department.DepartmentService;
import com.raspel.cardtracker.domain.card.CardService;
import com.raspel.cardtracker.ui.utils.FormatUtils;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Route(value = "budgets", layout = MainLayout.class)
@PageTitle("Bütçe Yönetimi")
@PermitAll
public class BudgetView extends VerticalLayout {

    private final DepartmentBudgetService budgetService;
    private final DepartmentService departmentService;
    private final CardService cardService;
    
    private final Grid<DepartmentBudget> grid = new Grid<>(DepartmentBudget.class, false);
    private final ProgressBar loadingBar = new ProgressBar();
    private final Div emptyState = new Div();
    public BudgetView(DepartmentBudgetService budgetService, CardService cardService, DepartmentService departmentService) {
        this.budgetService = budgetService;
        this.departmentService = departmentService;
        this.cardService = cardService;

        addClassName("budget-view");
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);
        loadingBar.setWidthFull();
        add(loadingBar);

        HorizontalLayout toolbar = createToolbar();
        toolbar.addClassName("view-toolbar");
        configureGrid();

        HorizontalLayout deptAddSection = createDepartmentAddSection();
        deptAddSection.getStyle().set("margin-bottom", "16px");

        add(toolbar, deptAddSection, grid);
        configureEmptyState();
        add(emptyState);
        refreshGrid();
    }

    private HorizontalLayout createDepartmentAddSection() {
        TextField deptNameField = new TextField();
        deptNameField.setPlaceholder("Departman adı girin");
        deptNameField.setWidth("250px");

        ComboBox<Department> deptSelect = new ComboBox<>();
        deptSelect.setItems(departmentService.findAllActive());
        deptSelect.setItemLabelGenerator(Department::getName);
        deptSelect.setPlaceholder("Silinecek departman");
        deptSelect.setWidth("250px");

        Button addBtn = new Button("Ekle", new Icon(VaadinIcon.PLUS), e -> {
            String name = deptNameField.getValue();
            if (name == null || name.trim().isEmpty()) {
                Notification.show("Departman adı boş olamaz", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            if (departmentService.findByName(name.trim()).isPresent()) {
                Notification.show("Bu isimde bir departman zaten mevcut", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            Department dept = Department.builder().name(name.trim()).isActive(true).build();
            departmentService.save(dept);
            Notification.show("Departman eklendi: " + name.trim(), 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            deptNameField.clear();
            deptSelect.setItems(departmentService.findAllActive());
            refreshGrid();
        });
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

        Button deleteBtn = new Button("Sil", new Icon(VaadinIcon.TRASH), e -> {
            Department selected = deptSelect.getValue();
            if (selected == null) {
                Notification.show("Lütfen bir departman seçin", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            departmentService.delete(selected.getId());
            Notification.show("Departman silindi: " + selected.getName(), 3000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            deptSelect.setItems(departmentService.findAllActive());
            deptSelect.clear();
            refreshGrid();
        });
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_SMALL);

        HorizontalLayout section = new HorizontalLayout(deptNameField, addBtn, deptSelect, deleteBtn);
        section.setAlignItems(Alignment.END);
        section.setSpacing(true);
        section.setWidthFull();
        section.getStyle().set("flex-wrap", "wrap");

        VerticalLayout deptListSection = new VerticalLayout();
        deptListSection.setPadding(false);
        deptListSection.setSpacing(false);
        Span deptListTitle = new Span("Mevcut Departmanlar:");
        deptListTitle.getStyle().set("font-weight", "600").set("font-size", "0.9em").set("color", "var(--lumo-secondary-text-color)");
        deptListSection.add(deptListTitle);
        List<Department> departments = departmentService.findAllActive();
        if (departments.isEmpty()) {
            deptListSection.add(new Span("Henüz departman eklenmemiş."));
        } else {
            for (Department d : departments) {
                Span deptSpan = new Span("• " + d.getName());
                deptSpan.getStyle().set("font-size", "0.85em").set("padding", "0.2em 0");
                deptListSection.add(deptSpan);
            }
        }

        VerticalLayout wrapper = new VerticalLayout(section, deptListSection);
        wrapper.setPadding(false);
        wrapper.setSpacing(true);

        HorizontalLayout result = new HorizontalLayout(wrapper);
        result.setWidthFull();
        return result;
    }

    private HorizontalLayout createToolbar() {
        H3 title = new H3("Departman Aylık Bütçe Limitleri");
        title.getStyle().set("margin", "0");

        Button addBtn = new Button("Yeni Bütçe Tanımla", new Icon(VaadinIcon.PLUS), e -> openEditDialog(null));
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(title, addBtn);
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setWidthFull();
        toolbar.expand(title);
        return toolbar;
    }

    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setSizeFull();

        grid.addColumn(b -> b.getDepartment() != null ? b.getDepartment().getName() : "-").setHeader("Departman").setSortable(true).setAutoWidth(true);
        grid.addColumn(DepartmentBudget::getBudgetYear).setHeader("Yıl").setSortable(true).setAutoWidth(true);
        grid.addColumn(b -> {
            String[] months = {"", "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
                    "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};
            return months[b.getBudgetMonth()];
        }).setHeader("Ay").setSortable(true).setAutoWidth(true);

        grid.addColumn(b -> FormatUtils.formatNumber(b.getBudgetLimit()) + " ₺")
                .setHeader("Bütçe Limiti").setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(budget -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT), e -> openEditDialog(budget));
            editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH), e -> deleteBudget(budget));
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);

            HorizontalLayout layout = new HorizontalLayout(editBtn, deleteBtn);
            layout.setSpacing(true);
            return layout;
        }).setHeader("İşlemler").setAutoWidth(true);
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
        Span emptyIcon = new Span("\uD83D\uDCB0");
        emptyIcon.getStyle().set("font-size", "3em").set("display", "block");
        Span emptyText = new Span("Henüz bütçe kaydı bulunmuyor.");
        emptyText.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "1.1em")
                .set("margin-top", "1em");
        emptyState.add(emptyIcon, emptyText);
    }

    private void refreshGrid() {
        loadingBar.setVisible(true);
        List<DepartmentBudget> budgets = budgetService.findAll();
        grid.setItems(budgets);
        if (budgets.isEmpty()) {
            grid.setVisible(false);
            emptyState.getStyle().set("display", "flex");
        } else {
            grid.setVisible(true);
            emptyState.getStyle().set("display", "none");
        }
        loadingBar.setVisible(false);
    }

    private void openEditDialog(DepartmentBudget budgetToEdit) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(budgetToEdit == null ? "Yeni Bütçe Tanımla" : "Bütçe Düzenle");
        dialog.setWidth("450px");

        FormLayout form = new FormLayout();

        ComboBox<Department> deptField = new ComboBox<>("Departman");
        deptField.setItems(departmentService.findAllActive());
        deptField.setItemLabelGenerator(Department::getName);
        deptField.setRequired(true);
        deptField.setWidthFull();

        int currentYear = LocalDate.now().getYear();
        ComboBox<Integer> yearField = new ComboBox<>("Yil");
        yearField.setItems(java.util.stream.IntStream.rangeClosed(currentYear - 2, currentYear + 10).boxed().toList());
        yearField.setValue(currentYear);
        yearField.setRequired(true);

        ComboBox<Integer> monthField = new ComboBox<>("Ay");
        monthField.setItems(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12));
        monthField.setItemLabelGenerator(m -> {
            String[] months = {"", "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
                    "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"};
            return months[m];
        });
        monthField.setValue(LocalDate.now().getMonthValue());
        monthField.setRequired(true);

        TextField limitField = new TextField("Bütçe Limiti (TL)");
        limitField.setValue("0,00");
        limitField.setRequired(true);
        FormatUtils.attachCurrencyFormatting(limitField);

        form.add(deptField, yearField, monthField, limitField);

        if (budgetToEdit != null) {
            deptField.setValue(budgetToEdit.getDepartment());
            yearField.setValue(budgetToEdit.getBudgetYear());
            monthField.setValue(budgetToEdit.getBudgetMonth());
            limitField.setValue(FormatUtils.formatTurkishCurrency(budgetToEdit.getBudgetLimit()));
        }

        Button saveBtn = new Button("Kaydet", e -> {
            if (deptField.isEmpty() || yearField.isEmpty() || monthField.isEmpty() || limitField.isEmpty()) {
                Notification.show("Lütfen tüm alanları doldurun!", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            BigDecimal limitVal = FormatUtils.parseTurkishCurrency(limitField.getValue());
            if (limitVal.compareTo(BigDecimal.ZERO) <= 0) {
                Notification.show("Bütçe limiti 0'dan büyük olmalıdır!", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            Department dept = deptField.getValue();
            if (dept == null) {
                Notification.show("Lütfen bir departman seçin!", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            Integer yr = yearField.getValue();
            Integer mn = monthField.getValue();

            var existing = budgetService.findByDepartmentAndYearAndMonth(dept.getId(), yr, mn);
            if (existing.isPresent() && (budgetToEdit == null || !existing.get().getId().equals(budgetToEdit.getId()))) {
                Notification.show("Bu departman ve tarih için zaten bir bütçe tanımlanmış!", 4000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            DepartmentBudget budget = budgetToEdit != null ? budgetToEdit : new DepartmentBudget();
            budget.setDepartment(dept);
            budget.setBudgetYear(yr);
            budget.setBudgetMonth(mn);
            budget.setBudgetLimit(limitVal);

            try {
                budgetService.save(budget);
                Notification.show("Bütçe kaydedildi.", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshGrid();
            } catch (Exception ex) {
                Notification.show("Bir hata oluştu, lütfen tekrar deneyin.", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        Button cancelBtn = new Button("İptal", e -> dialog.close());
        cancelBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);
        dialog.getFooter().add(cancelBtn, saveBtn);

        dialog.add(form);
        dialog.open();
    }

    private void deleteBudget(DepartmentBudget budget) {
        Dialog confirm = new Dialog();
        confirm.setHeaderTitle("Bütçeyi Sil");
        confirm.add(new Span("Seçilen departman bütçesi silinecektir. Emin misiniz?"));

        Button yesBtn = new Button("Evet, Sil", e -> {
            try {
                budgetService.delete(budget.getId());
                Notification.show("Bütçe silindi.", 3000, Notification.Position.BOTTOM_END)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                confirm.close();
                refreshGrid();
            } catch (Exception ex) {
                Notification.show("Bir hata oluştu, lütfen tekrar deneyin.", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        yesBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        Button noBtn = new Button("Vazgeç", e -> confirm.close());
        confirm.getFooter().add(noBtn, yesBtn);
        confirm.open();
    }
}
