package com.raspel.cardtracker.config;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.server.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

@Component
public class GlobalExceptionHandler implements ErrorHandler, VaadinServiceInitListener {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addSessionInitListener(sessionInitEvent ->
            sessionInitEvent.getSession().setErrorHandler(this)
        );
    }

    @Override
    public void error(ErrorEvent event) {
        Throwable throwable = event.getThrowable();
        if (throwable == null) {
            Notification.show("Bir hata oluştu. Lütfen daha sonra tekrar deneyin.",
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
            return;
        }

        log.error("Unhandled exception", throwable);

        if (throwable instanceof AccessDeniedException) {
            Notification.show("Bu sayfaya erişim yetkiniz bulunmamaktadır.",
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        } else {
            Notification.show("Bir hata oluştu. Lütfen daha sonra tekrar deneyin.",
                5000, Notification.Position.MIDDLE)
                .addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }
}
