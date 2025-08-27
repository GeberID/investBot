package org.invest.bot.invest.core.modules.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.invest.bot.invest.api.InvestApiCore;
import org.invest.bot.invest.core.modules.balanse.BalanceModuleConf;
import org.invest.bot.invest.core.modules.balanse.BalanceService;
import org.invest.bot.invest.core.objects.InstrumentObj;
import org.springframework.stereotype.Service;
import ru.tinkoff.piapi.contract.v1.Account;
import ru.tinkoff.piapi.contract.v1.MoneyValue;
import ru.tinkoff.piapi.contract.v1.Operation;
import ru.tinkoff.piapi.core.models.Portfolio;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AiReportService {

    private InvestApiCore apiCore;
    private final BalanceService balanceService;
    private final ObjectMapper objectMapper;
    private final Map<String, InstrumentObj> instrumentCache = new HashMap<>();

    public AiReportService(InvestApiCore apiCore, BalanceService balanceService) {
        this.apiCore = apiCore;
        this.balanceService = balanceService;
        this.objectMapper = new ObjectMapper();
    }


    public File generateReportFile() throws IOException {
        ObjectNode rootNode = (ObjectNode) loadPromptTemplate();
        Account account = apiCore.getAccounts().get(0);
        Portfolio portfolio = apiCore.getPortfolio(account.getId());
        List<InstrumentObj> instruments = apiCore.getInstruments(portfolio);
        List<Operation> operations = apiCore.getOperationsForLastMonth(account.getId());
        ObjectNode portfolioDataNode = objectMapper.createObjectNode();
        portfolioDataNode.put("export_date", Instant.now().toString());
        addAccountInfo(portfolioDataNode, account);
        addPortfolioSummary(portfolioDataNode, portfolio);
        addStrategicAllocation(portfolioDataNode, portfolio, instruments);
        addInstrumentsDetails(portfolioDataNode, instruments);
        addTransactionLog(portfolioDataNode, operations, instruments);
        rootNode.set("portfolio_data", portfolioDataNode);
        File tempFile = File.createTempFile("llm_portfolio_report_", ".json");
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(tempFile, rootNode);
        return tempFile;
    }

    private void addTransactionLog(ObjectNode root, List<Operation> operations, List<InstrumentObj> currentInstruments) {
        ArrayNode logNode = root.putArray("transaction_log");
        for (Operation op : operations) {
            ObjectNode opNode = logNode.addObject();
            Optional<InstrumentObj> instrumentOpt = currentInstruments.stream()
                    .filter(f -> f.getFigi().equals(op.getFigi()))
                    .findFirst();
            InstrumentObj instrumentDetails = null;
            if (instrumentOpt.isPresent()) {
                instrumentDetails = instrumentOpt.get();
            } else {
                instrumentDetails = apiCore.getInstrumentByFigi(op.getFigi());
            }
            opNode.put("date", op.getDate().toString());
            opNode.put("type", op.getOperationType().name());
            opNode.put("figi", op.getFigi());
            if (instrumentDetails != null) {
                opNode.put("ticker", instrumentDetails.getTicker());
                opNode.put("name", instrumentDetails.getName());
            } else {
                opNode.put("ticker", "N/A");
                opNode.put("name", "Информация недоступна");
            }
            opNode.put("quantity", op.getQuantity());
            if (op.getPrice() != null && op.getQuantity() != 0) {
                opNode.put("price_per_unit", quotationToBigDecimal(op.getPrice()));
            }
            opNode.put("payment", quotationToBigDecimal(op.getPayment()));
            opNode.put("currency", op.getPayment().getCurrency());
        }
    }

    private JsonNode loadPromptTemplate() throws IOException {
        String resourcePath = "prompt_template.json";
        try (InputStream inputStream = this.getClass().getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                throw new IOException("Ресурсный файл не найден: " + resourcePath);
            }
            return objectMapper.readTree(inputStream);
        }
    }

    private BigDecimal quotationToBigDecimal(MoneyValue quotation) {
        if (quotation == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(quotation.getUnits())
                .add(BigDecimal.valueOf(quotation.getNano(), 2));
    }

    private void fillInstrumentCache(List<InstrumentObj> instruments) {
        for (InstrumentObj inst : instruments) {
            instrumentCache.put(inst.getFigi(), inst);
        }
    }

    private void addAccountInfo(ObjectNode root, Account account) {
        ObjectNode info = root.putObject("account_info");
        info.put("account_id", account.getId());
        info.put("account_name", account.getName());
    }

    private void addPortfolioSummary(ObjectNode root, Portfolio portfolio) {
        ObjectNode summary = root.putObject("portfolio_summary");
        ObjectNode totalValue = summary.putObject("total_value");
        totalValue.put("amount", portfolio.getTotalAmountPortfolio().getValue());
        totalValue.put("currency", portfolio.getTotalAmountPortfolio().getCurrency());
        summary.put("total_profit_percentage", portfolio.getExpectedYield());
    }

    private void addStrategicAllocation(ObjectNode root, Portfolio portfolio, List<InstrumentObj> instruments) {
        Map<BalanceModuleConf, BigDecimal> distribution = balanceService.calculateActualDistribution(portfolio, instruments);
        ObjectNode allocation = root.putObject("strategic_allocation_actual");
        for (Map.Entry<BalanceModuleConf, BigDecimal> entry : distribution.entrySet()) {
            String key = entry.getKey().name()
                    .toLowerCase()
                    .replace("target_", "")
                    .replace("__", "_");
            allocation.put(key, entry.getValue());
        }
    }

    private void addInstrumentsDetails(ObjectNode root, List<InstrumentObj> instruments) {
        ArrayNode instrumentsNode = root.putArray("instruments");
        for (InstrumentObj inst : instruments) {
            ObjectNode instNode = instrumentsNode.addObject();
            instNode.put("name", inst.getName());
            instNode.put("ticker", inst.getTicker());
            instNode.put("figi", inst.getFigi());
            instNode.put("type", inst.getType());
            instNode.put("quantity", inst.getQuantity());
            instNode.put("currency", inst.getCurrentPrice().getCurrency());
            instNode.put("current_price_per_unit", inst.getCurrentPrice().getValue());
            instNode.put("total_current_value", inst.getCurrentPrice().getValue().multiply(inst.getQuantity()));
            if (inst.getAverageBuyPrice() != null && inst.getTotalProfit() != null) {
                instNode.put("average_buy_price", inst.getAverageBuyPrice().getValue());
                instNode.put("total_profit_absolute", inst.getTotalProfit());
                BigDecimal totalInvested = inst.getAverageBuyPrice().getValue().multiply(inst.getQuantity());
                if (totalInvested.signum() != 0) {
                    BigDecimal profitPercentage = inst.getTotalProfit()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(totalInvested, 2, RoundingMode.HALF_UP);
                    instNode.put("total_profit_percentage", profitPercentage);
                } else {
                    instNode.put("total_profit_percentage", 0);
                }
            }
        }
    }
}