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
        log.info("Inside calculateSellActions method:");
        List<SellAction> actions = new ArrayList<>();
        List<ActualDistribution> actualDistributions = concentrationProblem.getConcentrationInstrumentProblems()
                .stream().filter(filter -> filter.getInstrumentStructure().equals(TARGET_STOCK_SATELLITE))
                .toList();
        log.info("actualDistributions:");
        for (ActualDistribution actualDistribution : actualDistributions) {
            log.info(actualDistribution.toString() + ":");
            log.info("getInstrumentStructure " + actualDistribution.getInstrumentStructure());
            log.info("getTotalPresent " + actualDistribution.getTotalPresent());
            for (InstrumentObj instrument : actualDistribution.getInstruments().keySet()) {
                log.info("instrument:");
                log.info("getInstrumentUid " + instrument.getInstrumentUid());
                log.info("getFigi " + instrument.getFigi());
                log.info("getName " + instrument.getName());
                log.info("getTicker " + instrument.getTicker());
                log.info("getType " + instrument.getType());
                log.info("getAverageBuyPrice " + instrument.getAverageBuyPrice().getValue());
                log.info("getCurrentPrice " + instrument.getCurrentPrice().getValue());
                log.info("getLot " + instrument.getLot());
                log.info("getQuantity " + instrument.getQuantity());
            }

        }
        // Работаем со списком проблемных инструментов, который вы уже подготовили
        log.info("instToSell:");
        for (InstrumentObj instToSell : actualDistributions.stream().findFirst().get().getInstruments().keySet()) {
            BigDecimal pricePerShare = instToSell.getCurrentPrice().getValue();
            int sharesPerLot = instToSell.getLot(); // Правильная лотность из WhiteListOfShares
            int totalSharesInPortfolio = instToSell.getQuantity().intValue(); // Всего ШТУК акций в портфеле
            log.info("pricePerShare " + pricePerShare);
            log.info("sharesPerLot" + sharesPerLot);
            log.info("instToSell " + totalSharesInPortfolio);
            if (pricePerShare.signum() == 0 || sharesPerLot == 0) {
                continue;
            }

            // --- ВЫЧИСЛЯЕМ ПРАВИЛЬНОЕ КОЛИЧЕСТВО ЛОТОВ В ПОРТФЕЛЕ ---
            int currentLotsInPortfolio = totalSharesInPortfolio / sharesPerLot;
            log.info("currentLotsInPortfolio " + currentLotsInPortfolio);

            // --- Шаг 1: Определяем "потолок" стоимости позиции в рублях ---
            BigDecimal targetValueInRub = totalPortfolioValue
                    .multiply(SATELLITE_CONCENTRATION_LIMIT.value)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            log.info("targetValueInRub " + targetValueInRub);
            // --- Шаг 2: Вычисляем реальную цену одного лота ---
            BigDecimal pricePerLot = pricePerShare.multiply(BigDecimal.valueOf(sharesPerLot));
            log.info("pricePerLot " + pricePerLot);
            // --- Шаг 3: Рассчитываем, сколько лотов мы можем себе позволить оставить ---
            int targetLots = targetValueInRub.divide(pricePerLot, 0, RoundingMode.FLOOR).intValue();
            log.info("targetLots " + targetLots);
            // --- Шаг 4: Главная формула: сколько лотов нужно продать ---
            // Теперь здесь будут правильные цифры: 1 - 0 = 1
            int lotsToSell = currentLotsInPortfolio - targetLots;
            log.info("lotsToSell " + lotsToSell);
            // --- Шаг 5: Если нужно что-то продать, создаем SellAction ---
            if (lotsToSell > 0) {
                BigDecimal sellAmount = pricePerLot.multiply(new BigDecimal(lotsToSell));
                log.info("sellAmount " + sellAmount);
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
        log.info("actions TOTAL:");
        for (SellAction action : actions) {
            log.info("figi " + action.figi());
            log.info("name " + action.name());
            log.info("amount " + action.amount());
            log.info("reason " + action.reason());
            log.info("lots " + action.lots());
        }

        return actions;
    }

    /**
     * ПРИВАТНЫЙ МЕТОД №2: Отвечает ТОЛЬКО за расчет действий по ПОКУПКЕ.
     */
    private List<BuyAction> calculateBuyActions(ConcentrationProblem concentrationProblem, BigDecimal availableCash) {
        log.info("inside calculateBuyActions method:");
        List<BuyAction> actions = new ArrayList<>();
        if (availableCash == null || availableCash.signum() <= 0) {
            return actions;
        }

        // --- Шаг 1: Находим дефицитные категории ---
        Map<PortfolioInstrumentStructure, BigDecimal> deficits = new HashMap<>();
        BigDecimal totalDeficitPercentage = BigDecimal.ZERO;
        log.info("distribution:");
        for (ActualDistribution distribution : concentrationProblem.getConcentrationInstrumentProblems()) {
            PortfolioInstrumentStructure target = distribution.getInstrumentStructure();
            BigDecimal currentPercent = distribution.getTotalPresent();
            log.info("target:" + target);
            log.info("currentPercent:" + currentPercent);
            if (currentPercent.compareTo(target.value) < 0) {
                BigDecimal deficit = target.value.subtract(currentPercent);
                deficits.put(target, deficit);
                totalDeficitPercentage = totalDeficitPercentage.add(deficit);
                log.info("deficit:" + deficit);
                log.info("totalDeficitPercentage:" + totalDeficitPercentage);
            }
        }

        if (totalDeficitPercentage.signum() == 0) {
            return actions;
        }

        // --- Шаг 2: Определяем, ЧТО покупать и запрашиваем информацию ---
        Map<PortfolioInstrumentStructure, String> purchaseTickerMap = new HashMap<>();
        log.info("purchaseTickerMap:");
        getCorePurchaseTicker().ifPresent(ticker -> purchaseTickerMap.put(TARGET_STOCK_CORE, ticker));
        getReservePurchaseTicker().ifPresent(ticker -> purchaseTickerMap.put(TARGET_RESERVE, ticker));
        getProtectionPurchaseTicker().ifPresent(ticker -> purchaseTickerMap.put(TARGET_PROTECTION, ticker));
        Map<String, Instrument> instrumentDetailsMap = new HashMap<>();
        for (String ticker : purchaseTickerMap.values()) {
            Instrument instrument = apiCore.getInstrumentByTicker(ticker); // Используем новый метод
            log.info("apiCore.getInstrumentByTicker(ticker) :" + instrument);
            if (instrument != null) {
                instrumentDetailsMap.put(ticker, instrument);
            }
        }

        List<String> figisToFetch = instrumentDetailsMap.values().stream().map(Instrument::getFigi).collect(Collectors.toList());
        Map<String, Quotation> lastPrices = apiCore.getLastPrices(figisToFetch);
        log.info("figisToFetch:" + figisToFetch);
        log.info("lastPrices:" + lastPrices);
        log.info("Map.Entry<PortfolioInstrumentStructure, BigDecimal> entry:");
        // --- Шаг 3: Распределяем деньги и конвертируем в лоты ---
        for (Map.Entry<PortfolioInstrumentStructure, BigDecimal> entry : deficits.entrySet()) {
            PortfolioInstrumentStructure category = entry.getKey();
            String tickerToBuy = purchaseTickerMap.get(category);

            if (tickerToBuy == null) continue;

            Instrument instrumentDetails = instrumentDetailsMap.get(tickerToBuy);
            if (instrumentDetails == null) continue;

            Quotation lastPriceQuotation = lastPrices.get(instrumentDetails.getFigi());
            if (lastPriceQuotation == null) continue;

            BigDecimal lastPrice = quotationToBigDecimal(lastPriceQuotation); // Нужен ваш хелпер
            int lotSize = instrumentDetails.getLot();

            BigDecimal deficit = entry.getValue();
            BigDecimal categoryWeight = deficit.divide(totalDeficitPercentage, 4, RoundingMode.HALF_UP);
            BigDecimal moneyForCategory = availableCash.multiply(categoryWeight).setScale(2, RoundingMode.DOWN);

            BigDecimal pricePerLot = lastPrice.multiply(BigDecimal.valueOf(lotSize));
            if (pricePerLot.signum() <= 0) continue;

            int lotsToBuy = moneyForCategory.divide(pricePerLot, 0, RoundingMode.FLOOR).intValue();
            log.info("PortfolioInstrumentStructure category:" + category);
            log.info("tickerToBuy:" + tickerToBuy);
            log.info("instrumentDetails:" + instrumentDetails);
            log.info("lastPriceQuotation:" + lastPriceQuotation);
            log.info("lastPrice:" + lastPrice);
            log.info("deficit:" + deficit);
            log.info("categoryWeight:" + categoryWeight);
            log.info("moneyForCategory:" + moneyForCategory);
            log.info("pricePerLot:" + pricePerLot);
            log.info("lotsToBuy:" + lotsToBuy);
            if (lotsToBuy > 0) {
                BigDecimal finalBuyAmount = pricePerLot.multiply(new BigDecimal(lotsToBuy));
                log.info("finalBuyAmount:" + finalBuyAmount);
                actions.add(new BuyAction(
                        instrumentDetails.getTicker(),
                        instrumentDetails.getFigi(),
                        instrumentDetails.getName(),
                        lotsToBuy,
                        finalBuyAmount,
                        "Восстановление баланса категории"
                ));
            }
        }
        log.info("actions TOTAL:");
        for (BuyAction action : actions) {
            log.info("figi " + action.figi());
            log.info("name " + action.name());
            log.info("amount " + action.amount());
            log.info("reason " + action.reason());
            log.info("lots " + action.lots());
        }
        return actions;
    }
}