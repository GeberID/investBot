package org.invest.bot.invest.core.modules.balanse;

import org.invest.bot.invest.core.modules.balanse.actions.BuyAction;
import org.invest.bot.invest.core.modules.balanse.actions.SellAction;

import java.math.BigDecimal;
import java.util.List;

public record RebalancePlan(
        List<SellAction> sellActions,
        List<BuyAction> buyActions,
        BigDecimal totalCashFromSales
) {}