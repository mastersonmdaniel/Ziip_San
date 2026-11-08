package br.com.ziipsan.core.regulatory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Painel OPCIONAL de capacidade economico-financeira do Decreto
 * 11.598/2023 (GES-02): os quatro indicadores do aceite, com os
 * limiares DO PROPRIO ACEITE — e so eles:
 * <ul>
 *   <li>margem liquida sem depreciacao e amortizacao &gt; 0;</li>
 *   <li>endividamento &lt;= 1;</li>
 *   <li>retorno sobre o patrimonio liquido &gt; 0;</li>
 *   <li>suficiencia de caixa &gt; 1.</li>
 * </ul>
 *
 * <p><b>Calculado da exportacao do ERP do cliente:</b> a exportacao
 * ({@link ErpExport}) traz os agregados contabeis do exercicio como
 * exportados pelo ERP do tenant — nenhum agregado e presumido nem
 * buscado em outra fonte. A margem usa a composicao que o proprio
 * nome do indicador declara (lucro liquido + depreciacao +
 * amortizacao, sobre a receita liquida); endividamento e retorno
 * sao as razoes entre os agregados exportados nomeados em cada
 * componente; a suficiencia de caixa e a razao entre o fluxo de
 * caixa disponivel exportado e o servico da divida do periodo
 * exportado — o DE-PARA de contas do ERP para esses dois agregados
 * e declaracao contabil do tenant na exportacao [SEM FONTE local
 * para um de-para presumido] (pendencia P-ED96-02).
 *
 * <p>Denominador nulo ou nao positivo = indicador INDETERMINADO
 * (nunca aprovado nem reprovado por presuncao). O painel so e
 * OFERECIDO a tenant sob contrato — a trava esta no servico
 * ({@link RegulatoryContractService#financialPanel}); aqui fica a
 * aritmetica pura.
 */
public final class FinancialCapacity {

    private FinancialCapacity() {
    }

    public enum Verdict { MEETS, DOES_NOT_MEET, INDETERMINATE }

    /** Resultado de um indicador: valor (nulo quando indeterminado) + veredito contra o limiar do aceite. */
    public record IndicatorResult(String indicator, BigDecimal value, String threshold, Verdict verdict) {
    }

    /**
     * Agregados de um exercicio vindos da exportacao do ERP do
     * cliente. Todos obrigatorios como NUMEROS EXPORTADOS (podem
     * ser negativos onde a contabilidade admite, ex.: lucro); os
     * denominadores sao validados no calculo, nao na construcao.
     * {@code exportRef} identifica a exportacao (arquivo/lote) para
     * auditoria da origem.
     */
    public record ErpExport(int exercise, String exportRef,
                            BigDecimal netIncome, BigDecimal depreciation, BigDecimal amortization,
                            BigDecimal netRevenue, BigDecimal totalLiabilities, BigDecimal totalAssets,
                            BigDecimal equity, BigDecimal availableCashFlow, BigDecimal debtServiceDue) {
        public ErpExport {
            if (exercise < 1900 || exercise > 2200)
                throw new IllegalArgumentException("exercicio fora da faixa plausivel: " + exercise);
            if (exportRef == null || exportRef.isBlank())
                throw new IllegalArgumentException("exportacao do ERP sem referencia de origem");
            Objects.requireNonNull(netIncome, "exportacao sem lucro liquido");
            Objects.requireNonNull(depreciation, "exportacao sem depreciacao");
            Objects.requireNonNull(amortization, "exportacao sem amortizacao");
            Objects.requireNonNull(netRevenue, "exportacao sem receita liquida");
            Objects.requireNonNull(totalLiabilities, "exportacao sem passivo total");
            Objects.requireNonNull(totalAssets, "exportacao sem ativo total");
            Objects.requireNonNull(equity, "exportacao sem patrimonio liquido");
            Objects.requireNonNull(availableCashFlow, "exportacao sem fluxo de caixa disponivel");
            Objects.requireNonNull(debtServiceDue, "exportacao sem servico da divida do periodo");
            if (depreciation.signum() < 0 || amortization.signum() < 0)
                throw new IllegalArgumentException("depreciacao/amortizacao exportadas nao podem ser negativas");
        }
    }

    private static BigDecimal ratio(BigDecimal numerator, BigDecimal denominator) {
        if (denominator == null || denominator.signum() <= 0) return null;
        return numerator.divide(denominator, 6, RoundingMode.HALF_UP);
    }

    /** Margem liquida sem depreciacao e amortizacao = (lucro + deprec. + amort.) / receita liquida; limiar &gt; 0. */
    public static IndicatorResult netMarginWithoutDepAmort(ErpExport e) {
        BigDecimal adjusted = e.netIncome().add(e.depreciation()).add(e.amortization());
        BigDecimal value = ratio(adjusted, e.netRevenue());
        Verdict v = value == null ? Verdict.INDETERMINATE
                : value.signum() > 0 ? Verdict.MEETS : Verdict.DOES_NOT_MEET;
        return new IndicatorResult("NET_MARGIN_WITHOUT_DEP_AMORT", value, "> 0", v);
    }

    /** Endividamento = passivo total / ativo total; limiar &lt;= 1 (aceite). */
    public static IndicatorResult leverage(ErpExport e) {
        BigDecimal value = ratio(e.totalLiabilities(), e.totalAssets());
        Verdict v = value == null ? Verdict.INDETERMINATE
                : value.compareTo(BigDecimal.ONE) <= 0 ? Verdict.MEETS : Verdict.DOES_NOT_MEET;
        return new IndicatorResult("LEVERAGE", value, "<= 1", v);
    }

    /** Retorno sobre o patrimonio liquido = lucro liquido / PL; limiar &gt; 0. */
    public static IndicatorResult returnOnEquity(ErpExport e) {
        BigDecimal value = ratio(e.netIncome(), e.equity());
        Verdict v = value == null ? Verdict.INDETERMINATE
                : value.signum() > 0 ? Verdict.MEETS : Verdict.DOES_NOT_MEET;
        return new IndicatorResult("RETURN_ON_EQUITY", value, "> 0", v);
    }

    /** Suficiencia de caixa = fluxo disponivel / servico da divida; limiar &gt; 1 (aceite). */
    public static IndicatorResult cashSufficiency(ErpExport e) {
        BigDecimal value = ratio(e.availableCashFlow(), e.debtServiceDue());
        Verdict v = value == null ? Verdict.INDETERMINATE
                : value.compareTo(BigDecimal.ONE) > 0 ? Verdict.MEETS : Verdict.DOES_NOT_MEET;
        return new IndicatorResult("CASH_SUFFICIENCY", value, "> 1", v);
    }

    /** Painel completo de um exercicio: os quatro indicadores do Decreto 11.598/2023 citados no aceite. */
    public record Panel(int exercise, String exportRef, IndicatorResult margin, IndicatorResult leverage,
                        IndicatorResult returnOnEquity, IndicatorResult cashSufficiency) {
        /** Capacidade comprovada so com os quatro MEETS; qualquer outro caso nao e apresentado como comprovada. */
        public boolean allMeet() {
            return margin.verdict() == Verdict.MEETS && leverage.verdict() == Verdict.MEETS
                    && returnOnEquity.verdict() == Verdict.MEETS && cashSufficiency.verdict() == Verdict.MEETS;
        }

        public boolean hasIndeterminate() {
            return margin.verdict() == Verdict.INDETERMINATE || leverage.verdict() == Verdict.INDETERMINATE
                    || returnOnEquity.verdict() == Verdict.INDETERMINATE
                    || cashSufficiency.verdict() == Verdict.INDETERMINATE;
        }
    }

    public static Panel panel(ErpExport e) {
        Objects.requireNonNull(e, "painel sem exportacao do ERP");
        return new Panel(e.exercise(), e.exportRef(), netMarginWithoutDepAmort(e), leverage(e),
                returnOnEquity(e), cashSufficiency(e));
    }
}
