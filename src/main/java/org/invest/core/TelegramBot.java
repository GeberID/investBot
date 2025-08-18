package org.invest.core;

import org.invest.core.commands.PrepareMessage;
import org.invest.core.commands.enums.Commands;
import org.invest.core.local.RuLocal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.BotSession;
import org.telegram.telegrambots.longpolling.interfaces.LongPollingUpdateConsumer;
import org.telegram.telegrambots.longpolling.starter.AfterBotRegistration;
import org.telegram.telegrambots.longpolling.starter.SpringLongPollingBot;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethodMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.message.Message;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.Arrays;

@Component
@Slf4j
public class TelegramBot implements SpringLongPollingBot, LongPollingSingleThreadUpdateConsumer  {

    private final String token;
    private final TelegramClient telegramClient;

    public TelegramBot(@Value("${telegram.token}") String token) {
        this.token = token;
        this.telegramClient = new OkHttpTelegramClient(this.token);
    }

    @Override
    public String getBotToken() {
        return token;
    }

    @Override
    public LongPollingUpdateConsumer getUpdatesConsumer() {
        return this;
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            handleTextMessage(update);
        } else if (update.hasCallbackQuery()) {
            //handleCallbackQuery(update);
        }
    }

    @AfterBotRegistration
    public void afterRegistration(@NotNull BotSession botSession) {
        System.out.println("Registered bot running state is: " + botSession.isRunning());
    }


    private void handleTextMessage(Update update) {
        Message messageObj = update.getMessage();
        long chatId = messageObj.getChatId();
        String text = messageObj.getText();
        if (isCommand(text)) {
            processCommand(text, chatId, messageObj);
        }
    }

    public void processStartCommand(long chatId, Message messageObj){
        String username = messageObj.getChat().getUserName();
        sendMessage(PrepareMessage.createMessage(chatId, RuLocal.hello.formatted(username)));
    }

    public void processCommand(String text, long chatId, Message messageObj) {
        Commands command = Commands.valueOf(text.substring(1));
        switch (command) {
            case start -> processStartCommand(chatId, messageObj);
        }
    }

    public boolean isCommand(String text) {
        return Arrays.stream(Commands.values())
                .anyMatch(cmd -> cmd.getCommand().equals(text));
    }

    public void sendMessage(BotApiMethodMessage message) {
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}