package br.com.ziipsan.core.certification.water46001;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Revisao do uso da agua, linha de base e desempenho (ED-81; ciclo
 * da ISO 46001:2019 sobre o uso PROPRIO do prestador).
 *
 * <p>A revisao cobre UM periodo e UMA instalacao do escopo, e le o
 * uso proprio da {@link OwnUseSource} (balanco da ED-85, sem
 * recalculo). Os usos significativos sao os que o criterio de
 * significancia DECLARADO PELO TENANT nesta revisao aponta — nenhum
 * criterio de fabrica existe (sem fonte, nao se presume limiar).
 *
 * <p>LINHA DE BASE: o volume de referencia do periodo-base
 * DECLARADO pelo tenant a partir da medicao (hidrometria da ED-14
 * sobre as unidades de uso operacional) — {@code baselineM3OrNull}
 * ausente significa linha de base [A DEFINIR]: a revisao existe em
 * rascunho, mas nao fecha ({@link #close()}) nem produz variacao
 * sem ela. NENHUM valor de linha de base ou meta e inventado aqui;
 * meta, quando existir, e parametro/decisao do tenant fora deste
 * registro.
 *
 * <p>A variacao contra a linha de base e aritmetica sobre os dois
 * valores declarados (periodo corrente da fonte ED-85 x base
 * declarada) — nao recalcula balanco nem medicao.
 *
 * <p><b>AMARRA FONTE x PERIODO (correcao D4 da revisao):</b> o
 * construtor RECUSA fonte cujo ano/mes ({@code periodYear}/
 * {@code periodMonth} da {@link OwnUseSource}) nao caia dentro do
 * periodo [inicio, fim] desta revisao — uma revisao de marco nao
 * fecha mais sobre um balanco de dezembro. <b>Ligacao DMC x
 * instalacao:</b> NAO existe neste pacote — nem a ED-85 nem o
 * cadastro de instalacoes daqui amarram {@code dmcProfileRef} a
 * {@code installationRef}; a ausencia e a pendencia nomeada
 * <b>P-ED81-05</b>, com este modo de falha: ate a fiacao resolver o
 * mapa DMC→instalacao, uma fonte de DMC que nao atende a instalacao
 * do escopo pode ser anexada a revisao dela sem deteccao aqui.
 *
 * <p><b>UNICIDADE DE FECHAMENTO (correcao D3 da revisao):</b> so
 * pode existir UMA revisao CLOSED por (tenant, instalacao, inicio,
 * fim) — a trava executavel e o indice unico parcial
 * {@code uq_w46001_reviews_one_closed_per_scope} do SQL da ED-81;
 * esta classe nao mantem registro global de revisoes, por isso a
 * garantia e do banco (e da fiacao que persiste o fechamento).
 */
public final class WaterUseReview {

    public enum Status { DRAFT, CLOSED }

    private final Iso46001Activation activation;
    private final String installationRef;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final OwnUseSource source;
    private final String significanceCriterion;
    private final List<String> significantUses;
    private final String baselinePeriodLabel;
    private final Double baselineM3OrNull;
    private Status status = Status.DRAFT;

    public WaterUseReview(Iso46001Activation activation, String installationRef,
                          LocalDate periodStart, LocalDate periodEnd, OwnUseSource source,
                          String significanceCriterion, List<String> significantUses,
                          String baselinePeriodLabel, Double baselineM3OrNull) {
        Objects.requireNonNull(activation, "ativacao da ISO 46001 obrigatoria");
        activation.requireEnabled();
        activation.requireInstallationInScope(installationRef);
        this.activation = activation;
        this.installationRef = installationRef.trim();
        this.periodStart = Objects.requireNonNull(periodStart, "inicio do periodo da revisao obrigatorio");
        this.periodEnd = Objects.requireNonNull(periodEnd, "fim do periodo da revisao obrigatorio");
        if (periodEnd.isBefore(periodStart))
            throw new IllegalArgumentException("periodo da revisao invertido: " + periodStart + ".." + periodEnd);
        this.source = Objects.requireNonNull(source,
                "fonte do uso proprio (balanco da ED-85) obrigatoria: a revisao le o balanco, nao o recalcula");
        // Amarra D4: o ano/mes da fonte tem de cair DENTRO do periodo da revisao.
        LocalDate sourceMonth = LocalDate.of(source.periodYear(), source.periodMonth(), 1);
        if (sourceMonth.isBefore(periodStart.withDayOfMonth(1))
                || sourceMonth.isAfter(periodEnd.withDayOfMonth(1)))
            throw new IllegalArgumentException("fonte do balanco da ED-85 fora do periodo da "
                    + "revisao (correcao D4): fonte de " + source.periodYear() + "-"
                    + source.periodMonth() + " nao cai em " + periodStart + ".." + periodEnd);
        if (significanceCriterion == null || significanceCriterion.isBlank())
            throw new IllegalArgumentException("criterio de significancia declarado pelo tenant "
                    + "obrigatorio (nenhum criterio de fabrica e presumido)");
        this.significanceCriterion = significanceCriterion.trim();
        this.significantUses = significantUses == null ? List.of() : List.copyOf(significantUses);
        if (baselinePeriodLabel != null && baselinePeriodLabel.isBlank())
            throw new IllegalArgumentException("rotulo do periodo-base em branco");
        this.baselinePeriodLabel = baselinePeriodLabel;
        if (baselineM3OrNull != null && (!Double.isFinite(baselineM3OrNull) || baselineM3OrNull < 0))
            throw new IllegalArgumentException("linha de base invalida: " + baselineM3OrNull);
        if ((baselinePeriodLabel == null) != (baselineM3OrNull == null))
            throw new IllegalArgumentException("linha de base exige rotulo do periodo-base E valor "
                    + "declarado juntos (ou ambos ausentes = [A DEFINIR])");
        this.baselineM3OrNull = baselineM3OrNull;
    }

    public boolean hasBaseline() { return baselineM3OrNull != null; }

    /** Fecha a revisao: sem linha de base declarada, nao fecha ([A DEFINIR] nao vira numero). */
    public void close() {
        if (!hasBaseline()) throw new IllegalStateException(
                "revisao sem linha de base declarada nao fecha: linha de base [A DEFINIR] "
                        + "e parametro do tenant, nao valor presumido");
        this.status = Status.CLOSED;
    }

    /**
     * Variacao percentual do uso proprio do periodo contra a linha
     * de base declarada: (corrente - base) / base x 100. Sem base,
     * ausente (null) — nunca estimada. Base zero declarada torna a
     * variacao indefinida e tambem devolve null nomeado pelo chamador.
     */
    public Double variationVsBaselinePctOrNull() {
        if (!hasBaseline() || baselineM3OrNull == 0.0) return null;
        return (source.totalM3() - baselineM3OrNull) / baselineM3OrNull * 100.0;
    }

    public Iso46001Activation activation() { return activation; }
    public String installationRef() { return installationRef; }
    public LocalDate periodStart() { return periodStart; }
    public LocalDate periodEnd() { return periodEnd; }
    public OwnUseSource source() { return source; }
    public String significanceCriterion() { return significanceCriterion; }
    public List<String> significantUses() { return significantUses; }
    public String baselinePeriodLabel() { return baselinePeriodLabel; }
    public Double baselineM3OrNull() { return baselineM3OrNull; }
    public Status status() { return status; }
}
