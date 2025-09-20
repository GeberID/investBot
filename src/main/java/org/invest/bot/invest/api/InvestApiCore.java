package org.invest.bot.invest.api;

import com.google.protobuf.Timestamp;
import lombok.extern.slf4j.Slf4j;
import org.invest.bot.invest.core.modules.instruments.IndicatorType;
import org.invest.bot.invest.core.modules.instruments.WhiteListOfShares;
import org.invest.bot.invest.core.objects.InstrumentObj;
import org.springframework.stereotype.Component;
import ru.tinkoff.piapi.contract.v1.*;
import ru.ttech.piapi.core.connector.ConnectorConfiguration;
import ru.ttech.piapi.core.connector.ServiceStubFactory;
import ru.ttech.piapi.core.connector.SyncStubWrapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;


@Component
@Slf4j
public class InvestApiCore {
    private final SyncStubWrapper<UsersServiceGrpc.UsersServiceBlockingStub> usersService;
    private final SyncStubWrapper<OperationsServiceGrpc.OperationsServiceBlockingStub> operationsService;
    private final SyncStubWrapper<InstrumentsServiceGrpc.InstrumentsServiceBlockingStub> instrumentsService;
    private final SyncStubWrapper<MarketDataServiceGrpc.MarketDataServiceBlockingStub> marketDataService;

    public InvestApiCore(String token) {
        ConnectorConfiguration configuration = ConnectorConfiguration.loadPropertiesFromResources("invest.properties");
        ServiceStubFactory factory = ServiceStubFactory.create(configuration);
        this.usersService = factory.newSyncService(UsersServiceGrpc::newBlockingStub);
        this.operationsService = factory.newSyncService(OperationsServiceGrpc::newBlockingStub);
        this.instrumentsService = factory.newSyncService(InstrumentsServiceGrpc::newBlockingStub);
        this.marketDataService = factory.newSyncService(MarketDataServiceGrpc::newBlockingStub);
    }

    public List<Account> getAccounts() {
        GetAccountsRequest request = GetAccountsRequest.getDefaultInstance();
        GetAccountsResponse response = usersService.callSyncMethod(stub -> stub.getAccounts(request));
        return response.getAccountsList();
    }

    public PortfolioResponse  getPortfolio(String accountId) {
        PortfolioRequest request = PortfolioRequest.newBuilder().setAccountId(accountId).build();
        return operationsService.callSyncMethod(stub -> stub.getPortfolio(request));
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
        GetDividendsRequest request = GetDividendsRequest.newBuilder()
                .setFigi(instrumentFigi)
                .setFrom(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build())
                .setTo(Timestamp.newBuilder().setSeconds(yearAhead.getEpochSecond()).build())
                .build();
        GetDividendsResponse response = instrumentsService.callSyncMethod
                (stub -> stub.getDividends(request));
        return response.getDividendsList();
    }

    public GetTechAnalysisResponse getTechAnalysis(
            InstrumentObj instrument,
            IndicatorType indicatorType) {
        /*Instant to = Instant.now();
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
        }*/
        log.error("Метод getTechAnalysis больше не поддерживается в API. Требуется рефакторинг с ручным расчетом индикаторов.");
        // Возвращаем пустой ответ, чтобы избежать NullPointerException
        return GetTechAnalysisResponse.getDefaultInstance();
    }

    public List<InstrumentObj> getInstruments(PortfolioResponse portfolio) {
        List<InstrumentObj> instrumentObjs = new ArrayList<>();
        // Работаем с новым типом PortfolioPosition
        for (PortfolioPosition position : portfolio.getPositionsList()) {
            try {
                Instrument instrument = getInstrumentByFigi(position.getFigi());
                if (instrument == null) continue;

                if (position.getInstrumentType().equals("share")) {
                    instrumentObjs.add(new InstrumentObj(position, instrument, WhiteListOfShares.getCorrectLot(position.getFigi())));
                } else {
                    instrumentObjs.add(new InstrumentObj(position, instrument, 1));
                }
            } catch (Exception e) {
                log.error("Не удалось обработать позицию {}: {}", position.getFigi(), e.getMessage());
            }
        }
        instrumentObjs.sort(Comparator.comparing(InstrumentObj::getType).thenComparing(InstrumentObj::getName));
        return instrumentObjs;
    }

