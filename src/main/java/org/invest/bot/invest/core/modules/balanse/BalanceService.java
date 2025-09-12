package org.invest.bot.invest.core.modules.balanse;

import lombok.extern.slf4j.Slf4j;
import org.invest.bot.invest.api.InvestApiCore;
import org.invest.bot.invest.core.modules.balanse.actions.BuyAction;
import org.invest.bot.invest.core.modules.balanse.actions.SellAction;
import org.invest.bot.invest.core.objects.InstrumentObj;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.Instrument;
import ru.tinkoff.piapi.contract.v1.Quotation;
import ru.tinkoff.piapi.core.models.Money;
import ru.tinkoff.piapi.core.models.Portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import static org.invest.bot.core.DataConvertUtility.quotationToBigDecimal;
import static org.invest.bot.invest.core.modules.balanse.PortfolioInstrumentStructure.*;

@Service
@Slf4j
public class BalanceService {
    private final InvestApiCore apiCore;

    public BalanceService(InvestApiCore apiCore) {
        this.apiCore = apiCore;
    }

    public RebalancePlan createRebalancePlan(ConcentrationProblem concentrationProblem, Portfolio portfolio) {
        log.info("createRebalancePlan:");
        List<SellAction> sellActions = calculateSellActions(concentrationProblem, portfolio.getTotalAmountPortfolio().getValue());
        log.info("sellActions:");
        for (SellAction sellAction : sellActions) {
            log.info(sellAction.toString());
        }
        BigDecimal totalCashFromSales = sellActions.stream().map(SellAction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        log.info("totalCashFromSales = " + totalCashFromSales);
        List<BuyAction> buyActions = calculateBuyActions(concentrationProblem, totalCashFromSales);
        log.info("buyActions:");
        for (BuyAction buyAction : buyActions) {
            log.info(buyAction.toString());
        }
        return new RebalancePlan(sellActions, buyActions, totalCashFromSales);
    }

    public ConcentrationProblem analyzePortfolio(Portfolio portfolio, List<InstrumentObj> instrumentObjs) {
        Money totalValue = portfolio.getTotalAmountPortfolio();
        if (totalValue.getValue().signum() == 0) {
            return new ConcentrationProblem(new ArrayList<>(), new ArrayList<>());
        }
        return concentrationProblems(totalValue, instrumentObjs);
    }

    private ConcentrationProblem concentrationProblems(Money totalValue, List<InstrumentObj> instrumentObjs) {
        List<ActualDistribution> concentrationInstrumentProblems = ActualDistribution.getAllDistribution(totalValue, instrumentObjs);
        List<String> concentrationHumanProblems = new ArrayList<>();
        for (ActualDistribution concentrationInstrumentProblem : concentrationInstrumentProblems) {
            for (InstrumentObj instrumentObj : concentrationInstrumentProblem.getInstruments().keySet()) {
                String problem = String.format(
                        "‼️ <b>Риск концентрации:</b> доля '%s' (<b>%s%%</b>) превышает лимит в <b>%s%%</b>!",
                        instrumentObj.getName(), concentrationInstrumentProblem.getInstruments().get(instrumentObj).setScale(2, RoundingMode.HALF_UP),
                        Objects.requireNonNull(getLimitByTarget(concentrationInstrumentProblem.getInstrumentStructure())).value
                );
                concentrationHumanProblems.add(problem);
            }

        }
        return new ConcentrationProblem(concentrationHumanProblems, concentrationInstrumentProblems);
    }

    /**
     * ПРИВАТНЫЙ МЕТОД №1: Отвечает ТОЛЬКО за расчет действий по ПРОДАЖЕ.
     */
    private List<SellAction> calculateSellActions(ConcentrationProblem concentrationProblem, BigDecimal totalPortfolioValue) {
        log.info("--- Запуск calculateSellActions ---");
        List<SellAction> actions = new ArrayList<>();

        // Проходим по ВСЕМ категориям, где есть проблемы с концентрацией
        for (ActualDistribution distribution : concentrationProblem.getConcentrationInstrumentProblems()) {
            log.info("=> Анализ категории: {}", distribution.getInstrumentStructure());
            log.info("   - Общая доля категории: {}%", distribution.getTotalPresent().setScale(2, RoundingMode.HALF_UP));

            // Получаем правильный лимит для ЭТОЙ КОНКРЕТНОЙ категории
            PortfolioInstrumentStructure limitConfig = getLimitByTarget(distribution.getInstrumentStructure());
            if (limitConfig == null) {
                log.warn("   - ПРЕДУПРЕЖДЕНИЕ: Лимит концентрации не найден для категории {}. Пропускаем.", distribution.getInstrumentStructure());
                continue;
            }
            BigDecimal concentrationLimit = limitConfig.value;
            log.info("   - Применяемый лимит концентрации: {}%", concentrationLimit);

            // Проходим по ВСЕМ инструментам с превышением внутри этой категории
            for (InstrumentObj instToSell : distribution.getInstruments().keySet()) {
                log.info("   -> Расчет для инструмента: '{}' ({})", instToSell.getName(), instToSell.getTicker());

                BigDecimal pricePerShare = instToSell.getCurrentPrice().getValue();
                int sharesPerLot = instToSell.getLot();
                int totalSharesInPortfolio = instToSell.getQuantity().intValue();

                if (pricePerShare.signum() == 0 || sharesPerLot == 0) {
                    log.warn("      - ПРЕДУПРЕЖДЕНИЕ: Некорректные данные (цена={} или лот={}). Пропускаем.", pricePerShare, sharesPerLot);
                    continue;
                }

                int currentLotsInPortfolio = totalSharesInPortfolio / sharesPerLot;
                log.debug("      - Данные: Цена за шт.={}, Акций в лоте={}, Всего акций={}, Всего лотов={}",
                        pricePerShare.setScale(2), sharesPerLot, totalSharesInPortfolio, currentLotsInPortfolio);


                BigDecimal targetValueInRub = totalPortfolioValue
                        .multiply(concentrationLimit)
                        .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

                BigDecimal pricePerLot = pricePerShare.multiply(BigDecimal.valueOf(sharesPerLot));
                int targetLots = targetValueInRub.divide(pricePerLot, 0, RoundingMode.FLOOR).intValue();
                int lotsToSell = currentLotsInPortfolio - targetLots;

                log.debug("      - Расчет: Целевая стоимость={} RUB, Цена лота={}, Целевое кол-во лотов={}, Лотов к продаже={}",
                        targetValueInRub, pricePerLot.setScale(2), targetLots, lotsToSell);

                if (lotsToSell > 0) {
                    BigDecimal sellAmount = pricePerLot.multiply(new BigDecimal(lotsToSell));
                    log.info("      - РЕШЕНИЕ: Продать {} лот(ов) на сумму ~{} RUB", lotsToSell, sellAmount.setScale(2));
                    actions.add(new SellAction(
                            instToSell.getTicker(),
                            instToSell.getFigi(),
                            instToSell.getName(),
                            lotsToSell,
                            sellAmount,
                            "Снижение риска концентрации"
                    ));
                } else {
                    log.info("      - РЕШЕНИЕ: Продажа не требуется.");
                }
            }
        }
        log.info("--- Завершение calculateSellActions. Сформировано {} действий на продажу. ---", actions.size());
        return actions;
    }

    /**
     * ПРИВАТНЫЙ МЕТОД №2: Отвечает ТОЛЬКО за расчет действий по ПОКУПКЕ.
     */
    private List<BuyAction> calculateBuyActions(ConcentrationProblem concentrationProblem, BigDecimal availableCash) {
        log.info("--- Запуск calculateBuyActions ---");
        List<BuyAction> actions = new ArrayList<>();
        if (availableCash == null || availableCash.signum() <= 0) {
            log.warn("   - Выход: нет доступных средств для покупки (availableCash = {}).", availableCash);
            log.info("--- Завершение calculateBuyActions. Сформировано 0 действий на покупку. ---");
            return actions;
        }
        log.info("   - Доступные средства для покупки: {} RUB", availableCash.setScale(2, RoundingMode.HALF_UP));

        // --- Шаг 1: Находим дефицитные категории ---
        log.info("=> Шаг 1: Поиск дефицитных категорий...");
        Map<PortfolioInstrumentStructure, BigDecimal> deficits = new HashMap<>();
        BigDecimal totalDeficitPercentage = BigDecimal.ZERO;

        for (ActualDistribution distribution : concentrationProblem.getConcentrationInstrumentProblems()) {
            PortfolioInstrumentStructure target = distribution.getInstrumentStructure();
            BigDecimal currentPercent = distribution.getTotalPresent();

            if (currentPercent.compareTo(target.value) < 0) {
                BigDecimal deficit = target.value.subtract(currentPercent);
                deficits.put(target, deficit);
                totalDeficitPercentage = totalDeficitPercentage.add(deficit);
            }
        }

        if (totalDeficitPercentage.signum() == 0) {
            log.info("   - Дефицитные категории не найдены.");
            log.info("--- Завершение calculateBuyActions. Сформировано 0 действий на покупку. ---");
            return actions;
        }
        log.info("   - Найдены дефициты в {} категориях: {}", deficits.size(), deficits.keySet());
        log.info("   - Общий процент дефицита: {}%", totalDeficitPercentage.setScale(2));


        // --- Шаг 2: Определяем, ЧТО покупать и запрашиваем информацию ---
        log.info("=> Шаг 2: Определение инструментов для покупки и запрос данных...");
        Map<PortfolioInstrumentStructure, String> purchaseTickerMap = new HashMap<>();
        getCorePurchaseTicker().ifPresent(ticker -> purchaseTickerMap.put(TARGET_STOCK_CORE, ticker));
        getReservePurchaseTicker().ifPresent(ticker -> purchaseTickerMap.put(TARGET_RESERVE, ticker));
        getProtectionPurchaseTicker().ifPresent(ticker -> purchaseTickerMap.put(TARGET_PROTECTION, ticker));
        log.info("   - Сформирована карта покупок: {}", purchaseTickerMap);

        Map<String, Instrument> instrumentDetailsMap = new HashMap<>();
        for (String ticker : purchaseTickerMap.values()) {
            Instrument instrument = apiCore.getInstrumentByTicker(ticker);
            if (instrument != null) {
                instrumentDetailsMap.put(ticker, instrument);
            }
        }
        log.info("   - Запрошена информация по {} тикерам.", instrumentDetailsMap.size());

        List<String> figisToFetch = instrumentDetailsMap.values().stream().map(Instrument::getFigi).collect(Collectors.toList());
        Map<String, Quotation> lastPrices = apiCore.getLastPrices(figisToFetch);
        log.info("   - Запрошены цены по {} FIGI.", lastPrices.size());


        // --- Шаг 3: Распределяем деньги и конвертируем в лоты ---
        log.info("=> Шаг 3: Распределение средств и расчет лотов...");
        for (Map.Entry<PortfolioInstrumentStructure, BigDecimal> entry : deficits.entrySet()) {
            PortfolioInstrumentStructure category = entry.getKey();
            String tickerToBuy = purchaseTickerMap.get(category);
            log.info("   -> Анализ категории: {}", category);

            if (tickerToBuy == null) {
                log.warn("      - ПРЕДУПРЕЖДЕНИЕ: Инструмент для покупки не определен. Пропускаем.");
                continue;
            }

            Instrument instrumentDetails = instrumentDetailsMap.get(tickerToBuy);
            if (instrumentDetails == null) {
                log.error("      - ОШИБКА: Не удалось получить детали для тикера {}. Пропускаем.", tickerToBuy);
                continue;
            }

            Quotation lastPriceQuotation = lastPrices.get(instrumentDetails.getFigi());
            if (lastPriceQuotation == null) {
                log.error("      - ОШИБКА: Не удалось получить цену для FIGI {}. Пропускаем.", instrumentDetails.getFigi());
                continue;
            }

            BigDecimal lastPrice = quotationToBigDecimal(lastPriceQuotation);
            int lotSize = instrumentDetails.getLot();

            BigDecimal deficit = entry.getValue();
            BigDecimal categoryWeight = deficit.divide(totalDeficitPercentage, 4, RoundingMode.HALF_UP);
            BigDecimal moneyForCategory = availableCash.multiply(categoryWeight).setScale(2, RoundingMode.DOWN);

            BigDecimal pricePerLot = lastPrice.multiply(BigDecimal.valueOf(lotSize));
            if (pricePerLot.signum() <= 0) {
                log.error("      - ОШИБКА: Цена за лот равна нулю для {}. Пропускаем.", tickerToBuy);
                continue;
            }

            int lotsToBuy = moneyForCategory.divide(pricePerLot, 0, RoundingMode.FLOOR).intValue();

            // Используем DEBUG для детальных расчетов, чтобы не засорять основной лог
            log.debug("      - Расчет бюджета: Дефицит={}%, Доля дефицита={}, Бюджет на категорию={}",
                    deficit.setScale(2), categoryWeight, moneyForCategory);
            log.debug("      - Расчет лотов: Цена лота={}, Лотов к покупке (floor)={}",
                    pricePerLot.setScale(2), lotsToBuy);

            if (lotsToBuy > 0) {
                BigDecimal finalBuyAmount = pricePerLot.multiply(new BigDecimal(lotsToBuy));
                log.info("      - РЕШЕНИЕ: Купить '{}' ({} лот) на сумму ~{} RUB",
                        instrumentDetails.getName(), lotsToBuy, finalBuyAmount.setScale(2));
                actions.add(new BuyAction(
                        instrumentDetails.getTicker(),
                        instrumentDetails.getFigi(),
                        instrumentDetails.getName(),
                        lotsToBuy,
                        finalBuyAmount,
                        "Восстановление баланса категории"
                ));
            } else {
                log.info("      - РЕШЕНИЕ: Покупка не требуется (недостаточно средств для 1 лота).");
            }
        }
        log.info("--- Завершение calculateBuyActions. Сформировано {} действий на покупку. ---", actions.size());
        return actions;
    }
}