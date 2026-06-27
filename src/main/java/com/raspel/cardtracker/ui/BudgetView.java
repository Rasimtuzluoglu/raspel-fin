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
import com.raspel.cardtracker.domain.expense.Expense;
import com.raspel.cardtracker.domain.expense.ExpenseService;
import com.raspel.cardtracker.ui.utils.FormatUtils;
import jakarta.annotation.security.PermitAll;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Route(value = "budgets", layout = MainLayout.class)
@PageTitle("Bütçe Yönetimi")
@PermitAll
public class BudgetView extends VerticalLayout {

    private final DepartmentBudgetService budgetService;
    private final DepartmentService departmentService;
    private final CardService cardService;
    private final ExpenseService expenseService;

    private final VerticalLayout cardsContainer = new VerticalLayout();
    private final Div emptyState = new Div();

    public BudgetView(DepartmentBudgetService budgetService, CardService cardService,
                      DepartmentService departmentService, ExpenseService expenseService) {
        this.budgetService = budgetService;
        this.departmentService = departmentService;
        this.cardService = cardService;
        this.expenseService = expenseService;

        addClassName("budget-view");
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        H3 title = new H3("Bütçe Yönetimi");
        title.getStyle().set("margin-top", "0");

        HorizontalLayout toolbar = new HorizontalLayout(title, createAddSection());
        toolbar.setWidthFull();
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.expand(title);

        configureEmptyState();
        cardsContainer.setPadding(false);
        cardsContainer.setSpacing(false);
        cardsContainer.getStyle().set("gap", "12px");

        add(toolbar, cardsContainer, emptyState);
        refreshAll();
    }

    private HorizontalLayout createAddSection() {
        Button newBudgetBtn = new Button("Yeni Bütçe", new Icon(VaadinIcon.PLUS), e -> openBudgetDialog(null));
        newBudgetBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button deptBtn = new Button("Departman Ekle", new Icon(VaadinIcon.PLUS), e -> openDeptDialog());
        deptBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY);