    public PortfolioPosition getPortfolioPosition(String accountId, String figi) {
        PortfolioResponse portfolio = getPortfolio(accountId);
        return portfolio.getPositionsList().stream()
                .filter(p -> p.getFigi().equalsIgnoreCase(figi))
                .findFirst()
                .orElse(null);
    }

    public List<Operation> getOperationsForLastMonth(String accountId) {
        Instant now = Instant.now();
        Instant monthAgo = now.minus(30, ChronoUnit.DAYS);
        OperationsRequest request = OperationsRequest.newBuilder()
                .setAccountId(accountId)
                .setFrom(Timestamp.newBuilder().setSeconds(monthAgo.getEpochSecond()).build())
                .setTo(Timestamp.newBuilder().setSeconds(now.getEpochSecond()).build())
                .build();
        OperationsResponse response = operationsService.callSyncMethod(stub -> stub.getOperations(request));
        return response.getOperationsList();
    }

    /**
     * Получает полную информацию об инструменте по его FIGI.
     * Использует внутренний кэш SDK для эффективности.
     * @param figi FIGI инструмента
     * @return Объект Instrument или null, если не найден.
     */
    public Instrument getInstrumentByFigi(String figi) {
        InstrumentRequest request = InstrumentRequest.newBuilder().setIdType(InstrumentIdType.INSTRUMENT_ID_TYPE_FIGI).setId(figi).build();
        try {
            InstrumentResponse response = instrumentsService.callSyncMethod(stub -> stub.getInstrumentBy(request));
            return response.getInstrument();
        } catch (Exception e) {
            log.error("Не удалось найти инструмент по FIGI: {}", figi, e);
            return null;
        }
    }

    /**
     * Универсальный метод для поиска ЛЮБОГО инструмента по его тикеру.
     * Сначала находит инструмент через общий поиск, а затем получает
     * полную информацию по найденному FIGI.
     * @param ticker Тикер инструмента (напр. "SBER", "TMON@", "GLDRUB_TOM")
     * @return Объект Instrument или null, если не найден.
     */
    public Instrument getInstrumentByTicker(String ticker) {
        FindInstrumentRequest request = FindInstrumentRequest.newBuilder().setQuery(ticker).build();
        try {
            FindInstrumentResponse response = instrumentsService.callSyncMethod(stub -> stub.findInstrument(request));
            return response.getInstrumentsList().stream()
                    .findFirst()
                    .map(instrumentShort -> getInstrumentByFigi(instrumentShort.getFigi()))
                    .orElse(null);
        } catch (Exception e) {
            log.error("Ошибка при поиске инструмента по тикеру: {}", ticker, e);
            return null;
        }
    }

    /**
     * Получает последние цены для списка инструментов по их FIGI.
     * @param figis Список FIGI
     * @return Карта [FIGI -> Quotation], содержащая цены.
     */
    public Map<String, Quotation> getLastPrices(List<String> figis) {
        if (figis == null || figis.isEmpty()) return new HashMap<>();
        GetLastPricesRequest request = GetLastPricesRequest.newBuilder().addAllFigi(figis).build();
        try {
            GetLastPricesResponse response = marketDataService.callSyncMethod(stub -> stub.getLastPrices(request));
            return response.getLastPricesList().stream()
                    .collect(Collectors.toMap(LastPrice::getFigi, LastPrice::getPrice));
        } catch (Exception e) {
            log.error("Не удалось получить последние цены для списка FIGI.", e);
            return new HashMap<>();
        }
    }
}