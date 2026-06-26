package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.renderer.ComponentRenderer;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.raspel.cardtracker.domain.audit.AuditAction;
import com.raspel.cardtracker.domain.audit.AuditLog;
import com.raspel.cardtracker.domain.audit.AuditLogService;
import com.raspel.cardtracker.ui.utils.TurkishDatePickerI18n;
import jakarta.annotation.security.RolesAllowed;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Route(value = "audit-log", layout = MainLayout.class)
@PageTitle("İşlem Geçmişi")
@RolesAllowed("ADMIN")
public class AuditLogView extends VerticalLayout {

    private final AuditLogService auditLogService;

    private final Grid<AuditLog> grid = new Grid<>(AuditLog.class, false);
    private final TextField searchField = new TextField("Ara (Kullanıcı, Açıklama)");
    private final ComboBox<AuditAction> actionFilter = new ComboBox<>("İşlem Tipi");
    private final ComboBox<String> entityTypeFilter = new ComboBox<>("Varlık Tipi");
    private final DatePicker startDateFilter = new DatePicker("Başlangıç Tarihi");
    private final DatePicker endDateFilter = new DatePicker("Bitiş Tarihi");
    private final ProgressBar loadingBar = new ProgressBar();
    private final Div emptyState = new Div();

    public AuditLogView(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;

        addClassName("audit-log-view");
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);
        loadingBar.setWidthFull();
        add(loadingBar);

        H3 title = new H3("İşlem Geçmişi");
        title.getStyle().set("margin-top", "0");

        configureFilters();
        configureGrid();

        HorizontalLayout toolbar = createToolbar();
        toolbar.addClassName("view-toolbar");
        
        add(title, toolbar, grid);
        configureEmptyState();
        add(emptyState);
        refreshGrid();
    }

    private void configureFilters() {
        searchField.setPlaceholder("Arama terimi...");
        searchField.setClearButtonVisible(true);
        searchField.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);
        searchField.setValueChangeTimeout(400);
        searchField.addValueChangeListener(e -> refreshGrid());

        actionFilter.setItems(AuditAction.values());
        actionFilter.setItemLabelGenerator(AuditAction::getLabel);
        actionFilter.setClearButtonVisible(true);
        actionFilter.addValueChangeListener(e -> refreshGrid());

        entityTypeFilter.setItems("Kart", "Harcama", "Çek", "Cari", "Bütçe", "Kullanıcı");
        entityTypeFilter.setClearButtonVisible(true);
        entityTypeFilter.addValueChangeListener(e -> refreshGrid());

        DatePicker.DatePickerI18n turkishI18n = TurkishDatePickerI18n.get();

        startDateFilter.setI18n(turkishI18n);
        startDateFilter.addValueChangeListener(e -> refreshGrid());
        
        endDateFilter.setI18n(turkishI18n);
        endDateFilter.addValueChangeListener(e -> refreshGrid());
    }

    private HorizontalLayout createToolbar() {
        Button refreshBtn = new Button("Yenile", new Icon(VaadinIcon.REFRESH), e -> refreshGrid());
        refreshBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        Button resetBtn = new Button(new Icon(VaadinIcon.ERASER));
        resetBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
        resetBtn.setAriaLabel("Filtreleri Sıfırla");
        resetBtn.addClickListener(e -> {
            searchField.clear();
            actionFilter.clear();
            entityTypeFilter.clear();
            startDateFilter.clear();
            endDateFilter.clear();
            refreshGrid();
        });

        HorizontalLayout layout = new HorizontalLayout(searchField, actionFilter, entityTypeFilter, startDateFilter, endDateFilter, resetBtn, refreshBtn);
        layout.setAlignItems(Alignment.END);
        layout.setWidthFull();
        layout.getStyle().set("flex-wrap", "wrap");
        return layout;
    }

    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setPageSize(50);
        grid.setSizeFull();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

        grid.addColumn(log -> log.getCreatedAt().format(formatter))
                .setHeader("Tarih").setSortable(true).setWidth("150px").setFlexGrow(0);
                
        grid.addColumn(AuditLog::getUsername)
                .setHeader("Kullanıcı").setSortable(true).setAutoWidth(true);

        grid.addColumn(new ComponentRenderer<>(log -> {
            Span badge = new Span(log.getAction().getLabel());
            String theme = "badge";
            switch (log.getAction()) {
                case CREATE -> theme += " success";
                case UPDATE -> theme += " contrast";
                case DELETE -> theme += " error";
                case LOGIN, LOGOUT -> theme += " primary";
            }
            badge.getElement().getThemeList().add(theme);
            return badge;
        })).setHeader("İşlem").setSortable(true).setAutoWidth(true);

        grid.addColumn(AuditLog::getEntityType)
                .setHeader("Varlık Tipi").setSortable(true).setAutoWidth(true);
                
        grid.addColumn(AuditLog::getEntityId)
                .setHeader("Kayıt ID").setSortable(true).setWidth("100px").setFlexGrow(0);

        grid.addColumn(AuditLog::getDescription)
                .setHeader("Açıklama").setAutoWidth(true).setFlexGrow(1);
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
        Span emptyIcon = new Span("\uD83D\uDCDC");
        emptyIcon.getStyle().set("font-size", "3em").set("display", "block");
        Span emptyText = new Span("Henüz işlem geçmişi kaydı bulunmuyor.");
        emptyText.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "1.1em")
                .set("margin-top", "1em");
        emptyState.add(emptyIcon, emptyText);
    }

    private void refreshGrid() {
        loadingBar.setVisible(true);
        AuditAction action = actionFilter.getValue();
        String entityType = entityTypeFilter.getValue();
        LocalDateTime startDate = startDateFilter.getValue() != null ? startDateFilter.getValue().atStartOfDay() : null;
        LocalDateTime endDate = endDateFilter.getValue() != null ? endDateFilter.getValue().plusDays(1).atStartOfDay() : null;

        List<AuditLog> logs = auditLogService.findFiltered(null, action, entityType, startDate, endDate);
        
        String term = searchField.getValue() != null ? searchField.getValue().trim().toLowerCase() : "";
        if (!term.isEmpty()) {
            logs = logs.stream().filter(log -> {
                boolean matchUser = log.getUsername() != null && log.getUsername().toLowerCase().contains(term);
                boolean matchDesc = log.getDescription() != null && log.getDescription().toLowerCase().contains(term);
                return matchUser || matchDesc;
            }).toList();
        }

        grid.setItems(logs);
        if (logs.isEmpty()) {
            grid.setVisible(false);
            emptyState.getStyle().set("display", "flex");
        } else {
            grid.setVisible(true);
            emptyState.getStyle().set("display", "none");
        }
        loadingBar.setVisible(false);
    }
}
