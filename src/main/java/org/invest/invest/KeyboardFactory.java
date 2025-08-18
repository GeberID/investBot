package org.invest.invest;

import org.invest.core.commands.PrepareMessage;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class KeyboardFactory {

    public InlineKeyboardMarkup createPortfolioFilterKeyboard(String accountId) {
        Map<String, String> buttons = new LinkedHashMap<>();
        String prefix = "filter:";
        buttons.put(prefix + "share:" + accountId, "Акции \uD83D\uDCC8");
        buttons.put(prefix + "bond:" + accountId, "Облиг. \uD83D\uDCDC");
        buttons.put(prefix + "etf:" + accountId, "Фонды \uD83D\uDDA5️");
        buttons.put(prefix + "currency:" + accountId, "Валюта \uD83D\uDCB0");
        buttons.put(prefix + "all:" + accountId, "Показать все");
        return PrepareMessage.inlineKeyboardMarkupBuilder(buttons, 2);
    }
}