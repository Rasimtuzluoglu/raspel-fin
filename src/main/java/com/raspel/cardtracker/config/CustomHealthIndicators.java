package com.raspel.cardtracker.config;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CustomHealthIndicators {

    @Component("telegramBot")
    public static class TelegramBotHealthIndicator extends AbstractHealthIndicator {
        @Autowired(required = false)
        private TelegramBotService telegramBotService;

        @Override
        protected void doHealthCheck(Health.Builder builder) {
            if (telegramBotService != null) {
                builder.up().withDetail("bot", "@raspel_fin_bot");
            } else {
                builder.down().withDetail("reason", "Bot not configured");
            }
        }
    }

    @Component("currencyService")
    public static class CurrencyServiceHealthIndicator extends AbstractHealthIndicator {
        @Autowired(required = false)
        private com.raspel.cardtracker.domain.expense.TcmbCurrencyService currencyService;

        @Override
        protected void doHealthCheck(Health.Builder builder) {
            if (currencyService != null) {
                builder.up();
            } else {
                builder.unknown();
            }
        }
    }
}
