package org.invest.bot.core.messages;

import org.invest.bot.invest.core.modules.balanse.ActualDistribution;
import org.invest.bot.invest.core.modules.balanse.ConcentrationProblem;
import org.invest.bot.invest.core.modules.balanse.PortfolioInstrumentStructure;
import org.invest.bot.invest.core.modules.balanse.RebalancePlan;
import org.invest.bot.invest.core.modules.balanse.actions.BuyAction;
import org.invest.bot.invest.core.modules.balanse.actions.SellAction;
import org.invest.bot.invest.core.objects.InstrumentObj;
import org.springframework.stereotype.Component;
import ru.tinkoff.piapi.contract.v1.Dividend;
import ru.tinkoff.piapi.core.models.Money;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.invest.bot.core.DataConvertUtility.convertTimeStampToStringWithoutYearSymbol;
import static org.invest.bot.core.DataConvertUtility.getPercentCount;

@Component
public class MessageFormatter {
    /**
     * НОВЫЙ ПУБЛИЧНЫЙ МЕТОД
     * Форматирует план ребалансировки в понятное для человека сообщение.
     * @param plan Объект RebalancePlan, полученный от RebalanceService.
     * @return Готовый к отправке текст сообщения.
     */
    public String formatRebalancePlan(RebalancePlan plan) {
        boolean hasSells = !plan.sellActions().isEmpty();
        boolean hasBuys = !plan.buyActions().isEmpty();

        // --- Сценарий 1: Портфель уже сбалансирован ---
        if (!hasSells && !hasBuys) {
            return "✅ <b>План ребалансировки</b>\n\n" +
                    "Ваш портфель идеально сбалансирован. Никаких действий не требуется.";
        }

        StringBuilder sb = new StringBuilder();

        // --- Сценарий 2: Классическая ребалансировка (есть и продажи, и покупки) ---
        if (hasSells) {
            sb.append("<b>План Ребалансировки Портфеля</b>\n\n");
            // Блок Продажи
            sb.append(formatSellBlock(plan.sellActions(), plan.totalCashFromSales()));

            // Блок Покупки
            if (hasBuys) {
                sb.append("\n"); // Добавляем отступ между блоками
                sb.append(formatBuyBlock(plan.buyActions()));
            } else if (!plan.buyActions().isEmpty()) { // <--- Измените это
                sb.append(formatBuyBlock(plan.buyActions()));
            } else {
                // Новый блок: если нет конкретных покупок, даем общую рекомендацию
                sb.append("\n<b>Шаг 2: Размещение средств</b>\n");
                sb.append("<i>Рекомендуется направить высвобожденные средства на пополнение категорий с недостаточной долей.</i>\n");
            }
        }
        // --- Сценарий 3: Нужно только покупать (например, после пополнения счета) ---
        else if (hasBuys) {
            sb.append("<b>План пополнения баланса</b>\n\n");
            // Просто используем наш новый хелпер, изменив заголовок
            String buyBlock = formatBuyBlock(plan.buyActions())
                    .replace("<b>Шаг 2: Покупка</b>\n", ""); // Убираем лишний заголовок
            sb.append(buyBlock);
        }

        sb.append("\n<i>Все расчеты являются приблизительными.</i>");
        return sb.toString();
    }

