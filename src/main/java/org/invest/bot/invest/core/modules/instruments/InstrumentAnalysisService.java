package org.invest.bot.invest.core.modules.instruments;

import org.invest.bot.invest.api.InvestApiCore;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.core.models.Position;



@Service
public class InstrumentAnalysisService {
    private final InvestApiCore apiCore;

    public InstrumentAnalysisService(InvestApiCore apiCore) {
        this.apiCore = apiCore;
    }

    public void getInstrument(String  accountId,String instrumentFigi){

        Position position = apiCore.getPortfolio(accountId).getPositions().stream()
                .filter(filter -> filter.getFigi().equals(instrumentFigi)).findFirst().get();
    }

}
