package org.invest.bot.invest.core.modules.balanse.actions;

import java.math.BigDecimal;

public record SellAction(String ticker, String name, BigDecimal amount, String reason) {}