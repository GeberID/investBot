package org.invest.bot.core;

import ru.tinkoff.piapi.core.models.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DataConvertUtility {
    public static BigDecimal getPercentCount(Money total, Money instrument){
        return instrument.getValue()
                .multiply(new BigDecimal(100))
                .divide(total.getValue(), 2, RoundingMode.HALF_UP);
    }
    public static BigDecimal getPercentCount(Money total, BigDecimal instrument){
        return instrument
                .multiply(new BigDecimal(100))
                .divide(total.getValue(), 2, RoundingMode.HALF_UP);
    }
}