        return new HorizontalLayout(newBudgetBtn, deptBtn);
    }

    private void openDeptDialog() {
        Dialog d = new Dialog();
        d.setHeaderTitle("Yeni Departman");
        d.setWidth("380px");
        TextField name = new TextField("Departman Adı");
        name.setWidthFull();
        Button save = new Button("Ekle", ev -> {
            String n = name.getValue().trim();
            if (n.isEmpty()) { Notification.show("Ad boş olamaz", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR); return; }
            departmentService.save(Department.builder().name(n).isActive(true).build());
            Notification.show("Eklendi", 2000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            d.close(); refreshAll();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("İptal", ev -> d.close());
        d.add(name);
        d.getFooter().add(cancel, save);
        d.open();
        d.getElement().getStyle().set("overflow", "hidden");
    }

    private void openBudgetDialog(DepartmentBudget edit) {
        Dialog d = new Dialog();
        d.setHeaderTitle(edit == null ? "Yeni Bütçe" : "Bütçe Düzenle");
        d.setWidth("420px");
        FormLayout f = new FormLayout();

        ComboBox<Department> deptCb = new ComboBox<>("Departman");
        deptCb.setItems(departmentService.findAllActive());
        deptCb.setItemLabelGenerator(Department::getName);
        deptCb.setWidthFull();

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
        int yr = LocalDate.now().getYear();
        int mn = LocalDate.now().getMonthValue();
        List<DepartmentBudget> budgets = budgetService.findByYearAndMonth(yr, mn);
        if (budgets.isEmpty()) { emptyState.getStyle().set("display", "flex"); cardsContainer.setVisible(false); return; }
        emptyState.getStyle().set("display", "none"); cardsContainer.setVisible(true);

        Map<String, BigDecimal> spentCache = new HashMap<>();
        for (DepartmentBudget b : budgets) {
            String deptName = b.getDepartment() != null ? b.getDepartment().getName() : "";
            if (!spentCache.containsKey(deptName)) {
                spentCache.put(deptName, expenseService.getDepartmentSpentForMonth(deptName, yr, mn));
            }
            cardsContainer.add(buildDeptCard(b, spentCache.get(deptName)));
        }
    }

    private VerticalLayout buildDeptCard(DepartmentBudget budget, BigDecimal spent) {
        String deptName = budget.getDepartment() != null ? budget.getDepartment().getName() : "Bilinmeyen";
        BigDecimal limit = budget.getBudgetLimit();
        if (spent == null) spent = BigDecimal.ZERO;
        BigDecimal remaining = limit.subtract(spent);
        boolean over = remaining.compareTo(BigDecimal.ZERO) < 0;

        Div card = new Div();
        card.getStyle()
                .set("border-radius", "12px")
                .set("padding", "16px 20px")
                .set("background", over ? "var(--lumo-error-color-10pct)" : "var(--lumo-base-color)")
                .set("border", over ? "2px solid var(--lumo-error-color)" : "1px solid var(--lumo-contrast-10pct)")
                .set("box-shadow", "0 1px 4px rgba(0,0,0,0.06)");

        HorizontalLayout top = new HorizontalLayout();
        top.setWidthFull(); top.setAlignItems(Alignment.CENTER);

        Span nameSpan = new Span(deptName);
        nameSpan.getStyle().set("font-weight", "700").set("font-size", "1em");

        Span spentSpan = new Span(FormatUtils.formatNumber(spent) + " / " + FormatUtils.formatNumber(limit) + " ₺");
        spentSpan.getStyle().set("font-size", "0.8em").set("color", "var(--lumo-secondary-text-color)");

        Span remainingSpan = new Span("Kalan: " + FormatUtils.formatNumber(remaining.abs()) + " ₺");
        remainingSpan.getStyle().set("font-weight", "600").set("font-size", "0.8em")
                .set("color", over ? "var(--lumo-error-color)" : "var(--lumo-success-color)");

        top.add(nameSpan, spentSpan, remainingSpan);
        top.expand(nameSpan);

        Div bar = new Div();
        bar.getStyle().set("height", "10px").set("border-radius", "5px").set("background", "var(--lumo-contrast-10pct)").set("margin-top", "10px").set("overflow", "hidden");
        double pct = limit.compareTo(BigDecimal.ZERO) > 0 ? Math.min(100, spent.doubleValue() / limit.doubleValue() * 100) : 0;
        Div fill = new Div();
        fill.getStyle().set("height", "100%").set("width", pct + "%").set("background", over ? "var(--lumo-error-color)" : "var(--lumo-success-color)").set("border-radius", "5px").set("transition", "width 0.3s ease");
        bar.add(fill);

        VerticalLayout content = new VerticalLayout(top, bar);
        content.setPadding(false); content.setSpacing(false);

        // Expandable expense list
        Div detail = new Div();
        detail.setVisible(false);
        detail.getStyle().set("margin-top", "12px").set("padding-top", "12px").set("border-top", "1px solid var(--lumo-contrast-10pct)");
        VerticalLayout expList = new VerticalLayout();
        expList.setPadding(false); expList.setSpacing(false); expList.getStyle().set("gap", "4px");
        buildExpenseList(budget, expList);

        Button toggleBtn = new Button("Harcamalar", new Icon(VaadinIcon.CHEVRON_DOWN), ev -> {
            boolean show = !detail.isVisible();
            detail.setVisible(show);
            if (show) { expList.removeAll(); buildExpenseList(budget, expList); }
            ev.getSource().setIcon(new Icon(show ? VaadinIcon.CHEVRON_UP : VaadinIcon.CHEVRON_DOWN));
        });
        toggleBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        Button addExpBtn = new Button("Yeni Harcama", new Icon(VaadinIcon.PLUS), ev -> openBudgetExpenseDialog(budget));
        addExpBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_PRIMARY);

        Button editBtn = new Button(new Icon(VaadinIcon.EDIT), ev -> openBudgetDialog(budget));
        editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

        Button delBtn = new Button(new Icon(VaadinIcon.TRASH), ev -> {
            Dialog c = new Dialog(); c.setHeaderTitle("Bütçeyi Sil");
            c.add(new Span("Bu bütçe silinecek. Emin misiniz?"));
            Button yes = new Button("Sil", e2 -> { budgetService.delete(budget.getId()); c.close(); refreshAll(); });
            yes.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
            Button no = new Button("İptal", e2 -> c.close());
            c.getFooter().add(no, yes); c.open();
        });
        delBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);

        detail.add(expList);

        HorizontalLayout actions = new HorizontalLayout(toggleBtn, addExpBtn, editBtn, delBtn);
        actions.setWidthFull(); actions.setSpacing(true);
        actions.expand(toggleBtn);

        card.add(content, actions, detail);
        VerticalLayout wrapper = new VerticalLayout(card);
        wrapper.setPadding(false); wrapper.setSpacing(false);
        return wrapper;
    }

    private VerticalLayout buildExpenseList(DepartmentBudget budget, VerticalLayout container) {
        container.removeAll();
        String deptName = budget.getDepartment() != null ? budget.getDepartment().getName() : "";
        // Get expenses for this department in this month via installment entries
        List<com.raspel.cardtracker.domain.expense.InstallmentEntry> entries = expenseService.getInstallmentsForMonth(budget.getBudgetYear(), budget.getBudgetMonth());
        boolean found = false;
        for (com.raspel.cardtracker.domain.expense.InstallmentEntry ie : entries) {
            if (ie.getExpense() == null || ie.getExpense().getCard() == null) continue;
            String cd = ie.getExpense().getCard().getDepartment() != null ? ie.getExpense().getCard().getDepartment().getName() : "";
            if (!cd.equalsIgnoreCase(deptName)) continue;
            found = true;
            HorizontalLayout row = new HorizontalLayout();
            row.setWidthFull(); row.setAlignItems(Alignment.CENTER); row.getStyle().set("padding", "2px 0").set("gap", "8px");
            String desc = ie.getExpense().getDescription() != null ? ie.getExpense().getDescription() : "";
            if (desc.length() > 25) desc = desc.substring(0, 25) + "...";
            Span d = new Span(desc);
            d.getStyle().set("font-size", "0.8em"); Span a = new Span(FormatUtils.formatNumber(ie.getAmount()) + " ₺");
            a.getStyle().set("font-weight", "600").set("font-size", "0.8em").set("margin-left", "auto");
            row.add(d, a); row.expand(d);
            container.add(row);
        }
        if (!found) container.add(new Span("Henüz harcama yok."));
        return container;
    }

    private void openBudgetExpenseDialog(DepartmentBudget budget) {
        String deptName = budget.getDepartment() != null ? budget.getDepartment().getName() : "";
        BigDecimal spent = expenseService.getDepartmentSpentForMonth(deptName, budget.getBudgetYear(), budget.getBudgetMonth());
        BigDecimal raw = budget.getBudgetLimit().subtract(spent);
        final BigDecimal remaining = raw.compareTo(BigDecimal.ZERO) < 0 ? BigDecimal.ZERO : raw;

        Dialog d = new Dialog();
        d.setHeaderTitle(deptName + " - Harcama Ekle");
        d.setWidth("420px");

        Span rem = new Span("Kalan: " + FormatUtils.formatNumber(remaining) + " ₺");
        rem.getStyle().set("font-weight", "600").set("color", remaining.compareTo(BigDecimal.ZERO) <= 0 ? "var(--lumo-error-color)" : "var(--lumo-success-color)").set("display", "block").set("text-align", "center").set("padding", "8px").set("background", "var(--lumo-contrast-5pct)").set("border-radius", "8px");

        ComboBox<Card> cardCb = new ComboBox<>("Kart");
        List<Card> deptCards = cardService.findAllActive().stream().filter(c -> c.getDepartment() != null && c.getDepartment().getId().equals(budget.getDepartment().getId())).collect(java.util.stream.Collectors.toList());
        cardCb.setItems(deptCards.isEmpty() ? cardService.findAllActive() : deptCards);
        cardCb.setItemLabelGenerator(c -> c.getName() + " (" + c.getBank() + ")"); cardCb.setWidthFull();

        TextArea descF = new TextArea("Açıklama"); descF.setWidthFull();
        TextField amtF = new TextField("Tutar (₺)"); amtF.setValue("0,00"); FormatUtils.attachCurrencyFormatting(amtF); amtF.setWidthFull();

        Button save = new Button("Kaydet", ev -> {
            if (cardCb.isEmpty()) { Notification.show("Kart seçin", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR); return; }
            BigDecimal amt = FormatUtils.parseTurkishCurrency(amtF.getValue());
            if (amt == null || amt.compareTo(BigDecimal.ZERO) <= 0) { Notification.show("Geçerli tutar girin", 3000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR); return; }
            if (amt.compareTo(remaining) > 0) { Notification.show("Tutar kalan bütçeyi aşıyor: " + FormatUtils.formatNumber(remaining) + " ₺", 4000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_ERROR); return; }
            Card card = cardCb.getValue();
            if (card.getDepartment() == null || !card.getDepartment().getId().equals(budget.getDepartment().getId())) {
                card.setDepartment(budget.getDepartment()); cardService.save(card);
            }
            Expense exp = Expense.builder().card(card).description(descF.getValue() != null ? descF.getValue() : deptName + " harcaması").totalAmount(amt).originalAmount(amt).installments(1).expenseDate(LocalDate.now()).currency("TRY").category(deptName).build();
            expenseService.createExpense(exp);
            Notification.show("Kaydedildi: " + FormatUtils.formatNumber(amt) + " ₺", 2000, Notification.Position.MIDDLE).addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            d.close(); refreshAll();
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("İptal", ev -> d.close());
        d.add(rem, cardCb, descF, amtF); d.getFooter().add(cancel, save);
        d.open(); d.getElement().getStyle().set("overflow", "hidden");
    }

    private void configureEmptyState() {
        emptyState.getStyle().set("display", "none").set("flex-direction", "column").set("align-items", "center").set("justify-content", "center").set("text-align", "center").set("padding", "3em").set("position", "absolute").set("inset", "0").set("margin", "auto");
        emptyState.add(new Span("💰") {{ getStyle().set("font-size", "2.5em").set("display","block"); }}, new Span("Bu ay için bütçe tanımlanmamış.") {{ getStyle().set("color","var(--lumo-secondary-text-color)").set("margin-top","0.5em"); }});
    }
}
