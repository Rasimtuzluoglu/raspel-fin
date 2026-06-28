package com.raspel.cardtracker.ui;

import com.raspel.cardtracker.domain.budget.Budget;
import com.raspel.cardtracker.domain.budget.BudgetService;
import com.raspel.cardtracker.domain.department.Department;
import com.raspel.cardtracker.domain.department.DepartmentService;
import com.raspel.cardtracker.domain.expense.ExpenseService;
import com.raspel.cardtracker.ui.utils.FormatUtils;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

@Route(value = "budgets", layout = MainLayout.class)
@PageTitle("Bütçe Yönetimi")
@PermitAll
public class BudgetView extends VerticalLayout {

    private final BudgetService budgetService;
    private final DepartmentService departmentService;
    private final ExpenseService expenseService;

    private final Grid<BudgetRow> grid = new Grid<>(BudgetRow.class, false);
    private final ComboBox<Integer> yearFilter = new ComboBox<>("Yıl");
    private final ComboBox<Integer> monthFilter = new ComboBox<>("Ay");

    private int currentYear;
    private int currentMonth;

    public BudgetView(BudgetService budgetService, DepartmentService departmentService, ExpenseService expenseService) {
        this.budgetService = budgetService;
        this.departmentService = departmentService;
        this.expenseService = expenseService;

        setSizeFull();
        YearMonth now = YearMonth.now();
        currentYear = now.getYear();
        currentMonth = now.getMonthValue();

        configureFilters();
        configureGrid();

        Button addButton = new Button("Yeni Bütçe", e -> openEditDialog(null));
        addButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button addDeptButton = new Button("Departman Ekle", e -> openDeptDialog());
        addDeptButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        Button delDeptButton = new Button("Departman Sil", e -> openDeleteDeptDialog());
        delDeptButton.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);

        HorizontalLayout toolbar = new HorizontalLayout(yearFilter, monthFilter, addButton, addDeptButton, delDeptButton);
        toolbar.setAlignItems(Alignment.BASELINE);

