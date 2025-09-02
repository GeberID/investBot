package org.invest.bot.invest.core.modules.instruments;

import ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest;
import ru.tinkoff.piapi.contract.v1.Quotation;

public enum IndicatorType {
    SMA_200_DAY(
            GetTechAnalysisRequest.IndicatorType.INDICATOR_TYPE_SMA,
            GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_ONE_DAY,
            200,
            280,
            null,
            0,
            0,
            0
    ),
    RSI_14_WEEK(
            GetTechAnalysisRequest.IndicatorType.INDICATOR_TYPE_RSI,
            GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_WEEK,
            14,
            140,
            null,
            0
            ,0,
            0
    );
    /*MACD_WEEKLY(
            GetTechAnalysisRequest.IndicatorType.INDICATOR_TYPE_MACD,
            GetTechAnalysisRequest.IndicatorInterval.INDICATOR_INTERVAL_WEEK,
            0, // length не используется для MACD
            250 // Нужна достаточная история для расчета EMA (3-4 месяца)
    );*/

    private final GetTechAnalysisRequest.IndicatorType apiType;
    private final GetTechAnalysisRequest.IndicatorInterval interval;
    private final int length;
    private final int historyDays;
    private final Quotation deviation;
    private final int smoothingFastLength;
    private final int smoothingSlowLength;
    private final int smoothingSignal;

    IndicatorType(GetTechAnalysisRequest.IndicatorType apiType, GetTechAnalysisRequest.IndicatorInterval interval,
                  int length, int historyDays, Quotation deviation,int smoothingFastLength,int smoothingSlowLength,
                  int smoothingSignal) {
        this.apiType = apiType;
        this.interval = interval;
        this.length = length;
        this.historyDays = historyDays;
        this.deviation = deviation;
        this.smoothingFastLength = smoothingFastLength;
        this.smoothingSlowLength = smoothingSlowLength;
        this.smoothingSignal = smoothingSignal;
    }

    public GetTechAnalysisRequest.IndicatorType getApiType() {
        return apiType;
    }

    public GetTechAnalysisRequest.IndicatorInterval getInterval() {
        return interval;
    }

    public int getLength() {
        return length;
    }

    public int getHistoryDays() {
        return historyDays;
    }

    public Quotation getDeviation() {
        return deviation;
    }

    public int getSmoothingFastLength() {
        return smoothingFastLength;
    }

    public int getSmoothingSlowLength() {
        return smoothingSlowLength;
    }

    public int getSmoothingSignal() {
        return smoothingSignal;
    }
}