    public String reportInstrument(String ticker, Portfolio portfolio,
                                   InstrumentObj targetPosition,
                                   Position portfolioPosition,
                                   BigDecimal sma50,
                                   BigDecimal sma200,
                                   BigDecimal weeklyRsi,
                                   BigDecimal macdLine,
                                   BigDecimal signalLine,
                                   List<Dividend> dividends) {
        if (targetPosition == null || portfolioPosition == null) {
            return String.format("Инструмент с тикером '%s' не найден в вашем портфеле.", ticker);
        }

        StringBuilder report = new StringBuilder();
        report.append(String.format("<b>Сводка по %s (%s)</b>\n\n", targetPosition.getName(), targetPosition.getTicker()));
        positionFormatter(report,portfolioPosition);
        finResultFormatter(report,targetPosition);
        techAnalyseFormatter(report,portfolioPosition,sma50,sma200,weeklyRsi,macdLine,signalLine);
        corporateSituationsFormatter(report,portfolio,targetPosition,dividends);
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
        sb.append("<pre>")
                .append(String.format("%-14s %-8s %12s %12s\n", "Название", "Кол-во", "Цена", "Стоимость"))
                .append("--------------------------------------------------\n");
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
     * Форматирует отчет о стратегических отклонениях.
     *
     * @param result Карта с отклонениями от BalanceService.
     * @return Готовый к отправке текст сообщения.
     */
    public String formatBalanceDeviations(ConcentrationProblem result) {
        if (result.getConcentrationInstrumentProblems().isEmpty()) {
            return "✅ <b>Стратегический анализ портфеля</b>\n\n" +
                    "Отклонений от вашей стратегии не обнаружено.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("️️️️️️️️️️️️️️️<b>Cтратегический анализ портфеля</b>\n\nОбнаружены следующие отклонения:\n");

        for (ActualDistribution actualDistributionList : result.getConcentrationInstrumentProblems()) {
            PortfolioInstrumentStructure target = actualDistributionList.getInstrumentStructure();
            BigDecimal fact = actualDistributionList.getTotalPresent();
            sb.append("\n• ").append(formatDeviationLine(target, fact));
        }
        for (String problem : result.getConcentrationHumanProblems()) {
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

    private StringBuilder positionFormatter(StringBuilder report,Position portfolioPosition){
                report.append("<b>Позиция в портфеле:</b>\n")
                .append(String.format(" • Количество: %s шт.\n", portfolioPosition.getQuantity().setScale(0, RoundingMode.DOWN)))
                .append(String.format(" • Средняя цена: %s\n", formatMoney(portfolioPosition.getAveragePositionPrice())))
                .append(String.format(" • Текущая цена: %s\n", formatMoney(portfolioPosition.getCurrentPrice())))
                .append(String.format(" • Вся цена: %s\n", portfolioPosition.getCurrentPrice().getValue()
                        .multiply(portfolioPosition.getQuantity()).setScale(2, RoundingMode.HALF_UP)));
        return report;
    }

    private StringBuilder techAnalyseFormatter(StringBuilder report,
                                               Position portfolioPosition,
                                               BigDecimal sma50,
                                               BigDecimal sma200,
                                               BigDecimal weeklyRsi,
                                               BigDecimal macdLine,
                                               BigDecimal signalLine){
        report.append("\n<b>Технический анализ (долгосрок):</b>\n")
                .append(formatTrend200(portfolioPosition.getCurrentPrice().getValue(), sma200))
                .append(formatTrend50(portfolioPosition.getCurrentPrice().getValue(), sma50))
                .append(formatRsi(weeklyRsi))
                .append(formatMacd(macdLine,signalLine));
        return report;
    }

    private StringBuilder finResultFormatter(StringBuilder report,  InstrumentObj targetPosition){
        report.append("\n<b>Финансовый результат:</b>\n");

        BigDecimal profitValue = targetPosition.getTotalProfit();
        Money averageBuyPrice = targetPosition.getAverageBuyPrice();

        if (profitValue != null && averageBuyPrice != null) {
            BigDecimal totalInvested = averageBuyPrice.getValue().multiply(targetPosition.getQuantity());

            BigDecimal profitPercentage = BigDecimal.ZERO;
            if (totalInvested.signum() != 0) {
                profitPercentage = profitValue.multiply(BigDecimal.valueOf(100))
                        .divide(totalInvested, 2, RoundingMode.HALF_UP);
            }
            String profitCurrencySymbol = getCurrencySymbol(averageBuyPrice.getCurrency());
            String profitStr = formatProfit(profitValue, profitCurrencySymbol);
            String percentageStr = formatPercentage(profitPercentage);
            report.append(String.format(" • Прибыль/убыток: %s (%s)\n", profitStr, percentageStr));
        } else {
            report.append(" • Данные о прибыли недоступны.\n");
        }
        return report;
    }

    private StringBuilder corporateSituationsFormatter(StringBuilder report,
                                                       Portfolio portfolio,
                                                       InstrumentObj targetPosition,
                                                       List<Dividend> dividends){
        report.append("\n<b>Корпоративные события:</b>\n")
                .append(formatDividends(dividends, targetPosition) + "\n")
                .append(" • Доля в портфеле: ")
                .append(getPercentCount(portfolio.getTotalAmountPortfolio(),
                        targetPosition.getQuantity().multiply(targetPosition.getCurrentPrice().getValue())))
                .append("\n")
                .append(" • Тип: Спутник\n");
        return report;
    }


    private String formatTrend200(BigDecimal currentPrice, BigDecimal smaValue) {
        if (currentPrice == null || smaValue == null || smaValue.signum() == 0) {
            return " • Тренд (vs SMA 200): недостаточно данных\n";
        }
        String trend = currentPrice.compareTo(smaValue) > 0 ? "✅ Бычий" : "❌ Медвежий";
        return String.format(" • Тренд (vs SMA 200): %s (%s / %s)\n",
                trend,
                currentPrice.setScale(2, RoundingMode.HALF_UP),
                smaValue.setScale(2, RoundingMode.HALF_UP)
        );
    }

    private String formatTrend50(BigDecimal currentPrice, BigDecimal smaValue) {
        if (currentPrice == null || smaValue == null || smaValue.signum() == 0) {
            return " • Тренд (vs SMA 50): недостаточно данных\n";
        }
        String trend = currentPrice.compareTo(smaValue) > 0 ? "✅ Бычий" : "❌ Медвежий";
        return String.format(" • Тренд (vs SMA 50): %s (%s / %s)\n",
                trend,
                currentPrice.setScale(2, RoundingMode.HALF_UP),
                smaValue.setScale(2, RoundingMode.HALF_UP)
        );
    }

    private String formatRsi(BigDecimal rsiValue) {
        if (rsiValue == null) {
            return " • Недельный RSI(14): недостаточно данных\n";
        }
        String rsiStatus;
        if (rsiValue.compareTo(new BigDecimal("70")) > 0) {
            rsiStatus = "🥵 Перекупленность";
        } else if (rsiValue.compareTo(new BigDecimal("30")) < 0) {
            rsiStatus = "🥶 Перепроданность";
        } else {
            rsiStatus = "⚖️ Нейтральный";
        }
        return String.format(" • Недельный RSI(14): %s (%s)\n", rsiStatus, rsiValue.setScale(2, RoundingMode.HALF_UP));
    }

    private String formatMacd(BigDecimal macdLine,BigDecimal signalLine) {
        if (macdLine == null && signalLine == null) {
            return " • Импульс (MACD): недостаточно данных\n";
        }
        String status;
        if (macdLine.compareTo(signalLine) > 0) {
            status = "✅ Бычий (линия MACD выше сигнальной)";
        } else if (macdLine.compareTo(signalLine) < 0) {
            status = "❌ Медвежий (линия MACD ниже сигнальной)";
        } else {
            status = "⚖️ Нейтральный (линии пересеклись)";
        }
        BigDecimal histogram = macdLine.subtract(signalLine).setScale(2, RoundingMode.HALF_UP);
        return String.format(" • Импульс (MACD): %s\n   └ Гистограмма: %s\n", status, histogram);
    }

    private String formatDividends(List<Dividend> dividends, InstrumentObj targetPosition) {
        if (dividends == null || dividends.isEmpty()) {
            return " • Дивиденды в ближайший год не анонсированы.\n";
        }
        StringBuilder sb = new StringBuilder();
        Dividend nextDividend = dividends.get(0); // API возвращает их отсортированными по дате
        sb.append(" • Ближайшие дивиденды:" + convertTimeStampToStringWithoutYearSymbol(nextDividend.getLastBuyDate()) + "\n")
                .append(" • Cумма дивидендов на 1 акцию: ")
                .append(nextDividend.getDividendNet().getUnits() + " " + nextDividend.getDividendNet().getCurrency() + "\n")
                .append(" • Получишь дивидендов: " + targetPosition.getQuantity().multiply(BigDecimal.valueOf(nextDividend.getDividendNet().getUnits())));

        return sb.toString();
    }

    /**
     * Форматирует одну строку для отчета об отклонениях.
     */
    private String formatDeviationLine(PortfolioInstrumentStructure target, BigDecimal fact) {
        String name = getFriendlyNameForTarget(target);
        String direction = fact.compareTo(target.value) > 0 ? "превышает" : "ниже";

        return String.format(
                "<b>%s:</b> фактическая доля (<b>%s%%</b>) %s цели (<b>%s%%</b>)",
                name, fact, direction, target.value
        );
    }

    /**
     * Возвращает человекочитаемое имя для целевого показателя.
     */
    private String getFriendlyNameForTarget(PortfolioInstrumentStructure target) {
        switch (target) {
            case TARGET_STOCK_CORE:
                return "Акции (Ядро)";
            case TARGET_STOCK_SATELLITE:
                return "Акции (Спутники)";
            case TARGET_BOND:
                return "Облигации";
            case TARGET_PROTECTION:
                return "Защита";
            case TARGET_RESERVE:
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
            sb.append(String.format("%-15s %s | %s%%\n", name + ":", amount.getValue()
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

    private String formatMoney(ru.tinkoff.piapi.core.models.Money money) {
        if (money == null) return "N/A";
        return money.getValue().setScale(2, RoundingMode.HALF_UP) + " " + money.getCurrency().toUpperCase();
    }

    private String formatSellBlock(List<SellAction> sellActions, BigDecimal totalCash) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("<b>Шаг 1: Продажа (высвободится ~%s ₽)</b>\n", formatAmount(totalCash)));

        for (SellAction action : sellActions) {
            sb.append(String.format(" • Продать <b>%s</b> (<b>%d</b> лот) на сумму ~<b>%s ₽</b>\n",
                    action.name(), action.lots(), formatAmount(action.amount())));
            sb.append(String.format("   └ <i>Причина: %s</i>\n", action.reason()));
        }
        return sb.toString();
    }

    private String formatBuyBlock(List<BuyAction> buyActions) {
        StringBuilder sb = new StringBuilder();
        sb.append("<b>Шаг 2: Покупка</b>\n");
        sb.append("<i>Рекомендуется направить средства на покупку:</i>\n");

        for (BuyAction action : buyActions) {
            sb.append(String.format(" • Купить <b>%s</b> (<b>%d</b> лот) на сумму ~<b>%s ₽</b>\n",
                    action.name(), action.lots(), formatAmount(action.amount())));
        }
        return sb.toString();
    }

    /**
     * Приватный хелпер для получения "красивых" имен классов активов.
     */
    private String getFriendlyClassName(PortfolioInstrumentStructure target) {
        switch (target) {
            case TARGET_STOCK_CORE: return "Акции (Ядро)";
            case TARGET_STOCK_SATELLITE: return "Акции (Спутники)";
            case TARGET_BOND: return "Облигации";
            case TARGET_PROTECTION: return "Защита";
            case TARGET_RESERVE: return "Резерв";
            default: return "Категория";
        }
    }

    /**
     * Приватный хелпер для красивого форматирования денежных сумм.
     */
    private String formatAmount(BigDecimal amount) {
        // Используем DecimalFormat для разделения тысяч пробелами
        DecimalFormat formatter = new DecimalFormat("#,###");
        return formatter.format(amount).replace(",", " ");
    }
}