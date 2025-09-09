package org.invest.bot.invest.core.modules.balanse;

import org.invest.bot.invest.core.modules.balanse.actions.BuyAction;
import org.invest.bot.invest.core.modules.balanse.actions.SellAction;
import org.invest.bot.invest.core.objects.InstrumentObj;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.models.Money;
import ru.tinkoff.piapi.core.models.Portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.invest.bot.core.DataConvertUtility.getPercentCount;
import static org.invest.bot.core.DataConvertUtility.quotationToBigDecimal;
import static org.invest.bot.invest.core.modules.balanse.BalanceModuleConf.*;

@Service
public class BalanceService {

    public AnalysisResult analyzePortfolio(Portfolio portfolio, List<InstrumentObj> instrumentObjs) {
        Map<BalanceModuleConf, BigDecimal> classDeviations = new HashMap<>();
        Money totalValue = portfolio.getTotalAmountPortfolio();
        if (totalValue.getValue().signum() == 0) {
            return new AnalysisResult(new HashMap<>(), new ConcentrationProblem(new ArrayList<>(), new ArrayList<>()));
        }

        // --- ШАГ 1: Получаем ФАКТИЧЕСКОЕ процентное распределение ---
        Map<BalanceModuleConf, BigDecimal> actualDistribution = calculateActualDistribution(portfolio, instrumentObjs);

        // --- ШАГ 2: Находим отклонения, сравнивая факт с целью ---
        for (Map.Entry<BalanceModuleConf, BigDecimal> entry : actualDistribution.entrySet()) {
            BalanceModuleConf target = entry.getKey();
            BigDecimal currentPercentage = entry.getValue();

            BigDecimal difference = currentPercentage.subtract(target.value).abs();
            if (difference.compareTo(ALLOCATION_TOLERANCE.value) > 0) {
                classDeviations.put(target, currentPercentage);
            }
        }

        // --- ШАГ 3: Находим отклонения по КОНЦЕНТРАЦИИ ---
        ConcentrationProblem concentrationProblems = concentrationProblems(totalValue, instrumentObjs);

        return new AnalysisResult(classDeviations, concentrationProblems);
    }

