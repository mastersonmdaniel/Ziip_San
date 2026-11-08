package br.com.ziipsan.commercial.collection.dunning.ruler;

import br.com.ziipsan.commercial.collection.dunning.DunningPorts;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Execucao da regua de cobranca (CAD-100): dado o debito, os passos ja
 * executados e a promessa de pagamento, decide o proximo passo.
 *
 * <p>Promessa de pagamento: SUSPENDE os passos seguintes enquanto estiver
 * vigente (registrada e com vencimento nao alcancado, sem pagamento); se
 * QUEBRADA (vencimento passou sem pagamento), os passos REINICIAM do
 * primeiro — e a promessa registrada tambem e o reconhecimento da divida
 * que interrompe a prescricao (ver {@code Prescription}).
 *
 * <p>Passo de mensagem so e emitido com LINK PIX (aceite: link Pix na
 * mensagem), obtido da porta da P3.2; sem link, a emissao recusa.
 */
public final class DunningEngine {

    private DunningEngine() {
    }

    /** Promessa de pagamento registrada na regua. */
    public record PaymentPromise(LocalDate promisedAt, LocalDate promisedFor, boolean fulfilled) {
        public PaymentPromise {
            if (promisedAt == null || promisedFor == null || promisedFor.isBefore(promisedAt)) {
                throw new IllegalArgumentException("promessa exige data do registro e vencimento coerentes");
            }
        }

        /** Vigente: registrada, nao cumprida e vencimento ainda nao alcancado. */
        public boolean suspendsAt(LocalDate date) {
            return !fulfilled && !date.isAfter(promisedFor);
        }

        /** Quebrada: nao cumprida e o vencimento passou. */
        public boolean brokenAt(LocalDate date) {
            return !fulfilled && date.isAfter(promisedFor);
        }
    }

    /** Estado da cobranca de um debito para a decisao do proximo passo. */
    public record CaseState(
            UUID unitId, UUID billId, String debtType, String category, String protectedClass,
            LocalDate dueDate, List<String> executedStepCodes, PaymentPromise promise) {
        public CaseState {
            if (unitId == null || billId == null || dueDate == null) {
                throw new IllegalArgumentException("estado da cobranca exige unidade, conta e vencimento");
            }
            executedStepCodes = executedStepCodes == null ? List.of() : List.copyOf(executedStepCodes);
        }

        public long ageDaysAt(LocalDate date) {
            return ChronoUnit.DAYS.between(dueDate, date);
        }
    }

    public enum DecisionKind {
        /** Executar o passo indicado. */
        EXECUTE_STEP,
        /** Promessa vigente: passos suspensos ate o vencimento dela. */
        SUSPENDED_BY_PROMISE,
        /** Regua esgotada para o segmento/idade. */
        NO_STEP_DUE
    }

    public record Decision(DecisionKind kind, DunningRuler.Step step, boolean rulerRestartedByBrokenPromise) {
    }

    /** Mensagem emitida por um passo, SEMPRE com link Pix. */
    public record DunningMessage(String stepCode, String templateCode, String templateVersion,
                                 String pixLink, UUID unitId, UUID billId) {
        public DunningMessage {
            if (pixLink == null || pixLink.isBlank()) {
                throw new IllegalArgumentException("mensagem de cobranca exige link Pix (aceite da P4.1)");
            }
        }
    }

    /**
     * Proximo passo devido na data. Passos ja executados nao se repetem —
     * exceto apos promessa quebrada, quando a regua reinicia e o primeiro
     * passo volta a ser devido (o historico fica registrado; o chamador
     * grava a execucao nova como novo ciclo).
     */
    public static Decision nextStep(DunningRuler ruler, CaseState state, LocalDate today) {
        if (ruler == null || state == null || today == null) {
            throw new IllegalArgumentException("regua, estado e data sao obrigatorios");
        }
        if (state.promise() != null && state.promise().suspendsAt(today)) {
            return new Decision(DecisionKind.SUSPENDED_BY_PROMISE, null, false);
        }
        boolean restarted = state.promise() != null && state.promise().brokenAt(today);
        long age = state.ageDaysAt(today);
        List<DunningRuler.Step> applicable = ruler.stepsFor(state.debtType(), age,
                state.category(), state.protectedClass());
        for (DunningRuler.Step step : applicable) {
            if (age < step.dayOffset()) {
                continue;
            }
            if (!restarted && state.executedStepCodes().contains(step.code())) {
                continue;
            }
            return new Decision(DecisionKind.EXECUTE_STEP, step, restarted);
        }
        return new Decision(DecisionKind.NO_STEP_DUE, null, restarted);
    }

    /** Emite a mensagem de um passo (modelo da P2.30 + link Pix da P3.2). */
    public static DunningMessage emitMessage(DunningRuler.Step step, CaseState state, UUID tenantId,
                                             DunningPorts.TemplatePort templates,
                                             DunningPorts.PixLinkPort pixLinks) {
        if (!step.isMessage()) {
            throw new IllegalArgumentException("passo " + step.code() + " nao e passo de mensagem");
        }
        // D9: o link Pix e exigido ANTES de renderizar — sem link, o motor
        // de modelos nem e chamado (nenhum documento e gerado/persistido
        // para uma mensagem que nao pode ser emitida) e "null" nunca e
        // injetado como variavel do modelo.
        String pixLink = pixLinks.pixLinkFor(tenantId, state.unitId(), state.billId());
        if (pixLink == null || pixLink.isBlank()) {
            throw new IllegalArgumentException(
                    "mensagem de cobranca exige link Pix antes da renderizacao (aceite da P4.1)");
        }
        var rendered = templates.render(tenantId, step.templateCode(),
                java.util.Map.of("stepCode", step.code(), "pixLink", pixLink));
        return new DunningMessage(step.code(), rendered.templateCode(), rendered.templateVersion(),
                pixLink, state.unitId(), state.billId());
    }

    /** Conversao medida por passo: executados x convertidos (pagamento apos o passo). */
    public record StepConversion(String stepCode, int executed, int converted) {
        public StepConversion {
            if (executed < 0 || converted < 0 || converted > executed) {
                throw new IllegalArgumentException("conversao exige executados >= convertidos >= 0");
            }
        }

        /** Taxa de conversao do passo (0 quando nada foi executado). */
        public double rate() {
            return executed == 0 ? 0.0 : (double) converted / executed;
        }
    }

    /** Mede a conversao de um passo a partir dos eventos registrados. */
    public static StepConversion conversionOf(String stepCode, List<Boolean> convertedAfterStep) {
        int converted = (int) convertedAfterStep.stream().filter(Boolean::booleanValue).count();
        return new StepConversion(stepCode, convertedAfterStep.size(), converted);
    }

    /** Atalho: a promessa vigente, se houver, como Optional. */
    public static Optional<PaymentPromise> activePromise(CaseState state, LocalDate today) {
        return state.promise() != null && state.promise().suspendsAt(today)
                ? Optional.of(state.promise()) : Optional.empty();
    }
}
