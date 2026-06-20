package com.raspel.cardtracker.ui.utils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public class HolidayUtils {

    private static final Set<String> HOLIDAYS = new HashSet<>();

    static {
        // Sabit Resmi Tatiller (GG-AA formatında)
        HOLIDAYS.add("01-01"); // Yılbaşı
        HOLIDAYS.add("23-04"); // Ulusal Egemenlik ve Çocuk Bayramı
        HOLIDAYS.add("01-05"); // Emek ve Dayanışma Günü
        HOLIDAYS.add("19-05"); // Atatürk'ü Anma, Gençlik ve Spor Bayramı
        HOLIDAYS.add("15-07"); // Demokrasi ve Milli Birlik Günü
        HOLIDAYS.add("30-08"); // Zafer Bayramı
        HOLIDAYS.add("29-10"); // Cumhuriyet Bayramı

        // Dini Bayramlar (Yıl-AA-GG formatında, 2025-2027 arası)
        // 2025 Dini Bayramlar
        HOLIDAYS.add("2025-03-29"); // Ramazan Bayramı 1. Günü
        HOLIDAYS.add("2025-03-30"); // Ramazan Bayramı 2. Günü
        HOLIDAYS.add("2025-03-31"); // Ramazan Bayramı 3. Günü
        HOLIDAYS.add("2025-06-05"); // Kurban Bayramı Arife Günü
        HOLIDAYS.add("2025-06-06"); // Kurban Bayramı 1. Günü
        HOLIDAYS.add("2025-06-07"); // Kurban Bayramı 2. Günü
        HOLIDAYS.add("2025-06-08"); // Kurban Bayramı 3. Günü
        HOLIDAYS.add("2025-06-09"); // Kurban Bayramı 4. Günü

        // 2026 Dini Bayramlar
        HOLIDAYS.add("2026-03-19"); // Ramazan Bayramı 1. Günü
        HOLIDAYS.add("2026-03-20"); // Ramazan Bayramı 2. Günü
        HOLIDAYS.add("2026-03-21"); // Ramazan Bayramı 3. Günü
        HOLIDAYS.add("2026-05-26"); // Kurban Bayramı 1. Günü
        HOLIDAYS.add("2026-05-27"); // Kurban Bayramı 2. Günü
        HOLIDAYS.add("2026-05-28"); // Kurban Bayramı 3. Günü
        HOLIDAYS.add("2026-05-29"); // Kurban Bayramı 4. Günü

        // 2027 Dini Bayramlar
        HOLIDAYS.add("2027-03-09"); // Ramazan Bayramı 1. Günü
        HOLIDAYS.add("2027-03-10"); // Ramazan Bayramı 2. Günü
        HOLIDAYS.add("2027-03-11"); // Ramazan Bayramı 3. Günü
        HOLIDAYS.add("2027-05-16"); // Kurban Bayramı 1. Günü
        HOLIDAYS.add("2027-05-17"); // Kurban Bayramı 2. Günü
        HOLIDAYS.add("2027-05-18"); // Kurban Bayramı 3. Günü
        HOLIDAYS.add("2027-05-19"); // Kurban Bayramı 4. Günü

        // 2028 Dini Bayramlar
        HOLIDAYS.add("2028-02-26"); // Ramazan Bayramı 1. Günü
        HOLIDAYS.add("2028-02-27"); // Ramazan Bayramı 2. Günü
        HOLIDAYS.add("2028-02-28"); // Ramazan Bayramı 3. Günü
        HOLIDAYS.add("2028-05-04"); // Kurban Bayramı 1. Günü
        HOLIDAYS.add("2028-05-05"); // Kurban Bayramı 2. Günü
        HOLIDAYS.add("2028-05-06"); // Kurban Bayramı 3. Günü
        HOLIDAYS.add("2028-05-07"); // Kurban Bayramı 4. Günü

        // 2029 Dini Bayramlar
        HOLIDAYS.add("2029-02-14"); // Ramazan Bayramı 1. Günü
        HOLIDAYS.add("2029-02-15"); // Ramazan Bayramı 2. Günü
        HOLIDAYS.add("2029-02-16"); // Ramazan Bayramı 3. Günü
        HOLIDAYS.add("2029-04-23"); // Kurban Bayramı 1. Günü
        HOLIDAYS.add("2029-04-24"); // Kurban Bayramı 2. Günü
        HOLIDAYS.add("2029-04-25"); // Kurban Bayramı 3. Günü
        HOLIDAYS.add("2029-04-26"); // Kurban Bayramı 4. Günü

        // 2030 Dini Bayramlar
        HOLIDAYS.add("2030-02-04"); // Ramazan Bayramı 1. Günü
        HOLIDAYS.add("2030-02-05"); // Ramazan Bayramı 2. Günü
        HOLIDAYS.add("2030-02-06"); // Ramazan Bayramı 3. Günü
        HOLIDAYS.add("2030-04-12"); // Kurban Bayramı 1. Günü
        HOLIDAYS.add("2030-04-13"); // Kurban Bayramı 2. Günü
        HOLIDAYS.add("2030-04-14"); // Kurban Bayramı 3. Günü
        HOLIDAYS.add("2030-04-15"); // Kurban Bayramı 4. Günü

        // 2031 Dini Bayramlar
        HOLIDAYS.add("2031-01-24"); // Ramazan Bayramı 1. Günü
        HOLIDAYS.add("2031-01-25"); // Ramazan Bayramı 2. Günü
        HOLIDAYS.add("2031-01-26"); // Ramazan Bayramı 3. Günü
        HOLIDAYS.add("2031-04-01"); // Kurban Bayramı 1. Günü
        HOLIDAYS.add("2031-04-02"); // Kurban Bayramı 2. Günü
        HOLIDAYS.add("2031-04-03"); // Kurban Bayramı 3. Günü
        HOLIDAYS.add("2031-04-04"); // Kurban Bayramı 4. Günü

        // 2032 Dini Bayramlar
        HOLIDAYS.add("2032-01-13"); // Ramazan Bayramı 1. Günü
        HOLIDAYS.add("2032-01-14"); // Ramazan Bayramı 2. Günü
        HOLIDAYS.add("2032-01-15"); // Ramazan Bayramı 3. Günü
        HOLIDAYS.add("2032-03-20"); // Kurban Bayramı 1. Günü
        HOLIDAYS.add("2032-03-21"); // Kurban Bayramı 2. Günü
        HOLIDAYS.add("2032-03-22"); // Kurban Bayramı 3. Günü
        HOLIDAYS.add("2032-03-23"); // Kurban Bayramı 4. Günü

        // 2033 Dini Bayramlar
        HOLIDAYS.add("2033-01-02"); // Ramazan Bayramı 1. Günü
        HOLIDAYS.add("2033-01-03"); // Ramazan Bayramı 2. Günü
        HOLIDAYS.add("2033-01-04"); // Ramazan Bayramı 3. Günü
        HOLIDAYS.add("2033-03-09"); // Kurban Bayramı 1. Günü
        HOLIDAYS.add("2033-03-10"); // Kurban Bayramı 2. Günü
        HOLIDAYS.add("2033-03-11"); // Kurban Bayramı 3. Günü
        HOLIDAYS.add("2033-03-12"); // Kurban Bayramı 4. Günü
        HOLIDAYS.add("2033-12-22"); // Ramazan Bayramı 1. Günü
        HOLIDAYS.add("2033-12-23"); // Ramazan Bayramı 2. Günü
        HOLIDAYS.add("2033-12-24"); // Ramazan Bayramı 3. Günü

        // 2034 Dini Bayramlar
        HOLIDAYS.add("2034-02-26"); // Kurban Bayramı 1. Günü
        HOLIDAYS.add("2034-02-27"); // Kurban Bayramı 2. Günü
        HOLIDAYS.add("2034-02-28"); // Kurban Bayramı 3. Günü
        HOLIDAYS.add("2034-03-01"); // Kurban Bayramı 4. Günü
        HOLIDAYS.add("2034-12-11"); // Ramazan Bayramı 1. Günü
        HOLIDAYS.add("2034-12-12"); // Ramazan Bayramı 2. Günü
        HOLIDAYS.add("2034-12-13"); // Ramazan Bayramı 3. Günü

        // 2035 Dini Bayramlar
        HOLIDAYS.add("2035-02-15"); // Kurban Bayramı 1. Günü
        HOLIDAYS.add("2035-02-16"); // Kurban Bayramı 2. Günü
        HOLIDAYS.add("2035-02-17"); // Kurban Bayramı 3. Günü
        HOLIDAYS.add("2035-02-18"); // Kurban Bayramı 4. Günü
        HOLIDAYS.add("2035-12-01"); // Ramazan Bayramı 1. Günü
        HOLIDAYS.add("2035-12-02"); // Ramazan Bayramı 2. Günü
        HOLIDAYS.add("2035-12-03"); // Ramazan Bayramı 3. Günü

        // 2036 Dini Bayramlar
        HOLIDAYS.add("2036-02-04"); // Kurban Bayramı 1. Günü
        HOLIDAYS.add("2036-02-05"); // Kurban Bayramı 2. Günü
        HOLIDAYS.add("2036-02-06"); // Kurban Bayramı 3. Günü
        HOLIDAYS.add("2036-02-07"); // Kurban Bayramı 4. Günü
        HOLIDAYS.add("2036-11-19"); // Ramazan Bayramı 1. Günü
        HOLIDAYS.add("2036-11-20"); // Ramazan Bayramı 2. Günü
        HOLIDAYS.add("2036-11-21"); // Ramazan Bayramı 3. Günü
    }

    /**
     * Verilen tarihin hafta sonu veya resmi/dini tatil olup olmadığını kontrol eder.
     */
    public static boolean isWeekendOrHoliday(LocalDate date) {
        if (date == null) {
            return false;
        }

        // Hafta sonu kontrolü (Cumartesi veya Pazar)
        DayOfWeek dow = date.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
            return true;
        }

        // Sabit Resmi Tatiller kontrolü (GG-AA)
        String keyFixed = String.format("%02d-%02d", date.getDayOfMonth(), date.getMonthValue());
        if (HOLIDAYS.contains(keyFixed)) {
            return true;
        }

        // Değişken Dini Bayramlar kontrolü (YYYY-AA-GG)
        String keyVariable = date.toString();
        if (HOLIDAYS.contains(keyVariable)) {
            return true;
        }

        return false;
    }

    /**
     * Eğer tarih tatil veya hafta sonu ise bir sonraki ilk iş gününü döner.
     */
    public static LocalDate getNextBusinessDay(LocalDate date) {
        if (date == null) {
            return null;
        }
        LocalDate current = date;
        while (isWeekendOrHoliday(current)) {
            current = current.plusDays(1);
        }
        return current;
    }
}
