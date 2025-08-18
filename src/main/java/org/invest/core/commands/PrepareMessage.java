package org.invest.core.commands;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class PrepareMessage {

    private PrepareMessage() {}

    public static InlineKeyboardMarkup inlineKeyboardMarkupBuilder(Map<String, String> buttons, int buttonsInRow) {
        // ПРОВЕРЕНО: Используем List<InlineKeyboardRow>
        List<InlineKeyboardRow> rowList = new ArrayList<>();
        List<Map.Entry<String, String>> buttonEntries = new ArrayList<>(buttons.entrySet());

        int buttonIndex = 0;
        while (buttonIndex < buttonEntries.size()) {
            InlineKeyboardRow keyboardRow = new InlineKeyboardRow();
            for (int j = 0; j < buttonsInRow && buttonIndex < buttonEntries.size(); j++) {
                Map.Entry<String, String> entry = buttonEntries.get(buttonIndex);

                // ПРОВЕРЕНО: Используем конструктор с текстом
                InlineKeyboardButton button = new InlineKeyboardButton(entry.getValue());
                button.setCallbackData(entry.getKey());
                keyboardRow.add(button);
                buttonIndex++;
            }
            rowList.add(keyboardRow);
        }
        return new InlineKeyboardMarkup(rowList);
    }

    public static SendMessage createMessage(long chatId, String text) {
        SendMessage sendMessage = new SendMessage(String.valueOf(chatId), text);
        sendMessage.enableHtml(true);
        return sendMessage;
    }

    public static SendMessage createMessage(long chatId, String text, InlineKeyboardMarkup keyboardMarkup) {
        SendMessage sendMessage = createMessage(chatId, text);
        sendMessage.setReplyMarkup(keyboardMarkup);
        return sendMessage;
    }
}