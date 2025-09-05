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

import static org.invest.bot.core.DataConvertUtility.getPercentCount;
import static org.invest.bot.core.DataConvertUtility.quotationToBigDecimal;
import static org.invest.bot.invest.core.modules.balanse.BalanceModuleConf.*;

@Slf4j
@Service
public class BalanceService {

    private final InvestApiCore apiCore;

    // --- ДОБАВЛЯЕМ КОНСТРУКТОР ДЛЯ SPRING ---
    public BalanceService(InvestApiCore apiCore) {
        this.apiCore = apiCore;
    }

    public AnalysisResult analyzePortfolio(Portfolio portfolio, List<InstrumentObj> instrumentObjs) {
        Money totalValue = portfolio.getTotalAmountPortfolio();
        if (totalValue.getValue().signum() == 0) {
            return new AnalysisResult(new HashMap<>(), new ConcentrationProblem(new ArrayList<>(), new ArrayList<>()));
        }

        // 1. Получаем фактическое процентное распределение
        Map<BalanceModuleConf, BigDecimal> actualDistribution = calculateActualDistribution(portfolio, instrumentObjs);

        // 2. Находим отклонения по классам
        Map<BalanceModuleConf, BigDecimal> classDeviations = findClassDeviations(actualDistribution);

        // 3. Находим отклонения по концентрации
        ConcentrationProblem concentrationProblems = findConcentrationProblems(totalValue, instrumentObjs);

        return new AnalysisResult(classDeviations, concentrationProblems);
    }

