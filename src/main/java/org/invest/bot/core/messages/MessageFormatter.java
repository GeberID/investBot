package org.invest.bot.core.messages;

import org.invest.bot.invest.api.InvestApiCore;
import org.invest.bot.invest.core.modules.balanse.AnalysisResult;
import org.invest.bot.invest.core.modules.balanse.BalanceModuleConf;
import org.invest.bot.invest.core.modules.instruments.InstrumentAnalysisService;
import org.invest.bot.invest.core.objects.InstrumentObj;
import org.springframework.stereotype.Component;
import ru.tinkoff.piapi.contract.v1.Instrument;
import ru.tinkoff.piapi.core.models.Money;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.invest.bot.core.DataConvertUtility.getPercentCount;

@Component
public class MessageFormatter {

    public String reportInstrument(String ticker,Portfolio portfolio, InstrumentObj targetPosition, Position portfolioPosition) {
        // --- Шаг 1: Обработка случая, если инструмент не найден ---
        if (targetPosition == null || portfolioPosition == null) {
            return String.format("Инструмент с тикером '%s' не найден в вашем портфеле.", ticker);
        }

        StringBuilder report = new StringBuilder();
        report.append(String.format("<b>Сводка по %s (%s)</b>\n\n", targetPosition.getName(), targetPosition.getTicker()));

        BigDecimal percentage = getPercentCount(portfolio.getTotalAmountPortfolio(),
                targetPosition.getQuantity().multiply(targetPosition.getCurrentPrice().getValue()));

        // --- Шаг 2: Блок "Позиция в портфеле" (как у вас) ---
        report.append("<b>Позиция в портфеле:</b>\n");
        report.append(String.format(" • Количество: %s шт.\n", portfolioPosition.getQuantity().setScale(0, RoundingMode.DOWN)));
        report.append(String.format(" • Средняя цена: %s\n", formatMoney(portfolioPosition.getAveragePositionPrice())));
        report.append(String.format(" • Текущая цена: %s\n", formatMoney(portfolioPosition.getCurrentPrice())));
        report.append(String.format(" • Вся цена: %s\n", portfolioPosition.getCurrentPrice().getValue()
                .multiply(portfolioPosition.getQuantity()).setScale(2,RoundingMode.HALF_UP)));

        // --- Шаг 3: БЛОК "ФИНАНСОВЫЙ РЕЗУЛЬТАТ" (НОВАЯ ЛОГИКА) ---
        report.append("\n<b>Финансовый результат:</b>\n");

        BigDecimal profitValue = targetPosition.getTotalProfit();
        Money averageBuyPrice = targetPosition.getAverageBuyPrice();

        // Проверяем, что у нас есть все данные для расчета
        if (profitValue != null && averageBuyPrice != null) {
            // Рассчитываем общую сумму инвестиций
            BigDecimal totalInvested = averageBuyPrice.getValue().multiply(targetPosition.getQuantity());

            BigDecimal profitPercentage = BigDecimal.ZERO;
            // Защита от деления на ноль
            if (totalInvested.signum() != 0) {
                profitPercentage = profitValue.multiply(BigDecimal.valueOf(100))
                        .divide(totalInvested, 2, RoundingMode.HALF_UP);
            }

            // Получаем символ валюты из средней цены покупки (она всегда в той же валюте, что и профит)
            String profitCurrencySymbol = getCurrencySymbol(averageBuyPrice.getCurrency());

            String profitStr = formatProfit(profitValue, profitCurrencySymbol);
            String percentageStr = formatPercentage(profitPercentage);

            report.append(String.format(" • Прибыль/убыток: %s (%s)\n", profitStr, percentageStr));
        } else {
            // Если данных для расчета нет, сообщаем об этом
            report.append(" • Данные о прибыли недоступны.\n");
        }

        // --- Шаг 4: Блок "Роль в стратегии" (пока заглушка) ---
        report.append("\n<b>Роль в стратегии:</b>\n");
        report.append(" • Доля в портфеле: ");
        report.append(percentage).append("\n");
        report.append(" • Тип: Спутник\n");

        return report.toString();
    }

