package br.com.ziipsan.platform.engagement;

import br.com.ziipsan.platform.portal.selfcare.PeerComparison;
import br.com.ziipsan.platform.portal.selfcare.SelfCareParameters;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.Objects;
import java.util.UUID;

/**
 * Relatorio periodico de comparacao com domicilios de perfil semelhante
 * (ED-101): REUTILIZA a comparacao da ED-38 (CAD-102) — a media, a mediana
 * e a posicao sao calculadas por {@link PeerComparison} da ED-38, sem
 * matematica nova nem segundo piso de agregacao. Esta camada acrescenta
 * as guardas proprias da ED-101, NESTA ORDEM (correcao D2 da revisao:
 * as guardas de interruptor/adesao vem ANTES de qualquer calculo de
 * minimo, para modulo desligado suprimir mesmo com configuracao de grupo
 * invalida, e a violacao do piso da ED-38 chega ao chamador como a recusa
 * nomeada desta tarefa, nunca como excecao crua da ED-38):
 *
 * <ol>
 *   <li>modulo do tenant desligado ({@code eng.peer_report_enabled}) →
 *       suprimido {@code FEATURE_DISABLED};</li>
 *   <li>sem adesao voluntaria ativa do usuario → recusa nomeada (o
 *       relatorio e pessoal: so existe para quem aderiu);</li>
 *   <li>comparativo da ED-38 desligado ({@code bill.peer_comparison}) →
 *       suprimido {@code PEER_COMPARISON_DISABLED} (o relatorio USA a
 *       comparacao da ED-38; desligada ela, nao ha relatorio);</li>
 *   <li>grupo abaixo do minimo efetivo → suprimido
 *       {@code BELOW_MIN_GROUP_SIZE} — o TESTE do aceite. O minimo efetivo
 *       e o MAIOR entre {@code eng.peer_group_min_size} (aceite) e o minimo
 *       da ED-38 ({@code bill.peer_min_units}), ambos sob o MESMO piso de
 *       privacidade da O9-3 ({@code PeerComparison.MIN_UNITS_FLOOR},
 *       classe c, so amplia): configuracao abaixo do piso e recusada,
 *       nunca aplicada.</li>
 * </ol>
 *
 * <p>"Periodico": um relatorio por periodo (mes do ciclo). A frequencia e
 * o agendamento da geracao NAO sao nomeados pelo aceite — sao fiacao de
 * integracao (pendencia P-ED101-04); este servico gera sob demanda o
 * relatorio de um periodo, sem presumir periodicidade.
 */
public final class PeerReportService {

    private final EngagementParameters parameters;
    private final SelfCareParameters selfCareParameters;
    private final PeerComparison peerComparison;
    private final GoalService goalService;

    public PeerReportService(EngagementParameters parameters,
                             SelfCareParameters selfCareParameters,
                             GoalService goalService) {
        this.parameters = Objects.requireNonNull(parameters, "parametros sao obrigatorios");
        this.selfCareParameters = Objects.requireNonNull(selfCareParameters,
                "parametros da ED-38 sao obrigatorios");
        this.peerComparison = new PeerComparison(selfCareParameters);
        this.goalService = Objects.requireNonNull(goalService, "servico de metas e obrigatorio");
    }

    /** Minimo de grupo configurado nesta tarefa, sob o piso da ED-38. */
    public int configuredMinGroupSize(UUID tenantId) {
        int configured = parameters.peerGroupMinSize(tenantId)
                .orElse(PeerComparison.MIN_UNITS_FLOOR);
        if (configured < PeerComparison.MIN_UNITS_FLOOR) {
            throw new EngagementValidationException("PEER_GROUP_MIN_BELOW_FLOOR",
                    "eng.peer_group_min_size abaixo do piso " + PeerComparison.MIN_UNITS_FLOOR
                            + " (classe c, so amplia — piso da ED-38/O9-3 reutilizado): recusado");
        }
        return configured;
    }

