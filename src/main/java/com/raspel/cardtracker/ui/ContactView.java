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
import com.vaadin.flow.component.html.H4;
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
import com.raspel.cardtracker.domain.contact.Contact;
import com.raspel.cardtracker.domain.contact.ContactService;
import com.raspel.cardtracker.domain.contact.ContactType;
import jakarta.annotation.security.PermitAll;

import java.util.List;

@Route(value = "contacts", layout = MainLayout.class)
@PageTitle("Cari Hesaplar")
@PermitAll
public class ContactView extends VerticalLayout {

    private final ContactService contactService;
    private final Grid<Contact> grid = new Grid<>(Contact.class, false);
    private final TextField searchFilter = new TextField("Cari Adı Ara");
    private final Div emptyState = new Div();

    private final Span totalCustomersSpan = new Span("0");
    private final Span totalSuppliersSpan = new Span("0");
    private final ProgressBar loadingBar = new ProgressBar();

    public ContactView(ContactService contactService) {
        this.contactService = contactService;

        addClassName("contact-view");
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);
        loadingBar.setWidthFull();
        add(loadingBar);

        // 1. Üst özet kartları
        add(createSummaryPanel());

        // 2. Toolbar & Filtreler
        HorizontalLayout toolbar = createToolbar();
        toolbar.addClassName("view-toolbar");
        HorizontalLayout filters = createFilters();

        // 3. Grid konfigürasyonu
        configureGrid();

