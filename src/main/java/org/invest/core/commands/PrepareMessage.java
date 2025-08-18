package org.invest.core.commands;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;

public abstract class PrepareMessage {
    public static InlineKeyboardMarkup inlineKeyboardMarkupBuilder(List<String> texts, int buttonsInRow){
        List<InlineKeyboardRow> rowList = new ArrayList<>();
        double rowsCount = Math.ceil((double) texts.size() /buttonsInRow);
        int countText = 0;
        for (int i = 0; i < rowsCount; i++) {
            InlineKeyboardRow keyboardButtonsRow = new InlineKeyboardRow();
            for (int j = 0; j < buttonsInRow; j++) {
                String text = texts.get(countText).trim();
                if(text.length() > 40){
                    text = text.substring(0,37)+"...";
                }
                InlineKeyboardButton inlineKeyboardButton = new InlineKeyboardButton(text);
                inlineKeyboardButton.setCallbackData(text);
                keyboardButtonsRow.add(inlineKeyboardButton);
                countText++;
            }
            rowList.add(keyboardButtonsRow);
        }
        return new InlineKeyboardMarkup(rowList);
    }

    public static SendMessage createMessage(long chatId, String text) {
        return new SendMessage(String.valueOf(chatId), text);
    }

    public static SendMessage createMessage(long chatId, String text, InlineKeyboardMarkup keyboardMarkup) {
        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), text);
        sendMessage.setReplyMarkup(keyboardMarkup);
        return sendMessage;
    }
}