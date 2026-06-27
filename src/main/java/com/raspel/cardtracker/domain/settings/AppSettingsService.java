package com.raspel.cardtracker.domain.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AppSettingsService {

    private static final String KEY_COMPANY_NAME = "company_name";
    private static final String KEY_SENT_ALERTS = "sent_alerts";
    private static final String DEFAULT_COMPANY_NAME = "Bozkır Ağaç Ürünleri";

    private final AppSettingsRepository appSettingsRepository;

    @Cacheable("companyName")
    public String getCompanyName() {
        return appSettingsRepository.findById(KEY_COMPANY_NAME)
                .map(AppSettings::getSettingValue)
                .orElse(DEFAULT_COMPANY_NAME);
    }

    @CacheEvict(value = "companyName", allEntries = true)
    public void setCompanyName(String name) {
        AppSettings setting = appSettingsRepository.findById(KEY_COMPANY_NAME)
                .orElse(new AppSettings(KEY_COMPANY_NAME, name));
        setting.setSettingValue(name);
        appSettingsRepository.save(setting);
    }

    public boolean isSetupComplete() {
        return appSettingsRepository.findById(KEY_COMPANY_NAME)
                .map(s -> s.getSettingValue() != null && !s.getSettingValue().trim().isEmpty())
                .orElse(false);
    }

    public java.util.Set<String> getSentAlerts() {
        String raw = appSettingsRepository.findById(KEY_SENT_ALERTS)
                .map(AppSettings::getSettingValue).orElse("");
        if (raw.isEmpty()) return java.util.concurrent.ConcurrentHashMap.newKeySet();
        java.util.Set<String> set = java.util.concurrent.ConcurrentHashMap.newKeySet();
        for (String key : raw.split(",")) {
            if (!key.trim().isEmpty()) set.add(key.trim());
        }
        return set;
    }

    public void saveSentAlerts(java.util.Set<String> alerts) {
        String raw = String.join(",", alerts);
        AppSettings setting = appSettingsRepository.findById(KEY_SENT_ALERTS)
                .orElse(new AppSettings(KEY_SENT_ALERTS, raw));
        setting.setSettingValue(raw);
        appSettingsRepository.save(setting);
    }
}