        add(toolbar, grid);
        refreshGrid();
    }

    private void configureFilters() {
        yearFilter.setItems(getYearRange());
        yearFilter.setValue(currentYear);
        monthFilter.setItems(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        monthFilter.setItemLabelGenerator(m -> getMonthName(m));
        monthFilter.setValue(currentMonth);

        yearFilter.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                currentYear = e.getValue();
                refreshGrid();
            }
        });
        monthFilter.addValueChangeListener(e -> {
            if (e.getValue() != null) {
                currentMonth = e.getValue();
                refreshGrid();
            }
        });
    }

    private List<Integer> getYearRange() {
        java.util.List<Integer> years = new java.util.ArrayList<>();
        int now = java.time.YearMonth.now().getYear();
        for (int y = now - 2; y <= now + 3; y++) {
            years.add(y);
        }
        return years;
    }

    private String getMonthName(int month) {
        return java.time.Month.of(month).getDisplayName(java.time.format.TextStyle.FULL, java.util.Locale.of("tr", "TR"));
    }

    private void configureGrid() {
        grid.addColumn(row -> row.getDepartment() != null ? row.getDepartment().getName() : "-")
                .setHeader("Departman")
                .setSortable(true);
        grid.addColumn(row -> FormatUtils.formatTL(row.getLimitAmount()))
                .setHeader("Limit")
                .setSortable(true);
        grid.addColumn(row -> FormatUtils.formatTL(row.getSpentAmount()))
                .setHeader("Harcanan")
                .setSortable(true);
        grid.addColumn(row -> {
            if (row.getLimitAmount().compareTo(BigDecimal.ZERO) == 0) return "%0";
            BigDecimal pct = row.getSpentAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .divide(row.getLimitAmount(), 1, RoundingMode.HALF_UP);
            return "%" + FormatUtils.formatDecimal(pct);
        }).setHeader("%");
        grid.addComponentColumn(row -> {
            ProgressBar bar = new ProgressBar();
            if (row.getLimitAmount().compareTo(BigDecimal.ZERO) > 0) {
                double ratio = row.getSpentAmount().doubleValue() / row.getLimitAmount().doubleValue();
                bar.setValue(Math.min(ratio, 1.0));
            } else {
                bar.setValue(0);
            }
            bar.setWidth("100px");
            return bar;
        }).setHeader("Durum");
        grid.addComponentColumn(row -> {
            if (row.getBudget().getId() != null) {
                Button editBtn = new Button("Düzenle", e -> openEditDialog(row.getBudget()));
                Button deleteBtn = new Button("Sil", e -> deleteBudget(row.getBudget()));
                deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR);
                return new HorizontalLayout(editBtn, deleteBtn);
            } else {
                Button addBtn = new Button("Bütçe Ekle", e -> {
                    Budget b = new Budget();
                    b.setDepartment(row.getDepartment());
                    b.setYear(currentYear);
                    b.setMonth(currentMonth);
                    openEditDialog(b);
                });
                addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
                return new HorizontalLayout(addBtn);
            }
        }).setHeader("İşlemler");

        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();
    }

    private void refreshGrid() {
        List<Budget> budgets = budgetService.findByYearAndMonth(currentYear, currentMonth);
        List<Department> departments = departmentService.findAllActive();

        List<BudgetRow> rows = new java.util.ArrayList<>();
        for (Department dept : departments) {
            Optional<Budget> existingBudget = budgets.stream()
                    .filter(b -> b.getDepartment() != null && b.getDepartment().getId().equals(dept.getId()))
                    .findFirst();

            BigDecimal spent = expenseService.getDepartmentSpentForMonth(dept.getId(), currentYear, currentMonth);

            if (existingBudget.isPresent()) {
                rows.add(new BudgetRow(existingBudget.get(), dept, existingBudget.get().getLimitAmount(), spent));
            } else {
                Budget emptyBudget = Budget.builder()
                        .department(dept)
                        .year(currentYear)
                        .month(currentMonth)
                        .limitAmount(BigDecimal.ZERO)
                        .build();
                rows.add(new BudgetRow(emptyBudget, dept, BigDecimal.ZERO, spent));
            }
        }

        grid.setItems(rows);
    }

    private void openEditDialog(Budget existing) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(existing != null ? "Bütçe Düzenle" : "Yeni Bütçe");

        ComboBox<Department> departmentCombo = new ComboBox<>("Departman");
        departmentCombo.setItems(departmentService.findAllActive());
        departmentCombo.setItemLabelGenerator(Department::getName);
        departmentCombo.setWidthFull();

        ComboBox<Integer> yearCombo = new ComboBox<>("Yıl");
        yearCombo.setItems(getYearRange());
        yearCombo.setWidthFull();

        ComboBox<Integer> monthCombo = new ComboBox<>("Ay");
        monthCombo.setItems(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);
        monthCombo.setItemLabelGenerator(this::getMonthName);
        monthCombo.setWidthFull();

        TextField amountField = new TextField("Limit Tutarı");
        amountField.setWidthFull();
        FormatUtils.attachCurrencyFormatting(amountField);

        TextField descriptionField = new TextField("Açıklama");
        descriptionField.setWidthFull();

        if (existing != null) {
            departmentCombo.setValue(existing.getDepartment());
            yearCombo.setValue(existing.getYear());
            monthCombo.setValue(existing.getMonth());
            amountField.setValue(FormatUtils.formatDecimal(existing.getLimitAmount()));
            descriptionField.setValue(existing.getDescription() != null ? existing.getDescription() : "");
            if (existing.getId() != null) {
                departmentCombo.setEnabled(false);
                yearCombo.setEnabled(false);
                monthCombo.setEnabled(false);
            }
        }

        FormLayout form = new FormLayout(departmentCombo, yearCombo, monthCombo, amountField, descriptionField);

        Button saveBtn = new Button("Kaydet", e -> {
            try {
                Budget budget = existing != null ? existing : new Budget();
                budget.setDepartment(departmentCombo.getValue());
                budget.setYear(yearCombo.getValue());
                budget.setMonth(monthCombo.getValue());
                String amountStr = amountField.getValue().replace(".", "").replace(",", ".");
                budget.setLimitAmount(new BigDecimal(amountStr.isEmpty() ? "0" : amountStr));
                budget.setDescription(descriptionField.getValue());
                budgetService.save(budget);
                dialog.close();
                refreshGrid();
                Notification.show("Bütçe kaydedildi", 3000, Notification.Position.TOP_CENTER);
            } catch (Exception ex) {
                Notification.show("Hata: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button cancelBtn = new Button("İptal", e -> dialog.close());
        cancelBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        dialog.getFooter().add(saveBtn, cancelBtn);
        dialog.add(form);
        dialog.open();
    }

    private void deleteBudget(Budget budget) {
        if (budget.getId() == null) {
            Notification.show("Kaydedilmemiş bütçe silinemez", 3000, Notification.Position.TOP_CENTER);
            return;
        }
        try {
            budgetService.delete(budget.getId());
            refreshGrid();
            Notification.show("Bütçe silindi", 3000, Notification.Position.TOP_CENTER);
        } catch (Exception ex) {
            Notification.show("Hata: " + ex.getMessage(), 5000, Notification.Position.TOP_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void openDeptDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Yeni Departman");
        dialog.setWidth("380px");

        TextField nameField = new TextField("Departman Adı");
        nameField.setWidthFull();

        Button saveBtn = new Button("Ekle", ev -> {
            String name = nameField.getValue().trim();
            if (name.isEmpty()) {
                Notification.show("Ad boş olamaz", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            departmentService.save(Department.builder().name(name).isActive(true).build());
            Notification.show("Departman eklendi", 2000, Notification.Position.MIDDLE)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            dialog.close();
            refreshGrid();
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        Button cancelBtn = new Button("İptal", ev -> dialog.close());
        cancelBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);

        dialog.add(nameField);
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
    }

    private void openDeleteDeptDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Departman Sil");
        dialog.setWidth("400px");

        ComboBox<Department> deptCombo = new ComboBox<>("Departman");
        deptCombo.setItems(departmentService.findAllActive());
        deptCombo.setItemLabelGenerator(Department::getName);
        deptCombo.setWidthFull();

        Span warning = new Span("Departman silindiğinde o departmana ait bütçeler de silinir. Bu işlem geri alınamaz.");
        warning.getStyle().set("font-size", "0.85em").set("color", "var(--lumo-error-color)");

        Button confirmBtn = new Button("Sil", ev -> {
            if (deptCombo.isEmpty()) {
                Notification.show("Lütfen bir departman seçin", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            Department dept = deptCombo.getValue();
            try {
                departmentService.delete(dept.getId());
                Notification.show("\"" + dept.getName() + "\" silindi", 2000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshGrid();
            } catch (Exception ex) {
                Notification.show("Hata: " + ex.getMessage(), 5000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);

        Button cancelBtn = new Button("İptal", ev -> dialog.close());

        dialog.add(warning, deptCombo);
        dialog.getFooter().add(cancelBtn, confirmBtn);
        dialog.open();
    }

    public static class BudgetRow {
        private final Budget budget;
        private final Department department;
        private final BigDecimal limitAmount;
        private final BigDecimal spentAmount;

        public BudgetRow(Budget budget, Department department, BigDecimal limitAmount, BigDecimal spentAmount) {
            this.budget = budget;
            this.department = department;
            this.limitAmount = limitAmount;
            this.spentAmount = spentAmount;
        }

        public Budget getBudget() { return budget; }
        public Department getDepartment() { return department; }
        public BigDecimal getLimitAmount() { return limitAmount; }
        public BigDecimal getSpentAmount() { return spentAmount; }
    }
}
