package br.com.ziipsan.reporting.panels;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;
import br.com.ziipsan.reporting.indicator.Semaphore;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * ED-49: uma linha EXIBIDA de painel. Todos os campos de negocio sao
 * LEITURA do registro da P3.41 (JOR-19): {@code value}, {@code semaphore}
 * e {@code formulaVersion} vem da apuracao do registro
 * ({@code frozen == true} quando o valor e o congelado do fechamento);
 * {@code indicatorName} e {@code unit} vem da definicao do registro;
 * {@code targetValue}/{@code targetDirection} vem da meta vigente do
 * registro para o periodo/area ({@code null} declarado quando nao ha
 * meta — o semaforo correspondente e {@code NO_TARGET}, nunca verde
 * presumido). O painel nao calcula nenhum destes campos.
 *
 * <p><b>Semantica de igualdade do valor exibido (correcao D1a da
 * ED-49, pousada em 2026-11-08 as 21:34, registrada no adendo de
 * correcao do RELATORIO-ED-49 e detalhada no
 * RELATORIO-CORRECAO-ED-49.md).</b> O transporte do valor e VERBATIM:
 * o painel guarda o {@link BigDecimal} exatamente como a apuracao do
 * registro o devolveu, sem {@code setScale}, sem arredondamento e sem
 * normalizacao. Por isso a igualdade que esta entrega AFIRMA entre o
 * valor exibido e o valor do registro e a igualdade ESTRITA de
 * representacao — {@link BigDecimal#equals(Object)}, valor <i>e</i>
 * escala: {@code 1.0} e {@code 1.00} sao o mesmo numero, mas NAO sao a
 * mesma representacao, e um painel que trocasse um pelo outro nao
 * estaria exibindo o valor do registro tal qual. A igualdade apenas
 * numerica ({@code compareTo == 0}) continua existindo como nocao
 * separada e nomeada ({@link #matchesRegistryValueNumerically(BigDecimal)}):
 * ela serve a comparacoes numericas (e a que o semaforo e o benchmark
 * usam nas suas proprias casas), mas NAO autoriza afirmar que o
 * exibido e identico ao do registro. Quem afirma identidade usa
 * {@link #matchesRegistryValueExactly(BigDecimal)} — e o
 * {@link StandardPanelService} confere essa identidade a cada linha
 * montada, recusando nomeado se um dia o transporte divergir
 * ({@code PANEL_DISPLAYED_VALUE_DIVERGES_FROM_REGISTRY}).
 *
 * <p><b>Selagem da identidade (correcao D3 da revisao).</b> Antes
 * desta correcao esta classe era um {@code record} publico: qualquer
 * codigo montava uma linha com valor e semaforo inventados e os
 * metodos publicos de exportacao do servico a emitiam sem nenhuma
 * recusa (sonda do revisor: valor {@code 999} e semaforo
 * {@code GREEN} fabricados sairam no JSON/CSV) — a garantia de
 * identidade so vivia dentro da montagem. Agora, no padrao das
 * selagens da base (precedente ED-94, {@code ReviewedAlert}): a
 * classe e {@code final}, o construtor e package-private e a UNICA
 * fabrica que produz linha SELADA e
 * {@link #fromRegistryComputation}, chamada somente pelo
 * {@code StandardPanelService}, que confere a apuracao antes de
 * devolver a linha (linha reprovada na conferencia nunca escapa); a
 * exportacao ({@code exportJson}/{@code exportCsv}) recusa nomeada
 * ({@code PANEL_ROW_NOT_FROM_REGISTRY}) qualquer linha nao selada.
 * Defesa em profundidade, como no precedente: codigo do MESMO pacote
 * ainda alcanca o construtor (e o que os testes usam para provar a
 * semantica de igualdade e a recusa da exportacao), mas nenhuma
 * linha assim construida e exportavel, e fora do pacote nao ha como
 * construir linha nenhuma.
 *
 * <p>Pendencias herdadas, registradas no RELATORIO-ED-49 (adendos de
 * correcao) e no RELATORIO-CORRECAO-ED-49.md: P-ED49-02 (a igualdade
 * interna do registro da P3.41 e numerica, cega a escala — a correcao
 * dessa semantica pertence a dona do registro) e P-ED49-03 (a ED-48
 * recusa com excecao quando falta dado de benchmark em vez de
 * propagar ausencia honesta). Esta camada NAO importa essa cegueira
 * para a sua afirmacao de identidade: aqui, exibido x registro e
 * estrito.
 */
public final class PanelRowView {

    private final PanelKind panel;
    private final String indicatorCode;
    private final String indicatorName;
    private final String unit;
    private final LocalDate periodStart;
    private final LocalDate periodEnd;
    private final String areaCode;
    private final BigDecimal value;
    private final BigDecimal targetValue;
    private final String targetDirection;
    private final Semaphore semaphore;
    private final boolean frozen;
    private final int formulaVersion;
    /** Selo de proveniencia (D3): {@code true} so via {@link #fromRegistryComputation}. */
    private final boolean registrySealed;

    PanelRowView(PanelKind panel, String indicatorCode, String indicatorName, String unit,
                 LocalDate periodStart, LocalDate periodEnd, String areaCode,
                 BigDecimal value, BigDecimal targetValue, String targetDirection,
                 Semaphore semaphore, boolean frozen, int formulaVersion) {
        this(panel, indicatorCode, indicatorName, unit, periodStart, periodEnd, areaCode,
                value, targetValue, targetDirection, semaphore, frozen, formulaVersion, false);
    }

    private PanelRowView(PanelKind panel, String indicatorCode, String indicatorName, String unit,
                         LocalDate periodStart, LocalDate periodEnd, String areaCode,
                         BigDecimal value, BigDecimal targetValue, String targetDirection,
                         Semaphore semaphore, boolean frozen, int formulaVersion,
                         boolean registrySealed) {
        if (value == null) {
            throw new IndicatorValidationException("PANEL_ROW_VALUE_REQUIRED",
                    "linha de painel do indicador " + indicatorCode
                            + " sem o valor apurado pelo registro (P3.41): exibido sem valor"
                            + " do registro nao existe — nem zero, nem nulo silencioso");
        }
        if (semaphore == null) {
            throw new IndicatorValidationException("PANEL_ROW_SEMAPHORE_REQUIRED",
                    "linha de painel do indicador " + indicatorCode
                            + " sem o semaforo apurado pelo registro (P3.41)");
        }
        this.panel = panel;
        this.indicatorCode = indicatorCode;
        this.indicatorName = indicatorName;
        this.unit = unit;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.areaCode = areaCode;
        this.value = value;
        this.targetValue = targetValue;
        this.targetDirection = targetDirection;
        this.semaphore = semaphore;
        this.frozen = frozen;
        this.formulaVersion = formulaVersion;
        this.registrySealed = registrySealed;
    }

    /**
     * UNICA fabrica de linha selada (D3): chamada somente pelo
     * {@code StandardPanelService.buildRow}, com os campos lidos da
     * apuracao/definicao/meta do registro; a conferencia estrita
     * exibido x registro roda no servico antes de a linha ser
     * devolvida. Package-private de proposito.
     */
    static PanelRowView fromRegistryComputation(PanelKind panel, String indicatorCode,
                                                String indicatorName, String unit,
                                                LocalDate periodStart, LocalDate periodEnd,
                                                String areaCode, BigDecimal value,
                                                BigDecimal targetValue, String targetDirection,
                                                Semaphore semaphore, boolean frozen,
                                                int formulaVersion) {
        return new PanelRowView(panel, indicatorCode, indicatorName, unit, periodStart, periodEnd,
                areaCode, value, targetValue, targetDirection, semaphore, frozen, formulaVersion,
                true);
    }

    public PanelKind panel() { return panel; }

    public String indicatorCode() { return indicatorCode; }

    public String indicatorName() { return indicatorName; }

    public String unit() { return unit; }

    public LocalDate periodStart() { return periodStart; }

    public LocalDate periodEnd() { return periodEnd; }

    public String areaCode() { return areaCode; }

    public BigDecimal value() { return value; }

    public BigDecimal targetValue() { return targetValue; }

    public String targetDirection() { return targetDirection; }

    public Semaphore semaphore() { return semaphore; }

    public boolean frozen() { return frozen; }

    public int formulaVersion() { return formulaVersion; }

    /**
     * A linha nasceu da apuracao do registro via servico (D3)?
     * Package-private: quem confere e a exportacao do servico.
     */
    boolean registrySealed() { return registrySealed; }

    /**
     * Identidade ESTRITA com o valor do registro: mesmo valor numerico E
     * mesma escala ({@link BigDecimal#equals(Object)}). E a unica
     * comparacao que autoriza dizer "o exibido e o valor do registro".
     * {@code 1.0} x {@code 1.00} = {@code false}; {@code 1.0} x
     * {@code 1.01} = {@code false}; {@code 1.0} x {@code 1.0} = {@code true}.
     */
    public boolean matchesRegistryValueExactly(BigDecimal registryValue) {
        return registryValue != null && value.equals(registryValue);
    }

    /**
     * Igualdade apenas NUMERICA com um valor ({@code compareTo == 0}),
     * insensivel a escala: {@code 1.0} x {@code 1.00} = {@code true},
     * {@code 1.0} x {@code 1.01} = {@code false}. Nomeada para nao ser
     * confundida com identidade de representacao — ver o javadoc da
     * classe (correcao D1a).
     */
    public boolean matchesRegistryValueNumerically(BigDecimal otherValue) {
        return otherValue != null && value.compareTo(otherValue) == 0;
    }

    /** Igualdade por VALOR dos campos de negocio (o selo e proveniencia, nao valor). */
    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof PanelRowView that)) {
            return false;
        }
        return frozen == that.frozen
                && formulaVersion == that.formulaVersion
                && panel == that.panel
                && Objects.equals(indicatorCode, that.indicatorCode)
                && Objects.equals(indicatorName, that.indicatorName)
                && Objects.equals(unit, that.unit)
                && Objects.equals(periodStart, that.periodStart)
                && Objects.equals(periodEnd, that.periodEnd)
                && Objects.equals(areaCode, that.areaCode)
                && Objects.equals(value, that.value)
                && Objects.equals(targetValue, that.targetValue)
                && Objects.equals(targetDirection, that.targetDirection)
                && semaphore == that.semaphore;
    }

    @Override
    public int hashCode() {
        return Objects.hash(panel, indicatorCode, indicatorName, unit, periodStart, periodEnd,
                areaCode, value, targetValue, targetDirection, semaphore, frozen, formulaVersion);
    }

    @Override
    public String toString() {
        return "PanelRowView[panel=" + panel + ", indicatorCode=" + indicatorCode
                + ", indicatorName=" + indicatorName + ", unit=" + unit
                + ", periodStart=" + periodStart + ", periodEnd=" + periodEnd
                + ", areaCode=" + areaCode + ", value=" + value
                + ", targetValue=" + targetValue + ", targetDirection=" + targetDirection
                + ", semaphore=" + semaphore + ", frozen=" + frozen
                + ", formulaVersion=" + formulaVersion + "]";
    }
}
