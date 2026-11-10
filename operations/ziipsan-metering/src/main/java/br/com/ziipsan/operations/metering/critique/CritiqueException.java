package br.com.ziipsan.operations.metering.critique;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.time.YearMonth;

/**
 * Uma excepcao na fila (II-2, E3): o resultado criticado de uma leitura,
 * com o seu ciclo de vida. Transicoes validas (as restantes sao recusa
 * tipificada, {@link InvalidTransitionException}):
 * <pre>
 *   PENDING             -&gt; ACCEPTED            (aceitar como esta)
 *   PENDING             -&gt; CORRECTED           (corrigir com leitura corrigida &gt;= 0)
 *   PENDING             -&gt; REREADING_REQUESTED (pedir releitura)
 *   REREADING_REQUESTED -&gt; CORRECTED           (a releitura chega como correccao)
 *   REREADING_REQUESTED -&gt; ACCEPTED            (aceitar apos releitura)
 * </pre>
 * ACCEPTED e CORRECTED sao terminais.
 */
public final class CritiqueException {

    /** Recusa tipificada de transicao invalida. */
    public static final class InvalidTransitionException extends IllegalStateException {
        public InvalidTransitionException(String message) {
            super(message);
        }
    }

    private final UUID unitId;
    private final YearMonth reference;
    private final List<RuleFinding> findings;
    private ExceptionStatus status = ExceptionStatus.PENDING;
    private Integer correctedReading;

    public CritiqueException(UUID unitId, YearMonth reference, List<RuleFinding> findings) {
        this.unitId = Objects.requireNonNull(unitId, "unitId");
        this.reference = Objects.requireNonNull(reference, "reference");
        this.findings = List.copyOf(findings);
        if (this.findings.isEmpty()) {
            throw new IllegalArgumentException("excepcao sem disparos nao entra na fila");
        }
    }

    public static CritiqueException from(CritiqueResult result) {
        return new CritiqueException(result.unitId(), result.reference(), result.findings());
    }

    public UUID unitId() { return unitId; }
    public YearMonth reference() { return reference; }
    public List<RuleFinding> findings() { return findings; }
    public ExceptionStatus status() { return status; }
    /** Leitura corrigida; so presente quando CORRECTED por correccao. */
    public Integer correctedReading() { return correctedReading; }

    /** Primeira regra que disparou (chave de ordenacao deterministica da fila). */
    public String firstRuleCode() { return findings.get(0).ruleCode(); }

    public void accept() {
        requireStatus("aceitar", ExceptionStatus.PENDING, ExceptionStatus.REREADING_REQUESTED);
        status = ExceptionStatus.ACCEPTED;
    }

    public void correct(int correctedReading) {
        if (correctedReading < 0) {
            throw new IllegalArgumentException("leitura corrigida negativa");
        }
        requireStatus("corrigir", ExceptionStatus.PENDING, ExceptionStatus.REREADING_REQUESTED);
        this.correctedReading = correctedReading;
        status = ExceptionStatus.CORRECTED;
    }

    public void requestRereading() {
        requireStatus("pedir releitura", ExceptionStatus.PENDING);
        status = ExceptionStatus.REREADING_REQUESTED;
    }

    private void requireStatus(String action, ExceptionStatus... allowed) {
        for (ExceptionStatus s : allowed) {
            if (status == s) {
                return;
            }
        }
        throw new InvalidTransitionException("transicao invalida: nao pode " + action
                + " uma excepcao em estado " + status + " (unidade " + unitId + ", referencia " + reference + ")");
    }
}
