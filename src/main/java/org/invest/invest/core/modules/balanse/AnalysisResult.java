package org.invest.invest.core.modules.balanse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public class AnalysisResult {
    public final Map<BalanceModuleConf, BigDecimal> classDeviations;
    public final List<String> concentrationProblems;
    public AnalysisResult(Map<BalanceModuleConf, BigDecimal> classDeviations, List<String> concentrationProblems) {
        this.classDeviations = classDeviations;
        this.concentrationProblems = concentrationProblems;
    }
    public boolean hasDeviations() {
        return !classDeviations.isEmpty() || !concentrationProblems.isEmpty();
    }
}
