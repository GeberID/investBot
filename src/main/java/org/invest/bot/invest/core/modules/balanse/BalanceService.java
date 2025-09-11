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
import java.util.List;
import java.util.Objects;

import static org.invest.bot.invest.core.modules.balanse.PortfolioInstrumentStructure.*;

@Service
public class BalanceService {

    public RebalancePlan createRebalancePlan(ConcentrationProblem concentrationProblem, Portfolio portfolio) {
        List<SellAction> sellActions = calculateSellActions(concentrationProblem, portfolio.getTotalAmountPortfolio().getValue());
        BigDecimal totalCashFromSales = sellActions.stream().map(SellAction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BuyAction> buyActions = calculateBuyActions(concentrationProblem, portfolio.getTotalAmountPortfolio().getValue());
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
        List<SellAction> actions = new ArrayList<>();
        List<ActualDistribution> actualDistributions = concentrationProblem.getConcentrationInstrumentProblems()
                .stream().filter(filter -> filter.getInstrumentStructure().equals(TARGET_STOCK_SATELLITE))
                .toList();
        // Работаем со списком проблемных инструментов, который вы уже подготовили

        for (InstrumentObj instToSell : actualDistributions.stream().findFirst().get().getInstruments().keySet()) {
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
    private List<BuyAction> calculateBuyActions(ConcentrationProblem concentrationProblem, BigDecimal availableCash) {
        return new ArrayList<>();
    }
   /* private List<BuyAction> calculateBuyActions(ConcentrationProblem concentrationProblem, BigDecimal availableCash) {
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
    }*/
}