    /**
     * Главный метод, который генерирует полное сообщение о портфеле.
     *
     * @param accountName    Имя счета
     * @param instrumentObjs Список всех инструментов на счете
     * @param filterType     Тип для фильтрации ("share", "bond", "all")
     * @return Готовый к отправке текст сообщения
     */
    public String portfolio(String accountName, List<InstrumentObj> instrumentObjs,
                            Portfolio portfolio, String filterType) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>\uD83D\uDDC3️ ").append(accountName).append("</b>\n\n");
        if (instrumentObjs.isEmpty()) {
            sb.append("В этом портфеле пока нет активов.");
            return sb.toString();
        }
        Map<String, List<InstrumentObj>> groupedInstruments = instrumentObjs.stream()
                .collect(Collectors.groupingBy(InstrumentObj::getType));
        sb.append("<pre>");
        sb.append(String.format("%-14s %-8s %12s %12s\n", "Название", "Кол-во", "Цена", "Стоимость"));
        sb.append("--------------------------------------------------\n");
        for (Map.Entry<String, List<InstrumentObj>> entry : groupedInstruments.entrySet()) {
            String currentType = entry.getKey();
            if (!filterType.equals("all") && !currentType.equals(filterType)) {
                continue;
            }
            sb.append("\n<b>").append(getFriendlyTypeName(currentType)).append("</b>\n");
            for (InstrumentObj instrumentObj : entry.getValue()) {
                sb.append(formatInstrumentMultiLine(instrumentObj));
            }
        }
        sb.append("</pre>");
        sb.append(generateAllocationSummary(portfolio));
        return sb.toString();
    }

    /**
     * НОВЫЙ ПУБЛИЧНЫЙ МЕТОД
     * Форматирует отчет о стратегических отклонениях.
     *
     * @param result Карта с отклонениями от BalanceService.
     * @return Готовый к отправке текст сообщения.
     */
    public String formatBalanceDeviations(AnalysisResult result) {
        if (!result.hasDeviations()) {
            return "✅ <b>Стратегический анализ портфеля</b>\n\n" +
                    "Отклонений от вашей стратегии не обнаружено.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("️️️️️️️️️️️️️️️<b>Cтратегический анализ портфеля</b>\n\nОбнаружены следующие отклонения:\n");
        for (Map.Entry<BalanceModuleConf, BigDecimal> entry : result.classDeviations.entrySet()) {
            BalanceModuleConf target = entry.getKey();
            BigDecimal fact = entry.getValue();
            sb.append("\n• ").append(formatDeviationLine(target, fact));
        }
        for (String problem : result.concentrationProblems) {
            sb.append("\n• ").append(problem);
        }
        sb.append("\n\n<i>Рекомендуется провести ребалансировку.</i>");
        return sb.toString();
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

    /**
     * НОВЫЙ ПРИВАТНЫЙ МЕТОД
     * Форматирует одну строку для отчета об отклонениях.
     */
    private String formatDeviationLine(BalanceModuleConf target, BigDecimal fact) {
        String name = getFriendlyNameForTarget(target);
        String direction = fact.compareTo(target.value) > 0 ? "превышает" : "ниже";

        return String.format(
                "<b>%s:</b> фактическая доля (<b>%s%%</b>) %s цели (<b>%s%%</b>)",
                name, fact, direction, target.value
        );
    }

    /**
     * НОВЫЙ ПРИВАТНЫЙ МЕТОД
     * Возвращает человекочитаемое имя для целевого показателя.
     */
    private String getFriendlyNameForTarget(BalanceModuleConf target) {
        switch (target) {
            case TARGET_STOCK_CORE_PERCENTAGE:
                return "Акции (Ядро)";
            case TARGET_STOCK_SATELLITE__PERCENTAGE:
                return "Акции (Спутники)";
            case TARGET_BOND_PERCENTAGE:
                return "Облигации";
            case TARGET_PROTECTION_PERCENTAGE:
                return "Защита";
            case TARGET_RESERVE_PERCENTAGE:
                return "Резерв";
            default:
                return "Неизвестная категория";
        }
    }

    /**
     * Форматирует информацию об инструменте в две строки.
     * 1-я строка: Название, Кол-во, Цена, Стоимость.
     * 2-я строка: Отступ и информация о прибыли (P/L).
     */
    private String formatInstrumentMultiLine(InstrumentObj instrumentObj) {
        StringBuilder sb = new StringBuilder();
        DecimalFormat qtyFormat = new DecimalFormat("#,###.##");
        DecimalFormat priceFormat = new DecimalFormat("#,##0.00");
        String name = instrumentObj.getName();
        if (name.length() > 13) {
            name = name.substring(0, 12) + ".";
        }
        String quantityStr = qtyFormat.format(instrumentObj.getQuantity());
        String priceStr = priceFormat.format(instrumentObj.getCurrentPrice().getValue())
                + getCurrencySymbol(instrumentObj.getCurrentPrice().getCurrency());
        String totalPriceStr = priceFormat.format(instrumentObj.getCurrentPrice().getValue()
                .multiply(instrumentObj.getQuantity())) + getCurrencySymbol(instrumentObj.getCurrentPrice().getCurrency());
        sb.append(String.format("%-14s %-8s %12s %12s\n", name, quantityStr, priceStr, totalPriceStr));
        BigDecimal profitValue = instrumentObj.getTotalProfit();
        Money averageBuyPrice = instrumentObj.getAverageBuyPrice();
        if (profitValue != null && profitValue.signum() != 0) {
            BigDecimal totalInvested = instrumentObj.getAverageBuyPrice().getValue().multiply(instrumentObj.getQuantity());
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
        Money totalValue = portfolio.getTotalAmountPortfolio();
        String currency = portfolio.getTotalAmountPortfolio().getCurrency();
        if (totalValue.getValue().signum() == 0) {
            return "";
        }
        StringBuilder summary = new StringBuilder();
        summary.append("\n<b>Структура портфеля:</b>\n");
        addAssetAllocationLine(summary, "Акции \uD83D\uDCC8", portfolio.getTotalAmountShares(), totalValue);
        addAssetAllocationLine(summary, "Облигации \uD83D\uDCDC", portfolio.getTotalAmountBonds(), totalValue);
        addAssetAllocationLine(summary, "Фонды \uD83D\uDDA5️", portfolio.getTotalAmountEtfs(), totalValue);
        addAssetAllocationLine(summary, "Валюта \uD83D\uDCB0", portfolio.getTotalAmountCurrencies(), totalValue);
        addAssetAllocationLine(summary, "<b>Стоимость</b>: %s " + currency + "\n", totalValue.getValue());
        addAssetAllocationLine(summary, "<b>Профит</b>: %s%%", portfolio.getExpectedYield());
        return summary.toString();
    }

    private void addAssetAllocationLine(StringBuilder sb, String name, Money amount, Money total) {
        if (amount != null && amount.getValue().signum() != 0) {
            sb.append(String.format("%-15s %s - %s%%\n", name + ":", amount.getValue()
                    .setScale(2, RoundingMode.HALF_UP), getPercentCount(total, amount)));
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
        DecimalFormat profitFormat = new DecimalFormat("#,##0");
        String formattedValue = profitFormat.format(value);
        if (value.signum() > 0) {
            return "+" + formattedValue + currencySymbol;
        }
        return formattedValue + currencySymbol;
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

    // Вспомогательный метод для красивого вывода денег
    private String formatMoney(ru.tinkoff.piapi.core.models.Money money) {
        if (money == null) return "N/A";
        return money.getValue().setScale(2, RoundingMode.HALF_UP) + " " + money.getCurrency().toUpperCase();
    }
}
