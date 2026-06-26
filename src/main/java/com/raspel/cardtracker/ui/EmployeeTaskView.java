package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.datepicker.DatePicker;
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
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.textfield.TextArea;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.raspel.cardtracker.domain.employee.Employee;
import com.raspel.cardtracker.domain.employee.EmployeeService;
import com.raspel.cardtracker.domain.employee.EmployeeTask;
import com.raspel.cardtracker.domain.employee.TaskPriority;
import com.raspel.cardtracker.domain.employee.TaskStatus;
import com.raspel.cardtracker.ui.utils.TurkishDatePickerI18n;
import jakarta.annotation.security.PermitAll;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Locale;

@Route(value = "employee-tasks", layout = MainLayout.class)
@PageTitle("Eleman & Görev Takibi")
@PermitAll
public class EmployeeTaskView extends VerticalLayout {

    private final EmployeeService employeeService;
    private final boolean isAdmin;

    private final Grid<Employee> employeeGrid = new Grid<>(Employee.class, false);
    private final Grid<EmployeeTask> taskGrid = new Grid<>(EmployeeTask.class, false);
    private final ProgressBar loadingBar = new ProgressBar();

    private final Div contentDiv = new Div();
    private final Tab taskTab = new Tab("Görev Atamaları");
    private final Tab employeeTab = new Tab("Eleman Yönetimi");
    private final Tabs tabs = new Tabs(taskTab, employeeTab);

    private final Div taskEmptyState = new Div();
    private final Div employeeEmptyState = new Div();

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public EmployeeTaskView(EmployeeService employeeService) {
        this.employeeService = employeeService;
        
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        this.isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        setSizeFull();
        setPadding(true);
        setSpacing(true);

        loadingBar.setIndeterminate(true);
        loadingBar.setVisible(false);
        loadingBar.setWidthFull();
        add(loadingBar);

        createHeader();
        createTabs();
        createGrids();
        configureEmptyStates();

        add(tabs, contentDiv);
        showTaskContent();
    }

    private void createHeader() {
        H3 title = new H3("Görev & Eleman Takip Sistemi");
        title.getStyle().set("margin", "0");
        add(title);
    }

    private void createTabs() {
        tabs.setWidthFull();
        tabs.addSelectedChangeListener(event -> {
            if (tabs.getSelectedTab().equals(employeeTab)) {
                showEmployeeContent();
            } else {
                showTaskContent();
            }
        });
    }