    /**
     * Minimo efetivo: nunca menor que o da ED-38 nem que o desta tarefa.
     * A violacao do piso no parametro da ED-38 ({@code bill.peer_min_units})
     * e TRADUZIDA aqui para a recusa nomeada desta tarefa
     * ({@code PEER_GROUP_MIN_BELOW_FLOOR}) — correcao D2 da revisao: a
     * {@code IllegalStateException} crua da ED-38 nao vaza ao chamador.
     * O piso em si nao muda: configuracao abaixo dele e recusada, nunca
     * aplicada.
     */
    public int effectiveMinGroupSize(UUID tenantId) {
        int fromEd38;
        try {
            fromEd38 = peerComparison.effectiveMinUnits(tenantId);
        } catch (IllegalStateException e) {
            throw new EngagementValidationException("PEER_GROUP_MIN_BELOW_FLOOR",
                    "bill.peer_min_units abaixo do piso " + PeerComparison.MIN_UNITS_FLOOR
                            + " (classe c, so amplia — piso da ED-38/O9-3 reutilizado): recusado");
        }
        return Math.max(configuredMinGroupSize(tenantId), fromEd38);
    }

    /**
     * Minimo a registrar num relatorio suprimido por modulo desligado:
     * o minimo efetivo quando a configuracao e valida; havendo violacao
     * de piso na configuracao, o proprio piso (a supressao por modulo
     * desligado tem precedencia sobre a recusa de configuracao — D2).
     */
    private int minGroupSizeForDisabledReport(UUID tenantId) {
        try {
            return effectiveMinGroupSize(tenantId);
        } catch (EngagementValidationException e) {
            return PeerComparison.MIN_UNITS_FLOOR;
        }
    }

    public PeerReport generate(UUID tenantId, UUID customerId, UUID unitId, YearMonth period,
                               BigDecimal ownConsumptionM3, PeerCohort cohort) {
        Objects.requireNonNull(cohort, "coorte e obrigatoria");
        if (ownConsumptionM3 == null || ownConsumptionM3.signum() < 0) {
            throw new EngagementValidationException("OWN_CONSUMPTION_INVALID",
                    "consumo proprio deve ser nao-negativo: " + ownConsumptionM3);
        }
        // Guardas PRIMEIRO (correcao D2): interruptor e adesao antes de
        // qualquer calculo de minimo — modulo desligado suprime com
        // FEATURE_DISABLED mesmo se a configuracao de grupo estiver
        // abaixo do piso, e sem adesao a recusa nomeada precede a de piso.
        // (requireActiveEnrollment tambem checa o interruptor lancando
        // FEATURE_DISABLED; aqui a supressao tem precedencia, por desenho.)
        if (!parameters.peerReportEnabled(tenantId)) {
            return PeerReport.suppressed(tenantId, unitId, period, cohort.groupKey(),
                    PeerReport.Suppression.FEATURE_DISABLED,
                    minGroupSizeForDisabledReport(tenantId), ownConsumptionM3);
        }
        // O relatorio e pessoal: exige adesao ativa (recusa nomeada, nao supressao).
        goalService.requireActiveEnrollment(tenantId, customerId, unitId);
        int effectiveMin = effectiveMinGroupSize(tenantId);
        if (!selfCareParameters.peerComparisonEnabled(tenantId)) {
            return PeerReport.suppressed(tenantId, unitId, period, cohort.groupKey(),
                    PeerReport.Suppression.PEER_COMPARISON_DISABLED, effectiveMin, ownConsumptionM3);
        }
        if (cohort.size() < effectiveMin) {
            return PeerReport.suppressed(tenantId, unitId, period, cohort.groupKey(),
                    PeerReport.Suppression.BELOW_MIN_GROUP_SIZE, effectiveMin, ownConsumptionM3);
        }
        PeerComparison.Result result = peerComparison.compare(tenantId, ownConsumptionM3,
                cohort.consumptionsM3());
        if (!result.published()) {
            // Inalcancavel pelas guardas acima (minimo e interruptor ja
            // conferidos); se a ED-38 mudar, a supressao dela prevalece.
            return PeerReport.suppressed(tenantId, unitId, period, cohort.groupKey(),
                    PeerReport.Suppression.BELOW_MIN_GROUP_SIZE, effectiveMin, ownConsumptionM3);
        }
        return PeerReport.published(tenantId, unitId, period, cohort.groupKey(), effectiveMin,
                result.cohortAverageM3(), result.cohortMedianM3(),
                result.ownConsumptionM3(), result.position());
    }
}
