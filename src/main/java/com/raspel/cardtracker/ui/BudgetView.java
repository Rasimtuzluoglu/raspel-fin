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
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.raspel.cardtracker.domain.budget.DepartmentBudget;
import com.raspel.cardtracker.domain.budget.DepartmentBudgetService;
import com.raspel.cardtracker.domain.card.CardService;
import com.raspel.cardtracker.domain.department.Department;
import com.raspel.cardtracker.domain.department.DepartmentService;
import com.raspel.cardtracker.domain.employee.EmployeeService;
import com.raspel.cardtracker.domain.expense.ExpenseService;
import com.raspel.cardtracker.ui.utils.FormatUtils;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
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

        HorizontalLayout toolbar = new HorizontalLayout(titleEl, createAddSection());
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.expand(titleEl);

        configureEmptyState();
        cardsContainer.setPadding(false);
        cardsContainer.setSpacing(false);
        cardsContainer.getStyle().set("gap", "12px");

        add(toolbar, cardsContainer, emptyState);

        int yr = LocalDate.now().getYear();
        int mn = LocalDate.now().getMonthValue();
        loadBudgets(yr, mn);
    }

    private HorizontalLayout createAddSection() {
        Button newBudgetBtn = new Button("Yeni Bütçe", new Icon(VaadinIcon.PLUS), e -> openBudgetDialog(null));
        newBudgetBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button deptBtn = new Button("Departman Ekle", new Icon(VaadinIcon.PLUS), e -> openDeptDialog());
        deptBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        return new HorizontalLayout(newBudgetBtn, deptBtn);
    }

    private void openDeptDialog() {
        Dialog d = new Dialog(); d.setHeaderTitle("Yeni Departman"); d.setWidth("380px");
        TextField name = new TextField("Departman Adı"); name.setWidthFull();
        Button save = new Button("Ekle", ev -> {
            String n = name.getValue().trim();
            if (n.isEmpty()) { Notification.show("Ad boş olamaz", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR); return; }
            departmentService.save(Department.builder().name(n).isActive(true).build());
            Notification.show("Eklendi", 2000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            d.close();
            int yr = LocalDate.now().getYear(), mn = LocalDate.now().getMonthValue();
            loadBudgets(yr, mn);
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("İptal", ev -> d.close());
        d.add(name); d.getFooter().add(cancel, save);
        d.open(); d.getElement().getStyle().set("overflow", "hidden");
    }

    private void openBudgetDialog(DepartmentBudget edit) {
        Dialog d = new Dialog();
        d.setHeaderTitle(edit == null ? "Yeni Bütçe" : "Bütçe Düzenle"); d.setWidth("420px");
        FormLayout f = new FormLayout();

        ComboBox<Department> deptCb = new ComboBox<>("Departman");
        deptCb.setItems(departmentService.findAllActive());
        deptCb.setItemLabelGenerator(Department::getName); deptCb.setWidthFull();

        int yr = LocalDate.now().getYear();
        ComboBox<Integer> yearCb = new ComboBox<>("Yıl");
        yearCb.setItems(java.util.stream.IntStream.rangeClosed(yr - 2, yr + 10).boxed().toList());
        yearCb.setValue(yr); yearCb.setWidthFull();

        ComboBox<Integer> monthCb = new ComboBox<>("Ay");
        monthCb.setItems(1,2,3,4,5,6,7,8,9,10,11,12);
        monthCb.setValue(LocalDate.now().getMonthValue()); monthCb.setWidthFull();

        TextField limitF = new TextField("Limit (₺)");
        limitF.setValue("0,00"); FormatUtils.attachCurrencyFormatting(limitF); limitF.setWidthFull();

        if (edit != null) {
            deptCb.setValue(edit.getDepartment());
            yearCb.setValue(edit.getBudgetYear());
            monthCb.setValue(edit.getBudgetMonth());
            limitF.setValue(FormatUtils.formatTurkishCurrency(edit.getBudgetLimit()));
        }

        f.add(deptCb, yearCb, monthCb, limitF);

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
            b.setDepartment(dept); b.setBudgetYear(yearCb.getValue()); b.setBudgetMonth(monthCb.getValue()); b.setBudgetLimit(lim);
            try {
                budgetService.save(b);
                Notification.show("Kaydedildi", 2000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                d.close();
                loadBudgets(yearCb.getValue(), monthCb.getValue());
            } catch (Exception exc) {
                Notification.show("Kayıt başarısız", 4000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("İptal", ev -> d.close());
        d.add(f); d.getFooter().add(cancel, save);
        d.open(); d.getElement().getStyle().set("overflow", "hidden");
    }

    private void loadBudgets(int year, int month) {
        cardsContainer.removeAll();
        List<DepartmentBudget> budgets = budgetService.findByYearAndMonth(year, month);

        if (budgets.isEmpty()) {
            emptyState.getStyle().set("display", "flex");
            cardsContainer.setVisible(false);
            return;
        }
        emptyState.getStyle().set("display", "none");
        cardsContainer.setVisible(true);

        for (DepartmentBudget budget : budgets) {
            String deptName = budget.getDepartment() != null ? budget.getDepartment().getName() : "Bilinmeyen";
            BigDecimal spent = expenseService.getDepartmentSpentForMonth(deptName, year, month);
            cardsContainer.add(buildDeptCard(budget, spent, year, month));
        }
    }

    private VerticalLayout buildDeptCard(DepartmentBudget budget, BigDecimal spent, int year, int month) {
        String deptName = budget.getDepartment() != null ? budget.getDepartment().getName() : "Bilinmeyen";
        BigDecimal limit = budget.getBudgetLimit();
        BigDecimal remaining = limit.subtract(spent);
        boolean over = remaining.compareTo(BigDecimal.ZERO) < 0;
        double pct = limit.compareTo(BigDecimal.ZERO) > 0 ? Math.min(100, spent.doubleValue() / limit.doubleValue() * 100) : 0;

        Div card = new Div();
        card.getStyle().set("border-radius", "12px").set("padding", "14px 18px")
                .set("background", over ? "var(--lumo-error-color-10pct)" : "var(--lumo-base-color)")
                .set("border", over ? "2px solid var(--lumo-error-color)" : "1px solid var(--lumo-contrast-10pct)")
                .set("box-shadow", "0 1px 4px rgba(0,0,0,0.05)");

        HorizontalLayout top = new HorizontalLayout();
        top.setWidthFull(); top.setAlignItems(Alignment.CENTER);

        H4 nameEl = new H4(deptName);
        nameEl.getStyle().set("margin", "0").set("font-size", "1em");

        Span amountSpan = new Span(FormatUtils.formatNumber(spent) + " / " + FormatUtils.formatNumber(limit) + " ₺");
        amountSpan.getStyle().set("font-size", "0.8em").set("color", "var(--lumo-secondary-text-color)");

        Span pctSpan = new Span("%" + String.format("%.0f", pct));
        pctSpan.getStyle().set("font-weight", "700").set("font-size", "0.85em")
                .set("color", over || pct >= 90 ? "var(--lumo-error-color)" : pct >= 80 ? "var(--lumo-warning-color)" : "var(--lumo-success-color)");

        top.add(nameEl, amountSpan, pctSpan);
        top.expand(nameEl);

        Div bar = new Div();
        bar.getStyle().set("height", "8px").set("border-radius", "4px").set("background", "var(--lumo-contrast-10pct)").set("margin-top", "8px").set("overflow", "hidden");
        Div fill = new Div();
        fill.getStyle().set("height", "100%").set("width", pct + "%")
                .set("background", over || pct >= 90 ? "var(--lumo-error-color)" : pct >= 80 ? "var(--lumo-warning-color)" : "var(--lumo-success-color)")
                .set("border-radius", "4px").set("transition", "width 0.3s ease");
        bar.add(fill);

        Span remainingSpan = new Span("Kalan: " + FormatUtils.formatNumber(remaining.abs()) + " ₺");
        remainingSpan.getStyle().set("font-size", "0.75em").set("color", "var(--lumo-tertiary-text-color)").set("margin-top", "4px").set("display", "block");

        Button editBtn = new Button(new Icon(VaadinIcon.EDIT), ev -> openBudgetDialog(budget));
        editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        Button delBtn = new Button(new Icon(VaadinIcon.TRASH), ev -> {
            Dialog c = new Dialog(); c.setHeaderTitle("Bütçeyi Sil");
            c.add(new Span(deptName + " bütçesi silinecek. Emin misiniz?"));
            Button yes = new Button("Sil", e2 -> {
                budgetService.delete(budget.getId()); c.close();
                loadBudgets(year, month);
            });
            yes.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
            c.getFooter().add(new Button("İptal", e2 -> c.close()), yes); c.open();
        });
        delBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);

        VerticalLayout content = new VerticalLayout(top, bar, remainingSpan, new HorizontalLayout(editBtn, delBtn));
        content.setPadding(false); content.setSpacing(false);
        card.add(content);

        return new VerticalLayout(card);
    }

    private void configureEmptyState() {
        emptyState.getStyle().set("display", "none").set("flex-direction", "column").set("align-items", "center")
                .set("justify-content", "center").set("text-align", "center").set("padding", "3em")
                .set("position", "absolute").set("inset", "0").set("margin", "auto");
        emptyState.add(
                new Span("💰") {{ getStyle().set("font-size", "2.5em").set("display","block"); }},
                new Span("Bu ay için bütçe tanımlanmamış.") {{ getStyle().set("color","var(--lumo-secondary-text-color)").set("margin-top","0.5em"); }}
        );
    }
}
