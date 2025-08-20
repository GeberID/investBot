package org.invest.invest.core.modules.balanse;

import org.invest.invest.core.objects.InstrumentObj;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.models.Money;
import ru.tinkoff.piapi.core.models.Portfolio;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.invest.bot.core.DataConvertUtility.getPercentCount;
import static org.invest.invest.core.modules.balanse.BalanceModuleConf.*;

@Service
public class BalanceService {

    public BalanceService() {
    }

    public Map<BalanceModuleConf, BigDecimal> findTotalDeviation(Portfolio portfolio, List<InstrumentObj> instrumentObjs) {
        Map<BalanceModuleConf, BigDecimal> deviations = new HashMap<>();
        Money totalValue = portfolio.getTotalAmountPortfolio();
        if (totalValue.getValue().signum() == 0) {
            return deviations;
        }

        // --- ШАГ 1: ОПТИМИЗАЦИЯ ---
        // Рассчитаем все необходимые нам суммы ЗАРАНЕЕ, за один проход по списку.
        // Это позволяет избежать многократной фильтрации внутри switch-case.
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

        // --- ШАГ 2: ВАША ЛЮБИМАЯ КОНСТРУКЦИЯ ---
        // Теперь мы итерируемся по enum и используем уже готовые, рассчитанные значения.
        for (BalanceModuleConf target : BalanceModuleConf.values()) {
            BigDecimal currentValue = BigDecimal.ZERO; // Значение текущей категории

            switch (target) {
                case TARGET_BOND_PERCENTAGE:
                    currentValue = portfolio.getTotalAmountBonds().getValue();
                    break;
                case TARGET_STOCK_CORE_PERCENTAGE:
                    currentValue = coreStockValue; // Берем заранее рассчитанное значение
                    break;
                case TARGET_STOCK_SATELLITE__PERCENTAGE:
                    currentValue = portfolio.getTotalAmountShares().getValue();
                    break;
                case TARGET_RESERVE_PERCENTAGE:
                    currentValue = reserveValue; // Берем заранее рассчитанное значение
                    break;
                case TARGET_PROTECTION_PERCENTAGE:
                    currentValue = protectionValue; // Берем заранее рассчитанное значение
                    break;
                // Пропускаем константы, которые не являются целевыми долями
                case SATELLITE_CONCENTRATION_LIMIT:
                case ALLOCATION_TOLERANCE:
                    continue; // Переходим к следующему элементу enum
            }

            checkAndAddDeviation(deviations, target, currentValue, totalValue);
        }

        return deviations;
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