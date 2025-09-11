package org.invest.bot.invest.core.modules.balanse;

import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
@AllArgsConstructor
public enum PortfolioInstrumentStructure {
    TARGET_STOCK_SATELLITE(BigDecimal.valueOf(20)),
    TARGET_STOCK_CORE(BigDecimal.valueOf(20)),
    TARGET_BOND(BigDecimal.valueOf(45.0)),
    TARGET_PROTECTION(BigDecimal.valueOf(10.0)),
    TARGET_RESERVE(BigDecimal.valueOf(5.0)),
    SATELLITE_CONCENTRATION_LIMIT(BigDecimal.valueOf(6.0)),
    BOND_CONCENTRATION_LIMIT(BigDecimal.valueOf(10.0)),
    PROTECTION_CONCENTRATION_LIMIT(BigDecimal.valueOf(50.0)),
    RESERVE_CONCENTRATION_LIMIT(BigDecimal.valueOf(5.0)),
    ALLOCATION_TOLERANCE(BigDecimal.valueOf(2.0));

    public BigDecimal value;

    // --- МЕТОДЫ ДЛЯ ПОКУПКИ (Возвращают ОДИН тикер) ---
    public static Optional<String> getCorePurchaseTicker() { return Optional.of("TMOS@"); }
    public static Optional<String> getReservePurchaseTicker() { return Optional.of("TMON@"); }
    public static Optional<String> getProtectionPurchaseTicker() { return Optional.of("GLDRUB_TOM"); }


    // --- МЕТОДЫ ДЛЯ КЛАССИФИКАЦИИ (Возвращают ПОЛНЫЙ список) ---
    public static String getCoreStockTicket() { return "TMOS@"; }
    public static Set<String> getProtectionTickets() { return Set.of("GLDRUB_TOM", "USD000UTSTOM"); }
    public static Set<String> getReserveTickets() { return Set.of("TMON@", "RUB000UTSTOM"); }


    public static List<PortfolioInstrumentStructure> getInsValues(){
        return List.of(TARGET_STOCK_SATELLITE,TARGET_STOCK_CORE,TARGET_BOND,
                TARGET_PROTECTION,TARGET_RESERVE);
    }

    public static PortfolioInstrumentStructure getLimitByTarget(PortfolioInstrumentStructure portfolioInstrumentStructure){
        if (portfolioInstrumentStructure.equals(TARGET_STOCK_SATELLITE)){
            return SATELLITE_CONCENTRATION_LIMIT;
        }
        if (portfolioInstrumentStructure.equals(TARGET_STOCK_CORE)){
            return TARGET_STOCK_CORE;
        }
        if (portfolioInstrumentStructure.equals(TARGET_BOND)){
            return BOND_CONCENTRATION_LIMIT;
        }
        if (portfolioInstrumentStructure.equals(TARGET_RESERVE)){
            return RESERVE_CONCENTRATION_LIMIT;
        }
        if (portfolioInstrumentStructure.equals(TARGET_PROTECTION)){
            return PROTECTION_CONCENTRATION_LIMIT;
        }
        return null;
    }
}
