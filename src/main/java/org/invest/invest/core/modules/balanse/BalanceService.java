package org.invest.invest.core.modules.balanse;

import org.invest.invest.core.objects.InstrumentObj;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.models.Money;
import ru.tinkoff.piapi.core.models.Portfolio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.invest.bot.core.DataConvertUtility.getPercentCount;
import static org.invest.invest.core.modules.balanse.BalanceModuleConf.*;

@Service
public class BalanceService {

    public BalanceService() {
    }

    public AnalysisResult findTotalDeviation(Portfolio portfolio, List<InstrumentObj> instrumentObjs) {
        Map<BalanceModuleConf, BigDecimal> classDeviations = new HashMap<>();
        List<String> concentrationProblems = new ArrayList<>(); // Список для проблем концентрации
        Money totalValue = portfolio.getTotalAmountPortfolio();

        if (totalValue.getValue().signum() == 0) {
            return new AnalysisResult(classDeviations, concentrationProblems); // Возвращаем пустой результат
        }
        BigDecimal coreStockValue = BigDecimal.ZERO;
        BigDecimal reserveValue = BigDecimal.ZERO;
        BigDecimal protectionValue = BigDecimal.ZERO;

        for (InstrumentObj inst : instrumentObjs) {
            BigDecimal positionValue = inst.getQuantity().multiply(inst.getCurrentPrice().getValue());
            if (getCoreStockTicket().equals(inst.getTicker())) {
                coreStockValue = positionValue;
            } else if (getReserveTickets().contains(inst.getTicker())) { // Используем тикеры, как в вашем коде
                reserveValue = reserveValue.add(positionValue);
            } else if (getProtectionTickets().contains(inst.getTicker())) { // Используем тикеры, как в вашем коде
                protectionValue = protectionValue.add(positionValue);
            }
        }

        for (BalanceModuleConf target : BalanceModuleConf.values()) {
            BigDecimal currentValue = BigDecimal.ZERO; // Значение текущей категории

            switch (target) {
                case TARGET_BOND_PERCENTAGE:
                    currentValue = portfolio.getTotalAmountBonds().getValue();
                    break;
                case TARGET_STOCK_CORE_PERCENTAGE:
                    currentValue = coreStockValue;
                    break;
                case TARGET_STOCK_SATELLITE__PERCENTAGE:
                    currentValue = portfolio.getTotalAmountShares().getValue();
                    break;
                case TARGET_RESERVE_PERCENTAGE:
                    currentValue = reserveValue;
                    break;
                case TARGET_PROTECTION_PERCENTAGE:
                    currentValue = protectionValue;
                    break;
                case SATELLITE_CONCENTRATION_LIMIT, ALLOCATION_TOLERANCE: continue;
            }
            checkAndAddDeviation(classDeviations, target, currentValue, totalValue);
        }
        for (InstrumentObj inst : instrumentObjs) {
            // Проверяем только акции, которые не являются "Ядром"
            if ("share".equals(inst.getType()) && !getCoreStockTicket().equals(inst.getTicker())) {
                BigDecimal positionValue = inst.getQuantity().multiply(inst.getCurrentPrice().getValue());
                BigDecimal percentage = getPercentCount(totalValue, positionValue);

                // ИСПОЛЬЗУЕМ ВАШ СУЩЕСТВУЮЩИЙ ЛИМИТ
                if (percentage.compareTo(SATELLITE_CONCENTRATION_LIMIT.value) > 0) {
                    // Формируем понятную строку и добавляем в наш список
                    String problem = String.format(
                            "‼️ <b>Риск концентрации:</b> доля '%s' (<b>%s%%</b>) превышает лимит в <b>%s%%</b>!",
                            inst.getName(), percentage.setScale(2, RoundingMode.HALF_UP), SATELLITE_CONCENTRATION_LIMIT.value
                    );
                    concentrationProblems.add(problem);
                }
            }
        }

        return new AnalysisResult(classDeviations, concentrationProblems);
    }

    /**
     * Вспомогательный метод, который ИСПРАВЛЯЕТ ошибку сравнения.
     * Он проверяет, выходит ли отклонение за рамки допуска, а не точное равенство.
     */
    private void checkAndAddDeviation(Map<BalanceModuleConf, BigDecimal> deviations,
                                      BalanceModuleConf target,
                                      BigDecimal currentValue,
                                      Money totalValue) {

        BigDecimal currentPercentage = getPercentCount(totalValue, currentValue);
        BigDecimal difference = currentPercentage.subtract(target.value).abs();

        // --- ГЛАВНОЕ ИСПРАВЛЕНИЕ: Сравниваем разницу с допуском ---
        if (difference.compareTo(ALLOCATION_TOLERANCE.value) > 0) {
            deviations.put(target, currentPercentage);
        }
    }
}