package org.invest.bot.core.messages;

import org.invest.invest.core.Instrument;
import org.springframework.stereotype.Component;
import ru.tinkoff.piapi.core.models.Money;
import ru.tinkoff.piapi.core.models.Portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MessageFormatter {
    /**
     * Главный метод, который генерирует полное сообщение о портфеле.
     *
     * @param accountName Имя счета
     * @param instruments Список всех инструментов на счете
     * @param filterType  Тип для фильтрации ("share", "bond", "all")
     * @return Готовый к отправке текст сообщения
     */
    public String format(String accountName, List<Instrument> instruments, Portfolio portfolio, String filterType) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>\uD83D\uDDC3️ ").append(accountName).append("</b>\n\n");
        if (instruments.isEmpty()) {
            sb.append("В этом портфеле пока нет активов.");
            return sb.toString();
        }

        Map<String, List<Instrument>> groupedInstruments = instruments.stream()
                .collect(Collectors.groupingBy(Instrument::getType));

        sb.append("<pre>");
        // --- 1. ВОЗВРАЩАЕМ СТАРЫЙ, ПРАВИЛЬНЫЙ ЗАГОЛОВОК ---
        sb.append(String.format("%-14s %-8s %12s %12s\n", "Название", "Кол-во", "Цена", "Стоимость"));
        sb.append("--------------------------------------------------\n");

        for (Map.Entry<String, List<Instrument>> entry : groupedInstruments.entrySet()) {
            String currentType = entry.getKey();
            if (!filterType.equals("all") && !currentType.equals(filterType)) {
                continue;
            }
            sb.append("\n<b>").append(getFriendlyTypeName(currentType)).append("</b>\n");
            for (Instrument instrument : entry.getValue()) {
                // --- 2. ИСПОЛЬЗУЕМ НОВЫЙ ДВУХСТРОЧНЫЙ ФОРМАТ ---
                sb.append(formatInstrumentMultiLine(instrument));
            }
        }
        sb.append("</pre>");
        sb.append(generateAllocationSummary(portfolio));
        return sb.toString();
    }

    /**
     * НОВЫЙ МЕТОД: Форматирует информацию об инструменте в две строки.
     * 1-я строка: Название, Кол-во, Цена, Стоимость.
     * 2-я строка: Отступ и информация о прибыли (P/L).
     */
    private String formatInstrumentMultiLine(Instrument instrument) {
        StringBuilder sb = new StringBuilder();
        DecimalFormat qtyFormat = new DecimalFormat("#,###.##");
        DecimalFormat priceFormat = new DecimalFormat("#,##0.00");

        // --- ДАННЫЕ ДЛЯ ПЕРВОЙ СТРОКИ (КАК БЫЛО РАНЬШЕ) ---
        String name = instrument.getName();
        if (name.length() > 13) {
            name = name.substring(0, 12) + ".";
        }
        String quantityStr = qtyFormat.format(instrument.getQuantity());
        String priceStr = priceFormat.format(instrument.getCurrentPrice().getValue()) + getCurrencySymbol(instrument.getCurrentPrice().getCurrency());
        String totalPriceStr = priceFormat.format(instrument.getCurrentPrice().getValue().multiply(instrument.getQuantity())) + getCurrencySymbol(instrument.getCurrentPrice().getCurrency());

        // Формируем первую строку
        sb.append(String.format("%-14s %-8s %12s %12s\n", name, quantityStr, priceStr, totalPriceStr));

        // --- ДАННЫЕ ДЛЯ ВТОРОЙ СТРОКИ (ИНФОРМАЦИЯ О ПРИБЫЛИ) ---
        BigDecimal profitValue = instrument.getTotalProfit();
        Money averageBuyPrice = instrument.getAverageBuyPrice();
        // Не показываем строку P/L для фиатных валют или если данных нет
        if (profitValue != null && profitValue.signum() != 0) {
            BigDecimal totalInvested = instrument.getAverageBuyPrice().getValue().multiply(instrument.getQuantity());

            BigDecimal profitPercentage = BigDecimal.ZERO;
            if (totalInvested.signum() != 0) {
                profitPercentage = profitValue.multiply(BigDecimal.valueOf(100))
                        .divide(totalInvested, 2, RoundingMode.HALF_UP);
            }
            String profitCurrencySymbol = getCurrencySymbol(averageBuyPrice.getCurrency());
            String profitStr = formatProfit(profitValue, profitCurrencySymbol);
            String percentageStr = formatPercentage(profitPercentage);

            sb.append(String.format("  └ P/L: %s (%s)\n", profitStr, percentageStr));
        }
        return sb.toString();
    }

    /**
     * Генерирует текстовый блок со структурой портфеля в процентах.
     */
    private String generateAllocationSummary(Portfolio portfolio) {
        BigDecimal totalValue = portfolio.getTotalAmountPortfolio().getValue();
        String currency = portfolio.getTotalAmountPortfolio().getCurrency();
        // Если портфель пустой, не показываем блок
        if (totalValue.signum() == 0) {
            return "";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("\n<b>Структура портфеля:</b>\n");

        // Рассчитываем и добавляем каждую категорию
        addAssetAllocationLine(summary, "Акции \uD83D\uDCC8", portfolio.getTotalAmountShares(), totalValue);
        addAssetAllocationLine(summary, "Облигации \uD83D\uDCDC", portfolio.getTotalAmountBonds(), totalValue);
        addAssetAllocationLine(summary, "Фонды \uD83D\uDDA5️", portfolio.getTotalAmountEtfs(), totalValue);
        addAssetAllocationLine(summary, "Валюта \uD83D\uDCB0", portfolio.getTotalAmountCurrencies(), totalValue);
        addAssetAllocationLine(summary, "<b>Стоимость</b>: %s " + currency + "\n", totalValue);
        addAssetAllocationLine(summary, "<b>Профит</b>: %s%%", portfolio.getExpectedYield());
        return summary.toString();
    }

    /**
     * Возвращает символ валюты по ее коду
     */
    public String getCurrencySymbol(String currencyCode) {
        switch (currencyCode.toUpperCase()) {
            case "RUB":
                return "₽";
            case "USD":
                return "$";
            case "EUR":
                return "€";
            default:
                return " " + currencyCode;
        }
    }

    private void addAssetAllocationLine(StringBuilder sb, String name, Money amount, BigDecimal total) {
        if (amount != null && amount.getValue().signum() != 0) {
            BigDecimal percentage = amount.getValue()
                    .multiply(new BigDecimal(100))
                    .divide(total, 2, RoundingMode.HALF_UP);
            sb.append(String.format("%-15s %s - %s%%\n", name + ":", amount.getValue().setScale(2, RoundingMode.HALF_UP), percentage));
        }
    }

    private void addAssetAllocationLine(StringBuilder sb, String stringFormat, BigDecimal bigDecimal) {
        if (bigDecimal != null && bigDecimal.signum() != 0) {
            sb.append(String.format(stringFormat, bigDecimal.setScale(2, RoundingMode.HALF_UP)));
        }
    }

    private String getFriendlyTypeName(String instrumentType) {
        switch (instrumentType.toLowerCase()) {
            case "share":
                return "Акции \uD83D\uDCC8";
            case "bond":
                return "Облигации \uD83D\uDCDC";
            case "etf":
                return "Фонды \uD83D\uDDA5️";
            case "currency":
                return "Валюта \uD83D\uDCB0";
            default:
                return "Прочее";
        }
    }

    private String formatProfit(BigDecimal value, String currencySymbol) {
        DecimalFormat profitFormat = new DecimalFormat("#,##0"); // Формат без копеек для краткости
        String formattedValue = profitFormat.format(value);
        if (value.signum() > 0) {
            return "+" + formattedValue + currencySymbol;
        }
        return formattedValue + currencySymbol; // Минус будет добавлен автоматически
    }

    /**
     * Форматирует процент, добавляя знак, эмодзи и символ %.
     */
    private String formatPercentage(BigDecimal percentage) {
        String emoji = "";
        String sign = "";
        if (percentage.signum() > 0) {
            emoji = "\uD83D\uDCC8"; // 📈
            sign = "+";
        } else if (percentage.signum() < 0) {
            emoji = "\uD83D\uDCC9"; // 📉
        }

        return String.format("%s%s%s%%", emoji, sign, percentage.abs());
    }
}
