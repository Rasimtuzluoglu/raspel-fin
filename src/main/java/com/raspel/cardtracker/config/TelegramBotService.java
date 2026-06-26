package com.raspel.cardtracker.config;

import com.raspel.cardtracker.domain.user.AppUser;
import com.raspel.cardtracker.domain.user.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.Optional;

@Component
@ConditionalOnExpression("T(org.springframework.util.StringUtils).hasText(environment.getProperty('TELEGRAM_BOT_TOKEN'))")
@Slf4j
public class TelegramBotService extends TelegramLongPollingBot {

    private final UserRepository userRepository;
    private final Environment environment;

    public TelegramBotService(UserRepository userRepository, Environment environment) {
        this.userRepository = userRepository;
        this.environment = environment;
    }

    @Override
    public String getBotUsername() {
        return "raspel_fin_bot";
    }

    @Override
    public String getBotToken() {
        return environment.getProperty("TELEGRAM_BOT_TOKEN");
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String text = update.getMessage().getText().trim();
        Long chatId = update.getMessage().getChatId();
        String firstName = update.getMessage().getFrom().getFirstName();

        if ("/start".equalsIgnoreCase(text)) {
            sendWelcomeMessage(chatId, firstName);
            return;
        }

        if (text.startsWith("/")) {
            sendMessage(chatId, "Geçersiz komut. Sadece /start veya doğrulama kodunuzu gönderebilirsiniz.");
            return;
        }

        handleVerificationCode(chatId, text, firstName);
    }

    private void sendWelcomeMessage(Long chatId, String firstName) {
        String msg = "Merhaba " + (firstName != null ? firstName : "") + "! \uD83D\uDC4B\n\n" +
                "RasPel Finans Yönetim Bot'una hoş geldiniz.\n\n" +
                "Bu bot, web panel hesabınızı Telegram'a bağlamak için kullanılır.\n\n" +
                "Bağlantı kurmak için:\n" +
                "1. Web panelinde Profilim sayfasına gidin\n" +
                "2. \"Telegram'a Bağlan\" butonuna tıklayın\n" +
                "3. Size verilen 6 haneli doğrulama kodunu buraya gönderin\n\n" +
                "Kodunuzu bekliyorum...";
        sendMessage(chatId, msg);
    }

    private void handleVerificationCode(Long chatId, String code, String firstName) {
        if (code.length() < 4 || code.length() > 20) {
            sendMessage(chatId, "Geçersiz kod formatı. Lütfen web panelinden aldığınız doğrulama kodunu girin.");
            return;
        }

        Optional<AppUser> userOpt = userRepository.findByTelegramVerificationCode(code);

        if (userOpt.isEmpty()) {
            sendMessage(chatId, "Bu doğrulama kodu bulunamadı. Lütfen web panelinden yeni bir kod alın.");
            return;
        }

        AppUser user = userOpt.get();

        Optional<AppUser> existingChatUser = userRepository.findByTelegramChatId(chatId);
        if (existingChatUser.isPresent() && !existingChatUser.get().getId().equals(user.getId())) {
            sendMessage(chatId, "Bu Telegram hesabı zaten başka bir kullanıcıya bağlı.");
            return;
        }

        userRepository.linkTelegramChatId(user.getId(), chatId);
        log.info("Telegram bağlantısı kuruldu: {} -> chatId={}", user.getUsername(), chatId);

        String msg = "Doğrulama başarılı! \u2705\n\n" +
                "Telegram hesabınız \"" + user.getUsername() + "\" kullanıcısına bağlandı.\n\n" +
                "Artık bildirimleri buradan alabilirsiniz.";
        sendMessage(chatId, msg);
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage msg = new SendMessage(chatId.toString(), text);
        msg.enableHtml(true);
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            log.error("Telegram mesajı gönderilemedi: {}", e.getMessage());
        }
    }
}