        add(toolbar, filters, grid);
        configureEmptyState();
        add(emptyState);
        refreshGrid();
    }

    private HorizontalLayout createSummaryPanel() {
        HorizontalLayout panel = new HorizontalLayout();
        panel.setWidthFull();
        panel.setSpacing(true);

        Div customerCard = createSummaryCard("Kayıtlı Müşteriler", totalCustomersSpan, "👥", "#2196F3");
        Div supplierCard = createSummaryCard("Kayıtlı Tedarikçiler", totalSuppliersSpan, "🏭", "#FF9800");

        panel.add(customerCard, supplierCard);
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
        H3 title = new H3("Cari Hesap Yönetimi (Müşteri & Tedarikçi)");
        title.getStyle().set("margin", "0");

        Button addBtn = new Button("Yeni Cari Kart", new Icon(VaadinIcon.PLUS), e -> openEditDialog(null));
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(title, addBtn);
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setWidthFull();
        toolbar.expand(title);
        return toolbar;
    }

    private HorizontalLayout createFilters() {
        searchFilter.setPlaceholder("Firma veya şahıs ismi yazın...");
        searchFilter.setClearButtonVisible(true);
        searchFilter.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);
        searchFilter.setValueChangeTimeout(400);
        searchFilter.addValueChangeListener(e -> refreshGrid());
        searchFilter.setWidth("300px");

        HorizontalLayout layout = new HorizontalLayout(searchFilter);
        layout.setWidthFull();
        layout.setAlignItems(Alignment.END);
        return layout;
    }

    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_WRAP_CELL_CONTENT);
        grid.setPageSize(25);
        grid.setSizeFull();

        grid.addColumn(Contact::getName).setHeader("Firma / Şahıs Adı").setSortable(true).setAutoWidth(true);
        grid.addColumn(c -> c.getType().getLabel()).setHeader("Cari Tipi").setSortable(true).setAutoWidth(true);
        grid.addColumn(Contact::getTaxNo).setHeader("Vergi No").setAutoWidth(true);
        grid.addColumn(Contact::getTaxOffice).setHeader("Vergi Dairesi").setAutoWidth(true);
        grid.addColumn(Contact::getPhone).setHeader("Telefon").setAutoWidth(true);
        grid.addColumn(Contact::getEmail).setHeader("E-posta").setAutoWidth(true);

        grid.addComponentColumn(contact -> {
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT), e -> openEditDialog(contact));
            editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH), e -> deleteContact(contact));
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
        Span emptyIcon = new Span("\uD83D\uDC65");
        emptyIcon.getStyle().set("font-size", "3em").set("display", "block");
        Span emptyText = new Span("Henüz cari kaydı bulunmuyor.");
        emptyText.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "1.1em")
                .set("margin-top", "1em");
        emptyState.add(emptyIcon, emptyText);
    }

    private void refreshGrid() {
        loadingBar.setVisible(true);
        List<Contact> contacts = contactService.findAll();
        
        String searchTerm = searchFilter.getValue() != null ? searchFilter.getValue().trim().toLowerCase(java.util.Locale.forLanguageTag("tr-TR")) : "";
        if (!searchTerm.isEmpty()) {
            contacts = contacts.stream()
                .filter(c -> c.getName().toLowerCase(java.util.Locale.forLanguageTag("tr-TR")).contains(searchTerm))
                .collect(java.util.stream.Collectors.toList());
        }
        
        grid.setItems(contacts);

        if (contacts.isEmpty()) {
            grid.setVisible(false);
            emptyState.getStyle().set("display", "flex");
        } else {
            grid.setVisible(true);
            emptyState.getStyle().set("display", "none");
        }

        long customers = contacts.stream().filter(c -> c.getType() == ContactType.CUSTOMER || c.getType() == ContactType.BOTH).count();
        long suppliers = contacts.stream().filter(c -> c.getType() == ContactType.SUPPLIER || c.getType() == ContactType.BOTH).count();

        totalCustomersSpan.setText(String.valueOf(customers));
        totalSuppliersSpan.setText(String.valueOf(suppliers));
        loadingBar.setVisible(false);
    }

    private void openEditDialog(Contact contactToEdit) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(contactToEdit == null ? "Yeni Cari Kart Oluştur" : "Cari Kart Düzenle");
        dialog.setWidth("500px");

        FormLayout form = new FormLayout();

        TextField nameField = new TextField("Firma / Şahıs Adı");
        nameField.setRequired(true);
        nameField.setWidthFull();

        ComboBox<ContactType> typeField = new ComboBox<>("Cari Tipi");
        typeField.setItems(ContactType.values());
        typeField.setItemLabelGenerator(ContactType::getLabel);
        typeField.setRequired(true);
        typeField.setWidthFull();

        TextField taxOfficeField = new TextField("Vergi Dairesi");
        TextField taxNoField = new TextField("Vergi No");
        TextField phoneField = new TextField("Telefon");
        phoneField.setMaxLength(20);
        phoneField.setPlaceholder("örn: 0555 123 45 67");
        TextField emailField = new TextField("E-posta");
        emailField.setMaxLength(100);
        emailField.setPlaceholder("örn: ornek@firma.com");

        form.add(nameField, typeField, taxOfficeField, taxNoField, phoneField, emailField);

        if (contactToEdit != null) {
            nameField.setValue(contactToEdit.getName());
            typeField.setValue(contactToEdit.getType());
            taxOfficeField.setValue(contactToEdit.getTaxOffice() != null ? contactToEdit.getTaxOffice() : "");
            taxNoField.setValue(contactToEdit.getTaxNo() != null ? contactToEdit.getTaxNo() : "");
            phoneField.setValue(contactToEdit.getPhone() != null ? contactToEdit.getPhone() : "");
            emailField.setValue(contactToEdit.getEmail() != null ? contactToEdit.getEmail() : "");
        }

        Button saveBtn = new Button("Kaydet", e -> {
            if (nameField.isEmpty() || typeField.isEmpty()) {
                Notification.show("Lütfen gerekli alanları doldurun!", 3000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            String ph = phoneField.getValue().replaceAll("[\\s-]", "");
            if (!ph.isEmpty() && !ph.matches("^05\\d{9}$")) {
                Notification.show("Telefon 11 haneli olmalı (05XX XXX XX XX). Örn: 05321234567", 4000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }
            String em = emailField.getValue().trim();
            if (!em.isEmpty() && !em.contains("@")) {
                Notification.show("Geçersiz e-posta. @ işareti içermelidir. Örn: ornek@firma.com", 4000, Notification.Position.MIDDLE)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            Contact contact = contactToEdit != null ? contactToEdit : new Contact();
            contact.setName(nameField.getValue().trim());
            contact.setType(typeField.getValue());
            contact.setTaxOffice(taxOfficeField.getValue().trim());
            contact.setTaxNo(taxNoField.getValue().trim());
            contact.setPhone(phoneField.getValue().trim());
            contact.setEmail(emailField.getValue().trim());

            try {
                contactService.save(contact);
                Notification.show("Cari başarıyla kaydedildi.", 3000, Notification.Position.MIDDLE)
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
        dialog.getElement().getStyle().set("overflow", "hidden");
    }

    private void deleteContact(Contact contact) {
        Dialog confirm = new Dialog();
        confirm.setHeaderTitle("Cari Kartı Sil");
        confirm.add(new Span(contact.getName() + " isimli cari kart silinecektir. Emin misiniz?"));

        Button yesBtn = new Button("Evet, Sil", e -> {
            try {
                contactService.delete(contact.getId());
                Notification.show("Cari silindi.", 3000, Notification.Position.BOTTOM_END)
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
