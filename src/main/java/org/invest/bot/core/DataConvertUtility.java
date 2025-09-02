package org.invest.bot.core;

import com.google.protobuf.Timestamp;
import ru.tinkoff.piapi.contract.v1.MoneyValue;
import ru.tinkoff.piapi.contract.v1.Quotation;
import ru.tinkoff.piapi.core.models.Money;

import java.math.BigDecimal;
import java.math.RoundingMode;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

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

    public static BigDecimal quotationToBigDecimal(MoneyValue quotation) {
        return BigDecimal.valueOf(quotation.getUnits())
                .add(BigDecimal.valueOf(quotation.getNano(), 9));
    }

    public static BigDecimal quotationToBigDecimal(Quotation quotation) {
        return BigDecimal.valueOf(quotation.getUnits())
                .add(BigDecimal.valueOf(quotation.getNano(), 9));
    }

    public static String convertTimeStampToStringWithoutYearSymbol(Timestamp dateTime) {
        Instant instant = Instant.ofEpochSecond(dateTime.getSeconds(), dateTime.getNanos());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        return formatter.format(Date.from(instant));
    }
}
