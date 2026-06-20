package com.raspel.cardtracker.domain.user;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class DashboardConfig {

    private List<WidgetConfig> widgets = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class WidgetConfig {
        private String id;
        private String label;
        private boolean visible;
        private int order;
    }

    /**
     * Varsayılan dashboard konfigürasyonunu oluşturur.
     */
    public static DashboardConfig createDefault() {
        DashboardConfig config = new DashboardConfig();
        List<WidgetConfig> widgets = new ArrayList<>();
        widgets.add(new WidgetConfig("limit_warnings", "Limit Uyarıları", true, 1));
        widgets.add(new WidgetConfig("currency_rates", "Döviz & Altın Kurları", true, 2));
        widgets.add(new WidgetConfig("payment_reminders", "Ödeme Hatırlatıcıları (Yaklaşan/Geciken)", true, 3));
        widgets.add(new WidgetConfig("summary_cards", "Özet Kartları", true, 4));
        widgets.add(new WidgetConfig("charts_row", "Kart Dağılımı & Projeksiyon", true, 5));
        widgets.add(new WidgetConfig("second_row", "Departman & Ödeme Takvimi", true, 6));
        widgets.add(new WidgetConfig("third_row", "Bütçe & Nakit Akışı", true, 7));
        widgets.add(new WidgetConfig("fourth_row", "12 Aylık Harcama Trendi", true, 8));
        config.setWidgets(widgets);
        return config;
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * JSON string'den DashboardConfig nesnesine dönüştürür.
     */
    public static DashboardConfig fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return createDefault();
        }
        try {
            DashboardConfig config = MAPPER.readValue(json, DashboardConfig.class);
            
            // Yeni eklenen varsayılan widget'ları mevcut kullanıcı konfigürasyonuna ekle
            DashboardConfig defaultCfg = createDefault();
            boolean updated = false;
            for (WidgetConfig defaultWidget : defaultCfg.getWidgets()) {
                boolean exists = config.getWidgets().stream()
                        .anyMatch(w -> w.getId().equals(defaultWidget.getId()));
                if (!exists) {
                    config.getWidgets().add(defaultWidget);
                    updated = true;
                }
            }
            if (updated) {
                // Widget'ları sıralarına göre yeniden sırala
                config.getWidgets().sort(java.util.Comparator.comparingInt(WidgetConfig::getOrder));
            }
            
            return config;
        } catch (JsonProcessingException e) {
            return createDefault();
        }
    }

    /**
     * DashboardConfig nesnesini JSON string'e dönüştürür.
     */
    public String toJson() {
        try {
            return MAPPER.writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    /**
     * Widget'ları sıraya göre sıralanmış olarak döndürür.
     */
    public List<WidgetConfig> getOrderedWidgets() {
        List<WidgetConfig> sorted = new ArrayList<>(widgets);
        sorted.sort((a, b) -> Integer.compare(a.getOrder(), b.getOrder()));
        return sorted;
    }

    /**
     * Görünür widget'ları sıraya göre döndürür.
     */
    public List<WidgetConfig> getVisibleWidgets() {
        List<WidgetConfig> visible = new ArrayList<>();
        for (WidgetConfig w : getOrderedWidgets()) {
            if (w.isVisible()) {
                visible.add(w);
            }
        }
        return visible;
    }
}
