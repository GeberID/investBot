package org.invest.bot.invest.core.modules.instruments;

import org.invest.bot.invest.api.InvestApiCore;
import org.invest.bot.invest.core.objects.InstrumentObj;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.Instrument;
import ru.tinkoff.piapi.core.models.Portfolio;

import java.util.concurrent.ExecutionException;


@Service
public class InstrumentAnalysisService {
    private final InvestApiCore apiCore;

    public InstrumentAnalysisService(InvestApiCore apiCore) {
        this.apiCore = apiCore;
    }

    public void getInstrument(String  accountId,String instrumentTicket){
        Portfolio portfolio = apiCore.getPortfolio(accountId);
        InstrumentObj instrumentObj = apiCore.getInstruments(portfolio)
                .stream().filter(f -> f.getTicker().equals(instrumentTicket)).findFirst().orElse(null);
        try {
            Instrument instrumentInfo = apiCore.getInstrumentInfo(instrumentObj);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
