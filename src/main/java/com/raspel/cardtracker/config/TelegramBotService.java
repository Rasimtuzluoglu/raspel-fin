package com.raspel.cardtracker.config;

import com.raspel.cardtracker.domain.card.Card;
import com.raspel.cardtracker.domain.card.CardService;
import com.raspel.cardtracker.domain.cheque.Cheque;
import com.raspel.cardtracker.domain.cheque.ChequeService;
import com.raspel.cardtracker.domain.cheque.ChequeStatus;
import com.raspel.cardtracker.domain.cheque.ChequeType;
import com.raspel.cardtracker.domain.employee.EmployeeService;
import com.raspel.cardtracker.domain.employee.EmployeeTask;
import com.raspel.cardtracker.domain.employee.TaskStatus;
import com.raspel.cardtracker.domain.expense.Expense;
import com.raspel.cardtracker.domain.expense.ExpenseService;
import com.raspel.cardtracker.domain.expense.InstallmentEntry;
import com.raspel.cardtracker.domain.user.AppUser;
import com.raspel.cardtracker.domain.user.UserService;
import com.raspel.cardtracker.ui.utils.FormatUtils;
import com.raspel.cardtracker.ui.utils.HolidayUtils;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
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
import java.time.temporal.ChronoUnit;
import java.util.*;

@Component
@Slf4j
public class TelegramBotService extends TelegramLongPollingBot {

    private final UserService userService;
    private final CardService cardService;
    private final ExpenseService expenseService;
    private final ChequeService chequeService;
    private final EmployeeService employeeService;
    private final Environment environment;

    public TelegramBotService(UserService userService, CardService cardService,
                              ExpenseService expenseService, ChequeService chequeService,
                              EmployeeService employeeService, Environment environment) {
        super(environment.getProperty("TELEGRAM_BOT_TOKEN", ""));
        this.userService = userService;
        this.cardService = cardService;
        this.expenseService = expenseService;
        this.chequeService = chequeService;
        this.employeeService = employeeService;
        this.environment = environment;
    }

    @PostConstruct
    void init() {
        if (environment.getProperty("TELEGRAM_BOT_TOKEN", "").isEmpty()) {
            log.info("TELEGRAM_BOT_TOKEN tanımlı değil, Telegram bot devre dışı.");
        } else {
            try {
                new TelegramBotsApi(DefaultBotSession.class).registerBot(this);
                registerCommands();
                log.info("Telegram bot başlatıldı: @raspel_fin_bot (12 komut)");
            } catch (TelegramApiException e) {
                log.error("Bot kaydedilemedi: {}", e.getMessage());
            }
        }
    }

    private void registerCommands() {
        List<BotCommand> cmds = Arrays.asList(
                new BotCommand("start", "Bot'u başlat"),
                new BotCommand("help", "Komut listesi"),
                new BotCommand("durum", "Bağlantı durumu"),
                new BotCommand("ozet", "Günlük finansal özet"),
                new BotCommand("kartlar", "Kartlar ve son ödeme tarihleri"),
                new BotCommand("bakiye", "Kart borç durumu"),
                new BotCommand("limit", "Limit kullanım oranları"),
                new BotCommand("odemeler", "Bu ayki taksitler"),
                new BotCommand("gecikmeler", "Vadesi geçmiş ödemeler"),
                new BotCommand("cekler", "Portföydeki çekler"),
                new BotCommand("sonharcamalar", "Son 5 harcama"),
                new BotCommand("ismegore", "İsme göre kart ara"),
                new BotCommand("gorevler", "Bekleyen görevler"),
                new BotCommand("baglantikes", "Bağlantıyı kes")
        );
        try { execute(new SetMyCommands(cmds, new BotCommandScopeDefault(), null)); }
        catch (TelegramApiException e) { log.error("Komutlar kaydedilemedi: {}", e.getMessage()); }
    }

