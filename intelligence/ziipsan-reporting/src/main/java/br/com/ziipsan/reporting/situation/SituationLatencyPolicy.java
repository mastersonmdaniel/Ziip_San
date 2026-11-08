package br.com.ziipsan.reporting.situation;

import br.com.ziipsan.reporting.indicator.IndicatorValidationException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * ED-93: a meta de latencia do console como RNF (ADR-0012).
 *
 * <p>O aceite fixa a meta em TEXTO — "segundos a poucos minutos" —
 * e NENHUMA fonte consultada (plano v3.0, JSON da tarefa, ADR-0012
 * citado por referencia) nomeia um numero de segundos/minutos para
 * este console. Logo esta classe NAO tem padrao de fabrica: o teto
 * numerico e um dado OBRIGATORIO de quem configura o console (tenant/
 * implantacao), e a sua ausencia e recusada nomeada com
 * {@code [SEM FONTE]} — nunca substituida por um numero presumido.
 * A frase do aceite fica gravada em {@link #ACCEPTANCE_PHRASE} como a
 * fonte da exigencia; o numero, quando existir, vem de fora.
 *
 * <p>Frescor e medido da fonte a leitura: {@code sourceAt} e o
 * instante do fato na origem (inicio da ocorrencia, leitura do
 * reservatorio...); a idade na leitura e {@code readAt - sourceAt}
 * sobre a linha da tabela de estado — que e o que a projecao prova
 * no nucleo. A latencia de rede/banco ate o navegador so se mede na
 * integracao (pendencia P-ED93-03).
 *
 * <p>CRITERIO DE CONJUNTO (declarado — correcao D2 da revisao
 * ED-93-REV-1): quando um conjunto tem varias linhas, o frescor do
 * conjunto e o da linha com o {@code sourceAt} MAIS ANTIGO (minimo —
 * o pior caso de idade que o operador ve). E o criterio UNICO do
 * console ({@code SituationConsoleService}) e da sonda de latencia
 * ({@code SituationLatencyProbe}); nao ha criterio otimista (maximo)
 * em nenhum dos dois.
 */
public record SituationLatencyPolicy(Duration maxEndToEnd) {

    /** Referencia do RNF no aceite. */
    public static final String RNF_REF = "ADR-0012";
    /** Frase literal da meta no acceptance da ED-93 — a unica fonte da meta. */
    public static final String ACCEPTANCE_PHRASE = "segundos a poucos minutos";

    public enum Freshness {
        FRESH,
        STALE,
        NO_DATA
    }

    public SituationLatencyPolicy {
        if (maxEndToEnd == null) {
            throw new IndicatorValidationException("SITUATION_LATENCY_BUDGET_REQUIRED",
                    "[SEM FONTE] teto numerico de latencia obrigatorio: o aceite fixa a meta "
                            + "como \"" + ACCEPTANCE_PHRASE + "\" (" + RNF_REF + ") sem numero; "
                            + "nenhum teto e presumido por esta entrega");
        }
        if (maxEndToEnd.isZero() || maxEndToEnd.isNegative()) {
            throw new IndicatorValidationException("SITUATION_LATENCY_BUDGET_INVALID",
                    "teto de latencia deve ser positivo, recebido: " + maxEndToEnd);
        }
    }

    /** Idade do fato na leitura (nunca negativa: leitura antes da fonte e erro nomeado). */
    public Duration ageAt(Instant sourceAt, Instant readAt) {
        Objects.requireNonNull(sourceAt, "instante da fonte obrigatorio");
        Objects.requireNonNull(readAt, "instante da leitura obrigatorio");
        Duration age = Duration.between(sourceAt, readAt);
        if (age.isNegative()) {
            throw new IndicatorValidationException("SITUATION_SOURCE_IN_FUTURE",
                    "fonte em " + sourceAt + " posterior a leitura em " + readAt
                            + ": relogio divergente nao vira frescor negativo silencioso");
        }
        return age;
    }

    /** FRESH quando a idade cabe no teto configurado; STALE fora dele. Sem fato, NO_DATA. */
    public Freshness freshness(Instant sourceAtOrNull, Instant readAt) {
        if (sourceAtOrNull == null) {
            return Freshness.NO_DATA;
        }
        return ageAt(sourceAtOrNull, readAt).compareTo(maxEndToEnd) <= 0
                ? Freshness.FRESH : Freshness.STALE;
    }

    /** Verdadeiro quando a duracao medida cabe no teto configurado. */
    public boolean withinBudget(Duration measured) {
        Objects.requireNonNull(measured, "duracao medida obrigatoria");
        return !measured.isNegative() && measured.compareTo(maxEndToEnd) <= 0;
    }
}
