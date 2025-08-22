package org.invest.bot.invest.core.modules.balanse;

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
import static org.invest.bot.invest.core.modules.balanse.BalanceModuleConf.*;

@Service
public class BalanceService {

    public AnalysisResult findTotalDeviation(Portfolio portfolio, List<InstrumentObj> instrumentObjs) {
        Map<BalanceModuleConf, BigDecimal> classDeviations = new HashMap<>();
        Money totalValue = portfolio.getTotalAmountPortfolio();
        if (totalValue.getValue().signum() == 0) {
            return new AnalysisResult(classDeviations, new ArrayList<>());
        }
        Map<String, BigDecimal> coreReserveProtectionValues = setBondsCoreReserveProtectionValues(instrumentObjs);
        for (BalanceModuleConf target : BalanceModuleConf.values()) {
            BigDecimal currentValue = BigDecimal.ZERO;
            switch (target) {
                case TARGET_BOND_PERCENTAGE:
                    currentValue = portfolio.getTotalAmountBonds().getValue();
                    break;
                case TARGET_STOCK_CORE_PERCENTAGE:
                    currentValue = coreReserveProtectionValues.get("coreStockValue");
                    break;
                case TARGET_STOCK_SATELLITE__PERCENTAGE:
                    currentValue = coreReserveProtectionValues.get("satelliteStockValue");
                    break;
                case TARGET_RESERVE_PERCENTAGE:
                    currentValue = coreReserveProtectionValues.get("reserveValue");
                    break;
                case TARGET_PROTECTION_PERCENTAGE:
                    currentValue = coreReserveProtectionValues.get("protectionValue");
                    break;
                case SATELLITE_CONCENTRATION_LIMIT, ALLOCATION_TOLERANCE:
                    continue;
            }
            checkAndAddDeviation(classDeviations, target, currentValue, totalValue);
        }
        return new AnalysisResult(classDeviations, concentrationProblems(totalValue, instrumentObjs));
    }

    public Map<BalanceModuleConf, BigDecimal> calculateActualDistribution(Portfolio portfolio, List<InstrumentObj> instrumentObjs) {
        Map<BalanceModuleConf, BigDecimal> distribution = new HashMap<>();
        Money totalValue = portfolio.getTotalAmountPortfolio();
        if (totalValue.getValue().signum() == 0) {
            return distribution;
        }
        Map<String, BigDecimal> coreReserveProtectionValues = setBondsCoreReserveProtectionValues(instrumentObjs);
        distribution.put(TARGET_STOCK_CORE_PERCENTAGE, getPercentCount(totalValue, coreReserveProtectionValues.get("coreStockValue")));
        distribution.put(TARGET_STOCK_SATELLITE__PERCENTAGE, getPercentCount(totalValue, coreReserveProtectionValues.get("satelliteStockValue")));
        distribution.put(TARGET_BOND_PERCENTAGE, getPercentCount(totalValue, portfolio.getTotalAmountBonds().getValue()));
        distribution.put(TARGET_PROTECTION_PERCENTAGE, getPercentCount(totalValue, coreReserveProtectionValues.get("protectionValue")));
        distribution.put(TARGET_RESERVE_PERCENTAGE, getPercentCount(totalValue, coreReserveProtectionValues.get("reserveValue")));
        return distribution;
    }

    private List<String> concentrationProblems(Money totalValue, List<InstrumentObj> instrumentObjs) {
        List<String> concentrationProblems = new ArrayList<>();
        for (InstrumentObj inst : instrumentObjs.stream()
                .filter(filter -> filter.getType().equals("share")).collect(Collectors.toSet())) {
            BigDecimal positionValue = inst.getQuantity().multiply(inst.getCurrentPrice().getValue());
            BigDecimal percentage = getPercentCount(totalValue, positionValue);
            if (percentage.compareTo(SATELLITE_CONCENTRATION_LIMIT.value) > 0) {
                String problem = String.format(
                        "‼️ <b>Риск концентрации:</b> доля '%s' (<b>%s%%</b>) превышает лимит в <b>%s%%</b>!",
                        inst.getName(), percentage.setScale(2, RoundingMode.HALF_UP), SATELLITE_CONCENTRATION_LIMIT.value
                );
                concentrationProblems.add(problem);
            }
        }
        return concentrationProblems;
    }

    private Map<String, BigDecimal> setBondsCoreReserveProtectionValues(List<InstrumentObj> instrumentObjs) {
        Map<String, BigDecimal> values = new HashMap<>();
        values.put("coreStockValue", BigDecimal.ZERO);
        values.put("reserveValue", BigDecimal.ZERO);
        values.put("protectionValue", BigDecimal.ZERO);
        values.put("satelliteStockValue", BigDecimal.ZERO);
        values.put("bondValue", BigDecimal.ZERO);
        for (InstrumentObj inst : instrumentObjs) {
            BigDecimal positionValue = inst.getQuantity().multiply(inst.getCurrentPrice().getValue());
            if (getCoreStockTicket().equals(inst.getTicker())) {
                values.replace("coreStockValue", values.get("coreStockValue").add(positionValue));
            } else if (getReserveTickets().contains(inst.getTicker())) {
                values.replace("reserveValue", values.get("reserveValue").add(positionValue));
            } else if (getProtectionTickets().contains(inst.getTicker())) {
                values.replace("protectionValue", values.get("protectionValue").add(positionValue));
            } else if ("share".equals(inst.getType())) {
                values.replace("satelliteStockValue", values.get("satelliteStockValue").add(positionValue));
            } else if ("bond".equals(inst.getType())) {
                values.replace("bondValue", values.get("bondValue").add(positionValue));
            }
        }
        return values;
    }

    private void checkAndAddDeviation(Map<BalanceModuleConf, BigDecimal> deviations,
                                      BalanceModuleConf target,
                                      BigDecimal currentValue,
                                      Money totalValue) {

        BigDecimal currentPercentage = getPercentCount(totalValue, currentValue);
        BigDecimal difference = currentPercentage.subtract(target.value).abs();
        if (difference.compareTo(ALLOCATION_TOLERANCE.value) > 0) {
            deviations.put(target, currentPercentage);
        }
    }
}