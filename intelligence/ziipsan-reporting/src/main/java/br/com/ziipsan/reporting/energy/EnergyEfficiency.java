package br.com.ziipsan.reporting.energy;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Calculos de eficiencia energetica da ED-90 (OPE-08):
 * <ul>
 *   <li><b>consumo especifico</b> — kWh por m3 produzido ou bombeado,
 *       apuravel por sistema, ETA e elevatoria (a agregacao e por
 *       instalacao/unidade: quem chama soma as faturas da unidade e o
 *       volume dela; aqui a divisao e UNICA, sem media de medias);</li>
 *   <li><b>participacao na ponta</b> — consumo na ponta sobre o total;</li>
 *   <li><b>alerta de multa</b> — demanda medida acima da contratada
 *       alem da tolerancia {@code energy.demand_alert_pct} e fator de
 *       potencia abaixo de {@code energy.pf_min}. Sem o parametro do
 *       tenant, a dimensao NAO e avaliada (declarado no resultado):
 *       nenhum limiar de fabrica existe nesta entrega. O alerta e de
 *       risco de multa — nenhum valor em R$ e calculado, porque
 *       nenhuma tarifa de ultrapassagem consta das fontes.</li>
 * </ul>
 * A divisao usa {@code MathContext(20, HALF_UP)}, a mesma regra de
 * exatidao declarada do FormulaEvaluator da P3.41.
 */
public final class EnergyEfficiency {

    private static final MathContext DIVISION = new MathContext(20, RoundingMode.HALF_UP);
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private EnergyEfficiency() {
    }

    /** kWh por m3: recusa sem consumo ou sem volume positivo — nunca zero presumido. */
    public static BigDecimal specificConsumptionKwhPerM3(BigDecimal totalKwh, BigDecimal volumeM3) {
        if (totalKwh == null || totalKwh.signum() < 0) {
            throw new IndicatorValidationException("ENERGY_KWH_REQUIRED",
                    "consumo especifico exige o consumo (kWh) nao negativo da unidade/periodo");
        }
        if (volumeM3 == null || volumeM3.signum() <= 0) {
            throw new IndicatorValidationException("ENERGY_VOLUME_REQUIRED",
                    "consumo especifico exige o volume produzido/bombeado (m3) positivo: "
                            + "sem volume nao ha kWh/m3, e zero nao e presumido");
        }
        return totalKwh.divide(volumeM3, DIVISION);
    }

    /** Participacao do consumo na ponta no total (%), na mesma fatura. */
    public static BigDecimal peakSharePercent(EnergyBill bill) {
        if (bill.peakKwh() == null) {
            throw new IndicatorValidationException("ENERGY_PEAK_KWH_REQUIRED",
                    "fatura sem consumo na ponta lancado: participacao nao apurada "
                            + "(a fatura nao separa ponta — nao presumir zero)");
        }
        if (bill.totalKwh().signum() == 0) {
            throw new IndicatorValidationException("ENERGY_KWH_REQUIRED",
                    "fatura com consumo total zero: participacao na ponta indefinida");
        }
        return bill.peakKwh().multiply(HUNDRED).divide(bill.totalKwh(), DIVISION);
    }

    /**
     * Avaliacao de alerta de multa de uma fatura frente aos parametros
     * do tenant. {@code evaluated=false} numa dimensao significa
     * "parametro ou dado ausente — nao avaliado", nunca "sem risco".
     */
    public record DemandAssessment(boolean demandEvaluated, BigDecimal demandLimitKw,
                                   boolean demandAlert, boolean powerFactorEvaluated,
                                   boolean powerFactorAlert, List<String> reasons) {
        public DemandAssessment {
            reasons = reasons == null ? List.of() : List.copyOf(reasons);
        }

        public boolean anyAlert() {
            return demandAlert || powerFactorAlert;
        }
    }

    public static DemandAssessment assessDemand(EnergyBill bill, String parametersJson) {
        var alertPct = EnergyParameters.demandAlertPct(parametersJson);
        var pfMin = EnergyParameters.pfMin(parametersJson);
        List<String> reasons = new ArrayList<>();

        boolean demandEvaluated = false;
        BigDecimal demandLimit = null;
        boolean demandAlert = false;
        if (alertPct.isPresent() && bill.contractedDemandKw() != null && bill.measuredDemandKw() != null) {
            demandEvaluated = true;
            demandLimit = bill.contractedDemandKw()
                    .multiply(BigDecimal.ONE.add(alertPct.get().movePointLeft(2)));
            demandAlert = bill.measuredDemandKw().compareTo(demandLimit) > 0;
            if (demandAlert) {
                reasons.add("DEMAND_OVER_CONTRACT: demanda medida " + bill.measuredDemandKw()
                        + " kW acima do limite " + demandLimit + " kW (contratada "
                        + bill.contractedDemandKw() + " kW + tolerancia "
                        + EnergyParameters.DEMAND_ALERT_PCT + "=" + alertPct.get() + "%)");
            }
        }

        boolean pfEvaluated = false;
        boolean pfAlert = false;
        if (pfMin.isPresent() && bill.powerFactor() != null) {
            pfEvaluated = true;
            pfAlert = bill.powerFactor().compareTo(pfMin.get()) < 0;
            if (pfAlert) {
                reasons.add("POWER_FACTOR_BELOW_MIN: fator de potencia " + bill.powerFactor()
                        + " abaixo de " + EnergyParameters.PF_MIN + "=" + pfMin.get());
            }
        }
        return new DemandAssessment(demandEvaluated, demandLimit, demandAlert,
                pfEvaluated, pfAlert, reasons);
    }
}
