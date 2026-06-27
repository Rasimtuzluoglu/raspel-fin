package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
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
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.raspel.cardtracker.domain.budget.DepartmentBudget;
import com.raspel.cardtracker.domain.budget.DepartmentBudgetService;
import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.domain.card.CardService;
import com.raspel.cardtracker.domain.department.Department;
import com.raspel.cardtracker.domain.department.DepartmentService;
import com.raspel.cardtracker.domain.employee.Employee;
import com.raspel.cardtracker.domain.employee.EmployeeService;
import com.raspel.cardtracker.domain.expense.Expense;
import com.raspel.cardtracker.domain.expense.ExpenseService;
import com.raspel.cardtracker.ui.utils.FormatUtils;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

@Route(value = "budgets", layout = MainLayout.class)
@PageTitle("Bütçe Yönetimi")
@PermitAll
public class BudgetView extends VerticalLayout {

    private final DepartmentBudgetService budgetService;
    private final DepartmentService departmentService;
    private final CardService cardService;
    private final ExpenseService expenseService;
    private final EmployeeService employeeService;

    private final VerticalLayout cardsContainer = new VerticalLayout();
    private final Div emptyState = new Div();
    private int selectedYear = LocalDate.now().getYear();
    private final H3 titleEl = new H3("Bütçe Yönetimi");

    public BudgetView(DepartmentBudgetService budgetService, CardService cardService,
                      DepartmentService departmentService, ExpenseService expenseService,
                      EmployeeService employeeService) {
        this.budgetService = budgetService;
        this.departmentService = departmentService;
        this.cardService = cardService;
        this.expenseService = expenseService;
        this.employeeService = employeeService;

        addClassName("budget-view");
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        titleEl.getStyle().set("margin-top", "0");

        HorizontalLayout toolbar = new HorizontalLayout(titleEl, createYearSelector(), createAddSection());
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.expand(titleEl);

        configureEmptyState();
        cardsContainer.setPadding(false);
        cardsContainer.setSpacing(false);
        cardsContainer.getStyle().set("gap", "16px");

        // Department spending chart (6 month trend)
        Div chartSection = createDeptChartSection();
        chartSection.setVisible(false);

        add(toolbar, chartSection, cardsContainer, emptyState);
        refreshAll();
    }

    private ComboBox<Integer> createYearSelector() {
        int yr = LocalDate.now().getYear();
        ComboBox<Integer> cb = new ComboBox<>();
        cb.setItems(java.util.stream.IntStream.rangeClosed(yr - 2, yr + 5).boxed().toList());
        cb.setValue(yr);
        cb.setWidth("100px");
        cb.addValueChangeListener(e -> { selectedYear = e.getValue() != null ? e.getValue() : yr; refreshAll(); });
        return cb;
    }

    private HorizontalLayout createAddSection() {
        Button newBudgetBtn = new Button("Yeni Bütçe", new Icon(VaadinIcon.PLUS), e -> openBudgetDialog(null));
        newBudgetBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button deptBtn = new Button("Departman Ekle", new Icon(VaadinIcon.PLUS), e -> openDeptDialog());
        deptBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return new HorizontalLayout(newBudgetBtn, deptBtn);
    }

