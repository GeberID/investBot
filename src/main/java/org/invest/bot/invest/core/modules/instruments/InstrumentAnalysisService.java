package org.invest.bot.invest.core.modules.instruments;

import org.invest.bot.core.messages.MessageFormatter;
import org.invest.bot.invest.api.InvestApiCore;
import org.invest.bot.invest.core.objects.InstrumentObj;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.Dividend;
import ru.tinkoff.piapi.contract.v1.GetTechAnalysisResponse;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;

import java.math.BigDecimal;
import java.util.List;

import static org.invest.bot.core.DataConvertUtility.quotationToBigDecimal;
import static org.invest.bot.invest.core.modules.instruments.IndicatorType.*;


@Service
public class InstrumentAnalysisService {
    private InvestApiCore apiCore;
    private final MessageFormatter messageFormatter;

    public InstrumentAnalysisService(InvestApiCore apiCore, MessageFormatter messageFormatter) {
        this.apiCore = apiCore;
        this.messageFormatter = messageFormatter;
    }

    public String analyzeInstrumentByTicker(String ticker) {
        BigDecimal sma200 = null;
        BigDecimal weeklyRsi = null;
        BigDecimal macdLine = null;
        BigDecimal signalLine = null;
        List<Dividend> dividends = null;
        String accountId = apiCore.getAccounts().get(0).getId();
        Portfolio portfolio = apiCore.getPortfolio(accountId);
        InstrumentObj instrumentObj = apiCore.getInstrument(portfolio, ticker);
        Position portfolioPosition = null;
        if (instrumentObj != null) {
            portfolioPosition = apiCore.getPortfolioPosition(accountId, instrumentObj.getFigi());
            sma200 = quotationToBigDecimal(apiCore.getTechAnalysis(instrumentObj.getFigi(),
                    SMA_200_DAY).getTechnicalIndicators(0).getSignal());
            weeklyRsi = quotationToBigDecimal(apiCore.getTechAnalysis(instrumentObj.getFigi(),
                    RSI_14_WEEK).getTechnicalIndicators(0).getSignal());
            GetTechAnalysisResponse.TechAnalysisItem macd = apiCore.getTechAnalysis(instrumentObj.getFigi(), MACD_WEEKLY).getTechnicalIndicators(0);
            macdLine = quotationToBigDecimal(macd.getMacd());
            signalLine = quotationToBigDecimal(macd.getSignal());
            // 3. Получаем дивиденды
            dividends = apiCore.getDividends(instrumentObj.getFigi());
        }
        return messageFormatter.reportInstrument(ticker,portfolio, instrumentObj, portfolioPosition,sma200, weeklyRsi,
                macdLine,signalLine,dividends);
    }
}
