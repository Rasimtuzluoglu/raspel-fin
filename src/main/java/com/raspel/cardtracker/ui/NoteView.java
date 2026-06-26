package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.*;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.FlexLayout;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import com.vaadin.flow.component.datetimepicker.DateTimePicker;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.raspel.cardtracker.domain.note.Note;
import com.raspel.cardtracker.domain.note.NoteService;
import com.raspel.cardtracker.ui.utils.CategoryConstants;
import com.raspel.cardtracker.ui.utils.TurkishDatePickerI18n;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Route(value = "notes", layout = MainLayout.class)
@PageTitle("Notlarım")
@PermitAll
public class NoteView extends VerticalLayout {

    private final NoteService noteService;
    private final FlexLayout notesContainer;
    private final ComboBox<String> categoryFilter;
    private final TextField searchField;
    private final ProgressBar loadingBar = new ProgressBar();

    private static final String[] NOTE_COLORS = {
            "#FFF9C4", "#FFE0B2", "#FFCDD2", "#E1BEE7",
            "#BBDEFB", "#B2DFDB", "#C8E6C9", "#D7CCC8",
            "#CFD8DC", "#F8BBD0"
    };

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    public NoteView(NoteService noteService) {
        this.noteService = noteService;

        addClassName("note-view");
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);
        loadingBar.setWidthFull();
        add(loadingBar);

        // Toolbar
        HorizontalLayout toolbar = createToolbar();
        toolbar.addClassName("view-toolbar");

        // Filtreler
        categoryFilter = new ComboBox<>("Kategori");
        categoryFilter.setPlaceholder("Tümü");
        categoryFilter.setClearButtonVisible(true);
        categoryFilter.setWidth("200px");
        categoryFilter.addValueChangeListener(e -> refreshNotes());

        searchField = new TextField();
        searchField.setPlaceholder("Notlarda ara...");
        searchField.setPrefixComponent(new Icon(VaadinIcon.SEARCH));
        searchField.setClearButtonVisible(true);
        searchField.setWidth("300px");
        searchField.setValueChangeMode(com.vaadin.flow.data.value.ValueChangeMode.LAZY);
        searchField.setValueChangeTimeout(400);
        searchField.addValueChangeListener(e -> refreshNotes());

        HorizontalLayout filters = new HorizontalLayout(categoryFilter, searchField);
        filters.setAlignItems(FlexComponent.Alignment.END);

        // Not kartları container
        notesContainer = new FlexLayout();
        notesContainer.setFlexWrap(FlexLayout.FlexWrap.WRAP);
        notesContainer.getStyle()
                .set("gap", "16px")
                .set("padding", "8px 0");
        notesContainer.setWidthFull();

        add(toolbar, filters, notesContainer);

