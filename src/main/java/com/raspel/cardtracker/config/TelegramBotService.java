package com.raspel.cardtracker.config;

import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.domain.card.CardService;
import com.raspel.cardtracker.domain.expense.Expense;
import com.raspel.cardtracker.domain.expense.ExpenseService;
import com.raspel.cardtracker.domain.expense.InstallmentEntry;
import com.raspel.cardtracker.domain.user.AppUser;
import com.raspel.cardtracker.domain.user.UserService;
import com.raspel.cardtracker.ui.utils.FormatUtils;
import com.raspel.cardtracker.ui.utils.HolidayUtils;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.*;

@Component
@Lazy(false)
@Slf4j
public class TelegramBotService extends TelegramLongPollingBot {

    private final UserService userService;
    private final CardService cardService;
    private final ExpenseService expenseService;
    private final Environment environment;

    public TelegramBotService(UserService userService, CardService cardService,
                              ExpenseService expenseService, Environment environment) {
        super(environment.getProperty("TELEGRAM_BOT_TOKEN", ""));
        this.userService = userService;
        this.cardService = cardService;
        this.expenseService = expenseService;
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
        commands.add(new BotCommand("kartlar", "Kayıtlı kartlar ve son ödeme tarihleri"));
        commands.add(new BotCommand("bakiye", "Kart borç durumu"));
        commands.add(new BotCommand("limit", "Kart limit kullanım oranları"));
        commands.add(new BotCommand("odemeler", "Bu ayki taksit ödemeleri"));
        commands.add(new BotCommand("sonharcamalar", "Son 5 harcama"));
        commands.add(new BotCommand("baglantikes", "Telegram bağlantısını kes"));
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
            case "/kartlar" -> sendCardsMessage(chatId);
            case "/bakiye" -> sendBalanceMessage(chatId);
            case "/limit" -> sendLimitMessage(chatId);
            case "/odemeler" -> sendInstallmentsMessage(chatId);
            case "/sonharcamalar" -> sendRecentExpensesMessage(chatId);
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

    // --- Public API for system notifications ---

    public void notifyUser(Long chatId, String message) {
        sendMessage(chatId, message);
    }

    // --- Command handlers ---

    private void sendWelcomeMessage(Long chatId, String firstName) {
        String msg = "<b>Hoş Geldiniz!</b> \uD83D\uDC4B\n\n" +
                "<b>RasPel Finans Yönetim Bot'u</b>\n\n" +
                "Bu bot ile kredi kartı harcamalarınızı, borç durumunuzu ve ödemelerinizi Telegram üzerinden takip edebilirsiniz.\n\n" +
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
                "<b>/durum</b> - Hesap bağlantı durumu\n" +
                "<b>/kartlar</b> - Kayıtlı kartlar ve son ödeme tarihleri\n" +
                "<b>/bakiye</b> - Kart borç durumu\n" +
                "<b>/limit</b> - Kart limit kullanım oranları\n" +
                "<b>/odemeler</b> - Bu ay yapılacak taksit ödemeleri\n" +
                "<b>/sonharcamalar</b> - Son 5 harcama kaydı\n" +
                "<b>/baglantikes</b> - Telegram bağlantısını kes\n\n" +
                "<b>🔗 Bağlantı Kurma:</b>\n" +
                "Web panel → Profilim → Telegram'a Bağlan → Kodu buraya yazın";
        sendMessage(chatId, msg);
    }

    private void sendStatusMessage(Long chatId) {
        Optional<AppUser> userOpt = userService.findByTelegramChatId(chatId);
        if (userOpt.isPresent()) {
            AppUser user = userOpt.get();
            String msg = "<b>✅ Bağlantı Aktif</b>\n\n" +
                    "Kullanıcı: <b>" + esc(user.getUsername()) + "</b>\n" +
                    "Ad Soyad: <b>" + esc(user.getFullName() != null ? user.getFullName() : "-") + "</b>\n" +
                    "Chat ID: <code>" + chatId + "</code>";
            sendMessage(chatId, msg);
        } else {
            sendMessage(chatId, "<b>❌ Bağlı Değil</b>\n\nBu Telegram hesabı henüz bir kullanıcıya bağlanmamış.\n\nBağlanmak için web panel → Profilim → Telegram'a Bağlan");
        }
    }

    private void sendCardsMessage(Long chatId) {
        Optional<AppUser> userOpt = userService.findByTelegramChatId(chatId);
        if (userOpt.isEmpty()) { sendNotConnected(chatId); return; }

        List<Card> cards = cardService.findAllActive();
        if (cards.isEmpty()) { sendMessage(chatId, "Kayıtlı aktif kart bulunamadı."); return; }

        StringBuilder sb = new StringBuilder("<b>💳 Kayıtlı Kartlar</b>\n\n");
        Map<Long, BigDecimal> unpaidMap = expenseService.getUnpaidBalancesGroupedByCard();
        LocalDate today = LocalDate.now();

        for (Card card : cards) {
            BigDecimal unpaid = unpaidMap.getOrDefault(card.getId(), BigDecimal.ZERO);
            String color = card.getColor() != null ? card.getColor().substring(1) : "1976D2";

            YearMonth ym = YearMonth.from(today);
            int closingDay = Math.min(card.getClosingDay() != null ? card.getClosingDay() : 1, ym.lengthOfMonth());
            LocalDate statementDate = LocalDate.of(ym.getYear(), ym.getMonthValue(), closingDay);
            if (today.isAfter(statementDate)) statementDate = statementDate.plusMonths(1);
            LocalDate dueDate = HolidayUtils.getNextBusinessDay(
                    statementDate.plusDays(card.getDueDay() != null ? card.getDueDay() : 10));

            sb.append("• <b>").append(esc(card.getName())).append("</b> (").append(esc(card.getBank())).append(")\n");
            sb.append("  Borç: ").append(FormatUtils.formatNumber(unpaid)).append(" ₺");
            if (card.getCardLimit() != null && card.getCardLimit().compareTo(BigDecimal.ZERO) > 0) {
                sb.append(" / ").append(FormatUtils.formatNumber(card.getCardLimit())).append(" ₺");
            }
            sb.append("\n");
            sb.append("  Son Ödeme: ").append(dueDate.format(DateTimeFormatter.ofPattern("dd MMMM EEEE", Locale.of("tr")))).append("\n\n");
        }
        sendMessage(chatId, sb.toString());
    }

    private void sendBalanceMessage(Long chatId) {
        Optional<AppUser> userOpt = userService.findByTelegramChatId(chatId);
        if (userOpt.isEmpty()) { sendNotConnected(chatId); return; }

        List<Card> cards = cardService.findAllActive();
        if (cards.isEmpty()) { sendMessage(chatId, "Aktif kart bulunamadı."); return; }

        Map<Long, BigDecimal> unpaidMap = expenseService.getUnpaidBalancesGroupedByCard();
        BigDecimal totalDebt = BigDecimal.ZERO;
        StringBuilder sb = new StringBuilder("<b>💰 Borç Durumu</b>\n\n");

        for (Card card : cards) {
            BigDecimal unpaid = unpaidMap.getOrDefault(card.getId(), BigDecimal.ZERO);
            totalDebt = totalDebt.add(unpaid);
            sb.append("• <b>").append(esc(card.getName())).append("</b>: ").append(FormatUtils.formatNumber(unpaid)).append(" ₺\n");
        }
        sb.append("\n<b>Toplam Borç: ").append(FormatUtils.formatNumber(totalDebt)).append(" ₺</b>");
        sendMessage(chatId, sb.toString());
    }

    private void sendLimitMessage(Long chatId) {
        Optional<AppUser> userOpt = userService.findByTelegramChatId(chatId);
        if (userOpt.isEmpty()) { sendNotConnected(chatId); return; }

        List<Card> cards = cardService.findAllActive();
        if (cards.isEmpty()) { sendMessage(chatId, "Aktif kart bulunamadı."); return; }

        Map<Long, BigDecimal> unpaidMap = expenseService.getUnpaidBalancesGroupedByCard();
        StringBuilder sb = new StringBuilder("<b>📊 Limit Kullanımı</b>\n\n");

        for (Card card : cards) {
            BigDecimal unpaid = unpaidMap.getOrDefault(card.getId(), BigDecimal.ZERO);
            BigDecimal limit = card.getCardLimit();
            if (limit != null && limit.compareTo(BigDecimal.ZERO) > 0) {
                double pct = unpaid.divide(limit, 4, RoundingMode.HALF_UP).doubleValue() * 100;
                String bar = buildProgressBar(pct);
                String emoji = pct >= 90 ? "🔴" : pct >= 80 ? "🟡" : "🟢";
                sb.append(emoji).append(" <b>").append(esc(card.getName())).append("</b>\n");
                sb.append("  ").append(bar).append(" %").append(String.format("%.0f", pct)).append("\n");
                sb.append("  ").append(FormatUtils.formatNumber(unpaid)).append(" / ").append(FormatUtils.formatNumber(limit)).append(" ₺\n\n");
            }
        }
        if (sb.toString().equals("<b>📊 Limit Kullanımı</b>\n\n")) {
            sendMessage(chatId, "Limit tanımlı kart bulunamadı.");
            return;
        }
        sendMessage(chatId, sb.toString());
    }

    private void sendInstallmentsMessage(Long chatId) {
        Optional<AppUser> userOpt = userService.findByTelegramChatId(chatId);
        if (userOpt.isEmpty()) { sendNotConnected(chatId); return; }

        YearMonth now = YearMonth.now();
        List<InstallmentEntry> entries = expenseService.getInstallmentsForMonth(now.getYear(), now.getMonthValue());
        if (entries.isEmpty()) { sendMessage(chatId, "Bu ay için taksit bulunamadı."); return; }

        StringBuilder sb = new StringBuilder("<b>📅 Bu Ayki Ödemeler</b>\n\n");
        entries.sort(Comparator.comparing(e -> HolidayUtils.getNextBusinessDay(
                LocalDate.of(e.getDueYear(), e.getDueMonth(),
                        Math.min(e.getExpense().getCard().getClosingDay(), YearMonth.of(e.getDueYear(), e.getDueMonth()).lengthOfMonth()))
                        .plusDays(e.getExpense().getCard().getDueDay()))));

        int count = 0;
        for (InstallmentEntry entry : entries) {
            if (count++ >= 15) { sb.append("\n<i>... ve daha fazlası</i>"); break; }
            String cardName = entry.getExpense().getCard() != null ? entry.getExpense().getCard().getName() : "-";
            String desc = entry.getExpense().getDescription() != null ? entry.getExpense().getDescription() : "";
            if (desc.length() > 25) desc = desc.substring(0, 25) + "...";
            YearMonth ym = YearMonth.of(entry.getDueYear(), entry.getDueMonth());
            int cd = Math.min(entry.getExpense().getCard().getClosingDay(), ym.lengthOfMonth());
            LocalDate due = HolidayUtils.getNextBusinessDay(
                    LocalDate.of(entry.getDueYear(), entry.getDueMonth(), cd).plusDays(entry.getExpense().getCard().getDueDay()));

            String status = entry.getIsPaid() ? "✅" : "⏳";
            sb.append(status).append(" <b>").append(esc(cardName)).append("</b> ").append(FormatUtils.formatNumber(entry.getAmount())).append(" ₺\n");
            sb.append("  ").append(esc(desc)).append(" | ").append(due.format(DateTimeFormatter.ofPattern("dd.MM"))).append("\n\n");
        }
        sendMessage(chatId, sb.toString());
    }

    private void sendRecentExpensesMessage(Long chatId) {
        Optional<AppUser> userOpt = userService.findByTelegramChatId(chatId);
        if (userOpt.isEmpty()) { sendNotConnected(chatId); return; }

        List<Expense> expenses = expenseService.findRecentByCreatedBy(userOpt.get().getUsername(), 5);
        if (expenses.isEmpty()) { sendMessage(chatId, "Henüz harcama kaydı bulunamadı."); return; }

        StringBuilder sb = new StringBuilder("<b>🛒 Son 5 Harcama</b>\n\n");
        for (Expense e : expenses) {
            String date = e.getExpenseDate() != null ? e.getExpenseDate().format(DateTimeFormatter.ofPattern("dd.MM")) : "-";
            String card = e.getCard() != null ? e.getCard().getName() : "-";
            String desc = e.getDescription() != null ? e.getDescription() : "";
            if (desc.length() > 20) desc = desc.substring(0, 20) + "...";
            sb.append("• ").append(date).append(" | <b>").append(esc(card)).append("</b>\n");
            sb.append("  ").append(esc(desc)).append(": ").append(FormatUtils.formatNumber(e.getTotalAmount())).append(" ₺\n\n");
        }
        sendMessage(chatId, sb.toString());
    }

    private void sendDisconnectConfirmation(Long chatId) {
        if (userService.disconnectTelegramByChatId(chatId)) {
            sendMessage(chatId, "<b>🔌 Bağlantı Kesildi</b>\n\nTelegram bağlantınız kaldırıldı.\n\nTekrar bağlanmak için web panelden yeni bir kod alıp /start ile başlayabilirsiniz.");
        } else {
            sendMessage(chatId, "❌ Bu Telegram hesabı zaten bir kullanıcıya bağlı değil.");
        }
    }

    private void handleVerificationCode(Long chatId, String code, String firstName) {
        if (code.length() < 4 || code.length() > 20) {
            sendMessage(chatId, "⚠️ Geçersiz kod. Lütfen web panelinden aldığınız 6 haneli doğrulama kodunu girin.");
            return;
        }

        Optional<AppUser> userOpt = userService.findByTelegramVerificationCode(code);
        if (userOpt.isEmpty()) {
            sendMessage(chatId, "❌ Bu doğrulama kodu bulunamadı.\n\nKodun süresi dolmuş olabilir. Lütfen web panelinden <b>yeni bir kod</b> alın.");
            return;
        }

        AppUser user = userOpt.get();
        Optional<AppUser> existingChatUser = userService.findByTelegramChatId(chatId);
        if (existingChatUser.isPresent() && !existingChatUser.get().getId().equals(user.getId())) {
            sendMessage(chatId, "⚠️ Bu Telegram hesabı zaten <b>" + esc(existingChatUser.get().getUsername()) + "</b> kullanıcısına bağlı.");
            return;
        }

        if (!userService.linkTelegramChatId(code, chatId)) {
            sendMessage(chatId, "❌ Bağlantı kurulamadı. Lütfen tekrar deneyin.");
            return;
        }
        log.info("Telegram bağlantısı kuruldu: {} -> chatId={}", user.getUsername(), chatId);

        String msg = "<b>✅ Doğrulama Başarılı!</b>\n\n" +
                "<b>\"" + esc(user.getUsername()) + "\"</b> hesabınıza bağlandınız.\n\n" +
                "👤 " + esc(user.getUsername()) + "\n" +
                "📝 " + esc(user.getFullName() != null ? user.getFullName() : "-") + "\n\n" +
                "<i>Komutlar: /kartlar /bakiye /limit /odemeler /sonharcamalar</i>";
        sendMessage(chatId, msg);
    }

    // --- Helpers ---

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

    private void sendNotConnected(Long chatId) {
        sendMessage(chatId, "⚠️ Hesabınız henüz bağlı değil.\n\nWeb panel → Profilim → Telegram'a Bağlan → kodu buraya gönderin.");
    }

    private String esc(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String buildProgressBar(double percent) {
        int filled = (int) (percent / 10);
        StringBuilder bar = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        return bar.toString();
    }
}