    /**
     * ПУБЛИЧНЫЙ МЕТОД-"ДИРИЖЕР"
     * Создает полный план ребалансировки на основе результатов анализа.
     */
    public RebalancePlan createPlan(Portfolio portfolio, List<InstrumentObj> instrumentObjs) {
        AnalysisResult analysisResult = analyzePortfolio(portfolio, instrumentObjs);
        BigDecimal totalPortfolioValue = portfolio.getTotalAmountPortfolio().getValue();

        List<SellAction> sellActions = calculateSellActions(analysisResult, totalPortfolioValue);
        BigDecimal totalCashFromSales = sellActions.stream().map(SellAction::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<BuyAction> buyActions = calculateBuyActions(analysisResult, totalCashFromSales, instrumentObjs);

        return new RebalancePlan(sellActions, buyActions, totalCashFromSales);
    }

    /**
     * ПЕРЕПИСАННЫЙ, БОЛЕЕ ЧИСТЫЙ МЕТОД
     * Рассчитывает стоимость КАЖДОЙ стратегической группы за один проход.
     */
    private Map<AssetGroup, BigDecimal> calculateActualGroupValues(List<InstrumentObj> instrumentObjs) {
        Map<AssetGroup, BigDecimal> values = new HashMap<>();
        for (AssetGroup group : AssetGroup.values()) {
            values.put(group, BigDecimal.ZERO);
        }

        for (InstrumentObj inst : instrumentObjs) {
            BigDecimal positionValue = inst.getQuantity().multiply(inst.getCurrentPrice().getValue());
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
        Map<AssetGroup, BigDecimal> actualValues = calculateActualGroupValues(instrumentObjs);

        distribution.put(TARGET_STOCK_CORE_PERCENTAGE, getPercentCount(totalValue, actualValues.get(AssetGroup.STOCKS_CORE)));
        distribution.put(TARGET_STOCK_SATELLITE__PERCENTAGE, getPercentCount(totalValue, actualValues.get(AssetGroup.STOCKS_SATELLITE)));
        distribution.put(TARGET_BOND_PERCENTAGE, getPercentCount(totalValue, actualValues.get(AssetGroup.BONDS)));
        distribution.put(TARGET_PROTECTION_PERCENTAGE, getPercentCount(totalValue, actualValues.get(AssetGroup.PROTECTION)));
        distribution.put(TARGET_RESERVE_PERCENTAGE, getPercentCount(totalValue, actualValues.get(AssetGroup.RESERVE)));

        return distribution;
    }

    private ConcentrationProblem findConcentrationProblems(Money totalValue, List<InstrumentObj> instrumentObjs) {
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
        return new ConcentrationProblem(concentrationHumanProblems,concentrationInstrumentProblems);
    }

    private Map<BalanceModuleConf, BigDecimal> findClassDeviations(Map<BalanceModuleConf, BigDecimal> actualDistribution) {
        Map<BalanceModuleConf, BigDecimal> deviations = new HashMap<>();
        for (Map.Entry<BalanceModuleConf, BigDecimal> entry : actualDistribution.entrySet()) {
            BalanceModuleConf target = entry.getKey();
            if (target.isClassTarget()) {
                BigDecimal currentPercentage = entry.getValue();
                BigDecimal difference = currentPercentage.subtract(target.value).abs();
                if (difference.compareTo(ALLOCATION_TOLERANCE.value) > 0) {
                    deviations.put(target, currentPercentage);
                }
            }
        }
        return deviations;
    }

    /**
     * ПРИВАТНЫЙ МЕТОД №1: Отвечает ТОЛЬКО за расчет действий по ПРОДАЖЕ.
     */
    private List<SellAction> calculateSellActions(AnalysisResult analysisResult, BigDecimal totalPortfolioValue) {
        List<SellAction> actions = new ArrayList<>();
        for (InstrumentObj instToSell : analysisResult.concentrationProblems.getConcentrationInstrumentProblems()) {
            BigDecimal pricePerOneShare = instToSell.getCurrentPrice().getValue();
            int lotSize = getRealLotSize(instToSell.getTicker());
            if (pricePerOneShare.signum() <= 0 || lotSize <= 0) continue;

            BigDecimal pricePerLot = pricePerOneShare.multiply(BigDecimal.valueOf(lotSize));
            BigDecimal currentPositionValue = pricePerOneShare.multiply(instToSell.getQuantity());
            BigDecimal currentPercent = getPercentCount(totalPortfolioValue, currentPositionValue);
            BigDecimal limitPercent = SATELLITE_CONCENTRATION_LIMIT.value;

            if (currentPercent.compareTo(limitPercent) > 0) {
                BigDecimal excessPercent = currentPercent.subtract(limitPercent);
                BigDecimal idealSellAmount = totalPortfolioValue.multiply(excessPercent).divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP);
                int lotsToSell = idealSellAmount.divide(pricePerLot, 0, RoundingMode.CEILING).intValue();
                int actualLotsOwned = instToSell.getQuantity().intValue() / lotSize;
                int finalLotsToSell = Math.min(lotsToSell, actualLotsOwned);
                if (finalLotsToSell == 0 && idealSellAmount.signum() > 0) finalLotsToSell = 1;

                if (finalLotsToSell > 0) {
                    BigDecimal realSellAmount = pricePerLot.multiply(BigDecimal.valueOf(finalLotsToSell));
                    actions.add(new SellAction(instToSell.getTicker(), instToSell.getName(), finalLotsToSell, realSellAmount, "Снижение риска концентрации"));
                }
            }
        }
        return actions;
    }

    private List<BuyAction> calculateBuyActions(AnalysisResult analysisResult,
                                                BigDecimal availableCash,
                                                List<InstrumentObj> allInstruments) {
        List<BuyAction> actions = new ArrayList<>();
        if (availableCash.signum() <= 0) return actions;

        // --- Шаг 1: Находим все категории с недобором (этот код у вас верный) ---
        Map<BalanceModuleConf, BigDecimal> deficits = findDeficits(analysisResult);
        BigDecimal totalDeficitPercentage = deficits.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalDeficitPercentage.signum() <= 0) return actions;

        // --- Шаг 2: Распределяем бюджет и конвертируем в лоты ---
        for (Map.Entry<BalanceModuleConf, BigDecimal> entry : deficits.entrySet()) {
            BalanceModuleConf target = entry.getKey();
            BigDecimal deficit = entry.getValue();

            BigDecimal categoryWeight = deficit.divide(totalDeficitPercentage, 4, RoundingMode.HALF_UP);
            BigDecimal idealBuyAmount = availableCash.multiply(categoryWeight);

            String tickerToBuy = getTickerForAssetClass(target);
            if (tickerToBuy.isEmpty()) continue;

            // --- УПРОЩЕННАЯ ЛОГИКА: Ищем информацию ТОЛЬКО в текущем портфеле ---
            Optional<InstrumentObj> instrumentOpt = allInstruments.stream()
                    .filter(inst -> inst.getTicker().equals(tickerToBuy))
                    .findFirst();

            // Если мы нашли инструмент в портфеле, у нас есть и цена, и лотность
            if (instrumentOpt.isPresent()) {
                InstrumentObj instrumentToBuy = instrumentOpt.get();
                BigDecimal pricePerOneShare = instrumentToBuy.getCurrentPrice().getValue();
                int lotSize = getRealLotSize(tickerToBuy); // Используем наш надежный справочник

                if (pricePerOneShare.signum() > 0 && lotSize > 0) {
                    BigDecimal pricePerLot = pricePerOneShare.multiply(BigDecimal.valueOf(lotSize));
                    int lotsToBuy = idealBuyAmount.divide(pricePerLot, 0, RoundingMode.FLOOR).intValue();

                    if (lotsToBuy > 0) {
                        BigDecimal realBuyAmount = pricePerLot.multiply(BigDecimal.valueOf(lotsToBuy));
                        actions.add(new BuyAction(target, lotsToBuy, realBuyAmount, "Восстановление баланса"));
                    }
                }
            } else {
                // Если инструмента нет в портфеле, мы ПОКА ЧТО ничего не делаем.
                // Это самое простое и безопасное решение для прототипа.
                log.warn("Актив для покупки '{}' не найден в текущем портфеле. Покупка пропущена.", tickerToBuy);
            }
        }
        return actions;
    }

