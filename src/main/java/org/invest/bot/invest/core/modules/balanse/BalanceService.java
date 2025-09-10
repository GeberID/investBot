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
import static org.invest.bot.invest.core.modules.balanse.PortfolioInstrumentStructure.*;

@Service
public class BalanceService {
    
    public RebalancePlan createRebalancePlan(AnalysisResult analysisResult, Portfolio portfolio) {
        List<SellAction> sellActions = calculateSellActions(analysisResult, portfolio.getTotalAmountPortfolio().getValue());
        BigDecimal totalCashFromSales = sellActions.stream().map(SellAction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BuyAction> buyActions = calculateBuyActions(analysisResult, portfolio.getTotalAmountPortfolio().getValue());
        return new RebalancePlan(sellActions, buyActions, totalCashFromSales);
    }

    public AnalysisResult analyzePortfolio(Portfolio portfolio, List<InstrumentObj> instrumentObjs) {
        Money totalValue = portfolio.getTotalAmountPortfolio();
        if (totalValue.getValue().signum() == 0) {
            return new AnalysisResult(new HashMap<>(), new ConcentrationProblem(new ArrayList<>(), new ArrayList<>()));
        }
        Map<PortfolioInstrumentStructure, BigDecimal> classDeviations = getClassDeviations(totalValue,instrumentObjs);
        ConcentrationProblem concentrationProblems = concentrationProblems(totalValue, instrumentObjs);
        return new AnalysisResult(classDeviations, concentrationProblems);
    }

    public Map<PortfolioInstrumentStructure, BigDecimal> calculateActualDistribution(Money totalValue, List<InstrumentObj> instrumentObjs) {
        Map<PortfolioInstrumentStructure, BigDecimal> distribution = new HashMap<>();

        // 1. Получаем абсолютные значения из нашего "единого источника правды"
        Map<PortfolioInstrumentStructure, BigDecimal> actualValues = calculateActualGroupValues(instrumentObjs);

        // 2. Превращаем абсолютные значения в проценты и кладем в карту
        distribution.put(TARGET_STOCK_CORE, getPercentCount(totalValue, actualValues.get(PortfolioInstrumentStructure.TARGET_STOCK_CORE)));
        distribution.put(TARGET_STOCK_SATELLITE, getPercentCount(totalValue, actualValues.get(PortfolioInstrumentStructure.TARGET_STOCK_SATELLITE)));
        distribution.put(TARGET_BOND, getPercentCount(totalValue, actualValues.get(PortfolioInstrumentStructure.TARGET_BOND)));
        distribution.put(TARGET_PROTECTION, getPercentCount(totalValue, actualValues.get(PortfolioInstrumentStructure.TARGET_PROTECTION)));
        distribution.put(TARGET_RESERVE, getPercentCount(totalValue, actualValues.get(PortfolioInstrumentStructure.TARGET_RESERVE)));

        return distribution;
    }

    private Map<PortfolioInstrumentStructure, BigDecimal> calculateActualGroupValues(List<InstrumentObj> instrumentObjs) {
        Map<PortfolioInstrumentStructure, BigDecimal> values = new HashMap<>();
        // Инициализируем все группы нулями
        for (PortfolioInstrumentStructure group : PortfolioInstrumentStructure.values()) {
            values.put(group, BigDecimal.ZERO);
        }
        for (InstrumentObj inst : instrumentObjs) {
            BigDecimal positionValue = inst.getQuantity().multiply(inst.getCurrentPrice().getValue());
            // Определяем, к какой группе относится инструмент, и добавляем его стоимость
            if (getCoreStockTicket().equals(inst.getTicker())) {
                values.merge(PortfolioInstrumentStructure.TARGET_STOCK_CORE, positionValue, BigDecimal::add);
            } else if (getReserveTickets().contains(inst.getTicker())) {
                values.merge(PortfolioInstrumentStructure.TARGET_RESERVE, positionValue, BigDecimal::add);
            } else if (getProtectionTickets().contains(inst.getTicker())) {
                values.merge(PortfolioInstrumentStructure.TARGET_PROTECTION, positionValue, BigDecimal::add);
            } else if ("share".equals(inst.getType())) {
                values.merge(PortfolioInstrumentStructure.TARGET_STOCK_SATELLITE, positionValue, BigDecimal::add);
            } else if ("bond".equals(inst.getType())) {
                values.merge(PortfolioInstrumentStructure.TARGET_BOND, positionValue, BigDecimal::add);
            }
        }
        return values;
    }
    
    private Map<PortfolioInstrumentStructure, BigDecimal> getClassDeviations(Money totalValue, List<InstrumentObj> instrumentObjs){
        Map<PortfolioInstrumentStructure, BigDecimal> classDeviations = new HashMap<>();
        // --- ШАГ 1: Получаем ФАКТИЧЕСКОЕ процентное распределение ---
        Map<PortfolioInstrumentStructure, BigDecimal> actualDistribution = calculateActualDistribution(totalValue, instrumentObjs);

        // --- ШАГ 2: Находим отклонения, сравнивая факт с целью ---
        for (Map.Entry<PortfolioInstrumentStructure, BigDecimal> entry : actualDistribution.entrySet()) {
            PortfolioInstrumentStructure target = entry.getKey();
            BigDecimal currentPercentage = entry.getValue();

            BigDecimal difference = currentPercentage.subtract(target.value).abs();
            if (difference.compareTo(ALLOCATION_TOLERANCE.value) > 0) {
                classDeviations.put(target, currentPercentage);
            }
        }
        return classDeviations;
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

            // --- ВЫЧИСЛЯЕМ ПРАВИЛЬНОЕ КОЛИЧЕСТВО ЛОТОВ В ПОРТФЕЛЕ ---
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
                        instToSell.getFigi(),
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

        // ШАГ 1: Найти "дыры" в портфеле (дефицитные категории)
        Map<PortfolioInstrumentStructure, BigDecimal> deficits = new HashMap<>();
        BigDecimal totalDeficitPercentage = BigDecimal.ZERO;

        for (Map.Entry<PortfolioInstrumentStructure, BigDecimal> entry : analysisResult.classDeviations.entrySet()) {
            PortfolioInstrumentStructure target = entry.getKey();
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
        for (Map.Entry<PortfolioInstrumentStructure, BigDecimal> entry : deficits.entrySet()) {
            PortfolioInstrumentStructure target = entry.getKey();
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
}