package org.invest.bot.core.messages;

import org.invest.bot.invest.core.objects.InstrumentObj;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import java.util.LinkedHashMap;
import java.util.List;
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

    public InlineKeyboardMarkup createTickerKeyboard(List<InstrumentObj> getInstruments) {
        Map<String, String> buttons = new LinkedHashMap<>();
        for (InstrumentObj instrumentObj : getInstruments) {
            buttons.put("instr_" + instrumentObj.getTicker(), instrumentObj.getName());
        }
        return PrepareMessage.inlineKeyboardMarkupBuilder(buttons, 2);
    }
}