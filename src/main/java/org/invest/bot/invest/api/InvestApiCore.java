package org.invest.bot.invest.api;

import lombok.extern.slf4j.Slf4j;
import org.invest.bot.invest.core.modules.instruments.IndicatorType;
import org.invest.bot.invest.core.modules.instruments.WhiteListOfShares;
import org.invest.bot.invest.core.objects.InstrumentObj;
import org.springframework.stereotype.Component;
import ru.tinkoff.piapi.contract.v1.*;
import ru.tinkoff.piapi.core.InvestApi;
import ru.tinkoff.piapi.core.models.Portfolio;
import ru.tinkoff.piapi.core.models.Position;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.TypeOfPrice.TYPE_OF_PRICE_CLOSE;

@Component
@Slf4j
public class InvestApiCore {
    private final InvestApi api;

    public InvestApiCore(String token) {
        this.api = InvestApi.createReadonly(token);
    }

    public List<Account> getAccounts() {
        return api.getUserService().getAccountsSync();
    }

    public Portfolio getPortfolio(String accountId) {
        return api.getOperationsService().getPortfolioSync(accountId);
    }

    public Account getAccountById(String accountId) {
        return this.getAccounts().stream()
                .filter(acc -> acc.getId().equals(accountId))
                .findFirst()
                .orElse(null);
    }

    public List<Dividend> getDividends(String instrumentFigi){
        Instant now = Instant.now();
        Instant yearAhead = now.plus(365, ChronoUnit.DAYS);
        List<Dividend> dividends = api.getInstrumentsService().getDividendsSync(instrumentFigi, now, yearAhead);
        if(dividends == null){
            return new ArrayList<>();
        }
        return dividends;
    }

    public GetTechAnalysisResponse getTechAnalysis(
            InstrumentObj instrument,
            IndicatorType indicatorType) {
        Instant to = Instant.now();
        Instant from = to.minus(indicatorType.getHistoryDays(),ChronoUnit.DAYS);
        try {
            return api.getMarketDataService().getTechAnalysis(indicatorType.getApiType(),
                    instrument.getInstrumentUid(),
                    from,
                    to,
                    indicatorType.getInterval(),
                    TYPE_OF_PRICE_CLOSE,
                    indicatorType.getLength(),
                    indicatorType.getDeviation(),
                    indicatorType.getSmoothingFastLength(),
                    indicatorType.getSmoothingSlowLength(),
                    indicatorType.getSmoothingSignal()).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<InstrumentObj> getInstruments(Portfolio portfolio) {
        List<InstrumentObj> instrumentObjs = new ArrayList<>();
        for (Position position : portfolio.getPositions()) {
            try {
                if(position.getInstrumentType().equals("share")){
                    instrumentObjs.add(new InstrumentObj(position,
                            api.getInstrumentsService().getInstrumentByFigiSync(position.getFigi()),
                            WhiteListOfShares.getCorrectLot(position.getFigi())));
                }else {
                    instrumentObjs.add(new InstrumentObj(position,
                            api.getInstrumentsService().getInstrumentByFigiSync(position.getFigi()),1));
                }

            } catch (Exception e) {
                System.err.println("Could not retrieve instrument details for FIGI: " + position.getFigi() +
                        ". Error: " + e.getMessage());
            }
        }
        instrumentObjs.sort(Comparator.comparing(InstrumentObj::getType).thenComparing(InstrumentObj::getName));
        return instrumentObjs;
    }

    public InstrumentObj getInstrument(Portfolio portfolio, String instrumentTicket){
        return getInstruments(portfolio).stream().filter(f -> f.getTicker().equals(instrumentTicket)).findFirst().orElse(null);
    }

    public Position getPortfolioPosition(String accountId,String figi){
        Portfolio portfolio = getPortfolio(accountId);
        List<Position> positions = portfolio.getPositions();
        return positions.stream()
                .filter(p -> p.getFigi().equalsIgnoreCase(figi)) // Ищем по тикеру, игнорируя регистр
                .findFirst()
                .orElse(null);
    }

    public List<Operation> getOperationsForLastMonth(String accountId) {
        Instant now = Instant.now();
        Instant monthAgo = now.minus(30, ChronoUnit.DAYS);
        return api.getOperationsService().getExecutedOperationsSync(accountId, monthAgo, now);
    }
}