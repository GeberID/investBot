package org.invest.bot.invest.core.modules.instruments;

import org.invest.bot.invest.api.InvestApiCore;
import org.springframework.stereotype.Service;




@Service
public class InstrumentAnalysisService {
    private final InvestApiCore apiCore;

    public InstrumentAnalysisService(InvestApiCore apiCore) {
        this.apiCore = apiCore;
    }

    public void getInstrument(String  accountId,String instrumentTicket){

    }

}