    /**
     * ПУБЛИЧНЫЙ МЕТОД-"ДИРИЖЕР"
     * Создает полный план ребалансировки на основе результатов анализа.
     */
    public RebalancePlan createPlan(Portfolio portfolio, List<InstrumentObj> instrumentObjs) {
        AnalysisResult analysisResult = analyzePortfolio(portfolio, instrumentObjs);
        List<SellAction> sellActions = calculateSellActions(analysisResult, portfolio.getTotalAmountPortfolio().getValue());
        BigDecimal totalCashFromSales = sellActions.stream().map(SellAction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BuyAction> buyActions = calculateBuyActions(analysisResult, portfolio.getTotalAmountPortfolio().getValue());

        return new RebalancePlan(sellActions, buyActions, totalCashFromSales);
    }


    private Map<AssetGroup, BigDecimal> calculateActualGroupValues(List<InstrumentObj> instrumentObjs) {
        Map<AssetGroup, BigDecimal> values = new HashMap<>();
        // Инициализируем все группы нулями
        for (AssetGroup group : AssetGroup.values()) {
            values.put(group, BigDecimal.ZERO);
        }

        for (InstrumentObj inst : instrumentObjs) {
            BigDecimal positionValue = inst.getQuantity().multiply(inst.getCurrentPrice().getValue());

            // Определяем, к какой группе относится инструмент, и добавляем его стоимость
            if (getCoreStockTicket().equals(inst.getTicker())) {
                values.merge(AssetGroup.STOCKS_CORE, positionValue, BigDecimal::add);
            } else if (getReserveTickets().contains(inst.getTicker())) {
                values.merge(AssetGroup.RESERVE, positionValue, BigDecimal::add);
            } else if (getProtectionTickets().contains(inst.getTicker())) {
                values.merge(AssetGroup.PROTECTION, positionValue, BigDecimal::add);
            } else if ("share".equals(inst.getType())) {
                values.merge(AssetGroup.STOCKS_SATELLITE, positionValue, BigDecimal::add);
            } else if ("bond".equals(inst.getType())) {
                values.merge(AssetGroup.BONDS, positionValue, BigDecimal::add);
            }
        }
        return values;
    }

    public Map<BalanceModuleConf, BigDecimal> calculateActualDistribution(Portfolio portfolio, List<InstrumentObj> instrumentObjs) {
        Map<BalanceModuleConf, BigDecimal> distribution = new HashMap<>();
        Money totalValue = portfolio.getTotalAmountPortfolio();
        if (totalValue.getValue().signum() == 0) {
            return distribution;
        }

        // 1. Получаем абсолютные значения из нашего "единого источника правды"
        Map<AssetGroup, BigDecimal> actualValues = calculateActualGroupValues(instrumentObjs);

        // 2. Превращаем абсолютные значения в проценты и кладем в карту
        distribution.put(TARGET_STOCK_CORE_PERCENTAGE, getPercentCount(totalValue, actualValues.get(AssetGroup.STOCKS_CORE)));
        distribution.put(TARGET_STOCK_SATELLITE__PERCENTAGE, getPercentCount(totalValue, actualValues.get(AssetGroup.STOCKS_SATELLITE)));
        distribution.put(TARGET_BOND_PERCENTAGE, getPercentCount(totalValue, actualValues.get(AssetGroup.BONDS)));
        distribution.put(TARGET_PROTECTION_PERCENTAGE, getPercentCount(totalValue, actualValues.get(AssetGroup.PROTECTION)));
        distribution.put(TARGET_RESERVE_PERCENTAGE, getPercentCount(totalValue, actualValues.get(AssetGroup.RESERVE)));

        return distribution;
    }

    private ConcentrationProblem concentrationProblems(Money totalValue, List<InstrumentObj> instrumentObjs) {
        List<String> concentrationHumanProblems = new ArrayList<>();
        List<InstrumentObj> concentrationInstrumentProblems = new ArrayList<>();
        for (InstrumentObj inst : instrumentObjs.stream()
                .filter(filter -> filter.getType().equals("share")).collect(Collectors.toSet())) {
            BigDecimal positionValue = inst.getQuantity().multiply(inst.getCurrentPrice().getValue());
            BigDecimal percentage = getPercentCount(totalValue, positionValue);
            if (percentage.compareTo(SATELLITE_CONCENTRATION_LIMIT.value) > 0) {
                String problem = String.format(
                        "‼️ <b>Риск концентрации:</b> доля '%s' (<b>%s%%</b>) превышает лимит в <b>%s%%</b>!",
                        inst.getName(), percentage.setScale(2, RoundingMode.HALF_UP), SATELLITE_CONCENTRATION_LIMIT.value
                );
                concentrationHumanProblems.add(problem);
                concentrationInstrumentProblems.add(inst);
            }
        }
        return new ConcentrationProblem(concentrationHumanProblems, concentrationInstrumentProblems);
    }

    /**
     * ПРИВАТНЫЙ МЕТОД №1: Отвечает ТОЛЬКО за расчет действий по ПРОДАЖЕ.
     */
    private List<SellAction> calculateSellActions(AnalysisResult analysisResult, BigDecimal totalPortfolioValue) {
        List<SellAction> actions = new ArrayList<>();

        // Работаем со списком проблемных инструментов, который вы уже подготовили
        for (InstrumentObj instToSell : analysisResult.concentrationProblems.getConcentrationInstrumentProblems()) {
            BigDecimal pricePerShare = instToSell.getCurrentPrice().getValue();
            int sharesPerLot = instToSell.getLot(); // Правильная лотность из WhiteListOfShares
            int totalSharesInPortfolio = instToSell.getQuantity().intValue(); // Всего ШТУК акций в портфеле

            if (pricePerShare.signum() == 0 || sharesPerLot == 0) {
                continue;
            }

            // --- ШАГ 0.5: ВЫЧИСЛЯЕМ ПРАВИЛЬНОЕ КОЛИЧЕСТВО ЛОТОВ В ПОРТФЕЛЕ ---
            // ЭТО ГЛАВНОЕ ИСПРАВЛЕНИЕ!
            int currentLotsInPortfolio = totalSharesInPortfolio / sharesPerLot;

            // --- Шаг 1: Определяем "потолок" стоимости позиции в рублях ---
            BigDecimal targetValueInRub = totalPortfolioValue
                    .multiply(SATELLITE_CONCENTRATION_LIMIT.value)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

            // --- Шаг 2: Вычисляем реальную цену одного лота ---
            BigDecimal pricePerLot = pricePerShare.multiply(BigDecimal.valueOf(sharesPerLot));

            // --- Шаг 3: Рассчитываем, сколько лотов мы можем себе позволить оставить ---
            int targetLots = targetValueInRub.divide(pricePerLot, 0, RoundingMode.FLOOR).intValue();

            // --- Шаг 4: Главная формула: сколько лотов нужно продать ---
            // Теперь здесь будут правильные цифры: 1 - 0 = 1
            int lotsToSell = currentLotsInPortfolio - targetLots;

            // --- Шаг 5: Если нужно что-то продать, создаем SellAction ---
            if (lotsToSell > 0) {
                BigDecimal sellAmount = pricePerLot.multiply(new BigDecimal(lotsToSell));

                actions.add(new SellAction(
                        instToSell.getTicker(),
                        instToSell.getName(),
                        lotsToSell,
                        sellAmount,
                        "Снижение риска концентрации"
                ));
            }
        }
        return actions;
    }

    /**
     * ПРИВАТНЫЙ МЕТОД №2: Отвечает ТОЛЬКО за расчет действий по ПОКУПКЕ.
     */
    private List<BuyAction> calculateBuyActions(AnalysisResult analysisResult, BigDecimal availableCash) {
        List<BuyAction> actions = new ArrayList<>();
        // Если продавать ничего не нужно, то и покупать не на что.
        if (availableCash.signum() == 0) {
            return actions;
        }

        // --- Шаг 2.1: Находим все категории с недобором и считаем ОБЩИЙ дефицит ---
        Map<BalanceModuleConf, BigDecimal> deficits = new HashMap<>();
        BigDecimal totalDeficitPercentage = BigDecimal.ZERO;

        for (Map.Entry<BalanceModuleConf, BigDecimal> entry : analysisResult.classDeviations.entrySet()) {
            BalanceModuleConf target = entry.getKey();
            BigDecimal currentPercent = entry.getValue();

            if (currentPercent.compareTo(target.value) < 0) {
                BigDecimal deficit = target.value.subtract(currentPercent);
                deficits.put(target, deficit);
                totalDeficitPercentage = totalDeficitPercentage.add(deficit);
            }
        }

        // Если нет категорий с недобором, выходим.
        if (totalDeficitPercentage.signum() == 0) {
            return actions;
        }

        // --- Шаг 2.2: Распределяем доступные деньги ПРОПОРЦИОНАЛЬНО дефициту ---
        for (Map.Entry<BalanceModuleConf, BigDecimal> entry : deficits.entrySet()) {
            BalanceModuleConf target = entry.getKey();
            BigDecimal deficit = entry.getValue();

            // Считаем "вес" этой категории в общем дефиците
            BigDecimal categoryWeight = deficit.divide(totalDeficitPercentage, 4, RoundingMode.HALF_UP);

            // Вычисляем, какая сумма из нашего "бюджета" пойдет на эту категорию
            BigDecimal buyAmount = availableCash.multiply(categoryWeight).setScale(0, RoundingMode.DOWN);

            if (buyAmount.signum() > 0) {
                actions.add(new BuyAction(target, buyAmount, "Пропорциональное восстановление баланса"));
            }
        }

        return actions;
    }

    // Внутренний enum для ключей карты, чтобы избежать "магических строк"
    private enum AssetGroup {
        STOCKS_CORE, STOCKS_SATELLITE, BONDS, PROTECTION, RESERVE
    }
}