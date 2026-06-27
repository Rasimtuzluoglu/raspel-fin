package com.raspel.cardtracker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.theme.Theme;
import com.vaadin.flow.server.PWA;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableAsync
@Theme("cardtracker")
@PWA(name = "${app.pwa.name:Finansal Yönetim}", shortName = "${app.pwa.shortname:Finans}")
public class RaspelCardTrackerApplication implements AppShellConfigurator {
    public static void main(String[] args) {
        SpringApplication.run(RaspelCardTrackerApplication.class, args);
    }
}
