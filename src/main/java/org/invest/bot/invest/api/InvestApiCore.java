package org.invest.bot.invest.api;

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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static ru.tinkoff.piapi.contract.v1.GetTechAnalysisRequest.TypeOfPrice.TYPE_OF_PRICE_CLOSE;

@Component
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

    public List<HistoricCandle> getHistoricCandles(String figi, int days, CandleInterval interval) {
        Instant now = Instant.now();
        Instant startDate = now.minus(days, ChronoUnit.DAYS);
        return api.getMarketDataService().getCandlesSync(figi, startDate, now, interval);
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
            String figi,
            GetTechAnalysisRequest.IndicatorType type,
            GetTechAnalysisRequest.IndicatorInterval interval,
            int historyDays,
            int length) {
        Instant to = Instant.now();
        Instant from = to.minus(historyDays,ChronoUnit.DAYS);
        InstrumentObj instrument = getInstrumentByFigi(figi);
        try {
            return api.getMarketDataService().getTechAnalysis(type,
                    instrument.getInstrumentUid(),
                    from,
                    to,
                    interval,
                    TYPE_OF_PRICE_CLOSE,
                    length,
                    null,
                    null,
                    null,
                    null).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<InstrumentObj> getInstruments(Portfolio portfolio) {
        List<InstrumentObj> instrumentObjs = new ArrayList<>();
        for (Position position : portfolio.getPositions()) {
            try {
                instrumentObjs.add(new InstrumentObj(position,
                        api.getInstrumentsService().getInstrumentByFigiSync(position.getFigi())));
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

    /**
     * Получает базовую информацию об инструменте по его FIGI.
     * Этот метод НЕ заполняет данные, специфичные для портфеля (количество, цена покупки, профит).
     * Он нужен как справочник для получения имени и тикера по FIGI.
     *
     * @param figi FIGI-идентификатор инструмента.
     * @return Объект InstrumentObj с базовой информацией или null, если инструмент не найден.
     */
    public InstrumentObj getInstrumentByFigi(String figi) {
        if (figi == null || figi.isEmpty()) {
            return null;
        }
        try {
            Instrument instrumentByFigiSync = api.getInstrumentsService().getInstrumentByFigiSync(figi);
            return new InstrumentObj(
                    instrumentByFigiSync.getName(),instrumentByFigiSync.getUid(),null,null,
                    instrumentByFigiSync.getInstrumentType(),instrumentByFigiSync.getTicker(),
                    null,null,instrumentByFigiSync.getFigi());
        } catch (Exception e) {

            System.err.println("Не удалось получить информацию по FIGI: " + figi + ". Ошибка: " + e.getMessage());
            return null;
        }
    }
}