    @Override public String getBotUsername() { return "raspel_fin_bot"; }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) return;
        String text = update.getMessage().getText().trim();
        Long chatId = update.getMessage().getChatId();
        String fn = update.getMessage().getFrom().getFirstName();

        switch (text.toLowerCase(java.util.Locale.ENGLISH)) {
            case "/start" -> sendWelcome(chatId, fn);
            case "/help" -> sendHelp(chatId);
            case "/durum" -> sendStatus(chatId);
            case "/ozet" -> sendSummary(chatId);
            case "/kartlar" -> sendCards(chatId);
            case "/bakiye" -> sendBalance(chatId);
            case "/limit" -> sendLimits(chatId);
            case "/odemeler" -> sendInstallments(chatId);
            case "/gecikmeler" -> sendOverdue(chatId);
            case "/cekler" -> sendCheques(chatId);
            case "/sonharcamalar" -> sendRecent(chatId);
            case "/gorevler" -> sendTasks(chatId);
            case "/baglantikes" -> sendDisconnect(chatId);
            default -> {
                if (text.toLowerCase(java.util.Locale.ENGLISH).startsWith("/ismegore ")) {
                    sendCardsByName(chatId, text.substring(10).trim());
                } else if (text.startsWith("/")) {
                    sendMsg(chatId, "❌ Bilinmeyen komut. /help yazın.");
                } else {
                    handleCode(chatId, text);
                }
            }
        }
    }

    public void notifyUser(Long chatId, String message) { sendMsg(chatId, message); }

    public void checkAndNotifyLimits() {
        List<Card> cards = cardService.findAllActive();
        Map<Long, BigDecimal> unpaidMap = expenseService.getUnpaidBalancesGroupedByCard();
        for (Card c : cards) {
            BigDecimal unpaid = unpaidMap.getOrDefault(c.getId(), BigDecimal.ZERO);
            if (c.getCardLimit() == null || c.getCardLimit().compareTo(BigDecimal.ZERO) <= 0) continue;
            double pct = unpaid.divide(c.getCardLimit(), 4, RoundingMode.HALF_UP).doubleValue() * 100;
            if (pct >= 90.0) {
                List<AppUser> connected = userService.findAllActive().stream()
                        .filter(u -> u.getTelegramChatId() != null).toList();
                for (AppUser u : connected) {
                    sendMsg(u.getTelegramChatId(), "<b>🔴 LİMİT UYARISI</b>\n\n" +
                            "<b>" + esc(c.getName()) + "</b> kart limiti %" + String.format("%.0f", pct) +
                            " dolu!\nBorç: " + FormatUtils.formatNumber(unpaid) + " ₺ / Limit: " +
                            FormatUtils.formatNumber(c.getCardLimit()) + " ₺");
                }
            }
        }
    }

    // ── /ismegore ──
    private void sendCardsByName(Long chatId, String nameFilter) {
        var u = getUser(chatId);
        if (u.isEmpty()) { sendNotConnected(chatId); return; }
        if (nameFilter.isEmpty()) { sendMsg(chatId, "Kullanım: /ismegore KartAdı\nÖrnek: /ismegore Visa"); return; }

        List<Card> cards = cardService.findAllActive().stream()
                .filter(c -> c.getName().toLowerCase(java.util.Locale.forLanguageTag("tr-TR")).contains(nameFilter.toLowerCase(java.util.Locale.forLanguageTag("tr-TR"))))
                .toList();
        if (cards.isEmpty()) { sendMsg(chatId, "\"" + esc(nameFilter) + "\" ile eşleşen kart bulunamadı."); return; }

        Map<Long, BigDecimal> unpaidMap = expenseService.getUnpaidBalancesGroupedByCard();
        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder("<b>🔍 \"" + esc(nameFilter) + "\" Sonuçları</b>\n\n");

        for (Card card : cards) {
            BigDecimal unpaid = unpaidMap.getOrDefault(card.getId(), BigDecimal.ZERO);
            YearMonth ym = YearMonth.from(today);
            int cd = Math.min(card.getClosingDay() != null ? card.getClosingDay() : 1, ym.lengthOfMonth());
            LocalDate stmt = LocalDate.of(ym.getYear(), ym.getMonthValue(), cd);
            if (today.isAfter(stmt)) stmt = stmt.plusMonths(1);
            LocalDate due = HolidayUtils.getNextBusinessDay(stmt.plusDays(card.getDueDay() != null ? card.getDueDay() : 10));

            sb.append("• <b>").append(esc(card.getName())).append("</b> (").append(esc(card.getBank())).append(")\n");
            sb.append("  Borç: ").append(FormatUtils.formatNumber(unpaid)).append(" ₺");
            if (card.getCardLimit() != null && card.getCardLimit().compareTo(BigDecimal.ZERO) > 0)
                sb.append(" / ").append(FormatUtils.formatNumber(card.getCardLimit())).append(" ₺");
            sb.append("\n  Son Ödeme: ").append(due.format(DateTimeFormatter.ofPattern("dd MMMM EEEE", Locale.of("tr")))).append("\n\n");
        }
        sendMsg(chatId, sb.toString());
    }

    // ── /gorevler ──
    private void sendTasks(Long chatId) {
        var u = getUser(chatId);
        if (u.isEmpty()) { sendNotConnected(chatId); return; }
        List<EmployeeTask> tasks = employeeService.findAllTasks().stream()
                .filter(t -> t.getStatus() != TaskStatus.COMPLETED)
                .toList();
        if (tasks.isEmpty()) { sendMsg(chatId, "✅ Bekleyen görev yok."); return; }

        StringBuilder sb = new StringBuilder("<b>📋 Bekleyen Görevler</b>\n\n");
        for (EmployeeTask t : tasks) {
            String statusEmoji = t.getStatus() == TaskStatus.IN_PROGRESS ? "🔄" : t.getStatus() == TaskStatus.TODO ? "📌" : "✅";
            sb.append(statusEmoji).append(" <b>").append(esc(t.getTitle())).append("</b>\n");
            if (t.getAssignedTo() != null)
                sb.append("  👤 ").append(esc(t.getAssignedTo().getFullName())).append("\n");
            if (t.getDueDate() != null)
                sb.append("  📅 ").append(t.getDueDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))).append("\n");
            sb.append("\n");
        }
        sendMsg(chatId, sb.toString());
    }

    // ── Komutlar ──

    private void sendWelcome(Long chatId, String firstName) {
        sendMsg(chatId, "<b>Hoş Geldiniz!</b> \uD83D\uDC4B\n\n" +
                "<b>RasPel Finans Bot'u</b>\n\n" +
                "Kart borçları, ödemeler, çekler ve limit durumunuzu Telegram'dan takip edin.\n\n" +
                "<b>Bağlanmak için:</b>\n" +
                "1️⃣ Web panel → Profilim → Telegram'a Bağlan\n" +
                "2️⃣ Aldığınız <b>6 haneli kodu</b> buraya gönderin\n\n" +
                "/help ile tüm komutları görebilirsiniz.");
    }

    private void sendHelp(Long chatId) {
        sendMsg(chatId, "<b>📋 Komutlar</b>\n\n" +
                "/start - Başlangıç\n/help - Bu menü\n/durum - Bağlantı bilgisi\n" +
                "/ozet - <b>Günlük finansal özet</b>\n/kartlar - Kart listesi\n" +
                "/bakiye - Borç durumu\n/limit - Limit kullanımı\n" +
                "/odemeler - Bu ay taksitler\n/gecikmeler - <b>Geciken ödemeler</b>\n" +
                "/cekler - Portföy çekleri\n/sonharcamalar - Son harcamalar\n" +
                "/ismegore Visa - <b>İsme göre kart ara</b>\n/gorevler - <b>Bekleyen görevler</b>\n" +
                "/baglantikes - Bağlantıyı kes\n\n<b>Bağlantı:</b> Web panel → Profilim → Telegram'a Bağlan → Kodu gönder");
    }

    private void sendStatus(Long chatId) {
        var u = getUser(chatId);
        if (u.isEmpty()) { sendNotConnected(chatId); return; }
        sendMsg(chatId, "<b>✅ Bağlı</b>\nKullanıcı: <b>" + esc(u.get().getUsername()) + "</b>\nAd Soyad: <b>" +
                esc(u.get().getFullName() != null ? u.get().getFullName() : "-") + "</b>\nChat ID: <code>" + chatId + "</code>");
    }

    // ── /ozet ──
    private void sendSummary(Long chatId) {
        var u = getUser(chatId);
        if (u.isEmpty()) { sendNotConnected(chatId); return; }

        List<Card> cards = cardService.findAllActive();
        Map<Long, BigDecimal> unpaidMap = expenseService.getUnpaidBalancesGroupedByCard();
        BigDecimal totalDebt = cards.stream().map(c -> unpaidMap.getOrDefault(c.getId(), BigDecimal.ZERO)).reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate today = LocalDate.now();
        YearMonth ym = YearMonth.now();
        List<InstallmentEntry> monthEntries = expenseService.getInstallmentsForMonth(ym.getYear(), ym.getMonthValue());
        long overdueCount = monthEntries.stream().filter(e -> !e.getIsPaid() &&
                HolidayUtils.getNextBusinessDay(LocalDate.of(e.getDueYear(), e.getDueMonth(),
                        Math.min(e.getExpense().getCard().getClosingDay(), YearMonth.of(e.getDueYear(), e.getDueMonth()).lengthOfMonth()))
                        .plusDays(e.getExpense().getCard().getDueDay())).isBefore(today)).count();

        List<Cheque> cheques = chequeService.findAll();
        long incomingCheques = cheques.stream().filter(c -> c.getType() == ChequeType.ENTERING && c.getStatus() == ChequeStatus.PORTFOLIO).count();
        long outgoingCheques = cheques.stream().filter(c -> c.getType() == ChequeType.EXITING && c.getStatus() == ChequeStatus.PORTFOLIO).count();

        int limitWarnings = 0;
        for (Card c : cards) {
            BigDecimal unpaid = unpaidMap.getOrDefault(c.getId(), BigDecimal.ZERO);
            if (c.getCardLimit() != null && c.getCardLimit().compareTo(BigDecimal.ZERO) > 0 &&
                    unpaid.divide(c.getCardLimit(), 4, RoundingMode.HALF_UP).doubleValue() >= 0.8) limitWarnings++;
        }

        StringBuilder sb = new StringBuilder("<b>📊 Günlük Finansal Özet</b>\n");
        sb.append(today.format(DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.of("tr")))).append("\n\n");

        sb.append("<b>💰 Borç:</b> ").append(FormatUtils.formatNumber(totalDebt)).append(" ₺  (").append(cards.size()).append(" kart)\n");
        sb.append("<b>📅 Bu Ay Taksit:</b> ").append(monthEntries.size()).append(" adet\n");
        sb.append("<b>🔴 Geciken:</b> ").append(overdueCount).append(" ödeme\n");
        sb.append("<b>📄 Çek:</b> ").append(incomingCheques).append(" giriş / ").append(outgoingCheques).append(" çıkış\n");
        if (limitWarnings > 0) sb.append("<b>⚠️ Limit Uyarısı:</b> ").append(limitWarnings).append(" kart\n");
        if (overdueCount == 0 && limitWarnings == 0) sb.append("\n✅ Tüm ödemeler güncel, limitler normal.");
        sendMsg(chatId, sb.toString());
    }

    // ── /kartlar ──
    private void sendCards(Long chatId) {
        var u = getUser(chatId); if (u.isEmpty()) { sendNotConnected(chatId); return; }
        List<Card> cards = cardService.findAllActive();
        if (cards.isEmpty()) { sendMsg(chatId, "Aktif kart yok."); return; }

        Map<Long, BigDecimal> unpaidMap = expenseService.getUnpaidBalancesGroupedByCard();
        LocalDate today = LocalDate.now();
        StringBuilder sb = new StringBuilder("<b>💳 Kartlar</b>\n\n");

        for (Card card : cards) {
            BigDecimal unpaid = unpaidMap.getOrDefault(card.getId(), BigDecimal.ZERO);
            YearMonth ym = YearMonth.from(today);
            int cd = Math.min(card.getClosingDay() != null ? card.getClosingDay() : 1, ym.lengthOfMonth());
            LocalDate stmt = LocalDate.of(ym.getYear(), ym.getMonthValue(), cd);
            if (today.isAfter(stmt)) stmt = stmt.plusMonths(1);
            LocalDate due = HolidayUtils.getNextBusinessDay(stmt.plusDays(card.getDueDay() != null ? card.getDueDay() : 10));

            sb.append("• <b>").append(esc(card.getName())).append("</b> (").append(esc(card.getBank())).append(")\n");
            sb.append("  Borç: ").append(FormatUtils.formatNumber(unpaid)).append(" ₺");
            if (card.getCardLimit() != null && card.getCardLimit().compareTo(BigDecimal.ZERO) > 0)
                sb.append(" / ").append(FormatUtils.formatNumber(card.getCardLimit())).append(" ₺");
            sb.append("\n  Son Ödeme: ").append(due.format(DateTimeFormatter.ofPattern("dd MMMM EEEE", Locale.of("tr")))).append("\n\n");
        }
        sendMsg(chatId, sb.toString());
    }

    // ── /bakiye ──
    private void sendBalance(Long chatId) {
        var u = getUser(chatId); if (u.isEmpty()) { sendNotConnected(chatId); return; }
        List<Card> cards = cardService.findAllActive();
        Map<Long, BigDecimal> m = expenseService.getUnpaidBalancesGroupedByCard();
        BigDecimal total = BigDecimal.ZERO;
        StringBuilder sb = new StringBuilder("<b>💰 Borç Durumu</b>\n\n");
        for (Card c : cards) {
            BigDecimal unpaid = m.getOrDefault(c.getId(), BigDecimal.ZERO);
            total = total.add(unpaid);
            sb.append("• <b>").append(esc(c.getName())).append("</b>: ").append(FormatUtils.formatNumber(unpaid)).append(" ₺\n");
        }
        sb.append("\n<b>Toplam: ").append(FormatUtils.formatNumber(total)).append(" ₺</b>");
        sendMsg(chatId, sb.toString());
    }

    // ── /limit ──
    private void sendLimits(Long chatId) {
        var u = getUser(chatId); if (u.isEmpty()) { sendNotConnected(chatId); return; }
        List<Card> cards = cardService.findAllActive();
        Map<Long, BigDecimal> m = expenseService.getUnpaidBalancesGroupedByCard();
        StringBuilder sb = new StringBuilder("<b>📊 Limit Kullanımı</b>\n\n");
        boolean found = false;
        for (Card c : cards) {
            BigDecimal unpaid = m.getOrDefault(c.getId(), BigDecimal.ZERO);
            if (c.getCardLimit() == null || c.getCardLimit().compareTo(BigDecimal.ZERO) <= 0) continue;
            found = true;
            double pct = unpaid.divide(c.getCardLimit(), 4, RoundingMode.HALF_UP).doubleValue() * 100;
            String emoji = pct >= 90 ? "🔴" : pct >= 80 ? "🟡" : "🟢";
            sb.append(emoji).append(" <b>").append(esc(c.getName())).append("</b>\n");
            sb.append("  ").append(progressBar(pct)).append(" %").append(String.format("%.0f", pct)).append("\n");
            sb.append("  ").append(FormatUtils.formatNumber(unpaid)).append(" / ").append(FormatUtils.formatNumber(c.getCardLimit())).append(" ₺\n\n");
        }
        if (!found) sb.append("Limit tanımlı kart yok.");
        sendMsg(chatId, sb.toString());
    }

    // ── /odemeler ──
    private void sendInstallments(Long chatId) {
        var u = getUser(chatId); if (u.isEmpty()) { sendNotConnected(chatId); return; }
        YearMonth ym = YearMonth.now();
        List<InstallmentEntry> entries = expenseService.getInstallmentsForMonth(ym.getYear(), ym.getMonthValue());
        if (entries.isEmpty()) { sendMsg(chatId, "Bu ay taksit yok."); return; }

        entries.sort(Comparator.comparing(e -> {
            YearMonth ym2 = YearMonth.of(e.getDueYear(), e.getDueMonth());
            int cd = Math.min(e.getExpense().getCard().getClosingDay(), ym2.lengthOfMonth());
            return HolidayUtils.getNextBusinessDay(LocalDate.of(e.getDueYear(), e.getDueMonth(), cd).plusDays(e.getExpense().getCard().getDueDay()));
        }));

        StringBuilder sb = new StringBuilder("<b>📅 Bu Ayki Ödemeler</b>\n\n");
        int cnt = 0;
        for (InstallmentEntry e : entries) {
            if (cnt++ >= 15) { sb.append("\n<i>...ve daha fazlası</i>"); break; }
            String cn = e.getExpense().getCard() != null ? e.getExpense().getCard().getName() : "-";
            String desc = e.getExpense().getDescription() != null ? e.getExpense().getDescription() : "";
            if (desc.length() > 22) desc = desc.substring(0, 22) + "...";
            YearMonth ym2 = YearMonth.of(e.getDueYear(), e.getDueMonth());
            int cd = Math.min(e.getExpense().getCard().getClosingDay(), ym2.lengthOfMonth());
            LocalDate due = HolidayUtils.getNextBusinessDay(LocalDate.of(e.getDueYear(), e.getDueMonth(), cd).plusDays(e.getExpense().getCard().getDueDay()));
            String st = e.getIsPaid() ? "✅" : "⏳";
            sb.append(st).append(" <b>").append(esc(cn)).append("</b> ").append(FormatUtils.formatNumber(e.getAmount())).append(" ₺\n");
            sb.append("  ").append(esc(desc)).append(" | ").append(due.format(DateTimeFormatter.ofPattern("dd.MM"))).append("\n\n");
        }
        sendMsg(chatId, sb.toString());
    }

    // ── /gecikmeler ──
    private void sendOverdue(Long chatId) {
        var u = getUser(chatId); if (u.isEmpty()) { sendNotConnected(chatId); return; }
        LocalDate today = LocalDate.now();
        List<InstallmentEntry> all = expenseService.getInstallmentsForMonth(today.getYear(), today.getMonthValue());

        List<InstallmentEntry> overdue = all.stream().filter(e -> !e.getIsPaid()).filter(e -> {
            YearMonth ym = YearMonth.of(e.getDueYear(), e.getDueMonth());
            int cd = Math.min(e.getExpense().getCard().getClosingDay(), ym.lengthOfMonth());
            LocalDate due = HolidayUtils.getNextBusinessDay(LocalDate.of(e.getDueYear(), e.getDueMonth(), cd).plusDays(e.getExpense().getCard().getDueDay()));
            return due.isBefore(today);
        }).toList();

        List<InstallmentEntry> upcoming = all.stream().filter(e -> !e.getIsPaid()).filter(e -> {
            YearMonth ym = YearMonth.of(e.getDueYear(), e.getDueMonth());
            int cd = Math.min(e.getExpense().getCard().getClosingDay(), ym.lengthOfMonth());
            LocalDate due = HolidayUtils.getNextBusinessDay(LocalDate.of(e.getDueYear(), e.getDueMonth(), cd).plusDays(e.getExpense().getCard().getDueDay()));
            long days = ChronoUnit.DAYS.between(today, due);
            return days >= 0 && days <= 7;
        }).toList();

        if (overdue.isEmpty() && upcoming.isEmpty()) {
            sendMsg(chatId, "✅ Tüm ödemeler güncel. Geciken veya yaklaşan ödeme yok.");
            return;
        }

        StringBuilder sb = new StringBuilder("<b>⚠️ Geciken ve Yaklaşan Ödemeler</b>\n\n");
        if (!overdue.isEmpty()) {
            sb.append("<b>🔴 GECİKMİŞ:</b>\n");
            for (InstallmentEntry e : overdue) {
                String cn = e.getExpense().getCard() != null ? e.getExpense().getCard().getName() : "-";
                YearMonth ym = YearMonth.of(e.getDueYear(), e.getDueMonth());
                int cd = Math.min(e.getExpense().getCard().getClosingDay(), ym.lengthOfMonth());
                LocalDate due = HolidayUtils.getNextBusinessDay(LocalDate.of(e.getDueYear(), e.getDueMonth(), cd).plusDays(e.getExpense().getCard().getDueDay()));
                long days = ChronoUnit.DAYS.between(due, today);
                sb.append("• <b>").append(esc(cn)).append("</b> ").append(FormatUtils.formatNumber(e.getAmount())).append(" ₺ (").append(days).append(" gün)\n");
            }
            sb.append("\n");
        }
        if (!upcoming.isEmpty()) {
            sb.append("<b>🟡 7 GÜN İÇİNDE:</b>\n");
            for (InstallmentEntry e : upcoming) {
                String cn = e.getExpense().getCard() != null ? e.getExpense().getCard().getName() : "-";
                YearMonth ym = YearMonth.of(e.getDueYear(), e.getDueMonth());
                int cd = Math.min(e.getExpense().getCard().getClosingDay(), ym.lengthOfMonth());
                LocalDate due = HolidayUtils.getNextBusinessDay(LocalDate.of(e.getDueYear(), e.getDueMonth(), cd).plusDays(e.getExpense().getCard().getDueDay()));
                long days = ChronoUnit.DAYS.between(today, due);
                sb.append("• <b>").append(esc(cn)).append("</b> ").append(FormatUtils.formatNumber(e.getAmount())).append(" ₺ (").append(days).append(" gün kaldı)\n");
            }
        }
        sendMsg(chatId, sb.toString());
    }

    // ── /cekler ──
    private void sendCheques(Long chatId) {
        var u = getUser(chatId); if (u.isEmpty()) { sendNotConnected(chatId); return; }
        List<Cheque> cheques = chequeService.findAll();
        List<Cheque> portfolio = cheques.stream().filter(c -> c.getStatus() == ChequeStatus.PORTFOLIO).toList();
        if (portfolio.isEmpty()) { sendMsg(chatId, "Portföyde çek yok."); return; }

        BigDecimal inTotal = BigDecimal.ZERO;
        BigDecimal outTotal = BigDecimal.ZERO;
        StringBuilder sbIn = new StringBuilder();
        StringBuilder sbOut = new StringBuilder();

        for (Cheque c : portfolio) {
            String line = "• <b>" + esc(c.getChequeNumber()) + "</b> | " + esc(c.getBank()) + " | " +
                    FormatUtils.formatNumber(c.getAmount()) + " ₺ | " +
                    c.getMaturityDate().format(DateTimeFormatter.ofPattern("dd.MM.yyyy")) + "\n";
            if (c.getType() == ChequeType.ENTERING) { inTotal = inTotal.add(c.getAmount()); sbIn.append(line); }
            else { outTotal = outTotal.add(c.getAmount()); sbOut.append(line); }
        }

        StringBuilder sb = new StringBuilder("<b>📄 Portföy Çekleri</b>\n\n");
        if (sbIn.length() > 0) {
            sb.append("<b>📥 Giriş Çekleri (").append(FormatUtils.formatNumber(inTotal)).append(" ₺)</b>\n").append(sbIn).append("\n");
        }
        if (sbOut.length() > 0) {
            sb.append("<b>📤 Çıkış Çekleri (").append(FormatUtils.formatNumber(outTotal)).append(" ₺)</b>\n").append(sbOut);
        }
        sendMsg(chatId, sb.toString());
    }

    // ── /sonharcamalar ──
    private void sendRecent(Long chatId) {
        var u = getUser(chatId); if (u.isEmpty()) { sendNotConnected(chatId); return; }
        List<Expense> expenses = expenseService.findRecentByCreatedBy(u.get().getUsername(), 5);
        if (expenses.isEmpty()) { sendMsg(chatId, "Henüz harcama yok."); return; }

        StringBuilder sb = new StringBuilder("<b>🛒 Son 5 Harcama</b>\n\n");
        for (Expense e : expenses) {
            String dt = e.getExpenseDate() != null ? e.getExpenseDate().format(DateTimeFormatter.ofPattern("dd.MM")) : "-";
            String cn = e.getCard() != null ? e.getCard().getName() : "-";
            String desc = e.getDescription() != null ? e.getDescription() : "";
            if (desc.length() > 20) desc = desc.substring(0, 20) + "...";
            sb.append("• ").append(dt).append(" | <b>").append(esc(cn)).append("</b>\n  ").append(esc(desc)).append(": ").append(FormatUtils.formatNumber(e.getTotalAmount())).append(" ₺\n\n");
        }
        sendMsg(chatId, sb.toString());
    }

    // ── /baglantikes ──
    private void sendDisconnect(Long chatId) {
        if (userService.disconnectTelegramByChatId(chatId))
            sendMsg(chatId, "<b>🔌 Bağlantı Kesildi</b>\n\nTekrar bağlanmak için web panelden yeni kod alıp /start yazın.");
        else
            sendMsg(chatId, "❌ Zaten bağlı değilsiniz.");
    }

    // ── Kod doğrulama ──
    private void handleCode(Long chatId, String code) {
        if (code.length() < 4 || code.length() > 20) { sendMsg(chatId, "⚠️ Geçersiz kod. Web panelden 6 haneli kodu alın."); return; }
        var u = userService.findByTelegramVerificationCode(code);
        if (u.isEmpty()) { sendMsg(chatId, "❌ Kod bulunamadı. Web panelden yeni kod alın."); return; }
        var existing = userService.findByTelegramChatId(chatId);
        if (existing.isPresent() && !existing.get().getId().equals(u.get().getId())) {
            sendMsg(chatId, "⚠️ Bu Telegram hesabı zaten <b>" + esc(existing.get().getUsername()) + "</b> kullanıcısına bağlı.");
            return;
        }
        if (!userService.linkTelegramChatId(code, chatId)) { sendMsg(chatId, "❌ Bağlantı kurulamadı."); return; }
        log.info("Telegram bağlandı: {} -> chatId={}", u.get().getUsername(), chatId);
        sendMsg(chatId, "<b>✅ Bağlantı Kuruldu!</b>\n\n<b>" + esc(u.get().getUsername()) + "</b> hesabına bağlandınız.\n\n/ozet ile özet alabilirsiniz.");
    }

    // ── Yardımcılar ──
    private Optional<AppUser> getUser(Long chatId) { return userService.findByTelegramChatId(chatId); }
    private void sendNotConnected(Long chatId) { sendMsg(chatId, "⚠️ Hesabınız bağlı değil.\nWeb panel → Profilim → Telegram'a Bağlan → kodu gönderin."); }
    private void sendMsg(Long chatId, String text) {
        try { execute(SendMessage.builder().chatId(chatId.toString()).text(text).parseMode("HTML").disableWebPagePreview(true).build()); }
        catch (TelegramApiException e) { log.error("Mesaj hatası: {}", e.getMessage()); }
    }
    private String esc(String t) { return t == null ? "" : t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    private String progressBar(double pct) {
        int n = (int)(pct / 10);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 10; i++) b.append(i < n ? "█" : "░");
        return b.toString();
    }
}
