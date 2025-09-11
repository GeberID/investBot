package org.invest.bot.invest.core.modules.balanse.actions;

import org.invest.bot.invest.core.modules.balanse.PortfolioInstrumentStructure;

import java.math.BigDecimal;

public record BuyAction(
        String ticker,
        String figi,
        String name,
        int lots,
        BigDecimal amount,
        String reason
) {}

