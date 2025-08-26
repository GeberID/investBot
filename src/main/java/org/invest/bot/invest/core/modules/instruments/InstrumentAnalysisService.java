package org.invest.bot.invest.core.modules.instruments;

import org.invest.bot.core.messages.MessageFormatter;
import org.invest.bot.invest.api.InvestApiCore;
import org.invest.bot.invest.core.objects.InstrumentObj;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;


@Service
public class InstrumentAnalysisService {
    private final InvestApiCore apiCore;
    private final MessageFormatter messageFormatter;

    public InstrumentAnalysisService(InvestApiCore apiCore, MessageFormatter messageFormatter) {
        this.apiCore = apiCore;
        this.messageFormatter = messageFormatter;
    }


    public String analyzeInstrumentByTicker(String ticker) {
        String accountId = apiCore.getAccounts().get(0).getId();
        Portfolio portfolio = apiCore.getPortfolio(accountId);
        InstrumentObj instrumentObj = apiCore.getInstrument(portfolio, ticker);
        Position portfolioPosition = null;
        if (instrumentObj != null) {
            portfolioPosition = apiCore.getPortfolioPosition(accountId, instrumentObj.getFigi());
        }
        return messageFormatter.reportInstrument(ticker,portfolio, instrumentObj, portfolioPosition);
    }
}
