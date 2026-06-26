package com.raspel.cardtracker.ui.utils;

import com.vaadin.flow.component.datepicker.DatePicker;
import java.util.List;

public class TurkishDatePickerI18n {

    private static final DatePicker.DatePickerI18n INSTANCE = new DatePicker.DatePickerI18n();

    static {
        INSTANCE.setDateFormat("dd/MM/yyyy");
        INSTANCE.setMonthNames(List.of("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
                "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"));
        INSTANCE.setWeekdays(List.of("Pazar", "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi"));
        INSTANCE.setWeekdaysShort(List.of("Paz", "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt"));
        INSTANCE.setToday("Bugün");
        INSTANCE.setCancel("İptal");
        INSTANCE.setFirstDayOfWeek(1);
    }

    public static DatePicker.DatePickerI18n get() {
        return INSTANCE;
    }

    public static void applyTo(DatePicker datePicker) {
        datePicker.setI18n(INSTANCE);
    }
}
