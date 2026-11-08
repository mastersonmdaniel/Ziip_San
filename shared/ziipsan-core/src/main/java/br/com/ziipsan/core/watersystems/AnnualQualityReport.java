package br.com.ziipsan.core.watersystems;

import br.com.ziipsan.core.chemicals.ChemReports;
import br.com.ziipsan.core.quality.BillQualityBlockSource;
import br.com.ziipsan.core.quality.MonthlyClosing;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Relatorio anual de qualidade da agua A CADA LIGACAO — ED-79, com a
 * base legal citada no aceite: Decreto 5.440/2005, art. 3o, III
 * ({@link #LEGAL_BASIS}). E a UNICA citacao legal de conteudo desta
 * tarefa alem da Lei 13.460/2017 da Carta: nenhum outro texto,
 * parametro, limite ou frequencia e afirmado aqui.
 *
 * <p>Fontes do relatorio (todas REAIS, nenhuma duplicada):
 * <ul>
 *   <li>resultados da ED-50: agregacao dos fechamentos mensais
 *       ({@link MonthlyClosing.ParameterLine}) via
 *       {@link MonthlyQualitySummary#fromClosingLine};</li>
 *   <li>bloco de qualidade da P2.24: as linhas mensais no formato que
 *       a ED-50 entrega para a P2.24 ({@link BillQualityBlockSource.BillBlockLine},
 *       que o montador da P2.24 consome como {@code WaterQualityResult})
 *       via {@link MonthlyBlockLine#fromBillBlockLine} — o bloco em si
 *       continua sendo montado pela P2.24; aqui ele e TRANSPORTADO
 *       para dentro do relatorio anual, sem remontagem;</li>
 *   <li>limpeza e desinfeccao de reservatorios da ED-89 (OPE-07):
 *       {@link ChemReports.PublicCleaningRecord} via
 *       {@link CleaningLine#fromPublicRecord} — so execucoes com
 *       laudo entram (a ED-89 ja exige o laudo no registro).</li>
 * </ul>
 *
 * <p><b>Correcao D1 (revisao da ED-79, 08/11/2026 — padrao da
 * correcao M-1 da ED-50):</b> esta classe era um {@code record} de
 * construtor publico com {@code withStatus} publico — as sondas
 * A1/A2 do revisor fizeram um relatorio nascer/passar a ISSUED sem
 * P2.32 nem P3.5 e o gravaram pelo store. Correcao: classe final
 * SELADA, com construtor PRIVADO. Os unicos nascimentos sao
 * (1) {@link #born}, fabrica de pacote chamada pelo
 * {@link AnnualReportBuilder}, que computa DRAFT/READY dos meses
 * presentes e RECUSA ISSUED, e (2) {@link #markIssued()}, de pacote,
 * chamada SOMENTE pelo {@code AnnualReportIssuanceService} depois
 * da prova da P2.32 e da tentativa despachada da P3.5. A API de
 * leitura (acessores no estilo do record, {@code equals}/
 * {@code hashCode} por valor) e preservada. A reidratacao pela
 * integracao (JPA/JDBC) deve reconstruir somente relatorios ja
 * nascidos por essas vias, a partir da linha persistida — ver
 * pendencias P-ED79-03 (declaracao formal) e P-ED79-06.
 */
public final class AnnualQualityReport {

    /** Base legal do relatorio anual, literal do aceite (Decreto 5.440/2005, art. 3o, III). */
    public static final String LEGAL_BASIS = "Decreto 5.440/2005, art. 3\u00ba, III";

    public enum Status { DRAFT, READY, ISSUED }

    private final UUID id;
    private final UUID tenantId;
    private final UUID consumerUnitId;
    private final String supplySystemCode;
    private final int year;
    private final Status status;
    private final List<AnnualLine> lines;
    private final List<YearMonth> monthsPresent;
    private final List<YearMonth> monthsMissing;
    private final List<MonthlyBlockLine> monthlyBlocks;
    private final List<CleaningLine> cleanings;
    private final String legalBasis;

    private AnnualQualityReport(UUID id, UUID tenantId, UUID consumerUnitId, String supplySystemCode,
                                int year, Status status, List<AnnualLine> lines,
                                List<YearMonth> monthsPresent, List<YearMonth> monthsMissing,
                                List<MonthlyBlockLine> monthlyBlocks, List<CleaningLine> cleanings,
                                String legalBasis) {
        Objects.requireNonNull(id, "id do relatorio e obrigatorio");
        Objects.requireNonNull(tenantId, "tenant e obrigatorio");
        Objects.requireNonNull(consumerUnitId, "ligacao do relatorio e obrigatoria (relatorio e a cada ligacao)");
        if (supplySystemCode == null || supplySystemCode.isBlank())
            throw new IllegalArgumentException("sistema do relatorio e obrigatorio");
        if (year < 2000 || year > 2100) throw new IllegalArgumentException("ano do relatorio invalido: " + year);
        Objects.requireNonNull(status, "situacao do relatorio e obrigatoria");
        this.lines = List.copyOf(lines);
        this.monthsPresent = List.copyOf(monthsPresent);
        this.monthsMissing = List.copyOf(monthsMissing);
        this.monthlyBlocks = List.copyOf(monthlyBlocks);
        this.cleanings = List.copyOf(cleanings);
        if (!LEGAL_BASIS.equals(legalBasis))
            throw new IllegalArgumentException("base legal do relatorio anual e a do aceite: " + LEGAL_BASIS);
        if (status == Status.READY && !this.monthsMissing.isEmpty())
            throw new IllegalArgumentException("relatorio READY nao pode ter meses faltantes: " + monthsMissing);
        if (status == Status.ISSUED && !this.monthsMissing.isEmpty())
            throw new IllegalArgumentException("relatorio ISSUED nao pode ter meses faltantes: " + monthsMissing);
        this.id = id;
        this.tenantId = tenantId;
        this.consumerUnitId = consumerUnitId;
        this.supplySystemCode = supplySystemCode;
        this.year = year;
        this.status = status;
        this.legalBasis = legalBasis;
    }

    /**
     * Nascimento UNICO via montador (acesso de pacote): o builder
     * computa DRAFT/READY dos meses presentes; ISSUED nunca nasce
     * por esta via (correcao D1).
     */
    static AnnualQualityReport born(UUID id, UUID tenantId, UUID consumerUnitId, String supplySystemCode,
                                    int year, Status status, List<AnnualLine> lines,
                                    List<YearMonth> monthsPresent, List<YearMonth> monthsMissing,
                                    List<MonthlyBlockLine> monthlyBlocks, List<CleaningLine> cleanings,
                                    String legalBasis) {
        if (status == Status.ISSUED)
            throw new IllegalArgumentException("relatorio nao nasce ISSUED: a emissao e ato do "
                    + "AnnualReportIssuanceService, com a prova da P2.32 e a entrega da P3.5 (correcao D1)");
        return new AnnualQualityReport(id, tenantId, consumerUnitId, supplySystemCode, year, status,
                lines, monthsPresent, monthsMissing, monthlyBlocks, cleanings, legalBasis);
    }

    /**
     * Transicao READY -&gt; ISSUED (acesso de pacote): chamada SOMENTE
     * pelo {@code AnnualReportIssuanceService}, depois de obter a
     * prova da P2.32 e a tentativa despachada da P3.5. Nao ha
     * {@code withStatus} publico (sonda A2 da revisao).
     */
    AnnualQualityReport markIssued() {
        if (status != Status.READY)
            throw new IllegalStateException("so relatorio READY pode ser emitido (estado atual: " + status + ")");
        return new AnnualQualityReport(id, tenantId, consumerUnitId, supplySystemCode, year, Status.ISSUED,
                lines, monthsPresent, monthsMissing, monthlyBlocks, cleanings, legalBasis);
    }

    public UUID id() { return id; }
    public UUID tenantId() { return tenantId; }
    public UUID consumerUnitId() { return consumerUnitId; }
    public String supplySystemCode() { return supplySystemCode; }
    public int year() { return year; }
    public Status status() { return status; }
    public List<AnnualLine> lines() { return lines; }
    public List<YearMonth> monthsPresent() { return monthsPresent; }
    public List<YearMonth> monthsMissing() { return monthsMissing; }
    public List<MonthlyBlockLine> monthlyBlocks() { return monthlyBlocks; }
    public List<CleaningLine> cleanings() { return cleanings; }
    public String legalBasis() { return legalBasis; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AnnualQualityReport other)) return false;
        return year == other.year
                && Objects.equals(id, other.id) && Objects.equals(tenantId, other.tenantId)
                && Objects.equals(consumerUnitId, other.consumerUnitId)
                && Objects.equals(supplySystemCode, other.supplySystemCode)
                && status == other.status
                && Objects.equals(lines, other.lines) && Objects.equals(monthsPresent, other.monthsPresent)
                && Objects.equals(monthsMissing, other.monthsMissing)
                && Objects.equals(monthlyBlocks, other.monthlyBlocks)
                && Objects.equals(cleanings, other.cleanings)
                && Objects.equals(legalBasis, other.legalBasis);
    }

    @Override public int hashCode() {
        return Objects.hash(id, tenantId, consumerUnitId, supplySystemCode, year, status,
                lines, monthsPresent, monthsMissing, monthlyBlocks, cleanings, legalBasis);
    }

    @Override public String toString() {
        return "AnnualQualityReport[id=" + id + ", tenantId=" + tenantId + ", consumerUnitId=" + consumerUnitId
                + ", supplySystemCode=" + supplySystemCode + ", year=" + year + ", status=" + status
                + ", lines=" + lines + ", monthsPresent=" + monthsPresent + ", monthsMissing=" + monthsMissing
                + ", monthlyBlocks=" + monthlyBlocks + ", cleanings=" + cleanings
                + ", legalBasis=" + legalBasis + "]";
    }

    /** Resumo mensal de um parametro, vindo do fechamento da ED-50. */
    public record MonthlyQualitySummary(
            YearMonth month, String parameterKey,
            int planned, int collected, int analyzed,
            int conforming, int nonConforming, int indeterminate) {
        public MonthlyQualitySummary {
            Objects.requireNonNull(month, "mes do resumo e obrigatorio");
            if (parameterKey == null || parameterKey.isBlank())
                throw new IllegalArgumentException("parametro do resumo e obrigatorio");
            if (planned < 0 || collected < 0 || analyzed < 0 || conforming < 0 || nonConforming < 0 || indeterminate < 0)
                throw new IllegalArgumentException("contagens do resumo nao podem ser negativas: " + parameterKey);
            if (conforming + nonConforming + indeterminate > analyzed)
                throw new IllegalArgumentException("vereditos excedem o analisado em " + parameterKey + "/" + month);
        }

        /** Conversao REAL da linha do fechamento mensal da ED-50. */
        public static MonthlyQualitySummary fromClosingLine(YearMonth month, MonthlyClosing.ParameterLine line) {
            return new MonthlyQualitySummary(month, line.parameterKey(), line.planned(), line.collected(),
                    line.analyzed(), line.conforming(), line.nonConforming(), line.indeterminate());
        }

        /**
         * Conversao REAL a partir do FECHAMENTO da ED-50 (correcao
         * D2, sonda A4 da revisao): exige o fechamento ENCERRADO
         * ({@code isClosed()}) — antes, {@code fromClosingLine}
         * recebia so a linha e o estado do fechamento era descartado,
         * tornando fechamento aberto e fechado indistinguiveis.
         * Devolve um resumo por linha de parametro do fechamento.
         */
        public static List<MonthlyQualitySummary> fromClosing(MonthlyClosing closing) {
            Objects.requireNonNull(closing, "fechamento da ED-50 e obrigatorio");
            if (!closing.isClosed())
                throw new IllegalArgumentException("fechamento de " + closing.month()
                        + " nao esta ENCERRADO (isClosed=false): resumo de fechamento aberto "
                        + "nao entra no relatorio anual (correcao D2/A4)");
            return closing.lines().stream()
                    .map(line -> fromClosingLine(closing.month(), line))
                    .toList();
        }
    }

    /** Linha anual agregada de um parametro (soma dos 12 meses presentes). */
    public record AnnualLine(
            String parameterKey, int monthsPresent,
            int planned, int collected, int analyzed,
            int conforming, int nonConforming, int indeterminate) {
        /** % de conformidade SO sobre vereditos determinados — mesma regra da ED-50; null sem veredito determinado. */
        public Double conformityPercent() {
            int determined = conforming + nonConforming;
            if (determined == 0) return null;
            return 100.0 * conforming / determined;
        }
    }

    /** Linha do bloco mensal (P2.24) transportada para o relatorio anual, sem remontagem. */
    public record MonthlyBlockLine(
            LocalDate referenceMonth, String parameterKey, String parameterName,
            String unit, String resultText, String referenceText) {
        public MonthlyBlockLine {
            Objects.requireNonNull(referenceMonth, "mes de referencia do bloco e obrigatorio");
            if (referenceMonth.getDayOfMonth() != 1)
                throw new IllegalArgumentException("referenceMonth deve ser o primeiro dia do mes: " + referenceMonth);
            if (parameterKey == null || parameterKey.isBlank())
                throw new IllegalArgumentException("parametro do bloco e obrigatorio");
            if (resultText == null || resultText.isBlank())
                throw new IllegalArgumentException("resultado do bloco e obrigatorio para " + parameterKey);
        }

        /** Conversao REAL da linha que a ED-50 entrega ao bloco da P2.24. */
        public static MonthlyBlockLine fromBillBlockLine(BillQualityBlockSource.BillBlockLine line) {
            return new MonthlyBlockLine(line.referenceMonth(), line.parameterKey(), line.parameterName(),
                    line.unit(), line.resultText(), line.referenceText());
        }
    }

    /**
     * Limpeza de reservatorio do ano (ED-89/OPE-07), como registrada
     * com laudo. <b>Correcao D2:</b> carrega o {@code supplySystem} do
     * registro publico da ED-89 — antes ele era DESCARTADO na
     * conversao e uma limpeza de OUTRO sistema entrava no relatorio
     * (sondas A6/A7 da revisao). O pertencimento ao sistema do
     * relatorio e conferido pelo {@link AnnualReportBuilder}.
     */
    public record CleaningLine(
            String reservoirCode, String reservoirName, String supplySystem, LocalDate executedOn,
            String reportRef, String productItemCode) {
        public CleaningLine {
            if (reservoirCode == null || reservoirCode.isBlank())
                throw new IllegalArgumentException("reservatorio da limpeza e obrigatorio");
            Objects.requireNonNull(executedOn, "data da limpeza e obrigatoria");
            if (reportRef == null || reportRef.isBlank())
                throw new IllegalArgumentException("limpeza sem laudo nao entra no relatorio anual (ED-89/OPE-07)");
        }

        /** Conversao REAL do registro publico da ED-89 (com o sistema de origem — correcao D2). */
        public static CleaningLine fromPublicRecord(ChemReports.PublicCleaningRecord record) {
            return new CleaningLine(record.reservoirCode(), record.reservoirName(), record.supplySystem(),
                    record.executedOn(), record.reportRef(), record.productItemCode());
        }
    }
}
