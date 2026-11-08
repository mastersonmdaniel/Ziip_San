package br.com.ziipsan.commercial.billing.altsolution;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Plano do serviço de esgotamento (limpa-fossa) de uma solução individual
 * (ED-88): o serviço pode ser PRESTADO ou a solução apenas CADASTRADA, por
 * {@code sewer.alt_solution_service_enabled}; prestado, a agenda é periódica
 * ({@code sewer.alt_solution_service_interval_months}) ou sob demanda.
 *
 * <p>Regra pura: esta classe decide o modo e a próxima data devida; gerar a
 * OS no motor da P1.31 e montar o roteiro são fiação da integração.
 */
public final class AltSolutionServicePlan {

    public enum Mode {
        /** Serviço desligado: solução só cadastrada — nenhuma OS é gerada. */
        REGISTER_ONLY,
        /** Serviço ligado, sem intervalo declarado: OS só quando solicitado. */
        ON_DEMAND,
        /** Serviço ligado com intervalo: agenda periódica a partir da última limpeza (ou do cadastro). */
        PERIODIC
    }

    public record Plan(Mode mode, LocalDate nextDueOn, boolean due) {}

    private AltSolutionServicePlan() {}

    /**
     * @param serviceEnabled valor de {@code sewer.alt_solution_service_enabled}
     * @param intervalMonths valor de {@code sewer.alt_solution_service_interval_months},
     *                       ou {@code null} quando a agenda é sob demanda (o chamador só
     *                       lê o parâmetro no modo periódico — ausência com serviço
     *                       periódico pretendido deve ser resolvida pelo chamador,
     *                       nunca assumida aqui)
     */
    public static Plan plan(AltSolution solution, boolean serviceEnabled, Integer intervalMonths,
                            LocalDate referenceDate) {
        Objects.requireNonNull(solution, "solucao individual e obrigatoria");
        Objects.requireNonNull(referenceDate, "data de referencia e obrigatoria");
        if (!solution.active())
            return new Plan(Mode.REGISTER_ONLY, null, false); // desativada pela ligacao: fora da agenda
        if (!serviceEnabled)
            return new Plan(Mode.REGISTER_ONLY, null, false);
        if (intervalMonths == null)
            return new Plan(Mode.ON_DEMAND, null, false);
        if (intervalMonths < 1)
            throw new IllegalArgumentException("intervalo do servico deve ser ao menos 1 mes: " + intervalMonths);
        LocalDate base = solution.lastCleaningOn() != null ? solution.lastCleaningOn() : solution.registeredOn();
        LocalDate next = base.plusMonths(intervalMonths);
        return new Plan(Mode.PERIODIC, next, !referenceDate.isBefore(next));
    }
}
