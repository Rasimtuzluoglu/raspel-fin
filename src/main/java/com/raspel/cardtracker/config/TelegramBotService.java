package com.raspel.cardtracker.config;

import com.raspel.cardtracker.domain.user.AppUser;
import com.raspel.cardtracker.domain.user.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.api.objects.commands.scope.BotCommandScopeDefault;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
@Lazy(false)
@Slf4j
public class TelegramBotService extends TelegramLongPollingBot {

    private final UserRepository userRepository;
    private final Environment environment;

    public TelegramBotService(UserRepository userRepository, Environment environment) {
        super(environment.getProperty("TELEGRAM_BOT_TOKEN", ""));
        this.userRepository = userRepository;
        this.environment = environment;
    }

    @PostConstruct
    void init() {
        if (environment.getProperty("TELEGRAM_BOT_TOKEN", "").isEmpty()) {
            log.info("TELEGRAM_BOT_TOKEN tanımlı değil, Telegram bot devre dışı.");
        } else {
            try {
                TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
                botsApi.registerBot(this);
                registerCommands();
                log.info("Telegram bot başlatıldı ve API'ye kaydedildi: @raspel_fin_bot");
            } catch (TelegramApiException e) {
                log.error("Telegram bot kaydedilemedi: {}", e.getMessage());
            }
        }
    }

    private void registerCommands() {
        List<BotCommand> commands = new ArrayList<>();
        commands.add(new BotCommand("start", "Bot'u başlat ve bilgi al"));
        commands.add(new BotCommand("help", "Yardım ve kullanım kılavuzu"));
        commands.add(new BotCommand("durum", "Hesap bağlantı durumunu sorgula"));
        commands.add(new BotCommand("baglantikes", "Telegram hesap bağlantısını kes"));
        try {
            execute(new SetMyCommands(commands, new BotCommandScopeDefault(), null));
        } catch (TelegramApiException e) {
            log.error("Bot komutları kaydedilemedi: {}", e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return "raspel_fin_bot";
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        String text = update.getMessage().getText().trim();
        Long chatId = update.getMessage().getChatId();
        String firstName = update.getMessage().getFrom().getFirstName();

        switch (text.toLowerCase()) {
            case "/start" -> sendWelcomeMessage(chatId, firstName);
            case "/help" -> sendHelpMessage(chatId);
            case "/durum" -> sendStatusMessage(chatId);
            case "/baglantikes" -> sendDisconnectConfirmation(chatId);
            default -> {
                if (text.startsWith("/")) {
                    sendMessage(chatId, "❌ Geçersiz komut. /help yazarak komut listesini görebilirsiniz.");
                } else {
                    handleVerificationCode(chatId, text, firstName);
                }
            }
        }
    }

    private void sendWelcomeMessage(Long chatId, String firstName) {
        String msg = "<b>Hoş Geldiniz!</b> \uD83D\uDC4B\n\n" +
                "<b>RasPel Finans Yönetim Bot'u</b>\n\n" +
                "Bu bot, web panel hesabınızı Telegram'a bağlamak için kullanılır.\n\n" +
                "<b>Bağlantı kurmak için:</b>\n" +
                "1️⃣ Web panelde <b>Profilim</b> sayfasına gidin\n" +
                "2️⃣ <b>Telegram'a Bağlan</b> butonuna tıklayın\n" +
                "3️⃣ Size verilen <b>6 haneli kodu</b> buraya gönderin\n\n" +
                "<i>Komutları görmek için /help yazabilirsiniz.</i>";
        sendMessage(chatId, msg);
    }

    private void sendHelpMessage(Long chatId) {
        String msg = "<b>📋 Kullanılabilir Komutlar</b>\n\n" +
                "<b>/start</b> - Bot'u başlat, bağlantı bilgisi al\n" +
                "<b>/help</b> - Bu yardım menüsü\n" +
                "<b>/durum</b> - Hesabınızın bağlantı durumunu gösterir\n" +
                "<b>/baglantikes</b> - Telegram bağlantısını keser\n\n" +
                "<b>🔗 Bağlantı Kurma:</b>\n" +
                "1. Web panel → Profilim → Telegram'a Bağlan\n" +
                "2. Aldığınız 6 haneli kodu buraya yazın\n\n" +
                "<i>Sorun yaşarsanız sistem yöneticinize başvurun.</i>";
        sendMessage(chatId, msg);
    }

    private void sendStatusMessage(Long chatId) {
        Optional<AppUser> userOpt = userRepository.findByTelegramChatId(chatId);
        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            String msg = "<b>✅ Bağlantı Durumu: Aktif</b>\n\n" +
                    "Kullanıcı: <b>" + escapeHtml(user.getUsername()) + "</b>\n" +
                    "Ad Soyad: <b>" + escapeHtml(user.getFullName() != null ? user.getFullName() : "-") + "</b>\n" +
                    "Chat ID: <code>" + chatId + "</code>\n\n" +
                    "<i>Bağlantıyı kesmek için /baglantikes yazın.</i>";
            sendMessage(chatId, msg);
        } else {
            String msg = "<b>❌ Bağlantı Durumu: Bağlı Değil</b>\n\n" +
                    "Bu Telegram hesabı henüz bir web panel kullanıcısına bağlanmamış.\n\n" +
                    "<b>Bağlanmak için:</b>\n" +
                    "1. Web panel → Profilim → Telegram'a Bağlan\n" +
                    "2. Aldığınız kodu buraya gönderin";
            sendMessage(chatId, msg);
        }
    }

    private void sendDisconnectConfirmation(Long chatId) {
        Optional<AppUser> userOpt = userRepository.findByTelegramChatId(chatId);
        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            user.setTelegramChatId(null);
            user.setTelegramVerificationCode(null);
            userRepository.save(user);
            log.info("Telegram bağlantısı kesildi: {} -> chatId={}", user.getUsername(), chatId);
            sendMessage(chatId, "<b>🔌 Bağlantı Kesildi</b>\n\n" +
                    "\"" + escapeHtml(user.getUsername()) + "\" hesabının Telegram bağlantısı kaldırıldı.\n\n" +
                    "Tekrar bağlanmak için web panelden yeni bir kod alıp /start ile başlayabilirsiniz.");
        } else {
            sendMessage(chatId, "❌ Bu Telegram hesabı zaten bir kullanıcıya bağlı değil.");
        }
    }