    private void openDeptDialog() {
        Dialog d = new Dialog(); d.setHeaderTitle("Yeni Departman"); d.setWidth("400px");
        FormLayout f = new FormLayout();
        TextField name = new TextField("Departman Adı"); name.setWidthFull();
        TextField desc = new TextField("Açıklama"); desc.setWidthFull();
        f.add(name, desc);
        Button save = new Button("Ekle", ev -> {
            String n = name.getValue().trim();
            if (n.isEmpty()) { Notification.show("Ad boş olamaz", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR); return; }
            departmentService.save(Department.builder().name(n).description(desc.getValue().trim()).isActive(true).build());
            Notification.show("Eklendi", 2000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            d.close(); refreshAll();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("İptal", ev -> d.close());
        d.add(f); d.getFooter().add(cancel, save);
        d.open(); d.getElement().getStyle().set("overflow", "hidden");
    }

    private void openBudgetDialog(DepartmentBudget edit) {
        Dialog d = new Dialog();
        d.setHeaderTitle(edit == null ? "Yeni Bütçe" : "Bütçe Düzenle"); d.setWidth("420px");
        FormLayout f = new FormLayout();

        ComboBox<Department> deptCb = new ComboBox<>("Departman");
        deptCb.setItems(departmentService.findAllActive());
        deptCb.setItemLabelGenerator(Department::getName);
        deptCb.setWidthFull();

        int yr = LocalDate.now().getYear();
        ComboBox<Integer> yearCb = new ComboBox<>("Yıl");
        yearCb.setItems(java.util.stream.IntStream.rangeClosed(yr - 2, yr + 10).boxed().toList());
        yearCb.setValue(selectedYear); yearCb.setWidthFull();

        ComboBox<Integer> monthCb = new ComboBox<>("Ay");
        monthCb.setItems(1,2,3,4,5,6,7,8,9,10,11,12);
        monthCb.setValue(LocalDate.now().getMonthValue()); monthCb.setWidthFull();

        TextField limitF = new TextField("Limit (₺)");
        limitF.setValue("0,00"); FormatUtils.attachCurrencyFormatting(limitF); limitF.setWidthFull();

        ComboBox<String> catCb = new ComboBox<>("Kategori");
        catCb.setItems("Genel", "Personel", "Ekipman", "Yazılım", "Seyahat", "Pazarlama", "Ofis", "Diğer");
        catCb.setAllowCustomValue(true);
        catCb.addCustomValueSetListener(ev -> catCb.setValue(ev.getDetail()));
        catCb.setClearButtonVisible(true);

        if (edit != null) {
            deptCb.setValue(edit.getDepartment());
            yearCb.setValue(edit.getBudgetYear());
            monthCb.setValue(edit.getBudgetMonth());
            limitF.setValue(FormatUtils.formatTurkishCurrency(edit.getBudgetLimit()));
            catCb.setValue(edit.getCategory());
        }

        f.add(deptCb, yearCb, monthCb, limitF, catCb);

        Button save = new Button("Kaydet", ev -> {
            if (deptCb.isEmpty() || yearCb.isEmpty() || monthCb.isEmpty() || limitF.isEmpty()) {
                Notification.show("Tüm alanları doldurun", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR); return;
            }
            BigDecimal lim = FormatUtils.parseTurkishCurrency(limitF.getValue());
            if (lim.compareTo(BigDecimal.ZERO) <= 0) {
                Notification.show("Limit > 0 olmalı", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR); return;
            }
            Department dept = deptCb.getValue();
            var ex = budgetService.findByDepartmentAndYearAndMonth(dept.getId(), yearCb.getValue(), monthCb.getValue());
            if (ex.isPresent() && (edit == null || !ex.get().getId().equals(edit.getId()))) {
                Notification.show("Bu ay için zaten bütçe var", 4000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR); return;
            }
            DepartmentBudget b = edit != null ? edit : new DepartmentBudget();
            b.setDepartment(dept); b.setBudgetYear(yearCb.getValue()); b.setBudgetMonth(monthCb.getValue());
            b.setBudgetLimit(lim); b.setCategory(catCb.getValue());
            try {
                budgetService.save(b);
                Notification.show("Kaydedildi", 2000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                d.close(); refreshAll();
            } catch (Exception exc) {
                Notification.show("Kayıt başarısız: " + (exc.getMessage() != null ? exc.getMessage() : "Bilinmeyen hata"), 4000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("İptal", ev -> d.close());
        d.add(f); d.getFooter().add(cancel, save);
        d.open(); d.getElement().getStyle().set("overflow", "hidden");
    }

    private void refreshAll() {
        cardsContainer.removeAll();
        int mn = selectedYear == LocalDate.now().getYear() ? LocalDate.now().getMonthValue() : 12;

        // Load budgets for either all months or just current month
        List<DepartmentBudget> allBudgets = new ArrayList<>();
        for (int m = 1; m <= mn; m++) {
            allBudgets.addAll(budgetService.findByYearAndMonth(selectedYear, m));
        }

        titleEl.setText("Bütçe Yönetimi (" + selectedYear + ")");

        if (allBudgets.isEmpty()) {
            emptyState.getStyle().set("display", "flex");
            cardsContainer.setVisible(false);
            return;
        }

        emptyState.getStyle().set("display", "none");
        cardsContainer.setVisible(true);

        // Build department summary cards (aggregated by department)
        Map<Department, List<DepartmentBudget>> grouped = new LinkedHashMap<>();
        for (DepartmentBudget b : allBudgets) {
            grouped.computeIfAbsent(b.getDepartment(), k -> new ArrayList<>()).add(b);
        }

        Map<String, BigDecimal> spentCache = new HashMap<>();
        for (Department dept : grouped.keySet()) {
            String dn = dept.getName();
            if (!spentCache.containsKey(dn)) {
                BigDecimal total = BigDecimal.ZERO;
                for (DepartmentBudget b : grouped.get(dept)) {
                    total = total.add(expenseService.getDepartmentSpentForMonth(dn, b.getBudgetYear(), b.getBudgetMonth()));
                }
                spentCache.put(dn, total);
            }
            cardsContainer.add(buildDeptSummaryCard(dept, grouped.get(dept), spentCache.get(dn)));
        }
    }

    private VerticalLayout buildDeptSummaryCard(Department dept, List<DepartmentBudget> budgets, BigDecimal ytdSpent) {
        BigDecimal totalLimit = budgets.stream().map(DepartmentBudget::getBudgetLimit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = totalLimit.subtract(ytdSpent);
        boolean over = remaining.compareTo(BigDecimal.ZERO) < 0;
        double pct = totalLimit.compareTo(BigDecimal.ZERO) > 0 ? Math.min(100, ytdSpent.doubleValue() / totalLimit.doubleValue() * 100) : 0;

        Div card = new Div();
        card.getStyle()
                .set("border-radius", "14px").set("padding", "20px 24px")
                .set("background", over ? "var(--lumo-error-color-10pct)" : "var(--lumo-base-color)")
                .set("border", over ? "2px solid var(--lumo-error-color)" : "1px solid var(--lumo-contrast-10pct)")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.06)");

        // Header
        H4 nameEl = new H4(dept.getName());
        nameEl.getStyle().set("margin", "0").set("font-size", "1.1em");
        if (dept.getDescription() != null && !dept.getDescription().isEmpty()) {
            Span descSpan = new Span(dept.getDescription());
            descSpan.getStyle().set("font-size", "0.75em").set("color", "var(--lumo-secondary-text-color)");
            nameEl.add(descSpan);
        }

        // Stats row
        HorizontalLayout stats = new HorizontalLayout();
        stats.setWidthFull(); stats.setSpacing(true); stats.getStyle().set("margin-top", "12px");

        stats.add(statBox("Toplam Bütçe", FormatUtils.formatNumber(totalLimit) + " ₺", "#2196F3"));
        stats.add(statBox("Kullanılan", FormatUtils.formatNumber(ytdSpent) + " ₺", over ? "var(--lumo-error-color)" : "#FF9800"));
        stats.add(statBox("Kalan", FormatUtils.formatNumber(remaining.abs()) + " ₺", over ? "var(--lumo-error-color)" : "#4CAF50"));
        stats.add(statBox("Kart", String.valueOf(cardService.findAllActive().stream().filter(c -> c.getDepartment() != null && c.getDepartment().getId().equals(dept.getId())).count()), "#9C27B0"));

        // Progress bar
        Div bar = new Div();
        bar.getStyle().set("height", "12px").set("border-radius", "6px").set("background", "var(--lumo-contrast-10pct)").set("margin-top", "12px").set("overflow", "hidden");
        Div fill = new Div();
        fill.getStyle().set("height", "100%").set("width", pct + "%").set("background", over ? "var(--lumo-error-color)" : pct > 80 ? "var(--lumo-warning-color)" : "var(--lumo-success-color)").set("border-radius", "6px").set("transition", "width 0.5s ease");
        bar.add(fill);

        Span pctLabel = new Span("%" + String.format("%.0f", pct) + " kullanıldı");
        pctLabel.getStyle().set("font-size", "0.75em").set("color", "var(--lumo-secondary-text-color)").set("display", "block").set("margin-top", "4px");

        // Monthly bars
        Div monthlyBars = new Div();
        monthlyBars.getStyle().set("display", "flex").set("gap", "4px").set("margin-top", "12px").set("align-items", "flex-end").set("height", "40px");
        for (DepartmentBudget b : budgets) {
            BigDecimal spent = expenseService.getDepartmentSpentForMonth(dept.getName(), b.getBudgetYear(), b.getBudgetMonth());
            double mpct = b.getBudgetLimit().compareTo(BigDecimal.ZERO) > 0 ? Math.min(100, spent.doubleValue() / b.getBudgetLimit().doubleValue() * 100) : 0;
            Div mBar = new Div();
            mBar.getStyle().set("flex", "1").set("height", mpct + "%").set("min-height", "3px").set("background", mpct > 100 ? "var(--lumo-error-color)" : mpct > 80 ? "var(--lumo-warning-color)" : "var(--lumo-success-color)").set("border-radius", "3px 3px 0 0");
            mBar.getElement().setAttribute("title", monthName(b.getBudgetMonth()) + ": " + FormatUtils.formatNumber(spent) + " / " + FormatUtils.formatNumber(b.getBudgetLimit()) + " ₺");
            monthlyBars.add(mBar);
        }

        // Employees
        List<Employee> deptEmployees = employeeService.findAllActiveEmployees().stream()
                .filter(e -> e.getDepartmentId() != null && e.getDepartmentId().equals(dept.getId())).toList();
        HorizontalLayout empRow = null;
        if (!deptEmployees.isEmpty()) {
            empRow = new HorizontalLayout();
            empRow.setSpacing(false); empRow.getStyle().set("gap", "6px").set("margin-top", "8px");
            Span empLabel = new Span("\uD83D\uDC64");
            empLabel.getStyle().set("font-size", "0.8em").set("color", "var(--lumo-tertiary-text-color)");
            empRow.add(empLabel);
            for (Employee emp : deptEmployees) {
                Span badge = new Span(emp.getFullName());
                badge.getStyle().set("font-size", "0.7em").set("padding", "1px 8px").set("border-radius", "10px").set("background", "var(--lumo-contrast-10pct)");
                empRow.add(badge);
            }
        }

        // Actions
        Button editBtn = new Button(new Icon(VaadinIcon.EDIT), ev -> {
            if (!budgets.isEmpty()) openBudgetDialog(budgets.get(0));
        });
        editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        Button delBtn = new Button(new Icon(VaadinIcon.TRASH), ev -> {
            Dialog c = new Dialog(); c.setHeaderTitle("Departmanı Sil");
            c.add(new Span(dept.getName() + " departmanı ve tüm bütçeleri silinecek. Emin misiniz?"));
            Button yes = new Button("Sil", e2 -> {
                departmentService.delete(dept.getId()); c.close(); refreshAll();
            });
            yes.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
            c.getFooter().add(new Button("İptal", e2 -> c.close()), yes); c.open();
        });
        delBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);

        HorizontalLayout actions = new HorizontalLayout(editBtn, delBtn);
        actions.setSpacing(false);

        VerticalLayout content = new VerticalLayout();
        content.setPadding(false); content.setSpacing(false);
        content.add(nameEl, stats, bar, pctLabel, monthlyBars);
        if (empRow != null) content.add(empRow);
        content.add(actions);

        card.add(content);
        return new VerticalLayout(card);
    }

    private Div statBox(String label, String value, String color) {
        Div box = new Div();
        box.getStyle().set("flex", "1").set("text-align", "center").set("padding", "4px");
        Span v = new Span(value);
        v.getStyle().set("font-weight", "700").set("font-size", "0.85em").set("color", color).set("display", "block");
        Span l = new Span(label);
        l.getStyle().set("font-size", "0.65em").set("color", "var(--lumo-tertiary-text-color)").set("display", "block");
        box.add(v, l);
        return box;
    }

    private Div createDeptChartSection() {
        Div container = new Div();
        container.getStyle().set("background", "var(--lumo-base-color)").set("border-radius", "12px").set("padding", "16px").set("margin-bottom", "16px");
        container.add(new Span("📊 Departman Harcama Trendi (son 6 ay)"));
        return container;
    }

    private void configureEmptyState() {
        emptyState.getStyle().set("display", "none").set("flex-direction", "column").set("align-items", "center").set("justify-content", "center").set("text-align", "center").set("padding", "3em").set("position", "absolute").set("inset", "0").set("margin", "auto");
        emptyState.add(
                new Span("💰") {{ getStyle().set("font-size", "2.5em").set("display","block"); }},
                new Span(selectedYear + " yılı için bütçe tanımlanmamış.") {{ getStyle().set("color","var(--lumo-secondary-text-color)").set("margin-top","0.5em"); }}
        );
    }

    private String monthName(int m) {
        return new String[]{"","Oca","Şub","Mar","Nis","May","Haz","Tem","Ağu","Eyl","Eki","Kas","Ara"}[m];
    }
}
