package org.invest.invest;

import org.springframework.stereotype.Component;

import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class PortfolioMessageFormatter {
    /**
     * Главный метод, который генерирует полное сообщение о портфеле.
     * @param accountName Имя счета
     * @param instruments Список всех инструментов на счете
     * @param filterType Тип для фильтрации ("share", "bond", "all")
     * @return Готовый к отправке текст сообщения
     */
    public String format(String accountName, List<Instrument> instruments, String filterType) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>\uD83D\uDDC3️ ").append(accountName).append("</b>\n\n");

        if (instruments.isEmpty()) {
            sb.append("В этом портфеле пока нет активов.");
            return sb.toString();
        }

        Map<String, List<Instrument>> groupedInstruments = instruments.stream()
                .collect(Collectors.groupingBy(Instrument::getType));

        sb.append("<pre>");

        for (Map.Entry<String, List<Instrument>> entry : groupedInstruments.entrySet()) {
            String currentType = entry.getKey();
            if (!filterType.equals("all") && !currentType.equals(filterType)) {
                continue;
            }

            sb.append("\n<b>").append(getFriendlyTypeName(currentType)).append("</b>\n");
            for (Instrument instrument : entry.getValue()) {
                sb.append(formatInstrumentLine(instrument));
            }
        }

        sb.append("</pre>");
        return sb.toString();
    }

    private String formatInstrumentLine(Instrument instrument) {
        DecimalFormat qtyFormat = new DecimalFormat("#,###.##");
        DecimalFormat priceFormat = new DecimalFormat("#,##0.00");

        String name = instrument.getName();
        if (name.length() > 13) {
            name = name.substring(0, 12) + ".";
        }

        String quantityStr = qtyFormat.format(instrument.getQuantity());
        String priceStr = priceFormat.format(instrument.getCurrentPrice().getValue()) + getCurrencySymbol(instrument.getCurrentPrice().getCurrency());

        return String.format("%-14s %-8s %12s\n", name, quantityStr, priceStr);
    }

    private String getFriendlyTypeName(String instrumentType) {
        switch (instrumentType.toLowerCase()) {
            case "share": return "Акции \uD83D\uDCC8";
            case "bond": return "Облигации \uD83D\uDCDC";
            case "etf": return "Фонды \uD83D\uDDA5️";
            case "currency": return "Валюта \uD83D\uDCB0";
            default: return "Прочее";
        }
    }

    /**
     * Возвращает символ валюты по ее коду
     */
    public String getCurrencySymbol(String currencyCode) {
        switch (currencyCode.toUpperCase()) {
            case "RUB": return "₽";
            case "USD": return "$";
            case "EUR": return "€";
            default: return " " + currencyCode;
        }
    }
}
