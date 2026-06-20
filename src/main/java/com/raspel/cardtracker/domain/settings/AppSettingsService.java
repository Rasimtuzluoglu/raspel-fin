package com.raspel.cardtracker.domain.settings;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class AppSettingsService {

    private static final String KEY_COMPANY_NAME = "company_name";
    private static final String DEFAULT_COMPANY_NAME = "Bozkır Ağaç Ürünleri";

    private final AppSettingsRepository appSettingsRepository;

    public String getCompanyName() {
        return appSettingsRepository.findById(KEY_COMPANY_NAME)
                .map(AppSettings::getSettingValue)
                .orElse(DEFAULT_COMPANY_NAME);
    }

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
}
