package org.invest.bot.invest.core.modules.balanse.actions;

import org.invest.bot.invest.core.modules.balanse.PortfolioInstrumentStructure;

import java.math.BigDecimal;

public record BuyAction(PortfolioInstrumentStructure assetClass, BigDecimal amount, String reason) {}