    private void handleVerificationCode(Long chatId, String code, String firstName) {
        if (code.length() < 4 || code.length() > 20) {
            sendMessage(chatId, "⚠️ Geçersiz kod. Lütfen web panelinden aldığınız 6 haneli doğrulama kodunu girin.");
            return;
        }

        Optional<AppUser> userOpt = userRepository.findByTelegramVerificationCode(code);

        if (userOpt.isEmpty()) {
            sendMessage(chatId, "❌ Bu doğrulama kodu bulunamadı.\n\n" +
                    "Kodun süresi dolmuş olabilir. Lütfen web panelinden <b>yeni bir kod</b> alın.");
            return;
        }

        AppUser user = userOpt.get();

        Optional<AppUser> existingChatUser = userRepository.findByTelegramChatId(chatId);
        if (existingChatUser.isPresent() && !existingChatUser.get().getId().equals(user.getId())) {
            sendMessage(chatId, "⚠️ Bu Telegram hesabı zaten başka bir kullanıcıya (<b>" +
                    escapeHtml(existingChatUser.get().getUsername()) + "</b>) bağlı.\n\n" +
                    "Önce o hesabın bağlantısını kesmeniz gerekiyor.");
            return;
        }

        userRepository.linkTelegramChatId(user.getId(), chatId);
        log.info("Telegram bağlantısı kuruldu: {} -> chatId={}", user.getUsername(), chatId);

        String msg = "<b>✅ Doğrulama Başarılı!</b>\n\n" +
                "Telegram hesabınız <b>\"" + escapeHtml(user.getUsername()) + "\"</b> kullanıcısına bağlandı.\n\n" +
                "👤 Kullanıcı: <b>" + escapeHtml(user.getUsername()) + "</b>\n" +
                "📝 Ad Soyad: <b>" + escapeHtml(user.getFullName() != null ? user.getFullName() : "-") + "</b>\n\n" +
                "<i>Durum sorgulamak için /durum yazabilirsiniz.</i>";
        sendMessage(chatId, msg);
    }

    private void sendMessage(Long chatId, String text) {
        SendMessage msg = new SendMessage(chatId.toString(), text);
        msg.enableHtml(true);
        msg.disableWebPagePreview();
        try {
            execute(msg);
        } catch (TelegramApiException e) {
            log.error("Telegram mesajı gönderilemedi: {}", e.getMessage());
        }
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;");
    }
}
