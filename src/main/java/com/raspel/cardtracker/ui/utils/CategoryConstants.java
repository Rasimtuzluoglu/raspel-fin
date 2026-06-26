package com.raspel.cardtracker.ui.utils;

import java.util.List;

public class CategoryConstants {

    public static final List<String> EXPENSE_CATEGORIES = List.of(
            "Ofis Giderleri", "Seyahat", "Tedarik", "IT", "Yemek", "Yakıt", "Genel"
    );

    public static final List<String> CARD_CATEGORIES = List.of(
            "Ofis Giderleri", "Seyahat", "Tedarik", "Genel", "IT", "Pazarlama"
    );

    public static final List<String> NOTE_CATEGORIES = List.of(
            "Ödemeler", "Hatırlatmalar", "Kişisel", "İş", "Alışveriş"
    );

    public static final List<String> EXPENSE_TAGS = List.of(
            "Zorunlu", "İsteğe Bağlı", "Ertelenebilir"
    );
}
