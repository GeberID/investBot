package org.invest.bot.invest.core.modules.balanse.actions;

import org.invest.bot.invest.core.modules.balanse.BalanceModuleConf;

import java.math.BigDecimal;

public record BuyAction(BalanceModuleConf assetClass, BigDecimal amount, String reason) {}