    private void createGrids() {
        // --- Eleman Tablosu ---
        employeeGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        employeeGrid.setSizeFull();

        employeeGrid.addColumn(Employee::getFirstName).setHeader("Ad").setSortable(true).setAutoWidth(true);
        employeeGrid.addColumn(Employee::getLastName).setHeader("Soyad").setSortable(true).setAutoWidth(true);
        employeeGrid.addColumn(Employee::getDepartment).setHeader("Departman").setSortable(true).setAutoWidth(true);
        employeeGrid.addColumn(Employee::getEmail).setHeader("E-Posta").setAutoWidth(true);
        employeeGrid.addColumn(Employee::getPhone).setHeader("Telefon").setAutoWidth(true);
        
        employeeGrid.addComponentColumn(emp -> {
            Span badge = new Span(emp.getActive() ? "Aktif" : "Pasif");
            badge.getElement().getThemeList().add("badge " + (emp.getActive() ? "success" : "error"));
            return badge;
        }).setHeader("Durum").setAutoWidth(true);

        if (isAdmin) {
            employeeGrid.addComponentColumn(emp -> {
                Button editBtn = new Button("Düzenle", new Icon(VaadinIcon.EDIT), e -> openEmployeeDialog(emp));
                editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

                Button deleteBtn = new Button("Sil", new Icon(VaadinIcon.TRASH), e -> {
                    try {
                        employeeService.deleteEmployee(emp.getId());
                        Notification.show("Eleman silindi.", 3000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                        refreshEmployeeGrid();
                    } catch (Exception ex) {
                        Notification.show("Bir hata oluştu, lütfen tekrar deneyin.", 3000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    }
                });
                deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

                return new HorizontalLayout(editBtn, deleteBtn);
            }).setHeader("İşlemler").setAutoWidth(true);
        }

        // --- Görev Tablosu ---
        taskGrid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);
        taskGrid.setSizeFull();

        taskGrid.addColumn(EmployeeTask::getTitle).setHeader("Görev Başlığı").setSortable(true).setAutoWidth(true);
        taskGrid.addColumn(EmployeeTask::getDescription).setHeader("Açıklama").setAutoWidth(true);
        
        taskGrid.addColumn(task -> task.getAssignedTo() != null ? task.getAssignedTo().getFullName() : "Atanmadı")
                .setHeader("Atanan Eleman").setSortable(true).setAutoWidth(true);

        taskGrid.addColumn(task -> task.getDueDate() != null ? task.getDueDate().format(dateFormatter) : "-")
                .setHeader("Son Tarih").setSortable(true).setAutoWidth(true);

        taskGrid.addComponentColumn(task -> {
            Span badge = new Span(task.getStatus().getLabel());
            if (task.getStatus() == TaskStatus.COMPLETED) {
                badge.getElement().getThemeList().add("badge success");
            } else if (task.getStatus() == TaskStatus.IN_PROGRESS) {
                badge.getElement().getThemeList().add("badge contrast");
            } else {
                badge.getElement().getThemeList().add("badge");
            }
            return badge;
        }).setHeader("Durum").setAutoWidth(true);

        taskGrid.addComponentColumn(task -> {
            Span badge = new Span(task.getPriority().getLabel());
            if (task.getPriority() == TaskPriority.HIGH) {
                badge.getElement().getThemeList().add("badge error");
            } else if (task.getPriority() == TaskPriority.MEDIUM) {
                badge.getElement().getThemeList().add("badge contrast");
            } else {
                badge.getElement().getThemeList().add("badge success");
            }
            return badge;
        }).setHeader("Önem Derecesi").setAutoWidth(true);

        taskGrid.addComponentColumn(task -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            String currentUsername = auth != null ? auth.getName() : "";
            Button editBtn = new Button(new Icon(VaadinIcon.EDIT), e -> openTaskDialog(task));
            editBtn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);

            Button deleteBtn = new Button(new Icon(VaadinIcon.TRASH), e -> {
                employeeService.deleteTask(task.getId());
                Notification.show("Görev silindi.", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                refreshTaskGrid();
            });
            deleteBtn.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);

            HorizontalLayout actions = new HorizontalLayout();
            actions.setSpacing(true);
            actions.setPadding(false);
            actions.getStyle().set("flex-wrap", "nowrap");
            if (task.getStatus() != TaskStatus.COMPLETED) {
                Button completeBtn = new Button(new Icon(VaadinIcon.CHECK), e -> {
                    task.setStatus(TaskStatus.COMPLETED);
                    employeeService.saveTask(task);
                    Notification.show("Görev tamamlandı.", 3000, Notification.Position.BOTTOM_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                    refreshTaskGrid();
                });
                completeBtn.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
                completeBtn.getElement().setAttribute("title", "Tamamlandı");
                actions.add(completeBtn);
            }
            if (task.getStatus() == TaskStatus.COMPLETED) {
                Button undoBtn = new Button(new Icon(VaadinIcon.ARROW_BACKWARD), e -> {
                    Dialog confirm = new Dialog();
                    confirm.setHeaderTitle("Görev Geri Alınsın mı?");
                    confirm.add(new Span("\"" + task.getTitle() + "\" görevini tekrar yapılacaklar listesine almak istediğinize emin misiniz?"));
                    Button yesBtn = new Button("Evet, Geri Al", ev -> {
                        task.setStatus(TaskStatus.TODO);
                        employeeService.saveTask(task);
                        Notification.show("Görev geri alındı.", 3000, Notification.Position.BOTTOM_CENTER)
                                .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
                        confirm.close();
                        refreshTaskGrid();
                    });
                    yesBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
                    yesBtn.addClickShortcut(com.vaadin.flow.component.Key.ENTER);
                    Button noBtn = new Button("İptal", ev -> confirm.close());
                    noBtn.addClickShortcut(com.vaadin.flow.component.Key.ESCAPE);
                    confirm.getFooter().add(noBtn, yesBtn);
                    confirm.open();
                });
                undoBtn.addThemeVariants(ButtonVariant.LUMO_CONTRAST, ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_ICON);
                undoBtn.getElement().setAttribute("title", "Geri Al");
                actions.add(undoBtn);
            }
            boolean canEdit = isAdmin || (task.getCreatedBy() != null && task.getCreatedBy().equals(currentUsername));
            if (canEdit) {
                editBtn.getElement().setAttribute("title", "Düzenle");
                deleteBtn.getElement().setAttribute("title", "Sil");
                actions.add(editBtn, deleteBtn);
            }
            return actions;
        }).setHeader("İşlemler").setWidth("200px").setFlexGrow(0);
    }

