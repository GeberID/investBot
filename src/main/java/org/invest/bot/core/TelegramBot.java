package org.invest.bot.core;

import lombok.extern.slf4j.Slf4j;
import org.invest.bot.core.messages.PrepareMessage;
import org.invest.core.commands.enums.Commands;
import org.invest.core.local.RuLocal;
import org.invest.invest.api.InvestApiCore;
import org.invest.invest.core.Instrument;
import org.invest.bot.core.messages.KeyboardFactory;
import org.invest.bot.core.messages.MessageFormatter;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;
import ru.tinkoff.piapi.contract.v1.Account;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class TelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer {

    private final String telegramToken;
    private final TelegramClient telegramClient;
    private final InvestApiCore apiCore;
    private final MessageFormatter portfolioFormatter;
    private final KeyboardFactory keyboardFactory;

    public TelegramBot(@Value("${telegram.token}") String telegramToken,
                       @Value("${tinkoff.readonly}") String tinkoffReadonly,
                       MessageFormatter portfolioFormatter,
                       KeyboardFactory keyboardFactory) {
        this.telegramToken = telegramToken;
        this.apiCore = new InvestApiCore(tinkoffReadonly);
        this.telegramClient = new OkHttpTelegramClient(this.telegramToken);
        this.portfolioFormatter = portfolioFormatter;
        this.keyboardFactory = keyboardFactory;
    }

    @Override
    public String getBotToken() { return telegramToken; }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() { return this; }

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
            case portfolio -> portfolio(message.getChatId());
        }
    }

    public void portfolio(long chatId) {
        for (Account account : apiCore.getAccounts()) {
            List<Instrument> instruments = apiCore.getInstruments(account.getId());
            String messageText = portfolioFormatter.format(account.getName(), instruments, "all");
            InlineKeyboardMarkup keyboard = keyboardFactory.createPortfolioFilterKeyboard(account.getId());
            executeMethod(PrepareMessage.createMessage(chatId, messageText, keyboard));
        }
    }

    private void handleCallbackQuery(CallbackQuery query) {
        String data = query.getData();
        if (data == null || !data.startsWith("filter:")) {
            return; // Если данные некорректны, выходим
        }

        try {
            String[] parts = data.split(":");
            if (parts.length < 3) return; // Проверка на корректность callback-данных

            String filterType = parts[1];
            String accountId = parts[2];

            Account account = apiCore.getAccountById(accountId);
            if (account == null) {
                log.warn("Account not found for ID: {}", accountId);
                return;
            }

            List<Instrument> instruments = apiCore.getInstruments(accountId);
            String newText = portfolioFormatter.format(account.getName(), instruments, filterType);
            InlineKeyboardMarkup keyboard = keyboardFactory.createPortfolioFilterKeyboard(accountId);

            EditMessageText editMessage = EditMessageText.builder()
                    .chatId(query.getMessage().getChatId())
                    .messageId(query.getMessage().getMessageId())
                    .text(newText)
                    .parseMode("HTML") // Указываем режим разметки явно
                    .replyMarkup(keyboard)
                    .build();

            executeMethod(editMessage);

        } catch (Exception e) {
            // Логируем любую непредвиденную ошибку при обработке
            log.error("Error processing callback query: {}", data, e);
        }
    }

    private void processStartCommand(long chatId, String username) {
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