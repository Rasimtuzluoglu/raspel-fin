package com.raspel.cardtracker.ui;

import com.vaadin.flow.component.html.H1;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.raspel.cardtracker.domain.settings.AppSettingsService;

@Route("login")
@PageTitle("Giriş")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm loginForm = new LoginForm();

    public LoginView(AppSettingsService appSettingsService) {
        addClassName("login-view");
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.CENTER);

        // Türkçe dil desteği
        LoginI18n i18n = LoginI18n.createDefault();
        LoginI18n.Form i18nForm = i18n.getForm();
        i18nForm.setTitle("Finansal Yönetim Portalı");
        i18nForm.setUsername("Kullanıcı Adı");
        i18nForm.setPassword("Şifre");
        i18nForm.setSubmit("Giriş Yap");
        i18nForm.setForgotPassword("");
        i18n.setForm(i18nForm);

        LoginI18n.ErrorMessage i18nErrorMessage = i18n.getErrorMessage();
        i18nErrorMessage.setTitle("Giriş Başarısız");
        i18nErrorMessage.setMessage("Kullanıcı adı veya şifre hatalı. Lütfen tekrar deneyin.");
        i18n.setErrorMessage(i18nErrorMessage);

        loginForm.setI18n(i18n);
        loginForm.setAction("login");
        loginForm.setForgotPasswordButtonVisible(false);

        H1 title = new H1(appSettingsService.getCompanyName());
        title.getStyle()
                .set("color", "#ffffff")
                .set("font-size", "2.5em")
                .set("text-shadow", "0 2px 4px rgba(0,0,0,0.3)")
                .set("margin-bottom", "0");

        Paragraph subtitle = new Paragraph("Finansal Yönetim ve Takip Sistemi");
        subtitle.getStyle()
                .set("color", "rgba(255, 255, 255, 0.85)")
                .set("font-size", "1.1em")
                .set("text-shadow", "0 1px 2px rgba(0,0,0,0.2)")
                .set("margin-top", "0.5em");

        Paragraph footerText = new Paragraph("© 2026 RasPel Co. | Yazılım: Rasim Tuzluoğlu");
        footerText.getStyle()
                .set("font-size", "0.85em")
                .set("color", "rgba(255, 255, 255, 0.65)")
                .set("margin-top", "2em");

        add(title, subtitle, loginForm, footerText);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            loginForm.setError(true);
        }
    }
}