    private Map<BalanceModuleConf, BigDecimal> findDeficits(AnalysisResult analysisResult) {
        Map<BalanceModuleConf, BigDecimal> deficits = new HashMap<>();
        for (Map.Entry<BalanceModuleConf, BigDecimal> entry : analysisResult.classDeviations.entrySet()) {
            BalanceModuleConf target = entry.getKey();
            BigDecimal currentPercent = entry.getValue();
            if (currentPercent.compareTo(target.value) < 0) {
                BigDecimal deficit = target.value.subtract(currentPercent);
                deficits.put(target, deficit);
            }
        }
        return deficits;
    }

    private int getRealLotSize(String ticker) {
        Map<String, Integer> specialLots = Map.of("SBER", 10, "SBERP", 10, "VTBR", 10000, "GAZP", 10);
        return specialLots.getOrDefault(ticker, 1);
    }

    private String getTickerForAssetClass(BalanceModuleConf target) {
        switch (target) {
            case TARGET_STOCK_CORE_PERCENTAGE: return BalanceModuleConf.getCoreStockTicket();
            case TARGET_RESERVE_PERCENTAGE: return "TMON@";
            case TARGET_PROTECTION_PERCENTAGE: return "GLDRUB_TOM";
            default: return "";
        }
    }

    // Внутренний enum для ключей карты, чтобы избежать "магических строк"
    private enum AssetGroup {
        STOCKS_CORE, STOCKS_SATELLITE, BONDS, PROTECTION, RESERVE;

        public static AssetGroup fromConfig(BalanceModuleConf conf) {
            switch (conf) {
                case TARGET_STOCK_CORE_PERCENTAGE: return STOCKS_CORE;
                case TARGET_STOCK_SATELLITE__PERCENTAGE: return STOCKS_SATELLITE;
                case TARGET_BOND_PERCENTAGE: return BONDS;
                case TARGET_PROTECTION_PERCENTAGE: return PROTECTION;
                case TARGET_RESERVE_PERCENTAGE: return RESERVE;
                default: throw new IllegalArgumentException("Неверный тип цели: " + conf);
            }
        }
    }
}