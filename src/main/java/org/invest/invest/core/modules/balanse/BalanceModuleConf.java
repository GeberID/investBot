package org.invest.invest.core.modules.balanse;

import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.Set;
@AllArgsConstructor
public enum BalanceModuleConf {
    TARGET_STOCK_SATELLITE__PERCENTAGE(BigDecimal.valueOf(20)),
    TARGET_STOCK_CORE_PERCENTAGE(BigDecimal.valueOf(20)),
    TARGET_BOND_PERCENTAGE(BigDecimal.valueOf(45.0)),
    TARGET_PROTECTION_PERCENTAGE(BigDecimal.valueOf(10.0)),
    TARGET_RESERVE_PERCENTAGE(BigDecimal.valueOf(5.0)),
    SATELLITE_CONCENTRATION_LIMIT(BigDecimal.valueOf(5.0)),
    ALLOCATION_TOLERANCE(BigDecimal.valueOf(2.0));

    public BigDecimal value;

    /** Акции, входящие в "Ядро" (индексные фонды) */
    public static String getCoreStockTicket() {
        return "TMOS@"; // FIGI для TMOS
    }

    /** Инструменты, входящие в "Защиту" */
    public static Set<String> getProtectionTickets() {
        return Set.of(
                "GLDRUB_TOM", // FIGI для TGLD (золото)
                "USD"  // FIGI для USD
        );
    }

    /** Инструменты, входящие в "Резерв" */
    public static Set<String> getReserveTickets() {
        return Set.of(
                "TMON@", // FIGI для TMON (фонд ден. рынка)
                "RUB"  // FIGI для RUB
        );
    }
}
