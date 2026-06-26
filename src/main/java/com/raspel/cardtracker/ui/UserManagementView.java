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
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.raspel.cardtracker.domain.user.AppUser;
import com.raspel.cardtracker.domain.user.Role;
import com.raspel.cardtracker.domain.user.UserService;
import jakarta.annotation.security.RolesAllowed;

import java.util.List;

@Route(value = "users", layout = MainLayout.class)
@PageTitle("Kullanıcı Yönetimi")
@RolesAllowed("ADMIN")
public class UserManagementView extends VerticalLayout {

    private final UserService userService;
    private final Grid<AppUser> grid = new Grid<>(AppUser.class, false);
    private final ProgressBar loadingBar = new ProgressBar();
    private final Div emptyState = new Div();

    public UserManagementView(UserService userService) {
        this.userService = userService;

        addClassName("user-management-view");
        setSizeFull();
        setPadding(true);
        setSpacing(true);

        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);
        loadingBar.setWidthFull();
        add(loadingBar);

        configureGrid();

        HorizontalLayout toolbar = createToolbar();
        toolbar.addClassName("view-toolbar");
        add(toolbar, grid);

        configureEmptyState();
        add(emptyState);
        refreshGrid();
    }

    private void configureGrid() {
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        grid.setSizeFull();

        grid.addColumn(AppUser::getUsername).setHeader("Kullanıcı Adı").setSortable(true).setAutoWidth(true);
        grid.addColumn(AppUser::getFullName).setHeader("Ad Soyad").setSortable(true).setAutoWidth(true);
        grid.addColumn(AppUser::getRole).setHeader("Rol").setSortable(true).setAutoWidth(true);

        grid.addComponentColumn(user -> {
            Span badge = new Span(user.getActive() ? "Aktif" : "Pasif");
            badge.getElement().getThemeList().add("badge " + (user.getActive() ? "success" : "error"));
            return badge;
        }).setHeader("Durum").setAutoWidth(true);

        grid.addColumn(user -> user.getCreatedAt() != null ?
                        user.getCreatedAt().toLocalDate().toString() : "-")
                .setHeader("Kayıt Tarihi").setAutoWidth(true);

        grid.addComponentColumn(user -> {
            Button toggleBtn = new Button(
                    user.getActive() ? "Devre Dışı Bırak" : "Aktifleştir",
                    e -> {
                        try {
                            if (user.getActive()) {
                                userService.deactivateUser(user.getId());
                                Notification.show("Kullanıcı devre dışı bırakıldı.", 3000, Notification.Position.BOTTOM_CENTER)
                                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                            } else {
                                userService.activateUser(user.getId());
                                Notification.show("Kullanıcı aktifleştirildi.", 3000, Notification.Position.BOTTOM_CENTER)
                                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                            }
                            refreshGrid();
                        } catch (Exception ex) {
                            Notification.show("Bir hata oluştu, lütfen tekrar deneyin.", 3000, Notification.Position.BOTTOM_CENTER)
                                    .addThemeVariants(NotificationVariant.LUMO_ERROR);
                        }
                    }
            );
            toggleBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);
            
            if (Role.ADMIN.equals(user.getRole()) && user.getActive()) {
                toggleBtn.setEnabled(false);
                toggleBtn.setTooltipText("Yöneticiler devre dışı bırakılamaz.");
            }

            Button resetPwdBtn = new Button("Şifre Sıfırla", e -> openPasswordResetDialog(user));
            resetPwdBtn.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_TERTIARY);

            return new HorizontalLayout(toggleBtn, resetPwdBtn);
        }).setHeader("İşlemler").setAutoWidth(true);
    }

    private HorizontalLayout createToolbar() {
        H3 title = new H3("Kullanıcı Yönetimi");
        title.getStyle().set("margin", "0");

        Button addBtn = new Button("Yeni Kullanıcı", new Icon(VaadinIcon.PLUS), e -> openCreateDialog());
        addBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(title, addBtn);
        toolbar.setAlignItems(Alignment.CENTER);
        toolbar.setWidthFull();
        toolbar.expand(title);

        return toolbar;
    }

    private void openCreateDialog() {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Yeni Kullanıcı");
        dialog.setWidth("400px");

        FormLayout form = new FormLayout();

        TextField usernameField = new TextField("Kullanıcı Adı");
        usernameField.setRequired(true);

        PasswordField passwordField = new PasswordField("Şifre");
        passwordField.setRequired(true);
        passwordField.setMinLength(8);
        passwordField.setHelperText("En az 8 karakter, 1 büyük harf ve 1 rakam içermelidir");

        TextField fullNameField = new TextField("Ad Soyad");

        ComboBox<Role> roleField = new ComboBox<>("Rol");
        roleField.setItems(Role.values());
        roleField.setValue(Role.USER);
        roleField.setItemLabelGenerator(Role::name);

        form.add(usernameField, passwordField, fullNameField, roleField);

        Button saveBtn = new Button("Kaydet", e -> {
            if (usernameField.getValue().isEmpty() || passwordField.getValue().isEmpty()) {
                Notification.show("Kullanıcı adı ve şifre zorunludur", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                userService.createUser(
                        usernameField.getValue(),
                        passwordField.getValue(),
                        fullNameField.getValue(),
                        roleField.getValue()
                );
                Notification.show("Kullanıcı oluşturuldu", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
                refreshGrid();
            } catch (IllegalArgumentException ex) {
                Notification.show(ex.getMessage() != null ? ex.getMessage() : "Bir hata oluştu, lütfen tekrar deneyin.", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
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

    private void openPasswordResetDialog(AppUser user) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle("Şifre Sıfırla - " + user.getUsername());
        dialog.setWidth("400px");

        FormLayout form = new FormLayout();

        PasswordField newPasswordField = new PasswordField("Yeni Şifre");
        newPasswordField.setRequired(true);
        newPasswordField.setMinLength(4);

        PasswordField confirmPasswordField = new PasswordField("Şifre Tekrar");
        confirmPasswordField.setRequired(true);

        form.add(newPasswordField, confirmPasswordField);

        Button saveBtn = new Button("Şifreyi Güncelle", e -> {
            String newPassword = newPasswordField.getValue();
            String confirmPassword = confirmPasswordField.getValue();

            if (newPassword.isEmpty() || confirmPassword.isEmpty()) {
                Notification.show("Lütfen tüm alanları doldurun", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            if (!newPassword.equals(confirmPassword)) {
                Notification.show("Şifreler eşleşmiyor", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            try {
                userService.updatePassword(user.getId(), newPassword);
                Notification.show("Şifre başarıyla güncellendi", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                dialog.close();
            } catch (Exception ex) {
                Notification.show("Bir hata oluştu, lütfen tekrar deneyin.",
                                3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
            }
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
        Span emptyIcon = new Span("\uD83D\uDC64");
        emptyIcon.getStyle().set("font-size", "3em").set("display", "block");
        Span emptyText = new Span("Henüz kullanıcı kaydı bulunmuyor.");
        emptyText.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "1.1em")
                .set("margin-top", "1em");
        emptyState.add(emptyIcon, emptyText);
    }

    private void refreshGrid() {
        loadingBar.setVisible(true);
        List<AppUser> users = userService.findAll();
        grid.setItems(users);
        if (users.isEmpty()) {
            grid.setVisible(false);
            emptyState.getStyle().set("display", "flex");
        } else {
            grid.setVisible(true);
            emptyState.getStyle().set("display", "none");
        }
        loadingBar.setVisible(false);
    }
}
