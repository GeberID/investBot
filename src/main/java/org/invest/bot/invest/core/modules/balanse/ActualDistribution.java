package org.invest.bot.invest.core.modules.balanse;

import org.invest.bot.invest.core.objects.InstrumentObj;
import ru.tinkoff.piapi.core.models.Money;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.invest.bot.core.DataConvertUtility.getPercentCount;
import static org.invest.bot.invest.core.modules.balanse.PortfolioInstrumentStructure.*;

public class ActualDistribution {
    private PortfolioInstrumentStructure instrumentStructure;
    private BigDecimal totalPresent;
    HashMap<InstrumentObj,BigDecimal> instruments;

    public ActualDistribution(PortfolioInstrumentStructure instrumentStructure, BigDecimal totalPresent, HashMap<InstrumentObj, BigDecimal> instruments) {
        this.instrumentStructure = instrumentStructure;
        this.totalPresent = totalPresent;
        this.instruments = instruments;
    }

    public PortfolioInstrumentStructure getInstrumentStructure() {
        return instrumentStructure;
    }

    public BigDecimal getTotalPresent() {
        return totalPresent;
    }

    public HashMap<InstrumentObj, BigDecimal> getInstruments() {
        return instruments;
    }

    public static List<ActualDistribution> getAllDistribution(Money totalValue, List<InstrumentObj> instrumentObjs){
        Map<PortfolioInstrumentStructure, BigDecimal> totalDistribution = calculateActualDistribution(totalValue,instrumentObjs);
        List<ActualDistribution> actualDistributionList = new ArrayList<>();

        for (Map.Entry<PortfolioInstrumentStructure, BigDecimal> entry : totalDistribution.entrySet()) {

            PortfolioInstrumentStructure target = entry.getKey();
            BigDecimal currentPercentage = entry.getValue();

            BigDecimal difference = currentPercentage.subtract(target.value).abs();
            if (difference.compareTo(ALLOCATION_TOLERANCE.value) > 0) {
                switch (PortfolioInstrumentStructure.valueOf(target.name())){
                    case TARGET_STOCK_SATELLITE -> {
                        HashMap<InstrumentObj,BigDecimal> instruments = new HashMap<>();
                        for (InstrumentObj instrumentObj : instrumentObjs.stream().filter(filter-> filter.getType().equals("share")).toList()) {
                            BigDecimal percentCountInstrument = getPercentCount(totalValue, instrumentObj.getQuantity().multiply(instrumentObj.getCurrentPrice().getValue()));
                            if(percentCountInstrument.compareTo(SATELLITE_CONCENTRATION_LIMIT.value) > 0){
                                instruments.put(instrumentObj,percentCountInstrument);
                            }
                        }
                        actualDistributionList.add(new ActualDistribution(target,currentPercentage,instruments));
                    }
                    case TARGET_BOND -> {
                        HashMap<InstrumentObj,BigDecimal> instruments = new HashMap<>();
                        for (InstrumentObj instrumentObj : instrumentObjs.stream().filter(filter-> filter.getType().equals("bond")).toList()) {
                            BigDecimal percentCountInstrument = getPercentCount(totalValue, instrumentObj.getQuantity().multiply(instrumentObj.getCurrentPrice().getValue()));
                            if(percentCountInstrument.compareTo(BOND_CONCENTRATION_LIMIT.value) > 0){
                                instruments.put(instrumentObj,percentCountInstrument);
                            }
                        }
                        actualDistributionList.add(new ActualDistribution(target,currentPercentage,instruments));
                    }
                    case TARGET_PROTECTION -> {
                        HashMap<InstrumentObj,BigDecimal> instruments = new HashMap<>();
                        for (InstrumentObj instrumentObj : instrumentObjs.stream().filter(filter-> getProtectionTickets().contains(filter.getTicker())).toList()) {
                            BigDecimal percentCountInstrument = getPercentCount(totalValue, instrumentObj.getQuantity().multiply(instrumentObj.getCurrentPrice().getValue()));
                            if(percentCountInstrument.compareTo(PROTECTION_CONCENTRATION_LIMIT.value) > 0){
                                instruments.put(instrumentObj,percentCountInstrument);
                            }
                        }
                        actualDistributionList.add(new ActualDistribution(target,currentPercentage,instruments));
                    }
                    case TARGET_RESERVE -> {
                        HashMap<InstrumentObj,BigDecimal> instruments = new HashMap<>();
                        for (InstrumentObj instrumentObj : instrumentObjs.stream().filter(filter-> getReserveTickets().contains(filter.getTicker())).toList()) {
                            BigDecimal percentCountInstrument = getPercentCount(totalValue, instrumentObj.getQuantity().multiply(instrumentObj.getCurrentPrice().getValue()));
                            if(percentCountInstrument.compareTo(RESERVE_CONCENTRATION_LIMIT.value) > 0){
                                instruments.put(instrumentObj,percentCountInstrument);
                            }
                        }
                        actualDistributionList.add(new ActualDistribution(target,currentPercentage,instruments));
                    }
                }
            }
        }
        return actualDistributionList;
    }


    private static Map<PortfolioInstrumentStructure, BigDecimal> calculateActualDistribution(Money totalValue, List<InstrumentObj> instrumentObjs){
        Map<PortfolioInstrumentStructure, BigDecimal> distribution = new HashMap<>();
        // Инициализируем все группы нулями
        for (PortfolioInstrumentStructure group : PortfolioInstrumentStructure.getInsValues()) {
            distribution.put(group, BigDecimal.ZERO);
        }
        for (InstrumentObj inst : instrumentObjs) {
            BigDecimal positionValue = inst.getQuantity().multiply(inst.getCurrentPrice().getValue());
            // Определяем, к какой группе относится инструмент, и добавляем его стоимость
            if (getCoreStockTicket().equals(inst.getTicker())) {
                distribution.merge(PortfolioInstrumentStructure.TARGET_STOCK_CORE, positionValue, BigDecimal::add);
            } else if (getReserveTickets().contains(inst.getTicker())) {
                distribution.merge(PortfolioInstrumentStructure.TARGET_RESERVE, positionValue, BigDecimal::add);
            } else if (getProtectionTickets().contains(inst.getTicker())) {
                distribution.merge(PortfolioInstrumentStructure.TARGET_PROTECTION, positionValue, BigDecimal::add);
            } else if ("share".equals(inst.getType())) {
                distribution.merge(PortfolioInstrumentStructure.TARGET_STOCK_SATELLITE, positionValue, BigDecimal::add);
            } else if ("bond".equals(inst.getType())) {
                distribution.merge(PortfolioInstrumentStructure.TARGET_BOND, positionValue, BigDecimal::add);
            }
        }
        // 2. Превращаем абсолютные значения в проценты и кладем в карту
        distribution.replace(TARGET_STOCK_CORE, getPercentCount(totalValue, distribution.get(PortfolioInstrumentStructure.TARGET_STOCK_CORE)));
        distribution.replace(TARGET_STOCK_SATELLITE, getPercentCount(totalValue, distribution.get(PortfolioInstrumentStructure.TARGET_STOCK_SATELLITE)));
        distribution.replace(TARGET_BOND, getPercentCount(totalValue, distribution.get(PortfolioInstrumentStructure.TARGET_BOND)));
        distribution.replace(TARGET_PROTECTION, getPercentCount(totalValue, distribution.get(PortfolioInstrumentStructure.TARGET_PROTECTION)));
        distribution.replace(TARGET_RESERVE, getPercentCount(totalValue, distribution.get(PortfolioInstrumentStructure.TARGET_RESERVE)));
        return distribution;
    }
}