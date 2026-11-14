package br.com.ziipsan.commercial.billing.relief;

import br.com.ziipsan.commercial.billing.tariff.TariffSchedule;
import br.com.ziipsan.commercial.billing.tariff.TariffVersion;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Camada viva de MONTAGEM do schedule de faturacao com desoneracao
 * (DESENHO-PA02 secoes 4.3.1 e 5.2.3; correccao R-03 da REVISAO-PA-02 —
 * antes, resolver e guarda existiam mas nenhuma camada viva os invocava).
 *
 * <p>Pipeline de cada montagem:
 * <ol>
 *   <li>{@link ReliefProgramSelector} escolhe o programa aplicavel
 *       (mais recente cujo {@code effective_from} cubra a emissao;
 *       ambiguidade = erro de cadastro fechado);</li>
 *   <li>{@link EmissionDateResolver} compoe o schedule desonerado
 *       fatia a fatia (R-01);</li>
 *   <li>{@link ReliefEmissionGuard} decide: {@code RELIEVED_OK},
 *       {@code PROVISIONAL_ALLOWED} (schedule vigente, MARCADO no
 *       resultado para o snapshot gravar a via provisoria) ou falha
 *       fechada.</li>
 * </ol>
 *
 * <p><b>Comportamento identico sem programa:</b> sem programas, ou sem
 * programa aplicavel a emissao, o schedule devolvido e a MESMA instancia
 * recebida ({@code isSameAs}) e a decisao e {@code NOT_APPLICABLE} — a
 * prova diferencial ADR-0048 e o caso-ancora P2.5 nao se movem.
 *
 * <p>Instancia sem estado: registada como bean em
 * {@code Pa02ReliefConfig} e invocada pela camada de montagem viva
 * ({@code BillCalculatorAdapter.toContext(...)} com contexto de
 * desoneracao).
 */
public final class ReliefBillingAssembly {

    /** Tudo o que a montagem precisa sobre desoneracao, pre-resolvido pelo chamador. */
    public record ReliefContext(List<ReliefProgram> programs, List<ReliefLink> links,
                                Map<UUID, TariffVersion> relievedById,
                                boolean homologationPublished, boolean formalNoticeRecorded) {
        public ReliefContext {
            programs = programs == null ? List.of() : List.copyOf(programs);
            links = links == null ? List.of() : List.copyOf(links);
            relievedById = relievedById == null ? Map.of() : Map.copyOf(relievedById);
        }

        /** Contexto vazio: nenhum programa — comportamento vigente puro. */
        public static ReliefContext none() {
            return new ReliefContext(List.of(), List.of(), Map.of(), false, false);
        }
    }

    /**
     * Resultado da montagem.
     *
     * @param schedule schedule efectivo a entregar ao motor/composicao
     * @param program  programa aplicado (null quando {@code NOT_APPLICABLE})
     */
    public record Assembled(TariffSchedule schedule, ReliefEmissionGuard.Decision decision,
                            ReliefProgram program, EmissionDateResolver.Resolution resolution) {
    }

    public Assembled assemble(TariffSchedule periodSchedule, LocalDate periodFrom, LocalDate periodTo,
                              LocalDate issuedOn, String municipalityIbge, ReliefContext context) {
        Objects.requireNonNull(periodSchedule, "schedule do periodo e obrigatorio");
        Objects.requireNonNull(context, "contexto de desoneracao e obrigatorio");
        ReliefProgram program = ReliefProgramSelector
                .selectApplicable(context.programs(), municipalityIbge, issuedOn)
                .orElse(null);
        if (program == null) {
            return new Assembled(periodSchedule, ReliefEmissionGuard.Decision.NOT_APPLICABLE, null,
                    new EmissionDateResolver.Resolution(periodSchedule, false, List.of(), List.of()));
        }
        EmissionDateResolver.Resolution resolution = EmissionDateResolver.resolveForEmission(
                periodSchedule, periodFrom, periodTo, issuedOn, program,
                context.links(), context.relievedById());
        if (resolution.relievedApplied()) {
            // Todas as fatias desoneradas: a guarda confirma contra as ligacoes.
            List<UUID> relievedIds = resolution.schedule().versions().stream()
                    .map(TariffVersion::id).distinct().toList();
            ReliefEmissionGuard.Decision decision = ReliefEmissionGuard.checkAll(program, issuedOn,
                    relievedIds, context.links(), context.homologationPublished(),
                    context.formalNoticeRecorded());
            return new Assembled(resolution.schedule(), decision, program, resolution);
        }
        if (!resolution.sourceVersionIds().isEmpty()) {
            // Conjunto nao desonerado (falta ligacao de alguma fatia): a
            // guarda decide sobre as versoes de ORIGEM resolvidas — falha
            // fechada ou via provisoria marcada; o schedule e o vigente.
            ReliefEmissionGuard.Decision decision = ReliefEmissionGuard.checkAll(program, issuedOn,
                    resolution.sourceVersionIds(), context.links(),
                    context.homologationPublished(), context.formalNoticeRecorded());
            return new Assembled(periodSchedule, decision, program, resolution);
        }
        // Programa nao accende a guarda nesta emissao (pre-corte/estado):
        // comportamento vigente intacto.
        return new Assembled(periodSchedule, ReliefEmissionGuard.Decision.NOT_APPLICABLE,
                program, resolution);
    }
}
