package org.invest.bot.core;

import lombok.extern.slf4j.Slf4j;
import org.invest.bot.core.local.RuLocal;
import org.invest.bot.core.messages.KeyboardFactory;
import org.invest.bot.core.messages.MessageFormatter;
import org.invest.bot.core.messages.PrepareMessage;
import org.invest.bot.core.messages.enums.Commands;
import org.invest.bot.invest.api.InvestApiCore;
import org.invest.bot.invest.core.modules.ai.AiReportService;
import org.invest.bot.invest.core.modules.balanse.AnalysisResult;
import org.invest.bot.invest.core.modules.balanse.BalanceService;
import org.invest.bot.invest.core.modules.instruments.InstrumentAnalysisService;
import org.invest.bot.invest.core.objects.InstrumentObj;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.tinkoff.piapi.contract.v1.Account;
import ru.tinkoff.piapi.contract.v1.Operation;
import ru.tinkoff.piapi.core.models.Portfolio;

import java.io.File;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class TelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final String telegramToken;
    private final TelegramClient telegramClient;
    private final MessageFormatter messageFormatter;
    private final KeyboardFactory keyboardFactory;
    private final BalanceService balanceService;
    private AnalysisResult lastSentDeviations;
    private final AiReportService aiReportService;
    private Long userChatId;
    private InvestApiCore apiCore;
    private final InstrumentAnalysisService instrumentAnalysisService;
    public TelegramBot(@Value("${telegram.token}") String telegramToken,
                       MessageFormatter messageFormatter,
                       KeyboardFactory keyboardFactory,
                       BalanceService balanceService,
                       InvestApiCore apiCore,
                       InstrumentAnalysisService instrumentAnalysisService,
                       AiReportService aiReportService) {
        this.telegramToken = telegramToken;
        this.apiCore = apiCore;
        this.telegramClient = new OkHttpTelegramClient(this.telegramToken);
        this.messageFormatter = messageFormatter;
        this.keyboardFactory = keyboardFactory;
        this.balanceService = balanceService;
        this.aiReportService = aiReportService;
        this.instrumentAnalysisService = instrumentAnalysisService;
    }
    @Override
    public String getBotToken() {
        return telegramToken;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update.getMessage());
        } else if (update.hasCallbackQuery()) {
            handleCallbackQuery(update.getCallbackQuery());
        }
    }

    private void handleTextMessage(Message message) {
        if (isCommand(message.getText())) {
            processCommand(message);
        }
    }

    private void processCommand(Message message) {
        Commands command = Commands.valueOf(message.getText().substring(1));
        switch (command) {
            case start -> processStartCommand(message.getChatId(), message.getChat().getUserName());
            case portfolio -> portfolio();
            case analyze -> analyzeCommand();
            case exp ->exportForAi();
            case instrument -> instrument();
        }
    }

    public void instrument(){
        if (!checkChatId()) return;
        Portfolio portfolio = apiCore.getPortfolio(apiCore.getAccounts().get(0).getId());
        List<InstrumentObj> shares = apiCore.getInstruments(portfolio).stream()
                .filter(f -> f.getType().equals("share")).toList();
        InlineKeyboardMarkup keyboard = keyboardFactory.createTickerKeyboard(shares);
        executeMethod(PrepareMessage.createMessage(userChatId, "Выберите акцию для анализа:", keyboard));
    }

    public void portfolio() {
        if (!checkChatId()) return;
        for (Account account : apiCore.getAccounts()) {
            Portfolio portfolio = apiCore.getPortfolio(account.getId());
            List<InstrumentObj> instrumentObjs = apiCore.getInstruments(portfolio);
            String messageText = messageFormatter.portfolio(account.getName(), instrumentObjs, portfolio, "all");
            InlineKeyboardMarkup keyboard = keyboardFactory.createPortfolioFilterKeyboard(account.getId());
            executeMethod(PrepareMessage.createMessage(userChatId, messageText, keyboard));
        }
    }
    public void analyzeCommand() {
        if (!checkChatId()) return;
        log.info("Запуск анализа по ручной команде для chatId {}", userChatId);
        performAnalysisAndNotify(false);
    }

    public void exportForAi() {
        try {
            log.info("Запрос на экспорт данных для AI от chatId {}", userChatId);
            Account account = apiCore.getAccounts().get(0);
            Portfolio portfolio = apiCore.getPortfolio(account.getId());
            List<InstrumentObj> instrumentObjs = apiCore.getInstruments(portfolio);
            List<Operation> operations = apiCore.getOperationsForLastMonth(account.getId());
            File reportFile = aiReportService.generateReportFile(account, portfolio,instrumentObjs,operations);

            InputFile inputFile = new InputFile(reportFile);
            SendDocument sendDocument = new SendDocument(String.valueOf(userChatId), inputFile);
            sendDocument.setCaption("Отчет по портфелю для анализа нейросетью.");
            try {
                telegramClient.execute(sendDocument);
            } catch (TelegramApiException e) {
                log.error("Telegram API execution error: {}", e.getMessage(), e);
            }
            if (reportFile.delete()) {
                log.info("Временный файл отчета удален: {}", reportFile.getName());
            }

        } catch (Exception e) {
            log.error("Не удалось создать или отправить AI-отчет для chatId {}: {}", userChatId, e.getMessage());
            executeMethod(PrepareMessage.createMessage(userChatId, "Произошла ошибка при создании отчета."));
        }
    }

    @Scheduled(cron = "0 0 13 * * MON-FRI")
    public void scheduledAnalysis() {
        if (!checkChatId()) {
            log.warn("Плановая проверка пропущена: chatId пользователя неизвестен.");
            return;
        }
        log.info("Запуск плановой проверки баланса для chatId {}", userChatId);
        performAnalysisAndNotify(true);
    }
    private void performAnalysisAndNotify(boolean checkChanges) {
        try {
            Account account = apiCore.getAccounts().get(1);
            Portfolio portfolio = apiCore.getPortfolio(account.getId());
            List<InstrumentObj> instrumentObjs = apiCore.getInstruments(portfolio);

            AnalysisResult currentResult = balanceService.analyzePortfolio(portfolio, instrumentObjs);
            if (checkChanges && currentResult.equals(lastSentDeviations)) {
                log.info("Отклонения для chatId {} не изменились. Отправка пропущена.", userChatId);
                return;
            }
            String messageText = messageFormatter.formatBalanceDeviations(currentResult);
            executeMethod(PrepareMessage.createMessage(userChatId, messageText));
            this.lastSentDeviations = currentResult;

            executeMethod(PrepareMessage.createMessage(userChatId, messageFormatter.formatRebalancePlan(balanceService.createPlan(portfolio,instrumentObjs))));
        } catch (Exception e) {
            log.error("Ошибка во время анализа для chatId {}: {}", userChatId, e.getMessage());
        }
    }

    private boolean checkChatId() {
        if (this.userChatId == null) {
            log.warn("Попытка выполнить команду до инициализации chatId. Пользователь должен сначала написать /start");
            return false;
        }
        return true;
    }

    private void handleCallbackQuery(CallbackQuery query) {
        String data = query.getData();
        if (data == null) return;
        if (data.startsWith("filter:")) {
            handlePortfolioFilter(query);
        }
        if (data.startsWith("instr_")){
            handleInstrumentSelection(query);
        }
    }

    private void handlePortfolioFilter(CallbackQuery callbackQuery){
        try {
            String[] parts = callbackQuery.getData().split(":");
            if (parts.length < 3) return;

            String filterType = parts[1];
            String accountId = parts[2];

            Account account = apiCore.getAccountById(accountId);
            if (account == null) {
                log.warn("Account not found for ID: {}", accountId);
                return;
            }
            Portfolio portfolio = apiCore.getPortfolio(accountId);
            List<InstrumentObj> instrumentObjs = apiCore.getInstruments(portfolio);
            String newText = messageFormatter.portfolio(account.getName(), instrumentObjs, portfolio, filterType);
            InlineKeyboardMarkup keyboard = keyboardFactory.createPortfolioFilterKeyboard(accountId);
            EditMessageText editMessage = EditMessageText.builder()
                    .chatId(callbackQuery.getMessage().getChatId())
                    .messageId(callbackQuery.getMessage().getMessageId())
                    .text(newText)
                    .parseMode("HTML")
                    .replyMarkup(keyboard)
                    .build();

            executeMethod(editMessage);

        } catch (Exception e) {
            log.error("Error processing callback query: {}", callbackQuery.getData(), e);
        }
    }

    private void handleInstrumentSelection(CallbackQuery query) {
        try {
            String ticker = query.getData().substring(6);
            String report = instrumentAnalysisService.analyzeInstrumentByTicker(ticker);
            EditMessageText editMessage = EditMessageText.builder()
                    .chatId(query.getMessage().getChatId())
                    .messageId(query.getMessage().getMessageId())
                    .text(report)
                    .parseMode("HTML")
                    .replyMarkup(null)
                    .build();
            executeMethod(editMessage);

        } catch (Exception e) {
            log.error("Error processing instrument selection callback: {}", query.getData(), e);
        }
    }

    private void processStartCommand(long chatId, String username) {
        userChatId = chatId;
        executeMethod(PrepareMessage.createMessage(chatId, RuLocal.hello.formatted(username)));
    }

    private boolean isCommand(String text) {
        if (text == null || !text.startsWith("/")) return false;
        return Arrays.stream(Commands.values()).anyMatch(c -> c.getCommand().equals(text));
    }

    private void executeMethod(BotApiMethod<?> method) {
        try {
            telegramClient.execute(method);
        } catch (TelegramApiException e) {
            log.error("Telegram API execution error: {}", e.getMessage(), e);
        }
    }

    @AfterBotRegistration
    public void afterRegistration(@NotNull BotSession botSession) {
        System.out.println("Registered bot running state is: " + botSession.isRunning());
    }
}