        refreshNotes();
    }

    private HorizontalLayout createToolbar() {
        H3 title = new H3("📝 Notlarım");
        title.getStyle().set("margin", "0");

        Button addBtn = new Button("Yeni Not", new Icon(VaadinIcon.PLUS), e -> openNoteDialog(null));
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(title, addBtn);
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.setWidthFull();
        toolbar.expand(title);

        return toolbar;
    }

    private void openNoteDialog(Note noteToEdit) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(noteToEdit == null ? "Yeni Not" : "Notu Düzenle");
        dialog.setWidth("600px");

        FormLayout form = new FormLayout();
        form.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));

        TextField titleField = new TextField("Başlık");
        titleField.setRequired(true);
        titleField.setWidthFull();
        titleField.setMaxLength(200);

        TextArea contentField = new TextArea("İçerik");
        contentField.setWidthFull();
        contentField.setMinHeight("200px");
        contentField.setMaxHeight("400px");
        contentField.setPlaceholder("Notunuzu buraya yazın...\n\nÖrnek:\n- Haziran ayı elektrik faturası: 450 ₺\n- İnternet: 250 ₺\n- Su: 120 ₺");

        ComboBox<String> categoryField = new ComboBox<>("Kategori");
        String username = getCurrentUsername();
        List<String> existingCategories = noteService.getCategories(username);
        // Varsayılan kategoriler ekle
        java.util.Set<String> allCategories = new java.util.LinkedHashSet<>();
        allCategories.addAll(CategoryConstants.NOTE_CATEGORIES);
        allCategories.addAll(existingCategories);
        categoryField.setItems(allCategories);
        categoryField.setAllowCustomValue(true);
        categoryField.addCustomValueSetListener(e -> categoryField.setValue(e.getDetail()));
        categoryField.setPlaceholder("Kategori seçin veya yazın");
        categoryField.setWidthFull();

        DateTimePicker reminderPicker = new DateTimePicker("Hatırlatma Tarihi / Saati");
        reminderPicker.setWidthFull();
        reminderPicker.setStep(java.time.Duration.ofMinutes(1));
        reminderPicker.setLocale(Locale.of("tr", "TR"));
        reminderPicker.setDatePickerI18n(TurkishDatePickerI18n.get());

        // Renk seçimi
        HorizontalLayout colorPicker = new HorizontalLayout();
        colorPicker.setSpacing(true);
        colorPicker.setAlignItems(FlexComponent.Alignment.CENTER);

        Span colorLabel = new Span("Renk:");
        colorLabel.getStyle().set("font-weight", "500").set("font-size", "0.875em")
                .set("color", "var(--lumo-secondary-text-color)");
        colorPicker.add(colorLabel);

        final String[] selectedColor = {noteToEdit != null && noteToEdit.getColor() != null ? noteToEdit.getColor() : NOTE_COLORS[0]};

        for (String color : NOTE_COLORS) {
            Div colorBtn = new Div();
            colorBtn.getStyle()
                    .set("width", "28px")
                    .set("height", "28px")
                    .set("border-radius", "50%")
                    .set("background-color", color)
                    .set("cursor", "pointer")
                    .set("border", color.equals(selectedColor[0]) ? "3px solid var(--lumo-primary-color)" : "2px solid var(--lumo-contrast-20pct)")
                    .set("transition", "transform 0.15s ease, border 0.15s ease")
                    .set("flex-shrink", "0");
            colorBtn.getElement().addEventListener("click", event -> {
                selectedColor[0] = color;
                // Tüm butonların border'ını sıfırla
                colorPicker.getChildren().forEach(child -> {
                    if (child instanceof Div) {
                        ((Div) child).getStyle().set("border", "2px solid var(--lumo-contrast-20pct)");
                    }
                });
                colorBtn.getStyle().set("border", "3px solid var(--lumo-primary-color)");
            });
            colorPicker.add(colorBtn);
        }

        // Form alanlarını doldur
        if (noteToEdit != null) {
            titleField.setValue(noteToEdit.getTitle() != null ? noteToEdit.getTitle() : "");
            contentField.setValue(noteToEdit.getContent() != null ? noteToEdit.getContent() : "");
            categoryField.setValue(noteToEdit.getCategory());
            reminderPicker.setValue(noteToEdit.getReminderAt());
        }

        form.add(titleField, contentField, categoryField);

        Span reminderLabel = new Span("Hatırlatma Tarihi ve Saati:");
        reminderLabel.getStyle().set("font-weight", "500").set("font-size", "0.875em")
                .set("color", "var(--lumo-secondary-text-color)");
        VerticalLayout dialogContent = new VerticalLayout(form, colorPicker, reminderLabel, reminderPicker);
        dialogContent.setPadding(false);
        dialogContent.setSpacing(true);

        Button saveBtn = new Button("Kaydet", e -> {
            if (titleField.getValue() == null || titleField.getValue().trim().isEmpty()) {
                Notification.show("Lütfen bir başlık girin", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            Note note;
            if (noteToEdit != null) {
                note = noteToEdit;
            } else {
                note = new Note();
                note.setCreatedBy(getCurrentUsername());
            }

            note.setTitle(titleField.getValue().trim());
            note.setContent(contentField.getValue());
            note.setCategory(categoryField.getValue());
            note.setColor(selectedColor[0]);
            note.setReminderAt(reminderPicker.getValue());
            note.setReminded(false);

            noteService.save(note);

            String message = noteToEdit != null ? "Not güncellendi" : "Not oluşturuldu";
            Notification.show(message, 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);

            dialog.close();
            refreshNotes();
        });
        saveBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        saveBtn.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        Button cancelBtn = new Button("İptal", e -> dialog.close());
        cancelBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);

        dialog.add(dialogContent);
        dialog.getFooter().add(cancelBtn, saveBtn);
        dialog.open();
        dialog.getElement().getStyle().set("overflow", "hidden");
    }

    private Div createNoteCard(Note note) {
        String bgColor = note.getColor() != null ? note.getColor() : "#FFD54F";
        String textColor = getContrastColor(bgColor);
        String secondaryTextColor = textColor.equals("#FFFFFF") ? "rgba(255,255,255,0.85)" : "#555555";
        String badgeBgColor = textColor.equals("#FFFFFF") ? "rgba(255,255,255,0.2)" : "rgba(0,0,0,0.1)";

        Div card = new Div();
        card.addClassName("note-card");
        card.getStyle()
                .set("background-color", bgColor)
                .set("border-radius", "12px")
                .set("padding", "16px")
                .set("min-width", "280px")
                .set("max-width", "350px")
                .set("flex", "1 1 300px")
                .set("box-shadow", "0 2px 8px rgba(0,0,0,0.12)")
                .set("cursor", "pointer")
                .set("transition", "transform 0.2s ease, box-shadow 0.2s ease")
                .set("position", "relative")
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "8px");

        // Hover efekti JS ile
        card.getElement().executeJs(
                "this.addEventListener('mouseenter', function() {" +
                "  this.style.transform = 'translateY(-4px)';" +
                "  this.style.boxShadow = '0 6px 20px rgba(0,0,0,0.18)';" +
                "});" +
                "this.addEventListener('mouseleave', function() {" +
                "  this.style.transform = 'translateY(0)';" +
                "  this.style.boxShadow = '0 2px 8px rgba(0,0,0,0.12)';" +
                "});"
        );

        // Başlık satırı
        HorizontalLayout titleRow = new HorizontalLayout();
        titleRow.setWidthFull();
        titleRow.setAlignItems(FlexComponent.Alignment.CENTER);

        Span titleSpan = new Span(note.getTitle());
        titleSpan.getStyle()
                .set("font-weight", "700")
                .set("font-size", "1.05em")
                .set("color", textColor)
                .set("flex", "1")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("white-space", "nowrap");

        // Pin ikonu
        if (note.getPinned() != null && note.getPinned()) {
            Span pinIcon = new Span("📌");
            pinIcon.getStyle().set("font-size", "0.9em");
            titleRow.add(pinIcon);
        }

        titleRow.add(titleSpan);
        titleRow.expand(titleSpan);

        // İçerik
        Div contentDiv = new Div();
        String content = note.getContent() != null ? note.getContent() : "";
        // İçeriği en fazla 6 satır göster
        String[] lines = content.split("\n");
        String preview = lines.length > 6
                ? String.join("\n", java.util.Arrays.copyOfRange(lines, 0, 6)) + "\n..."
                : content;
        contentDiv.setText(preview);
        contentDiv.getStyle()
                .set("font-size", "0.88em")
                .set("color", secondaryTextColor)
                .set("white-space", "pre-wrap")
                .set("word-break", "break-word")
                .set("flex", "1")
                .set("line-height", "1.5");

        // Alt bilgi satırı
        HorizontalLayout footer = new HorizontalLayout();
        footer.setWidthFull();
        footer.setAlignItems(FlexComponent.Alignment.CENTER);
        footer.getStyle().set("margin-top", "auto");

        VerticalLayout metaInfo = new VerticalLayout();
        metaInfo.setPadding(false);
        metaInfo.setSpacing(false);

        if (note.getCategory() != null && !note.getCategory().isEmpty()) {
            Span categoryBadge = new Span(note.getCategory());
            categoryBadge.getStyle()
                    .set("font-size", "0.72em")
                    .set("font-weight", "600")
                    .set("background", badgeBgColor)
                    .set("color", textColor)
                    .set("padding", "2px 8px")
                    .set("border-radius", "10px")
                    .set("display", "inline-block");
            metaInfo.add(categoryBadge);
        }

        Span dateSpan = new Span(note.getUpdatedAt() != null ? note.getUpdatedAt().format(DATE_FORMAT) : "");
        dateSpan.getStyle()
                .set("font-size", "0.72em")
                .set("color", secondaryTextColor)
                .set("margin-top", "4px");
        metaInfo.add(dateSpan);

        // Butonlar
        HorizontalLayout actions = new HorizontalLayout();
        actions.setSpacing(true);

        Button pinBtn = new Button(new Icon(VaadinIcon.PIN), e -> {
            noteService.togglePin(note.getId());
            refreshNotes();
        });
        pinBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        pinBtn.getStyle().set("color", secondaryTextColor);
        pinBtn.getElement().setAttribute("title", note.getPinned() != null && note.getPinned() ? "Sabitlemeyi Kaldır" : "Sabitle");

        Button editBtn = new Button(new Icon(VaadinIcon.EDIT), e -> openNoteDialog(note));
        editBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        editBtn.getStyle().set("color", secondaryTextColor);

        Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH), e -> deleteNote(note));
        deleteBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        deleteBtn.getStyle().set("color", textColor.equals("#FFFFFF") ? "#FFCDD2" : "#c62828");

        actions.add(pinBtn, editBtn, deleteBtn);

        footer.add(metaInfo, actions);
        footer.expand(metaInfo);

        card.add(titleRow, contentDiv, footer);

        // Kart üzerine çift tıkla -> düzenleme aç
        card.getElement().addEventListener("dblclick", event -> openNoteDialog(note));

        return card;
    }

    private void refreshNotes() {
        loadingBar.setVisible(true);
        notesContainer.removeAll();

        String username = getCurrentUsername();
        String searchTerm = searchField.getValue() != null ? searchField.getValue().trim() : "";
        String selectedCategory = categoryFilter.getValue();

        List<Note> notes;

        if (!searchTerm.isEmpty() && selectedCategory != null && !selectedCategory.isEmpty()) {
            notes = noteService.searchByTermAndCategory(username, searchTerm, selectedCategory);
        } else if (!searchTerm.isEmpty()) {
            notes = noteService.search(username, searchTerm);
        } else if (selectedCategory != null && !selectedCategory.isEmpty()) {
            notes = noteService.findByCategory(username, selectedCategory);
        } else {
            notes = noteService.findAllByUser(username);
        }

        // Kategori filtresini güncelle
        List<String> categories = noteService.getCategories(username);
        categoryFilter.setItems(categories);
        if (selectedCategory != null) {
            categoryFilter.setValue(selectedCategory);
        }

        if (notes.isEmpty()) {
            Div emptyState = new Div();
            emptyState.getStyle()
                    .set("text-align", "center")
                    .set("padding", "60px 20px")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("width", "100%");

            Span emptyIcon = new Span("📋");
            emptyIcon.getStyle().set("font-size", "3em").set("display", "block").set("margin-bottom", "12px");

            Span emptyText = new Span("Henüz not eklenmemiş. 'Yeni Not' butonuyla başlayın!");
            emptyText.getStyle().set("font-size", "1.1em");

            VerticalLayout emptyLayout = new VerticalLayout(emptyIcon, emptyText);
            emptyLayout.setAlignItems(FlexComponent.Alignment.CENTER);
            emptyLayout.setPadding(false);
            emptyDiv(emptyState, emptyLayout);

            notesContainer.add(emptyState);
        } else {
            notes.forEach(note -> notesContainer.add(createNoteCard(note)));
        }
        loadingBar.setVisible(false);
    }

    private void emptyDiv(Div container, VerticalLayout content) {
        container.add(content);
    }

    private void deleteNote(Note note) {
        Dialog confirmDialog = new Dialog();
        confirmDialog.setHeaderTitle("Not Silme Onayı");
        confirmDialog.add(new Span("\"" + note.getTitle() + "\" notunu silmek istediğinize emin misiniz?"));

        Button confirmBtn = new Button("Sil", e -> {
            noteService.delete(note.getId());
            Notification.show("Not silindi", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            confirmDialog.close();
            refreshNotes();
        });
        confirmBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_ERROR);
        confirmBtn.addClickShortcut(com.vaadin.flow.component.Key.ENTER);

        Button cancelBtn = new Button("İptal", e -> confirmDialog.close());
        cancelBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);

        confirmDialog.getFooter().add(cancelBtn, confirmBtn);
        confirmDialog.open();
    }

    private String getCurrentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "unknown";
    }

    private String getContrastColor(String hexColor) {
        if (hexColor == null || !hexColor.startsWith("#") || hexColor.length() < 7) {
            return "#333333";
        }
        int r = Integer.valueOf(hexColor.substring(1, 3), 16);
        int g = Integer.valueOf(hexColor.substring(3, 5), 16);
        int b = Integer.valueOf(hexColor.substring(5, 7), 16);
        double luminance = (0.299 * r + 0.587 * g + 0.114 * b) / 255;
        return luminance > 0.5 ? "#333333" : "#FFFFFF";
    }
}
