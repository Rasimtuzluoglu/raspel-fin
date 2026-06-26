package com.raspel.cardtracker.domain.settings;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppSettingsServiceTest {

    @Mock private AppSettingsRepository appSettingsRepository;

    @InjectMocks
    private AppSettingsService appSettingsService;

    @Test
    void getCompanyName_shouldReturnSavedValue() {
        AppSettings setting = new AppSettings("company_name", "RasPel Co.");
        when(appSettingsRepository.findById("company_name")).thenReturn(Optional.of(setting));

        String result = appSettingsService.getCompanyName();

        assertThat(result).isEqualTo("RasPel Co.");
    }

    @Test
    void getCompanyName_shouldReturnDefaultWhenNotSet() {
        when(appSettingsRepository.findById("company_name")).thenReturn(Optional.empty());

        String result = appSettingsService.getCompanyName();

        assertThat(result).isEqualTo("Bozkır Ağaç Ürünleri");
    }

    @Test
    void setCompanyName_shouldCreateNewSettingWhenNotExists() {
        when(appSettingsRepository.findById("company_name")).thenReturn(Optional.empty());
        when(appSettingsRepository.save(any(AppSettings.class))).thenAnswer(inv -> inv.getArgument(0));

        appSettingsService.setCompanyName("Yeni Firma");

        verify(appSettingsRepository).save(argThat(s ->
                "company_name".equals(s.getSettingKey()) && "Yeni Firma".equals(s.getSettingValue())));
    }

    @Test
    void setCompanyName_shouldUpdateExistingSetting() {
        AppSettings existing = new AppSettings("company_name", "Eski Firma");
        when(appSettingsRepository.findById("company_name")).thenReturn(Optional.of(existing));
        when(appSettingsRepository.save(any(AppSettings.class))).thenReturn(existing);

        appSettingsService.setCompanyName("Güncel Firma");

        assertThat(existing.getSettingValue()).isEqualTo("Güncel Firma");
        verify(appSettingsRepository).save(existing);
    }

    @Test
    void isSetupComplete_shouldReturnTrueWhenCompanyNameSet() {
        AppSettings setting = new AppSettings("company_name", "Test Firma");
        when(appSettingsRepository.findById("company_name")).thenReturn(Optional.of(setting));

        boolean result = appSettingsService.isSetupComplete();

        assertThat(result).isTrue();
    }

    @Test
    void isSetupComplete_shouldReturnFalseWhenCompanyNameEmpty() {
        AppSettings setting = new AppSettings("company_name", "   ");
        when(appSettingsRepository.findById("company_name")).thenReturn(Optional.of(setting));

        boolean result = appSettingsService.isSetupComplete();

        assertThat(result).isFalse();
    }

    @Test
    void isSetupComplete_shouldReturnFalseWhenNotSet() {
        when(appSettingsRepository.findById("company_name")).thenReturn(Optional.empty());

        boolean result = appSettingsService.isSetupComplete();

        assertThat(result).isFalse();
    }
}