    private void showEmployeeContent() {
        contentDiv.removeAll();
        contentDiv.setSizeFull();

        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);

        employeeGrid.setItems(employeeService.findAllEmployees());
        toggleEmployeeEmptyState();

        if (isAdmin) {
            Button newEmpBtn = new Button("Yeni Eleman Ekle", new Icon(VaadinIcon.PLUS), e -> openEmployeeDialog(new Employee()));
            newEmpBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            layout.add(newEmpBtn, employeeGrid, employeeEmptyState);
        } else {
            layout.add(employeeGrid, employeeEmptyState);
        }

        contentDiv.add(layout);
    }

    private void showTaskContent() {
        contentDiv.removeAll();
        contentDiv.setSizeFull();

        VerticalLayout layout = new VerticalLayout();
        layout.setSizeFull();
        layout.setPadding(false);

        Button newTaskBtn = new Button("Yeni Görev Ata", new Icon(VaadinIcon.PLUS), e -> openTaskDialog(new EmployeeTask()));
        newTaskBtn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        java.util.List<EmployeeTask> allTasks = employeeService.findAllTasks();
        taskGrid.setItems(allTasks);
        toggleTaskEmptyState();

        layout.add(newTaskBtn, taskGrid, taskEmptyState);
        contentDiv.add(layout);
    }

    private void openEmployeeDialog(Employee employee) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(employee.getId() == null ? "Yeni Eleman" : "Elemanı Düzenle");
        dialog.setWidth("400px");

        FormLayout form = new FormLayout();
        TextField firstName = new TextField("Ad");
        firstName.setRequired(true);
        firstName.setValue(employee.getFirstName() != null ? employee.getFirstName() : "");

        TextField lastName = new TextField("Soyad");
        lastName.setRequired(true);
        lastName.setValue(employee.getLastName() != null ? employee.getLastName() : "");

        TextField department = new TextField("Departman");
        department.setValue(employee.getDepartment() != null ? employee.getDepartment() : "");

        TextField email = new TextField("E-Posta");
        email.setValue(employee.getEmail() != null ? employee.getEmail() : "");

        TextField phone = new TextField("Telefon");
        phone.setValue(employee.getPhone() != null ? employee.getPhone() : "");

        Checkbox active = new Checkbox("Aktif", employee.getActive() != null ? employee.getActive() : true);

        form.add(firstName, lastName, department, email, phone, active);

        Button saveBtn = new Button("Kaydet", e -> {
            if (firstName.isEmpty() || lastName.isEmpty()) {
                Notification.show("Ad ve Soyad alanları zorunludur.", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            String emailVal = email.getValue();
            if (emailVal != null && !emailVal.trim().isEmpty() && !emailVal.contains("@")) {
                Notification.show("Geçerli bir e-posta adresi girin.", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            String phoneVal = phone.getValue();
            if (phoneVal != null && !phoneVal.trim().isEmpty()) {
                String digits = phoneVal.trim().replaceAll("[^0-9]", "");
                if (digits.length() < 10) {
                    Notification.show("Geçerli bir telefon numarası girin (en az 10 rakam).", 3000, Notification.Position.BOTTOM_CENTER)
                            .addThemeVariants(NotificationVariant.LUMO_ERROR);
                    return;
                }
            }

            employee.setFirstName(firstName.getValue());
            employee.setLastName(lastName.getValue());
            employee.setDepartment(department.getValue());
            employee.setEmail(email.getValue());
            employee.setPhone(phone.getValue());
            employee.setActive(active.getValue());

            employeeService.saveEmployee(employee);
            Notification.show("Eleman kaydedildi.", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            dialog.close();
            refreshEmployeeGrid();
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

    private void openTaskDialog(EmployeeTask task) {
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(task.getId() == null ? "Yeni Görev" : "Görevi Düzenle");
        dialog.setWidth("450px");

        FormLayout form = new FormLayout();
        TextField title = new TextField("Görev Başlığı");
        title.setRequired(true);
        title.setValue(task.getTitle() != null ? task.getTitle() : "");

        TextArea description = new TextArea("Açıklama");
        description.setValue(task.getDescription() != null ? task.getDescription() : "");

        ComboBox<Employee> assignedTo = new ComboBox<>("Atanacak Eleman");
        assignedTo.setItems(employeeService.findAllActiveEmployees());
        assignedTo.setItemLabelGenerator(Employee::getFullName);
        assignedTo.setValue(task.getAssignedTo());

        DatePicker dueDate = new DatePicker("Son Tarih");
        TurkishDatePickerI18n.applyTo(dueDate);
        dueDate.setValue(task.getDueDate());

        ComboBox<TaskStatus> status = new ComboBox<>("Durum");
        status.setItems(Arrays.asList(TaskStatus.values()));
        status.setItemLabelGenerator(TaskStatus::getLabel);
        status.setValue(task.getStatus() != null ? task.getStatus() : TaskStatus.TODO);

        ComboBox<TaskPriority> priority = new ComboBox<>("Önem Derecesi");
        priority.setItems(Arrays.asList(TaskPriority.values()));
        priority.setItemLabelGenerator(TaskPriority::getLabel);
        priority.setValue(task.getPriority() != null ? task.getPriority() : TaskPriority.MEDIUM);

        form.add(title, description, assignedTo, dueDate, status, priority);

        Button saveBtn = new Button("Kaydet", e -> {
            if (title.isEmpty()) {
                Notification.show("Görev başlığı zorunludur.", 3000, Notification.Position.BOTTOM_CENTER)
                        .addThemeVariants(NotificationVariant.LUMO_ERROR);
                return;
            }

            task.setTitle(title.getValue());
            task.setDescription(description.getValue());
            task.setAssignedTo(assignedTo.getValue());
            task.setDueDate(dueDate.getValue());
            task.setStatus(status.getValue());
            task.setPriority(priority.getValue());
            if (task.getId() == null) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                task.setCreatedBy(auth != null ? auth.getName() : "unknown");
            }

            employeeService.saveTask(task);
            Notification.show("Görev kaydedildi.", 3000, Notification.Position.BOTTOM_CENTER)
                    .addThemeVariants(NotificationVariant.LUMO_SUCCESS);
            dialog.close();
            refreshTaskGrid();
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

    private void configureEmptyStates() {
        taskEmptyState.getStyle()
                .set("display", "none")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("text-align", "center")
                .set("flex", "1")
                .set("margin", "auto")
                .set("width", "100%");
        Span taskEmptyIcon = new Span("\uD83D\uDCCB");
        taskEmptyIcon.getStyle().set("font-size", "3em").set("display", "block");
        Span taskEmptyText = new Span("Henüz görev kaydı bulunmuyor.");
        taskEmptyText.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "1.1em")
                .set("margin-top", "1em");
        taskEmptyState.add(taskEmptyIcon, taskEmptyText);

        employeeEmptyState.getStyle()
                .set("display", "none")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("justify-content", "center")
                .set("text-align", "center")
                .set("flex", "1")
                .set("margin", "auto")
                .set("width", "100%");
        Span empEmptyIcon = new Span("\uD83D\uDC64");
        empEmptyIcon.getStyle().set("font-size", "3em").set("display", "block");
        Span empEmptyText = new Span("Henüz eleman kaydı bulunmuyor.");
        empEmptyText.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "1.1em")
                .set("margin-top", "1em");
        employeeEmptyState.add(empEmptyIcon, empEmptyText);
    }

    private void toggleTaskEmptyState() {
        if (taskGrid.getListDataView().getItems().findAny().isPresent()) {
            taskGrid.setVisible(true);
            taskEmptyState.getStyle().set("display", "none");
        } else {
            taskGrid.setVisible(false);
            taskEmptyState.getStyle().set("display", "flex");
        }
    }

    private void toggleEmployeeEmptyState() {
        if (employeeGrid.getListDataView().getItems().findAny().isPresent()) {
            employeeGrid.setVisible(true);
            employeeEmptyState.getStyle().set("display", "none");
        } else {
            employeeGrid.setVisible(false);
            employeeEmptyState.getStyle().set("display", "flex");
        }
    }

    private void refreshEmployeeGrid() {
        loadingBar.setVisible(true);
        employeeGrid.setItems(employeeService.findAllEmployees());
        toggleEmployeeEmptyState();
        loadingBar.setVisible(false);
    }

    private void refreshTaskGrid() {
        loadingBar.setVisible(true);
        java.util.List<EmployeeTask> allTasks = employeeService.findAllTasks();
        taskGrid.setItems(allTasks);
        toggleTaskEmptyState();
        loadingBar.setVisible(false);
    }
}
